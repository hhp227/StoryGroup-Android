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
    let createPostUseCase: CreatePostUseCase
    let createLoungePostUseCase: CreateLoungePostUseCase
    let uploadImageUseCase: UploadImageUseCase
    let createGroupUseCase: CreateGroupUseCase
    let getDiscoverGroupsPagingDataUseCase: GetDiscoverGroupsPagingDataUseCase
    let joinGroupUseCase: JoinGroupUseCase
    let joinGroupByCodeUseCase: JoinGroupByCodeUseCase
    let cancelJoinRequestUseCase: CancelJoinRequestUseCase
    let getJoinRequestsUseCase: GetJoinRequestsUseCase
    let approveJoinRequestUseCase: ApproveJoinRequestUseCase
    let rejectJoinRequestUseCase: RejectJoinRequestUseCase
    let createGroupInviteUseCase: CreateGroupInviteUseCase
    let getNotificationsPagingDataUseCase: GetNotificationsPagingDataUseCase
    let getUnreadNotificationCountUseCase: GetUnreadNotificationCountUseCase
    let markNotificationAsReadUseCase: MarkNotificationAsReadUseCase
    let markAllNotificationsAsReadUseCase: MarkAllNotificationsAsReadUseCase

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
        let notificationRepository = NotificationRepositoryImpl(client: client)

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
        createPostUseCase = CreatePostUseCase(postRepository: postRepository)
        createLoungePostUseCase = CreateLoungePostUseCase(postRepository: postRepository)
        uploadImageUseCase = UploadImageUseCase(mediaRepository: mediaRepository)
        createGroupUseCase = CreateGroupUseCase(groupRepository: groupRepository)
        getDiscoverGroupsPagingDataUseCase = GetDiscoverGroupsPagingDataUseCase(groupRepository: groupRepository)
        joinGroupUseCase = JoinGroupUseCase(groupRepository: groupRepository)
        joinGroupByCodeUseCase = JoinGroupByCodeUseCase(groupRepository: groupRepository)
        cancelJoinRequestUseCase = CancelJoinRequestUseCase(groupRepository: groupRepository)
        getJoinRequestsUseCase = GetJoinRequestsUseCase(groupRepository: groupRepository)
        approveJoinRequestUseCase = ApproveJoinRequestUseCase(groupRepository: groupRepository)
        rejectJoinRequestUseCase = RejectJoinRequestUseCase(groupRepository: groupRepository)
        createGroupInviteUseCase = CreateGroupInviteUseCase(groupRepository: groupRepository)
        getNotificationsPagingDataUseCase = GetNotificationsPagingDataUseCase(notificationRepository: notificationRepository)
        getUnreadNotificationCountUseCase = GetUnreadNotificationCountUseCase(notificationRepository: notificationRepository)
        markNotificationAsReadUseCase = MarkNotificationAsReadUseCase(notificationRepository: notificationRepository)
        markAllNotificationsAsReadUseCase = MarkAllNotificationsAsReadUseCase(notificationRepository: notificationRepository)
    }
}
