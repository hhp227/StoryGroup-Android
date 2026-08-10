package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.ChatAttachment

interface MediaRepository {
    /**
     * 이미지 업로드 — POST /api/images(multipart), 공개 URL을 돌려준다.
     * 어느 리소스(프로필/게시글/그룹 커버)든 기존 API 계약(URL 문자열)에 그대로 얹을 수 있는 범용 엔드포인트.
     */
    suspend fun uploadImage(bytes: ByteArray, fileName: String, contentType: String): Result<String>

    /**
     * 동영상 업로드 — POST /api/videos(multipart, 서버가 video 타입만 받는다), 공개 URL을 돌려준다.
     * 게시글 videos가 URL 문자열 목록이라 [uploadImage]와 같은 방식으로 얹힌다.
     */
    suspend fun uploadVideo(bytes: ByteArray, fileName: String, contentType: String): Result<String>

    /**
     * 채팅 첨부 업로드 — POST /api/files(multipart, MIME 무제한 20MB).
     * 응답이 첨부 메타(url/name/contentType/size) 그대로라 sendMessage attachment에 바로 싣는다.
     * 이미지도 이 엔드포인트를 쓴다(/api/images는 url만 돌려줘 메타가 유실됨).
     */
    suspend fun uploadFile(bytes: ByteArray, fileName: String, contentType: String): Result<ChatAttachment>
}
