package kr.hhp227.storygroup.shared.domain.repository

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
}
