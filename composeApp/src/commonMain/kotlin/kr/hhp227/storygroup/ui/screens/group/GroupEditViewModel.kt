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
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UpdateGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadImageUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 정보 수정 — 웹 /groups/[id]/settings 폼 미러(설정 탭 "그룹 정보 수정" 행에서 진입하는
 * 풀스크린). 진입 시 GetGroup으로 self-load해 폼을 시드한다.
 * ⚠️PATCH /api/groups/{id}는 name/description/image 전체 교체 계약 — 폼이 로드해 온 값을
 * 항상 실어 보낸다(joinType만 null=유지, 라운지가 이 경로를 쓴다).
 * iosApp GroupEditViewModel.swift와 1:1 미러
 */
class GroupEditViewModel(
    val groupId: Long,
    private val getGroupUseCase: GetGroupUseCase,
    private val updateGroupUseCase: UpdateGroupUseCase,
    private val uploadImageUseCase: UploadImageUseCase
) : ViewModel(), MviViewModel<GroupEditViewModel.UiState, GroupEditViewModel.Action, GroupEditViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            is Action.SetName -> _uiState.update { it.copy(name = action.name.take(100)) }
            is Action.SetDescription -> _uiState.update { it.copy(description = action.description.take(1000)) }
            is Action.SetJoinType -> _uiState.update { it.copy(joinType = action.joinType) }
            is Action.ChangeImage -> changeImage(action.bytes, action.fileName, action.contentType)
            Action.Save -> save()
        }
    }

    /** 진입(init)·재시도 시 발화 — 그룹을 읽어 폼을 시드한다(웹 설정 페이지 getGroup 미러) */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { getGroupUseCase(groupId) }
                .onSuccess { group ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            group = group,
                            name = group.name,
                            description = group.description.orEmpty(),
                            image = group.image,
                            joinType = group.joinType
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "그룹 정보를 불러오지 못했습니다.")
                    }
                }
        }
    }

    /** 대표 이미지 교체 — 계정 설정 아바타 패턴(업로드 성공 시 URL만 폼 상태에 반영, 저장은 별도) */
    private fun changeImage(bytes: ByteArray, fileName: String, contentType: String) {
        if (_uiState.value.isUploadingImage) return

        _uiState.update { it.copy(isUploadingImage = true, saveError = null) }
        viewModelScope.launch {
            runCatching { uploadImageUseCase(bytes, fileName, contentType) }
                .onSuccess { url ->
                    _uiState.update { it.copy(isUploadingImage = false, image = url) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isUploadingImage = false, saveError = e.message ?: "이미지 업로드에 실패했습니다.")
                    }
                }
        }
    }

    /** 저장 — ⚠️전체 교체 계약이라 4필드 전부 전송, 라운지는 joinType을 안 보낸다(null=유지) */
    private fun save() {
        val state = _uiState.value
        val group = state.group ?: return
        if (state.isSaving || state.name.isBlank()) return

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            runCatching {
                updateGroupUseCase(
                    groupId = groupId,
                    name = state.name.trim(),
                    description = state.description.trim().ifBlank { null },
                    image = state.image?.ifBlank { null },
                    joinType = if (group.isLounge) null else state.joinType
                )
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false) }
                // 성공 문구 없이 바로 닫는다 — 화면이 onSaved로 결과 신호+pop을 요청한다
                _event.tryEmit(Event.Saved)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, saveError = e.message ?: "저장에 실패했습니다.") }
            }
        }
    }

    init {
        refresh()
    }

    data class UiState(
        // 로드 원본 — 라운지 분기(가입 방식 숨김·joinType 미전송)의 기준
        val group: Group? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        // 폼 상태 — 로드 성공 시 시드, 이후 사용자 입력이 이긴다(재시도 Refresh는 다시 시드)
        val name: String = "",
        val description: String = "",
        val image: String? = null,
        val joinType: GroupJoinType = GroupJoinType.AUTO_APPROVE,
        val isUploadingImage: Boolean = false,
        val isSaving: Boolean = false,
        val saveError: String? = null
    ) {
        val isLounge: Boolean get() = group?.isLounge == true
    }

    sealed interface Action {
        data object Refresh : Action
        data class SetName(val name: String) : Action
        data class SetDescription(val description: String) : Action
        data class SetJoinType(val joinType: GroupJoinType) : Action
        data class ChangeImage(val bytes: ByteArray, val fileName: String, val contentType: String) : Action
        data object Save : Action
    }

    sealed interface Event {
        /** 저장 성공 — GroupEditScreen이 NavResult.GroupUpdated·GroupsChanged를 publish하고 NavigateBack한다 */
        data object Saved : Event
    }
}
