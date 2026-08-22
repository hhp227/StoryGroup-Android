package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable

/**
 * 피커가 돌려주는 선택 결과 — 이미지는 bytes로(기존 그대로), 동영상은 filePath+메타데이터로 전달한다.
 * 동영상을 bytes로 들지 않는 것은 ⑴ 압축 워커(WorkManager)가 경로 기반이고(Data 10KB 제한)
 * ⑵ 수백 MB 원본을 통째로 메모리에 올리지 않기 위해서다. durationMs/width/height/sizeBytes는
 * VideoCompressionPlanner 판정 입력.
 */
data class PickedImage(
    val bytes: ByteArray,
    val fileName: String,
    val contentType: String,
    val filePath: String? = null,
    val durationMs: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = bytes.size.toLong()
)

/** 무엇을 고르게 할지 — Android PickVisualMedia·iOS PHPicker 모두 이 구분을 네이티브로 갖고 있다 */
enum class PickerMode { Image, Video }

/**
 * 단일 이미지/동영상 피커 — Android는 시스템 포토 피커(권한 불필요), Desktop은 java.awt.FileDialog.
 * 반환된 람다를 호출하면 피커가 열리고, 선택 완료 시 [onPicked]가 호출된다(취소 시 호출 안 됨).
 * 서드파티 의존성 없이 각 플랫폼 네이티브 메커니즘만 사용한다.
 *
 * 이름에 Image가 남아 있는 건 동영상까지 다루게 된 뒤에도 바꾸지 않았기 때문이다 — 리네임하면
 * 양 플랫폼 호출부와 iOS pbxproj 경로까지 함께 움직여야 해서, Mac 검증이 안 되는 환경에서
 * 동영상과 무관한 이유로 iOS 빌드를 흔들게 된다.
 */
@Composable
expect fun rememberImagePickerLauncher(
    // 기본값은 expect 선언에만 둔다(actual에서 반복하면 컴파일 에러) — 덕분에 기존 이미지 호출부는 그대로다
    mode: PickerMode = PickerMode.Image,
    onPicked: (PickedImage) -> Unit
): () -> Unit
