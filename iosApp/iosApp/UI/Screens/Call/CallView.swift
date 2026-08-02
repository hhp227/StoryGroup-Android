import AVFoundation
import SwiftUI
import WebRTC
import Shared

/// 통화 push 대상 — Compose CallRoute(chatRoomId, title, ring) 미러(ChatRoomRef와 같은 규칙).
/// ring=true는 발신(입장+벨울림), false는 수신 배너 수락으로 진입(벨울림 없음).
struct CallRef: Equatable {
    let chatRoomId: Int64
    let title: String
    let ring: Bool
}

/// 방 통화 — DM 1:1과 그룹 방 공용(페이스톡 미러)·composeApp CallScreen 미러.
/// 진입 즉시 카메라/마이크 권한을 물어 통화에 입장한다(발신=벨울림 포함, 수신=배너 수락으로 진입).
/// 허용=영상 통화(WebRTC 풀 메시), 거부=로스터 전용(명단만 실시간 — Compose 권한 거부 폴백 미러).
struct CallView: View {
    @StateObject private var viewModel: CallViewModel

    let title: String

    var body: some View {
        CallContent(viewModel: viewModel, title: title)
    }

    init(chatRoomId: Int64, title: String, ring: Bool, container: AppContainer) {
        _viewModel = StateObject(wrappedValue: CallViewModel(
            chatRoomId: chatRoomId,
            ring: ring,
            observeRtcCallEventsUseCase: container.observeRtcCallEventsUseCase,
            observeRtcSignalsUseCase: container.observeRtcSignalsUseCase,
            sendRtcSignalUseCase: container.sendRtcSignalUseCase,
            sendCallInviteUseCase: container.sendCallInviteUseCase,
            getIceServersUseCase: container.getIceServersUseCase,
            getCurrentUserIdUseCase: container.getCurrentUserIdUseCase
        ))
        self.title = title
    }
}

private struct CallContent: View {
    @ObservedObject var viewModel: CallViewModel

    let title: String

    @Environment(\.sgColors) private var colors

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let uiState = viewModel.uiState

        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(statusLabel(uiState))
                        .font(.subheadline)
                        .foregroundColor(colors.inkSoft)
                    if uiState.call.isMediaActive {
                        videoGrid(uiState)
                    } else {
                        rosterStrip(peers: uiState.call.peers)
                        if uiState.call.isInCall {
                            // 권한 거부 — 명단만 실시간으로 표시된다(Compose 미러)
                            Text("카메라·마이크 없이 참여 중입니다. 통화 명단만 실시간으로 표시됩니다.")
                                .font(.caption)
                                .foregroundColor(colors.inkFaint)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
            }
            // 통화 컨트롤 바 — 하단 고정(토글은 미디어 활성 시에만, Compose CallScreen 미러)
            HStack(spacing: 16) {
                Spacer()
                if uiState.call.isMediaActive {
                    toggleButton(
                        systemImage: uiState.call.micOn ? "mic.fill" : "mic.slash.fill",
                        active: uiState.call.micOn
                    ) { viewModel.onAction(.toggleMic) }
                    toggleButton(
                        systemImage: uiState.call.camOn ? "video.fill" : "video.slash.fill",
                        active: uiState.call.camOn
                    ) { viewModel.onAction(.toggleCam) }
                    // 스피커폰 — 영상통화라 기본 ON, 끄면 수화구·이어폰 경로(웹엔 없는 모바일 전용)
                    toggleButton(
                        systemImage: uiState.call.speakerOn ? "speaker.wave.2.fill" : "speaker.slash.fill",
                        active: uiState.call.speakerOn
                    ) { viewModel.onAction(.toggleSpeaker) }
                    // 오디오 전용(카메라 실패)이면 video sender가 없어 replaceTrack 불가 — 버튼 숨김(웹 D9)
                    if uiState.call.localVideoTrack != nil {
                        toggleButton(
                            systemImage: uiState.call.sharing ? "rectangle.on.rectangle.slash" : "rectangle.on.rectangle",
                            active: uiState.call.sharing
                        ) { viewModel.onAction(.toggleScreenShare) }
                    }
                }
                Button {
                    viewModel.onAction(.hangUp)
                } label: {
                    Image(systemName: "phone.down.fill")
                        .font(.system(size: 20))
                        .foregroundColor(colors.onAccent)
                        .frame(width: 56, height: 56)
                        .background(Circle().fill(colors.rust))
                }
                .buttonStyle(.plain)
                Spacer()
            }
            .padding(16)
        }
        .background(colors.paper.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        // 진입 즉시 권한 → 참가 — 이미 통화 중(재진입)이면 VM 가드가 무시한다(Compose 미러)
        .onAppear { requestPermissionsAndJoin() }
        .onReceive(viewModel.event) { event in
            switch event {
            case .ended: dismiss()
            }
        }
    }

    /// 카메라+마이크 권한 요청 — 이미 허용이면 즉시, 하나라도 거부면 로스터 전용 참가
    /// (Compose rememberRtcPermissionsRequester 미러)
    private func requestPermissionsAndJoin() {
        if viewModel.uiState.call.isInCall { return }

        AVCaptureDevice.requestAccess(for: .video) { videoGranted in
            AVCaptureDevice.requestAccess(for: .audio) { audioGranted in
                DispatchQueue.main.async {
                    viewModel.onAction(.join(withMedia: videoGranted && audioGranted))
                }
            }
        }
    }

    private func statusLabel(_ uiState: CallViewModel.UiState) -> String {
        // 발신 무응답 — 잠깐 보여준 뒤 VM이 ended로 pop한다
        if uiState.isNoAnswer { return "응답이 없어 통화를 종료합니다." }
        if !uiState.call.isInCall { return "연결 중…" }
        if !uiState.call.isConnected { return "재연결 중…" }
        if uiState.isAloneInCall, uiState.isRinging { return "응답을 기다리는 중…" }
        if uiState.isAloneInCall { return "아직 다른 참여자가 없습니다." }
        return "통화 중 \(uiState.call.peers.count)명"
    }

    /// 비디오 그리드 — 내 미리보기(거울)+상대 타일, 비디오 없는 상대는 아바타 타일
    /// (Compose RtcVideoGrid 미러). 내 타일은 카메라를 끄면 아바타 폴백(Compose camOn 게이트 미러)
    private func videoGrid(_ uiState: CallViewModel.UiState) -> some View {
        let myUserId = uiState.myUserId
        let remotePeers = uiState.call.peers.filter { $0.userId != myUserId }

        return LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
            // 공유 중 내 타일은 camOn과 무관하게 송출 중인 화면을 보여준다 — 화면은 거울 반전 없이(D9)
            videoTile(
                label: "나",
                track: uiState.call.camOn || uiState.call.sharing ? uiState.call.localVideoTrack : nil,
                mirror: !uiState.call.sharing
            )
            ForEach(remotePeers, id: \.userId) { peer in
                videoTile(label: peer.userName, track: uiState.call.remoteVideoTracks[peer.userId], mirror: false)
            }
        }
    }

    @ViewBuilder private func videoTile(label: String, track: RTCVideoTrack?, mirror: Bool) -> some View {
        ZStack(alignment: .bottomLeading) {
            if let track {
                RtcVideoView(track: track, mirror: mirror)
            } else {
                // 비디오 없는 참여자(오디오 전용) — 아바타 자리 표시(웹 hasVideo=false 타일 미러)
                ZStack {
                    colors.linen
                    SGAvatar(name: label, size: 48)
                }
            }
            Text(label)
                .font(.caption2)
                .foregroundColor(.white)
                .padding(.horizontal, 8)
                .padding(.vertical, 2)
                .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(Color.black.opacity(0.55)))
                .padding(8)
        }
        // 세로 3:4 크롭 — Compose RtcPeerTile(aspectRatio 3/4)과 동일 비율(모바일 세로 프레임에 맞춤).
        // 웹(4:3 가로)은 데스크톱 웹캠 기준이라 앱과 다른 게 의도다
        .aspectRatio(3 / 4, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func toggleButton(systemImage: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 18))
                .foregroundColor(active ? colors.ink : colors.onAccent)
                .frame(width: 48, height: 48)
                .background(Circle().fill(active ? colors.linen : colors.inkSoft))
        }
        .buttonStyle(.plain)
    }

    /// 로스터 표시(미디어 없음) — 아바타 스트립(Compose RosterOnlyStrip 미러)
    @ViewBuilder private func rosterStrip(peers: [RtcCallPeer]) -> some View {
        if peers.isEmpty {
            Text("통화 명단을 불러오는 중…").font(.caption).foregroundColor(colors.inkSoft)
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(peers, id: \.userId) { peer in
                        VStack(spacing: 4) {
                            SGAvatar(name: peer.userName, size: 56)
                            Text(peer.userId == viewModel.uiState.myUserId ? "나" : peer.userName)
                                .font(.caption2)
                                .foregroundColor(colors.inkSoft)
                                .lineLimit(1)
                        }
                    }
                }
            }
        }
    }
}
