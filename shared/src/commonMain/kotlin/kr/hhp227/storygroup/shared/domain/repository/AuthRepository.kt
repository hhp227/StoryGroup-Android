package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.AuthTokens
import kr.hhp227.storygroup.shared.domain.model.User

interface AuthRepository {
    fun isLoggedIn(): Boolean

    suspend fun register(name: String, email: String, password: String): Result<User>

    suspend fun login(email: String, password: String): Result<AuthTokens>

    /** 서버 호출 성패와 무관하게 로컬 세션은 정리된다 */
    suspend fun logout(): Result<Unit>
}
