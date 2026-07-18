import SwiftUI
import Shared

/// 홈(라운지) 피드 — 웹 메인 피드·Compose HomeScreen 미러
struct HomeView: View {
    @ObservedObject var viewModel: HomeViewModel

    @Environment(\.sgColors) private var colors

    var body: some View {
        Group {
            if viewModel.uiState.posts.isEmpty && viewModel.uiState.isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if viewModel.uiState.posts.isEmpty, let error = viewModel.uiState.error {
                VStack(spacing: 8) {
                    Text(error).font(.subheadline).foregroundColor(colors.rust)
                    Button("다시 시도") { viewModel.onAction(.refresh) }
                        .font(.subheadline)
                        .foregroundColor(colors.accent)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if viewModel.uiState.posts.isEmpty {
                SGEmptyState(title: "아직 이야기가 없습니다", subtitle: "첫 이야기를 남겨보세요.")
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(viewModel.uiState.posts, id: \.id) { post in
                            FeedPostCard(post: post)
                                .onAppear {
                                    // 웹 sentinel 미러 — 마지막 카드가 보이면 다음 페이지를 읽는다
                                    if post.id == viewModel.uiState.posts.last?.id { viewModel.onAction(.loadMore) }
                                }
                        }
                        feedFooter
                    }
                    .padding(16)
                }
            }
        }
        .background(colors.paper)
    }

    /// 추가 로딩/실패 표시 — 실패 시엔 수동 재시도만 노출(자동 재시도 루프 방지)
    @ViewBuilder private var feedFooter: some View {
        if let error = viewModel.uiState.error {
            VStack(spacing: 4) {
                Text(error).font(.caption).foregroundColor(colors.rust)
                Button("다시 시도") { viewModel.onAction(.loadMore) }
                    .font(.caption)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 8)
        } else if viewModel.uiState.isLoadingMore {
            ProgressView().padding(8)
        }
    }
}

private struct FeedPostCard: View {
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
                // TODO: 첨부 이미지는 이미지 로딩 도입(다음 단계 ④) 후 실제 렌더링으로 교체
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
