import SwiftUI
import Shared

/// 프로필 — 내 정보(GET /api/users/me) 헤더 + 메뉴 — Compose ProfileScreen 미러
struct ProfileView: View {
    @Environment(\.sgColors) private var colors

    let profile: Profile?

    let onOpenSettings: () -> Void

    let onLogout: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                SGCard {
                    HStack(spacing: 16) {
                        SGAvatar(name: profile?.name ?? "?", size: 64)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(profile?.name ?? "불러오는 중...").font(.title3.bold()).foregroundColor(colors.ink)
                            Text(profile?.email ?? "").font(.subheadline).foregroundColor(colors.inkSoft)
                            if let statusMessage = profile?.statusMessage {
                                Text(statusMessage).font(.caption).foregroundColor(colors.inkFaint)
                            }
                        }
                        Spacer()
                    }
                    .padding(16)
                }
                SGCard {
                    VStack(spacing: 0) {
                        menuRow("person.text.rectangle", "계정 설정") { /* TODO: 프로필 편집/비밀번호 변경 */ }
                        Divider().background(colors.stoneBorder).padding(.horizontal, 16)
                        menuRow("gearshape.fill", "앱 설정", action: onOpenSettings)
                        Divider().background(colors.stoneBorder).padding(.horizontal, 16)
                        menuRow("rectangle.portrait.and.arrow.right", "로그아웃", tint: colors.rust, action: onLogout)
                    }
                }
            }
            .padding(16)
        }
        .background(colors.paper)
    }

    private func menuRow(_ systemImage: String, _ label: String, tint: Color? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .frame(width: 24)
                    .foregroundColor(tint ?? colors.inkSoft)
                Text(label)
                    .font(.body)
                    .foregroundColor(tint ?? colors.ink)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
    }
}
