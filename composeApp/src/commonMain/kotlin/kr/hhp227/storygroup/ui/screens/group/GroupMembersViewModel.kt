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
import kr.hhp227.storygroup.shared.domain.model.GroupInvite
import kr.hhp227.storygroup.shared.domain.model.GroupJoinRequest
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.usecase.ApproveJoinRequestUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateGroupInviteUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetBlockedUsersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupMembersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetJoinRequestsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.OpenDirectRoomUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RejectJoinRequestUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 멤버 탭 — 레거시 MemberFragment의 VM 미러(탭별 VM 분리). 멤버 그리드+차단 필터+
 * 가입 신청 인박스(승인/거절)+초대코드+1:1 DM 열기. 인박스는 모더레이터 전용 API지만
 * role 게이트 없이 항상 시도하고 403은 빈 목록으로 흡수한다(비모더레이터에겐 인박스가
 * 안 그려질 뿐 — 초대코드 버튼 노출만 화면이 상세 VM의 canModerate로 게이트).
 * iosApp GroupMembersViewModel.swift와 1:1 미러
 */
class GroupMembersViewModel(
    val groupId: Long,
    private val getGroupMembersUseCase: GetGroupMembersUseCase,
    private val getJoinRequestsUseCase: GetJoinRequestsUseCase,
    private val approveJoinRequestUseCase: ApproveJoinRequestUseCase,
    private val rejectJoinRequestUseCase: RejectJoinRequestUseCase,
    private val createGroupInviteUseCase: CreateGroupInviteUseCase,
    private val openDirectRoomUseCase: OpenDirectRoomUseCase,
    private val getBlockedUsersUseCase: GetBlockedUsersUseCase,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel(), MviViewModel<GroupMembersViewModel.UiState, GroupMembersViewModel.Action, GroupMembersViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState(myUserId = getCurrentUserIdUseCase()))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            is Action.ApproveJoinRequest -> approveJoinRequest(action.userId)
            is Action.RejectJoinRequest -> rejectJoinRequest(action.userId)
            is Action.CreateInvite -> createInvite(action.maxUses, action.expiresInDays)
            Action.DismissInvite -> _uiState.update { it.copy(createdInvite = null, inviteError = null) }
            is Action.OpenDm -> openDm(action.userId, action.userName)
            Action.DismissDm -> _uiState.update { it.copy(dmError = null) }
        }
    }

    /** 화면 진입 LaunchedEffect가 발화 — 멤버+인박스+차단 목록 로드(재진입 신선화 겸용) */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val members = getGroupMembersUseCase(groupId)
                // 모더레이터 전용 API — 권한이 없으면 403이라 빈 목록으로 흡수(인박스만 안 그려진다)
                val joinRequests = runCatching { getJoinRequestsUseCase(groupId) }.getOrDefault(emptyList())
                // 서버는 멤버 목록에서 차단 사용자를 빼주지 않는다 — 그리드에서 직접 걸러내려고 함께 읽는다.
                // 실패해도 멤버 탭은 그린다(안 걸러진 멤버가 보일 뿐, 다음 Refresh가 따라잡는다)
                val blockedUserIds = runCatching { getBlockedUsersUseCase().map { blocked -> blocked.userId }.toSet() }
                    .getOrDefault(emptySet())

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        members = members,
                        blockedUserIds = blockedUserIds,
                        joinRequests = joinRequests
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "멤버를 불러오지 못했습니다.")
                }
            }
        }
    }

    /** 가입 신청 승인 — 성공 시 인박스에서 제거하고 새 멤버를 목록에 반영한다(웹 handleApprove 미러) */
    private fun approveJoinRequest(userId: Long) {
        if (_uiState.value.processingRequestUserId != null) return

        _uiState.update { it.copy(processingRequestUserId = userId, actionError = null) }
        viewModelScope.launch {
            runCatching { approveJoinRequestUseCase(groupId, userId) }
                .onSuccess {
                    // 승인은 확정됐으므로 멤버 재조회 실패는 무시한다 — 다음 Refresh가 따라잡는다
                    val members = runCatching { getGroupMembersUseCase(groupId) }.getOrNull()
                    _uiState.update {
                        it.copy(
                            processingRequestUserId = null,
                            joinRequests = it.joinRequests.filterNot { request -> request.userId == userId },
                            members = members ?: it.members
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(processingRequestUserId = null, actionError = e.message ?: "가입 승인에 실패했습니다.")
                    }
                }
        }
    }

    /** 가입 신청 거절 — 성공 시 인박스에서만 제거한다(웹 handleReject 미러) */
    private fun rejectJoinRequest(userId: Long) {
        if (_uiState.value.processingRequestUserId != null) return

        _uiState.update { it.copy(processingRequestUserId = userId, actionError = null) }
        viewModelScope.launch {
            runCatching { rejectJoinRequestUseCase(groupId, userId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            processingRequestUserId = null,
                            joinRequests = it.joinRequests.filterNot { request -> request.userId == userId }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(processingRequestUserId = null, actionError = e.message ?: "가입 거절에 실패했습니다.")
                    }
                }
        }
    }

    /** 초대코드 생성(모더레이터 전용) — 성공 시 다이얼로그가 결과(코드) 뷰로 전환된다 */
    private fun createInvite(maxUses: Int?, expiresInDays: Int?) {
        if (_uiState.value.isCreatingInvite) return

        _uiState.update { it.copy(isCreatingInvite = true, inviteError = null) }
        viewModelScope.launch {
            runCatching { createGroupInviteUseCase(groupId, maxUses, expiresInDays) }
                .onSuccess { invite ->
                    _uiState.update { it.copy(isCreatingInvite = false, createdInvite = invite) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isCreatingInvite = false, inviteError = e.message ?: "초대코드 생성에 실패했습니다.")
                    }
                }
        }
    }

    /** 멤버와 1:1 DM 열기 — get-or-create(멱등)라 이미 방이 있으면 그 방으로 간다(웹 handleDm 미러) */
    private fun openDm(userId: Long, userName: String) {
        if (_uiState.value.isOpeningDm) return

        _uiState.update { it.copy(isOpeningDm = true, dmError = null) }
        viewModelScope.launch {
            runCatching { openDirectRoomUseCase(userId) }
                .onSuccess { chatRoomId ->
                    _uiState.update { it.copy(isOpeningDm = false) }
                    // 방 이름은 서버가 "DM" 고정이라 상대 이름을 제목으로 넘긴다(허브와 동일)
                    _event.tryEmit(Event.DmOpened(chatRoomId, userName))
                }
                .onFailure { e ->
                    // 차단 관계(403 BLOCKED) 등 — 다이얼로그 안에 표시된다
                    _uiState.update { it.copy(isOpeningDm = false, dmError = e.message ?: "DM을 열지 못했습니다.") }
                }
        }
    }

    data class UiState(
        // 멤버 그리드에서 본인을 구분(본인은 DM 대상이 아니다) — 세션이 있는 한 null이 아니다
        val myUserId: Long? = null,
        val members: List<GroupMember> = emptyList(),
        // 내가 차단한 사용자 — 서버가 멤버 목록에선 걸러주지 않아 화면이 직접 뺀다
        val blockedUserIds: Set<Long> = emptySet(),
        // 모더레이터에게만 채워진다 — 일반 멤버는 403이 빈 목록으로 흡수돼 인박스가 안 그려진다
        val joinRequests: List<GroupJoinRequest> = emptyList(),
        // 승인/거절 버튼 로딩 표시용 — 동시에 하나만 처리(웹 busyFor 미러)
        val processingRequestUserId: Long? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        // 승인/거절 실패 문구 — 로드 에러(error)와 달리 탭 화면을 대체하지 않는다
        val actionError: String? = null,
        // 초대코드 다이얼로그 전용 — 생성 성공 시 createdInvite가 채워져 결과 뷰로 전환된다
        val createdInvite: GroupInvite? = null,
        val isCreatingInvite: Boolean = false,
        val inviteError: String? = null,
        // DM 확인 다이얼로그 전용 — 실패 문구(차단 관계 등)는 다이얼로그 안에 표시된다
        val isOpeningDm: Boolean = false,
        val dmError: String? = null
    ) {
        /**
         * 멤버 그리드에 그릴 멤버 — 차단한 사용자는 뺀다(차단=내 화면에서 숨김).
         * 탭하면 DM인데 차단하면 DM 자체가 막히므로, 남겨두면 열 수 없는 진입점이 된다.
         */
        val visibleMembers: List<GroupMember> get() = members.filterNot { it.userId in blockedUserIds }
    }

    sealed interface Action {
        data object Refresh : Action
        data class ApproveJoinRequest(val userId: Long) : Action
        data class RejectJoinRequest(val userId: Long) : Action
        data class CreateInvite(val maxUses: Int?, val expiresInDays: Int?) : Action
        data object DismissInvite : Action
        data class OpenDm(val userId: Long, val userName: String) : Action
        data object DismissDm : Action
    }

    sealed interface Event {
        /** DM 방 확보 성공 — 화면이 채팅방(groupId=null)으로 이동한다 */
        data class DmOpened(val chatRoomId: Long, val title: String) : Event
    }
}
