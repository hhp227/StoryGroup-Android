import Foundation
import Shared

/// 수동 DI 컨테이너 — composeApp di/AppContainer.kt 미러 (TokenStorage=UserDefaults, MVP).
/// ViewModel에는 유스케이스만 내려준다.
final class AppContainer {
    let isLoggedInUseCase: IsLoggedInUseCase
    let loginUseCase: LoginUseCase
    let logoutUseCase: LogoutUseCase
    let registerUseCase: RegisterUseCase
    let getMyProfileUseCase: GetMyProfileUseCase
    let updateMyProfileUseCase: UpdateMyProfileUseCase
    let changePasswordUseCase: ChangePasswordUseCase
    let getMyGroupsUseCase: GetMyGroupsUseCase
    let getMyGroupsPagingDataUseCase: GetMyGroupsPagingDataUseCase
    let getGroupUseCase: GetGroupUseCase
    // 설정 탭(Task 11) — 수정(전체 교체 계약)/삭제/나가기
    let updateGroupUseCase: UpdateGroupUseCase
    let deleteGroupUseCase: DeleteGroupUseCase
    let leaveGroupUseCase: LeaveGroupUseCase
    let getGroupMembersUseCase: GetGroupMembersUseCase
    let getLoungePostsPagingDataUseCase: GetLoungePostsPagingDataUseCase
    let getGroupPostsPagingDataUseCase: GetGroupPostsPagingDataUseCase
    let getGroupPhotosPagingDataUseCase: GetGroupPhotosPagingDataUseCase

    // 수정 알림 — 목록이 재조회 없이 그 항목만 갈아끼운다
    let observePostUpdatesUseCase: ObservePostUpdatesUseCase
    let createPostUseCase: CreatePostUseCase

    // 게시글 수정 — 작성 폼이 수정 모드로 재사용한다
    let getPostUseCase: GetPostUseCase

    let updatePostUseCase: UpdatePostUseCase

    // 게시글 상세 — 본문·좋아요·댓글
    let getPostDetailUseCase: GetPostDetailUseCase

    let setPostLikedUseCase: SetPostLikedUseCase

    // 목록 카드용 좋아요 토글 — 상세용 setPostLikedUseCase와 달리 좋아요 목록을 다시 읽지 않는다
    let togglePostLikeUseCase: TogglePostLikeUseCase

    let createCommentUseCase: CreateCommentUseCase

    let deleteCommentUseCase: DeleteCommentUseCase

    let deletePostUseCase: DeletePostUseCase

    // 게시글 상세 더보기 메뉴(남의 글) — 신고는 그룹 신고함, 차단은 내 화면에서 숨김
    let reportPostUseCase: ReportPostUseCase

    // 댓글엔 신고 API가 없어 작성자를 신고한다(접수처는 운영자 — 게시글 신고와 다르다)
    let reportUserUseCase: ReportUserUseCase

    let blockUserUseCase: BlockUserUseCase

    // 멤버 스트립에서 차단 사용자를 걸러내는 데 쓴다(서버는 멤버 목록을 걸러주지 않는다)
    let getBlockedUsersUseCase: GetBlockedUsersUseCase

    // 차단 알림 — 목록이 재조회 없이 그 작성자의 글만 걷어낸다
    let observeUserBlocksUseCase: ObserveUserBlocksUseCase

    // 삭제 알림 — 목록이 재조회 없이 그 글만 걷어낸다
    let observePostDeletionsUseCase: ObservePostDeletionsUseCase
    let createLoungePostUseCase: CreateLoungePostUseCase
    let uploadImageUseCase: UploadImageUseCase

    let uploadVideoUseCase: UploadVideoUseCase
    let createGroupUseCase: CreateGroupUseCase
    let getDiscoverGroupsPagingDataUseCase: GetDiscoverGroupsPagingDataUseCase
    let joinGroupUseCase: JoinGroupUseCase
    let joinGroupByCodeUseCase: JoinGroupByCodeUseCase
    let cancelJoinRequestUseCase: CancelJoinRequestUseCase
    let getMyJoinRequestedGroupsUseCase: GetMyJoinRequestedGroupsUseCase
    let getJoinRequestsUseCase: GetJoinRequestsUseCase
    let approveJoinRequestUseCase: ApproveJoinRequestUseCase
    let rejectJoinRequestUseCase: RejectJoinRequestUseCase
    let createGroupInviteUseCase: CreateGroupInviteUseCase
    let getNotificationsPagingDataUseCase: GetNotificationsPagingDataUseCase
    let getUnreadNotificationCountUseCase: GetUnreadNotificationCountUseCase
    let markNotificationAsReadUseCase: MarkNotificationAsReadUseCase
    let markAllNotificationsAsReadUseCase: MarkAllNotificationsAsReadUseCase
    let observePersonalEventsUseCase: ObservePersonalEventsUseCase
    let getGroupChatRoomsUseCase: GetGroupChatRoomsUseCase
    let getDirectRoomsUseCase: GetDirectRoomsUseCase
    let getChatMessagesUseCase: GetChatMessagesUseCase
    let sendChatMessageUseCase: SendChatMessageUseCase
    let markChatMessagesReadUseCase: MarkChatMessagesReadUseCase
    let observeChatRoomEventsUseCase: ObserveChatRoomEventsUseCase
    let uploadChatFileUseCase: UploadChatFileUseCase
    let sendChatTypingUseCase: SendChatTypingUseCase
    let getChatReadPositionsUseCase: GetChatReadPositionsUseCase
    let openDirectRoomUseCase: OpenDirectRoomUseCase
    let getGroupDefaultChatRoomUseCase: GetGroupDefaultChatRoomUseCase
    let observeRtcCallEventsUseCase: ObserveRtcCallEventsUseCase
    let observeRtcSignalsUseCase: ObserveRtcSignalsUseCase
    let sendRtcSignalUseCase: SendRtcSignalUseCase
    let sendCallInviteUseCase: SendCallInviteUseCase
    let getIceServersUseCase: GetIceServersUseCase
    let getCallRosterUseCase: GetCallRosterUseCase
    let getCurrentUserIdUseCase: GetCurrentUserIdUseCase

    // 일정 탭(Task 10) — 월 범위 목록/단건+참석자/생성/삭제/RSVP/RSVP 취소
    let getGroupEventsUseCase: GetGroupEventsUseCase
    let getEventDetailUseCase: GetEventDetailUseCase
    let createEventUseCase: CreateEventUseCase
    let deleteEventUseCase: DeleteEventUseCase
    let rsvpEventUseCase: RsvpEventUseCase
    let cancelEventRsvpUseCase: CancelEventRsvpUseCase

    init() {
        let tokenStorage = UserDefaultsTokenStorage(defaults: UserDefaults.standard)
        let client = ApiClientKt.createApiClient(
            tokenStorage: tokenStorage,
            baseUrl: StoryGroupApi.shared.DEFAULT_BASE_URL
        )
        let authRepository = AuthRepositoryImpl(client: client, tokenStorage: tokenStorage)
        let userRepository = UserRepositoryImpl(client: client)
        let groupRepository = GroupRepositoryImpl(client: client)
        let postRepository = PostRepositoryImpl(client: client, groupRepository: groupRepository)
        let mediaRepository = MediaRepositoryImpl(client: client)
        // Kotlin 기본 인자(baseUrl)는 ObjC로 내보내지지 않아 명시 전달(createApiClient와 동일)
        let notificationRepository = NotificationRepositoryImpl(
            client: client,
            tokenStorage: tokenStorage,
            baseUrl: StoryGroupApi.shared.DEFAULT_BASE_URL
        )
        let chatRepository = ChatRepositoryImpl(
            client: client,
            tokenStorage: tokenStorage,
            baseUrl: StoryGroupApi.shared.DEFAULT_BASE_URL
        )
        let rtcRepository = RtcRepositoryImpl(
            client: client,
            tokenStorage: tokenStorage,
            baseUrl: StoryGroupApi.shared.DEFAULT_BASE_URL
        )
        let eventRepository = EventRepositoryImpl(client: client)

        isLoggedInUseCase = IsLoggedInUseCase(authRepository: authRepository)
        loginUseCase = LoginUseCase(authRepository: authRepository)
        logoutUseCase = LogoutUseCase(authRepository: authRepository)
        registerUseCase = RegisterUseCase(authRepository: authRepository)
        getMyProfileUseCase = GetMyProfileUseCase(userRepository: userRepository)
        updateMyProfileUseCase = UpdateMyProfileUseCase(userRepository: userRepository)
        changePasswordUseCase = ChangePasswordUseCase(userRepository: userRepository)
        getMyGroupsUseCase = GetMyGroupsUseCase(groupRepository: groupRepository)
        getMyGroupsPagingDataUseCase = GetMyGroupsPagingDataUseCase(groupRepository: groupRepository)
        getGroupUseCase = GetGroupUseCase(groupRepository: groupRepository)
        updateGroupUseCase = UpdateGroupUseCase(groupRepository: groupRepository)
        deleteGroupUseCase = DeleteGroupUseCase(groupRepository: groupRepository)
        leaveGroupUseCase = LeaveGroupUseCase(groupRepository: groupRepository)
        getGroupMembersUseCase = GetGroupMembersUseCase(groupRepository: groupRepository)
        getLoungePostsPagingDataUseCase = GetLoungePostsPagingDataUseCase(postRepository: postRepository)
        getGroupPostsPagingDataUseCase = GetGroupPostsPagingDataUseCase(postRepository: postRepository)
        getGroupPhotosPagingDataUseCase = GetGroupPhotosPagingDataUseCase(groupRepository: groupRepository)
        observePostUpdatesUseCase = ObservePostUpdatesUseCase(postRepository: postRepository)
        createPostUseCase = CreatePostUseCase(postRepository: postRepository)
        getPostUseCase = GetPostUseCase(postRepository: postRepository)
        updatePostUseCase = UpdatePostUseCase(postRepository: postRepository)
        getPostDetailUseCase = GetPostDetailUseCase(postRepository: postRepository)
        setPostLikedUseCase = SetPostLikedUseCase(postRepository: postRepository)
        togglePostLikeUseCase = TogglePostLikeUseCase(postRepository: postRepository)
        createCommentUseCase = CreateCommentUseCase(postRepository: postRepository)
        deleteCommentUseCase = DeleteCommentUseCase(postRepository: postRepository)
        deletePostUseCase = DeletePostUseCase(postRepository: postRepository)
        reportPostUseCase = ReportPostUseCase(postRepository: postRepository)
        reportUserUseCase = ReportUserUseCase(userRepository: userRepository)
        blockUserUseCase = BlockUserUseCase(userRepository: userRepository)
        getBlockedUsersUseCase = GetBlockedUsersUseCase(userRepository: userRepository)
        observeUserBlocksUseCase = ObserveUserBlocksUseCase(userRepository: userRepository)
        observePostDeletionsUseCase = ObservePostDeletionsUseCase(postRepository: postRepository)
        createLoungePostUseCase = CreateLoungePostUseCase(postRepository: postRepository)
        uploadImageUseCase = UploadImageUseCase(mediaRepository: mediaRepository)
        uploadVideoUseCase = UploadVideoUseCase(mediaRepository: mediaRepository)
        createGroupUseCase = CreateGroupUseCase(groupRepository: groupRepository)
        getDiscoverGroupsPagingDataUseCase = GetDiscoverGroupsPagingDataUseCase(groupRepository: groupRepository)
        joinGroupUseCase = JoinGroupUseCase(groupRepository: groupRepository)
        joinGroupByCodeUseCase = JoinGroupByCodeUseCase(groupRepository: groupRepository)
        cancelJoinRequestUseCase = CancelJoinRequestUseCase(groupRepository: groupRepository)
        getMyJoinRequestedGroupsUseCase = GetMyJoinRequestedGroupsUseCase(groupRepository: groupRepository)
        getJoinRequestsUseCase = GetJoinRequestsUseCase(groupRepository: groupRepository)
        approveJoinRequestUseCase = ApproveJoinRequestUseCase(groupRepository: groupRepository)
        rejectJoinRequestUseCase = RejectJoinRequestUseCase(groupRepository: groupRepository)
        createGroupInviteUseCase = CreateGroupInviteUseCase(groupRepository: groupRepository)
        getNotificationsPagingDataUseCase = GetNotificationsPagingDataUseCase(notificationRepository: notificationRepository)
        getUnreadNotificationCountUseCase = GetUnreadNotificationCountUseCase(notificationRepository: notificationRepository)
        markNotificationAsReadUseCase = MarkNotificationAsReadUseCase(notificationRepository: notificationRepository)
        markAllNotificationsAsReadUseCase = MarkAllNotificationsAsReadUseCase(notificationRepository: notificationRepository)
        observePersonalEventsUseCase = ObservePersonalEventsUseCase(notificationRepository: notificationRepository)
        getGroupChatRoomsUseCase = GetGroupChatRoomsUseCase(chatRepository: chatRepository)
        getDirectRoomsUseCase = GetDirectRoomsUseCase(chatRepository: chatRepository)
        getChatMessagesUseCase = GetChatMessagesUseCase(chatRepository: chatRepository)
        sendChatMessageUseCase = SendChatMessageUseCase(chatRepository: chatRepository)
        markChatMessagesReadUseCase = MarkChatMessagesReadUseCase(chatRepository: chatRepository)
        observeChatRoomEventsUseCase = ObserveChatRoomEventsUseCase(chatRepository: chatRepository)
        uploadChatFileUseCase = UploadChatFileUseCase(mediaRepository: mediaRepository)
        sendChatTypingUseCase = SendChatTypingUseCase(chatRepository: chatRepository)
        getChatReadPositionsUseCase = GetChatReadPositionsUseCase(chatRepository: chatRepository)
        openDirectRoomUseCase = OpenDirectRoomUseCase(chatRepository: chatRepository)
        getGroupDefaultChatRoomUseCase = GetGroupDefaultChatRoomUseCase(chatRepository: chatRepository)
        observeRtcCallEventsUseCase = ObserveRtcCallEventsUseCase(rtcRepository: rtcRepository)
        observeRtcSignalsUseCase = ObserveRtcSignalsUseCase(rtcRepository: rtcRepository)
        sendRtcSignalUseCase = SendRtcSignalUseCase(rtcRepository: rtcRepository)
        sendCallInviteUseCase = SendCallInviteUseCase(rtcRepository: rtcRepository)
        getIceServersUseCase = GetIceServersUseCase(rtcRepository: rtcRepository)
        getCallRosterUseCase = GetCallRosterUseCase(rtcRepository: rtcRepository)
        getCurrentUserIdUseCase = GetCurrentUserIdUseCase(authRepository: authRepository)
        getGroupEventsUseCase = GetGroupEventsUseCase(eventRepository: eventRepository)
        getEventDetailUseCase = GetEventDetailUseCase(eventRepository: eventRepository)
        createEventUseCase = CreateEventUseCase(eventRepository: eventRepository)
        deleteEventUseCase = DeleteEventUseCase(eventRepository: eventRepository)
        rsvpEventUseCase = RsvpEventUseCase(eventRepository: eventRepository)
        cancelEventRsvpUseCase = CancelEventRsvpUseCase(eventRepository: eventRepository)
    }
}
