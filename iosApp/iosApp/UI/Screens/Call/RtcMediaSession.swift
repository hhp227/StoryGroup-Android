import AVFoundation
import Foundation
import WebRTC
import Shared

/// WebRTC 미디어 세션 — 웹 use-rtc-session·Android AndroidRtcMediaSession의 iOS판(stasel/WebRTC).
/// 시그널 payload는 같은 JSON({type,sdp} / {candidate,sdpMid,sdpMLineIndex})으로 상호운용된다.
/// 메시 구성 규칙(글레어 방지: userId 작은 쪽이 offer)은 VM(CallViewModel)이 판단해
/// createPeer(initiator:)로 내린다. WebRTC 콜백은 내부 스레드에서 오므로 피어 맵은 lock으로
/// 보호하고, 밖으로는 메인 큐로 넘긴 클로저만 부른다. 카메라 실패 시 오디오 전용으로 계속한다.
final class RtcMediaSession {
    struct OutgoingSignal {
        let toUserId: Int64
        let type: RtcSignalType
        let payload: String
    }

    /// 로컬 미리보기 트랙 — 카메라가 없거나 실패하면 nil(오디오 전용). 메인 큐에서 불린다
    var onLocalVideoTrack: ((RTCVideoTrack?) -> Void)?

    /// 원격 비디오 트랙(userId별) — nil이면 제거. 메인 큐에서 불린다
    var onRemoteVideoTrack: ((Int64, RTCVideoTrack?) -> Void)?

    /// 릴레이할 시그널 — VM이 STOMP(/app/rtc/.../signal)로 보낸다. 메인 큐에서 불린다
    var onOutgoingSignal: ((OutgoingSignal) -> Void)?

    private let lock = NSLock()

    private let factory: RTCPeerConnectionFactory

    private let rtcConfig: RTCConfiguration

    private var peers = [Int64: PeerHandle]()

    private var localAudioTrack: RTCAudioTrack?

    private var localVideoTrack: RTCVideoTrack?

    private var videoSource: RTCVideoSource?

    private var capturer: RTCCameraVideoCapturer?

    private var micEnabled = true

    private var camEnabled = true

    private var started = false

    private var disposed = false

    init(iceServers: [Shared.IceServer]) {
        Self.initializeFactoryOnce()
        factory = RTCPeerConnectionFactory(
            encoderFactory: RTCDefaultVideoEncoderFactory(),
            decoderFactory: RTCDefaultVideoDecoderFactory()
        )
        rtcConfig = RTCConfiguration()
        rtcConfig.sdpSemantics = .unifiedPlan
        rtcConfig.iceServers = iceServers.map { server in
            if let username = server.username, let credential = server.credential, !username.isEmpty, !credential.isEmpty {
                return RTCIceServer(urlStrings: server.urls, username: username, credential: credential)
            }
            return RTCIceServer(urlStrings: server.urls)
        }
    }

    /// 로컬 캡처 시작 — 전면 카메라 우선, 캡처 실패 시 오디오 전용으로 계속한다(웹 폴백 미러)
    func start() {
        lock.lock()
        if disposed || started {
            lock.unlock()
            return
        }
        started = true
        let audioSource = factory.audioSource(with: RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil))
        let audioTrack = factory.audioTrack(with: audioSource, trackId: "audio0")

        audioTrack.isEnabled = micEnabled
        localAudioTrack = audioTrack
        lock.unlock()

        let devices = RTCCameraVideoCapturer.captureDevices()
        guard let device = devices.first(where: { $0.position == .front }) ?? devices.first,
              let format = Self.selectFormat(for: device)
        else { return }
        let fps = Self.selectFps(for: format)
        let source = factory.videoSource()
        let cameraCapturer = RTCCameraVideoCapturer(delegate: source)
        let track = factory.videoTrack(with: source, trackId: "video0")

        track.isEnabled = camEnabled
        lock.lock()
        videoSource = source
        capturer = cameraCapturer
        localVideoTrack = track
        lock.unlock()
        cameraCapturer.startCapture(with: device, format: format, fps: fps)
        DispatchQueue.main.async { [weak self] in self?.onLocalVideoTrack?(track) }
    }

    /// 피어 연결 생성 — initiator면 offer를 만들어 흘린다. 이미 있으면 무시(Android 미러)
    func createPeer(peerId: Int64, initiator: Bool) {
        lock.lock()
        if disposed || peers[peerId] != nil {
            lock.unlock()
            return
        }
        let handle = PeerHandle(peerId: peerId, session: self)
        guard let pc = factory.peerConnection(
            with: rtcConfig,
            constraints: RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil),
            delegate: handle
        ) else {
            lock.unlock()
            return
        }

        handle.pc = pc
        peers[peerId] = handle
        let audioTrack = localAudioTrack
        let videoTrack = localVideoTrack

        lock.unlock()
        if let audioTrack { pc.add(audioTrack, streamIds: ["stream0"]) }
        if let videoTrack { pc.add(videoTrack, streamIds: ["stream0"]) }
        if initiator { createAndSendOffer(handle) }
    }

    /// 피어 연결 종료(로스터에서 빠진 상대) — 원격 트랙도 함께 정리된다
    func closePeer(peerId: Int64) {
        lock.lock()
        let handle = peers.removeValue(forKey: peerId)

        lock.unlock()
        guard let handle else { return }

        DispatchQueue.main.async { [weak self] in self?.onRemoteVideoTrack?(peerId, nil) }
        handle.pc?.close()
    }

    /// 시그널링 유실 시 메시 전체 해체(로컬 미디어 유지) — 재연결 후 PEERS로 재구축(웹 D7 미러)
    func closeAllPeers() {
        lock.lock()
        let closing = Array(peers.values)

        peers.removeAll()
        lock.unlock()
        closing.forEach { handle in
            DispatchQueue.main.async { [weak self] in self?.onRemoteVideoTrack?(handle.peerId, nil) }
            handle.pc?.close()
        }
    }

    /// 수신 시그널 적용 — OFFER면 answer를 만들어 되흘리고, ICE는 원격 SDP 설정 전이면 큐잉한다
    func applySignal(fromUserId: Int64, type: RtcSignalType, payload: String) {
        lock.lock()
        let handle = peers[fromUserId]

        lock.unlock()
        guard let handle else { return }

        switch type {
        case .offer: applyRemoteOffer(handle, payload: payload)
        case .answer: applyRemoteAnswer(handle, payload: payload)
        case .ice: applyRemoteCandidate(handle, payload: payload)
        default: break
        }
    }

    func setMicEnabled(_ enabled: Bool) {
        micEnabled = enabled
        localAudioTrack?.isEnabled = enabled
    }

    func setCamEnabled(_ enabled: Bool) {
        camEnabled = enabled
        localVideoTrack?.isEnabled = enabled
    }

    /// 세션 폐기 — 캡처/피어 전부 해제. 이후 어떤 호출도 하면 안 된다
    func dispose() {
        lock.lock()
        if disposed {
            lock.unlock()
            return
        }
        disposed = true
        let closing = Array(peers.values)

        peers.removeAll()
        let stoppingCapturer = capturer

        capturer = nil
        localVideoTrack = nil
        localAudioTrack = nil
        videoSource = nil
        lock.unlock()
        DispatchQueue.main.async { [weak self] in self?.onLocalVideoTrack?(nil) }
        closing.forEach { $0.pc?.close() }
        stoppingCapturer?.stopCapture()
    }

    // MARK: - SDP/ICE (payload 계약은 웹 JSON.stringify·Android JSONObject와 1:1)

    /// offer 생성 → 로컬 SDP 설정 성공 후에만 릴레이(웹이 pc.localDescription을 보내는 것과 동일 시점)
    private func createAndSendOffer(_ handle: PeerHandle) {
        let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)

        handle.pc?.offer(for: constraints) { [weak self, weak handle] description, _ in
            guard let self, let handle, let description else { return }

            handle.pc?.setLocalDescription(description) { error in
                if error == nil { self.emitDescription(handle.peerId, type: .offer, description: description) }
            }
        }
    }

    private func applyRemoteOffer(_ handle: PeerHandle, payload: String) {
        guard let offer = Self.parseDescription(payload) else { return }

        handle.pc?.setRemoteDescription(offer) { [weak self, weak handle] error in
            guard let self, let handle, error == nil else { return }

            self.drainPendingCandidates(handle)
            let constraints = RTCMediaConstraints(mandatoryConstraints: nil, optionalConstraints: nil)

            handle.pc?.answer(for: constraints) { description, _ in
                guard let description else { return }

                handle.pc?.setLocalDescription(description) { error in
                    if error == nil { self.emitDescription(handle.peerId, type: .answer, description: description) }
                }
            }
        }
    }

    private func applyRemoteAnswer(_ handle: PeerHandle, payload: String) {
        guard let answer = Self.parseDescription(payload) else { return }

        handle.pc?.setRemoteDescription(answer) { [weak self, weak handle] error in
            guard let self, let handle, error == nil else { return }

            self.drainPendingCandidates(handle)
        }
    }

    /// 원격 SDP 설정 전에 도착한 candidate는 큐잉했다가 적용한다(표준 패턴, 웹 pendingCandidates 미러)
    private func applyRemoteCandidate(_ handle: PeerHandle, payload: String) {
        guard let candidate = Self.parseCandidate(payload) else { return }

        lock.lock()
        let applyNow = handle.remoteDescriptionSet

        if !applyNow { handle.pendingCandidates.append(candidate) }
        lock.unlock()
        if applyNow { handle.pc?.add(candidate) { _ in } }
    }

    private func drainPendingCandidates(_ handle: PeerHandle) {
        lock.lock()
        handle.remoteDescriptionSet = true
        let queued = handle.pendingCandidates

        handle.pendingCandidates.removeAll()
        lock.unlock()
        queued.forEach { candidate in handle.pc?.add(candidate) { _ in } }
    }

    private func emitDescription(_ peerId: Int64, type: RtcSignalType, description: RTCSessionDescription) {
        let json: [String: Any] = [
            "type": RTCSessionDescription.string(for: description.type),
            "sdp": description.sdp,
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: json),
              let payload = String(data: data, encoding: .utf8)
        else { return }

        DispatchQueue.main.async { [weak self] in
            self?.onOutgoingSignal?(OutgoingSignal(toUserId: peerId, type: type, payload: payload))
        }
    }

    fileprivate func emitCandidate(_ peerId: Int64, candidate: RTCIceCandidate) {
        var json: [String: Any] = [
            "candidate": candidate.sdp,
            "sdpMLineIndex": candidate.sdpMLineIndex,
        ]

        if let sdpMid = candidate.sdpMid { json["sdpMid"] = sdpMid }
        guard let data = try? JSONSerialization.data(withJSONObject: json),
              let payload = String(data: data, encoding: .utf8)
        else { return }

        DispatchQueue.main.async { [weak self] in
            self?.onOutgoingSignal?(OutgoingSignal(toUserId: peerId, type: .ice, payload: payload))
        }
    }

    fileprivate func emitRemoteTrack(_ peerId: Int64, track: RTCVideoTrack) {
        DispatchQueue.main.async { [weak self] in self?.onRemoteVideoTrack?(peerId, track) }
    }

    /// 웹 JSON.stringify(localDescription)와 같은 {type,sdp} 형태를 파싱한다
    private static func parseDescription(_ payload: String) -> RTCSessionDescription? {
        guard let data = payload.data(using: .utf8),
              let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let typeString = json["type"] as? String,
              let sdp = json["sdp"] as? String
        else { return nil }

        return RTCSessionDescription(type: RTCSessionDescription.type(for: typeString), sdp: sdp)
    }

    /// 웹 JSON.stringify(candidate)와 같은 {candidate,sdpMid,sdpMLineIndex} 형태를 파싱한다
    private static func parseCandidate(_ payload: String) -> RTCIceCandidate? {
        guard let data = payload.data(using: .utf8),
              let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let candidate = json["candidate"] as? String
        else { return nil }

        return RTCIceCandidate(
            sdp: candidate,
            sdpMLineIndex: Int32(json["sdpMLineIndex"] as? Int ?? 0),
            sdpMid: json["sdpMid"] as? String
        )
    }

    /// Android CAPTURE 1280x720/30fps 미러 — 지원 포맷 중 가장 근접한 해상도를 고른다
    private static func selectFormat(for device: AVCaptureDevice) -> AVCaptureDevice.Format? {
        let targetWidth: Int32 = 1280
        let targetHeight: Int32 = 720

        return RTCCameraVideoCapturer.supportedFormats(for: device).min { lhs, rhs in
            func distance(_ format: AVCaptureDevice.Format) -> Int32 {
                let size = CMVideoFormatDescriptionGetDimensions(format.formatDescription)
                return abs(size.width - targetWidth) + abs(size.height - targetHeight)
            }
            return distance(lhs) < distance(rhs)
        }
    }

    private static func selectFps(for format: AVCaptureDevice.Format) -> Int {
        let maxFps = format.videoSupportedFrameRateRanges.map(\.maxFrameRate).max() ?? 30
        return Int(min(maxFps, 30))
    }

    private static var factoryInitialized = false

    /// 전역 초기화는 프로세스당 1회면 충분하다(Android initializeFactoryOnce 미러)
    private static func initializeFactoryOnce() {
        guard !factoryInitialized else { return }
        factoryInitialized = true
        RTCInitializeSSL()
    }
}

/// 피어 연결 하나의 핸들+델리게이트 — WebRTC 콜백 스레드에서 불리므로 세션으로 발행만 위임한다.
/// pc.delegate는 weak라 세션의 peers 딕셔너리가 이 핸들의 수명을 쥔다(Android PeerHandle+Observer 미러)
private final class PeerHandle: NSObject, RTCPeerConnectionDelegate {
    let peerId: Int64

    var pc: RTCPeerConnection?

    var pendingCandidates = [RTCIceCandidate]()

    var remoteDescriptionSet = false

    private unowned let session: RtcMediaSession

    init(peerId: Int64, session: RtcMediaSession) {
        self.peerId = peerId
        self.session = session
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didGenerate candidate: RTCIceCandidate) {
        session.emitCandidate(peerId, candidate: candidate)
    }

    /// unified plan 원격 트랙 수신 — 비디오 트랙만 상태로 올린다(오디오는 WebRTC가 자체 재생)
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd rtpReceiver: RTCRtpReceiver, streams mediaStreams: [RTCMediaStream]) {
        if let track = rtpReceiver.track as? RTCVideoTrack {
            session.emitRemoteTrack(peerId, track: track)
        }
    }

    func peerConnection(_ peerConnection: RTCPeerConnection, didChange stateChanged: RTCSignalingState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didAdd stream: RTCMediaStream) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove stream: RTCMediaStream) {}
    func peerConnectionShouldNegotiate(_ peerConnection: RTCPeerConnection) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceConnectionState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didChange newState: RTCIceGatheringState) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didRemove candidates: [RTCIceCandidate]) {}
    func peerConnection(_ peerConnection: RTCPeerConnection, didOpen dataChannel: RTCDataChannel) {}
}
