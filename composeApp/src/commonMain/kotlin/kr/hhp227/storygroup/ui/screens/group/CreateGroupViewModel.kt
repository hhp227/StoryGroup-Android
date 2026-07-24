package kr.hhp227.storygroup.ui.screens.group

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
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.shared.domain.usecase.CreateGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadImageUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 만들기 — 이름/소개/가입방식+커버 이미지(CreatePost 첨부와 동일하게 선택 즉시 업로드).
 * 성공은 Event.Created 일회성 발화 — 화면이 세션 GroupsViewModel을 갱신하고 뒤로 복귀한다.
 * iosApp CreateGroupViewModel.swift와 1:1 미러
 */
class CreateGroupViewModel(
    private val createGroupUseCase: CreateGroupUseCase,
    private val uploadImageUseCase: UploadImageUseCase
) : ViewModel(), MviViewModel<CreateGroupViewModel.UiState, CreateGroupViewModel.Action, CreateGroupViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            is Action.Submit -> submit(action.name, action.description, action.joinType)
            Action.ClearError -> _uiState.update { it.copy(error = null) }
            is Action.ChangeCoverImage -> changeCoverImage(action.bytes, action.fileName, action.contentType)
        }
    }

    private fun changeCoverImage(bytes: ByteArray, fileName: String, contentType: String) {
        if (_uiState.value.isUploadingImage) return

        _uiState.update { it.copy(isUploadingImage = true, error = null) }
        viewModelScope.launch {
            runCatching { uploadImageUseCase(bytes, fileName, contentType) }
                .onSuccess { url ->
                    _uiState.update { it.copy(isUploadingImage = false, image = url) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isUploadingImage = false, error = e.message ?: "이미지 업로드에 실패했습니다.") }
                }
        }
    }

    private fun submit(name: String, description: String, joinType: GroupJoinType) {
        if (_uiState.value.isSaving) return
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "그룹 이름을 입력해주세요.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                createGroupUseCase(
                    name = name.trim(),
                    description = description.ifBlank { null },
                    image = _uiState.value.image,
                    joinType = joinType
                )
            }.onSuccess { group ->
                _uiState.update { it.copy(isSaving = false) }
                _event.tryEmit(Event.Created(group))
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "그룹 생성에 실패했습니다.") }
            }
        }
    }

    data class UiState(
        val isSaving: Boolean = false,
        val error: String? = null,
        val image: String? = null,
        val isUploadingImage: Boolean = false
    )

    sealed interface Action {
        data class Submit(val name: String, val description: String, val joinType: GroupJoinType) : Action
        data object ClearError : Action
        class ChangeCoverImage(val bytes: ByteArray, val fileName: String, val contentType: String) : Action
    }

    sealed interface Event {
        data class Created(val group: Group) : Event
    }
}
