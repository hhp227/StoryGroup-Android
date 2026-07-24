package kr.hhp227.storygroup.ui.screens.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.usecase.CreateLoungePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreatePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadImageUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 게시글 작성 — groupId가 null이면 라운지(홈 피드)에 게시한다(웹 메인 피드 폼 미러).
 * 이미지는 선택 즉시 업로드해 URL을 UiState에 쌓아두고, 등록 시 함께 전송한다(웹 ImageUploadField 미러).
 * 성공은 Event.Created 일회성 발화 — 호출부(App.kt)가 복귀+피드 갱신을 처리한다.
 * iosApp CreatePostViewModel.swift와 1:1 미러
 */
class CreatePostViewModel(
    private val groupId: Long?,
    private val createPostUseCase: CreatePostUseCase,
    private val createLoungePostUseCase: CreateLoungePostUseCase,
    private val uploadImageUseCase: UploadImageUseCase
) : ViewModel(), MviViewModel<CreatePostViewModel.UiState, CreatePostViewModel.Action, CreatePostViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            is Action.Submit -> submit(action.text)
            Action.ClearError -> _uiState.update { it.copy(error = null) }
            is Action.AddImage -> addImage(action.bytes, action.fileName, action.contentType)
            is Action.RemoveImage -> _uiState.update { it.copy(images = it.images - action.url) }
        }
    }

    private fun addImage(bytes: ByteArray, fileName: String, contentType: String) {
        if (_uiState.value.images.size >= MAX_IMAGES) return

        _uiState.update { it.copy(isUploadingImage = true, error = null) }
        viewModelScope.launch {
            runCatching { uploadImageUseCase(bytes, fileName, contentType) }
                .onSuccess { url ->
                    _uiState.update { it.copy(isUploadingImage = false, images = it.images + url) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isUploadingImage = false, error = e.message ?: "이미지 업로드에 실패했습니다.") }
                }
        }
    }

    private fun submit(text: String) {
        if (_uiState.value.isLoading) return

        val images = _uiState.value.images
        // 본문/첨부 중 하나는 필수 — 백엔드 규칙과 일치(웹 폼의 required={images.length===0} 미러)
        if (text.isBlank() && images.isEmpty()) {
            _uiState.update { it.copy(error = "내용을 입력하거나 사진을 추가해주세요.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                groupId?.let { createPostUseCase(it, text, images) } ?: createLoungePostUseCase(text, images)
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                _event.tryEmit(Event.Created)
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "게시글 작성에 실패했습니다.") }
            }
        }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val images: List<String> = emptyList(),
        val isUploadingImage: Boolean = false
    )

    sealed interface Action {
        data class Submit(val text: String) : Action
        data object ClearError : Action
        class AddImage(val bytes: ByteArray, val fileName: String, val contentType: String) : Action
        data class RemoveImage(val url: String) : Action
    }

    sealed interface Event {
        data object Created : Event
    }

    companion object {
        // 서버는 개수 제한이 없지만 앱은 카드 레이아웃 감안해 클라 상한을 둔다(화면의 추가 버튼 비활성 조건과 공유)
        const val MAX_IMAGES = 4
    }
}
