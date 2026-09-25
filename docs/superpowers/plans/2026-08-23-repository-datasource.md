# Repository + DataSource 정합화 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 감사 4개 항목(UI→data 상수, Remote DataSource 계층, 리포지토리 간 의존, KeyValueStorage 노출)을 동작 불변으로 개선한다.

**Architecture:** 리포지토리 11종의 전송(HTTP·STOMP 소켓)을 `data/source/`의 `XxxRemoteDataSource`(인터페이스)+`XxxRemoteDataSourceImpl`로 추출한다. 도메인 `Repository` 인터페이스는 한 글자도 바뀌지 않아 UseCase·VM(Kotlin/Swift)은 무수정 — 변경은 data 계층 내부+DI 배선(AppContainer 양쪽)에 갇힌다. 스펙: 부모 리포 `docs/superpowers/specs/2026-08-23-repository-datasource-design.md`.

**Tech Stack:** Kotlin Multiplatform, Ktor(HttpClient), STOMP(StompSocket), Cash Paging, kotlinx-coroutines-test.

## Global Constraints

- **리포**: `/mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android`, 브랜치 `refactor/repository-datasource`(`196d935` 기반). 모든 경로는 리포 루트 기준.
- **빌드는 Windows gradlew.bat**: `cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android && cmd.exe /c "gradlew.bat <task>"` (WSL gradle은 EIO로 죽음, Bash timeout 600000).
- **커밋 금지**: 스테이징(`git add <경로>`)까지만, 커밋·push는 사용자. `git add -A` 금지 — 태스크에 명시된 경로만.
- **EOL 규칙(파일별 확인 필수)**: 기존 파일 수정 전 `git show HEAD:<path> | head -1 | cat -A`로 `^M$`(CRLF) 여부 확인. **LF면** 수정 후 `sed -i 's/\r$//' <파일>` 정규화, **CRLF면** 정규화 금지·줄바꿈 보존 편집(Edit 도구는 보존함, sed만 금지). 이후 `git diff --stat <파일>`로 실변경 라인만 남았는지 확인. 알려진 CRLF: `composeApp/.../di/AppContainer.kt`, `iosApp/iosApp/DI/AppContainer.swift`. 새 파일은 LF 그대로.
- **변환 계약(전 소스 태스크 공통)**:
  - 소스 = 전송만: suspend 함수, **DTO 반환/수신, 예외 그대로 던짐**(runCatching 금지). 메소드는 기존 리포지토리의 `client.<verb>(path){…}.body<Dto>()` 호출과 1:1, **메소드명은 리포 메소드명 그대로**. 응답을 안 쓰는 호출은 반환 타입 없음(Unit).
  - 리포지토리 잔류: `runCatching`, DTO→도메인 매핑(`toDomain()` — 리포 파일 하단 private 확장 그대로 유지), 인메모리 신호(`MutableSharedFlow`/`onSuccess { tryEmit }`), `PagePagingSource`/Pager 구성(loadPage 람다 본문만 소스 호출로 교체), 조합·캐시 로직.
  - 파일: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/source/XxxRemoteDataSource.kt` **한 파일에 인터페이스+Impl 동거**. Impl 생성자 `(private val client: HttpClient)`, 소켓 소스는 `(private val client: HttpClient, tokenStorage: TokenStorage, baseUrl: String = AppLinks.BASE_URL)`.
  - **도메인 `domain/repository/*.kt` 인터페이스는 절대 수정 금지** — 바뀌면 그 자체가 결함.
  - AppContainer.kt(CRLF 주의): 소스 Impl을 `private val`로 생성해 리포에 주입. 소스·리포 모두 private, 공개는 UseCase만.
- **검증 명령(태스크 공통)**: `cmd.exe /c "gradlew.bat :composeApp:compileKotlinJvm"`(shared 포함 컴파일), 테스트는 `:shared:jvmTest`. 최종 태스크에서 5타깃 전체.
- **Swift는 컴파일 검증 불가**(Mac 없음) — Task 10에서 문법·심볼 자체 점검만 하고 미검증 기록.
- 커밋 메시지는 태스크 진행하며 이 파일 하단 "커밋 메시지 기록"에 누적.

---

### Task 1: AppLinks 신설 + StoryGroupApi 완전 제거

**Files:**
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/config/AppLinks.kt`
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/ApiClient.kt:25-27,35`
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/ChatRepositoryImpl.kt:17,41` / `NotificationRepositoryImpl.kt:21,38` / `RtcRepositoryImpl.kt:12,34`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupSettingsTab.kt:37,47,50,52`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/profile/ProfileScreen.kt:35,43,45`

**Interfaces:**
- Produces: `kr.hhp227.storygroup.shared.config.AppLinks` — `const val BASE_URL/TERMS_URL/PRIVACY_URL`. 이후 태스크의 소켓 소스가 `AppLinks.BASE_URL`을 기본값으로 쓴다.

- [ ] **Step 1: AppLinks.kt 작성**

```kotlin
package kr.hhp227.storygroup.shared.config

/**
 * 서비스 링크 상수 — 계층 중립(config)이라 data(ApiClient 기본값)와 UI(약관/공유 링크) 양쪽에서 참조한다.
 * UI가 data.network의 StoryGroupApi를 직접 import하던 계층 위반을 해소하며 이 오브젝트로 일원화.
 */
object AppLinks {
    const val BASE_URL = "https://storygroup-k4cgcgz2ya-du.a.run.app"
    const val TERMS_URL = "$BASE_URL/terms"
    const val PRIVACY_URL = "$BASE_URL/privacy"
}
```

- [ ] **Step 2: ApiClient.kt에서 StoryGroupApi 제거**

`object StoryGroupApi { … }`(25-27행) 블록 삭제, import에 `kr.hhp227.storygroup.shared.config.AppLinks` 추가, 35행 기본값을 `baseUrl: String = AppLinks.BASE_URL`로 교체.

- [ ] **Step 3: 소켓 리포 3곳 기본값 임시 교체**

Chat/Notification/Rtc RepositoryImpl 각각: import `…data.network.StoryGroupApi` → `kr.hhp227.storygroup.shared.config.AppLinks`, 생성자 기본값 `baseUrl: String = AppLinks.BASE_URL`. (이 파라미터들은 Task 8·9에서 소스로 이동하며 사라진다 — 여기선 컴파일 유지용 최소 교체.)

- [ ] **Step 4: UI 2파일 교체**

GroupSettingsTab.kt: import를 `kr.hhp227.storygroup.shared.config.AppLinks`로 바꾸고 `StoryGroupApi.DEFAULT_BASE_URL` 3곳 → `AppLinks.BASE_URL`(47행), `AppLinks.PRIVACY_URL`(50행 — 문자열 보간 대신 상수 직접), `AppLinks.TERMS_URL`(52행). ProfileScreen.kt: 동일하게 43행 → `AppLinks.TERMS_URL`, 45행 → `AppLinks.PRIVACY_URL`.

- [ ] **Step 5: 잔존 참조 0 확인 + 컴파일**

Run: `grep -rn "StoryGroupApi" shared/src composeApp/src --include=*.kt` → 0건. `cmd.exe /c "gradlew.bat :composeApp:compileKotlinJvm"` → BUILD SUCCESSFUL.

- [ ] **Step 6: EOL 확인 후 스테이징 + 메시지 기록**

수정 6파일 각각 EOL 규칙 적용 후: `git add shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/config/AppLinks.kt <수정 6파일 경로>`

---

### Task 2: KeyValueStorage 도메인 이동

**Files:**
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/storage/KeyValueStorage.kt` (인터페이스 이동)
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/storage/InMemoryKeyValueStorage.kt` (구현 분리 잔류)
- Delete: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/storage/KeyValueStorage.kt` (기존 파일 — `git rm`)
- Modify(import 1줄씩): `shared/src/androidMain/.../data/storage/SharedPreferencesKeyValueStorage.kt`, `shared/src/jvmMain/.../data/storage/FileKeyValueStorage.kt`, `composeApp/.../di/AppContainer.kt`(CRLF), `composeApp/.../ui/theme/Theme.kt`, `composeApp/src/androidMain/.../StoryGroupApplication.kt`(CRLF), `composeApp/src/jvmMain/.../main.kt`

**Interfaces:**
- Produces: `kr.hhp227.storygroup.shared.domain.storage.KeyValueStorage` (메소드 시그니처 불변: `getString/putString/remove`)

- [ ] **Step 1: 도메인 인터페이스 파일 작성**

```kotlin
package kr.hhp227.storygroup.shared.domain.storage

/**
 * 앱 설정(테마 등) 영속화 포트 — 도메인 소유, 구현은 data/storage(플랫폼 주입 패턴).
 * (Android=SharedPreferences, Desktop=파일, iOS는 SwiftUI가 UserDefaults를 직접 쓴다)
 */
interface KeyValueStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}
```

- [ ] **Step 2: InMemory 구현 분리**

`data/storage/InMemoryKeyValueStorage.kt` 신설(기존 파일의 클래스 본문 그대로 + `import kr.hhp227.storygroup.shared.domain.storage.KeyValueStorage`), 기존 `data/storage/KeyValueStorage.kt`는 `git rm`.

- [ ] **Step 3: 인터페이스 import 교체**

`grep -rln "data.storage.KeyValueStorage" shared/src composeApp/src --include=*.kt`로 **인터페이스를 import하는 파일만** 찾아 `kr.hhp227.storygroup.shared.domain.storage.KeyValueStorage`로 교체 (예상 4곳: SharedPreferencesKeyValueStorage.kt, FileKeyValueStorage.kt, AppContainer.kt, Theme.kt — 진입점 StoryGroupApplication.kt/main.kt는 구현체 import뿐이라 무변경일 수 있음, grep 결과가 기준). `InMemoryKeyValueStorage` import는 기존 `data.storage` 그대로 유효.

- [ ] **Step 4: 컴파일 확인**

Run: `cmd.exe /c "gradlew.bat :composeApp:compileKotlinJvm :composeApp:compileDebugKotlinAndroid"` → BUILD SUCCESSFUL. Theme.kt에 `shared.data` import가 더는 없음을 grep으로 확인.

- [ ] **Step 5: EOL 확인 후 스테이징 + 메시지 기록** (git rm된 파일 포함 전 경로 add)

---

### Task 3: User 시임 — 소스 추출의 표준 예시 + 테스트

**Files:**
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/source/UserRemoteDataSource.kt`
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/UserRepositoryImpl.kt` (전면 재작성 수준)
- Modify: `composeApp/.../di/AppContainer.kt` (CRLF — User 배선 1곳)
- Test: `shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/UserRepositoryImplTest.kt`

**Interfaces:**
- Produces: `UserRemoteDataSource`(아래 인터페이스 전문) — 이후 태스크들이 이 파일을 변환의 표준 예시로 삼는다.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package kr.hhp227.storygroup.shared

import kotlinx.coroutines.test.runTest
import kr.hhp227.storygroup.shared.data.network.dto.BlockedUserResponse
import kr.hhp227.storygroup.shared.data.network.dto.ProfileResponse
import kr.hhp227.storygroup.shared.data.network.dto.PublicProfileResponse
import kr.hhp227.storygroup.shared.data.repository.UserRepositoryImpl
import kr.hhp227.storygroup.shared.data.source.UserRemoteDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserRepositoryImplTest {
    /** 전송 없는 가짜 소스 — 시임(인터페이스) 도입의 실효 증명 */
    private class FakeUserRemoteDataSource(
        private val profile: ProfileResponse? = null,
        private val error: Throwable? = null
    ) : UserRemoteDataSource {
        override suspend fun getMyProfile(): ProfileResponse = error?.let { throw it } ?: profile!!
        override suspend fun updateMyProfile(name: String, profileImg: String?, bio: String?, statusMessage: String?): ProfileResponse = throw UnsupportedOperationException()
        override suspend fun changePassword(currentPassword: String, newPassword: String) = throw UnsupportedOperationException()
        override suspend fun reportUser(userId: Long, reason: String?) = throw UnsupportedOperationException()
        override suspend fun blockUser(userId: Long) = throw UnsupportedOperationException()
        override suspend fun unblockUser(userId: Long) = throw UnsupportedOperationException()
        override suspend fun getBlockedUsers(): List<BlockedUserResponse> = throw UnsupportedOperationException()
        override suspend fun getPublicProfile(userId: Long): PublicProfileResponse = throw UnsupportedOperationException()
    }

    @Test
    fun getMyProfileMapsDtoToDomain() = runTest {
        val repository = UserRepositoryImpl(
            FakeUserRemoteDataSource(
                // ProfileResponse에 필수 필드가 더 있으면 named 인자 더미로 채운다 — 검증 대상은 매핑 통과뿐
                profile = ProfileResponse(id = 1L, name = "홍희표", email = "a@b.c", profileImg = null, bio = null, statusMessage = null, isAdmin = false)
            )
        )

        val result = repository.getMyProfile()

        assertTrue(result.isSuccess)
        assertEquals("홍희표", result.getOrThrow().name)
        assertEquals(1L, result.getOrThrow().id)
    }

    @Test
    fun getMyProfileWrapsErrorInFailure() = runTest {
        val repository = UserRepositoryImpl(FakeUserRemoteDataSource(error = IllegalStateException("boom")))

        val result = repository.getMyProfile()

        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }
}
```

- [ ] **Step 2: 실패 확인** — Run: `cmd.exe /c "gradlew.bat :shared:jvmTest --tests kr.hhp227.storygroup.shared.UserRepositoryImplTest"` → FAIL (`UserRemoteDataSource` unresolved)

- [ ] **Step 3: UserRemoteDataSource.kt 작성 (인터페이스+Impl 동거 — 표준 예시)**

```kotlin
package kr.hhp227.storygroup.shared.data.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kr.hhp227.storygroup.shared.data.network.dto.BlockedUserResponse
import kr.hhp227.storygroup.shared.data.network.dto.ChangePasswordRequest
import kr.hhp227.storygroup.shared.data.network.dto.ProfileResponse
import kr.hhp227.storygroup.shared.data.network.dto.PublicProfileResponse
import kr.hhp227.storygroup.shared.data.network.dto.ReportUserRequest
import kr.hhp227.storygroup.shared.data.network.dto.UpdateProfileRequest

/** 사용자 원격 소스 — 전송·DTO만 담당, 예외는 그대로 던진다(Result 래핑·도메인 매핑은 리포지토리 몫) */
interface UserRemoteDataSource {
    suspend fun getMyProfile(): ProfileResponse
    suspend fun updateMyProfile(name: String, profileImg: String?, bio: String?, statusMessage: String?): ProfileResponse
    suspend fun changePassword(currentPassword: String, newPassword: String)
    suspend fun reportUser(userId: Long, reason: String?)
    suspend fun blockUser(userId: Long)
    suspend fun unblockUser(userId: Long)
    suspend fun getBlockedUsers(): List<BlockedUserResponse>
    suspend fun getPublicProfile(userId: Long): PublicProfileResponse
}

class UserRemoteDataSourceImpl(private val client: HttpClient) : UserRemoteDataSource {
    override suspend fun getMyProfile(): ProfileResponse =
        client.get("/api/users/me").body()

    override suspend fun updateMyProfile(name: String, profileImg: String?, bio: String?, statusMessage: String?): ProfileResponse =
        client.patch("/api/users/me") {
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest(name, profileImg, bio, statusMessage))
        }.body()

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        client.patch("/api/users/me/password") {
            contentType(ContentType.Application.Json)
            setBody(ChangePasswordRequest(currentPassword, newPassword))
        }
    }

    override suspend fun reportUser(userId: Long, reason: String?) {
        client.post("/api/users/$userId/report") {
            contentType(ContentType.Application.Json)
            setBody(ReportUserRequest(reason))
        }
    }

    override suspend fun blockUser(userId: Long) {
        client.post("/api/users/$userId/block")
    }

    override suspend fun unblockUser(userId: Long) {
        client.delete("/api/users/$userId/block")
    }

    override suspend fun getBlockedUsers(): List<BlockedUserResponse> =
        client.get("/api/users/me/blocks").body()

    override suspend fun getPublicProfile(userId: Long): PublicProfileResponse =
        client.get("/api/users/$userId").body()
}
```

- [ ] **Step 4: UserRepositoryImpl.kt 재작성**

Ktor·DTO 요청 관련 import 제거(`HttpClient`/`body`/`delete`/`get`/`patch`/`post`/`setBody`/`ContentType`/`contentType`/`ChangePasswordRequest`/`ReportUserRequest`/`UpdateProfileRequest`), `import kr.hhp227.storygroup.shared.data.source.UserRemoteDataSource` 추가. 응답 DTO import(`ProfileResponse` 등)는 매퍼가 쓰므로 유지. 클래스:

```kotlin
class UserRepositoryImpl(private val userRemoteDataSource: UserRemoteDataSource) : UserRepository {
    // 구독자(홈·그룹 피드)가 살아 있는 동안만 의미 있는 일회성 신호라 replay는 두지 않는다
    private val _userBlocks =
        MutableSharedFlow<Long>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val userBlocks: Flow<Long> = _userBlocks.asSharedFlow()

    override suspend fun getMyProfile(): Result<Profile> =
        runCatching { userRemoteDataSource.getMyProfile().toDomain() }

    override suspend fun updateMyProfile(
        name: String,
        profileImg: String?,
        bio: String?,
        statusMessage: String?
    ): Result<Profile> =
        runCatching { userRemoteDataSource.updateMyProfile(name, profileImg, bio, statusMessage).toDomain() }

    override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        runCatching { userRemoteDataSource.changePassword(currentPassword, newPassword) }

    override suspend fun reportUser(userId: Long, reason: String?): Result<Unit> =
        runCatching { userRemoteDataSource.reportUser(userId, reason) }

    override suspend fun blockUser(userId: Long): Result<Unit> =
        runCatching { userRemoteDataSource.blockUser(userId) }.onSuccess {
            // 목록은 이 알림으로 그 작성자의 글만 걷어낸다 — 재조회(refresh)는 첫 페이지부터
            // 다시 읽어 이미 쌓아둔 페이지와 스크롤 위치를 잃는다
            _userBlocks.tryEmit(userId)
        }

    override suspend fun unblockUser(userId: Long): Result<Unit> =
        runCatching { userRemoteDataSource.unblockUser(userId) }

    override suspend fun getBlockedUsers(): Result<List<BlockedUser>> =
        runCatching { userRemoteDataSource.getBlockedUsers().map { it.toDomain() } }

    override suspend fun getPublicProfile(userId: Long): Result<PublicProfile> =
        runCatching { userRemoteDataSource.getPublicProfile(userId).toDomain() }
}
```

(파일 하단 `toDomain()` private 확장 3개는 그대로 유지.)

- [ ] **Step 5: AppContainer.kt 배선 (CRLF 보존 편집)**

import 2줄 추가(`data.source.UserRemoteDataSourceImpl` — 알파벳 위치), repository 블록의 `private val userRepository: UserRepository = UserRepositoryImpl(apiClient)` →

```kotlin
    private val userRepository: UserRepository = UserRepositoryImpl(UserRemoteDataSourceImpl(apiClient))
```

(소스 Impl을 별도 val로 둘 필요가 생기는 건 Post(Task 7)의 group 소스 공유뿐 — 그 외는 인라인 생성.)

- [ ] **Step 6: 테스트 통과+컴파일** — Run: `cmd.exe /c "gradlew.bat :shared:jvmTest --tests kr.hhp227.storygroup.shared.UserRepositoryImplTest :composeApp:compileKotlinJvm"` → PASS(2)+SUCCESS

- [ ] **Step 7: EOL 확인 후 스테이징 + 메시지 기록** (4파일)

---

### Task 4: Auth·Search·Friend 소스 전환

**Files:**
- Create: `shared/.../data/source/AuthRemoteDataSource.kt`, `SearchRemoteDataSource.kt`, `FriendRemoteDataSource.kt`
- Modify: `shared/.../data/repository/AuthRepositoryImpl.kt`(85줄), `SearchRepositoryImpl.kt`(45줄), `FriendRepositoryImpl.kt`(66줄)
- Modify: `composeApp/.../di/AppContainer.kt` (CRLF — 3곳 배선)

**Interfaces:**
- Consumes: Task 3의 `UserRemoteDataSource.kt` — **변환의 표준 예시**로 먼저 읽을 것 (인터페이스+Impl 동거, 메소드명=리포 메소드명, DTO 반환, 예외 그대로).
- Produces: `AuthRemoteDataSource`/`SearchRemoteDataSource`/`FriendRemoteDataSource` + 각 Impl(client).

- [ ] **Step 1**: 각 리포지토리 파일을 읽고 Global Constraints의 변환 계약대로 소스 파일 3개를 작성한다. 리포의 모든 `client.…` 호출이 소스 메소드가 되고(1:1, DTO 반환), 리포엔 `runCatching`+`toDomain()` 매핑+인메모리 신호만 남는다. **Auth 주의**: `tokenStorage`는 로그인/로그아웃의 토큰 저장·삭제(전송 아님)에 쓰이면 리포에 유지 — `AuthRepositoryImpl(authRemoteDataSource, tokenStorage)`. 소켓·HTTP 전송에만 쓰이던 의존만 소스로 이동한다는 원칙.
- [ ] **Step 2**: 리포지토리 3개 재작성 — 도메인 인터페이스(`domain/repository/AuthRepository.kt` 등)는 무수정. `git diff`로 도메인 디렉토리 변경 0건 확인.
- [ ] **Step 3**: AppContainer.kt 배선 3곳 인라인 교체 (`AuthRepositoryImpl(AuthRemoteDataSourceImpl(apiClient), tokenStorage)` 형태, CRLF 보존).
- [ ] **Step 4**: Run: `cmd.exe /c "gradlew.bat :shared:jvmTest :composeApp:compileKotlinJvm"` → 기존 테스트 전부 PASS + SUCCESS.
- [ ] **Step 5**: EOL 확인 후 스테이징 + 메시지 기록 (7파일).

---

### Task 5: Event·Media 소스 전환

**Files:**
- Create: `shared/.../data/source/EventRemoteDataSource.kt`, `MediaRemoteDataSource.kt`
- Modify: `shared/.../data/repository/EventRepositoryImpl.kt`(110줄), `MediaRepositoryImpl.kt`(108줄)
- Modify: `composeApp/.../di/AppContainer.kt` (CRLF — 2곳 배선)

**Interfaces:**
- Consumes: Task 3의 `UserRemoteDataSource.kt`(표준 예시).
- Produces: `EventRemoteDataSource`/`MediaRemoteDataSource` + Impl(client).

- [ ] **Step 1**: 변환 계약대로 소스 2개 작성. **Media 주의**: `imageCompressor`(도메인 포트, 압축=정책)는 리포에 유지 — `MediaRepositoryImpl(mediaRemoteDataSource, imageCompressor)`. 업로드 멀티파트 등 전송 코드만 소스로. 압축 분기(플래너 수식·GIF 우회 등 로직)가 전송 코드와 얽혀 있으면 "압축 판단은 리포, 바이트 전송은 소스"로 절단한다.
- [ ] **Step 2**: 리포 2개 재작성(도메인 인터페이스 무수정 확인).
- [ ] **Step 3**: AppContainer.kt 배선 2곳(CRLF 보존). `MediaRepositoryImpl(MediaRemoteDataSourceImpl(apiClient), imageCompressor)`.
- [ ] **Step 4**: Run: `cmd.exe /c "gradlew.bat :shared:jvmTest :composeApp:compileKotlinJvm"` → PASS+SUCCESS.
- [ ] **Step 5**: EOL 확인 후 스테이징 + 메시지 기록 (5파일).

---

### Task 6: Group 소스 전환

**Files:**
- Create: `shared/.../data/source/GroupRemoteDataSource.kt`
- Modify: `shared/.../data/repository/GroupRepositoryImpl.kt`(294줄 — 최대 규모)
- Modify: `composeApp/.../di/AppContainer.kt` (CRLF — 1곳)

**Interfaces:**
- Consumes: Task 3의 표준 예시.
- Produces: `GroupRemoteDataSource` + Impl(client). **반드시 포함**: `suspend fun getMyGroups(): List<GroupResponse>` (Task 7의 PostRepositoryImpl이 라운지 판별에 소비 — `GroupResponse.isLounge` 필드는 GroupDtos.kt:16에 존재).

- [ ] **Step 1**: 변환 계약대로 소스 작성 — 페이징 관련 메소드도 "page/size 파라미터 받아 DTO 리스트 반환" 형태로 1:1 추출(Pager/`PagePagingSource` 구성은 리포 잔류, loadPage 람다 본문만 소스 호출로).
- [ ] **Step 2**: 리포 재작성(도메인 인터페이스 무수정 확인).
- [ ] **Step 3**: AppContainer.kt — Group 소스는 Task 7에서 Post도 공유하므로 **별도 private val**로: `private val groupRemoteDataSource = GroupRemoteDataSourceImpl(apiClient)` 를 리포 블록 위에 두고 `GroupRepositoryImpl(groupRemoteDataSource)`로 배선(CRLF 보존).
- [ ] **Step 4**: Run: `cmd.exe /c "gradlew.bat :shared:jvmTest :composeApp:compileKotlinJvm"` → PASS+SUCCESS.
- [ ] **Step 5**: EOL 확인 후 스테이징 + 메시지 기록 (3파일).

---

### Task 7: Post 소스 전환 + 리포지토리 간 의존 제거 + 라운지 테스트

**Files:**
- Create: `shared/.../data/source/PostRemoteDataSource.kt`
- Modify: `shared/.../data/repository/PostRepositoryImpl.kt`(238줄)
- Modify: `composeApp/.../di/AppContainer.kt` (CRLF — 1곳)
- Test: `shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/PostRepositoryImplTest.kt`

**Interfaces:**
- Consumes: Task 6의 `GroupRemoteDataSource`(`getMyGroups(): List<GroupResponse>`), Task 3의 표준 예시.
- Produces: `PostRemoteDataSource` + Impl(client). `PostRepositoryImpl(postRemoteDataSource: PostRemoteDataSource, groupRemoteDataSource: GroupRemoteDataSource)`.

- [ ] **Step 1: 실패하는 테스트 작성** — 라운지 해석이 GroupRepository 없이 소스로 동작함을 검증:

```kotlin
package kr.hhp227.storygroup.shared

import kotlinx.coroutines.test.runTest
import kr.hhp227.storygroup.shared.data.network.dto.GroupResponse
import kr.hhp227.storygroup.shared.data.repository.PostRepositoryImpl
import kr.hhp227.storygroup.shared.data.source.GroupRemoteDataSource
import kr.hhp227.storygroup.shared.data.source.PostRemoteDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostRepositoryImplTest {
    /** 라운지 판별용 최소 그룹 DTO — GroupDtos.kt 계약(GroupResponse.isLounge) 그대로 */
    private fun group(id: Long, isLounge: Boolean) = GroupResponse(
        id = id, name = "g$id", joinType = "AUTO_APPROVE", myRole = "MEMBER",
        createdAt = "2026-01-01T00:00:00", isLounge = isLounge
    )

    // Fake 2종: Post 소스는 createPost류 호출의 groupId만 기록하고, 그 외 메소드는 테스트 미사용이라
    // UnsupportedOperationException을 던진다(Task 3 FakeUserRemoteDataSource와 같은 관용구).
    // PostRemoteDataSource의 실제 메소드 목록은 Step 3에서 확정되므로, 여기서는 라운지 글쓰기 경로가
    // 소비하는 메소드(리포 createLoungePost가 호출하는 소스 메소드)만 기록형으로 구현하고
    // 반환 DTO(PostResponse)는 named 인자 더미로 채운다 — 검증 대상은 groupId 라우팅뿐.

    @Test
    fun createLoungePostRoutesToLoungeGroupId() = runTest {
        // fake GroupRemoteDataSource: [일반(1), 라운지(7)] 반환 → 라운지 id 7이 선택되어야 한다
        // fake PostRemoteDataSource: 기록된 groupId == 7L 검증
        // (구현 세부는 Step 3의 소스 시그니처 확정 후 이 골격대로 완성한다)
    }

    @Test
    fun createLoungePostFailsWhenNoLounge() = runTest {
        // fake GroupRemoteDataSource: 라운지 없는 목록 → Result.isFailure, 메시지 "라운지를 찾을 수 없습니다."
    }
}
```

⚠️ 이 골격의 주석 부분은 Step 3에서 소스 시그니처가 확정되는 즉시 실코드로 완성한다(완성 전 커밋·스테이징 금지). 검증 자산은 ① 라운지 groupId 라우팅 ② 라운지 부재 실패 메시지 두 가지다.

- [ ] **Step 2: 실패 확인** — Run: `cmd.exe /c "gradlew.bat :shared:jvmTest --tests kr.hhp227.storygroup.shared.PostRepositoryImplTest"` → FAIL (unresolved)

- [ ] **Step 3: PostRemoteDataSource 작성 + 리포 재작성**

변환 계약대로 소스 추출. 리포 생성자는 `(private val postRemoteDataSource: PostRemoteDataSource, private val groupRemoteDataSource: GroupRemoteDataSource)` — `GroupRepository` import·의존 제거. 클래스 상단 주석을 현행화(`// 라운지 피드는 그룹 목록에서 라운지를 찾아야 해서 GroupRemoteDataSource에 의존한다(같은 데이터 계층 하향 의존)`). `resolveLoungeId()`는:

```kotlin
    private suspend fun resolveLoungeId(): Long =
        groupRemoteDataSource.getMyGroups().firstOrNull { it.isLounge }?.id
            ?: error("라운지를 찾을 수 없습니다.")
```

- [ ] **Step 4: 테스트 완성 후 통과 확인** — Step 1 골격의 fake 2종을 확정 시그니처로 완성. Run: `cmd.exe /c "gradlew.bat :shared:jvmTest --tests kr.hhp227.storygroup.shared.PostRepositoryImplTest :composeApp:compileKotlinJvm"` → PASS(2)+SUCCESS

- [ ] **Step 5: AppContainer.kt 배선** — `PostRepositoryImpl(PostRemoteDataSourceImpl(apiClient), groupRemoteDataSource)` (Task 6의 공유 val 사용, CRLF 보존).

- [ ] **Step 6: EOL 확인 후 스테이징 + 메시지 기록** (4파일).

---

### Task 8: Notification·Rtc 소켓 소스 전환

**Files:**
- Create: `shared/.../data/source/NotificationRemoteDataSource.kt`, `RtcRemoteDataSource.kt`
- Modify: `shared/.../data/repository/NotificationRepositoryImpl.kt`(146줄), `RtcRepositoryImpl.kt`(125줄)
- Modify: `composeApp/.../di/AppContainer.kt` (CRLF — 2곳)

**Interfaces:**
- Consumes: Task 3의 표준 예시, Task 1의 `AppLinks.BASE_URL`.
- Produces: `NotificationRemoteDataSource`/`RtcRemoteDataSource` + Impl`(client, tokenStorage, baseUrl = AppLinks.BASE_URL)`.

- [ ] **Step 1**: 소켓 계약(Global Constraints) 적용 — 소스 Impl이 `StompSocket` 인스턴스를 소유한다. Notification 1개(`private val socket = StompSocket(client, baseUrl, tokenStorage)`), **Rtc는 토픽/시그널 2개 분리 유지**(기존 RtcRepositoryImpl.kt:37-42 주석·구조 그대로 이동). destination 조립과 `trySend`도 소스 메소드로: 예) `NotificationRemoteDataSource.subscribePersonalEvents(): Flow<StompSessionEvent>`(= 기존 `socket.subscribe("/user/queue/notifications")`). 소스는 `Flow<StompSessionEvent>`를 그대로 반환 — **이벤트 파싱·DTO 역직렬화·도메인 매핑은 리포 잔류**(스펙 §2, 실시간 경로 동작 불변).
- [ ] **Step 2**: HTTP 호출도 동일 계약으로 소스에 1:1 추출. 리포 생성자에서 client/tokenStorage/baseUrl 제거(전송 외 용도가 없다면 — 있으면 그 의존만 유지하고 리포트에 기록). 도메인 인터페이스 무수정 확인.
- [ ] **Step 3**: AppContainer.kt 배선 2곳: `NotificationRepositoryImpl(NotificationRemoteDataSourceImpl(apiClient, tokenStorage))` 형태(CRLF 보존).
- [ ] **Step 4**: Run: `cmd.exe /c "gradlew.bat :shared:jvmTest :composeApp:compileKotlinJvm"` → PASS+SUCCESS.
- [ ] **Step 5**: EOL 확인 후 스테이징 + 메시지 기록 (5파일).

---

### Task 9: Chat 소켓 소스 전환

**Files:**
- Create: `shared/.../data/source/ChatRemoteDataSource.kt`
- Modify: `shared/.../data/repository/ChatRepositoryImpl.kt`(202줄)
- Modify: `composeApp/.../di/AppContainer.kt` (CRLF — 1곳)

**Interfaces:**
- Consumes: Task 3의 표준 예시, Task 8과 동일한 소켓 계약.
- Produces: `ChatRemoteDataSource` + Impl`(client, tokenStorage, baseUrl = AppLinks.BASE_URL)`.

- [ ] **Step 1**: 소스 작성 — 소켓 1개 소유, `subscribeRoom(chatRoomId): Flow<StompSessionEvent>`(기존 `socket.subscribe("/topic/chat-rooms/$chatRoomId")`), `sendTyping(chatRoomId)`(기존 `socket.trySend("/app/chat-rooms/$chatRoomId/typing")`) 등 destination 조립 전부 이동. HTTP(메시지 목록/전송/읽음/방 목록 등)도 1:1 추출. 이벤트 파싱→DTO→도메인 매핑은 리포 잔류.
- [ ] **Step 2**: 리포 재작성(도메인 인터페이스 무수정 확인) + AppContainer.kt 배선 `ChatRepositoryImpl(ChatRemoteDataSourceImpl(apiClient, tokenStorage))`(CRLF 보존).
- [ ] **Step 3**: Run: `cmd.exe /c "gradlew.bat :shared:jvmTest :composeApp:compileKotlinJvm"` → PASS+SUCCESS.
- [ ] **Step 4**: EOL 확인 후 스테이징 + 메시지 기록 (3파일).

---

### Task 10: AppContainer.swift 미러 재배선

**Files:**
- Modify: `iosApp/iosApp/DI/AppContainer.swift` (CRLF — 커밋본 CRLF 확인됨, sed 금지)

**Interfaces:**
- Consumes: Task 3~9가 만든 소스 11종의 Kotlin 시그니처(Swift 라벨은 Kotlin 파라미터명 그대로: `UserRemoteDataSourceImpl(client:)`, `ChatRemoteDataSourceImpl(client:tokenStorage:baseUrl:)` — 기본값 있는 baseUrl은 생략 가능).

- [ ] **Step 1**: init에서 소스 Impl 11개를 로컬 상수로 생성하고(리포 로컬 변수들 위), 리포 생성 11곳의 인자를 소스로 교체 — Kotlin AppContainer.kt(완성본)를 열어 배선을 1:1로 미러한다. `groupRemoteDataSource`는 Group·Post 두 리포가 공유(Kotlin과 동일). Auth는 `AuthRepositoryImpl(authRemoteDataSource: …, tokenStorage: tokenStorage)`, Media는 `(mediaRemoteDataSource: …, imageCompressor: …)` — Kotlin 최종 생성자와 라벨·순서 일치 필수.
- [ ] **Step 2**: 자체 점검 — Kotlin 소스 파일 11개의 클래스명·생성자 파라미터명과 Swift 호출부 라벨을 하나씩 대조(컴파일 불가 환경이므로 이 대조가 유일한 검증). 결과를 리포트에 표로 기록. Swift 미검증 사실 명기.
- [ ] **Step 3**: CRLF 보존 확인(`git diff --stat` 실변경만) 후 스테이징 + 메시지 기록 (1파일).

---

### Task 11: 최종 일괄 검증 + 인수인계

**Files:** 없음 (검증·기록만) + 이 계획 문서 스테이징

- [ ] **Step 1**: Run: `cmd.exe /c "gradlew.bat :shared:jvmTest :composeApp:jvmTest :shared:compileDebugKotlinAndroid :composeApp:compileDebugKotlinAndroid :shared:compileKotlinIosArm64"` → BUILD SUCCESSFUL, 기존+신규(User 2, Post 2) 테스트 전부 그린.
- [ ] **Step 2**: 계층 재검증 grep 3종 — ① `grep -rn "import kr.hhp227.storygroup.shared.data" shared/src/commonMain/…/domain/` 0건 ② `grep -rln "shared.data.network\|shared.data.storage\|shared.data.repository" composeApp/src/commonMain/…/ui/` 0건 ③ `grep -rn "HttpClient" shared/…/data/repository/*.kt` 0건(리포에 client 잔존 없음). 결과 기록.
- [ ] **Step 3**: `git status --short | grep -v "^ M"`로 이 리팩터의 파일들만 스테이징됐는지 확인, `git add docs/superpowers/plans/2026-08-23-repository-datasource.md`, 사용자 인수인계(변경 요약+커밋 메시지).

---

## 커밋 메시지 기록

기본 제안(단일 커밋):

```
refactor: Repository+DataSource 패턴 정합화 — Remote 소스 계층 도입

- data/source에 XxxRemoteDataSource(인터페이스+Impl) 11종 신설 — 전송(HTTP·STOMP)·DTO는 소스, Result 래핑·도메인 매핑·인메모리 신호·Pager는 리포지토리(동작 불변, 도메인 Repository 인터페이스 무수정)
- STOMP 소켓 소유·destination·trySend를 소스로 이동(Chat 1·Notification 1·Rtc 2개 분리 유지), 이벤트 파싱은 리포 잔류
- PostRepositoryImpl의 GroupRepository 의존 제거 → GroupRemoteDataSource 하향 의존(라운지 판별은 GroupResponse.isLounge)
- StoryGroupApi 제거 → 계층 중립 config/AppLinks(BASE_URL/TERMS/PRIVACY), UI의 data.network 직접 참조 해소(Kotlin 2파일+Swift 4파일 9곳 잔존 참조까지 일소)
- StompSessionEvent internal→public 완화(그 1줄만) — 소켓 구독 메소드를 소스 인터페이스 멤버로 유지하기 위한 결정
- KeyValueStorage 인터페이스 domain/storage로 이동(구현은 data 잔류) — Theme의 UI→data 의존 해소
- AppContainer(Kotlin/Swift) 소스 배선 미러, 시임 테스트 4개(User 매핑·실패 래핑, Post 라운지 라우팅·부재 실패)
- ⚠️ Swift 컴파일 미검증(Mac 없음)

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```

구현 결과 메모(2026-08-23 실행 완료): 검증 = 47테스트·5타깃 컴파일·계층 grep 4종 0건. 후속 과제 = 리포 3곳(Friend/Group/Post)의 ClientRequestException 문구 변환을 타입드 예외로 치환, Chat/소켓 소스 시임 테스트 보강. Mac 확보 시 iOS 전체 컴파일 확인(소스 11종 신규 노출+AppLinks.shared 접근).
