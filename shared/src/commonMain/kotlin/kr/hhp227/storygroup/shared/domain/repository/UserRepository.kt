package kr.hhp227.storygroup.shared.domain.repository

import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.model.BlockedUser
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.shared.domain.model.PublicProfile

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
     * 사용자 신고 — POST /api/users/{userId}/report. 접수는 앱 운영자의 신고 관리로 간다
     * (게시글 신고는 그룹 모더레이터 신고함으로 가서 접수처가 다르다).
     * 같은 대상에 대기중 신고가 있으면 409 — 서버 메시지를 그대로 보여준다.
     */
    suspend fun reportUser(userId: Long, reason: String? = null): Result<Unit>

    /**
     * 사용자 차단 — POST /api/users/{userId}/block. 차단하면 그 사용자의 게시글·댓글·채팅이
     * 내 화면에서 숨겨지고(단방향), DM은 양방향으로 막힌다. 해제는 설정의 차단 목록에서 한다.
     */
    suspend fun blockUser(userId: Long): Result<Unit>

    /**
     * 사용자를 차단했다는 알림(차단한 userId) — [blockUser] 성공 시 흘린다.
     * 목록 화면이 이걸 받아 자기 PagingData 스냅샷에서 그 작성자의 글만 걷어낸다 —
     * refresh는 첫 페이지부터 전체 재조회라 쌓아둔 페이지와 스크롤 위치를 잃기 때문이다.
     * 값을 보관하지 않는 일회성 신호라, 이후 새로고침에는 서버 필터가 그대로 이긴다.
     */
    val userBlocks: Flow<Long>

    /**
     * 내가 차단한 사용자 목록 — GET /api/users/me/blocks.
     * 서버는 게시글·댓글·채팅만 걸러줄 뿐 그룹 멤버 목록에는 차단 사용자가 그대로 있어서,
     * 멤버를 그리는 화면이 이 목록으로 직접 걸러낸다.
     */
    suspend fun getBlockedUsers(): Result<List<BlockedUser>>

    /**
     * 공개 프로필 — GET /api/users/{userId}. 게시글 작성자·검색·친구 행에서 진입하는 화면용.
     * 404(없는 사용자)는 예외로 떨어져 화면 로드 에러 문구가 된다
     */
    suspend fun getPublicProfile(userId: Long): Result<PublicProfile>
}
