import SwiftUI
import Shared

/// 통화 push 대상 — Compose CallRoute(chatRoomId, title, ring) 미러(ChatRoomRef와 같은 규칙).
/// ring=true는 발신(입장+벨울림), false는 수신 배너 수락으로 진입(벨울림 없음).
struct CallRef: Equatable {
    let chatRoomId: Int64
    let title: String
    let ring: Bool
}

/// 방 통화 — DM 1:1과 그룹 방 공용(페이스톡 미러)·composeApp CallScreen 미러.
/// 진입 즉시 통화에 입장한다(발신=벨울림 포함, 수신=배너 수락으로 진입).
/// 카메라/마이크 미디어는 네이티브 WebRTC SDK 도입 후속이라 로스터 전용으로 참가한다
/// (Compose Desktop과 동일한 표시 — 명단만 실시간).
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
            sendCallInviteUseCase: container.sendCallInviteUseCase,
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
                    rosterStrip(peers: uiState.call.peers)
                    if uiState.call.isInCall {
                        // 미디어 미지원(네이티브 WebRTC SDK 후속) — 명단만 실시간으로 표시된다
                        Text("카메라·마이크 없이 참여 중입니다. 통화 명단만 실시간으로 표시됩니다.")
                            .font(.caption)
                            .foregroundColor(colors.inkFaint)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
            }
            // 통화 컨트롤 바 — 하단 고정(Compose CallScreen 미러, 미디어 토글은 SDK 도입 후속)
            HStack {
                Spacer()
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
        // 진입 즉시 참가 — 이미 통화 중(재진입)이면 VM 가드가 무시한다
        .onAppear { viewModel.onAction(.join) }
        .onReceive(viewModel.event) { event in
            switch event {
            case .ended: dismiss()
            }
        }
    }

    private func statusLabel(_ uiState: CallViewModel.UiState) -> String {
        if !uiState.call.isInCall { return "연결 중…" }
        if !uiState.call.isConnected { return "재연결 중…" }
        if uiState.isAloneInCall, uiState.isRinging { return "응답을 기다리는 중…" }
        if uiState.isAloneInCall { return "아직 다른 참여자가 없습니다." }
        return "통화 중 \(uiState.call.peers.count)명"
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
