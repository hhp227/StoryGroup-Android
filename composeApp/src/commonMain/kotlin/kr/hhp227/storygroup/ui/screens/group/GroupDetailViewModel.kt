package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupRole
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupDefaultChatRoomUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel
import org.jetbrains.compose.resources.getString
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.group_load_failed

/**
 * 그룹 상세 화면 수준 VM — 커버(이름/설명/역할)+상단바 채팅 버튼용 기본 방 id만 담당.
 * 탭 상태는 레거시(탭 Fragment마다 VM)처럼 탭별 VM 5개가 각자 소유한다:
 * GroupFeed/GroupAlbum/GroupMembers/GroupEvents/GroupSettingsViewModel.
 * groupId만 받아 스스로 로드한다 — 목록이 페이징이라 스냅샷 lookup 불가, 딥링크에도 대비.
 * iosApp GroupDetailViewModel.swift와 1:1 미러
 */
class GroupDetailViewModel(
    val groupId: Long,
    private val getGroupUseCase: GetGroupUseCase,
    private val getGroupDefaultChatRoomUseCase: GetGroupDefaultChatRoomUseCase
) : ViewModel(), MviViewModel<GroupDetailViewModel.UiState, GroupDetailViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
        }
    }

    /** 상세 진입 시 발화 — VM이 탭 전환에도 유지되므로 재진입 때도 최신화된다 */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val group = getGroupUseCase(groupId)
                // 상단바 채팅 버튼용 기본 방 id — 실패해도 상세는 그린다(버튼만 숨고 다음 Refresh가 따라잡는다)
                val defaultChatRoomId = runCatching { getGroupDefaultChatRoomUseCase(groupId) }.getOrNull()

                _uiState.update {
                    it.copy(isLoading = false, group = group, defaultChatRoomId = defaultChatRoomId)
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: getString(Res.string.group_load_failed))
                }
            }
        }
    }

    data class UiState(
        // 로드 전 null — 화면은 그룹 정보 자리만 비워 두고 커버/피드를 먼저 그린다
        val group: Group? = null,
        // 상단바 채팅 버튼이 여는 기본 채팅방(가장 먼저 생성된 방) — 로드 전/실패 시 null이면 버튼이 숨는다
        val defaultChatRoomId: Long? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    ) {
        // 초대코드 버튼·일정 삭제(모더레이터) 노출 조건 — 웹 lib/roles canModerate 미러(라운지 제외)
        val canModerate: Boolean get() = group != null && !group.isLounge && group.myRole != GroupRole.MEMBER
    }

    sealed interface Action {
        data object Refresh : Action
    }
}
