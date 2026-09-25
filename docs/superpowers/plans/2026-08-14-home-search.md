# 홈 검색(통합검색) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈 상단바 검색 아이콘(현재 TODO 스텁)으로 진입하는 통합검색 화면 — 기배포 `GET /api/search` 5섹션(사용자·그룹·게시글·파일·메시지)을 웹 /search 미러로 표시하고 결과 탭으로 이동한다. 서버 수정 0.

**Architecture:** shared에 검색 DTO/도메인/Repository/UseCase를 신설(친구 탭의 users-만 파싱과 별개), Compose는 NavHost 풀스크린 `SearchRoute`+MVI VM, iOS는 셸 push `SearchView` 미러. 결과 탭은 기존 목적지(그룹 상세/게시글 상세/채팅방) 재사용, 파일은 URL 브라우저 열기.

**Tech Stack:** Kotlin Multiplatform(shared), Compose Multiplatform(M2), SwiftUI(iOS 15 폴백), Ktor client, kotlinx.serialization.

**Spec:** `docs/superpowers/specs/2026-08-14-home-search-design.md`

## Global Constraints

- **리포**: `/mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android` (이하 경로는 리포 루트 기준). 현재 브랜치 feature/friend(친구 탭 커밋 완료) — 새 브랜치 체크아웃 여부는 사용자 결정(제안: feature/search). 시작 전 1회 확인.
- **커밋 금지**: 이 리포는 스테이징+커밋 메시지 전달까지만(사용자가 직접 커밋·push). 각 태스크는 컴파일 검증으로 끝난다. 스테이징은 마지막 태스크에서 실수정 파일만 경로 지정으로 일괄(`git add -A` 금지 — CRLF 노이즈).
- **CRLF**: 워킹트리 전 파일이 M으로 보이는 drvfs/CRLF 노이즈가 있다. 수정한 파일만 스테이징 전 `git diff <경로>`로 diff가 의도한 변경만인지 확인하고, 전 파일 EOL 플립이 보이면 `sed -i 's/\r$//' <경로>`로 LF 정규화 후 재확인한다. 신규 파일은 LF로 작성.
- **Gradle**: WSL에 java가 없으면(이전 세션 실측) Windows gradle을 쓴다:
  `cmd.exe /c "cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android && set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& gradlew.bat <task>"`
  WSL에 java가 있으면 `./gradlew <task>`도 가능하나 **WSL·Windows gradle 동시 실행 금지**(캐시 lock 경합). Android `assembleDebug`는 반드시 Windows gradle(WSL에 Linux aapt2 없음).
- **Swift/Mac 미검증**: 이 환경에서 Swift 컴파일 불가. iOS 태스크는 코드 작성+pbxproj 정합성 검증까지 — Mac 검증은 사용자 몫으로 최종 보고에 명시.
- **네이밍 정렬**: 스펙의 `ClearFriendActionError`/`friendActionError`는 친구 탭 관용구에 맞춰 `DismissActionError`/`actionError`로 구현한다(FriendsViewModel 미러). users 섹션 행 액션 에러는 인라인 문구(친구 탭 미러 — 다이얼로그 아님).
- **iOS 규칙**: VM 프로퍼티 풀네임(`searchViewModel`), VM 생성자는 UseCase 주입(init(container:) 금지), KMP suspend는 `Task { @MainActor in }`, Kotlin `description` 프로퍼티는 Swift에서 `description_`.

---

### Task 1: shared 검색 DTO + 도메인 모델 (TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/SearchDtos.kt`
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/Search.kt`
- Test: `shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/SearchDtosTest.kt`

**Interfaces:**
- Consumes: 기존 `SearchUserResponse`(FriendDtos.kt), `UserSearchResult`(Friend.kt) — 그대로 재사용, FriendDtos.kt 무수정.
- Produces: `UnifiedSearchResponse`(DTO 5섹션), 도메인 `SearchResults`/`GroupSearchHit`/`PostSearchHit`/`FileSearchHit`/`MessageSearchHit` — Task 2의 Repository가 매핑, Task 3/6의 VM이 소비.

- [ ] **Step 1: 실패하는 테스트 작성**

`shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/SearchDtosTest.kt`:

```kotlin
package kr.hhp227.storygroup.shared

import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.dto.UnifiedSearchResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    // 백엔드 search/dto/SearchDtos.kt SearchResponse 5섹션과 1:1 — 대표 필드까지 확인
    @Test
    fun decodesAllFiveSections() {
        val decoded = json.decodeFromString<UnifiedSearchResponse>(
            """
            {
              "groups": [{"id": 1, "name": "산악회", "image": "https://x/img.png", "description": "설명"}],
              "posts": [{"id": 10, "groupId": 1, "groupName": "산악회", "authorName": "홍", "text": "본문", "createdAt": "2026-08-14T00:00:00Z"}],
              "files": [{"id": 20, "groupId": 1, "groupName": "산악회", "name": "a.pdf", "url": "https://x/a.pdf", "createdAt": "2026-08-14T00:00:00Z"}],
              "messages": [{"id": 30, "chatRoomId": 5, "groupId": null, "groupName": null, "authorName": "김", "text": "메시지", "createdAt": "2026-08-14T00:00:00Z"}],
              "users": [{"id": 2, "name": "이", "profileImg": null, "statusMessage": "상태"}]
            }
            """.trimIndent()
        )

        assertEquals(1, decoded.groups.size)
        assertEquals("산악회", decoded.groups[0].name)
        assertEquals(1L, decoded.posts[0].groupId)
        assertEquals("https://x/a.pdf", decoded.files[0].url)
        // DM 메시지 — groupId/groupName null
        assertNull(decoded.messages[0].groupId)
        assertEquals(5L, decoded.messages[0].chatRoomId)
        assertEquals("상태", decoded.users[0].statusMessage)
    }

    // 섹션 누락·옵셔널 누락 시 기본값으로 내려앉아야 한다(구서버·부분 응답 방어)
    @Test
    fun decodesMissingSectionsAndOptionalsToDefaults() {
        val decoded = json.decodeFromString<UnifiedSearchResponse>(
            """{"groups": [{"id": 1, "name": "이름만"}]}"""
        )

        assertEquals(emptyList(), decoded.posts)
        assertEquals(emptyList(), decoded.files)
        assertEquals(emptyList(), decoded.messages)
        assertEquals(emptyList(), decoded.users)
        assertNull(decoded.groups[0].image)
        assertNull(decoded.groups[0].description)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: gradle `:shared:jvmTest --tests "kr.hhp227.storygroup.shared.SearchDtosTest"`
Expected: **컴파일 실패** — `UnifiedSearchResponse` unresolved.

- [ ] **Step 3: DTO 구현**

`shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/SearchDtos.kt`:

```kotlin
package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// 통합검색(GET /api/search) 전체 응답 — StoryGroup-WebApp search/dto/SearchDtos.kt와 1:1.
// FriendDtos.kt의 SearchResponse(친구 탭이 users만 부분 파싱)와 별개 — 이름 충돌을 피해 Unified 접두사.
// users 섹션은 기존 SearchUserResponse를 재사용한다.
@Serializable
data class UnifiedSearchResponse(
    val groups: List<GroupSearchResponse> = emptyList(),
    val posts: List<PostSearchResponse> = emptyList(),
    val files: List<FileSearchResponse> = emptyList(),
    val messages: List<MessageSearchResponse> = emptyList(),
    val users: List<SearchUserResponse> = emptyList()
)

@Serializable
data class GroupSearchResponse(
    val id: Long,
    val name: String,
    val image: String? = null,
    val description: String? = null
)

@Serializable
data class PostSearchResponse(
    val id: Long,
    val groupId: Long,
    val groupName: String,
    val authorName: String,
    val text: String,
    val createdAt: String
)

@Serializable
data class FileSearchResponse(
    val id: Long,
    val groupId: Long,
    val groupName: String,
    val name: String,
    val url: String,
    val createdAt: String
)

// 그룹 방 메시지는 groupId/groupName 있음, DM은 null
@Serializable
data class MessageSearchResponse(
    val id: Long,
    val chatRoomId: Long,
    val groupId: Long? = null,
    val groupName: String? = null,
    val authorName: String,
    val text: String,
    val createdAt: String
)
```

`shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/Search.kt`:

```kotlin
package kr.hhp227.storygroup.shared.domain.model

/**
 * 통합검색 결과 — 웹 /search 5섹션 미러. 섹션당 서버 기본 limit 10, 더보기 없음.
 * 그룹/게시글/파일/메시지는 내가 속한 그룹 범위, users는 같은 그룹 소속만(서버 필터).
 * users는 친구 탭과 같은 UserSearchResult — 친구 추가/해제 유스케이스를 그대로 쓴다.
 */
data class SearchResults(
    val groups: List<GroupSearchHit> = emptyList(),
    val posts: List<PostSearchHit> = emptyList(),
    val files: List<FileSearchHit> = emptyList(),
    val messages: List<MessageSearchHit> = emptyList(),
    val users: List<UserSearchResult> = emptyList()
) {
    val isEmpty: Boolean
        get() = groups.isEmpty() && posts.isEmpty() && files.isEmpty() && messages.isEmpty() && users.isEmpty()
}

data class GroupSearchHit(
    val id: Long,
    val name: String,
    val image: String? = null,
    // Swift에서는 NSObject 충돌로 description_으로 브리징된다
    val description: String? = null
)

/** createdAt은 ISO 문자열 그대로 — 포맷팅은 플랫폼 UI 몫(Post 관용구) */
data class PostSearchHit(
    val id: Long,
    val groupId: Long,
    val groupName: String,
    val authorName: String,
    val text: String,
    val createdAt: String
)

data class FileSearchHit(
    val id: Long,
    val groupId: Long,
    val groupName: String,
    val name: String,
    val url: String,
    val createdAt: String
)

/** DM 메시지는 groupId/groupName null — 채팅방 제목은 authorName 폴백(알려진 한계, 스펙 참조) */
data class MessageSearchHit(
    val id: Long,
    val chatRoomId: Long,
    val groupId: Long? = null,
    val groupName: String? = null,
    val authorName: String,
    val text: String,
    val createdAt: String
)
```

- [ ] **Step 4: 테스트 통과 확인**

Run: gradle `:shared:jvmTest --tests "kr.hhp227.storygroup.shared.SearchDtosTest"`
Expected: PASS (2 tests).

---

### Task 2: shared SearchRepository + SearchUseCase + Compose AppContainer 배선

**Files:**
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/SearchRepository.kt`
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/SearchRepositoryImpl.kt`
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/SearchUseCase.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt`

**Interfaces:**
- Consumes: Task 1의 `UnifiedSearchResponse`·도메인 모델, 기존 `createApiClient` HttpClient.
- Produces: `SearchUseCase.invoke(query: String): SearchResults`(@Throws suspend) — Compose는 `container.searchUseCase`, iOS는 Task 6에서 동일 노출. Swift 호출명 `searchUseCase.invoke(query:)`.

- [ ] **Step 1: Repository 인터페이스**

`shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/SearchRepository.kt`:

```kotlin
package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.SearchResults

interface SearchRepository {
    /**
     * 통합검색 — GET /api/search 5섹션 전부 소비(친구 탭 FriendRepository.searchUsers는 users만).
     * limit은 생략(서버 기본 10, 1..20 클램프) — 5섹션이 쌓이는 화면이라 섹션당 10이면 충분.
     * 빈 검색어는 서버가 400 — 호출 전에 trim 빈 문자열을 걸러야 한다(VM 몫).
     */
    suspend fun search(query: String): Result<SearchResults>
}
```

- [ ] **Step 2: Repository 구현**

`shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/SearchRepositoryImpl.kt`:

```kotlin
package kr.hhp227.storygroup.shared.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kr.hhp227.storygroup.shared.data.network.dto.FileSearchResponse
import kr.hhp227.storygroup.shared.data.network.dto.GroupSearchResponse
import kr.hhp227.storygroup.shared.data.network.dto.MessageSearchResponse
import kr.hhp227.storygroup.shared.data.network.dto.PostSearchResponse
import kr.hhp227.storygroup.shared.data.network.dto.UnifiedSearchResponse
import kr.hhp227.storygroup.shared.domain.model.FileSearchHit
import kr.hhp227.storygroup.shared.domain.model.GroupSearchHit
import kr.hhp227.storygroup.shared.domain.model.MessageSearchHit
import kr.hhp227.storygroup.shared.domain.model.PostSearchHit
import kr.hhp227.storygroup.shared.domain.model.SearchResults
import kr.hhp227.storygroup.shared.domain.model.UserSearchResult
import kr.hhp227.storygroup.shared.domain.repository.SearchRepository

class SearchRepositoryImpl(private val client: HttpClient) : SearchRepository {
    override suspend fun search(query: String): Result<SearchResults> =
        runCatching {
            client.get("/api/search") {
                parameter("query", query)
            }.body<UnifiedSearchResponse>().toDomain()
        }
}

private fun UnifiedSearchResponse.toDomain() = SearchResults(
    groups = groups.map { it.toDomain() },
    posts = posts.map { it.toDomain() },
    files = files.map { it.toDomain() },
    messages = messages.map { it.toDomain() },
    // users 섹션은 친구 탭과 같은 도메인 모델 — 친구 추가/해제 유스케이스를 그대로 쓴다
    users = users.map { UserSearchResult(it.id, it.name, it.profileImg, it.statusMessage) }
)

private fun GroupSearchResponse.toDomain() = GroupSearchHit(id, name, image, description)

private fun PostSearchResponse.toDomain() = PostSearchHit(id, groupId, groupName, authorName, text, createdAt)

private fun FileSearchResponse.toDomain() = FileSearchHit(id, groupId, groupName, name, url, createdAt)

private fun MessageSearchResponse.toDomain() =
    MessageSearchHit(id, chatRoomId, groupId, groupName, authorName, text, createdAt)
```

- [ ] **Step 3: UseCase**

`shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/SearchUseCase.kt`:

```kotlin
package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.SearchResults
import kr.hhp227.storygroup.shared.domain.repository.SearchRepository

/** 홈 통합검색 — 5섹션 전부(users만 쓰는 친구 탭 SearchUsersUseCase와 별개) */
class SearchUseCase(
    private val searchRepository: SearchRepository
) {
    @Throws(Exception::class)
    suspend operator fun invoke(query: String): SearchResults =
        searchRepository.search(query).getOrThrow()
}
```

- [ ] **Step 4: Compose AppContainer 배선**

`composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt` 수정 3곳:

1. import 블록에 추가(알파벳 순 인접 위치):
```kotlin
import kr.hhp227.storygroup.shared.data.repository.SearchRepositoryImpl
import kr.hhp227.storygroup.shared.domain.repository.SearchRepository
import kr.hhp227.storygroup.shared.domain.usecase.SearchUseCase
```
2. repository 선언부(126행 `rtcRepository` 아래)에 추가:
```kotlin
    private val searchRepository: SearchRepository = SearchRepositoryImpl(apiClient)
```
3. 파일 끝 친구 탭 블록(214행 `searchUsersUseCase`) 아래에 추가:
```kotlin
    // 홈 통합검색 — 5섹션 전부(친구 탭 searchUsersUseCase는 users 섹션만)
    val searchUseCase = SearchUseCase(searchRepository)
```

- [ ] **Step 5: 3타깃 컴파일 검증**

Run: gradle `:shared:jvmTest :composeApp:compileKotlinJvm :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL (jvmTest에 SearchDtosTest 2개 포함).

---

### Task 3: Compose SearchViewModel

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/search/SearchViewModel.kt`

**Interfaces:**
- Consumes: Task 2 `SearchUseCase`, 기존 `GetFriendsUseCase`/`AddFriendUseCase`/`RemoveFriendUseCase`, `MviViewModel` 계약(EVENT=Nothing+emptyFlow 관용구).
- Produces: `SearchViewModel(UiState(results, isSearching, error, friendIds, processingUserId, actionError), Action(Search/ClearResults/AddFriend/RemoveFriend/DismissActionError))` — Task 4 화면이 소비, Task 6 Swift가 1:1 미러.

- [ ] **Step 1: VM 작성**

```kotlin
package kr.hhp227.storygroup.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.SearchResults
import kr.hhp227.storygroup.shared.domain.model.UserSearchResult
import kr.hhp227.storygroup.shared.domain.usecase.AddFriendUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetFriendsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RemoveFriendUseCase
import kr.hhp227.storygroup.shared.domain.usecase.SearchUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 홈 통합검색 — 웹 /search 미러(제출 기반, 5섹션 원페이지).
 * results가 null이면 검색 전(빈 상태 안내) — 친구 탭 searchResults 상태 축 미러.
 * users 섹션의 친구 여부는 응답에 없어 init에서 친구 목록을 읽어 friendIds로 판정(웹 미러).
 * iosApp SearchViewModel.swift와 1:1 미러
 */
class SearchViewModel(
    private val searchUseCase: SearchUseCase,
    private val getFriendsUseCase: GetFriendsUseCase,
    private val addFriendUseCase: AddFriendUseCase,
    private val removeFriendUseCase: RemoveFriendUseCase
) : ViewModel(), MviViewModel<SearchViewModel.UiState, SearchViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 일회성 이벤트 없음 — 결과 탭 내비게이션은 화면 콜백 몫
    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Action) {
        when (action) {
            is Action.Search -> search(action.query)
            Action.ClearResults -> _uiState.update { it.copy(results = null, error = null) }
            is Action.AddFriend -> addFriend(action.user)
            is Action.RemoveFriend -> removeFriend(action.userId)
            Action.DismissActionError -> _uiState.update { it.copy(actionError = null) }
        }
    }

    /** 통합검색(제출 기반) — 빈 검색어는 초기 화면 복귀(친구 탭 미러, 서버 400 선차단) */
    private fun search(query: String) {
        val trimmed = query.trim()

        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(results = null, error = null) }
            return
        }
        if (_uiState.value.isSearching) return

        _uiState.update { it.copy(isSearching = true, error = null) }
        viewModelScope.launch {
            runCatching { searchUseCase(trimmed) }
                .onSuccess { results ->
                    _uiState.update { it.copy(isSearching = false, results = results) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSearching = false, error = e.message ?: "검색에 실패했습니다.") }
                }
        }
    }

    /** users 섹션 버튼 판정용 친구 목록 — 실패해도 검색은 동작해야 하므로 조용히 무시(버튼은 "친구 추가" 기본, 중복 등록은 409 문구가 흡수) */
    private fun loadFriendIds() {
        viewModelScope.launch {
            runCatching { getFriendsUseCase() }
                .onSuccess { friends ->
                    _uiState.update { it.copy(friendIds = friends.map { friend -> friend.userId }.toSet()) }
                }
        }
    }

    /** 친구 등록 — friendIds만 낙관적 갱신(친구 탭과 달리 이 화면엔 친구 목록이 없다). 409는 서버 문구 노출 */
    private fun addFriend(user: UserSearchResult) {
        if (_uiState.value.processingUserId != null) return

        _uiState.update { it.copy(processingUserId = user.id, actionError = null) }
        viewModelScope.launch {
            runCatching { addFriendUseCase(user.id) }
                .onSuccess {
                    _uiState.update { it.copy(processingUserId = null, friendIds = it.friendIds + user.id) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(processingUserId = null, actionError = e.message ?: "친구 등록에 실패했습니다.")
                    }
                }
        }
    }

    private fun removeFriend(userId: Long) {
        if (_uiState.value.processingUserId != null) return

        _uiState.update { it.copy(processingUserId = userId, actionError = null) }
        viewModelScope.launch {
            runCatching { removeFriendUseCase(userId) }
                .onSuccess {
                    _uiState.update { it.copy(processingUserId = null, friendIds = it.friendIds - userId) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(processingUserId = null, actionError = e.message ?: "친구 해제에 실패했습니다.")
                    }
                }
        }
    }

    init {
        loadFriendIds()
    }

    data class UiState(
        // null=검색 전(빈 상태 안내) — 웹 /search results 상태 축 미러
        val results: SearchResults? = null,
        val isSearching: Boolean = false,
        // 검색 실패 문구 — 직전 결과를 대체하지 않고 위에 얹는다(친구 탭 미러)
        val error: String? = null,
        // users 섹션 "친구 추가/해제" 토글 판정 — init 로드+낙관적 갱신
        val friendIds: Set<Long> = emptySet(),
        // 추가/해제 버튼 로딩 표시 — 동시에 하나만 처리(친구 탭 미러)
        val processingUserId: Long? = null,
        val actionError: String? = null
    )

    sealed interface Action {
        data class Search(val query: String) : Action
        data object ClearResults : Action
        data class AddFriend(val user: UserSearchResult) : Action
        data class RemoveFriend(val userId: Long) : Action
        data object DismissActionError : Action
    }
}
```

- [ ] **Step 2: 컴파일 검증**

Run: gradle `:composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

---

### Task 4: Compose SearchScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/search/SearchScreen.kt`

**Interfaces:**
- Consumes: Task 3 `SearchViewModel`, `SgTopBar` 제목 슬롯 오버로드(SgTopBar.kt — "검색 입력폼 등" 용도로 이미 존재), `SgCard`/`SgAvatar`/`SgSectionTitle`/`SgEmptyState`, `formatRelativeTime(isoDateTime)`(ui/util/TimeFormats.kt), `LocalUriHandler`, `LocalAppContainer`.
- Produces: `SearchScreen(onBack, onOpenGroupDetail: (Long) -> Unit, onOpenPostDetail: (Long, Long) -> Unit, onOpenChatRoom: (Long, Long?, String) -> Unit, modifier, viewModel)` — Task 5의 App.kt가 배선.

- [ ] **Step 1: 화면 작성**

구조: SgTopBar(뒤로가기+제목 슬롯에 검색 필드, 자동 포커스) → 에러 인라인 문구 → 본문(검색 중=스피너 / results!=null=5섹션 LazyColumn / 검색 전=빈 상태 안내). 검색바 스타일·행 카드·버튼은 FriendsScreen.kt 미러.

```kotlin
package kr.hhp227.storygroup.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.shared.domain.model.FileSearchHit
import kr.hhp227.storygroup.shared.domain.model.GroupSearchHit
import kr.hhp227.storygroup.shared.domain.model.MessageSearchHit
import kr.hhp227.storygroup.shared.domain.model.PostSearchHit
import kr.hhp227.storygroup.shared.domain.model.SearchResults
import kr.hhp227.storygroup.shared.domain.model.UserSearchResult
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgSectionTitle
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.formatRelativeTime

/** 백스택 엔트리 스코프 VM — 화면이 default parameter로 선언(GroupDetail 패턴) */
@Composable
private fun searchViewModel(): SearchViewModel {
    val container = LocalAppContainer.current

    return viewModel {
        SearchViewModel(
            searchUseCase = container.searchUseCase,
            getFriendsUseCase = container.getFriendsUseCase,
            addFriendUseCase = container.addFriendUseCase,
            removeFriendUseCase = container.removeFriendUseCase
        )
    }
}

/**
 * 홈 통합검색 — 웹 /search 미러(5섹션 원페이지, 제출 기반).
 * 검색 전=빈 상태 안내(웹의 친구 목록은 친구 탭 몫이라 미러하지 않는다 — 스펙 확정).
 * 결과 탭: 그룹→상세, 게시글→상세, 파일→URL 브라우저, 메시지→채팅방, 사용자=친구 추가/해제 버튼만.
 * iosApp SearchView.swift와 1:1 미러
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenGroupDetail: (groupId: Long) -> Unit,
    onOpenPostDetail: (groupId: Long, postId: Long) -> Unit,
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = searchViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    var queryText by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val uriHandler = LocalUriHandler.current

    Column(modifier.fillMaxSize().background(sg.paper)) {
        SgTopBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            }
        ) {
            SearchField(
                queryText = queryText,
                onQueryChange = {
                    queryText = it
                    // 비우면 즉시 초기 화면 복귀(친구 탭 미러)
                    if (it.isEmpty()) onAction(SearchViewModel.Action.ClearResults)
                },
                onSearch = { onAction(SearchViewModel.Action.Search(queryText)) },
                focusRequester = focusRequester
            )
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        uiState.actionError?.let {
            Text(
                it,
                style = SgTheme.typography.bodySmall,
                color = sg.rust,
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp)
            )
        }
        uiState.error?.let {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(it, style = SgTheme.typography.bodySmall, color = sg.rust, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAction(SearchViewModel.Action.Search(queryText)) }) {
                    Text("다시 시도", color = sg.accent)
                }
            }
        }
        val results = uiState.results

        when {
            uiState.isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = sg.accent)
            }
            results != null -> ResultList(
                results = results,
                friendIds = uiState.friendIds,
                processingUserId = uiState.processingUserId,
                onOpenGroupDetail = onOpenGroupDetail,
                onOpenPostDetail = onOpenPostDetail,
                onOpenChatRoom = onOpenChatRoom,
                onOpenUrl = { uriHandler.openUri(it) },
                onAddFriend = { onAction(SearchViewModel.Action.AddFriend(it)) },
                onRemoveFriend = { onAction(SearchViewModel.Action.RemoveFriend(it)) }
            )
            else -> SgEmptyState(
                title = "무엇이든 찾아보세요",
                subtitle = "그룹, 게시글, 파일, 메시지, 사용자를 검색할 수 있습니다.",
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)
            )
        }
    }
}

/** 상단바 제목 슬롯 검색 필드 — 친구 탭 SearchBar 스타일 미러(linen 캡슐+IME 검색) */
@Composable
private fun SearchField(
    queryText: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(sg.paper, SgTheme.shapes.button)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = queryText,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            textStyle = SgTheme.typography.bodyMedium.copy(color = sg.ink),
            singleLine = true,
            cursorBrush = SolidColor(sg.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Box {
                    if (queryText.isEmpty()) {
                        Text("그룹, 게시글, 파일, 메시지 검색", style = SgTheme.typography.bodyMedium, color = sg.inkFaint)
                    }
                    innerTextField()
                }
            }
        )
        if (queryText.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, contentDescription = "지우기", tint = sg.inkFaint)
            }
        }
    }
}

/** 5섹션 결과 — 순서는 웹 /search 미러(사용자→그룹→게시글→파일→메시지), 빈 섹션 숨김 */
@Composable
private fun ResultList(
    results: SearchResults,
    friendIds: Set<Long>,
    processingUserId: Long?,
    onOpenGroupDetail: (Long) -> Unit,
    onOpenPostDetail: (Long, Long) -> Unit,
    onOpenChatRoom: (Long, Long?, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onAddFriend: (UserSearchResult) -> Unit,
    onRemoveFriend: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (results.isEmpty) {
        SgEmptyState(
            title = "검색 결과가 없습니다",
            subtitle = "다른 검색어로 다시 시도해보세요.",
            modifier = modifier.fillMaxWidth().padding(vertical = 48.dp)
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (results.users.isNotEmpty()) {
            item(key = "users-header") { SgSectionTitle("사용자") }
            items(results.users, key = { "user-${it.id}" }) { user ->
                UserRow(
                    user = user,
                    isFriend = user.id in friendIds,
                    isProcessing = processingUserId == user.id,
                    enabled = processingUserId == null,
                    onAddFriend = { onAddFriend(user) },
                    onRemoveFriend = { onRemoveFriend(user.id) }
                )
            }
        }
        if (results.groups.isNotEmpty()) {
            item(key = "groups-header") { SgSectionTitle("그룹") }
            items(results.groups, key = { "group-${it.id}" }) { group ->
                GroupRow(group = group, onClick = { onOpenGroupDetail(group.id) })
            }
        }
        if (results.posts.isNotEmpty()) {
            item(key = "posts-header") { SgSectionTitle("게시글") }
            items(results.posts, key = { "post-${it.id}" }) { post ->
                PostRow(post = post, onClick = { onOpenPostDetail(post.groupId, post.id) })
            }
        }
        if (results.files.isNotEmpty()) {
            item(key = "files-header") { SgSectionTitle("파일") }
            items(results.files, key = { "file-${it.id}" }) { file ->
                FileRow(file = file, onClick = { onOpenUrl(file.url) })
            }
        }
        if (results.messages.isNotEmpty()) {
            item(key = "messages-header") { SgSectionTitle("메시지") }
            items(results.messages, key = { "message-${it.id}" }) { message ->
                MessageRow(
                    message = message,
                    // DM은 방 이름이 없어 작성자 이름 폴백(알려진 한계 — 스펙 참조)
                    onClick = { onOpenChatRoom(message.chatRoomId, message.groupId, message.groupName ?: message.authorName) }
                )
            }
        }
    }
}

/** 사용자 행 — 친구 탭 SearchResultRow 미러(행 탭 무동작, 친구 추가/해제 버튼만) */
@Composable
private fun UserRow(
    user: UserSearchResult,
    isFriend: Boolean,
    isProcessing: Boolean,
    enabled: Boolean,
    onAddFriend: () -> Unit,
    onRemoveFriend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SgAvatar(user.name, imageUrl = user.profileImg)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name, style = SgTheme.typography.titleSmall, color = sg.ink)
                if (!user.statusMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(user.statusMessage.orEmpty(), style = SgTheme.typography.bodySmall, color = sg.inkFaint)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (isProcessing) {
                CircularProgressIndicator(color = sg.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (isFriend) {
                OutlinedButton(
                    onClick = onRemoveFriend,
                    enabled = enabled,
                    shape = SgTheme.shapes.button,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = sg.inkSoft)
                ) {
                    Text("친구 해제", style = SgTheme.typography.labelLarge)
                }
            } else {
                Button(
                    onClick = onAddFriend,
                    enabled = enabled,
                    shape = SgTheme.shapes.button,
                    colors = ButtonDefaults.buttonColors(backgroundColor = sg.accent, contentColor = sg.onAccent)
                ) {
                    Text("친구 추가", style = SgTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun GroupRow(group: GroupSearchHit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SgAvatar(group.name, imageUrl = group.image)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, style = SgTheme.typography.titleSmall, color = sg.ink)
                if (!group.description.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        group.description.orEmpty(),
                        style = SgTheme.typography.bodySmall,
                        color = sg.inkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PostRow(post: PostSearchHit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                post.text,
                style = SgTheme.typography.bodyMedium,
                color = sg.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${post.groupName} · ${post.authorName} · ${formatRelativeTime(post.createdAt)}",
                style = SgTheme.typography.bodySmall,
                color = sg.inkFaint
            )
        }
    }
}

/** 파일 행 — KMP에 그룹 파일 화면이 없어 탭하면 URL을 플랫폼 브라우저로 연다(스펙 확정) */
@Composable
private fun FileRow(file: FileSearchHit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                file.name,
                style = SgTheme.typography.titleSmall,
                color = sg.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${file.groupName} · ${formatRelativeTime(file.createdAt)}",
                style = SgTheme.typography.bodySmall,
                color = sg.inkFaint
            )
        }
    }
}

@Composable
private fun MessageRow(message: MessageSearchHit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                message.text,
                style = SgTheme.typography.bodyMedium,
                color = sg.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${message.authorName} · ${message.groupName ?: "DM"} · ${formatRelativeTime(message.createdAt)}",
                style = SgTheme.typography.bodySmall,
                color = sg.inkFaint
            )
        }
    }
}
```

- [ ] **Step 2: 컴파일 검증**

Run: gradle `:composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL. (SgAvatar/SgCard 시그니처가 다르면 FriendsScreen.kt의 실제 호출과 대조해 맞춘다 — SgAvatar는 `SgAvatar(name, imageUrl = ...)` 형태가 기존 사용례)

---

### Task 5: Compose 라우트 + 진입 배선 (App.kt·셸·HomeScreen)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/MainShell.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/TabShell.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/DrawerShell.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/home/HomeScreen.kt`

**Interfaces:**
- Consumes: Task 4 `SearchScreen`, 기존 라우트 `GroupDetailRoute`/`PostDetailRoute`/`ChatRoomRoute`.
- Produces: `SearchRoute` NavHost 목적지, `onOpenSearch: () -> Unit` 드릴링 체인(MainShell→Tab/DrawerShell→DestinationContent→DestinationScreen→HomeScreen).

- [ ] **Step 1: App.kt — 라우트 선언+composable+MainShell 배선**

라우트 선언부(`DiscoverGroupsRoute` 아래, App.kt 104행 부근)에 추가:

```kotlin
/** 홈 통합검색 — 웹 /search 미러(5섹션). 홈 상단바 검색 아이콘으로 진입 */
@Serializable
internal data object SearchRoute
```

import 추가: `import kr.hhp227.storygroup.ui.screens.search.SearchScreen`

`MainShell(...)` 호출(198행 부근)에 파라미터 추가(`onOpenDiscoverGroups` 아래):

```kotlin
                onOpenSearch = { navController.navigate(SearchRoute) },
```

NavHost 안(`DiscoverGroupsRoute` composable 아래)에 추가:

```kotlin
                composable<SearchRoute> {
                    Surface(color = SgTheme.colors.paper) {
                        SearchScreen(
                            onBack = { navController.popBackStack() },
                            onOpenGroupDetail = { groupId -> navController.navigate(GroupDetailRoute(groupId)) },
                            onOpenPostDetail = { groupId, postId -> navController.navigate(PostDetailRoute(groupId, postId)) },
                            onOpenChatRoom = { chatRoomId, groupId, title ->
                                navController.navigate(ChatRoomRoute(chatRoomId, groupId, title))
                            },
                            // 풀스크린이라 하단 시스템 내비바 인셋을 화면이 직접 소화
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        )
                    }
                }
```

- [ ] **Step 2: 셸 3파일 드릴링**

`MainShell.kt`: `MainShell`/`DestinationContent`/`DestinationScreen` 세 함수 시그니처에 `onOpenSearch: () -> Unit` 추가(각각 `onOpenDiscoverGroups`/`onOpenNotifications` 인접 위치, 기존 파라미터 순서 관례 유지)하고, `TabShell(...)`/`DrawerShell(...)` 호출과 `DestinationScreen(...)` 호출에 `onOpenSearch = onOpenSearch` 전달. `DestinationScreen`의 HOME 분기:

```kotlin
        MainDestination.HOME -> HomeScreen(
            onCreatePost = onCreatePost,
            onOpenPostDetail = onOpenPostDetail,
            refreshRequested = homeRefreshRequested,
            onRefreshHandled = onHomeRefreshHandled,
            onOpenNotifications = onOpenNotifications,
            onOpenSearch = onOpenSearch,
            navigationIcon = menuNavigationIcon
        )
```

`TabShell.kt`/`DrawerShell.kt`: 함수 시그니처에 `onOpenSearch: () -> Unit` 추가(`onOpenCreateGroup` 인접), 내부 `DestinationContent(...)` 호출에 `onOpenSearch = onOpenSearch` 전달.

- [ ] **Step 3: HomeScreen TODO 스텁 교체**

`HomeScreen.kt`: `HomeScreen`과 `HomeContent` 시그니처에 `onOpenSearch: () -> Unit` 추가(`onOpenNotifications` 아래), HomeScreen→HomeContent로 전달. 139행 부근 액션 블록 교체:

```kotlin
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Default.Search, contentDescription = "검색")
            }
```

- [ ] **Step 4: 컴파일+데스크톱 스모크**

Run: gradle `:composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

Run: gradle `:composeApp:run`을 백그라운드로 실행(이전 세션 검증된 절차) — 로그인 후 홈 검색 아이콘 탭→검색 화면 진입→검색 1회(예: "테스트")→섹션 렌더 확인→종료(PowerShell `Get-Process | Where-Object {$_.MainWindowTitle -eq "StoryGroup"} | Stop-Process`).
Expected: 검색 결과 표시, 콘솔 에러 없음. (서버가 살아있어야 한다 — Cloud Run 콜드스타트 30s 타임아웃은 HttpTimeout이 흡수)

- [ ] **Step 5: Android 빌드 검증**

Run: Windows gradle `:composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

---

### Task 6: iOS AppContainer + SearchViewModel.swift

**Files:**
- Modify: `iosApp/iosApp/DI/AppContainer.swift`
- Create: `iosApp/iosApp/UI/Screens/Search/SearchViewModel.swift`

**Interfaces:**
- Consumes: shared `SearchRepositoryImpl(client:)`/`SearchUseCase(searchRepository:)`(ObjC 브리징), 기존 `GetFriendsUseCase`/`AddFriendUseCase`/`RemoveFriendUseCase`, `MviViewModel` 프로토콜(`typealias Event = Never` 관용구), `Error.kotlinMessage(fallback:)`.
- Produces: `SearchViewModel(searchUseCase:getFriendsUseCase:addFriendUseCase:removeFriendUseCase:)` — Task 7 SearchView가 소유. `container.searchUseCase`.

- [ ] **Step 1: AppContainer.swift 배선**

프로퍼티 선언부(107행 `searchUsersUseCase` 아래)에 추가:

```swift
    /// 홈 통합검색 — 5섹션 전부(친구 탭 searchUsersUseCase는 users 섹션만)
    let searchUseCase: SearchUseCase
```

init 안(225행 `searchUsersUseCase = ...` 아래)에 추가(다른 repository 생성 문법과 동일하게 — 기존 `friendRepository` 생성 라인의 인자 라벨을 그대로 따른다):

```swift
        let searchRepository = SearchRepositoryImpl(client: apiClient)
        searchUseCase = SearchUseCase(searchRepository: searchRepository)
```

주의: 기존 코드에서 `FriendRepositoryImpl` 생성 인자 라벨이 `client:`가 아니면(예: `httpClient:`) 그 라벨을 따른다.

- [ ] **Step 2: SearchViewModel.swift 작성**

```swift
import Combine
import Foundation
import Shared

/// 홈 통합검색 — composeApp SearchViewModel.kt와 1:1 미러.
/// results가 nil이면 검색 전(빈 상태 안내). users 섹션 친구 여부는 init에서 친구 목록을 읽어 friendIds로 판정.
final class SearchViewModel: MviViewModel {
    /// 일회성 이벤트 없음 — 결과 탭 push는 뷰 몫(Kotlin EVENT=Nothing 미러)
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    private let searchUseCase: SearchUseCase

    private let getFriendsUseCase: GetFriendsUseCase

    private let addFriendUseCase: AddFriendUseCase

    private let removeFriendUseCase: RemoveFriendUseCase

    func onAction(_ action: Action) {
        switch action {
        case .search(let query): search(query: query)
        case .clearResults:
            uiState.results = nil
            uiState.error = nil
        case .addFriend(let user): addFriend(user: user)
        case .removeFriend(let userId): removeFriend(userId: userId)
        case .dismissActionError: uiState.actionError = nil
        }
    }

    /// 통합검색(제출 기반) — 빈 검색어는 초기 화면 복귀(친구 탭 미러, 서버 400 선차단)
    private func search(query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)

        if trimmed.isEmpty {
            uiState.results = nil
            uiState.error = nil
            return
        }
        if uiState.isSearching { return }

        uiState.isSearching = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let results = try await searchUseCase.invoke(query: trimmed)
                uiState.isSearching = false
                uiState.results = results
            } catch {
                uiState.isSearching = false
                uiState.error = error.kotlinMessage(fallback: "검색에 실패했습니다.")
            }
        }
    }

    /// users 섹션 버튼 판정용 — 실패해도 검색은 동작해야 하므로 조용히 무시(버튼은 "친구 추가" 기본)
    private func loadFriendIds() {
        Task { @MainActor in
            if let friends = try? await getFriendsUseCase.invoke() {
                uiState.friendIds = Set(friends.map { $0.userId })
            }
        }
    }

    /// 친구 등록 — friendIds만 낙관적 갱신(이 화면엔 친구 목록이 없다). 409는 서버 문구 노출
    private func addFriend(user: UserSearchResult) {
        if uiState.processingUserId != nil { return }

        uiState.processingUserId = user.id
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await addFriendUseCase.invoke(userId: user.id)
                uiState.processingUserId = nil
                uiState.friendIds.insert(user.id)
            } catch {
                uiState.processingUserId = nil
                uiState.actionError = error.kotlinMessage(fallback: "친구 등록에 실패했습니다.")
            }
        }
    }

    private func removeFriend(userId: Int64) {
        if uiState.processingUserId != nil { return }

        uiState.processingUserId = userId
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await removeFriendUseCase.invoke(userId: userId)
                uiState.processingUserId = nil
                uiState.friendIds.remove(userId)
            } catch {
                uiState.processingUserId = nil
                uiState.actionError = error.kotlinMessage(fallback: "친구 해제에 실패했습니다.")
            }
        }
    }

    init(
        searchUseCase: SearchUseCase,
        getFriendsUseCase: GetFriendsUseCase,
        addFriendUseCase: AddFriendUseCase,
        removeFriendUseCase: RemoveFriendUseCase
    ) {
        self.searchUseCase = searchUseCase
        self.getFriendsUseCase = getFriendsUseCase
        self.addFriendUseCase = addFriendUseCase
        self.removeFriendUseCase = removeFriendUseCase
        loadFriendIds()
    }

    struct UiState {
        /// nil=검색 전(빈 상태 안내) — 웹 /search results 상태 축 미러
        var results: SearchResults? = nil
        var isSearching = false
        /// 검색 실패 문구 — 직전 결과를 대체하지 않고 위에 얹는다(친구 탭 미러)
        var error: String? = nil
        /// users 섹션 "친구 추가/해제" 토글 판정 — init 로드+낙관적 갱신
        var friendIds: Set<Int64> = []
        /// 추가/해제 버튼 로딩 표시 — 동시에 하나만 처리(친구 탭 미러)
        var processingUserId: Int64? = nil
        var actionError: String? = nil
    }

    enum Action {
        case search(query: String)
        case clearResults
        case addFriend(user: UserSearchResult)
        case removeFriend(userId: Int64)
        case dismissActionError
    }
}
```

- [ ] **Step 3: 검증 (Kotlin 타입 체크만 가능)**

Run: gradle `:shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. Swift 컴파일은 불가 — FriendsViewModel.swift와 나란히 육안 대조(uiState 직접 변이 패턴·invoke 라벨·kotlinMessage 사용이 동일한지).

---

### Task 7: iOS SearchView.swift

**Files:**
- Create: `iosApp/iosApp/UI/Screens/Search/SearchView.swift`

**Interfaces:**
- Consumes: Task 6 `SearchViewModel`, `SGCard`/`SGAvatar`/`SGSectionTitle`/`SGEmptyState`(SGComponents.swift), `TimeFormats.relative(_:)`, `GroupDetailView(groupId:container:chatViewModel:theme:profileViewModel:onGroupClosed:onGroupUpdated:)`, `PostDetailView(container:groupId:postId:)`, `ChatRoomView(chatRoomId:groupId:title:container:chatViewModel:)`, `ChatRoomRef`(ChatView.swift).
- Produces: `SearchView(container:chatViewModel:theme:profileViewModel:onGroupsRefreshNeeded:)` — Task 8의 MainShellView가 push.

- [ ] **Step 1: SearchView 작성**

결과 push 3종은 자체 소유(GroupDetailView 자체 push 선례 — iOS16 navigationDestination/iOS15 숨김 NavigationLink 이중 분기). 파일은 `@Environment(\.openURL)`.

```swift
import SwiftUI
import Shared

/// 게시글 상세 push 대상 — (groupId, postId) 쌍
private struct PostRef: Equatable {
    let groupId: Int64
    let postId: Int64
}

/// 홈 통합검색 — composeApp SearchScreen.kt와 1:1 미러(제출 기반, 5섹션 원페이지).
/// 결과 push 3종(그룹 상세·게시글 상세·채팅방)은 자체 소유(GroupDetailView 선례),
/// 파일은 시스템 브라우저(openURL), 사용자는 친구 추가/해제 버튼만(행 탭 무동작).
struct SearchView: View {
    let container: AppContainer

    /// 채팅방·그룹 상세 push에 필요 — MainShellView 소유 세션 VM 전달
    @ObservedObject var chatViewModel: ChatViewModel

    /// GroupDetailView가 요구 — MainShellView에서 전달
    @ObservedObject var theme: SGThemeState

    let profileViewModel: ProfileViewModel

    /// 그룹 상세에서 나가기/삭제 시 셸의 그룹 탭 refresh 신호(MainShellView groupsRefreshPending)
    let onGroupsRefreshNeeded: () -> Void

    @StateObject private var searchViewModel: SearchViewModel

    @State private var queryText = ""

    @State private var selectedGroupId: Int64? = nil

    @State private var selectedPost: PostRef? = nil

    @State private var selectedChatRoom: ChatRoomRef? = nil

    @Environment(\.sgColors) private var colors

    @Environment(\.openURL) private var openURL

    var body: some View {
        pushContainer
            .navigationTitle("검색")
            .navigationBarTitleDisplayMode(.inline)
    }

    /// 자체 push 3종 — iOS 16 navigationDestination / iOS 15 숨김 NavigationLink 폴백(GroupDetailView 선례)
    @ViewBuilder private var pushContainer: some View {
        if #available(iOS 16.0, *) {
            content
                .navigationDestination(isPresented: showGroupDetail) { groupDetailDestination }
                .navigationDestination(isPresented: showPostDetail) { postDetailDestination }
                .navigationDestination(isPresented: showChatRoom) { chatRoomDestination }
        } else {
            content
                .background(
                    NavigationLink(isActive: showGroupDetail) { groupDetailDestination } label: { EmptyView() }.hidden()
                )
                .background(
                    NavigationLink(isActive: showPostDetail) { postDetailDestination } label: { EmptyView() }.hidden()
                )
                .background(
                    NavigationLink(isActive: showChatRoom) { chatRoomDestination } label: { EmptyView() }.hidden()
                )
        }
    }

    private var content: some View {
        VStack(spacing: 0) {
            searchBar
                .padding(.horizontal, 16)
                .padding(.top, 12)
            if let actionError = searchViewModel.uiState.actionError {
                Text(actionError)
                    .font(.footnote)
                    .foregroundColor(colors.rust)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
            }
            if let error = searchViewModel.uiState.error {
                HStack {
                    Text(error)
                        .font(.footnote)
                        .foregroundColor(colors.rust)
                    Spacer()
                    Button("다시 시도") { searchViewModel.onAction(.search(query: queryText)) }
                        .font(.footnote.weight(.semibold))
                        .foregroundColor(colors.accent)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
            }
            resultsBody
        }
        .background(colors.paper.ignoresSafeArea())
    }

    /// 인라인 검색바 — 친구 탭 검색바 미러(제출 기반, 지우면 초기 복귀)
    private var searchBar: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(colors.inkFaint)
            TextField("그룹, 게시글, 파일, 메시지 검색", text: $queryText)
                .submitLabel(.search)
                .onSubmit { searchViewModel.onAction(.search(query: queryText)) }
                .onChange(of: queryText) { newValue in
                    if newValue.isEmpty { searchViewModel.onAction(.clearResults) }
                }
            if !queryText.isEmpty {
                Button(action: {
                    queryText = ""
                    searchViewModel.onAction(.clearResults)
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(colors.inkFaint)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(colors.linen)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    @ViewBuilder private var resultsBody: some View {
        if searchViewModel.uiState.isSearching {
            Spacer()
            ProgressView().tint(colors.accent)
            Spacer()
        } else if let results = searchViewModel.uiState.results {
            if results.isEmpty {
                SGEmptyState(
                    title: "검색 결과가 없습니다",
                    subtitle: "다른 검색어로 다시 시도해보세요."
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                resultList(results)
            }
        } else {
            SGEmptyState(
                title: "무엇이든 찾아보세요",
                subtitle: "그룹, 게시글, 파일, 메시지, 사용자를 검색할 수 있습니다."
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    /// 5섹션 결과 — 순서는 웹 /search 미러(사용자→그룹→게시글→파일→메시지), 빈 섹션 숨김
    private func resultList(_ results: SearchResults) -> some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 8) {
                if !results.users.isEmpty {
                    SGSectionTitle(text: "사용자")
                    ForEach(results.users, id: \.id) { user in
                        userRow(user)
                    }
                }
                if !results.groups.isEmpty {
                    SGSectionTitle(text: "그룹")
                    ForEach(results.groups, id: \.id) { group in
                        groupRow(group)
                    }
                }
                if !results.posts.isEmpty {
                    SGSectionTitle(text: "게시글")
                    ForEach(results.posts, id: \.id) { post in
                        postRow(post)
                    }
                }
                if !results.files.isEmpty {
                    SGSectionTitle(text: "파일")
                    ForEach(results.files, id: \.id) { file in
                        fileRow(file)
                    }
                }
                if !results.messages.isEmpty {
                    SGSectionTitle(text: "메시지")
                    ForEach(results.messages, id: \.id) { message in
                        messageRow(message)
                    }
                }
            }
            .padding(16)
        }
    }

    /// 사용자 행 — 친구 탭 SearchResultRow 미러(행 탭 무동작, 버튼만)
    private func userRow(_ user: UserSearchResult) -> some View {
        SGCard {
            HStack(spacing: 12) {
                SGAvatar(name: user.name, imageUrl: user.profileImg)
                VStack(alignment: .leading, spacing: 2) {
                    Text(user.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(colors.ink)
                    if let status = user.statusMessage, !status.isEmpty {
                        Text(status)
                            .font(.footnote)
                            .foregroundColor(colors.inkFaint)
                    }
                }
                Spacer()
                if searchViewModel.uiState.processingUserId == user.id {
                    ProgressView().tint(colors.accent)
                } else if searchViewModel.uiState.friendIds.contains(user.id) {
                    Button("친구 해제") { searchViewModel.onAction(.removeFriend(userId: user.id)) }
                        .font(.footnote.weight(.semibold))
                        .foregroundColor(colors.inkSoft)
                        .disabled(searchViewModel.uiState.processingUserId != nil)
                } else {
                    Button("친구 추가") { searchViewModel.onAction(.addFriend(user: user)) }
                        .font(.footnote.weight(.semibold))
                        .foregroundColor(colors.accent)
                        .disabled(searchViewModel.uiState.processingUserId != nil)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }

    private func groupRow(_ group: GroupSearchHit) -> some View {
        Button(action: { selectedGroupId = group.id }) {
            SGCard {
                HStack(spacing: 12) {
                    SGAvatar(name: group.name, imageUrl: group.image)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(group.name)
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(colors.ink)
                        // Kotlin description은 NSObject 충돌로 description_
                        if let description = group.description_, !description.isEmpty {
                            Text(description)
                                .font(.footnote)
                                .foregroundColor(colors.inkFaint)
                                .lineLimit(1)
                        }
                    }
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .buttonStyle(.plain)
    }

    private func postRow(_ post: PostSearchHit) -> some View {
        Button(action: { selectedPost = PostRef(groupId: post.groupId, postId: post.id) }) {
            SGCard {
                VStack(alignment: .leading, spacing: 4) {
                    Text(post.text)
                        .font(.subheadline)
                        .foregroundColor(colors.ink)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Text("\(post.groupName) · \(post.authorName) · \(TimeFormats.relative(post.createdAt))")
                        .font(.footnote)
                        .foregroundColor(colors.inkFaint)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .buttonStyle(.plain)
    }

    /// 파일 행 — KMP에 그룹 파일 화면이 없어 탭하면 시스템 브라우저로 연다(스펙 확정)
    private func fileRow(_ file: FileSearchHit) -> some View {
        Button(action: {
            if let url = URL(string: file.url) { openURL(url) }
        }) {
            SGCard {
                VStack(alignment: .leading, spacing: 4) {
                    Text(file.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(colors.ink)
                        .lineLimit(1)
                    Text("\(file.groupName) · \(TimeFormats.relative(file.createdAt))")
                        .font(.footnote)
                        .foregroundColor(colors.inkFaint)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .buttonStyle(.plain)
    }

    private func messageRow(_ message: MessageSearchHit) -> some View {
        Button(action: {
            // DM은 방 이름이 없어 작성자 이름 폴백(알려진 한계 — 스펙 참조)
            selectedChatRoom = ChatRoomRef(
                chatRoomId: message.chatRoomId,
                groupId: message.groupId?.int64Value,
                title: message.groupName ?? message.authorName
            )
        }) {
            SGCard {
                VStack(alignment: .leading, spacing: 4) {
                    Text(message.text)
                        .font(.subheadline)
                        .foregroundColor(colors.ink)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Text("\(message.authorName) · \(message.groupName ?? "DM") · \(TimeFormats.relative(message.createdAt))")
                        .font(.footnote)
                        .foregroundColor(colors.inkFaint)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var groupDetailDestination: some View {
        if let groupId = selectedGroupId {
            GroupDetailView(
                groupId: groupId,
                container: container,
                chatViewModel: chatViewModel,
                theme: theme,
                profileViewModel: profileViewModel,
                onGroupClosed: {
                    selectedGroupId = nil
                    onGroupsRefreshNeeded()
                },
                onGroupUpdated: { onGroupsRefreshNeeded() }
            )
        }
    }

    @ViewBuilder private var postDetailDestination: some View {
        if let post = selectedPost {
            PostDetailView(container: container, groupId: post.groupId, postId: post.postId)
        }
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

    /// pop(백 버튼/스와이프) 시 상태를 nil로 되돌리는 브리지(MainShellView 선례)
    private var showGroupDetail: Binding<Bool> {
        Binding(
            get: { selectedGroupId != nil },
            set: { if !$0 { selectedGroupId = nil } }
        )
    }

    private var showPostDetail: Binding<Bool> {
        Binding(
            get: { selectedPost != nil },
            set: { if !$0 { selectedPost = nil } }
        )
    }

    private var showChatRoom: Binding<Bool> {
        Binding(
            get: { selectedChatRoom != nil },
            set: { if !$0 { selectedChatRoom = nil } }
        )
    }

    init(
        container: AppContainer,
        chatViewModel: ChatViewModel,
        theme: SGThemeState,
        profileViewModel: ProfileViewModel,
        onGroupsRefreshNeeded: @escaping () -> Void
    ) {
        _searchViewModel = StateObject(wrappedValue: SearchViewModel(
            searchUseCase: container.searchUseCase,
            getFriendsUseCase: container.getFriendsUseCase,
            addFriendUseCase: container.addFriendUseCase,
            removeFriendUseCase: container.removeFriendUseCase
        ))
        self.container = container
        self.chatViewModel = chatViewModel
        self.theme = theme
        self.profileViewModel = profileViewModel
        self.onGroupsRefreshNeeded = onGroupsRefreshNeeded
    }
}
```

작성 시 실제 파일과 대조할 것: `SGAvatar`/`SGCard`/`SGSectionTitle`/`SGEmptyState`의 실제 init 라벨(SGComponents.swift), `MessageSearchHit.groupId`의 브리징 타입(`KotlinLong?` → `.int64Value`), `ForEach(results.users, id: \.id)`에서 id가 Int64로 Hashable 충족하는지(안 되면 `id: \.self` 대신 `AnyHashable($0.id)` — HomeView ForEach 선례 참조).

- [ ] **Step 2: 육안 검증**

FriendsView.swift·GroupDetailView.swift와 나란히 두고 패턴 대조(push 이중 분기 구조, ChatRoomRef 필드명). Swift 컴파일 불가 — Mac 검증 항목으로 최종 보고에 기재.

---

### Task 8: iOS 셸 배선 + pbxproj 등록

**Files:**
- Modify: `iosApp/iosApp/UI/Shell/MainShellView.swift`
- Modify: `iosApp/iosApp/UI/Shell/TabShellView.swift`
- Modify: `iosApp/iosApp/UI/Shell/DrawerShellView.swift`
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`

**Interfaces:**
- Consumes: Task 7 `SearchView(container:chatViewModel:theme:profileViewModel:onGroupsRefreshNeeded:)`.
- Produces: 셸 툴바 검색 버튼 → SearchView push(셸 전체를 덮는 루트 내비 push).

- [ ] **Step 1: MainShellView.swift**

상태 추가(`showSettings` 아래):

```swift
    /// 홈 통합검색 풀스크린 push — Compose NavHost(SearchRoute) 미러
    @State private var showSearch = false
```

`navigationRoot` iOS16 분기에 추가(`.navigationDestination(isPresented: $showSettings)` 아래):

```swift
                    .navigationDestination(isPresented: $showSearch) { searchDestination }
```

iOS15 분기에 숨김 링크 추가(기존 5개와 같은 `.background(...)` 형태):

```swift
                    .background(
                        NavigationLink(isActive: $showSearch) {
                            searchDestination
                        } label: {
                            EmptyView()
                        }
                        .hidden()
                    )
```

destination 프로퍼티 추가(`settingsDestination` 아래):

```swift
    private var searchDestination: some View {
        SearchView(
            container: container,
            chatViewModel: chatViewModel,
            theme: theme,
            profileViewModel: profileViewModel,
            onGroupsRefreshNeeded: { groupsRefreshPending = true }
        )
    }
```

`shellContent`의 TabShellView/DrawerShellView 호출 양쪽에 `onOpenSearch: { showSearch = true },` 추가(`onOpenAccountSettings` 인접).

- [ ] **Step 2: TabShellView.swift / DrawerShellView.swift**

양쪽에 프로퍼티 추가(`onOpenAccountSettings` 인접):

```swift
    /// 홈 통합검색 풀스크린 push — MainShellView(루트 NavigationStack)로 위임
    let onOpenSearch: () -> Void
```

TabShellView 100행 부근 TODO 교체(DrawerShellView의 동일 스텁도 같은 교체):

```swift
                if current == .home {
                    Button(action: onOpenSearch) { Image(systemName: "magnifyingglass") }
                }
```

- [ ] **Step 3: pbxproj 등록**

현재 최대 합성 ID 스위트: 파일 0043, 그룹 A1013013. 신규:
- 그룹 `A1013014AABBCCDDEEFF0014 /* Search */` (path = Search) — `Screens` 그룹의 children에 추가하고, PBXGroup 섹션에 `A1013013 /* Call */` 블록을 본떠 정의(children = SearchView.swift, SearchViewModel.swift 참조 2개).
- 파일 참조: `A1011044AABBCCDDEEFF0044 /* SearchView.swift */`, `A1011045AABBCCDDEEFF0045 /* SearchViewModel.swift */` (PBXFileReference — 기존 FriendsViewModel.swift 라인 형식 복사).
- 빌드 파일: `A1010044AABBCCDDEEFF0044 /* SearchView.swift in Sources */`, `A1010045AABBCCDDEEFF0045 /* SearchViewModel.swift in Sources */` (PBXBuildFile 섹션+Sources 빌드 페이즈 양쪽).

- [ ] **Step 4: pbxproj 정합성 검증**

Run:
```bash
cd iosApp && \
echo "disk: $(find iosApp -name '*.swift' | wc -l)" && \
echo "refs: $(grep -c 'sourceTree = "<group>"; };' iosApp.xcodeproj/project.pbxproj || true)" && \
echo "sources: $(grep -c 'in Sources' iosApp.xcodeproj/project.pbxproj)" && \
python3 -c "s=open('iosApp.xcodeproj/project.pbxproj').read(); print('braces', s.count('{')-s.count('}'), 'parens', s.count('(')-s.count(')'))"
```
Expected: 디스크 .swift 수 == PBXFileReference의 .swift 수 == Sources 항목 수(세 카운트 일치, 이번 작업으로 각각 +2), braces/parens 차 0. (grep 패턴이 프로젝트 형식과 안 맞으면 `.swift in Sources` / `path = .*\.swift`로 세어 맞춘다)

---

### Task 9: 최종 검증 + 스테이징 + 커밋 메시지 전달

**Files:**
- Modify: `docs/superpowers/specs/2026-08-14-home-search-design.md` (에러 표기 1줄 정정)
- Stage: 이번 작업의 실수정·신규 파일 전부(아래 목록)

- [ ] **Step 1: 스펙 문구 정정**

스펙 "에러 처리" 섹션의 "친구 추가/해제 실패: 다이얼로그/알럿로 서버 문구(409 등) 노출 후 확인 시 Clear"를 "친구 추가/해제 실패: 인라인 문구로 서버 메시지(409 등) 노출(친구 탭 관용구 미러)"로 교체(구현과 일치화).

- [ ] **Step 2: 전체 재검증**

Run: gradle `:shared:jvmTest :composeApp:compileKotlinJvm :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL, 테스트 전건 통과(기존 44+2).

Run: Windows gradle `:composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: CRLF 확인 후 경로 스테이징**

수정 파일 각각 `git diff <경로>`로 의도 변경만인지 확인(전 파일 플립이면 `sed -i 's/\r$//'` 후 재확인). 스테이징(신규 10+수정 11+문서 2 = 23경로):

```bash
git add \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/SearchDtos.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/Search.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/SearchRepository.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/SearchRepositoryImpl.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/SearchUseCase.kt \
  shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/SearchDtosTest.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/search/SearchViewModel.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/search/SearchScreen.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/MainShell.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/TabShell.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/DrawerShell.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/home/HomeScreen.kt \
  iosApp/iosApp/DI/AppContainer.swift \
  iosApp/iosApp/UI/Screens/Search/SearchView.swift \
  iosApp/iosApp/UI/Screens/Search/SearchViewModel.swift \
  iosApp/iosApp/UI/Shell/MainShellView.swift \
  iosApp/iosApp/UI/Shell/TabShellView.swift \
  iosApp/iosApp/UI/Shell/DrawerShellView.swift \
  iosApp/iosApp.xcodeproj/project.pbxproj \
  docs/superpowers/specs/2026-08-14-home-search-design.md \
  docs/superpowers/plans/2026-08-14-home-search.md
```

`git status --short`로 스테이징 목록이 위와 일치하는지 확인(무관 파일 유입 금지).

- [ ] **Step 4: 커밋 메시지 전달(커밋은 사용자)**

제안 메시지:

```
홈 통합검색 — 웹 /search 5섹션 풀 미러(서버 수정 0)

- shared: UnifiedSearchResponse DTO(FriendDtos SearchResponse와 별개)+SearchResults 도메인
  +SearchRepository/SearchUseCase(GET /api/search, limit 생략=서버 기본 10), DTO 테스트 2개
- Compose: SearchRoute 풀스크린(홈 상단바 검색 아이콘 스텁 교체)+SearchScreen/ViewModel(MVI, 제출 기반)
  검색 전=빈 상태 안내, 결과 탭: 그룹→상세/게시글→상세/파일→URL/메시지→채팅방/사용자=친구 추가·해제
- iOS: SearchView/SearchViewModel 미러(pbxproj 0044/0045+Search 그룹), 셸 push+자체 push 3종
- 알려진 한계: 섹션당 10건 고정, DM 메시지 제목=작성자 이름 폴백(스펙 참조)
- 검증: jvmTest+jvm+assembleDebug+iOS klib, ⚠️Swift/Mac 미검증
```
