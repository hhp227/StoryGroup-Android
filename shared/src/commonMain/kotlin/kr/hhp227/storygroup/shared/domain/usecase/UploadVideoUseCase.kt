package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.MediaRepository

/** 동영상 업로드 — 실패는 예외로 던진다(UploadImageUseCase와 동일한 소비 규약). 결과 URL을 그대로 돌려준다 */
class UploadVideoUseCase(private val mediaRepository: MediaRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(bytes: ByteArray, fileName: String, contentType: String): String =
        mediaRepository.uploadVideo(bytes, fileName, contentType).getOrThrow()
}
