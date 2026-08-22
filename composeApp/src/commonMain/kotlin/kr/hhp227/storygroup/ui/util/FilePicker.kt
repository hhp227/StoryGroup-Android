package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable

/**
 * 피커가 돌려주는 선택 결과 — 일반 파일은 bytes로(전송 시점 업로드), 동영상은 filePath+메타데이터로
 * 전달한다(PickedImage와 같은 이유: 압축 워커가 경로 기반이고 원본을 메모리에 올리지 않는다 — §4-b).
 */
data class PickedFile(
    val bytes: ByteArray,
    val fileName: String,
    val contentType: String,
    val filePath: String? = null,
    val durationMs: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = bytes.size.toLong()
)

/**
 * 단일 파일 피커(MIME 무제한 — 서버 /api/files 계약 미러) — Android는 SAF 문서 피커(권한 불필요),
 * Desktop은 java.awt.FileDialog. 반환된 람다를 호출하면 피커가 열리고, 선택 완료 시 [onPicked]가
 * 호출된다(취소 시 호출 안 됨). 서드파티 의존성 없이 각 플랫폼 네이티브 메커니즘만 사용한다.
 */
@Composable
expect fun rememberFilePickerLauncher(onPicked: (PickedFile) -> Unit): () -> Unit
