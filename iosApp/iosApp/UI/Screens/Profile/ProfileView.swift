import SwiftUI
import UIKit
import Shared

/// 프로필 — 내 정보(GET /api/users/me) 헤더 + 메뉴(웹 /settings 허브 대응) — Compose ProfileScreen 미러
struct ProfileView: View {
    /// 약관·정책 — 웹 /terms·/privacy를 외부 브라우저로 연다(설정 허브 "약관 및 정책" 섹션 미러)
    private static let termsUrl = URL(string: AppLinks.shared.TERMS_URL)!

    private static let privacyPolicyUrl = URL(string: AppLinks.shared.PRIVACY_URL)!

    @Environment(\.sgColors) private var colors

    let profile: Profile?

    let onOpenAccountSettings: () -> Void

    let onOpenSettings: () -> Void

    /// 차단 사용자 관리 push — 웹 설정 허브 /settings/blocked 미러(MainShellView로 위임)
    let onOpenBlockedUsers: () -> Void

    let onLogout: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                SGCard {
                    HStack(spacing: 16) {
                        SGAvatar(name: profile?.name ?? "?", size: 64, imageUrl: profile?.profileImg)
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
                        menuRow("person.text.rectangle", "계정 설정", action: onOpenAccountSettings)
                        Divider().background(colors.stoneBorder).padding(.horizontal, 16)
                        menuRow("gearshape.fill", "앱 설정", action: onOpenSettings)
                        Divider().background(colors.stoneBorder).padding(.horizontal, 16)
                        menuRow("nosign", "차단 사용자 관리", action: onOpenBlockedUsers)
                        Divider().background(colors.stoneBorder).padding(.horizontal, 16)
                        menuRow("doc.text", "이용약관") { UIApplication.shared.open(Self.termsUrl) }
                        Divider().background(colors.stoneBorder).padding(.horizontal, 16)
                        menuRow("hand.raised", "개인정보처리방침") { UIApplication.shared.open(Self.privacyPolicyUrl) }
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
