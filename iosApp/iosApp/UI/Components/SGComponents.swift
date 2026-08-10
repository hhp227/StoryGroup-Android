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

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }
}

/// 웹 .field 미러(라벨 위 배치 + linen 입력창) — Compose SgTextField 미러
struct SGTextField: View {
    // 라벨이 nil이면 입력창만 그린다 — Compose SgTextField 미러
    var label: String? = nil

    @Binding var text: String

    @Environment(\.sgColors) private var colors

    var isSecure: Bool = false

    var keyboard: UIKeyboardType = .default

    var enabled: Bool = true

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let label = label {
                Text(label)
                    .font(.caption.bold())
                    .foregroundColor(colors.inkSoft)
            }
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

    /// 있으면 실제 이미지, 없으면 이니셜 원형 — 네이티브 AsyncImage(iOS 15+)
    var imageUrl: String? = nil

    var background: Color? = nil

    var foreground: Color? = nil

    var body: some View {
        Circle()
            .fill(background ?? colors.accent)
            .frame(width: size, height: size)
            .overlay {
                if let imageUrl, let url = URL(string: imageUrl) {
                    // phase만 success로 좁혀 그림 — 로딩/실패 중엔 배경 원(이니셜 대신) 그대로 폴백처럼 보인다
                    AsyncImage(url: url) { phase in
                        if case .success(let image) = phase {
                            image.resizable().scaledToFill()
                        }
                    }
                    .frame(width: size, height: size)
                    .clipShape(Circle())
                } else {
                    Text(String(name.prefix(1)))
                        .font(size >= 56 ? .title2.bold() : .subheadline.bold())
                        .foregroundColor(foreground ?? colors.onAccent)
                }
            }
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

/// 미읽음 수 뱃지 — Compose SgUnreadBadge 미러(0이면 그리지 않고, 99 초과는 "99+")
struct SGUnreadBadge: View {
    let count: Int64

    @Environment(\.sgColors) private var colors

    var body: some View {
        if count > 0 {
            Text(count > 99 ? "99+" : "\(count)")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(colors.onAccent)
                .padding(.horizontal, 5)
                .padding(.vertical, 2)
                .background(Capsule().fill(colors.accent))
        }
    }
}

/// 게시글 피드 카드 — 웹 피드 카드·Compose SgPostCard 미러(홈 라운지/그룹 상세 공유).
/// 첨부는 가로 스크롤 썸네일이고 동영상은 ▶ 자리로 표시한다(재생은 상세에서)
struct SGPostCard: View {
    let post: Post

    @Environment(\.sgColors) private var colors

    var body: some View {
        SGCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    SGAvatar(name: post.authorName, imageUrl: post.authorProfileImg)
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
                // 이미지와 동영상을 한 줄에 이어 붙인다 — 첨부가 섞인 글도 스크롤 한 번으로 훑을 수 있다.
                // 카드 안에서는 재생하지 않는다(카드 전체가 상세로 가는 링크라 탭이 겹친다).
                if !post.imageUrls.isEmpty || !post.videoUrls.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(post.imageUrls, id: \.self) { urlString in
                                if let url = URL(string: urlString) {
                                    AsyncImage(url: url) { phase in
                                        if case .success(let image) = phase {
                                            image.resizable().scaledToFill()
                                        } else {
                                            colors.linen
                                        }
                                    }
                                    .frame(width: 120, height: 120)
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                                }
                            }
                            ForEach(post.videoUrls, id: \.self) { urlString in
                                SGVideoThumbnail(urlString: urlString, size: 120)
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
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

// MARK: - 내비바 스크림 수동 제어

/// 글쓰기 FAB — 레거시 fab(bottom|end, ic_add_white_24dp) 미러, Compose FloatingActionButton 대응
struct SGFab: View {
    @Environment(\.sgColors) private var colors

    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "plus")
                .font(.system(size: 24, weight: .semibold))
                .foregroundColor(colors.onAccent)
                .frame(width: 56, height: 56)
                .background(colors.accent)
                .clipShape(Circle())
                .shadow(color: Color.black.opacity(0.25), radius: 6, x: 0, y: 3)
        }
    }
}

/// 시스템 내비바 배경 표시 여부를 화면 스크롤 상태로 올려보내는 프리퍼런스.
/// keep-alive ZStack에 스크롤뷰가 여러 개라 UIKit의 자동 전환(scrollEdge→standard)이
/// 어느 스크롤뷰를 추적할지 특정하지 못함 — 화면이 직접 임계값을 판정해 알린다.
struct NavigationBarScrimVisibleKey: PreferenceKey {
    static var defaultValue = false

    static func reduce(value: inout Bool, nextValue: () -> Bool) {
        value = value || nextValue()
    }
}

extension View {
    /// 내비바 배경을 명시 제어 — false면 투명(헤더 사진이 비침), true면 기본 머티리얼.
    /// iOS 15/16+ 공통으로 UIKit appearance를 직접 스왑한다.
    func navigationBarScrim(visible: Bool) -> some View {
        background(NavigationBarScrimSetter(visible: visible))
    }
}

private struct NavigationBarScrimSetter: UIViewControllerRepresentable {
    let visible: Bool

    func makeUIViewController(context: Context) -> Helper { Helper() }

    func updateUIViewController(_ helper: Helper, context: Context) {
        helper.visible = visible
        helper.applyIfPossible()
    }

    /// SwiftUI 계층 안에서 부모 UINavigationController에 접근하기 위한 숨은 VC.
    /// push/pop 복귀 시(viewWillAppear) 최신 상태를 다시 적용한다(pushed 화면이 덮어썼을 수 있음).
    final class Helper: UIViewController {
        var visible = false

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            applyIfPossible()
        }

        func applyIfPossible() {
            guard let bar = navigationController?.navigationBar else { return }
            let appearance = UINavigationBarAppearance()

            if visible {
                appearance.configureWithDefaultBackground()
            } else {
                appearance.configureWithTransparentBackground()
            }
            bar.standardAppearance = appearance
            bar.scrollEdgeAppearance = appearance
            bar.compactAppearance = appearance
        }
    }
}

/// 수신 통화 배너(DM·그룹 방) — 셸 위 오버레이로 뜨는 수락/거절 카드
/// (웹 헤더 배너·Compose IncomingCallBanner 미러)
struct SGIncomingCallBanner: View {
    let call: IncomingCallViewModel.IncomingCall

    let onAccept: () -> Void

    let onDecline: () -> Void

    @Environment(\.sgColors) private var colors

    var body: some View {
        SGCard {
            HStack(spacing: 12) {
                SGAvatar(name: call.callerName)
                // 보이스톡이면 문구로 구분 — 수락 시 카메라 OFF 입장과 짝을 이룬다(Compose 미러)
                let kind = call.video ? "통화" : "보이스톡"

                Text(
                    // 그룹 방이면 어느 방의 통화인지 함께 — DM은 발신자 이름만(Compose 미러)
                    call.roomName.map { "\($0) — \(call.callerName)님의 \(kind)" } ?? "\(call.callerName)님의 \(kind)"
                )
                .font(.subheadline.bold())
                .foregroundColor(colors.ink)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
                Button("거절", action: onDecline)
                    .font(.subheadline)
                    .foregroundColor(colors.inkSoft)
                Button(action: onAccept) {
                    Image(systemName: "phone.fill")
                        .font(.system(size: 16))
                        .foregroundColor(colors.onAccent)
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(colors.accent))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }
}
