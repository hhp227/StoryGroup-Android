package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import app.cash.paging.map
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupInvite
import kr.hhp227.storygroup.shared.domain.model.GroupJoinRequest
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.model.GroupRole
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.usecase.ApproveJoinRequestUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateGroupInviteUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetBlockedUsersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupDefaultChatRoomUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupMembersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPostsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetJoinRequestsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePostUpdatesUseCase
import kr.hhp227.storygroup.shared.domain.usecase.OpenDirectRoomUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RejectJoinRequestUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 상세 — 웹 /groups/[id] 미러. 커버+멤버는 UiState 필드, 피드는 UiState에 담기는 최신
 * PagingData(Paging-CRUD 샘플 패턴). groupId만 받아 스스로 로드한다 — 목록이 페이징으로
 * 바뀌어 스냅샷 lookup이 불가하고, 딥링크 진입에도 대비된다(로드 전 group은 null).
 * 피드 갱신은 화면이 Event를 받아 프레젠터 refresh()로 수행한다(홈 피드와 동일 패턴).
 * 모더레이터(방장/부방장)에겐 승인 대기 가입 신청 인박스가 함께 로드된다(웹 GroupMemberList 미러).
 * 멤버 스트립에서 타인을 탭하면 1:1 DM을 연다(웹 GroupMemberList의 DM 액션 미러).
 * 상단바 채팅 버튼용 기본 채팅방 id도 함께 로드한다(레거시 group.xml action_chat·웹 커버 "채팅" 버튼 미러).
 * iosApp GroupDetailViewModel.swift와 1:1 미러
 */
class GroupDetailViewModel(
    val groupId: Long,
    private val getGroupUseCase: GetGroupUseCase,
    private val getGroupMembersUseCase: GetGroupMembersUseCase,
    private val getJoinRequestsUseCase: GetJoinRequestsUseCase,
    private val approveJoinRequestUseCase: ApproveJoinRequestUseCase,
    private val rejectJoinRequestUseCase: RejectJoinRequestUseCase,
    private val createGroupInviteUseCase: CreateGroupInviteUseCase,
    private val openDirectRoomUseCase: OpenDirectRoomUseCase,
    private val getGroupDefaultChatRoomUseCase: GetGroupDefaultChatRoomUseCase,
    private val getBlockedUsersUseCase: GetBlockedUsersUseCase,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase,
    getGroupPostsPagingDataUseCase: GetGroupPostsPagingDataUseCase,
    observePostUpdatesUseCase: ObservePostUpdatesUseCase
) : ViewModel(), MviViewModel<GroupDetailViewModel.UiState, GroupDetailViewModel.Action, GroupDetailViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState(myUserId = getCurrentUserIdUseCase()))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    private fun setPagingData(pagingData: PagingData<Post>) {
        _uiState.update { it.copy(pagingData = pagingData) }
    }

    /**
     * 수정된 게시글을 현재 스냅샷에서 그 항목만 갈아끼운다 — refresh를 태우면 첫 페이지부터
     * 전체 재조회라 이미 쌓아둔 페이지와 스크롤 위치를 잃는다(수정은 목록 구조를 바꾸지 않는다).
     * 다음 세대(새로고침·재진입)부턴 서버 값이 그대로 이긴다.
     */
    private fun applyPostUpdate(post: Post) {
        _uiState.update { state ->
            state.copy(pagingData = state.pagingData.map { if (it.id == post.id) post else it })
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            // 글쓰기 성공 시 발화 — 화면이 refresh()로 피드를 첫 페이지부터 다시 읽는다
            Action.RefreshFeed -> _event.tryEmit(Event.RefreshFeed)
            is Action.ApproveJoinRequest -> approveJoinRequest(action.userId)
            is Action.RejectJoinRequest -> rejectJoinRequest(action.userId)
            is Action.CreateInvite -> createInvite(action.maxUses, action.expiresInDays)
            Action.DismissInvite -> _uiState.update { it.copy(createdInvite = null, inviteError = null) }
            is Action.OpenDm -> openDm(action.userId, action.userName)
            Action.DismissDm -> _uiState.update { it.copy(dmError = null) }
        }
    }

    /** 상세 진입 시 발화 — 그룹+멤버(+모더레이터면 가입 신청) 로드(피드는 Pager가 자체 로드/재시도) */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val group = getGroupUseCase(groupId)
                val members = getGroupMembersUseCase(groupId)
                // 가입 신청 목록은 모더레이터 전용 API — 권한이 있을 때만 조회하고,
                // 실패해도 상세 자체는 그린다(웹 GroupMemberList 미러)
                val joinRequests =
                    if (group.canModerate) runCatching { getJoinRequestsUseCase(groupId) }.getOrDefault(emptyList())
                    else emptyList()
                // 상단바 채팅 버튼용 기본 방 id — 실패해도 상세는 그린다(버튼만 숨고 다음 Refresh가 따라잡는다)
                val defaultChatRoomId = runCatching { getGroupDefaultChatRoomUseCase(groupId) }.getOrNull()
                // 서버는 멤버 목록에서 차단 사용자를 빼주지 않는다 — 스트립에서 직접 걸러내려고 함께 읽는다.
                // 실패해도 상세는 그린다(안 걸러진 멤버가 보일 뿐, 다음 Refresh가 따라잡는다)
                val blockedUserIds = runCatching { getBlockedUsersUseCase().map { blocked -> blocked.userId }.toSet() }
                    .getOrDefault(emptySet())

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        group = group,
                        members = members,
                        blockedUserIds = blockedUserIds,
                        joinRequests = joinRequests,
                        defaultChatRoomId = defaultChatRoomId
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "그룹을 불러오지 못했습니다.")
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

    init {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getGroupPostsPagingDataUseCase(groupId)
            .cachedIn(viewModelScope)
            .onEach(::setPagingData)
            .launchIn(viewModelScope)
        // 상세 화면에서 수정하면 목록도 바뀐 본문을 보여야 한다 — 재조회 대신 그 항목만 교체
        observePostUpdatesUseCase()
            .onEach(::applyPostUpdate)
            .launchIn(viewModelScope)
    }

    data class UiState(
        // 멤버 스트립에서 본인을 구분(본인은 DM 대상이 아니다) — 세션이 있는 한 null이 아니다
        val myUserId: Long? = null,
        // 로드 전 null — 화면은 그룹 정보 자리만 비워 두고 커버/피드를 먼저 그린다
        val group: Group? = null,
        // 상단바 채팅 버튼이 여는 기본 채팅방(가장 먼저 생성된 방) — 로드 전/실패 시 null이면 버튼이 숨는다
        val defaultChatRoomId: Long? = null,
        val pagingData: PagingData<Post> = PagingData.empty(),
        val members: List<GroupMember> = emptyList(),
        // 내가 차단한 사용자 — 서버가 멤버 목록에선 걸러주지 않아 화면이 직접 뺀다
        val blockedUserIds: Set<Long> = emptySet(),
        // 모더레이터에게만 채워진다 — 일반 멤버는 항상 빈 목록이라 인박스가 그려지지 않는다
        val joinRequests: List<GroupJoinRequest> = emptyList(),
        // 승인/거절 버튼 로딩 표시용 — 동시에 하나만 처리(웹 busyFor 미러)
        val processingRequestUserId: Long? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        // 승인/거절 실패 문구 — 로드 에러(error)와 달리 상세 화면을 대체하지 않는다
        val actionError: String? = null,
        // 초대코드 다이얼로그 전용 — 생성 성공 시 createdInvite가 채워져 결과 뷰로 전환된다
        val createdInvite: GroupInvite? = null,
        val isCreatingInvite: Boolean = false,
        val inviteError: String? = null,
        // DM 확인 다이얼로그 전용 — 실패 문구(차단 관계 등)는 다이얼로그 안에 표시된다
        val isOpeningDm: Boolean = false,
        val dmError: String? = null
    ) {
        // 초대코드 만들기 버튼 노출 조건 — 인박스와 동일한 모더레이터 판정
        val canModerate: Boolean get() = group?.canModerate == true

        /**
         * 멤버 스트립에 그릴 멤버 — 차단한 사용자는 뺀다(차단=내 화면에서 숨김).
         * 탭하면 DM인데 차단하면 DM 자체가 막히므로, 남겨두면 열 수 없는 진입점이 된다.
         */
        val visibleMembers: List<GroupMember> get() = members.filterNot { it.userId in blockedUserIds }
    }

    sealed interface Action {
        data object Refresh : Action
        data object RefreshFeed : Action
        data class ApproveJoinRequest(val userId: Long) : Action
        data class RejectJoinRequest(val userId: Long) : Action
        data class CreateInvite(val maxUses: Int?, val expiresInDays: Int?) : Action
        data object DismissInvite : Action
        data class OpenDm(val userId: Long, val userName: String) : Action
        data object DismissDm : Action
    }

    sealed interface Event {
        data object RefreshFeed : Event
        /** DM 방 확보 성공 — 화면이 채팅방(groupId=null)으로 이동한다 */
        data class DmOpened(val chatRoomId: Long, val title: String) : Event
    }
}

// 웹 lib/roles canModerate 미러 — 라운지는 가입 신청 자체가 없어 제외
private val Group.canModerate: Boolean
    get() = !isLounge && myRole != GroupRole.MEMBER
