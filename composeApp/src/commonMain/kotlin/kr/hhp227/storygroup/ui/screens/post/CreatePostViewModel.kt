package kr.hhp227.storygroup.ui.screens.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.usecase.CreateLoungePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreatePostUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 게시글 작성 — groupId가 null이면 라운지(홈 피드)에 게시한다(웹 메인 피드 폼 미러).
 * 성공은 Event.Created 일회성 발화 — 호출부(App.kt)가 복귀+피드 갱신을 처리한다.
 * iosApp CreatePostViewModel.swift와 1:1 미러
 */
class CreatePostViewModel(
    private val groupId: Long?,
    private val createPostUseCase: CreatePostUseCase,
    private val createLoungePostUseCase: CreateLoungePostUseCase
) : ViewModel(), MviViewModel<CreatePostViewModel.UiState, CreatePostViewModel.Action, CreatePostViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = Channel<Event>(Channel.BUFFERED)
    override val event: Flow<Event> = _event.receiveAsFlow()

    override fun onAction(action: Action) {
        when (action) {
            is Action.Submit -> submit(action.text)
            Action.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun submit(text: String) {
        if (_uiState.value.isLoading) return
        // 첨부 없는 MVP라 본문 필수 — 백엔드의 "본문/첨부 중 하나는 필수" 규칙과 일치
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "내용을 입력해주세요.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                groupId?.let { createPostUseCase(it, text) } ?: createLoungePostUseCase(text)
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                _event.trySend(Event.Created)
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "게시글 작성에 실패했습니다.") }
            }
        }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null
    )

    sealed interface Action {
        data class Submit(val text: String) : Action
        data object ClearError : Action
    }

    sealed interface Event {
        data object Created : Event
    }
}
