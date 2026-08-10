package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Desktop 미지원 — JVM에 동영상 디코더가 없다(재생도 브라우저로 넘기는 플랫폼이라 결이 맞는다).
 * 호출부는 null을 받으면 ▶만 있는 검은 자리로 폴백한다.
 */
@Composable
actual fun rememberVideoFrame(url: String): ImageBitmap? = null
