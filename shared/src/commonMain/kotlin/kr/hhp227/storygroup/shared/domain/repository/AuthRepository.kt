package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.AuthTokens
import kr.hhp227.storygroup.shared.domain.model.User

interface AuthRepository {
    fun isLoggedIn(): Boolean

    /** 액세스 토큰 JWT sub 클레임(내 userId) — 웹 getUserIdFromToken 미러, 네트워크 없이 디코드 */
    fun currentUserId(): Long?

    suspend fun register(name: String, email: String, password: String): Result<User>

    suspend fun login(email: String, password: String): Result<AuthTokens>

    /** 서버 호출 성패와 무관하게 로컬 세션은 정리된다 */
    suspend fun logout(): Result<Unit>
}
