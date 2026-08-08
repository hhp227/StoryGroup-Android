import AVFoundation
import Foundation
import ReplayKit
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

    /// 화면 공유 중 여부 — 시작 성공/중지·시작 실패(거부)를 모두 반영한다. 메인 큐에서 불린다
    var onScreenSharing: ((Bool) -> Void)?

    /// 전/후면 전환 결과(전면=true) — 뷰가 로컬 미리보기 거울에 쓴다. 메인 큐에서 불린다
    var onCameraFacing: ((Bool) -> Void)?

    private let lock = NSLock()

    private let factory: RTCPeerConnectionFactory

    private let rtcConfig: RTCConfiguration

    private var peers = [Int64: PeerHandle]()

    private var localAudioTrack: RTCAudioTrack?

    private var localVideoTrack: RTCVideoTrack?

    private var videoSource: RTCVideoSource?

    private var capturer: RTCCameraVideoCapturer?

    // 화면 공유 자원 — 카메라와 별도 파이프라인(카메라는 stop하지 않고 보관, 웹 D9 미러)
    private var screenSource: RTCVideoSource?

    private var screenCapturer: RTCVideoCapturer?

    private var screenVideoTrack: RTCVideoTrack?

    private var micEnabled = true

    private var camEnabled = true

    /// 카메라 캡처가 실제로 도는지 — 보이스톡(camEnabled=false 시작)은 캡처러만 만들어 두고 열지 않는다
    private var captureRunning = false

    // 현재 카메라가 전면인지 — onCameraFacing으로 뷰 거울에 반영한다(전면만 거울)
    private var frontCamera = true

    private var speakerEnabled = true

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

        // 통화 오디오 세션 진입(페이스톡 성격) — 현재 토글대로 라우팅 후 활성화.
        // dispose가 반드시 원복한다(Android MODE_IN_COMMUNICATION 진입/원복 미러)
        applySpeakerRouting()
        try? AVAudioSession.sharedInstance().setActive(true)

        let devices = RTCCameraVideoCapturer.captureDevices()
        guard let device = devices.first(where: { $0.position == .front }) ?? devices.first,
              let format = Self.selectFormat(for: device)
        else { return }
        let fps = Self.selectFps(for: format)
        let isFront = device.position == .front
        let source = factory.videoSource()
        let cameraCapturer = RTCCameraVideoCapturer(delegate: source)
        let track = factory.videoTrack(with: source, trackId: "video0")

        track.isEnabled = camEnabled
        lock.lock()
        videoSource = source
        capturer = cameraCapturer
        localVideoTrack = track
        frontCamera = isFront
        captureRunning = camEnabled
        let camOn = camEnabled

        lock.unlock()
        // 보이스톡(camEnabled=false) 시작이면 카메라를 아직 열지 않는다 — 켤 때 lazy 시작(setCamEnabled).
        // 트랙·sender는 미리 만들어 두므로 켤 때 재협상이 필요 없다(Android 미러)
        guard camOn else { return }

        cameraCapturer.startCapture(with: device, format: format, fps: fps)
        DispatchQueue.main.async { [weak self] in
            // 카메라가 실제로 도는 동안만 로컬 트랙 발행 — 보이스톡에선 전환/공유 버튼이 숨는다
            self?.onLocalVideoTrack?(track)
            // 전면이 없는 기기(후면 시작)면 처음부터 거울 없이 그린다(Android 미러)
            self?.onCameraFacing?(isFront)
        }
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
        // 공유 중 새로 들어온 피어도 화면을 받는다 — 송출 트랙 자체를 바꿔두는 웹 D9 미러
        let videoTrack = screenVideoTrack ?? localVideoTrack

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
        // 보이스톡 → 페이스톡 전환: 시작 때 열지 않은 카메라를 이때 연다 —
        // sender에는 트랙이 이미 실려 있어 재협상 없이 프레임만 흐르기 시작한다(Android 미러)
        lock.lock()
        guard enabled, !captureRunning, !disposed,
              let cameraCapturer = capturer, let track = localVideoTrack
        else {
            lock.unlock()
            return
        }
        let sharing = screenVideoTrack != nil

        lock.unlock()
        let devices = RTCCameraVideoCapturer.captureDevices()
        guard let device = devices.first(where: { $0.position == .front }) ?? devices.first,
              let format = Self.selectFormat(for: device)
        else { return }
        let isFront = device.position == .front

        cameraCapturer.startCapture(with: device, format: format, fps: Self.selectFps(for: format))
        lock.lock()
        captureRunning = true
        frontCamera = isFront
        lock.unlock()
        DispatchQueue.main.async { [weak self] in
            // 공유 중이면 로컬 표시는 화면 트랙 유지 — 복귀(stopScreenShare) 때 카메라 트랙이 실린다
            if !sharing { self?.onLocalVideoTrack?(track) }
            self?.onCameraFacing?(isFront)
        }
    }

    /// 전/후면 카메라 전환 — 반대편 카메라가 없으면 무시(시뮬레이터 등). 이미 도는 캡처러에
    /// startCapture를 다시 부르면 세션 입력이 교체된다(Android switchCamera 미러).
    /// 거울 반전은 뷰가 onCameraFacing(전면 여부)로 반영한다 — 상대에게는 원본이 그대로 간다
    func switchCamera() {
        lock.lock()
        let cameraCapturer = capturer
        let targetPosition: AVCaptureDevice.Position = frontCamera ? .back : .front

        lock.unlock()
        guard let cameraCapturer,
              let device = RTCCameraVideoCapturer.captureDevices().first(where: { $0.position == targetPosition }),
              let format = Self.selectFormat(for: device)
        else { return }

        cameraCapturer.startCapture(with: device, format: format, fps: Self.selectFps(for: format))
        lock.lock()
        frontCamera = targetPosition == .front
        lock.unlock()
        DispatchQueue.main.async { [weak self] in self?.onCameraFacing?(targetPosition == .front) }
    }

    /// 스피커폰 라우팅 — 켬=본체 스피커 강제, 끔=기본 경로(수화구·이어폰) 복귀(영상통화라 기본 ON)
    func setSpeakerEnabled(_ enabled: Bool) {
        speakerEnabled = enabled
        // start() 전이면 기억만 — 세션 진입 시 현재 토글대로 적용된다(Android 미러)
        if started { applySpeakerRouting() }
    }

    /// 켬=playAndRecord+videoChat 모드+스피커 오버라이드, 끔=voiceChat 모드(수화구 기본) —
    /// videoChat은 defaultToSpeaker가 내장이라 끄기는 모드째 voiceChat으로 내린다.
    /// WebRTC 오디오 유닛이 (재)시작할 때 자체 기본 구성을 다시 적용하므로 그 기본값도
    /// 같은 모드로 맞춰 연결 시점에 라우팅이 뒤집히지 않게 한다
    private func applySpeakerRouting() {
        let audioSession = AVAudioSession.sharedInstance()
        let mode: AVAudioSession.Mode = speakerEnabled ? .videoChat : .voiceChat
        let webRtcConfig = RTCAudioSessionConfiguration.webRTC()

        webRtcConfig.category = AVAudioSession.Category.playAndRecord.rawValue
        webRtcConfig.mode = mode.rawValue
        RTCAudioSessionConfiguration.setWebRTC(webRtcConfig)
        try? audioSession.setCategory(.playAndRecord, mode: mode, options: [])
        try? audioSession.overrideOutputAudioPort(speakerEnabled ? .speaker : .none)
    }

    /// 화면 공유 시작 — RPScreenRecorder 인앱 캡처 프레임을 화면 전용 소스로 밀어넣고
    /// 각 피어 video sender의 트랙만 교체한다(SDP 재협상 없음, 웹 D9 replaceTrack 미러).
    /// 카메라 트랙은 stop하지 않고 보관 → 중지 시 즉시 복귀. 오디오 전용이면 무시(sender 없음).
    /// 웹 getDisplayMedia와 달리 앱 자기 화면만 캡처된다(시스템 전체는 Broadcast Extension 필요).
    func startScreenShare() {
        lock.lock()
        if disposed || screenVideoTrack != nil || localVideoTrack == nil || !RPScreenRecorder.shared().isAvailable {
            lock.unlock()
            return
        }
        let source = factory.videoSource(forScreenCast: true)
        let screenCapturer = RTCVideoCapturer(delegate: source)
        let track = factory.videoTrack(with: source, trackId: "screen0")

        screenSource = source
        self.screenCapturer = screenCapturer
        screenVideoTrack = track
        lock.unlock()

        let recorder = RPScreenRecorder.shared()

        recorder.isMicrophoneEnabled = false
        recorder.startCapture(handler: { sampleBuffer, type, error in
            // source/capturer는 시작 시점 지역 캡처 — 중지 후 도착하는 프레임은 싱크가 없어 무해하다
            guard type == .video, error == nil,
                  let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer)
            else { return }

            let timeStampNs = Int64(CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sampleBuffer)) * Double(NSEC_PER_SEC))
            let frame = RTCVideoFrame(buffer: RTCCVPixelBuffer(pixelBuffer: pixelBuffer), rotation: ._0, timeStampNs: timeStampNs)

            source.capturer(screenCapturer, didCapture: frame)
        }, completionHandler: { [weak self] error in
            guard let self else { return }

            if error != nil {
                // 사용자 거부/시작 실패 — 웹 공유 선택창 취소 미러(에러 아님), 자원과 sender만 되돌린다
                self.lock.lock()
                self.screenSource = nil
                self.screenCapturer = nil
                self.screenVideoTrack = nil
                let handles = Array(self.peers.values)
                let camTrack = self.localVideoTrack

                self.lock.unlock()
                handles.forEach { handle in
                    handle.pc?.senders.first { $0.track?.kind == "video" }?.track = camTrack
                }
                return
            }
            self.lock.lock()
            let stillSharing = self.screenVideoTrack === track
            let handles = Array(self.peers.values)

            self.lock.unlock()
            guard stillSharing else { return }

            // 전송 중인 video sender의 트랙만 교체 — 시그널링 무변경(웹 replaceTrack, D9)
            handles.forEach { handle in
                handle.pc?.senders.first { $0.track?.kind == "video" }?.track = track
            }
            DispatchQueue.main.async { [weak self] in
                self?.onLocalVideoTrack?(track)
                self?.onScreenSharing?(true)
            }
        })
    }

    /// 화면 공유 중지 — 보관해둔 카메라 트랙을 각 sender와 미리보기에 복귀(웹 stopScreenShare 미러)
    func stopScreenShare() {
        lock.lock()
        guard screenVideoTrack != nil else {
            lock.unlock()
            return
        }
        let camTrack = localVideoTrack
        let handles = Array(peers.values)

        screenSource = nil
        screenCapturer = nil
        screenVideoTrack = nil
        lock.unlock()
        RPScreenRecorder.shared().stopCapture { _ in }
        handles.forEach { handle in
            handle.pc?.senders.first { $0.track?.kind == "video" }?.track = camTrack
        }
        DispatchQueue.main.async { [weak self] in
            self?.onLocalVideoTrack?(camTrack)
            self?.onScreenSharing?(false)
        }
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
        // 공유 중에 끊으면 화면 캡처도 함께 내린다 — 웹 cleanup의 screenTrack.stop 미러
        let wasSharing = screenVideoTrack != nil
        let wasStarted = started

        capturer = nil
        localVideoTrack = nil
        localAudioTrack = nil
        videoSource = nil
        screenSource = nil
        screenCapturer = nil
        screenVideoTrack = nil
        lock.unlock()
        DispatchQueue.main.async { [weak self] in self?.onLocalVideoTrack?(nil) }
        closing.forEach { $0.pc?.close() }
        stoppingCapturer?.stopCapture()
        if wasSharing { RPScreenRecorder.shared().stopCapture { _ in } }
        // 오디오 세션 원복 — 세션에 들어간 적이 있을 때만. 오버라이드를 걷고 비활성화해
        // 다른 앱 오디오 재개를 알린다(Android 통화 모드 원복 미러)
        if wasStarted {
            try? AVAudioSession.sharedInstance().overrideOutputAudioPort(.none)
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        }
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
