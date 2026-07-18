import SwiftUI

/// 피드 API 연동 전 표시용 모델 — Compose FeedPostUiModel 미러
struct FeedPostUiModel: Identifiable {
    let id: Int
    let authorName: String
    let groupName: String?
    let timeAgo: String
    let content: String
    let likeCount: Int
    let commentCount: Int
}

private let samplePosts = [
    FeedPostUiModel(id: 1, authorName: "홍희표", groupName: "우리들의 이야기", timeAgo: "방금", content: "라운지 피드 자리입니다. 홈 피드 API 연동 후 실제 게시글이 표시됩니다.", likeCount: 3, commentCount: 1),
    FeedPostUiModel(id: 2, authorName: "김재환", groupName: "등산 모임", timeAgo: "10분 전", content: "이번 주말 정기 모임 사진 올렸습니다. 앨범에서 확인해주세요!", likeCount: 5, commentCount: 2),
    FeedPostUiModel(id: 3, authorName: "이수진", groupName: nil, timeAgo: "1시간 전", content: "라운지에 처음 글 써봐요. 다들 반갑습니다 :)", likeCount: 8, commentCount: 4)
]

/// 홈(라운지) 피드 — 웹 메인 피드·Compose HomeScreen 미러
struct HomeView: View {
    let colors: SGColors

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                ForEach(samplePosts) { post in
                    FeedPostCard(post: post, colors: colors)
                }
            }
            .padding(16)
        }
        .background(colors.paper)
    }
}

private struct FeedPostCard: View {
    let post: FeedPostUiModel

    let colors: SGColors

    var body: some View {
        SGCard(colors: colors) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    SGAvatar(name: post.authorName, colors: colors)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(post.authorName).font(.subheadline.bold()).foregroundColor(colors.ink)
                        HStack(spacing: 0) {
                            if let groupName = post.groupName {
                                Text(groupName).font(.caption).foregroundColor(colors.accent)
                                Text(" · ").font(.caption).foregroundColor(colors.inkFaint)
                            }
                            Text(post.timeAgo).font(.caption).foregroundColor(colors.inkFaint)
                        }
                    }
                }
                Text(post.content)
                    .font(.subheadline)
                    .foregroundColor(colors.ink)
                HStack(spacing: 16) {
                    Text("좋아요 \(post.likeCount)").font(.caption).foregroundColor(colors.inkSoft)
                    Text("댓글 \(post.commentCount)").font(.caption).foregroundColor(colors.inkSoft)
                }
            }
            .padding(16)
        }
    }
}
