package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.AuthTokens
import kr.hhp227.storygroup.shared.domain.model.User

interface AuthRepository {
    fun isLoggedIn(): Boolean

    /** 액세스 토큰 JWT sub 클레임(내 userId) — 웹 getUserIdFromToken 미러, 네트워크 없이 디코드 */
    fun currentUserId(): Long?

    suspend fun register(name: String, email: String, password: String): Result<User>

    suspend fun login(email: String, password: String): Result<AuthTokens>

    /** 구글 ID 토큰으로 로그인(처음 보는 계정이면 서버가 가입까지) — 성공 시 login과 같이 토큰 저장 */
    suspend fun loginWithGoogle(idToken: String): Result<AuthTokens>

    /** Desktop 루프백 PKCE 인가 코드로 로그인 — 코드 교환은 서버 몫 */
    suspend fun loginWithGoogleCode(code: String, codeVerifier: String, redirectUri: String): Result<AuthTokens>

    /** 서버 호출 성패와 무관하게 로컬 세션은 정리된다 */
    suspend fun logout(): Result<Unit>
}
