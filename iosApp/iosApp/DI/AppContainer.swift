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
    let getGroupMembersUseCase: GetGroupMembersUseCase
    let getLoungePostsPagingDataUseCase: GetLoungePostsPagingDataUseCase
    let getGroupPostsPagingDataUseCase: GetGroupPostsPagingDataUseCase

    // 수정 알림 — 목록이 재조회 없이 그 항목만 갈아끼운다
    let observePostUpdatesUseCase: ObservePostUpdatesUseCase
    let createPostUseCase: CreatePostUseCase

    // 게시글 수정 — 작성 폼이 수정 모드로 재사용한다
    let getPostUseCase: GetPostUseCase

    let updatePostUseCase: UpdatePostUseCase

    // 게시글 상세 — 본문·좋아요·댓글
    let getPostDetailUseCase: GetPostDetailUseCase

    let setPostLikedUseCase: SetPostLikedUseCase

    let createCommentUseCase: CreateCommentUseCase

    let deleteCommentUseCase: DeleteCommentUseCase

    let deletePostUseCase: DeletePostUseCase

    // 게시글 상세 더보기 메뉴(남의 글) — 신고는 그룹 신고함, 차단은 내 화면에서 숨김
    let reportPostUseCase: ReportPostUseCase

    let blockUserUseCase: BlockUserUseCase

    // 멤버 스트립에서 차단 사용자를 걸러내는 데 쓴다(서버는 멤버 목록을 걸러주지 않는다)
    let getBlockedUsersUseCase: GetBlockedUsersUseCase
    let createLoungePostUseCase: CreateLoungePostUseCase
    let uploadImageUseCase: UploadImageUseCase
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
        getGroupMembersUseCase = GetGroupMembersUseCase(groupRepository: groupRepository)
        getLoungePostsPagingDataUseCase = GetLoungePostsPagingDataUseCase(postRepository: postRepository)
        getGroupPostsPagingDataUseCase = GetGroupPostsPagingDataUseCase(postRepository: postRepository)
        observePostUpdatesUseCase = ObservePostUpdatesUseCase(postRepository: postRepository)
        createPostUseCase = CreatePostUseCase(postRepository: postRepository)
        getPostUseCase = GetPostUseCase(postRepository: postRepository)
        updatePostUseCase = UpdatePostUseCase(postRepository: postRepository)
        getPostDetailUseCase = GetPostDetailUseCase(postRepository: postRepository)
        setPostLikedUseCase = SetPostLikedUseCase(postRepository: postRepository)
        createCommentUseCase = CreateCommentUseCase(postRepository: postRepository)
        deleteCommentUseCase = DeleteCommentUseCase(postRepository: postRepository)
        deletePostUseCase = DeletePostUseCase(postRepository: postRepository)
        reportPostUseCase = ReportPostUseCase(postRepository: postRepository)
        blockUserUseCase = BlockUserUseCase(userRepository: userRepository)
        getBlockedUsersUseCase = GetBlockedUsersUseCase(userRepository: userRepository)
        createLoungePostUseCase = CreateLoungePostUseCase(postRepository: postRepository)
        uploadImageUseCase = UploadImageUseCase(mediaRepository: mediaRepository)
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
    }
}
