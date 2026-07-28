package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.ChatAttachment
import kr.hhp227.storygroup.shared.domain.repository.MediaRepository

/** 채팅 첨부 업로드(POST /api/files) — 결과 메타를 SendChatMessageUseCase attachment에 그대로 싣는다 */
class UploadChatFileUseCase(private val mediaRepository: MediaRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(bytes: ByteArray, fileName: String, contentType: String): ChatAttachment =
        mediaRepository.uploadFile(bytes, fileName, contentType).getOrThrow()
}
