package kr.hhp227.storygroup.shared.domain.model

/**
 * 통합검색 결과 — 웹 /search 5섹션 미러. 섹션당 서버 기본 limit 10, 더보기 없음.
 * 그룹/게시글/파일/메시지는 내가 속한 그룹 범위, users는 같은 그룹 소속만(서버 필터).
 * users는 친구 탭과 같은 UserSearchResult — 친구 추가/해제 유스케이스를 그대로 쓴다.
 */
data class SearchResults(
    val groups: List<GroupSearchHit> = emptyList(),
    val posts: List<PostSearchHit> = emptyList(),
    val files: List<FileSearchHit> = emptyList(),
    val messages: List<MessageSearchHit> = emptyList(),
    val users: List<UserSearchResult> = emptyList()
) {
    val isEmpty: Boolean
        get() = groups.isEmpty() && posts.isEmpty() && files.isEmpty() && messages.isEmpty() && users.isEmpty()
}

data class GroupSearchHit(
    val id: Long,
    val name: String,
    val image: String? = null,
    // Swift에서는 NSObject 충돌로 description_으로 브리징된다
    val description: String? = null
)

/** createdAt은 ISO 문자열 그대로 — 포맷팅은 플랫폼 UI 몫(Post 관용구) */
data class PostSearchHit(
    val id: Long,
    val groupId: Long,
    val groupName: String,
    val authorName: String,
    val text: String,
    val createdAt: String
)

data class FileSearchHit(
    val id: Long,
    val groupId: Long,
    val groupName: String,
    val name: String,
    val url: String,
    val createdAt: String
)

/** DM 메시지는 groupId/groupName null — 채팅방 제목은 authorName 폴백(알려진 한계, 스펙 참조) */
data class MessageSearchHit(
    val id: Long,
    val chatRoomId: Long,
    val groupId: Long? = null,
    val groupName: String? = null,
    val authorName: String,
    val text: String,
    val createdAt: String
)
