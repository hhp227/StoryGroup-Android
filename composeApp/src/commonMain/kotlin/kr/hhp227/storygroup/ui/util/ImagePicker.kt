package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable

/** 피커가 돌려주는 선택 결과 — 업로드(UploadImageUseCase/UploadVideoUseCase)에 필요한 최소 정보만 담는다 */
data class PickedImage(val bytes: ByteArray, val fileName: String, val contentType: String)

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
