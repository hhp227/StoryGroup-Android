# 사용자 공개 프로필 화면 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 웹 `/users/[userId]` 미러 공개 프로필 화면(본인=프로필 수정 이동, 타인=1:1 DM+친구 추가/해제)을 만들고 세 진입점(친구 탭 행·검색 users 행·게시글 상세 본문/댓글 작성자)을 연다. 서버 수정 0.

**Architecture:** shared에 PublicProfile 계층(DTO/도메인/UserRepository 확장/UseCase)을 추가, Compose는 `UserProfileRoute` 풀스크린+MVI VM, iOS는 `UserProfileView` 미러(자체 push 2종: 채팅방·계정 설정). 진입점은 기존 콜백 드릴링(친구 탭)·자체 push(검색·게시글 상세) 패턴을 그대로 확장한다.

**Tech Stack:** Kotlin Multiplatform(shared), Compose Multiplatform(M2), SwiftUI(iOS 15 폴백), Ktor client, kotlinx.serialization.

**Spec:** `docs/superpowers/specs/2026-08-14-user-profile-design.md`

## Global Constraints

- **리포**: `/mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android` (이하 경로는 리포 루트 기준). 브랜치는 사용자 결정(제안: feature/userprofile — 홈 검색 커밋 위에서 분기). 시작 전 1회 확인.
- **커밋 금지**: 스테이징+커밋 메시지 전달까지만(사용자가 커밋·push). 각 태스크는 컴파일 검증으로 끝나고, 스테이징은 마지막 태스크에서 실수정 파일만 경로 지정(`git add -A` 금지 — CRLF 노이즈).
- **CRLF**: 수정 파일은 스테이징 전 `git diff <경로>`로 의도 변경만인지 확인, 전 파일 EOL 플립이 보이면 `sed -i 's/\r$//' <경로>` 후 재확인. 신규 파일은 LF로 작성.
- **Gradle**: WSL에 java가 없으면 Windows gradle:
  `cmd.exe /c "cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android && set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& gradlew.bat <task>"`
  WSL·Windows gradle 동시 실행 금지. `assembleDebug`는 반드시 Windows gradle(WSL에 Linux aapt2 없음).
- **Swift/Mac 미검증**: Swift 컴파일 불가 환경 — iOS 태스크는 정적 대조(기존 파일의 실제 시그니처 확인)+pbxproj 정합성 검증까지. Mac 검증은 사용자 몫으로 최종 보고에 명시.
- **iOS 규칙**: VM 프로퍼티 풀네임(`userProfileViewModel`), VM 생성자는 UseCase 주입, KMP suspend는 `Task { @MainActor in }`, 이벤트는 `PassthroughSubject`+`.onReceive`(FriendsViewModel 관용구).
- **문구/UX**: 가입일 표기는 웹 `toLocaleDateString("ko-KR")` 미러 — "YYYY. M. D."(선행 0 없음)+" 가입". 친구 목록 로드 실패 시 친구 버튼 **숨김**(웹 isFriend===null 미러 — 검색 화면의 "기본 친구 추가"와 다름). 신고·차단은 프로필에 없음.

---

### Task 1: shared 공개 프로필 DTO+도메인+Repository+UseCase (TDD)

**Files:**
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/UserDtos.kt` (끝에 DTO 추가)
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/PublicProfile.kt`
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/UserRepository.kt` (메서드 1개 추가)
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/UserRepositoryImpl.kt` (구현+매퍼 추가)
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/GetPublicProfileUseCase.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt` (유스케이스 노출)
- Test: `shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/PublicProfileDtoTest.kt`

**Interfaces:**
- Consumes: 기존 `UserRepository`/`UserRepositoryImpl`(client.get 패턴), 백엔드 계약 `GET /api/users/{userId}` → `{id, name, profileImg?, bio?, statusMessage?, createdAt}`.
- Produces: `PublicProfile(id: Long, name: String, profileImg: String?, bio: String?, statusMessage: String?, createdAt: String)`, `GetPublicProfileUseCase.invoke(userId: Long): PublicProfile`(@Throws suspend), Compose `container.getPublicProfileUseCase` — Task 2의 VM·Task 6의 iOS가 소비. Swift 호출명 `getPublicProfileUseCase.invoke(userId:)`.

- [ ] **Step 1: 실패하는 테스트 작성**

`shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/PublicProfileDtoTest.kt`:

```kotlin
package kr.hhp227.storygroup.shared

import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.dto.PublicProfileResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicProfileDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    // 백엔드 user/dto PublicProfileResponse와 1:1 — 전체 필드
    @Test
    fun decodesFullResponse() {
        val decoded = json.decodeFromString<PublicProfileResponse>(
            """{"id":2,"name":"홍희표","profileImg":"https://x/a.png","bio":"소개글","statusMessage":"상태","createdAt":"2026-07-01T09:00:00+09:00"}"""
        )

        assertEquals(2L, decoded.id)
        assertEquals("홍희표", decoded.name)
        assertEquals("소개글", decoded.bio)
        assertEquals("2026-07-01T09:00:00+09:00", decoded.createdAt)
    }

    // 옵셔널 누락 시 기본값으로 내려앉아야 한다
    @Test
    fun decodesMissingOptionalsToDefaults() {
        val decoded = json.decodeFromString<PublicProfileResponse>(
            """{"id":2,"name":"홍희표","createdAt":"2026-07-01T09:00:00+09:00"}"""
        )

        assertNull(decoded.profileImg)
        assertNull(decoded.bio)
        assertNull(decoded.statusMessage)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: gradle `:shared:jvmTest --tests "kr.hhp227.storygroup.shared.PublicProfileDtoTest"`
Expected: **컴파일 실패** — `PublicProfileResponse` unresolved.

- [ ] **Step 3: DTO+도메인+Repository+UseCase 구현**

`UserDtos.kt` 파일 끝에 추가:

```kotlin
// 공개 프로필 — GET /api/users/{userId}(PublicProfileController) 계약과 1:1.
// 친구 여부는 응답에 없다 — 화면이 친구 목록과 대조한다(웹 미러)
@Serializable
data class PublicProfileResponse(
    val id: Long,
    val name: String,
    val profileImg: String? = null,
    val bio: String? = null,
    val statusMessage: String? = null,
    val createdAt: String = ""
)
```

`shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/PublicProfile.kt`:

```kotlin
package kr.hhp227.storygroup.shared.domain.model

/**
 * 공개 프로필 — GET /api/users/{userId}. 내 프로필(Profile, email·isAdmin 포함)과 화면 계약이 달라 별개 타입.
 * createdAt은 서버 ISO 문자열 그대로 — 표시 포맷팅(가입일)은 플랫폼 UI 몫(Post 관용구)
 */
data class PublicProfile(
    val id: Long,
    val name: String,
    val profileImg: String? = null,
    val bio: String? = null,
    val statusMessage: String? = null,
    val createdAt: String = ""
)
```

`UserRepository.kt` — 인터페이스 끝(`getBlockedUsers` 아래)에 추가:

```kotlin
    /**
     * 공개 프로필 — GET /api/users/{userId}. 게시글 작성자·검색·친구 행에서 진입하는 화면용.
     * 404(없는 사용자)는 예외로 떨어져 화면 로드 에러 문구가 된다
     */
    suspend fun getPublicProfile(userId: Long): Result<PublicProfile>
```

`UserRepositoryImpl.kt` — `getBlockedUsers` 아래에 메서드, 파일 끝 매퍼 블록에 매퍼 추가(임포트 `PublicProfileResponse`·`PublicProfile` 추가):

```kotlin
    override suspend fun getPublicProfile(userId: Long): Result<PublicProfile> =
        runCatching { client.get("/api/users/$userId").body<PublicProfileResponse>().toDomain() }
```

```kotlin
private fun PublicProfileResponse.toDomain() = PublicProfile(
    id = id,
    name = name,
    profileImg = profileImg,
    bio = bio,
    statusMessage = statusMessage,
    createdAt = createdAt
)
```

`shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/GetPublicProfileUseCase.kt`:

```kotlin
package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.PublicProfile
import kr.hhp227.storygroup.shared.domain.repository.UserRepository

/** 공개 프로필 조회 — 프로필 화면 진입 시 1회 로드(친구 여부는 화면이 별도 대조) */
class GetPublicProfileUseCase(
    private val userRepository: UserRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(userId: Long): PublicProfile =
        userRepository.getPublicProfile(userId).getOrThrow()
}
```

`composeApp/.../di/AppContainer.kt` — import `GetPublicProfileUseCase` 추가(알파벳 순), `changePasswordUseCase` 라인 아래에 추가:

```kotlin
    // 공개 프로필 — 게시글 작성자·검색·친구 행에서 진입(웹 /users/[id] 미러)
    val getPublicProfileUseCase = GetPublicProfileUseCase(userRepository)
```

- [ ] **Step 4: 테스트 통과+3타깃 컴파일 확인**

Run: gradle `:shared:jvmTest :composeApp:compileKotlinJvm :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL (PublicProfileDtoTest 2건 포함 전건 통과).

---

### Task 2: Compose 가입일 유틸 + UserProfileViewModel

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/util/TimeFormats.kt` (함수 1개 추가)
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/user/UserProfileViewModel.kt`

**Interfaces:**
- Consumes: Task 1 `GetPublicProfileUseCase`·`PublicProfile`, 기존 `GetFriendsUseCase`/`AddFriendUseCase`/`RemoveFriendUseCase`/`OpenDirectRoomUseCase`/`GetCurrentUserIdUseCase`(동기, `invoke(): Long?`), `MviViewModel` 계약.
- Produces: `formatJoinDate(isoDateTime: String): String`("2026. 7. 1." — " 가입"은 화면이 붙임), `UserProfileViewModel(UiState(profile, isLoading, loadError, isFriend: Boolean?, isBusy, isOpeningDm, actionError, isSelf), Action(Refresh/ToggleFriend/OpenDm/DismissActionError), Event.DmOpened(chatRoomId, title))` — Task 3 화면·Task 6 Swift 미러가 소비.

- [ ] **Step 1: TimeFormats.kt에 가입일 유틸 추가**

파일 끝(`dateOnly` 확장 위)에 추가:

```kotlin
/**
 * 가입일 표기 — 웹 공개 프로필의 toLocaleDateString("ko-KR") 미러("2026. 7. 1." — 선행 0 없음).
 * " 가입" 접미는 화면 몫. iosApp TimeFormats.swift joinDate와 1:1 미러
 */
fun formatJoinDate(isoDateTime: String): String {
    val parts = isoDateTime.substringBefore('T').split('-').mapNotNull { it.toIntOrNull() }

    if (parts.size != 3) return isoDateTime.dateOnly()
    return "${parts[0]}. ${parts[1]}. ${parts[2]}."
}
```

- [ ] **Step 2: UserProfileViewModel 작성**

```kotlin
package kr.hhp227.storygroup.ui.screens.user

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
import kr.hhp227.storygroup.shared.domain.model.PublicProfile
import kr.hhp227.storygroup.shared.domain.usecase.AddFriendUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetFriendsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetPublicProfileUseCase
import kr.hhp227.storygroup.shared.domain.usecase.OpenDirectRoomUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RemoveFriendUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 공개 프로필 — 웹 /users/[userId] 미러. 본인이면 액션이 "프로필 수정"뿐이고,
 * 타인이면 1:1 DM+친구 추가/해제. 친구 여부는 응답에 없어 친구 목록과 대조하며,
 * 목록 로드 실패 시 isFriend=null 유지 → 화면이 친구 버튼을 숨긴다(웹 isFriend===null 미러 —
 * 해제가 오동작하면 안 되므로 검색 화면의 "기본 친구 추가"와 달리 숨김이 맞다).
 * iosApp UserProfileViewModel.swift와 1:1 미러
 */
class UserProfileViewModel(
    private val userId: Long,
    private val getPublicProfileUseCase: GetPublicProfileUseCase,
    private val getFriendsUseCase: GetFriendsUseCase,
    private val addFriendUseCase: AddFriendUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase,
    private val openDirectRoomUseCase: OpenDirectRoomUseCase,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel(), MviViewModel<UserProfileViewModel.UiState, UserProfileViewModel.Action, UserProfileViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState(isSelf = getCurrentUserIdUseCase() == userId))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> load()
            Action.ToggleFriend -> toggleFriend()
            Action.OpenDm -> openDm()
            Action.DismissActionError -> _uiState.update { it.copy(actionError = null) }
        }
    }

    private fun load() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            runCatching { getPublicProfileUseCase(userId) }
                .onSuccess { profile ->
                    _uiState.update { it.copy(isLoading = false, profile = profile) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, loadError = e.message ?: "프로필을 불러오지 못했습니다.") }
                }
        }
    }

    /** 친구 버튼 상태용 — 실패는 조용히 무시하고 isFriend=null 유지(버튼 숨김, 웹 미러) */
    private fun loadFriendState() {
        if (_uiState.value.isSelf) return

        viewModelScope.launch {
            runCatching { getFriendsUseCase() }
                .onSuccess { friends ->
                    _uiState.update { it.copy(isFriend = friends.any { friend -> friend.userId == userId }) }
                }
        }
    }

    /** 친구 토글 — 성공 시 isFriend만 뒤집는다(웹 setIsFriend(!isFriend) 미러). 409 등은 서버 문구 */
    private fun toggleFriend() {
        val isFriend = _uiState.value.isFriend ?: return

        if (_uiState.value.isBusy) return

        _uiState.update { it.copy(isBusy = true, actionError = null) }
        viewModelScope.launch {
            runCatching { if (isFriend) removeFriendUseCase(userId) else addFriendUseCase(userId) }
                .onSuccess {
                    _uiState.update { it.copy(isBusy = false, isFriend = !isFriend) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBusy = false, actionError = e.message ?: "친구 처리에 실패했습니다.") }
                }
        }
    }

    /** 1:1 DM — get-or-create(멱등). 방 이름은 서버가 "DM" 고정이라 상대 이름을 제목으로 넘긴다(친구 탭 미러) */
    private fun openDm() {
        val profile = _uiState.value.profile ?: return

        if (_uiState.value.isOpeningDm) return

        _uiState.update { it.copy(isOpeningDm = true, actionError = null) }
        viewModelScope.launch {
            runCatching { openDirectRoomUseCase(userId) }
                .onSuccess { chatRoomId ->
                    _uiState.update { it.copy(isOpeningDm = false) }
                    _event.tryEmit(Event.DmOpened(chatRoomId, profile.name))
                }
                .onFailure { e ->
                    // 차단 관계(403 BLOCKED) 등 — 인라인 문구로 노출
                    _uiState.update { it.copy(isOpeningDm = false, actionError = e.message ?: "DM을 시작하지 못했습니다.") }
                }
        }
    }

    init {
        load()
        loadFriendState()
    }

    data class UiState(
        val profile: PublicProfile? = null,
        val isLoading: Boolean = false,
        val loadError: String? = null,
        // null=친구 여부 판정 불가(목록 로드 실패) → 화면이 친구 버튼을 숨긴다(웹 미러)
        val isFriend: Boolean? = null,
        // 친구 토글 진행 중 — 버튼 비활성(웹 isBusy 미러)
        val isBusy: Boolean = false,
        val isOpeningDm: Boolean = false,
        val actionError: String? = null,
        // 본인 여부(JWT sub 대조) — 본인이면 액션이 "프로필 수정"뿐(웹 isSelf 미러)
        val isSelf: Boolean = false
    )

    sealed interface Action {
        data object Refresh : Action
        data object ToggleFriend : Action
        data object OpenDm : Action
        data object DismissActionError : Action
    }

    sealed interface Event {
        /** DM 방 확보 성공 — 화면이 채팅방(groupId=null)으로 이동한다 */
        data class DmOpened(val chatRoomId: Long, val title: String) : Event
    }
}
```

- [ ] **Step 3: 컴파일 검증**

Run: gradle `:composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

---

### Task 3: Compose UserProfileScreen + 라우트

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/user/UserProfileScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt` (라우트 선언+composable)

**Interfaces:**
- Consumes: Task 2 `UserProfileViewModel`·`formatJoinDate`, `SgTopBar(title:)`/`SgCard`/`SgAvatar(name, modifier, size, imageUrl)`/`LocalAppContainer`, 기존 라우트 `ChatRoomRoute(chatRoomId, groupId, title)`·`AccountSettingsRoute`.
- Produces: `UserProfileScreen(userId: Long, onBack, onOpenChatRoom: (Long, Long?, String) -> Unit, onOpenAccountSettings: () -> Unit, modifier, viewModel)`, `@Serializable internal data class UserProfileRoute(val userId: Long)` — Task 4·5의 진입점들이 navigate.

- [ ] **Step 1: UserProfileScreen 작성**

```kotlin
package kr.hhp227.storygroup.ui.screens.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatJoinDate

/** 백스택 엔트리 스코프 VM — 화면이 default parameter로 선언(GroupDetail 패턴) */
@Composable
private fun userProfileViewModel(userId: Long): UserProfileViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "user-profile-$userId") {
        UserProfileViewModel(
            userId = userId,
            getPublicProfileUseCase = container.getPublicProfileUseCase,
            getFriendsUseCase = container.getFriendsUseCase,
            addFriendUseCase = container.addFriendUseCase,
            removeFriendUseCase = container.removeFriendUseCase,
            openDirectRoomUseCase = container.openDirectRoomUseCase,
            getCurrentUserIdUseCase = container.getCurrentUserIdUseCase
        )
    }
}

/**
 * 공개 프로필 — 웹 /users/[userId] 미러(아바타+이름+상태메시지+가입일+bio,
 * 본인=프로필 수정 이동, 타인=1:1 DM+친구 추가/해제). 신고·차단은 여기 없다(게시글 더보기 몫).
 * iosApp UserProfileView.swift와 1:1 미러
 */
@Composable
fun UserProfileScreen(
    userId: Long,
    onBack: () -> Unit,
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    onOpenAccountSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserProfileViewModel = userProfileViewModel(userId)
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                is UserProfileViewModel.Event.DmOpened -> onOpenChatRoom(event.chatRoomId, null, event.title)
            }
        }
    }
    Column(modifier.fillMaxSize().background(sg.paper)) {
        SgTopBar(
            title = "프로필",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            }
        )
        val profile = uiState.profile

        when {
            profile == null && uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = sg.accent)
            }
            profile == null -> Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(uiState.loadError ?: "프로필을 불러오지 못했습니다.", style = SgTheme.typography.bodyMedium, color = sg.rust)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onAction(UserProfileViewModel.Action.Refresh) }) {
                    Text("다시 시도", color = sg.accent)
                }
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                SgCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SgAvatar(profile.name, size = 72.dp, imageUrl = profile.profileImg)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(profile.name, style = SgTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = sg.ink)
                                if (!profile.statusMessage.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(profile.statusMessage.orEmpty(), style = SgTheme.typography.bodySmall, color = sg.inkSoft)
                                }
                                Spacer(Modifier.height(2.dp))
                                Text("${formatJoinDate(profile.createdAt)} 가입", style = SgTheme.typography.labelSmall, color = sg.inkFaint)
                            }
                        }
                        if (!profile.bio.isNullOrBlank()) {
                            Divider(color = sg.stoneBorder)
                            Text(profile.bio.orEmpty(), style = SgTheme.typography.bodyMedium, color = sg.ink)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (uiState.isSelf) {
                                OutlinedButton(
                                    onClick = onOpenAccountSettings,
                                    shape = SgTheme.shapes.button,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.inkSoft)
                                ) {
                                    Text("프로필 수정", style = SgTheme.typography.labelLarge)
                                }
                            } else {
                                Button(
                                    onClick = { onAction(UserProfileViewModel.Action.OpenDm) },
                                    enabled = !uiState.isOpeningDm,
                                    shape = SgTheme.shapes.button,
                                    colors = ButtonDefaults.buttonColors(backgroundColor = sg.accent, contentColor = sg.onAccent)
                                ) {
                                    Text("1:1 DM", style = SgTheme.typography.labelLarge)
                                }
                                // 친구 여부 판정 불가(목록 로드 실패)면 버튼을 숨긴다 — 웹 isFriend===null 미러
                                uiState.isFriend?.let { isFriend ->
                                    OutlinedButton(
                                        onClick = { onAction(UserProfileViewModel.Action.ToggleFriend) },
                                        enabled = !uiState.isBusy,
                                        shape = SgTheme.shapes.button,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.inkSoft)
                                    ) {
                                        Text(if (isFriend) "친구 해제" else "친구 추가", style = SgTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        uiState.actionError?.let {
                            Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: App.kt 라우트+composable**

라우트 선언부(`SearchRoute` 아래)에 추가:

```kotlin
/** 공개 프로필 — 게시글 작성자·검색 users·친구 행에서 진입(웹 /users/[userId] 미러) */
@Serializable
internal data class UserProfileRoute(val userId: Long)
```

import 추가: `import kr.hhp227.storygroup.ui.screens.user.UserProfileScreen`

NavHost 안(`SearchRoute` composable 아래)에 추가:

```kotlin
                composable<UserProfileRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<UserProfileRoute>()

                    Surface(color = SgTheme.colors.paper) {
                        UserProfileScreen(
                            userId = route.userId,
                            onBack = { navController.popBackStack() },
                            onOpenChatRoom = { chatRoomId, groupId, title ->
                                navController.navigate(ChatRoomRoute(chatRoomId, groupId, title))
                            },
                            onOpenAccountSettings = { navController.navigate(AccountSettingsRoute) },
                            // 풀스크린이라 하단 시스템 내비바 인셋을 화면이 직접 소화
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        )
                    }
                }
```

- [ ] **Step 3: 컴파일 검증**

Run: gradle `:composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

---

### Task 4: Compose 진입점 A — 친구 탭 행+검색 users 행

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt` (MainShell·SearchRoute 배선)
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/MainShell.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/TabShell.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/DrawerShell.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/friend/FriendsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/search/SearchScreen.kt`

**Interfaces:**
- Consumes: Task 3 `UserProfileRoute`.
- Produces: `onOpenUserProfile: (Long) -> Unit` 드릴링 체인(MainShell→Tab/DrawerShell→DestinationContent→DestinationScreen→FriendsScreen), `SearchScreen(..., onOpenUserProfile: (Long) -> Unit, ...)` — Task 5와 독립.

- [ ] **Step 1: App.kt 배선**

`MainShell(...)` 호출에 파라미터 추가(`onOpenSearch` 아래):

```kotlin
                onOpenUserProfile = { userId -> navController.navigate(UserProfileRoute(userId)) },
```

`composable<SearchRoute>`의 `SearchScreen(...)` 호출에 추가(`onOpenChatRoom` 아래):

```kotlin
                            onOpenUserProfile = { userId -> navController.navigate(UserProfileRoute(userId)) },
```

- [ ] **Step 2: 셸 3파일 드릴링**

`MainShell.kt`: `MainShell`/`DestinationContent`/`DestinationScreen` 세 함수 시그니처에 `onOpenUserProfile: (Long) -> Unit` 추가(`onOpenSearch` 인접 위치), `TabShell(...)`/`DrawerShell(...)`/`DestinationScreen(...)` 호출에 전달. `DestinationScreen`의 FRIENDS 분기 교체:

```kotlin
        // 친구 탭 "메시지" 버튼 → DM 채팅방(groupId=null), 행 탭 → 공개 프로필(웹 /users/[id] 미러)
        MainDestination.FRIENDS -> FriendsScreen(
            onOpenChatRoom = onOpenChatRoom,
            onOpenUserProfile = onOpenUserProfile
        )
```

`TabShell.kt`/`DrawerShell.kt`: 시그니처에 `onOpenUserProfile: (Long) -> Unit` 추가(`onOpenSearch` 인접), `DestinationContent(...)` 호출에 전달.

- [ ] **Step 3: FriendsScreen 행 탭**

`FriendsScreen`·`FriendsContent` 시그니처에 `onOpenUserProfile: (Long) -> Unit` 추가(onOpenChatRoom 아래)하고 드릴링. `FriendRow`에 `onOpenProfile: () -> Unit` 파라미터 추가(isBusy 아래), items 호출부에 `onOpenProfile = { onOpenUserProfile(friend.userId) },` 전달. FriendRow의 SgCard를 onClick 형태로 교체:

```kotlin
    SgCard(modifier = modifier.fillMaxWidth(), onClick = onOpenProfile) {
```

(기존 `SgCard(modifier = modifier.fillMaxWidth())` 라인만 교체 — 내부 메시지/해제 버튼은 자기 탭을 우선 소비하므로 동작 불변.)

- [ ] **Step 4: SearchScreen users 행 탭**

`SearchScreen` 시그니처에 `onOpenUserProfile: (userId: Long) -> Unit` 추가(onOpenChatRoom 아래), `ResultList`에 드릴링(파라미터 `onOpenUserProfile: (Long) -> Unit`), `UserRow`에 `onOpenProfile: () -> Unit` 추가, users 섹션 items에서 `onOpenProfile = { onOpenUserProfile(user.id) },` 전달. UserRow의 SgCard 교체:

```kotlin
    SgCard(modifier = modifier.fillMaxWidth(), onClick = onOpenProfile) {
```

행 주석도 갱신: "행 탭 무동작" → "행 탭=공개 프로필(버튼 영역은 버튼이 우선)".

- [ ] **Step 5: 컴파일 검증**

Run: gradle `:composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

---

### Task 5: Compose 진입점 B — 게시글 상세 본문·댓글 작성자

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/post/PostDetailScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt` (PostDetailRoute composable 1줄)

**Interfaces:**
- Consumes: Task 3 `UserProfileRoute`, `Post.userId`·`Comment.userId`(기존 도메인).
- Produces: `PostDetailScreen(..., onOpenUserProfile: (Long) -> Unit, ...)`.

- [ ] **Step 1: PostDetailScreen 수정**

① `PostDetailScreen` 시그니처에 추가(`onEdit` 아래):

```kotlin
    // 본문·댓글 작성자 탭 → 공개 프로필(웹 작성자 메뉴의 "프로필 보기"만 직행으로 미러 —
    // 신고·차단은 기존 더보기 메뉴, DM은 프로필 화면 버튼이 담당해 중복이 없다)
    onOpenUserProfile: (Long) -> Unit,
```

② `PostBody` 시그니처에 `onOpenUserProfile: (Long) -> Unit` 추가(onAction 아래), 호출부(`item { PostBody(...) }`)에 `onOpenUserProfile = onOpenUserProfile,` 전달. 작성자 Row(415행 부근)를 clickable로 교체(import `androidx.compose.foundation.clickable` 추가):

```kotlin
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // 작성자 영역만 탭 타깃(본문·첨부 제외) — 본인 글이면 본인 프로필(프로필 수정 버튼)로 간다
            modifier = Modifier.clickable { onOpenUserProfile(post.userId) }
        ) {
```

③ `CommentRow` 시그니처에 `onOpenAuthor: () -> Unit` 추가(onBlock 아래), 두 호출부(최상위 댓글·답글)에 각각 `onOpenAuthor = { onOpenUserProfile(comment.userId) },` / `onOpenAuthor = { onOpenUserProfile(reply.userId) },` 전달. CommentRow 안에서 아바타와 이름을 탭 타깃으로:

```kotlin
        SgAvatar(
            comment.authorName,
            size = 28.dp,
            imageUrl = comment.authorProfileImg,
            modifier = Modifier.clickable(onClick = onOpenAuthor)
        )
```

```kotlin
                Text(
                    comment.authorName,
                    style = SgTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = sg.ink,
                    modifier = Modifier.clickable(onClick = onOpenAuthor)
                )
```

(⚠️ SgAvatar의 파라미터 순서는 `(name, modifier, size, imageUrl, ...)` — modifier는 이름 지정으로 넘기면 순서 무관.)

- [ ] **Step 2: App.kt PostDetailRoute 배선**

`composable<PostDetailRoute>`의 `PostDetailScreen(...)` 호출에 추가(`onEdit` 아래):

```kotlin
                            onOpenUserProfile = { userId -> navController.navigate(UserProfileRoute(userId)) },
```

- [ ] **Step 3: 컴파일+Android 빌드 검증**

Run: gradle `:composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

Run: Windows gradle `:composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 6: iOS AppContainer + TimeFormats + UserProfileViewModel.swift

**Files:**
- Modify: `iosApp/iosApp/DI/AppContainer.swift`
- Modify: `iosApp/iosApp/UI/Util/TimeFormats.swift`
- Create: `iosApp/iosApp/UI/Screens/User/UserProfileViewModel.swift`

**Interfaces:**
- Consumes: shared `GetPublicProfileUseCase(userRepository:)`·`PublicProfile`, 기존 Swift 유스케이스들(`invoke(userId:)` 라벨은 FriendsViewModel 사용례와 동일), `GetCurrentUserIdUseCase.invoke(): KotlinLong?`(동기 — Swift에서 `.int64Value`), `MviViewModel` 프로토콜+`PassthroughSubject` 이벤트 관용구, `Error.kotlinMessage(fallback:)`.
- Produces: `container.getPublicProfileUseCase`, `TimeFormats.joinDate(_:)`, `UserProfileViewModel(userId:getPublicProfileUseCase:getFriendsUseCase:addFriendUseCase:removeFriendUseCase:openDirectRoomUseCase:getCurrentUserIdUseCase:)` — Task 7 UserProfileView가 소유.

- [ ] **Step 1: AppContainer.swift 배선**

프로퍼티 선언부(`changePasswordUseCase` 인접)에 추가:

```swift
    /// 공개 프로필 — 게시글 작성자·검색·친구 행에서 진입(웹 /users/[id] 미러)
    let getPublicProfileUseCase: GetPublicProfileUseCase
```

init 안, 기존 `userRepository`로 만드는 유스케이스 라인들 인접에 추가(⚠️ 실제 지역변수명·라벨은 기존 `GetMyProfileUseCase(userRepository: ...)` 라인을 그대로 따른다):

```swift
        getPublicProfileUseCase = GetPublicProfileUseCase(userRepository: userRepository)
```

- [ ] **Step 2: TimeFormats.swift에 joinDate 추가**

`relative` 아래에 추가:

```swift
    /// 가입일 표기 — 웹 공개 프로필의 toLocaleDateString("ko-KR") 미러("2026. 7. 1." — 선행 0 없음).
    /// " 가입" 접미는 화면 몫. composeApp formatJoinDate와 1:1 미러
    static func joinDate(_ isoDateTime: String) -> String {
        let parts = String(isoDateTime.prefix(while: { $0 != "T" })).split(separator: "-").compactMap { Int($0) }

        guard parts.count == 3 else { return dateOnly(isoDateTime) }
        return "\(parts[0]). \(parts[1]). \(parts[2])."
    }
```

- [ ] **Step 3: UserProfileViewModel.swift 작성**

```swift
import Combine
import Foundation
import Shared

/// 공개 프로필 — composeApp UserProfileViewModel.kt와 1:1 미러.
/// 친구 여부는 응답에 없어 친구 목록과 대조하며, 목록 로드 실패 시 isFriend=nil 유지 →
/// 화면이 친구 버튼을 숨긴다(웹 isFriend===null 미러).
final class UserProfileViewModel: MviViewModel {
    @Published private(set) var uiState: UiState

    let event = PassthroughSubject<Event, Never>()

    private let userId: Int64

    private let getPublicProfileUseCase: GetPublicProfileUseCase

    private let getFriendsUseCase: GetFriendsUseCase

    private let addFriendUseCase: AddFriendUseCase

    private let removeFriendUseCase: RemoveFriendUseCase

    private let openDirectRoomUseCase: OpenDirectRoomUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: load()
        case .toggleFriend: toggleFriend()
        case .openDm: openDm()
        case .dismissActionError: uiState.actionError = nil
        }
    }

    private func load() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.loadError = nil
        Task { @MainActor in
            do {
                let profile = try await getPublicProfileUseCase.invoke(userId: userId)
                uiState.isLoading = false
                uiState.profile = profile
            } catch {
                uiState.isLoading = false
                uiState.loadError = error.kotlinMessage(fallback: "프로필을 불러오지 못했습니다.")
            }
        }
    }

    /// 친구 버튼 상태용 — 실패는 조용히 무시하고 isFriend=nil 유지(버튼 숨김, 웹 미러)
    private func loadFriendState() {
        if uiState.isSelf { return }

        Task { @MainActor in
            if let friends = try? await getFriendsUseCase.invoke() {
                uiState.isFriend = friends.contains { $0.userId == userId }
            }
        }
    }

    /// 친구 토글 — 성공 시 isFriend만 뒤집는다(웹 미러). 409 등은 서버 문구
    private func toggleFriend() {
        guard let isFriend = uiState.isFriend else { return }

        if uiState.isBusy { return }

        uiState.isBusy = true
        uiState.actionError = nil
        Task { @MainActor in
            do {
                if isFriend {
                    try await removeFriendUseCase.invoke(userId: userId)
                } else {
                    try await addFriendUseCase.invoke(userId: userId)
                }
                uiState.isBusy = false
                uiState.isFriend = !isFriend
            } catch {
                uiState.isBusy = false
                uiState.actionError = error.kotlinMessage(fallback: "친구 처리에 실패했습니다.")
            }
        }
    }

    /// 1:1 DM — get-or-create(멱등). 방 이름은 서버가 "DM" 고정이라 상대 이름을 제목으로(친구 탭 미러)
    private func openDm() {
        guard let profile = uiState.profile else { return }

        if uiState.isOpeningDm { return }

        uiState.isOpeningDm = true
        uiState.actionError = nil
        Task { @MainActor in
            do {
                let chatRoomId = try await openDirectRoomUseCase.invoke(otherUserId: userId)
                uiState.isOpeningDm = false
                event.send(.dmOpened(chatRoomId: chatRoomId.int64Value, title: profile.name))
            } catch {
                // 차단 관계(403 BLOCKED) 등 — 인라인 문구로 노출
                uiState.isOpeningDm = false
                uiState.actionError = error.kotlinMessage(fallback: "DM을 시작하지 못했습니다.")
            }
        }
    }

    init(
        userId: Int64,
        getPublicProfileUseCase: GetPublicProfileUseCase,
        getFriendsUseCase: GetFriendsUseCase,
        addFriendUseCase: AddFriendUseCase,
        removeFriendUseCase: RemoveFriendUseCase,
        openDirectRoomUseCase: OpenDirectRoomUseCase,
        getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    ) {
        self.userId = userId
        self.getPublicProfileUseCase = getPublicProfileUseCase
        self.getFriendsUseCase = getFriendsUseCase
        self.addFriendUseCase = addFriendUseCase
        self.removeFriendUseCase = removeFriendUseCase
        self.openDirectRoomUseCase = openDirectRoomUseCase
        uiState = UiState(isSelf: getCurrentUserIdUseCase.invoke()?.int64Value == userId)
        load()
        loadFriendState()
    }

    struct UiState {
        var profile: PublicProfile? = nil
        var isLoading = false
        var loadError: String? = nil
        /// nil=친구 여부 판정 불가(목록 로드 실패) → 화면이 친구 버튼을 숨긴다(웹 미러)
        var isFriend: Bool? = nil
        /// 친구 토글 진행 중 — 버튼 비활성(웹 isBusy 미러)
        var isBusy = false
        var isOpeningDm = false
        var actionError: String? = nil
        /// 본인 여부(JWT sub 대조) — 본인이면 액션이 "프로필 수정"뿐(웹 isSelf 미러)
        var isSelf = false

        init(isSelf: Bool = false) {
            self.isSelf = isSelf
        }
    }

    enum Action {
        case refresh
        case toggleFriend
        case openDm
        case dismissActionError
    }

    enum Event {
        /// DM 방 확보 성공 — 화면이 채팅방(groupId=nil)으로 push한다
        case dmOpened(chatRoomId: Int64, title: String)
    }
}
```

⚠️ 작성 시 실제 대조: `GetCurrentUserIdUseCase`의 Swift 반환형(Kotlin `Long?` → `KotlinLong?`)과 호출부 선례(채팅 VM에서 사용 중 — grep으로 확인해 같은 형태 사용), `OpenDirectRoomUseCase.invoke(otherUserId:)` 라벨(FriendsViewModel.swift:134 확인됨).

- [ ] **Step 4: 검증 (Kotlin 타입 체크)**

Run: gradle `:shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. Swift는 FriendsViewModel.swift와 육안 대조.

---

### Task 7: iOS UserProfileView.swift + pbxproj 등록

**Files:**
- Create: `iosApp/iosApp/UI/Screens/User/UserProfileView.swift`
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`

**Interfaces:**
- Consumes: Task 6 `UserProfileViewModel`·`TimeFormats.joinDate`, `SGCard`/`SGAvatar(name:size:imageUrl:)`/`ChatRoomRef(chatRoomId:groupId:title:)`, `ChatRoomView(chatRoomId:groupId:title:container:chatViewModel:)`, `AccountSettingsView(container:profileViewModel:)`, `colors.radiusButton ?? 12` 필 버튼 패턴(FriendsView SearchResultRow).
- Produces: `UserProfileView(userId:container:chatViewModel:profileViewModel:)` — Task 8·9의 진입점들이 push.

- [ ] **Step 1: UserProfileView 작성**

```swift
import SwiftUI
import Shared

/// 공개 프로필 — composeApp UserProfileScreen.kt와 1:1 미러(웹 /users/[userId]).
/// 자체 push 2종: DM 성공 → 채팅방, 본인 "프로필 수정" → 계정 설정(GroupDetailView 자체 push 선례).
struct UserProfileView: View {
    let container: AppContainer

    /// 채팅방 push에 필요 — 셸 소유 세션 VM 전달(pass-through라 plain let, GroupDetailView 선례)
    let chatViewModel: ChatViewModel

    /// 계정 설정 push에 필요 — 셸 소유 세션 VM(저장 성공 시 셸 헤더 갱신 공유)
    let profileViewModel: ProfileViewModel

    @StateObject private var userProfileViewModel: UserProfileViewModel

    @State private var selectedChatRoom: ChatRoomRef? = nil

    @State private var showAccountSettings = false

    @Environment(\.sgColors) private var colors

    var body: some View {
        pushContainer
            .navigationTitle("프로필")
            .navigationBarTitleDisplayMode(.inline)
            .onReceive(userProfileViewModel.event) { event in
                switch event {
                case .dmOpened(let chatRoomId, let title):
                    selectedChatRoom = ChatRoomRef(chatRoomId: chatRoomId, groupId: nil, title: title)
                }
            }
    }

    /// 자체 push 2종 — iOS 16 navigationDestination / iOS 15 숨김 NavigationLink 폴백(GroupDetailView 선례)
    @ViewBuilder private var pushContainer: some View {
        if #available(iOS 16.0, *) {
            content
                .navigationDestination(isPresented: showChatRoom) { chatRoomDestination }
                .navigationDestination(isPresented: $showAccountSettings) { accountSettingsDestination }
        } else {
            content
                .background(
                    NavigationLink(isActive: showChatRoom) { chatRoomDestination } label: { EmptyView() }.hidden()
                )
                .background(
                    NavigationLink(isActive: $showAccountSettings) { accountSettingsDestination } label: { EmptyView() }.hidden()
                )
        }
    }

    @ViewBuilder private var content: some View {
        let uiState = userProfileViewModel.uiState

        if uiState.profile == nil && uiState.isLoading {
            ProgressView()
                .tint(colors.accent)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(colors.paper.ignoresSafeArea())
        } else if let profile = uiState.profile {
            profileBody(profile, uiState: uiState)
        } else {
            VStack(spacing: 8) {
                Text(uiState.loadError ?? "프로필을 불러오지 못했습니다.")
                    .font(.subheadline)
                    .foregroundColor(colors.rust)
                Button("다시 시도") { userProfileViewModel.onAction(.refresh) }
                    .font(.subheadline.bold())
                    .foregroundColor(colors.accent)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(colors.paper.ignoresSafeArea())
        }
    }

    private func profileBody(_ profile: PublicProfile, uiState: UserProfileViewModel.UiState) -> some View {
        ScrollView {
            SGCard {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 16) {
                        SGAvatar(name: profile.name, size: 72, imageUrl: profile.profileImg)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(profile.name)
                                .font(.title3.bold())
                                .foregroundColor(colors.ink)
                            if let statusMessage = profile.statusMessage, !statusMessage.isEmpty {
                                Text(statusMessage)
                                    .font(.footnote)
                                    .foregroundColor(colors.inkSoft)
                            }
                            Text("\(TimeFormats.joinDate(profile.createdAt)) 가입")
                                .font(.caption2)
                                .foregroundColor(colors.inkFaint)
                        }
                    }
                    if let bio = profile.bio, !bio.isEmpty {
                        Divider().background(colors.stoneBorder)
                        Text(bio)
                            .font(.subheadline)
                            .foregroundColor(colors.ink)
                    }
                    HStack(spacing: 8) {
                        if uiState.isSelf {
                            Button("프로필 수정") { showAccountSettings = true }
                                .font(.subheadline.bold())
                                .foregroundColor(colors.inkSoft)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(
                                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                                        .stroke(colors.stoneBorder, lineWidth: 1)
                                )
                                .buttonStyle(.plain)
                        } else {
                            Button("1:1 DM") { userProfileViewModel.onAction(.openDm) }
                                .font(.subheadline.bold())
                                .foregroundColor(colors.onAccent)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(
                                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                                        .fill(colors.accent)
                                )
                                .buttonStyle(.plain)
                                .disabled(uiState.isOpeningDm)
                            // 친구 여부 판정 불가(목록 로드 실패)면 버튼을 숨긴다 — 웹 isFriend===null 미러
                            if let isFriend = uiState.isFriend {
                                Button(isFriend ? "친구 해제" : "친구 추가") { userProfileViewModel.onAction(.toggleFriend) }
                                    .font(.subheadline.bold())
                                    .foregroundColor(colors.inkSoft)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(
                                        RoundedRectangle(cornerRadius: colors.radiusButton ?? 12, style: .continuous)
                                            .stroke(colors.stoneBorder, lineWidth: 1)
                                    )
                                    .buttonStyle(.plain)
                                    .disabled(uiState.isBusy)
                            }
                        }
                    }
                    if let actionError = uiState.actionError {
                        Text(actionError)
                            .font(.footnote)
                            .foregroundColor(colors.rust)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
            }
            .padding(16)
        }
        .background(colors.paper.ignoresSafeArea())
    }

    @ViewBuilder private var chatRoomDestination: some View {
        if let room = selectedChatRoom {
            ChatRoomView(
                chatRoomId: room.chatRoomId,
                groupId: room.groupId,
                title: room.title,
                container: container,
                chatViewModel: chatViewModel
            )
        }
    }

    private var accountSettingsDestination: some View {
        AccountSettingsView(container: container, profileViewModel: profileViewModel)
    }

    /// pop(백 버튼/스와이프) 시 상태를 nil로 되돌리는 브리지(MainShellView 선례)
    private var showChatRoom: Binding<Bool> {
        Binding(
            get: { selectedChatRoom != nil },
            set: { if !$0 { selectedChatRoom = nil } }
        )
    }

    init(userId: Int64, container: AppContainer, chatViewModel: ChatViewModel, profileViewModel: ProfileViewModel) {
        _userProfileViewModel = StateObject(wrappedValue: UserProfileViewModel(
            userId: userId,
            getPublicProfileUseCase: container.getPublicProfileUseCase,
            getFriendsUseCase: container.getFriendsUseCase,
            addFriendUseCase: container.addFriendUseCase,
            removeFriendUseCase: container.removeFriendUseCase,
            openDirectRoomUseCase: container.openDirectRoomUseCase,
            getCurrentUserIdUseCase: container.getCurrentUserIdUseCase
        ))
        self.container = container
        self.chatViewModel = chatViewModel
        self.profileViewModel = profileViewModel
    }
}
```

⚠️ 작성 시 실제 대조: `SGAvatar` init 라벨(size 파라미터 위치), `PublicProfile` Swift 브리징 프로퍼티명(전부 예약어 충돌 없음 — description 없음), `colors.rust`/`onAccent`/`stoneBorder`/`radiusButton` 존재(SGTheme.swift — 홈 검색 픽스 때 확인됨).

- [ ] **Step 2: pbxproj 등록**

신규 그룹 `A1013015AABBCCDDEEFF0015 /* User */`(path = User) — `Screens` 그룹 children에 추가(기존 `A1013014 /* Search */` 블록을 본떠 정의). 파일 참조 `A1011046AABBCCDDEEFF0046 /* UserProfileView.swift */`·`A1011047AABBCCDDEEFF0047 /* UserProfileViewModel.swift */`, 빌드 파일 `A1010046AABBCCDDEEFF0046`·`A1010047AABBCCDDEEFF0047`(PBXBuildFile 섹션+Sources 페이즈). 기존 SearchView.swift(0044/0045) 항목을 템플릿으로 복사.

- [ ] **Step 3: pbxproj 정합성 검증**

```bash
cd iosApp && \
echo "disk: $(find iosApp -name '*.swift' | wc -l)" && \
echo "refs: $(grep -c '\.swift.*PBXFileReference\|lastKnownFileType = sourcecode.swift' iosApp.xcodeproj/project.pbxproj)" && \
echo "sources: $(grep -c '\.swift in Sources' iosApp.xcodeproj/project.pbxproj)" && \
python3 -c "s=open('iosApp.xcodeproj/project.pbxproj').read(); print('braces', s.count('{')-s.count('}'), 'parens', s.count('(')-s.count(')'))"
```
Expected: 세 카운트 일치(이번 작업으로 각각 +2, 홈 검색 후 63 → 65), braces/parens 차 0. (grep 패턴이 형식과 안 맞으면 조정해 세 카운트의 기준을 통일)

---

### Task 8: iOS 진입점 A — 셸(친구 탭)+profileViewModel 스레딩

**Files:**
- Modify: `iosApp/iosApp/UI/Shell/MainShellView.swift`
- Modify: `iosApp/iosApp/UI/Shell/TabShellView.swift`
- Modify: `iosApp/iosApp/UI/Shell/DrawerShellView.swift`
- Modify: `iosApp/iosApp/UI/Screens/Friend/FriendsView.swift`
- Modify: `iosApp/iosApp/UI/Screens/Home/HomeView.swift` (파라미터 2개 스레딩만 — 탭 동작은 Task 9)

**Interfaces:**
- Consumes: Task 7 `UserProfileView(userId:container:chatViewModel:profileViewModel:)`.
- Produces: `onOpenUserProfile: (Int64) -> Void` 셸 체인, `DestinationView`·`HomeView`에 `chatViewModel`/`profileViewModel` 전달(Task 9의 PostDetailView 호출부가 사용). HomeView 새 init: `HomeView(container:chatViewModel:profileViewModel:)`.

- [ ] **Step 1: MainShellView.swift**

상태 추가(`showSearch` 아래):

```swift
    /// 공개 프로필 풀스크린 push — Compose NavHost(UserProfileRoute) 미러(친구 탭 행 발 진입)
    @State private var selectedUserId: Int64? = nil
```

iOS16 분기에 추가(`$showSearch` 라인 아래):

```swift
                    .navigationDestination(isPresented: showUserProfile) { userProfileDestination }
```

iOS15 분기에 숨김 링크 추가(기존 6개와 같은 형태):

```swift
                    .background(
                        NavigationLink(isActive: showUserProfile) {
                            userProfileDestination
                        } label: {
                            EmptyView()
                        }
                        .hidden()
                    )
```

destination+브리지 추가(`searchDestination` 아래):

```swift
    @ViewBuilder private var userProfileDestination: some View {
        if let userId = selectedUserId {
            UserProfileView(
                userId: userId,
                container: container,
                chatViewModel: chatViewModel,
                profileViewModel: profileViewModel
            )
        }
    }

    /// pop(백 버튼/스와이프) 시 selectedUserId를 nil로 되돌리는 브리지
    private var showUserProfile: Binding<Bool> {
        Binding(
            get: { selectedUserId != nil },
            set: { if !$0 { selectedUserId = nil } }
        )
    }
```

`shellContent`의 TabShellView/DrawerShellView 호출 양쪽에 추가(`onOpenSearch` 인접):

```swift
                onOpenUserProfile: { selectedUserId = $0 },
                profileViewModel: profileViewModel,
```

- [ ] **Step 2: TabShellView.swift / DrawerShellView.swift**

양쪽에 프로퍼티 추가(`onOpenSearch` 인접):

```swift
    /// 공개 프로필 풀스크린 push — MainShellView(루트 NavigationStack)로 위임
    let onOpenUserProfile: (Int64) -> Void

    /// 홈→게시글 상세→작성자 프로필 체인이 계정 설정 push에 쓴다 — 셸 소유 세션 VM 전달
    let profileViewModel: ProfileViewModel
```

내부 `DestinationView(...)` 호출에 `profileViewModel: profileViewModel,`(= `profile:` 바로 아래)과 `onOpenUserProfile: onOpenUserProfile,`(= `onOpenChatRoom:` 아래) 전달.

⚠️ Swift 멤버와이즈 init 호출은 선언 순서를 따른다 — 새 프로퍼티의 선언 위치(Step 3의 DestinationView: `profile` 바로 아래 profileViewModel, `onOpenChatRoom` 아래 onOpenUserProfile / 셸 2종: `onOpenSearch` 인접)와 모든 호출부의 인자 위치를 반드시 일치시킨다(홈 검색 때 검증된 규칙).

- [ ] **Step 3: DestinationView(MainShellView.swift 하단)**

프로퍼티 추가(`profile` 인접):

```swift
    /// 홈 체인(게시글 상세→작성자 프로필→계정 설정)이 쓴다 — 셸 소유 세션 VM
    let profileViewModel: ProfileViewModel

    /// 친구 탭 행 탭 → 공개 프로필(웹 /users/[id] 미러) — MainShellView로 위임
    let onOpenUserProfile: (Int64) -> Void
```

`.home` 분기 교체(Task 9의 PostDetailView 체인용 선행 스레딩):

```swift
        case .home:
            HomeView(container: container, chatViewModel: chatViewModel, profileViewModel: profileViewModel)
```

`.friends` 분기 교체:

```swift
        case .friends:
            // 친구 탭 "메시지" 버튼 → DM 채팅방(groupId=nil), 행 탭 → 공개 프로필(웹 /users/[id] 미러)
            FriendsView(container: container, onOpenChatRoom: onOpenChatRoom, onOpenUserProfile: onOpenUserProfile)
```

- [ ] **Step 4: FriendsView.swift 행 탭**

`FriendsView`에 프로퍼티+init 파라미터 `onOpenUserProfile: (Int64) -> Void` 추가(onOpenChatRoom 인접, init에서 `self.onOpenUserProfile = onOpenUserProfile`), `FriendsContent`로 전달. `FriendRow`에 `let onOpenProfile: () -> Void` 추가(isBusy 아래), 호출부에 `onOpenProfile: { onOpenUserProfile(friend.userId) },` 전달. FriendRow body의 `SGCard { ... }`를 Button으로 감싼다:

```swift
    var body: some View {
        Button(action: onOpenProfile) {
            SGCard {
                // ...기존 HStack 내용 그대로...
            }
        }
        .buttonStyle(.plain)
    }
```

(내부 "메시지"/"해제" Button은 SwiftUI가 더 안쪽 버튼을 우선 처리해 동작 불변 — SearchView GroupRow의 Button-wrap 선례.)

- [ ] **Step 5: HomeView.swift 파라미터 스레딩**

`HomeView`·`HomeContent`에 `let chatViewModel: ChatViewModel`·`let profileViewModel: ProfileViewModel` 프로퍼티+init 파라미터 추가(pass-through, `HomeView(container:chatViewModel:profileViewModel:)` / `HomeContent` init도 동일). 이 태스크에서는 전달만 하고 사용은 Task 9(PostDetailView 호출부)가 한다 — Swift는 미사용 프로퍼티가 에러가 아니므로 안전.

- [ ] **Step 6: 육안 검증**

MainShellView의 shells 호출 인자 순서 == Tab/DrawerShellView 프로퍼티 선언 순서, DestinationView 호출 인자 순서 == 프로퍼티 순서, FriendsView/HomeView init 라벨 정합을 파일 대조로 확인. (Swift 컴파일 불가 — 리뷰가 컴파일러 역할)

---

### Task 9: iOS 진입점 B — 게시글 상세·검색 + 호출부 3곳

**Files:**
- Modify: `iosApp/iosApp/UI/Screens/Post/PostDetailView.swift`
- Modify: `iosApp/iosApp/UI/Screens/Home/HomeView.swift` (PostDetailView 호출부)
- Modify: `iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift` (PostDetailView 호출부)
- Modify: `iosApp/iosApp/UI/Screens/Search/SearchView.swift` (PostDetailView 호출부+users 행 탭+push)

**Interfaces:**
- Consumes: Task 7 `UserProfileView(userId:container:chatViewModel:profileViewModel:)`, Task 8이 스레딩한 `HomeView.chatViewModel/profileViewModel`.
- Produces: `PostDetailView(container:groupId:postId:chatViewModel:profileViewModel:)` (새 init 시그니처 — 호출부 3곳 갱신 필수).

- [ ] **Step 1: PostDetailView.swift**

① 프로퍼티 추가(`container` 아래):

```swift
    /// 작성자 프로필 push 체인(프로필→채팅방/계정 설정)에 필요 — 셸 소유 세션 VM pass-through
    private let chatViewModel: ChatViewModel

    private let profileViewModel: ProfileViewModel
```

② 상태 추가(`showEdit` 아래):

```swift
    /// 본문·댓글 작성자 탭 → 공개 프로필 push(웹 작성자 메뉴의 "프로필 보기" 직행 미러)
    @State private var selectedAuthorId: Int64? = nil
```

③ `body`의 이중 분기에 2번째 목적지 추가:

```swift
    var body: some View {
        if #available(iOS 16.0, *) {
            core
                .navigationDestination(isPresented: $showEdit) { editDestination }
                .navigationDestination(isPresented: showAuthorProfile) { authorProfileDestination }
        } else {
            core
                .background(
                    NavigationLink(isActive: $showEdit) {
                        editDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .background(
                    NavigationLink(isActive: showAuthorProfile) {
                        authorProfileDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
        }
    }
```

④ destination+브리지 추가(`editDestination` 인접):

```swift
    @ViewBuilder private var authorProfileDestination: some View {
        if let authorId = selectedAuthorId {
            UserProfileView(
                userId: authorId,
                container: container,
                chatViewModel: chatViewModel,
                profileViewModel: profileViewModel
            )
        }
    }

    /// pop(백 버튼/스와이프) 시 selectedAuthorId를 nil로 되돌리는 브리지
    private var showAuthorProfile: Binding<Bool> {
        Binding(
            get: { selectedAuthorId != nil },
            set: { if !$0 { selectedAuthorId = nil } }
        )
    }
```

⑤ `postBody`의 작성자 HStack(239행 부근)을 Button으로 감싼다:

```swift
                // 작성자 영역만 탭 타깃(본문·첨부 제외) — 본인 글이면 본인 프로필(프로필 수정)로 간다
                Button(action: { selectedAuthorId = post.userId }) {
                    HStack(spacing: 10) {
                        SGAvatar(name: post.authorName, imageUrl: post.authorProfileImg)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(post.authorName).font(.subheadline.bold()).foregroundColor(colors.ink)
                            Text(TimeFormats.relative(post.createdAt)).font(.caption).foregroundColor(colors.inkFaint)
                        }
                        Spacer()
                    }
                }
                .buttonStyle(.plain)
```

⑥ `commentRow`의 아바타·이름을 탭 타깃으로(기존 두 줄 교체):

```swift
            Button(action: { selectedAuthorId = comment.userId }) {
                SGAvatar(name: comment.authorName, size: 28, imageUrl: comment.authorProfileImg)
            }
            .buttonStyle(.plain)
```

```swift
                    Button(action: { selectedAuthorId = comment.userId }) {
                        Text(comment.authorName).font(.caption.bold()).foregroundColor(colors.ink)
                    }
                    .buttonStyle(.plain)
```

⑦ init 시그니처 교체+저장:

```swift
    init(container: AppContainer, groupId: Int64, postId: Int64, chatViewModel: ChatViewModel, profileViewModel: ProfileViewModel) {
        self.container = container
        self.groupId = groupId
        self.postId = postId
        self.chatViewModel = chatViewModel
        self.profileViewModel = profileViewModel
        // ...기존 _postDetailViewModel StateObject 초기화 그대로...
    }
```

- [ ] **Step 2: 호출부 3곳 갱신**

`HomeView.swift`(184행 부근)·`GroupDetailView.swift`(537행 부근)·`SearchView.swift`(postDetailDestination) — 각각의 `PostDetailView(...)` 호출에 `chatViewModel: chatViewModel, profileViewModel: profileViewModel` 인자 추가. (HomeView/HomeContent는 Task 8이 스레딩해 둠 — HomeContent 내부 호출이면 HomeContent의 프로퍼티 사용, GroupDetailView·SearchView는 기존 자기 프로퍼티.)

- [ ] **Step 3: SearchView.swift users 행 탭+push**

① 상태 추가(`selectedChatRoom` 아래):

```swift
    @State private var selectedUserId: Int64? = nil
```

② pushContainer 양 분기에 4번째 목적지 추가(iOS16: `.navigationDestination(isPresented: showUserProfile) { userProfileDestination }`, iOS15: 같은 형태의 `.background(NavigationLink(...).hidden())`).

③ destination+브리지 추가(`chatRoomDestination` 인접):

```swift
    @ViewBuilder private var userProfileDestination: some View {
        if let userId = selectedUserId {
            UserProfileView(
                userId: userId,
                container: container,
                chatViewModel: chatViewModel,
                profileViewModel: profileViewModel
            )
        }
    }

    private var showUserProfile: Binding<Bool> {
        Binding(
            get: { selectedUserId != nil },
            set: { if !$0 { selectedUserId = nil } }
        )
    }
```

④ `userRow`를 Button으로 감싼다(현재 SGCard 직접 반환 — GroupRow와 같은 형태로):

```swift
    private func userRow(_ user: UserSearchResult) -> some View {
        Button(action: { selectedUserId = user.id }) {
            SGCard {
                // ...기존 HStack 내용 그대로(친구 추가/해제 버튼 포함 — 내부 버튼이 탭 우선)...
            }
        }
        .buttonStyle(.plain)
    }
```

행 주석 갱신: "행 탭 무동작" → "행 탭=공개 프로필".

- [ ] **Step 4: 육안 검증**

PostDetailView 새 init 라벨 순서 == 호출부 3곳 인자 순서, UserProfileView 호출 라벨 정합, SearchView 이중 분기 4목적지 대칭 확인.

---

### Task 10: 최종 검증 + 스테이징 + 커밋 메시지

**Files:**
- Stage: 이번 작업의 실수정·신규 파일 전부(아래 목록)+스펙/플랜 문서 2개

- [ ] **Step 1: 전체 재검증**

Run: gradle `:shared:jvmTest :composeApp:compileKotlinJvm :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL, 테스트 전건 통과(기존 7+신규 2=9).

Run: Windows gradle `:composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: 데스크톱 부트 스모크**

Windows gradle `:composeApp:run`을 백그라운드로(로그 리다이렉트), ~60초 후 로그에 예외/스택트레이스 없음 확인(특히 kotlinx.serialization — UserProfileRoute 등록 크래시 모드), PowerShell로 종료(`Get-Process | Where-Object {$_.MainWindowTitle -eq 'StoryGroup'} | Stop-Process`). 실검색·프로필 진입 조작은 사용자 QA 몫.

- [ ] **Step 3: pbxproj 정합 재확인 + CRLF 확인 + 경로 스테이징**

Task 7 Step 3의 정합성 스크립트 재실행(65=65=65 기대). 수정 파일 각각 `git diff <경로>`로 의도 변경만인지 확인(전 파일 플립이면 `sed -i 's/\r$//'` 후 재확인). 스테이징(신규 7+수정 23+문서 2 = 32경로):

```bash
git add \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/UserDtos.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/PublicProfile.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/UserRepository.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/UserRepositoryImpl.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/GetPublicProfileUseCase.kt \
  shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/PublicProfileDtoTest.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/util/TimeFormats.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/user/UserProfileViewModel.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/user/UserProfileScreen.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/MainShell.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/TabShell.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/DrawerShell.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/friend/FriendsScreen.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/search/SearchScreen.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/post/PostDetailScreen.kt \
  iosApp/iosApp/DI/AppContainer.swift \
  iosApp/iosApp/UI/Util/TimeFormats.swift \
  iosApp/iosApp/UI/Screens/User/UserProfileView.swift \
  iosApp/iosApp/UI/Screens/User/UserProfileViewModel.swift \
  iosApp/iosApp/UI/Shell/MainShellView.swift \
  iosApp/iosApp/UI/Shell/TabShellView.swift \
  iosApp/iosApp/UI/Shell/DrawerShellView.swift \
  iosApp/iosApp/UI/Screens/Friend/FriendsView.swift \
  iosApp/iosApp/UI/Screens/Home/HomeView.swift \
  iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift \
  iosApp/iosApp/UI/Screens/Search/SearchView.swift \
  iosApp/iosApp/UI/Screens/Post/PostDetailView.swift \
  iosApp/iosApp.xcodeproj/project.pbxproj \
  docs/superpowers/specs/2026-08-14-user-profile-design.md \
  docs/superpowers/plans/2026-08-14-user-profile.md
```

`git status --short`로 스테이징 목록이 위 32경로와 일치하는지, 무관 파일 유입이 없는지 확인.

- [ ] **Step 4: 커밋 메시지 전달(커밋은 사용자)**

제안 메시지:

```
사용자 공개 프로필 화면 — 웹 /users/[userId] 미러(서버 수정 0)

- shared: PublicProfileResponse DTO+PublicProfile 도메인+UserRepository.getPublicProfile
  +GetPublicProfileUseCase(GET /api/users/{userId}), DTO 테스트 2개
- Compose: UserProfileRoute 풀스크린+UserProfileScreen/ViewModel(MVI, DmOpened 이벤트)
  본인=프로필 수정→계정 설정, 타인=1:1 DM+친구 추가/해제(친구 목록 로드 실패 시 버튼 숨김)
  가입일 "YYYY. M. D. 가입"(formatJoinDate — 웹 ko-KR 미러)
- 진입점 3곳: 친구 탭 행+검색 users 행(SgCard onClick)+게시글 상세 본문/댓글 작성자 탭
  (웹 4항목 메뉴는 미러 안 함 — 신고·차단은 기존 더보기, DM은 프로필 버튼이 담당)
- iOS: UserProfileView/ViewModel 미러(pbxproj 0046/0047+User 그룹), 셸 7번째 push+검색/상세 자체 push
  PostDetailView init에 chatViewModel/profileViewModel 추가(호출부 3곳), HomeView 스레딩
- 검증: jvmTest 9/9+jvm+assembleDebug+iOS klib+부트 스모크, ⚠️Swift/Mac 미검증
```
