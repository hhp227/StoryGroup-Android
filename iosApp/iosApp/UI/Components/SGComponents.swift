import SwiftUI
import Shared
// Shared에도 Group(도메인 모델)이 있어 동명 충돌 — 이 파일의 Group은 SwiftUI 뷰로 고정
import struct SwiftUI.Group

// 공용 컴포넌트 — Compose ui/components 미러

/// 웹 미러 헤더(linen + 하단 stone-border) — Compose SgTopBar 미러
struct SGHeader: View {
    let title: String
    @Environment(\.sgColors) private var colors
    var leadingIcon: String? = nil
    var onLeading: (() -> Void)? = nil
    var showsNotifications: Bool = false
    var onNotifications: (() -> Void)? = nil
    var showsSettings: Bool = false
    var onSettings: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                if let leadingIcon, let onLeading {
                    Button(action: onLeading) {
                        Image(systemName: leadingIcon).foregroundColor(colors.inkSoft)
                    }
                }
                Text(title)
                    .font(.headline.bold())
                    .foregroundColor(colors.ink)
                Spacer()
                if showsNotifications, let onNotifications {
                    Button(action: onNotifications) {
                        Image(systemName: "bell.fill").foregroundColor(colors.inkSoft)
                    }
                }
                if showsSettings, let onSettings {
                    Button(action: onSettings) {
                        Image(systemName: "gearshape.fill").foregroundColor(colors.inkSoft)
                    }
                }
            }
            .padding(.horizontal, 16)
            .frame(height: 52)
            .background(colors.linen)
            Divider().background(colors.stoneBorder)
        }
    }
}

/// 웹 .card 미러(linen 바탕 + stone 보더) — Compose SgCard 미러
struct SGCard<Content: View>: View {
    @Environment(\.sgColors) private var colors
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: colors.radiusCard)
                    .fill(colors.linen)
                    .overlay(
                        RoundedRectangle(cornerRadius: colors.radiusCard)
                            .stroke(colors.stoneBorder, lineWidth: 1)
                    )
            )
    }
}

/// 웹 .field 미러(라벨 위 배치 + linen 입력창) — Compose SgTextField 미러
struct SGTextField: View {
    let label: String
    @Binding var text: String
    @Environment(\.sgColors) private var colors
    var isSecure: Bool = false
    var keyboard: UIKeyboardType = .default
    var enabled: Bool = true

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.caption.bold())
                .foregroundColor(colors.inkSoft)
            Group {
                if isSecure {
                    SecureField("", text: $text)
                } else {
                    TextField("", text: $text)
                }
            }
            .keyboardType(keyboard)
            .autocapitalization(.none)
            .disableAutocorrection(true)
            .disabled(!enabled)
            .foregroundColor(colors.ink)
            .padding(.horizontal, 12)
            .frame(height: 46)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(colors.linen)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(colors.stoneBorder, lineWidth: 1))
            )
        }
    }
}

/// 웹 .btn-primary 미러(accent 채움, warm=캡슐/vibrant=8pt) — Compose SgPrimaryButton 미러
struct SGPrimaryButton: View {
    let title: String
    @Environment(\.sgColors) private var colors
    var enabled: Bool = true
    var isLoading: Bool = false
    let action: () -> Void

    private var isActive: Bool { enabled && !isLoading }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: colors.inkFaint))
                }
                Text(title).font(.system(size: 16, weight: .bold))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background(
                RoundedRectangle(cornerRadius: colors.radiusButton ?? 24, style: .continuous)
                    .fill(isActive ? colors.accent : colors.accentSoft)
            )
            .foregroundColor(isActive ? colors.onAccent : colors.inkFaint)
        }
        .disabled(!isActive)
    }
}

/// 이니셜 원형 아바타 — Compose SgAvatar 미러
struct SGAvatar: View {
    let name: String
    @Environment(\.sgColors) private var colors
    var size: CGFloat = 40
    var background: Color? = nil
    var foreground: Color? = nil

    var body: some View {
        Circle()
            .fill(background ?? colors.accent)
            .frame(width: size, height: size)
            .overlay(
                Text(String(name.prefix(1)))
                    .font(size >= 56 ? .title2.bold() : .subheadline.bold())
                    .foregroundColor(foreground ?? colors.onAccent)
            )
    }
}

/// 빈 상태 — Compose SgEmptyState 미러
struct SGEmptyState: View {
    let title: String
    let subtitle: String
    @Environment(\.sgColors) private var colors
    var systemImage: String? = nil

    var body: some View {
        VStack(spacing: 8) {
            Spacer()
            if let systemImage {
                Circle()
                    .fill(colors.accentSoft)
                    .frame(width: 64, height: 64)
                    .overlay(
                        Image(systemName: systemImage)
                            .font(.system(size: 24))
                            .foregroundColor(colors.accent)
                    )
                    .padding(.bottom, 8)
            }
            Text(title).font(.title3.bold()).foregroundColor(colors.ink)
            Text(subtitle).font(.subheadline).foregroundColor(colors.inkFaint)
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .background(colors.paper)
    }
}

/// 리스트 구획 라벨 — Compose SgSectionTitle 미러
struct SGSectionTitle: View {
    let text: String
    @Environment(\.sgColors) private var colors

    var body: some View {
        Text(text)
            .font(.caption.bold())
            .foregroundColor(colors.inkSoft)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// 게시글 피드 카드 — 웹 피드 카드·Compose SgPostCard 미러(홈 라운지/그룹 상세 공유).
/// 첨부는 요약 표기(이미지 로딩은 ④ 몫)
struct SGPostCard: View {
    let post: Post

    @Environment(\.sgColors) private var colors

    var body: some View {
        SGCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    SGAvatar(name: post.authorName)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(post.authorName).font(.subheadline.bold()).foregroundColor(colors.ink)
                        Text(TimeFormats.relative(post.createdAt)).font(.caption).foregroundColor(colors.inkFaint)
                    }
                    Spacer()
                    if post.isNotice {
                        Text("공지")
                            .font(.caption2.weight(.medium))
                            .foregroundColor(colors.accent)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(colors.accentSoft)
                            .cornerRadius(colors.radiusButton ?? 12)
                    }
                }
                if !post.text.isEmpty {
                    Text(post.text)
                        .font(.subheadline)
                        .foregroundColor(colors.ink)
                        .lineLimit(6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                if !attachmentSummary.isEmpty {
                    Text(attachmentSummary).font(.caption).foregroundColor(colors.inkSoft)
                }
            }
            .padding(16)
        }
    }

    private var attachmentSummary: String {
        var parts: [String] = []
        if !post.imageUrls.isEmpty { parts.append("사진 \(post.imageUrls.count)장") }
        if !post.videoUrls.isEmpty { parts.append("동영상 \(post.videoUrls.count)개") }
        return parts.joined(separator: " · ")
    }
}

/// 추가 로딩/실패 표시 — Compose SgPagingFooter 미러(실패 시엔 수동 재시도만 노출)
struct SGPagingFooter: View {
    let error: String?
    let isLoadingMore: Bool
    let onRetry: () -> Void

    @Environment(\.sgColors) private var colors

    var body: some View {
        if let error {
            VStack(spacing: 4) {
                Text(error).font(.caption).foregroundColor(colors.rust)
                Button("다시 시도", action: onRetry)
                    .font(.caption)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 8)
        } else if isLoadingMore {
            ProgressView().padding(8)
        }
    }
}
