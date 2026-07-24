package kr.hhp227.storygroup.shared.domain.repository

interface MediaRepository {
    /**
     * 이미지 업로드 — POST /api/images(multipart), 공개 URL을 돌려준다.
     * 어느 리소스(프로필/게시글/그룹 커버)든 기존 API 계약(URL 문자열)에 그대로 얹을 수 있는 범용 엔드포인트.
     */
    suspend fun uploadImage(bytes: ByteArray, fileName: String, contentType: String): Result<String>
}
