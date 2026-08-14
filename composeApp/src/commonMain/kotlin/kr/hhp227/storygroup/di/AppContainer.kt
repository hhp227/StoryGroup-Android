package kr.hhp227.storygroup.di

import kr.hhp227.storygroup.shared.data.network.createApiClient
import kr.hhp227.storygroup.shared.data.repository.AuthRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.ChatRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.EventRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.FriendRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.GroupRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.MediaRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.NotificationRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.PostRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.RtcRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.SearchRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.UserRepositoryImpl
import kr.hhp227.storygroup.shared.data.storage.InMemoryKeyValueStorage
import kr.hhp227.storygroup.shared.data.storage.KeyValueStorage
import kr.hhp227.storygroup.shared.data.storage.TokenStorage
import kr.hhp227.storygroup.shared.domain.repository.AuthRepository
import kr.hhp227.storygroup.shared.domain.repository.ChatRepository
import kr.hhp227.storygroup.shared.domain.repository.EventRepository
import kr.hhp227.storygroup.shared.domain.repository.FriendRepository
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository
import kr.hhp227.storygroup.shared.domain.repository.MediaRepository
import kr.hhp227.storygroup.shared.domain.repository.NotificationRepository
import kr.hhp227.storygroup.shared.domain.repository.PostRepository
import kr.hhp227.storygroup.shared.domain.repository.RtcRepository
import kr.hhp227.storygroup.shared.domain.repository.SearchRepository
import kr.hhp227.storygroup.shared.domain.repository.UserRepository
import kr.hhp227.storygroup.shared.domain.usecase.AddFriendUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ApproveJoinRequestUseCase
import kr.hhp227.storygroup.shared.domain.usecase.BlockUserUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CancelEventRsvpUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CancelJoinRequestUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ChangePasswordUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateCommentUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateEventUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateGroupInviteUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateLoungePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreatePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.DeleteCommentUseCase
import kr.hhp227.storygroup.shared.domain.usecase.DeleteEventUseCase
import kr.hhp227.storygroup.shared.domain.usecase.DeleteGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.DeletePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetPostDetailUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetPostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UpdatePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SetPostLikedUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetBlockedUsersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCallRosterUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetChatMessagesUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetChatReadPositionsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetDirectRoomsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetDiscoverGroupsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetEventDetailUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetFriendsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupChatRoomsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupDefaultChatRoomUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupMembersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPhotosPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetIceServersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPostsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetJoinRequestsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetLoungePostsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyGroupsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyGroupsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyJoinRequestedGroupsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyProfileUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetNotificationsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetUnreadNotificationCountUseCase
import kr.hhp227.storygroup.shared.domain.usecase.IsLoggedInUseCase
import kr.hhp227.storygroup.shared.domain.usecase.JoinGroupByCodeUseCase
import kr.hhp227.storygroup.shared.domain.usecase.JoinGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LeaveGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LoginUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LogoutUseCase
import kr.hhp227.storygroup.shared.domain.usecase.MarkAllNotificationsAsReadUseCase
import kr.hhp227.storygroup.shared.domain.usecase.MarkChatMessagesReadUseCase
import kr.hhp227.storygroup.shared.domain.usecase.MarkNotificationAsReadUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveChatRoomEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePersonalEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePostDeletionsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePostUpdatesUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveUserBlocksUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveRtcCallEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveRtcSignalsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.OpenDirectRoomUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RegisterUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RejectJoinRequestUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RemoveFriendUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ReportPostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ReportUserUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RsvpEventUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SearchUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SearchUsersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendChatMessageUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendChatTypingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendCallInviteUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendRtcSignalUseCase
import kr.hhp227.storygroup.shared.domain.usecase.TogglePostLikeUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UpdateGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UpdateMyProfileUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadChatFileUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadImageUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadVideoUseCase

/**
 * 수동 DI 컨테이너 — 플랫폼 진입점에서 저장소 2종만 주입하면 나머지 의존성이 구성된다.
 * (Android=SharedPreferences, Desktop=파일, Preview=InMemory)
 * ViewModel에는 유스케이스만 내려준다 — iosApp AppContainer.swift 미러.
 */
class AppContainer(
    tokenStorage: TokenStorage,
    val settingsStorage: KeyValueStorage = InMemoryKeyValueStorage()
) {
    private val apiClient = createApiClient(tokenStorage)
    private val authRepository: AuthRepository = AuthRepositoryImpl(apiClient, tokenStorage)
    private val userRepository: UserRepository = UserRepositoryImpl(apiClient)
    private val groupRepository: GroupRepository = GroupRepositoryImpl(apiClient)
    private val postRepository: PostRepository = PostRepositoryImpl(apiClient, groupRepository)
    private val mediaRepository: MediaRepository = MediaRepositoryImpl(apiClient)
    private val notificationRepository: NotificationRepository = NotificationRepositoryImpl(apiClient, tokenStorage)
    private val chatRepository: ChatRepository = ChatRepositoryImpl(apiClient, tokenStorage)
    private val eventRepository: EventRepository = EventRepositoryImpl(apiClient)
    private val friendRepository: FriendRepository = FriendRepositoryImpl(apiClient)
    private val rtcRepository: RtcRepository = RtcRepositoryImpl(apiClient, tokenStorage)
    private val searchRepository: SearchRepository = SearchRepositoryImpl(apiClient)

    val isLoggedInUseCase = IsLoggedInUseCase(authRepository)
    val loginUseCase = LoginUseCase(authRepository)
    val logoutUseCase = LogoutUseCase(authRepository)
    val registerUseCase = RegisterUseCase(authRepository)
    val getMyProfileUseCase = GetMyProfileUseCase(userRepository)
    val updateMyProfileUseCase = UpdateMyProfileUseCase(userRepository)
    val changePasswordUseCase = ChangePasswordUseCase(userRepository)
    val getMyGroupsUseCase = GetMyGroupsUseCase(groupRepository)
    val getMyGroupsPagingDataUseCase = GetMyGroupsPagingDataUseCase(groupRepository)
    val getGroupUseCase = GetGroupUseCase(groupRepository)
    val getGroupMembersUseCase = GetGroupMembersUseCase(groupRepository)
    val getLoungePostsPagingDataUseCase = GetLoungePostsPagingDataUseCase(postRepository)
    val getGroupPostsPagingDataUseCase = GetGroupPostsPagingDataUseCase(postRepository)
    val getGroupPhotosPagingDataUseCase = GetGroupPhotosPagingDataUseCase(groupRepository)
    // 수정 알림 — 목록이 재조회 없이 그 항목만 갈아끼운다
    val observePostUpdatesUseCase = ObservePostUpdatesUseCase(postRepository)
    val createPostUseCase = CreatePostUseCase(postRepository)
    val createLoungePostUseCase = CreateLoungePostUseCase(postRepository)
    val getPostUseCase = GetPostUseCase(postRepository)
    val updatePostUseCase = UpdatePostUseCase(postRepository)
    val getPostDetailUseCase = GetPostDetailUseCase(postRepository)
    val setPostLikedUseCase = SetPostLikedUseCase(postRepository)
    // 목록 카드 좋아요 토글 — 성공 반영은 postUpdates 알림이 담당
    val togglePostLikeUseCase = TogglePostLikeUseCase(postRepository)
    val createCommentUseCase = CreateCommentUseCase(postRepository)
    val deleteCommentUseCase = DeleteCommentUseCase(postRepository)
    val deletePostUseCase = DeletePostUseCase(postRepository)
    // 게시글 상세 더보기 메뉴(남의 글) — 신고는 그룹 신고함, 차단은 내 화면에서 숨김
    val reportPostUseCase = ReportPostUseCase(postRepository)
    // 댓글엔 신고 API가 없어 작성자를 신고한다(접수처는 운영자 — 게시글 신고와 다르다)
    val reportUserUseCase = ReportUserUseCase(userRepository)
    val blockUserUseCase = BlockUserUseCase(userRepository)
    // 멤버 스트립에서 차단 사용자를 걸러내는 데 쓴다(서버는 멤버 목록을 걸러주지 않는다)
    val getBlockedUsersUseCase = GetBlockedUsersUseCase(userRepository)
    // 차단 알림 — 목록이 재조회 없이 그 작성자의 글만 걷어낸다
    val observeUserBlocksUseCase = ObserveUserBlocksUseCase(userRepository)
    // 삭제 알림 — 목록이 재조회 없이 그 글만 걷어낸다
    val observePostDeletionsUseCase = ObservePostDeletionsUseCase(postRepository)
    val uploadImageUseCase = UploadImageUseCase(mediaRepository)
    val uploadVideoUseCase = UploadVideoUseCase(mediaRepository)
    val createGroupUseCase = CreateGroupUseCase(groupRepository)
    val getDiscoverGroupsPagingDataUseCase = GetDiscoverGroupsPagingDataUseCase(groupRepository)
    val joinGroupUseCase = JoinGroupUseCase(groupRepository)
    val joinGroupByCodeUseCase = JoinGroupByCodeUseCase(groupRepository)
    val cancelJoinRequestUseCase = CancelJoinRequestUseCase(groupRepository)
    val getMyJoinRequestedGroupsUseCase = GetMyJoinRequestedGroupsUseCase(groupRepository)
    val getJoinRequestsUseCase = GetJoinRequestsUseCase(groupRepository)
    val approveJoinRequestUseCase = ApproveJoinRequestUseCase(groupRepository)
    val rejectJoinRequestUseCase = RejectJoinRequestUseCase(groupRepository)
    val createGroupInviteUseCase = CreateGroupInviteUseCase(groupRepository)
    val updateGroupUseCase = UpdateGroupUseCase(groupRepository)
    val deleteGroupUseCase = DeleteGroupUseCase(groupRepository)
    val leaveGroupUseCase = LeaveGroupUseCase(groupRepository)
    val getGroupEventsUseCase = GetGroupEventsUseCase(eventRepository)
    val getEventDetailUseCase = GetEventDetailUseCase(eventRepository)
    val createEventUseCase = CreateEventUseCase(eventRepository)
    val deleteEventUseCase = DeleteEventUseCase(eventRepository)
    val rsvpEventUseCase = RsvpEventUseCase(eventRepository)
    val cancelEventRsvpUseCase = CancelEventRsvpUseCase(eventRepository)
    val getNotificationsPagingDataUseCase = GetNotificationsPagingDataUseCase(notificationRepository)
    val getUnreadNotificationCountUseCase = GetUnreadNotificationCountUseCase(notificationRepository)
    val markNotificationAsReadUseCase = MarkNotificationAsReadUseCase(notificationRepository)
    val markAllNotificationsAsReadUseCase = MarkAllNotificationsAsReadUseCase(notificationRepository)
    val observePersonalEventsUseCase = ObservePersonalEventsUseCase(notificationRepository)
    val getGroupChatRoomsUseCase = GetGroupChatRoomsUseCase(chatRepository)
    val getDirectRoomsUseCase = GetDirectRoomsUseCase(chatRepository)
    val getChatMessagesUseCase = GetChatMessagesUseCase(chatRepository)
    val sendChatMessageUseCase = SendChatMessageUseCase(chatRepository)
    val markChatMessagesReadUseCase = MarkChatMessagesReadUseCase(chatRepository)
    val observeChatRoomEventsUseCase = ObserveChatRoomEventsUseCase(chatRepository)
    val uploadChatFileUseCase = UploadChatFileUseCase(mediaRepository)
    val sendChatTypingUseCase = SendChatTypingUseCase(chatRepository)
    val getChatReadPositionsUseCase = GetChatReadPositionsUseCase(chatRepository)
    val openDirectRoomUseCase = OpenDirectRoomUseCase(chatRepository)
    val getGroupDefaultChatRoomUseCase = GetGroupDefaultChatRoomUseCase(chatRepository)
    val observeRtcCallEventsUseCase = ObserveRtcCallEventsUseCase(rtcRepository)
    val observeRtcSignalsUseCase = ObserveRtcSignalsUseCase(rtcRepository)
    val sendRtcSignalUseCase = SendRtcSignalUseCase(rtcRepository)
    val sendCallInviteUseCase = SendCallInviteUseCase(rtcRepository)
    val getCallRosterUseCase = GetCallRosterUseCase(rtcRepository)
    val getIceServersUseCase = GetIceServersUseCase(rtcRepository)
    val getCurrentUserIdUseCase = GetCurrentUserIdUseCase(authRepository)
    // 친구 탭 — 목록/등록/해제(단방향)+사용자 검색(통합검색 users 섹션)
    val getFriendsUseCase = GetFriendsUseCase(friendRepository)
    val addFriendUseCase = AddFriendUseCase(friendRepository)
    val removeFriendUseCase = RemoveFriendUseCase(friendRepository)
    val searchUsersUseCase = SearchUsersUseCase(friendRepository)
    // 홈 통합검색 — 5섹션 전부(친구 탭 searchUsersUseCase는 users 섹션만)
    val searchUseCase = SearchUseCase(searchRepository)
}
