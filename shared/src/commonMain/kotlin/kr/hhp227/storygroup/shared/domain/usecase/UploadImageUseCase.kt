package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.MediaRepository

/** 이미지 업로드 — 실패는 예외로 던진다(LoginUseCase와 동일한 소비 규약). 결과 URL을 그대로 돌려준다 */
class UploadImageUseCase(private val mediaRepository: MediaRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(bytes: ByteArray, fileName: String, contentType: String): String =
        mediaRepository.uploadImage(bytes, fileName, contentType).getOrThrow()
}
