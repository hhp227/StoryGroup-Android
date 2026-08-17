package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.Friend
import kr.hhp227.storygroup.shared.domain.model.UserSearchResult

interface FriendRepository {
    /**
     * 내 친구 목록 — GET /api/users/me/friends. 페이징 없이 전량 반환, 서버가 이름순 정렬.
     * 친구는 단방향 등록(즐겨찾기 성격, 승인 절차 없음) — 차단(BlockService)과 같은 구조.
     */
    suspend fun getFriends(): Result<List<Friend>>

    /**
     * 친구 등록 — POST /api/users/{userId}/friend. 자기 자신은 400,
     * 이미 등록한 사용자는 409(ALREADY_FRIEND) — 서버 문구를 그대로 보여준다.
     */
    suspend fun addFriend(userId: Long): Result<Unit>

    /** 친구 해제 — DELETE /api/users/{userId}/friend. 등록 내역이 없으면 404 */
    suspend fun removeFriend(userId: Long): Result<Unit>

    /**
     * 사용자 검색 — GET /api/search의 users 섹션만 소비(같은 그룹 소속만 노출, limit은 서버가 1..20 클램프).
     * 응답에 친구 여부 필드가 없어 화면이 친구 목록과 대조해 판정한다(웹 friendIds 미러).
     */
    suspend fun searchUsers(query: String, limit: Int = 20): Result<List<UserSearchResult>>
}
