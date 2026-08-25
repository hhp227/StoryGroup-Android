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

/// 입력 바 필드의 최소 높이 — 바 총 높이 = 이 값 + 세로 패딩 2배. Compose ComposerFieldMinHeight 미러
let sgComposerFieldMinHeight: CGFloat = 40

/// 하단 입력 바 전용 슬림 필드 — 레거시 EditText(background="@null") 미러.
///
/// SGTextField는 46pt 고정 높이에 테두리까지 그려 한 줄짜리 댓글/메시지 입력에는 두껍다.
/// 여기서는 테두리도 배경도 없이 바 배경 위에 글자만 얹고, 터치 영역만 40pt로 확보한다.
struct SGComposerField: View {
    let placeholder: String

    @Binding var text: String

    @Environment(\.sgColors) private var colors

    var body: some View {
        TextField(placeholder, text: $text)
            .font(.subheadline)
            .foregroundColor(colors.ink)
            // 세로 여백은 바깥 입력 바가 준다 — 여기는 레거시 paddingStart 5pt 자리만
            .padding(.horizontal, 6)
            .frame(minHeight: sgComposerFieldMinHeight)
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

/// 미디어 그리드에 보여줄 최대 장수 — 넘치면 마지막 타일에 "+N"(전체는 상세에서). Compose MEDIA_GRID_MAX 미러
private let postCardMediaGridMax = 6

/// 게시글 피드 카드 — 웹 피드 카드·Compose SgPostCard 미러(홈 라운지/그룹 상세 공유).
/// 미디어는 카드 전폭 풀블리드 — 1개면 원본 비율 한 장(레거시 iv_post 미러), 2개 이상이면 2열 스태거드 그리드.
/// 동영상은 ▶ 자리로 표시한다(재생은 상세에서)
struct SGPostCard: View {
    let post: Post

    @Environment(\.sgColors) private var colors

    var onToggleLike: () -> Void = {}

    var onShare: () -> Void = {}

    var body: some View {
        SGCard {
            VStack(spacing: 0) {
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
                }
                .padding(16)
                // 미디어는 패딩 밖 카드 전폭 — 레거시 iv_post(match_parent+adjustViewBounds) 풀블리드 미러.
                // 카드 안에서는 재생하지 않는다(카드 전체가 상세로 가는 링크라 탭이 겹친다).
                postMedia
                Divider().background(colors.stoneBorder)
                HStack(spacing: 0) {
                    // 레거시 item_post.xml 미러 — 등분 3버튼. NavigationLink 안이라 borderless로 탭을 분리한다
                    Button(action: onToggleLike) {
                        HStack(spacing: 4) {
                            Image(systemName: post.likedByMe ? "heart.fill" : "heart")
                                .font(.caption)
                            Text(post.likeCount > 0 ? "좋아요 \(post.likeCount)" : "좋아요")
                                .font(.caption)
                        }
                        .foregroundColor(post.likedByMe ? colors.accent : colors.inkSoft)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                    }
                    .buttonStyle(.borderless)
                    Text(post.replyCount > 0 ? "댓글 \(post.replyCount)" : "댓글")
                        .font(.caption)
                        .foregroundColor(colors.inkSoft)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                    Button(action: onShare) {
                        HStack(spacing: 4) {
                            Image(systemName: "square.and.arrow.up").font(.caption)
                            Text("공유").font(.caption)
                        }
                        .foregroundColor(colors.inkSoft)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                    }
                    .buttonStyle(.borderless)
                }
            }
        }
    }

    /// 미디어(이미지 먼저+동영상 뒤) — Compose PostCardMedia 미러
    private var mediaItems: [(url: String, isVideo: Bool)] {
        post.imageUrls.map { ($0, false) } + post.videoUrls.map { ($0, true) }
    }

    /// 카드 전폭 미디어 블록 — 1개=풀블리드 원본 비율, 2~6개=2열 스태거드(타일 간 2).
    /// 크기 메타데이터가 없어 열 배분은 인덱스 교대(0·2·4→왼쪽) — Compose PostCardMediaBlock 미러
    @ViewBuilder private var postMedia: some View {
        let media = mediaItems

        if media.count == 1 {
            mediaTile(media[0], overflowCount: 0)
        } else if media.count >= 2 {
            let visible = Array(media.prefix(postCardMediaGridMax))
            let overflow = media.count - visible.count

            HStack(alignment: .top, spacing: 2) {
                ForEach(0..<2, id: \.self) { column in
                    VStack(spacing: 2) {
                        ForEach(Array(visible.enumerated()), id: \.offset) { pair in
                            if pair.offset % 2 == column {
                                mediaTile(pair.element, overflowCount: pair.offset == visible.count - 1 ? overflow : 0)
                            }
                        }
                    }
                }
            }
        }
    }

    /// 미디어 한 타일 — 폭 맞춤+원본 비율(동영상은 프레임 비율, 없으면 16:9). overflowCount>0이면 "+N" 오버레이
    @ViewBuilder private func mediaTile(_ item: (url: String, isVideo: Bool), overflowCount: Int) -> some View {
        ZStack {
            if item.isVideo {
                SGVideoTile(urlString: item.url)
            } else if let url = URL(string: item.url) {
                AsyncImage(url: url) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFit()
                    } else {
                        // 로드 전 자리 표시 — Color는 고유 크기가 없어 비율을 강제한다
                        colors.linen.aspectRatio(4 / 3, contentMode: .fit)
                    }
                }
            }
            if overflowCount > 0 {
                Color.black.opacity(0.45)
                Text("+\(overflowCount)")
                    .font(.title3.bold())
                    .foregroundColor(.white)
            }
        }
        .frame(maxWidth: .infinity)
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
    ///
    /// 값은 화면(스택에 실린 VC)의 `navigationItem`에 걸린다 — 내비바 자체는 스택 공용이라
    /// 직접 스왑하면 다른 화면 설정을 덮어쓰고, 전환 뒤에 반영돼 진입 순간 깜빡임도 생긴다.
    ///
    /// ⚠️그래서 **내비 스택에 실리는 화면은 전부 이 modifier를 선언**해야 한다. 선언이 없으면
    /// 그 화면만 iOS 기본값(15에서는 스크롤 최상단 투명)으로 떨어진다.
    /// 시트는 자체 UINavigationController를 가지므로 대상이 아니다(공개 프로필 등).
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
    /// 화면에 붙는 순간(didMove)부터 나타나는 시점까지 여러 번 적용을 시도한다 —
    /// push 전환이 바를 그리기 전에 값을 걸어야 진입 첫 프레임부터 이 화면 배경이 나온다.
    final class Helper: UIViewController {
        var visible = false

        /// SwiftUI가 이 자식 VC를 붙이는 순간 — viewWillAppear보다 이를 수 있어 가장 먼저 시도한다
        override func didMove(toParent parent: UIViewController?) {
            super.didMove(toParent: parent)
            applyIfPossible()
        }

        override func viewWillAppear(_ animated: Bool) {
            super.viewWillAppear(animated)
            applyIfPossible()
        }

        /// 전환이 끝난 시점의 보정 — 앞의 두 시점에 아직 스택에 실리지 않았던 경우를 구제한다
        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            applyIfPossible()
        }

        func applyIfPossible() {
            guard let nav = navigationController else { return }

            let appearance = UINavigationBarAppearance()

            if visible {
                appearance.configureWithDefaultBackground()
            } else {
                appearance.configureWithTransparentBackground()
            }

            // 공용 바가 아니라 "내 화면 VC의 navigationItem"에 건다.
            //
            // ⚠️바(bar.standardAppearance)를 직접 스왑하면 두 가지가 따라온다:
            //   1) 스택 공용이라 다른 화면 설정을 덮어쓴다
            //   2) UIKit이 push 전환용으로 바를 구성한 "뒤"에 값이 바뀌어서, 진입 순간
            //      이전 화면의 배경이 한 번 보였다가 투명으로 뒤집힌다(그룹 상세 커버 깜빡임)
            // navigationItem 오버라이드는 UIKit이 화면별로 읽어 전환에 맞춰 적용하므로 둘 다 없다.
            if let owner = owningStackViewController(in: nav) {
                owner.navigationItem.standardAppearance = appearance
                owner.navigationItem.scrollEdgeAppearance = appearance
                owner.navigationItem.compactAppearance = appearance
                return
            }

            // 소유 VC를 못 찾은 경우의 폴백 — 종전대로 공용 바를 직접 스왑한다(최소 동작 보장).
            // 이 경로에선 화면 구분이 안 되므로 덮어쓰기 위험이 남는다
            let bar = nav.navigationBar
            bar.standardAppearance = appearance
            bar.scrollEdgeAppearance = appearance
            bar.compactAppearance = appearance
        }

        /// 이 헬퍼를 품은 "스택에 직접 실린" 조상 VC(= 화면 하나). 못 찾으면 nil
        private func owningStackViewController(in nav: UINavigationController) -> UIViewController? {
            var node: UIViewController? = self

            while let current = node {
                if nav.viewControllers.contains(current) { return current }
                node = current.parent
            }
            return nil
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

/// 공유 본문 — "작성자 — 본문", 본문 없는 첨부 전용 글은 첫 첨부 URL로 대체(Compose postShareText 미러)
func postShareText(_ post: Post) -> String {
    let body = post.text.isEmpty ? ((post.imageUrls.first ?? post.videoUrls.first) ?? "") : post.text
    return "\(post.authorName) — \(body)"
}

/// .sheet(item:)용 래퍼 — String은 Identifiable이 아니라서 감싼다
struct ShareItem: Identifiable {
    let id = UUID()
    let text: String
}

/// iOS 15 타깃이라 ShareLink(iOS 16+) 대신 UIActivityViewController를 그대로 띄운다
struct ActivityShareSheet: UIViewControllerRepresentable {
    let text: String

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [text], applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
