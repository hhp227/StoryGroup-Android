package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * 동영상 첫 프레임 — 썸네일 자리에 깔 포스터다. 아직 못 읽었거나 플랫폼이 디코딩을 못 하면 null.
 *
 * 서버가 포스터를 만들어주지 않아(런타임 이미지에 ffmpeg가 없다) 클라이언트가 직접 뽑는다.
 * 전체 파일을 받지는 않는다 — Android MediaMetadataRetriever는 HTTP range로 헤더와 첫 프레임만 당겨온다.
 * Desktop은 디코더가 없어 항상 null이고 호출부가 검은 자리로 폴백한다(재생도 못 하는 플랫폼이라 결이 맞는다).
 *
 * Coil의 ImageLoader에 끼우지 않고 따로 두는 이유: 싱글턴 ImageLoader 구성을 잘못 건드리면
 * 앱 전체 이미지 로딩이 죽는데, 이 환경에선 런타임으로 확인할 방법이 없다.
 */
@Composable
expect fun rememberVideoFrame(url: String): ImageBitmap?
