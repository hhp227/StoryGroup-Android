package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable
import kr.hhp227.storygroup.shared.domain.model.Post

/**
 * 게시글 텍스트 공유 런처 — Android는 ACTION_SEND 공유 시트, Desktop은 클립보드 복사+피드백.
 * 폐쇄형 그룹이라 외부 공개 URL이 없어 텍스트만 내보낸다(FilePicker처럼 서드파티 의존성 없음).
 */
@Composable
expect fun rememberShareLauncher(): (String) -> Unit

/** 공유 본문 — "작성자 — 본문", 본문 없는 첨부 전용 글은 첫 첨부 URL로 대체(서버가 본문/첨부 중 하나를 강제) */
fun postShareText(post: Post): String {
    val body = post.text.ifBlank { (post.imageUrls + post.videoUrls).firstOrNull().orEmpty() }
    return "${post.authorName} — $body"
}
