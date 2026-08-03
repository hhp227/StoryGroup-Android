import SwiftUI

/// 앱 설정(테마) — Compose AppSettingsScreen 미러.
/// 셸이 풀스크린 push로 표시(Compose 셸 위 풀스크린 오버레이 미러) — 내비바는 루트 스택 몫
struct SGSettingsView: View {
    @ObservedObject var theme: SGThemeState

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
        }
        .navigationTitle("앱 설정")
        .navigationBarTitleDisplayMode(.inline)
        .accentColor(colors.accent)
    }
}
