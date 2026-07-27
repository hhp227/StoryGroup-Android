package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable

/** 피커가 돌려주는 선택 결과 — 채팅 첨부 업로드(UploadChatFileUseCase)에 필요한 최소 정보만 담는다 */
data class PickedFile(val bytes: ByteArray, val fileName: String, val contentType: String)

/**
 * 단일 파일 피커(MIME 무제한 — 서버 /api/files 계약 미러) — Android는 SAF 문서 피커(권한 불필요),
 * Desktop은 java.awt.FileDialog. 반환된 람다를 호출하면 피커가 열리고, 선택 완료 시 [onPicked]가
 * 호출된다(취소 시 호출 안 됨). 서드파티 의존성 없이 각 플랫폼 네이티브 메커니즘만 사용한다.
 */
@Composable
expect fun rememberFilePickerLauncher(onPicked: (PickedFile) -> Unit): () -> Unit
