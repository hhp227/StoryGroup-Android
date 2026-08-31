import Combine
import Foundation
import Shared
import SwiftUI

/// 앱 설정(테마+알림) — Compose AppSettingsScreen 미러.
/// 테마는 SGThemeState 직접 소비, 알림(푸시 on/off)은 AppSettingsViewModel(아래 동거).
/// 셸이 풀스크린 push로 표시(Compose 셸 위 풀스크린 오버레이 미러) — 내비바는 루트 스택 몫
struct SGSettingsView: View {
    @ObservedObject var theme: SGThemeState

    // 화면 수명과 같이 간다(push 진입마다 생성·로드 = Compose screenViewModel 미러)
    @StateObject private var viewModel = AppSettingsViewModel()

    @Environment(\.sgColors) private var colors

    var body: some View {
        Form {
            Section(header: Text("테마 무드")) {
                Picker("테마 무드", selection: $theme.mood) {
                    ForEach(SGMood.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.inline)
                .labelsHidden()
            }
            Section(header: Text("화면 모드")) {
                Picker("화면 모드", selection: $theme.nightMode) {
                    ForEach(SGNightMode.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.inline)
                .labelsHidden()
            }
            Section(header: Text("내비게이션 스타일")) {
                Picker("내비게이션 스타일", selection: $theme.navStyle) {
                    ForEach(SGNavStyle.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.inline)
                .labelsHidden()
            }
            Section(
                header: Text("알림"),
                footer: Text("끄면 이 계정의 모든 기기에서 해당 푸시 알림이 오지 않습니다. 앱 안의 알림 목록과 배지는 그대로입니다.")
            ) {
                pushPreferencesRows
            }
        }
        .navigationTitle("앱 설정")
        .navigationBarTitleDisplayMode(.inline)
        // 호출 화면이 투명 바(커버 펼침) 상태로 push해도 이 화면은 기본 내비바 — 복귀 시엔 호출 화면이 재적용
        .navigationBarScrim(visible: true)
        // Form의 accentColor가 Toggle 틴트까지 맞춘다(Compose SwitchDefaults accent 미러)
        .accentColor(colors.accent)
    }

    /// 로드 전엔 스피너, 실패는 문구+재시도, 그 외 토글 2행(+저장 실패 문구) — Compose PushPreferencesSection 미러
    @ViewBuilder private var pushPreferencesRows: some View {
        let uiState = viewModel.uiState
        if let prefs = uiState.pushPreferences {
            Toggle(isOn: Binding(
                get: { prefs.chatEnabled },
                set: { viewModel.onAction(.setChatEnabled($0)) }
            )) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("채팅 알림").foregroundColor(colors.ink)
                    Text("그룹 채팅·DM 메시지 푸시").font(.caption).foregroundColor(colors.inkFaint)
                }
            }
            Toggle(isOn: Binding(
                get: { prefs.activityEnabled },
                set: { viewModel.onAction(.setActivityEnabled($0)) }
            )) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("활동 알림").foregroundColor(colors.ink)
                    Text("댓글·좋아요·공지 등 푸시").font(.caption).foregroundColor(colors.inkFaint)
                }
            }
            if let saveError = uiState.saveError {
                Text(saveError).font(.caption).foregroundColor(colors.rust)
            }
        } else if uiState.isLoading {
            HStack {
                Spacer()
                ProgressView()
                Spacer()
            }
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text(uiState.loadError ?? "알림 설정을 불러오지 못했습니다.").font(.subheadline).foregroundColor(colors.rust)
                Button("다시 시도") { viewModel.onAction(.load) }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
        }
    }
}

/// 앱 설정 — 알림(푸시 on/off) 섹션 상태. composeApp AppSettingsViewModel.kt와 1:1 미러.
/// 테마는 SGThemeState가 그대로 담당하고 이 VM은 서버 저장 설정만 든다.
/// 진입 시 로드, 토글은 낙관적 갱신(즉시 반영 → PUT 실패 시 호출 직전 값으로 롤백+에러 문구). 두 플래그를 항상 함께 보낸다(전체 교체 계약).
/// 별도 파일이 아닌 이유: 설계가 pbxproj 무수정(기존 파일 수정만)으로 확정 — Mac에서 파일을 분리하려면 Xcode로 새 파일 등록이 필요하다.
final class AppSettingsViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    private let getPushPreferencesUseCase: GetPushPreferencesUseCase

    private let updatePushPreferencesUseCase: UpdatePushPreferencesUseCase

    func onAction(_ action: Action) {
        switch action {
        case .load:
            load()
        case .setChatEnabled(let enabled):
            save { PushPreferences(chatEnabled: enabled, activityEnabled: $0.activityEnabled) }
        case .setActivityEnabled(let enabled):
            save { PushPreferences(chatEnabled: $0.chatEnabled, activityEnabled: enabled) }
        }
    }

    private func load() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.loadError = nil
        Task { @MainActor in
            do {
                let prefs = try await getPushPreferencesUseCase.invoke()
                uiState.isLoading = false
                uiState.pushPreferences = prefs
            } catch {
                uiState.isLoading = false
                uiState.loadError = error.kotlinMessage(fallback: "알림 설정을 불러오지 못했습니다.")
            }
        }
    }

    // 낙관적 갱신 — 실패 시 호출 직전 값으로 롤백. 로드 전(nil)에는 토글이 없어 도달하지 않는다
    private func save(_ transform: (PushPreferences) -> PushPreferences) {
        guard let previous = uiState.pushPreferences else { return }
        let next = transform(previous)
        uiState.pushPreferences = next
        uiState.saveError = nil
        Task { @MainActor in
            do {
                try await updatePushPreferencesUseCase.invoke(chatEnabled: next.chatEnabled, activityEnabled: next.activityEnabled)
            } catch {
                uiState.pushPreferences = previous
                uiState.saveError = error.kotlinMessage(fallback: "알림 설정을 저장하지 못했습니다.")
            }
        }
    }

    init(
        getPushPreferencesUseCase: GetPushPreferencesUseCase = AppContainer.shared.getPushPreferencesUseCase,
        updatePushPreferencesUseCase: UpdatePushPreferencesUseCase = AppContainer.shared.updatePushPreferencesUseCase
    ) {
        self.getPushPreferencesUseCase = getPushPreferencesUseCase
        self.updatePushPreferencesUseCase = updatePushPreferencesUseCase
        load()
    }

    struct UiState {
        // 로드 전 nil — 화면은 로딩/에러 행만 그린다
        var pushPreferences: PushPreferences? = nil
        var isLoading = false
        var loadError: String? = nil
        var saveError: String? = nil
    }

    enum Action {
        case load
        case setChatEnabled(Bool)
        case setActivityEnabled(Bool)
    }
}
