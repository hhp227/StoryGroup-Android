package kr.hhp227.storygroup.di

import kr.hhp227.storygroup.shared.data.network.createApiClient
import kr.hhp227.storygroup.shared.data.repository.AuthRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.GroupRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.PostRepositoryImpl
import kr.hhp227.storygroup.shared.data.repository.UserRepositoryImpl
import kr.hhp227.storygroup.shared.data.storage.InMemoryKeyValueStorage
import kr.hhp227.storygroup.shared.data.storage.KeyValueStorage
import kr.hhp227.storygroup.shared.data.storage.TokenStorage
import kr.hhp227.storygroup.shared.domain.repository.AuthRepository
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository
import kr.hhp227.storygroup.shared.domain.repository.PostRepository
import kr.hhp227.storygroup.shared.domain.repository.UserRepository
import kr.hhp227.storygroup.shared.domain.usecase.CreateLoungePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreatePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupMembersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPostsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetLoungePostsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyGroupsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyGroupsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetMyProfileUseCase
import kr.hhp227.storygroup.shared.domain.usecase.IsLoggedInUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LoginUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LogoutUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RegisterUseCase

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

    val isLoggedInUseCase = IsLoggedInUseCase(authRepository)
    val loginUseCase = LoginUseCase(authRepository)
    val logoutUseCase = LogoutUseCase(authRepository)
    val registerUseCase = RegisterUseCase(authRepository)
    val getMyProfileUseCase = GetMyProfileUseCase(userRepository)
    val getMyGroupsUseCase = GetMyGroupsUseCase(groupRepository)
    val getMyGroupsPagingDataUseCase = GetMyGroupsPagingDataUseCase(groupRepository)
    val getGroupUseCase = GetGroupUseCase(groupRepository)
    val getGroupMembersUseCase = GetGroupMembersUseCase(groupRepository)
    val getLoungePostsPagingDataUseCase = GetLoungePostsPagingDataUseCase(postRepository)
    val getGroupPostsPagingDataUseCase = GetGroupPostsPagingDataUseCase(postRepository)
    val createPostUseCase = CreatePostUseCase(postRepository)
    val createLoungePostUseCase = CreateLoungePostUseCase(postRepository)
}
