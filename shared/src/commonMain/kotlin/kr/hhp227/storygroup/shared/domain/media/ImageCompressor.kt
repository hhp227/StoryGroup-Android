package kr.hhp227.storygroup.shared.domain.media

/** 압축 결과 — contentType이 바뀌면(HEIC→JPEG 등) 업로드 파일명 확장자도 따라 바뀐다 */
class CompressedImage(val bytes: ByteArray, val contentType: String)

/**
 * 이미지 압축 실행기 — 플랫폼이 구현해 DI로 주입한다(Android/Desktop=composeApp, iOS=Swift 구현체).
 * 구현 규약: 디코딩·인코딩 실패 시 예외를 던지지 말고 원본을 그대로 돌려줄 것(§3-3 원본 폴백 —
 * 이미지는 서버 10MB 상한이 여유라 실패보다 원본이 낫다). 판정은 ImageCompressionPlanner를 쓴다.
 */
interface ImageCompressor {
    suspend fun compress(bytes: ByteArray, contentType: String): CompressedImage
}
