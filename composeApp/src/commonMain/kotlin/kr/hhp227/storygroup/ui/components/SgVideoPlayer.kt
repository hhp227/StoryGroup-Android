package kr.hhp227.storygroup.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 게시글 첨부 동영상 재생기 — Android는 ExoPlayer(PlayerView), Desktop은 미지원 안내 + 브라우저 열기.
 * 비디오 재생은 플랫폼 네이티브로 둔다는 README 원칙에 따라 여기만 갈라진다(iOS는 SGVideoAttachment.swift).
 *
 * 컴포지션에 들어오는 순간 재생을 시작하고 빠질 때 자원을 해제한다 — 호출부([SgVideoAttachment])가
 * 재생 중인 항목 하나만 이 컴포저블을 그리므로, 다른 항목을 누르면 이전 재생기는 알아서 정리된다.
 */
@Composable
expect fun SgVideoPlayer(url: String, modifier: Modifier = Modifier)
