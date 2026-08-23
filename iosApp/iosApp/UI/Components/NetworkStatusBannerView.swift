import SwiftUI

/// 네트워크 연결 배너 — composeApp NetworkStatusBanner.kt와 1:1 미러(오프라인=rust, 복구=moss)
struct NetworkStatusBannerView: View {
    let message: String

    let isConnected: Bool

    @Environment(\.sgColors) private var colors

    private var tint: Color { isConnected ? colors.moss : colors.rust }

    var body: some View {
        Text(message)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(tint.opacity(0.14))
            .background(colors.paper)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .accessibilityLabel(message)
    }
}
