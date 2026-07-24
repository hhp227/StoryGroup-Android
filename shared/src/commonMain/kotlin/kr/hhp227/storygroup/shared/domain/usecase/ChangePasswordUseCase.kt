package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.UserRepository

/**
 * 비밀번호 변경 — 실패는 예외로 던진다(LoginUseCase와 동일한 소비 규약).
 * 성공 시 서버가 모든 리프레시 토큰을 폐기한다 — 다른 기기는 재로그인 필요(웹 안내 문구 미러).
 */
class ChangePasswordUseCase(private val userRepository: UserRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(currentPassword: String, newPassword: String) {
        userRepository.changePassword(currentPassword, newPassword).getOrThrow()
    }
}
