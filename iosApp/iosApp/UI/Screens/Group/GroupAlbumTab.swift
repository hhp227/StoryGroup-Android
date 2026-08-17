import Foundation
import SwiftUI
import UIKit
import Paging
import Shared

/// 앨범 탭 — Compose GroupAlbumTab.kt 미러(웹 /groups/[id]/photos·레거시 AlbumFragment).
/// 월별 섹션 + 3열 정사각 그리드, 셀 탭 → 원본 게시글 상세 push(맥락 보존, 라이트박스 없음).
/// 섹션 계산은 peek(로드 트리거 없음), 로드 트리거는 셀의 get이 담당(Compose lazyPagingItems[index] 미러).
struct GroupAlbumTab: View {
    @ObservedObject var photoItems: LazyPagingItems<GroupPhoto>

    let groupId: Int64

    /// 게시글 상세 push의 VM 생성에 쓰인다
    let container: AppContainer

    /// 게시글 상세→작성자 프로필 체인이 쓴다(Task 9 PostDetailView 호출부) — 이 화면은 전달만
    let chatViewModel: ChatViewModel

    let profileViewModel: ProfileViewModel

    @Environment(\.sgColors) private var colors

    private static let columns = [
        GridItem(.flexible(), spacing: 4),
        GridItem(.flexible(), spacing: 4),
        GridItem(.flexible(), spacing: 4)
    ]

    /// 목록이 최신 게시글 순이라 순서대로 끊기만 하면 된다(웹 monthLabel 미러)
    private var sections: [(label: String, indices: [Int])] {
        var result: [(label: String, indices: [Int])] = []

        for index in 0..<photoItems.itemCount {
            guard let photo = photoItems.peek(index) else { continue }
            let label = Self.monthLabel(photo.createdAt)

            if result.last?.label == label {
                result[result.count - 1].indices.append(index)
            } else {
                result.append((label, [index]))
            }
        }
        return result
    }

    var body: some View {
        let refreshState = photoItems.loadState.refresh
        let appendState = photoItems.loadState.append

        VStack(alignment: .leading, spacing: 12) {
            if photoItems.itemCount == 0, refreshState is LoadState.Loading {
                ProgressView().frame(maxWidth: .infinity).padding(.vertical, 48)
            } else if photoItems.itemCount == 0, refreshState is LoadState.Error {
                VStack(spacing: 8) {
                    Text("사진을 불러오지 못했습니다.").font(.subheadline).foregroundColor(colors.rust)
                    Button("다시 시도") { photoItems.retry() }
                        .font(.subheadline)
                        .foregroundColor(colors.accent)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 48)
            } else if photoItems.itemCount == 0 {
                SGEmptyState(title: "아직 사진이 없습니다", subtitle: "게시글에 사진이나 동영상을 올리면 여기에 모여요.")
                    .padding(.vertical, 48)
            } else {
                ForEach(sections, id: \.label) { section in
                    Text(section.label)
                        .font(.subheadline.bold())
                        .foregroundColor(colors.inkSoft)
                    LazyVGrid(columns: Self.columns, spacing: 4) {
                        ForEach(section.indices, id: \.self) { index in
                            cell(index)
                        }
                    }
                }
                SGPagingFooter(
                    error: appendState is LoadState.Error ? "사진을 더 불러오지 못했습니다." : nil,
                    isLoadingMore: appendState is LoadState.Loading,
                    onRetry: { photoItems.retry() }
                )
            }
        }
        .padding(.horizontal, 16)
    }

    private func cell(_ index: Int) -> some View {
        // get 접근이 Paging에 위치 힌트를 줘 다음 페이지를 당긴다 — peek만 쓰면 무한 스크롤이 죽는다
        let photo = photoItems.get(index)
        return NavigationLink {
            if let photo {
                PostDetailView(
                    container: container,
                    groupId: groupId,
                    postId: photo.postId,
                    chatViewModel: chatViewModel,
                    profileViewModel: profileViewModel
                )
            }
        } label: {
            // scaledToFill은 명시 프레임 필수 — GeometryReader로 셀 크기를 고정한다(그룹 그리드 커버 픽스 미러)
            GeometryReader { geometry in
                ZStack {
                    if let photo {
                        AlbumThumb(photo: photo)
                    } else {
                        colors.accentSoft
                    }
                }
                .frame(width: geometry.size.width, height: geometry.size.width)
                .clipped()
                // 크롭 이후에 얹어야 뱃지가 잘리지 않는다 — scaledToFill 이미지는 정사각이 아닌 원본에서
                // 프레임보다 큰 크기를 보고하므로, 크롭 전(AlbumThumb 내부)에 얹으면 잘려나간다
                .overlay(alignment: .bottomTrailing) {
                    if let photo, photo.mediaType == .image, Self.isGif(photo.image) {
                        gifBadge
                    }
                }
            }
            .aspectRatio(1, contentMode: .fit)
        }
        .buttonStyle(.plain)
        .cornerRadius(10)
    }

    /// GIF 뱃지 — 크롭된 정사각 셀 위(cell)에서만 얹는다(AlbumThumb 내부에 두면 오버사이즈 이미지 좌표계라 잘린다)
    private var gifBadge: some View {
        Text("GIF")
            .font(.caption2.bold())
            .foregroundColor(.white)
            .padding(.horizontal, 5)
            .padding(.vertical, 1)
            .background(Color.black.opacity(0.55))
            .cornerRadius(6)
            .padding(6)
    }

    /// URL 저장 규칙상 확장자가 보존되므로 GIF는 경로 끝으로 판별한다(웹 미러)
    private static func isGif(_ image: String) -> Bool {
        image.components(separatedBy: "?")[0].components(separatedBy: "#")[0].lowercased().hasSuffix(".gif")
    }

    /// "2026-08-03T…" → "2026년 8월" — 서버 ISO-8601 원문에서 잘라 만든다(Compose monthLabel 미러)
    private static func monthLabel(_ createdAt: String) -> String {
        let year = createdAt.prefix(4)
        var month = createdAt.dropFirst(5).prefix(2)

        while month.hasPrefix("0") { month = month.dropFirst() }
        return "\(year)년 \(month)월"
    }
}

/// 그리드 정사각 칸 하나 — 사진/동영상을 종류에 맞게 그린다(웹 MediaThumb·Compose AlbumCell 미러).
/// GIF 뱃지는 여기가 아니라 상위 cell(_:)이 크롭 이후에 얹는다(오버사이즈 좌표계 문제, GroupAlbumTab.gifBadge 참고).
/// 동영상: 서버 썸네일이 없어 첫 프레임(VideoPosterLoader)을 포스터로 쓰고, 렌더는 기존 VideoPoster를
/// 재사용한다(SGVideoThumbnail 미러 — 검은 폴백+scaledToFill+반투명 원+▶+clipped를 중복 구현하지 않는다).
/// 캐시 적중분은 init에서 바로 채워 넣어 스크롤 재진입 시 깜빡임이 없다(SGVideoThumbnail 미러).
private struct AlbumThumb: View {
    let photo: GroupPhoto

    @Environment(\.sgColors) private var colors

    @State private var poster: UIImage?

    var body: some View {
        // 이 파일은 SwiftUI(뷰 Group)와 Shared(도메인 모델 Group)를 함께 import해 이름이 충돌한다 —
        // SwiftUI.Group 대신 충돌 없는 ZStack으로 감싼다(GroupDetailView.swift의 동명 충돌 주석 참고)
        ZStack {
            if photo.mediaType == .video {
                VideoPoster(image: poster, badgeSize: 34)
            } else {
                AsyncImage(url: URL(string: photo.image)) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFill()
                    } else {
                        colors.accentSoft
                    }
                }
            }
        }
        .task(id: photo.image) {
            if photo.mediaType == .video, poster == nil {
                poster = await VideoPosterLoader.load(photo.image)
            }
        }
    }

    init(photo: GroupPhoto) {
        self.photo = photo
        _poster = State(initialValue: photo.mediaType == .video ? VideoPosterLoader.cached(photo.image) : nil)
    }
}
