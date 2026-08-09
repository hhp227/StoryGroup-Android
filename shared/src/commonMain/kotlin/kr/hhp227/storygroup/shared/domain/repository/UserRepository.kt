package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.BlockedUser
import kr.hhp227.storygroup.shared.domain.model.Profile

interface UserRepository {
    suspend fun getMyProfile(): Result<Profile>

    /**
     * 프로필 수정 — PATCH /api/users/me(전체 교체 계약: 수정하지 않는 필드도 기존 값을 보내야 유지된다),
     * 수정된 프로필을 돌려준다
     */
    suspend fun updateMyProfile(
        name: String,
        profileImg: String?,
        bio: String?,
        statusMessage: String?
    ): Result<Profile>

    /**
     * 비밀번호 변경 — PATCH /api/users/me/password. 성공 시 서버가 모든 리프레시 토큰을 폐기하므로
     * 현재 액세스 토큰 만료(30분) 후에는 재로그인이 필요하다(다른 기기 세션 차단 목적).
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    /**
     * 사용자 차단 — POST /api/users/{userId}/block. 차단하면 그 사용자의 게시글·댓글·채팅이
     * 내 화면에서 숨겨지고(단방향), DM은 양방향으로 막힌다. 해제는 설정의 차단 목록에서 한다.
     */
    suspend fun blockUser(userId: Long): Result<Unit>

    /**
     * 내가 차단한 사용자 목록 — GET /api/users/me/blocks.
     * 서버는 게시글·댓글·채팅만 걸러줄 뿐 그룹 멤버 목록에는 차단 사용자가 그대로 있어서,
     * 멤버를 그리는 화면이 이 목록으로 직접 걸러낸다.
     */
    suspend fun getBlockedUsers(): Result<List<BlockedUser>>
}
