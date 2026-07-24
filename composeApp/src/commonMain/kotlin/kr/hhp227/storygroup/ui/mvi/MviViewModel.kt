package kr.hhp227.storygroup.ui.mvi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * MVI 계약(UiState/Action/Event) — iosApp UI/Mvi/MviViewModel.swift와 1:1 미러.
 * - [uiState]: 화면 상태 단일 스트림(View는 이것만 그린다)
 * - [onAction]: 모든 사용자 액션의 단일 진입점(View → VM)
 * - [event]: 일회성 이벤트(VM → View, 화면 전환·토스트 등) — uiState처럼 백킹 필드 패턴:
 *   _event(MutableSharedFlow, replay 0 + 버퍼 1 DROP_OLDEST — tryEmit 논블로킹, 재구독 시
 *   재발화 없음)를 asSharedFlow로 노출하고, 구독 중에만 전달된다.
 *   이벤트가 없는 VM은 EVENT를 [Nothing]으로 두고 emptyFlow()를 노출한다.
 */
interface MviViewModel<STATE, ACTION, EVENT> {
    val uiState: StateFlow<STATE>

    val event: Flow<EVENT>

    fun onAction(action: ACTION)
}
