package kr.hhp227.storygroup.di

import kr.hhp227.storygroup.shared.data.network.createApiClient
import kr.hhp227.storygroup.shared.data.repository.AuthRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.ChatRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.GroupRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.MediaRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.MeetingRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.NotificationRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.PostRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.UserRepositoryImpl
import kr.hhp227.storygroup.shared.data.storage.InMemoryKeyValueStorage
import kr.hhp227.storygroup.shared.data.storage.KeyValueStorage
import kr.hhp227.storygroup.shared.data.storage.TokenStorage
import kr.hhp227.storygroup.shared.domain.repository.AuthRepository
import kr.hhp227.storygroup.shared.domain.repository.ChatRepository
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository
import kr.hhp227.storygroup.shared.domain.repository.MediaRepository
import kr.hhp227.storygroup.shared.domain.repository.MeetingRepository
import kr.hhp227.storygroup.shared.domain.repository.NotificationRepository
import kr.hhp227.storygroup.shared.domain.repository.PostRepository
import kr.hhp227.storygroup.shared.domain.repository.UserRepository
import kr.hhp227.storygroup.shared.domain.usecase.ApproveJoinRequestUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CancelJoinRequestUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ChangePasswordUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateGroupInviteUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateLoungePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreatePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.EndMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetChatMessagesUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetChatReadPositionsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetDirectRoomsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetDiscoverGroupsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupChatRoomsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupMeetingsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupMembersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPostsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetJoinRequestsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetLoungePostsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMeetingParticipantsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyGroupsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyGroupsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyProfileUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetNotificationsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetUnreadNotificationCountUseCase
import kr.hhp227.storygroup.shared.domain.usecase.IsLoggedInUseCase
import kr.hhp227.storygroup.shared.domain.usecase.JoinGroupByCodeUseCase
import kr.hhp227.storygroup.shared.domain.usecase.JoinGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.JoinMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LeaveMeetingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LoginUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LogoutUseCase
import kr.hhp227.storygroup.shared.domain.usecase.MarkAllNotificationsAsReadUseCase
import kr.hhp227.storygroup.shared.domain.usecase.MarkChatMessagesReadUseCase
import kr.hhp227.storygroup.shared.domain.usecase.MarkNotificationAsReadUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveChatRoomEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveMeetingCallEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePersonalEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.OpenDirectRoomUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RegisterUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RejectJoinRequestUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendChatMessageUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SendChatTypingUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UpdateMyProfileUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadChatFileUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadImageUseCase

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
    private val meetingRepository: MeetingRepository = MeetingRepositoryImpl(apiClient, tokenStorage)

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
    val createPostUseCase = CreatePostUseCase(postRepository)
    val createLoungePostUseCase = CreateLoungePostUseCase(postRepository)
    val uploadImageUseCase = UploadImageUseCase(mediaRepository)
    val createGroupUseCase = CreateGroupUseCase(groupRepository)
    val getDiscoverGroupsPagingDataUseCase = GetDiscoverGroupsPagingDataUseCase(groupRepository)
    val joinGroupUseCase = JoinGroupUseCase(groupRepository)
    val joinGroupByCodeUseCase = JoinGroupByCodeUseCase(groupRepository)
    val cancelJoinRequestUseCase = CancelJoinRequestUseCase(groupRepository)
    val getJoinRequestsUseCase = GetJoinRequestsUseCase(groupRepository)
    val approveJoinRequestUseCase = ApproveJoinRequestUseCase(groupRepository)
    val rejectJoinRequestUseCase = RejectJoinRequestUseCase(groupRepository)
    val createGroupInviteUseCase = CreateGroupInviteUseCase(groupRepository)
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
    val createMeetingUseCase = CreateMeetingUseCase(meetingRepository)
    val getGroupMeetingsPagingDataUseCase = GetGroupMeetingsPagingDataUseCase(meetingRepository)
    val getMeetingUseCase = GetMeetingUseCase(meetingRepository)
    val joinMeetingUseCase = JoinMeetingUseCase(meetingRepository)
    val leaveMeetingUseCase = LeaveMeetingUseCase(meetingRepository)
    val endMeetingUseCase = EndMeetingUseCase(meetingRepository)
    val getMeetingParticipantsUseCase = GetMeetingParticipantsUseCase(meetingRepository)
    val observeMeetingCallEventsUseCase = ObserveMeetingCallEventsUseCase(meetingRepository)
    val getCurrentUserIdUseCase = GetCurrentUserIdUseCase(authRepository)
}
