# 그룹 상세 일정·설정 탭 + 5탭 ViewModel 분리 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 그룹 상세의 일정(웹 캘린더 풀 미러)·설정(웹 미러+나가기) 탭을 구현하고, 5탭 전부를 레거시처럼 탭별 전용 ViewModel로 분리한다.

**Architecture:** 서버 수정 0(기배포 events CRUD+RSVP, group PATCH/DELETE/leave 소비). shared에 Event 계층+Group 설정 3메서드 추가 → Compose/iOS 각각 화면 VM 1+탭 VM 5로 재편 → 일정 탭(월 캘린더+RSVP+생성 폼), 설정 탭(OWNER 폼+위험구역 / 비OWNER 나가기).

**Tech Stack:** KMP(shared Ktor+kotlinx.serialization), Compose Multiplatform M2(material — material3 아님), SwiftUI(iOS 15), cash Paging(기존 화면만 — 신규 탭은 페이징 없음).

**스펙:** `docs/superpowers/specs/2026-08-12-group-events-settings-tabs-design.md`

## Global Constraints

- **커밋 금지**: 각 태스크의 Commit 단계는 **실수정 파일만 경로 지정 `git add`** 로 대체한다(`git add -A` 절대 금지 — 리포에 CRLF 플립 노이즈 파일 다수). 커밋·push는 사용자 몫.
- 리포 루트: `/mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android`. 신규 파일은 **LF**로 작성(이 리포 커밋본은 LF).
- 컴파일 검증(WSL에서 Windows gradle — WSL·Windows gradle 동시 실행 금지):
  `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat <태스크>"`
  파이프로 감쌀 땐 `set -o pipefail` 필수(실패가 exit 0으로 가려짐).
- MVI 3요소 관례: nested `UiState`/`sealed interface Action`/`sealed interface Event`, 진입은 `onAction` 하나, 일회성은 Event(`MutableSharedFlow(extraBufferCapacity=1, DROP_OLDEST)`+`tryEmit` ↔ Swift `PassthroughSubject`). 이벤트 없으면 Kotlin `EVENT=Nothing`+`emptyFlow()`, Swift `typealias Event = Never`.
- iOS: VM 생성자는 **UseCase 주입**(`init(container:)` 금지), VM 프로퍼티는 풀네임 `xxxViewModel`, 뷰 생성자 파라미터 순서는 Compose와 1:1. 신규 Swift 파일은 **pbxproj 수동 등록**(PBXBuildFile+PBXFileReference+PBXGroup+Sources 4곳, ID 규칙 `A1010xxx…`/`A1011xxx…` — 기존 마지막 ID를 grep으로 확인 후 이어서).
- Compose는 M2: `androidx.compose.material.*`, 색·타이포는 `SgTheme.colors/typography/shapes` 파사드만.
- 서버 시각은 ISO 문자열 원문을 도메인에 유지 — 로컬 변환은 플랫폼 UI 계층.
- ⚠️ `PATCH /api/groups/{id}`는 name/description/image **전체 교체 계약**(null=null로 덮어씀), joinType만 null=유지.
- Swift 컴파일은 Mac 부재로 검증 불가 — Kotlin 3타깃(android/jvm/iosSimulatorArm64 klib)까지가 검증 범위.

---

### Task 1: shared 일정 DTO + 도메인 모델 + 직렬화 테스트

**Files:**
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/EventDtos.kt`
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/GroupEvent.kt`
- Test: `shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/EventDtosTest.kt`

**Interfaces:**
- Produces: `EventResponse`/`EventDetailResponse`/`EventAttendeeResponse`/`CreateEventRequest`/`RsvpRequest`(DTO), `GroupEvent`/`RsvpStatus`/`EventAttendee`/`GroupEventDetail`(도메인) — 아래 정의 그대로. Task 2가 소비.

- [ ] **Step 1: 실패하는 테스트 작성** — `EventDtosTest.kt`:

```kotlin
package kr.hhp227.storygroup.shared

import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.dto.EventDetailResponse
import kr.hhp227.storygroup.shared.data.network.dto.EventResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    // 서버 EventResponse(V19) 전체 필드 — myRsvp null=미응답, endsAt null=종료 시각 없는 일정
    @Test
    fun decodesEventResponse() {
        val decoded = json.decodeFromString<EventResponse>(
            """{"id":1,"groupId":31,"userId":3,"authorName":"홍","authorProfileImg":null,
               |"title":"정기 모임","description":"장소 미정","location":"강남",
               |"startsAt":"2026-08-15T19:00:00+09:00","endsAt":null,
               |"createdAt":"2026-08-12T00:00:00Z","goingCount":2,"maybeCount":1,
               |"notGoingCount":0,"myRsvp":"GOING"}""".trimMargin().replace("\n", "")
        )
        assertEquals(1L, decoded.id)
        assertEquals("GOING", decoded.myRsvp)
        assertNull(decoded.endsAt)
        assertEquals(2L, decoded.goingCount)
    }

    @Test
    fun decodesDetailWithAttendees() {
        val decoded = json.decodeFromString<EventDetailResponse>(
            """{"event":{"id":1,"groupId":31,"userId":3,"authorName":"홍","title":"모임",
               |"startsAt":"2026-08-15T19:00:00+09:00","createdAt":"2026-08-12T00:00:00Z",
               |"goingCount":1,"maybeCount":0,"notGoingCount":0,"myRsvp":null},
               |"attendees":[{"userId":3,"name":"홍","profileImg":null,"status":"GOING"}]}"""
                .trimMargin().replace("\n", "")
        )
        assertEquals(1, decoded.attendees.size)
        assertEquals("GOING", decoded.attendees[0].status)
        assertNull(decoded.event.myRsvp)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat :shared:jvmTest --tests kr.hhp227.storygroup.shared.EventDtosTest"`
Expected: 컴파일 실패("unresolved reference EventResponse")

- [ ] **Step 3: DTO 작성** — `EventDtos.kt`(기존 `GroupDtos.kt` 스타일 그대로):

```kotlin
package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp event/dto/EventDtos.kt 계약과 1:1 (V19, 리비전 00066부터 서빙)

// GET /api/groups/{gid}/events 항목·RSVP/생성 응답 공용
@Serializable
data class EventResponse(
    val id: Long,
    val groupId: Long,
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String? = null,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startsAt: String,
    // null = 종료 시각 없는 일정(시작 시각만 공지)
    val endsAt: String? = null,
    val createdAt: String,
    val goingCount: Long = 0,
    val maybeCount: Long = 0,
    val notGoingCount: Long = 0,
    // 조회자 본인의 RSVP 상태 — null이면 아직 응답 안 함
    val myRsvp: String? = null
)

// GET /api/groups/{gid}/events/{eid} 응답 — 목록 항목 + 참석자 명단
@Serializable
data class EventDetailResponse(
    val event: EventResponse,
    val attendees: List<EventAttendeeResponse> = emptyList()
)

@Serializable
data class EventAttendeeResponse(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    val status: String
)

// POST /api/groups/{gid}/events 요청 본문 — title ≤100, description ≤2000, location ≤200
@Serializable
data class CreateEventRequest(
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startsAt: String,
    val endsAt: String? = null
)

// PUT /api/groups/{gid}/events/{eid}/rsvp 요청 본문
@Serializable
data class RsvpRequest(
    val status: String
)
```

- [ ] **Step 4: 도메인 모델 작성** — `GroupEvent.kt`:

```kotlin
package kr.hhp227.storygroup.shared.domain.model

/** 일정 참석 응답 상태 — 서버 event_rsvps.status CHECK와 1:1 */
enum class RsvpStatus { GOING, MAYBE, NOT_GOING }

/**
 * 그룹 일정(GET /api/groups/{id}/events) 도메인 모델 — 시각은 서버 ISO-8601 원문,
 * 로컬 날짜 귀속·표시 포맷팅은 각 플랫폼 UI가 담당. 카운트·myRsvp는 RSVP 응답마다
 * 서버가 집계해 돌려준다(웹 EventCard onChange 미러 — 낙관적 갱신 없음).
 */
data class GroupEvent(
    val id: Long,
    val groupId: Long,
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String? = null,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startsAt: String,
    // null = 종료 시각 없는 일정(시작 시각만 공지)
    val endsAt: String? = null,
    val createdAt: String = "",
    val goingCount: Long = 0,
    val maybeCount: Long = 0,
    val notGoingCount: Long = 0,
    // null = 아직 응답 안 함
    val myRsvp: RsvpStatus? = null
)

/** 참석자 명단 항목 — 단건 조회에만 실려 온다(멤버 목록처럼 차단 무관 표시) */
data class EventAttendee(
    val userId: Long,
    val name: String,
    val profileImg: String? = null,
    val status: RsvpStatus
)

/** 단건 조회(GET .../events/{id}) — 목록 항목 + 참석자 명단 */
data class GroupEventDetail(
    val event: GroupEvent,
    val attendees: List<EventAttendee>
)
```

- [ ] **Step 5: 테스트 통과 확인**

Run: Step 2와 동일 명령
Expected: PASS (기존 shared 테스트 포함 전부 녹색)

- [ ] **Step 6: 스테이징**

```bash
git add shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/EventDtos.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/GroupEvent.kt \
  shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/EventDtosTest.kt
```

---

### Task 2: shared EventRepository + UseCase 6종 + Compose 컨테이너 등록

**Files:**
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/EventRepository.kt`
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/EventRepositoryImpl.kt`
- Create(6개): `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/` 아래 `GetGroupEventsUseCase.kt` `GetEventDetailUseCase.kt` `CreateEventUseCase.kt` `DeleteEventUseCase.kt` `RsvpEventUseCase.kt` `CancelEventRsvpUseCase.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt`

**Interfaces:**
- Consumes: Task 1의 DTO·도메인 타입.
- Produces(후속 태스크가 이 시그니처 그대로 소비):
  - `GetGroupEventsUseCase(groupId: Long, fromIso: String, toIso: String): List<GroupEvent>`
  - `GetEventDetailUseCase(groupId: Long, eventId: Long): GroupEventDetail`
  - `CreateEventUseCase(groupId: Long, title: String, description: String?, location: String?, startsAtIso: String, endsAtIso: String?): GroupEvent`
  - `DeleteEventUseCase(groupId: Long, eventId: Long)`
  - `RsvpEventUseCase(groupId: Long, eventId: Long, status: RsvpStatus): GroupEvent`
  - `CancelEventRsvpUseCase(groupId: Long, eventId: Long): GroupEvent`
  - AppContainer 공개 프로퍼티 6개: `getGroupEventsUseCase` `getEventDetailUseCase` `createEventUseCase` `deleteEventUseCase` `rsvpEventUseCase` `cancelEventRsvpUseCase`

- [ ] **Step 1: EventRepository 인터페이스**

```kotlin
package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.model.GroupEventDetail
import kr.hhp227.storygroup.shared.domain.model.RsvpStatus

interface EventRepository {
    /** 월 범위 일정 목록 — GET /api/groups/{id}/events?from=&to= (starts_at 기준 [from, to)) */
    suspend fun listEvents(groupId: Long, fromIso: String, toIso: String): Result<List<GroupEvent>>

    /** 일정 단건+참석자 명단 — GET /api/groups/{id}/events/{eventId} */
    suspend fun getEvent(groupId: Long, eventId: Long): Result<GroupEventDetail>

    /** 일정 생성(멤버 누구나, 작성자 자동 GOING) — POST /api/groups/{id}/events */
    suspend fun createEvent(
        groupId: Long,
        title: String,
        description: String?,
        location: String?,
        startsAtIso: String,
        endsAtIso: String?
    ): Result<GroupEvent>

    /** 일정 삭제(작성자/방장/부방장) — DELETE /api/groups/{id}/events/{eventId} */
    suspend fun deleteEvent(groupId: Long, eventId: Long): Result<Unit>

    /** RSVP 응답(upsert) — PUT .../rsvp, 응답은 집계 갱신된 일정 */
    suspend fun rsvp(groupId: Long, eventId: Long, status: RsvpStatus): Result<GroupEvent>

    /** RSVP 취소 — DELETE .../rsvp, 응답은 집계 갱신된 일정 */
    suspend fun cancelRsvp(groupId: Long, eventId: Long): Result<GroupEvent>
}
```

- [ ] **Step 2: EventRepositoryImpl** (`GroupRepositoryImpl` 스타일 — runCatching+toDomain 하단 배치):

```kotlin
package kr.hhp227.storygroup.shared.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kr.hhp227.storygroup.shared.data.network.dto.CreateEventRequest
import kr.hhp227.storygroup.shared.data.network.dto.EventAttendeeResponse
import kr.hhp227.storygroup.shared.data.network.dto.EventDetailResponse
import kr.hhp227.storygroup.shared.data.network.dto.EventResponse
import kr.hhp227.storygroup.shared.data.network.dto.RsvpRequest
import kr.hhp227.storygroup.shared.domain.model.EventAttendee
import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.model.GroupEventDetail
import kr.hhp227.storygroup.shared.domain.model.RsvpStatus
import kr.hhp227.storygroup.shared.domain.repository.EventRepository

class EventRepositoryImpl(private val client: HttpClient) : EventRepository {

    override suspend fun listEvents(groupId: Long, fromIso: String, toIso: String): Result<List<GroupEvent>> =
        runCatching {
            client.get("/api/groups/$groupId/events") {
                parameter("from", fromIso)
                parameter("to", toIso)
            }.body<List<EventResponse>>().map { it.toDomain() }
        }

    override suspend fun getEvent(groupId: Long, eventId: Long): Result<GroupEventDetail> =
        runCatching {
            val response = client.get("/api/groups/$groupId/events/$eventId").body<EventDetailResponse>()

            GroupEventDetail(
                event = response.event.toDomain(),
                attendees = response.attendees.map { it.toDomain() }
            )
        }

    override suspend fun createEvent(
        groupId: Long,
        title: String,
        description: String?,
        location: String?,
        startsAtIso: String,
        endsAtIso: String?
    ): Result<GroupEvent> = runCatching {
        client.post("/api/groups/$groupId/events") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateEventRequest(
                    title = title,
                    description = description,
                    location = location,
                    startsAt = startsAtIso,
                    endsAt = endsAtIso
                )
            )
        }.body<EventResponse>().toDomain()
    }

    override suspend fun deleteEvent(groupId: Long, eventId: Long): Result<Unit> =
        runCatching {
            client.delete("/api/groups/$groupId/events/$eventId")
            Unit
        }

    override suspend fun rsvp(groupId: Long, eventId: Long, status: RsvpStatus): Result<GroupEvent> =
        runCatching {
            client.put("/api/groups/$groupId/events/$eventId/rsvp") {
                contentType(ContentType.Application.Json)
                setBody(RsvpRequest(status = status.name))
            }.body<EventResponse>().toDomain()
        }

    override suspend fun cancelRsvp(groupId: Long, eventId: Long): Result<GroupEvent> =
        runCatching {
            client.delete("/api/groups/$groupId/events/$eventId/rsvp").body<EventResponse>().toDomain()
        }
}

private fun EventResponse.toDomain() = GroupEvent(
    id = id,
    groupId = groupId,
    userId = userId,
    authorName = authorName,
    authorProfileImg = authorProfileImg,
    title = title,
    description = description,
    location = location,
    startsAt = startsAt,
    endsAt = endsAt,
    createdAt = createdAt,
    goingCount = goingCount,
    maybeCount = maybeCount,
    notGoingCount = notGoingCount,
    // 미지의 값은 미응답으로 흡수 — 서버가 상태를 늘려도 목록이 통째로 깨지지 않는다
    myRsvp = myRsvp?.let { raw -> RsvpStatus.entries.firstOrNull { it.name == raw } }
)

private fun EventAttendeeResponse.toDomain() = EventAttendee(
    userId = userId,
    name = name,
    profileImg = profileImg,
    status = RsvpStatus.entries.firstOrNull { it.name == status } ?: RsvpStatus.GOING
)
```

- [ ] **Step 3: UseCase 6종** (파일당 하나, 전부 같은 꼴 — `GetGroupUseCase` 스타일):

```kotlin
// GetGroupEventsUseCase.kt
package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.repository.EventRepository

/** 월 범위 일정 목록 — 캘린더가 [월초, 다음달 초) ISO를 넘긴다(웹 listEvents 미러) */
class GetGroupEventsUseCase(private val eventRepository: EventRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, fromIso: String, toIso: String): List<GroupEvent> =
        eventRepository.listEvents(groupId, fromIso, toIso).getOrThrow()
}

// GetEventDetailUseCase.kt
package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupEventDetail
import kr.hhp227.storygroup.shared.domain.repository.EventRepository

/** 일정 단건+참석자 명단 — 카드의 참석자 펼침이 lazy 조회한다(웹 toggleAttendees 미러) */
class GetEventDetailUseCase(private val eventRepository: EventRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, eventId: Long): GroupEventDetail =
        eventRepository.getEvent(groupId, eventId).getOrThrow()
}

// CreateEventUseCase.kt
package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.repository.EventRepository

/** 일정 생성(멤버 누구나, 작성자 자동 GOING — 서버 정책) */
class CreateEventUseCase(private val eventRepository: EventRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        groupId: Long,
        title: String,
        description: String?,
        location: String?,
        startsAtIso: String,
        endsAtIso: String?
    ): GroupEvent =
        eventRepository.createEvent(groupId, title, description, location, startsAtIso, endsAtIso).getOrThrow()
}

// DeleteEventUseCase.kt
package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.EventRepository

/** 일정 삭제 — 작성자 본인 또는 방장/부방장(서버 검증, 댓글 삭제 규칙과 동일) */
class DeleteEventUseCase(private val eventRepository: EventRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, eventId: Long) {
        eventRepository.deleteEvent(groupId, eventId).getOrThrow()
    }
}

// RsvpEventUseCase.kt
package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.model.RsvpStatus
import kr.hhp227.storygroup.shared.domain.repository.EventRepository

/** RSVP 응답(upsert) — 반환된 일정으로 카드를 교체한다(집계 갱신 포함, 낙관적 갱신 없음) */
class RsvpEventUseCase(private val eventRepository: EventRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, eventId: Long, status: RsvpStatus): GroupEvent =
        eventRepository.rsvp(groupId, eventId, status).getOrThrow()
}

// CancelEventRsvpUseCase.kt
package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.repository.EventRepository

/** RSVP 취소 — 같은 상태 버튼 재탭이 취소다(웹 handleRsvp 토글 미러) */
class CancelEventRsvpUseCase(private val eventRepository: EventRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, eventId: Long): GroupEvent =
        eventRepository.cancelRsvp(groupId, eventId).getOrThrow()
}
```

- [ ] **Step 4: AppContainer 등록** — `AppContainer.kt`에 (import 정렬 유지하며) 추가:

```kotlin
// 리포지토리 선언부(private val rtcRepository 아래)에:
    private val eventRepository: EventRepository = EventRepositoryImpl(apiClient)

// 유스케이스 공개 프로퍼티 블록(그룹 관련 근처)에:
    val getGroupEventsUseCase = GetGroupEventsUseCase(eventRepository)
    val getEventDetailUseCase = GetEventDetailUseCase(eventRepository)
    val createEventUseCase = CreateEventUseCase(eventRepository)
    val deleteEventUseCase = DeleteEventUseCase(eventRepository)
    val rsvpEventUseCase = RsvpEventUseCase(eventRepository)
    val cancelEventRsvpUseCase = CancelEventRsvpUseCase(eventRepository)
```

import 추가: `EventRepository`/`EventRepositoryImpl`/유스케이스 6종.

- [ ] **Step 5: 컴파일 확인**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat :shared:jvmTest :composeApp:compileKotlinJvm"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 스테이징**

```bash
git add shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/EventRepository.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/EventRepositoryImpl.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/GetGroupEventsUseCase.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/GetEventDetailUseCase.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/CreateEventUseCase.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/DeleteEventUseCase.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/RsvpEventUseCase.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/CancelEventRsvpUseCase.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt
```

---

### Task 3: shared 그룹 설정 3메서드(수정/삭제/나가기) + UseCase 3종 + 컨테이너

**Files:**
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/GroupDtos.kt`
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/GroupRepository.kt`
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/GroupRepositoryImpl.kt`
- Create(3개): `shared/.../domain/usecase/UpdateGroupUseCase.kt` `DeleteGroupUseCase.kt` `LeaveGroupUseCase.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt`

**Interfaces:**
- Produces:
  - `UpdateGroupUseCase(groupId: Long, name: String, description: String?, image: String?, joinType: GroupJoinType?): Group`
  - `DeleteGroupUseCase(groupId: Long)` / `LeaveGroupUseCase(groupId: Long)`
  - AppContainer 공개 프로퍼티: `updateGroupUseCase` `deleteGroupUseCase` `leaveGroupUseCase`

- [ ] **Step 1: DTO 추가** — `GroupDtos.kt`의 `CreateGroupRequest` 아래에:

```kotlin
// PATCH /api/groups/{id} 요청 본문 — group/dto/GroupDtos.kt UpdateGroupRequest와 1:1.
// ⚠️name/description/image는 전체 교체 계약(null=null로 덮어씀) — 호출부가 기존 값을 실어 보낸다.
// joinType만 null=기존 유지(배포 전 클라이언트 하위호환용 서버 시맨틱).
@Serializable
data class UpdateGroupRequest(
    val name: String,
    val description: String? = null,
    val image: String? = null,
    val joinType: String? = null
)
```

- [ ] **Step 2: GroupRepository 인터페이스에 3메서드** (`joinByCode` 아래):

```kotlin
    /**
     * 그룹 정보 수정(OWNER 전용) — PATCH /api/groups/{id}.
     * ⚠️name/description/image는 전체 교체 계약(null=null로 덮어씀) — 폼이 기존 값을 항상 실어 보낸다.
     * joinType null=기존 유지(라운지는 서버가 가입 방식 자체를 안 바꾼다).
     */
    suspend fun updateGroup(
        groupId: Long,
        name: String,
        description: String?,
        image: String?,
        joinType: GroupJoinType?
    ): Result<Group>

    /** 그룹 삭제(OWNER 전용, 라운지 불가) — DELETE /api/groups/{id} */
    suspend fun deleteGroup(groupId: Long): Result<Unit>

    /** 그룹 나가기(멤버/부방장 — OWNER·라운지는 서버가 거부) — POST /api/groups/{id}/leave */
    suspend fun leaveGroup(groupId: Long): Result<Unit>
```

- [ ] **Step 3: Impl 구현** — `GroupRepositoryImpl.kt`의 `joinByCode` 아래(ktor `patch` import 추가):

```kotlin
    override suspend fun updateGroup(
        groupId: Long,
        name: String,
        description: String?,
        image: String?,
        joinType: GroupJoinType?
    ): Result<Group> = runCatching {
        client.patch("/api/groups/$groupId") {
            contentType(ContentType.Application.Json)
            setBody(
                UpdateGroupRequest(
                    name = name,
                    description = description,
                    image = image,
                    joinType = joinType?.name
                )
            )
        }.body<GroupResponse>().toDomain()
    }

    override suspend fun deleteGroup(groupId: Long): Result<Unit> =
        runCatching {
            client.delete("/api/groups/$groupId")
            Unit
        }

    override suspend fun leaveGroup(groupId: Long): Result<Unit> =
        runCatching {
            client.post("/api/groups/$groupId/leave")
            Unit
        }
```

import 추가: `io.ktor.client.request.patch`, `kr.hhp227.storygroup.shared.data.network.dto.UpdateGroupRequest`.

- [ ] **Step 4: UseCase 3종**

```kotlin
// UpdateGroupUseCase.kt
package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/**
 * 그룹 정보 수정(OWNER 전용) — ⚠️전체 교체 계약이라 폼이 로드해 온 기존 값을 항상 실어 보낸다.
 * joinType null=기존 유지(라운지 폼이 가입 방식을 안 보낼 때).
 */
class UpdateGroupUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(
        groupId: Long,
        name: String,
        description: String?,
        image: String?,
        joinType: GroupJoinType?
    ): Group = groupRepository.updateGroup(groupId, name, description, image, joinType).getOrThrow()
}

// DeleteGroupUseCase.kt
package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 그룹 삭제(OWNER 전용, 라운지 불가) — 게시글·채팅·파일이 모두 사라진다(웹 위험 구역 미러) */
class DeleteGroupUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long) {
        groupRepository.deleteGroup(groupId).getOrThrow()
    }
}

// LeaveGroupUseCase.kt
package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.GroupRepository

/** 그룹 나가기(멤버/부방장) — OWNER는 서버가 거부("그룹 삭제를 이용하세요"), 라운지도 거부 */
class LeaveGroupUseCase(private val groupRepository: GroupRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long) {
        groupRepository.leaveGroup(groupId).getOrThrow()
    }
}
```

- [ ] **Step 5: AppContainer에 3종 등록** (import 포함):

```kotlin
    val updateGroupUseCase = UpdateGroupUseCase(groupRepository)
    val deleteGroupUseCase = DeleteGroupUseCase(groupRepository)
    val leaveGroupUseCase = LeaveGroupUseCase(groupRepository)
```

- [ ] **Step 6: 컴파일 확인**

Run: Task 2 Step 5와 동일 명령
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 스테이징**

```bash
git add shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/GroupDtos.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/GroupRepository.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/GroupRepositoryImpl.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/UpdateGroupUseCase.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/DeleteGroupUseCase.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/LeaveGroupUseCase.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt
```

---

### Task 4: Compose 로컬 날짜 유틸(expect/actual)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/util/LocalDates.kt`
- Create: `composeApp/src/androidMain/kotlin/kr/hhp227/storygroup/ui/util/LocalDates.android.kt`
- Create: `composeApp/src/jvmMain/kotlin/kr/hhp227/storygroup/ui/util/LocalDates.jvm.kt`

**Interfaces:**
- Produces(Task 7이 소비): `LocalStamp(year, month, day, hour, minute)`+`dateKey`,
  `todayLocal()`, `monthLength(year, month)`, `firstDayOfWeekOfMonth(year, month)`(일=0…토=6),
  `dayOfWeek(year, month, day)`, `dateKeyOf(year, month, day)`, `isoToLocal(iso)`, `localToIso(...)`

- [ ] **Step 1: expect 선언** — `LocalDates.kt`:

```kotlin
package kr.hhp227.storygroup.ui.util

/**
 * 기기 로컬 타임존 기준 날짜·시각 — 일정 캘린더의 셀 귀속/선택 키 전용(표시도 저장도 아닌
 * UI 키, 웹 ymd() 미러). composeApp은 android+jvm 타깃뿐이라 actual은 둘 다 java.time
 * 동일 구현(kotlinx-datetime 의존성 대신 expect/actual 2벌 — 프로젝트 의존성 최소 관례).
 */
data class LocalStamp(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) {
    /** 캘린더 셀 귀속/선택 키(yyyy-MM-dd) — 표시용이 아니다 */
    val dateKey: String get() = dateKeyOf(year, month, day)
}

/** yyyy-MM-dd 키 조립 — 웹 ymd() 미러 */
fun dateKeyOf(year: Int, month: Int, day: Int): String =
    "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

/** 해당 일자의 요일(일=0…토=6) — 월 1일 요일에서 상대 계산이라 expect 불필요 */
fun dayOfWeek(year: Int, month: Int, day: Int): Int =
    (firstDayOfWeekOfMonth(year, month) + day - 1) % 7

/** 지금 이 순간의 로컬 날짜·시각 */
expect fun todayLocal(): LocalStamp

/** 해당 월의 일수(28~31) */
expect fun monthLength(year: Int, month: Int): Int

/** 해당 월 1일의 요일(일=0…토=6) — 캘린더 앞쪽 빈 칸 수(웹 leadingBlanks 미러) */
expect fun firstDayOfWeekOfMonth(year: Int, month: Int): Int

/** 서버 ISO-8601(OffsetDateTime) → 로컬 날짜·시각. 파싱 실패는 null(셀 귀속 제외) */
expect fun isoToLocal(iso: String): LocalStamp?

/** 로컬 날짜·시각 → 서버로 보낼 ISO-8601(오프셋 포함) — 일정 생성 요청용 */
expect fun localToIso(year: Int, month: Int, day: Int, hour: Int, minute: Int): String
```

- [ ] **Step 2: actual 2벌** — `LocalDates.android.kt`와 `LocalDates.jvm.kt` 내용 동일(패키지 선언 포함 그대로 복사):

```kotlin
package kr.hhp227.storygroup.ui.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId

actual fun todayLocal(): LocalStamp {
    val now = LocalDateTime.now()
    return LocalStamp(now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute)
}

actual fun monthLength(year: Int, month: Int): Int = YearMonth.of(year, month).lengthOfMonth()

// java.time DayOfWeek는 월=1…일=7 — 캘린더 규약(일=0…토=6)으로 변환
actual fun firstDayOfWeekOfMonth(year: Int, month: Int): Int =
    LocalDate.of(year, month, 1).dayOfWeek.value % 7

actual fun isoToLocal(iso: String): LocalStamp? = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
}.getOrNull()?.let { LocalStamp(it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute) }

actual fun localToIso(year: Int, month: Int, day: Int, hour: Int, minute: Int): String =
    LocalDateTime.of(year, month, day, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toOffsetDateTime()
        .toString()
```

- [ ] **Step 3: 컴파일 확인**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinJvm"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/util/LocalDates.kt \
  composeApp/src/androidMain/kotlin/kr/hhp227/storygroup/ui/util/LocalDates.android.kt \
  composeApp/src/jvmMain/kotlin/kr/hhp227/storygroup/ui/util/LocalDates.jvm.kt
```

---

### Task 5: Compose 기존 3탭 ViewModel 분리(피드/앨범/멤버) + GroupDetailViewModel 축소

레거시(탭 Fragment마다 ViewModel) 미러. 현 `GroupDetailViewModel.kt`의 코드를 책임별로
옮긴다 — 로직 변경은 두 가지뿐: ① 인박스 조회를 role 게이트 없이 항상 시도(403=빈 목록,
지금도 runCatching 흡수 구조), ② Action/Event.RefreshFeed 중계 삭제(화면이 프레젠터
refresh()를 직접 호출).

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupFeedViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupAlbumViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupMembersViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailViewModel.kt` (축소 — 전체 교체)

**Interfaces:**
- Consumes: 기존 shared UseCase들(시그니처 무변경).
- Produces(Task 6이 소비):
  - `GroupFeedViewModel(groupId, getGroupPostsPagingDataUseCase, observePostUpdatesUseCase, observeUserBlocksUseCase, observePostDeletionsUseCase, togglePostLikeUseCase)` — UiState(pagingData, likeError), Action(ToggleLike/DismissLikeError), EVENT=Nothing
  - `GroupAlbumViewModel(groupId, getGroupPhotosPagingDataUseCase)` — UiState(photosPagingData), ACTION=Nothing, EVENT=Nothing
  - `GroupMembersViewModel(groupId, getGroupMembersUseCase, getJoinRequestsUseCase, approveJoinRequestUseCase, rejectJoinRequestUseCase, createGroupInviteUseCase, openDirectRoomUseCase, getBlockedUsersUseCase, getCurrentUserIdUseCase)` — UiState(myUserId, members, blockedUserIds, joinRequests, processingRequestUserId, isLoading, error, actionError, createdInvite, isCreatingInvite, inviteError, isOpeningDm, dmError, visibleMembers), Action(Refresh/ApproveJoinRequest/RejectJoinRequest/CreateInvite/DismissInvite/OpenDm/DismissDm), Event(DmOpened(chatRoomId, title))
  - `GroupDetailViewModel(groupId, getGroupUseCase, getGroupDefaultChatRoomUseCase)` — UiState(group, defaultChatRoomId, isLoading, error, canModerate 파생), Action(Refresh), EVENT=Nothing
- 로드 트리거 규약: 피드/앨범=init 스트림 수집(자동), 상세/멤버=**화면 진입 LaunchedEffect가 Refresh 발화**(현행 미러 — 재진입 신선화 유지).

- [ ] **Step 1: GroupFeedViewModel.kt 작성** — 기존 VM의 피드 부분을 그대로 이식:

```kotlin
package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import app.cash.paging.filter
import app.cash.paging.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Post
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPostsPagingDataUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePostDeletionsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObservePostUpdatesUseCase
import kr.hhp227.storygroup.shared.domain.usecase.ObserveUserBlocksUseCase
import kr.hhp227.storygroup.shared.domain.usecase.TogglePostLikeUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 소식 탭 — 레거시 PostFragment의 VM 미러(탭별 VM 분리). 피드는 UiState에 담기는 최신
 * PagingData(Paging-CRUD 샘플 패턴). 갱신은 화면이 프레젠터 refresh()로 수행하므로
 * 이벤트가 없다. 수정/차단/삭제는 재조회 대신 현재 스냅샷에서 그 항목만 패치한다.
 * iosApp GroupFeedViewModel.swift와 1:1 미러
 */
class GroupFeedViewModel(
    val groupId: Long,
    getGroupPostsPagingDataUseCase: GetGroupPostsPagingDataUseCase,
    observePostUpdatesUseCase: ObservePostUpdatesUseCase,
    observeUserBlocksUseCase: ObserveUserBlocksUseCase,
    observePostDeletionsUseCase: ObservePostDeletionsUseCase,
    private val togglePostLikeUseCase: TogglePostLikeUseCase
) : ViewModel(), MviViewModel<GroupFeedViewModel.UiState, GroupFeedViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

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

    /** 차단한 작성자의 글을 현재 스냅샷에서 걷어낸다(멤버 목록은 멤버 탭 Refresh가 걸러낸다) */
    private fun removeBlockedAuthorPosts(userId: Long) {
        _uiState.update { state ->
            state.copy(pagingData = state.pagingData.filter { it.userId != userId })
        }
    }

    /** 삭제된 글을 현재 스냅샷에서 걷어낸다 — 다음 세대부턴 서버 응답에 애초에 없다 */
    private fun removeDeletedPost(postId: Long) {
        _uiState.update { state ->
            state.copy(pagingData = state.pagingData.filter { it.id != postId })
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.ToggleLike -> toggleLike(action.post)
            Action.DismissLikeError -> _uiState.update { it.copy(likeError = null) }
        }
    }

    /** 성공 반영은 리포지토리의 postUpdates 알림(applyPostUpdate)이 담당 — 여기선 실패만 다룬다 */
    private fun toggleLike(post: Post) {
        viewModelScope.launch {
            try {
                togglePostLikeUseCase(post.groupId, post.id, !post.likedByMe)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(likeError = e.message ?: "좋아요 처리에 실패했습니다.") }
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
        // 차단하면 그 사람의 글이 목록에서 사라져야 한다 — 재조회 대신 그 항목들만 제거
        observeUserBlocksUseCase()
            .onEach(::removeBlockedAuthorPosts)
            .launchIn(viewModelScope)
        // 상세에서 삭제하면 목록에서도 사라져야 한다 — 재조회 대신 그 항목만 제거
        observePostDeletionsUseCase()
            .onEach(::removeDeletedPost)
            .launchIn(viewModelScope)
    }

    data class UiState(
        val pagingData: PagingData<Post> = PagingData.empty(),
        // 카드 좋아요 실패 안내 — 서버 확정 방식이라 실패해도 되돌릴 UI 상태가 없다
        val likeError: String? = null
    )

    sealed interface Action {
        data class ToggleLike(val post: Post) : Action
        data object DismissLikeError : Action
    }
}
```

- [ ] **Step 2: GroupAlbumViewModel.kt 작성** — 액션·이벤트가 둘 다 없는 첫 VM(ACTION=Nothing):

```kotlin
package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.paging.PagingData
import app.cash.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kr.hhp227.storygroup.shared.domain.model.GroupPhoto
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupPhotosPagingDataUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 앨범 탭 — 레거시 AlbumFragment의 VM 미러(탭별 VM 분리). 게시글 첨부의 파생 뷰라
 * 페이징 스트림 하나가 전부다. 갱신은 화면이 프레젠터 refresh()로 수행하고 사용자
 * 액션도 없다 — ACTION/EVENT 둘 다 Nothing(호출 불가). iosApp GroupAlbumViewModel.swift와 1:1 미러
 */
class GroupAlbumViewModel(
    val groupId: Long,
    getGroupPhotosPagingDataUseCase: GetGroupPhotosPagingDataUseCase
) : ViewModel(), MviViewModel<GroupAlbumViewModel.UiState, Nothing, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Nothing) = Unit

    init {
        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getGroupPhotosPagingDataUseCase(groupId)
            .cachedIn(viewModelScope)
            .onEach { pagingData -> _uiState.update { it.copy(photosPagingData = pagingData) } }
            .launchIn(viewModelScope)
    }

    data class UiState(
        val photosPagingData: PagingData<GroupPhoto> = PagingData.empty()
    )
}
```

- [ ] **Step 3: GroupMembersViewModel.kt 작성** — 기존 VM의 멤버/인박스/초대/DM 부분 이식.
  인박스는 role 게이트 없이 항상 시도(403=빈 목록):

```kotlin
package kr.hhp227.storygroup.ui.screens.group

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
import kr.hhp227.storygroup.shared.domain.model.GroupInvite
import kr.hhp227.storygroup.shared.domain.model.GroupJoinRequest
import kr.hhp227.storygroup.shared.domain.model.GroupMember
import kr.hhp227.storygroup.shared.domain.usecase.ApproveJoinRequestUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateGroupInviteUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetBlockedUsersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupMembersUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetJoinRequestsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.OpenDirectRoomUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RejectJoinRequestUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 멤버 탭 — 레거시 MemberFragment의 VM 미러(탭별 VM 분리). 멤버 그리드+차단 필터+
 * 가입 신청 인박스(승인/거절)+초대코드+1:1 DM 열기. 인박스는 모더레이터 전용 API지만
 * role 게이트 없이 항상 시도하고 403은 빈 목록으로 흡수한다(비모더레이터에겐 인박스가
 * 안 그려질 뿐 — 초대코드 버튼 노출만 화면이 상세 VM의 canModerate로 게이트).
 * iosApp GroupMembersViewModel.swift와 1:1 미러
 */
class GroupMembersViewModel(
    val groupId: Long,
    private val getGroupMembersUseCase: GetGroupMembersUseCase,
    private val getJoinRequestsUseCase: GetJoinRequestsUseCase,
    private val approveJoinRequestUseCase: ApproveJoinRequestUseCase,
    private val rejectJoinRequestUseCase: RejectJoinRequestUseCase,
    private val createGroupInviteUseCase: CreateGroupInviteUseCase,
    private val openDirectRoomUseCase: OpenDirectRoomUseCase,
    private val getBlockedUsersUseCase: GetBlockedUsersUseCase,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel(), MviViewModel<GroupMembersViewModel.UiState, GroupMembersViewModel.Action, GroupMembersViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState(myUserId = getCurrentUserIdUseCase()))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            is Action.ApproveJoinRequest -> approveJoinRequest(action.userId)
            is Action.RejectJoinRequest -> rejectJoinRequest(action.userId)
            is Action.CreateInvite -> createInvite(action.maxUses, action.expiresInDays)
            Action.DismissInvite -> _uiState.update { it.copy(createdInvite = null, inviteError = null) }
            is Action.OpenDm -> openDm(action.userId, action.userName)
            Action.DismissDm -> _uiState.update { it.copy(dmError = null) }
        }
    }

    /** 화면 진입 LaunchedEffect가 발화 — 멤버+인박스+차단 목록 로드(재진입 신선화 겸용) */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val members = getGroupMembersUseCase(groupId)
                // 모더레이터 전용 API — 권한이 없으면 403이라 빈 목록으로 흡수(인박스만 안 그려진다)
                val joinRequests = runCatching { getJoinRequestsUseCase(groupId) }.getOrDefault(emptyList())
                // 서버는 멤버 목록에서 차단 사용자를 빼주지 않는다 — 그리드에서 직접 걸러내려고 함께 읽는다.
                // 실패해도 멤버 탭은 그린다(안 걸러진 멤버가 보일 뿐, 다음 Refresh가 따라잡는다)
                val blockedUserIds = runCatching { getBlockedUsersUseCase().map { blocked -> blocked.userId }.toSet() }
                    .getOrDefault(emptySet())

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        members = members,
                        blockedUserIds = blockedUserIds,
                        joinRequests = joinRequests
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "멤버를 불러오지 못했습니다.")
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

    data class UiState(
        // 멤버 그리드에서 본인을 구분(본인은 DM 대상이 아니다) — 세션이 있는 한 null이 아니다
        val myUserId: Long? = null,
        val members: List<GroupMember> = emptyList(),
        // 내가 차단한 사용자 — 서버가 멤버 목록에선 걸러주지 않아 화면이 직접 뺀다
        val blockedUserIds: Set<Long> = emptySet(),
        // 모더레이터에게만 채워진다 — 일반 멤버는 403이 빈 목록으로 흡수돼 인박스가 안 그려진다
        val joinRequests: List<GroupJoinRequest> = emptyList(),
        // 승인/거절 버튼 로딩 표시용 — 동시에 하나만 처리(웹 busyFor 미러)
        val processingRequestUserId: Long? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        // 승인/거절 실패 문구 — 로드 에러(error)와 달리 탭 화면을 대체하지 않는다
        val actionError: String? = null,
        // 초대코드 다이얼로그 전용 — 생성 성공 시 createdInvite가 채워져 결과 뷰로 전환된다
        val createdInvite: GroupInvite? = null,
        val isCreatingInvite: Boolean = false,
        val inviteError: String? = null,
        // DM 확인 다이얼로그 전용 — 실패 문구(차단 관계 등)는 다이얼로그 안에 표시된다
        val isOpeningDm: Boolean = false,
        val dmError: String? = null
    ) {
        /**
         * 멤버 그리드에 그릴 멤버 — 차단한 사용자는 뺀다(차단=내 화면에서 숨김).
         * 탭하면 DM인데 차단하면 DM 자체가 막히므로, 남겨두면 열 수 없는 진입점이 된다.
         */
        val visibleMembers: List<GroupMember> get() = members.filterNot { it.userId in blockedUserIds }
    }

    sealed interface Action {
        data object Refresh : Action
        data class ApproveJoinRequest(val userId: Long) : Action
        data class RejectJoinRequest(val userId: Long) : Action
        data class CreateInvite(val maxUses: Int?, val expiresInDays: Int?) : Action
        data object DismissInvite : Action
        data class OpenDm(val userId: Long, val userName: String) : Action
        data object DismissDm : Action
    }

    sealed interface Event {
        /** DM 방 확보 성공 — 화면이 채팅방(groupId=null)으로 이동한다 */
        data class DmOpened(val chatRoomId: Long, val title: String) : Event
    }
}
```

- [ ] **Step 4: GroupDetailViewModel.kt 축소** — 파일 전체를 아래로 교체:

```kotlin
package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupRole
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupDefaultChatRoomUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 상세 화면 수준 VM — 커버(이름/설명/역할)+상단바 채팅 버튼용 기본 방 id만 담당.
 * 탭 상태는 레거시(탭 Fragment마다 VM)처럼 탭별 VM 5개가 각자 소유한다:
 * GroupFeed/GroupAlbum/GroupMembers/GroupEvents/GroupSettingsViewModel.
 * groupId만 받아 스스로 로드한다 — 목록이 페이징이라 스냅샷 lookup 불가, 딥링크에도 대비.
 * iosApp GroupDetailViewModel.swift와 1:1 미러
 */
class GroupDetailViewModel(
    val groupId: Long,
    private val getGroupUseCase: GetGroupUseCase,
    private val getGroupDefaultChatRoomUseCase: GetGroupDefaultChatRoomUseCase
) : ViewModel(), MviViewModel<GroupDetailViewModel.UiState, GroupDetailViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
        }
    }

    /** 상세 진입 시 발화 — VM이 탭 전환에도 유지되므로 재진입 때도 최신화된다 */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val group = getGroupUseCase(groupId)
                // 상단바 채팅 버튼용 기본 방 id — 실패해도 상세는 그린다(버튼만 숨고 다음 Refresh가 따라잡는다)
                val defaultChatRoomId = runCatching { getGroupDefaultChatRoomUseCase(groupId) }.getOrNull()

                _uiState.update {
                    it.copy(isLoading = false, group = group, defaultChatRoomId = defaultChatRoomId)
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "그룹을 불러오지 못했습니다.")
                }
            }
        }
    }

    data class UiState(
        // 로드 전 null — 화면은 그룹 정보 자리만 비워 두고 커버/피드를 먼저 그린다
        val group: Group? = null,
        // 상단바 채팅 버튼이 여는 기본 채팅방(가장 먼저 생성된 방) — 로드 전/실패 시 null이면 버튼이 숨는다
        val defaultChatRoomId: Long? = null,
        val isLoading: Boolean = false,
        val error: String? = null
    ) {
        // 초대코드 버튼·일정 삭제(모더레이터) 노출 조건 — 웹 lib/roles canModerate 미러(라운지 제외)
        val canModerate: Boolean get() = group != null && !group.isLounge && group.myRole != GroupRole.MEMBER
    }

    sealed interface Action {
        data object Refresh : Action
    }
}
```

- [ ] **Step 5: 컴파일 시도** — GroupDetailScreen이 옛 시그니처를 참조하므로 **실패가 정상**
  (Task 6에서 재배선). 이 태스크는 4개 VM 파일이 문법적으로 완결되면 종료 —
  `GroupDetailScreen.kt`의 컴파일 에러 외 다른 에러가 없는지만 확인:

Run: Task 4 Step 3와 동일 명령
Expected: FAIL — 에러가 전부 `GroupDetailScreen.kt`(그리고 그것만)에서 나는지 확인

- [ ] **Step 6: 스테이징 보류** — Task 6과 함께 컴파일이 풀린 뒤 스테이징한다(중간 상태 방지)

---

### Task 6: Compose GroupDetailScreen 재배선(분리 VM 소비)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt`

**Interfaces:**
- Consumes: Task 5의 VM 4종 시그니처.
- Produces: `GroupDetailScreen(groupId, onBack, onCreatePost, onOpenPostDetail, onOpenChatRoom, refreshRequested, onRefreshHandled, modifier, viewModel, feedViewModel, albumViewModel, membersViewModel)` — 시그니처의 콜백 부분은 무변경(App.kt 수정 불필요). 일정(2)·설정(4) 페이지는 이 태스크에선 기존 SgEmptyState 유지(Task 7·8이 교체).

- [ ] **Step 1: VM 팩토리 재편** — 기존 `groupDetailViewModel(groupId)` private 팩토리를 아래 4개로 교체:

```kotlin
@Composable
private fun groupDetailViewModel(groupId: Long): GroupDetailViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "group-detail-$groupId") {
        GroupDetailViewModel(
            groupId = groupId,
            getGroupUseCase = container.getGroupUseCase,
            getGroupDefaultChatRoomUseCase = container.getGroupDefaultChatRoomUseCase
        )
    }
}

@Composable
private fun groupFeedViewModel(groupId: Long): GroupFeedViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "group-feed-$groupId") {
        GroupFeedViewModel(
            groupId = groupId,
            getGroupPostsPagingDataUseCase = container.getGroupPostsPagingDataUseCase,
            observePostUpdatesUseCase = container.observePostUpdatesUseCase,
            observeUserBlocksUseCase = container.observeUserBlocksUseCase,
            observePostDeletionsUseCase = container.observePostDeletionsUseCase,
            togglePostLikeUseCase = container.togglePostLikeUseCase
        )
    }
}

@Composable
private fun groupAlbumViewModel(groupId: Long): GroupAlbumViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "group-album-$groupId") {
        GroupAlbumViewModel(
            groupId = groupId,
            getGroupPhotosPagingDataUseCase = container.getGroupPhotosPagingDataUseCase
        )
    }
}

@Composable
private fun groupMembersViewModel(groupId: Long): GroupMembersViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "group-members-$groupId") {
        GroupMembersViewModel(
            groupId = groupId,
            getGroupMembersUseCase = container.getGroupMembersUseCase,
            getJoinRequestsUseCase = container.getJoinRequestsUseCase,
            approveJoinRequestUseCase = container.approveJoinRequestUseCase,
            rejectJoinRequestUseCase = container.rejectJoinRequestUseCase,
            createGroupInviteUseCase = container.createGroupInviteUseCase,
            openDirectRoomUseCase = container.openDirectRoomUseCase,
            getBlockedUsersUseCase = container.getBlockedUsersUseCase,
            getCurrentUserIdUseCase = container.getCurrentUserIdUseCase
        )
    }
}
```

- [ ] **Step 2: GroupDetailScreen 시그니처 확장** — 탭 VM들도 default param으로 선언
  (화면당 VM 선언 원칙 — 탭은 화면의 부품이라 화면이 함께 선언, 백스택 엔트리 스코프라
  HorizontalPager가 페이지를 dispose해도 생존):

```kotlin
@Composable
fun GroupDetailScreen(
    groupId: Long,
    onBack: () -> Unit,
    onCreatePost: () -> Unit,
    // 이 그룹의 글이라 groupId는 화면이 이미 알고 있다 — postId만 넘긴다
    onOpenPostDetail: (postId: Long) -> Unit,
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    refreshRequested: Boolean,
    onRefreshHandled: () -> Unit,
    modifier: Modifier = Modifier,
    // 라우트(백스택 엔트리) 스코프 — pop되면 함께 정리된다(ConCafe CafeScreen 패턴).
    // 탭 상태는 레거시(탭 Fragment마다 VM)처럼 탭별 VM이 각자 소유한다
    viewModel: GroupDetailViewModel = groupDetailViewModel(groupId),
    feedViewModel: GroupFeedViewModel = groupFeedViewModel(groupId),
    albumViewModel: GroupAlbumViewModel = groupAlbumViewModel(groupId),
    membersViewModel: GroupMembersViewModel = groupMembersViewModel(groupId)
) {
    GroupDetailContent(
        viewModel = viewModel,
        feedViewModel = feedViewModel,
        albumViewModel = albumViewModel,
        membersViewModel = membersViewModel,
        onBack = onBack,
        onCreatePost = onCreatePost,
        onOpenPostDetail = onOpenPostDetail,
        onOpenChatRoom = onOpenChatRoom,
        refreshRequested = refreshRequested,
        onRefreshHandled = onRefreshHandled,
        modifier = modifier
    )
}
```

- [ ] **Step 3: GroupDetailContent 재배선** — 수집·조율부를 아래로 교체(다이얼로그·탭 구조는 유지):

```kotlin
@Composable
private fun GroupDetailContent(
    viewModel: GroupDetailViewModel,
    feedViewModel: GroupFeedViewModel,
    albumViewModel: GroupAlbumViewModel,
    membersViewModel: GroupMembersViewModel,
    onBack: () -> Unit,
    onCreatePost: () -> Unit,
    // 이 그룹의 글이라 groupId는 화면이 이미 알고 있다 — postId만 넘긴다
    onOpenPostDetail: (postId: Long) -> Unit,
    onOpenChatRoom: (chatRoomId: Long, groupId: Long?, title: String) -> Unit,
    refreshRequested: Boolean,
    onRefreshHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val feedUiState by feedViewModel.uiState.collectAsState()
    val membersUiState by membersViewModel.uiState.collectAsState()
    // 상태에서 pagingData만 뽑아낸 스트림을 수집 — Paging-CRUD 샘플·iOS($state.map)와 동일 관용구.
    // 프레젠터는 페이저 밖(Content 수준)에서 수집한다 — 페이지 dispose에 스크롤·스냅샷을 잃지 않게
    val pagingDataFlow = remember(feedViewModel) {
        feedViewModel.uiState.map { it.pagingData }.distinctUntilChanged()
    }
    val lazyPagingItems = pagingDataFlow.collectAsLazyPagingItems()
    // 앨범 탭 — 피드와 동일 관용구, 상태에서 photosPagingData만 뽑아낸 스트림을 수집
    val photosPagingDataFlow = remember(albumViewModel) {
        albumViewModel.uiState.map { it.photosPagingData }.distinctUntilChanged()
    }
    val photoLazyPagingItems = photosPagingDataFlow.collectAsLazyPagingItems()
    val sg = SgTheme.colors
    // 레거시 R.array.tab_name(소식/앨범/맴버/설정)에 일정 추가
    val tabs = remember { listOf("소식", "앨범", "일정", "멤버", "설정") }
    val pagerState = rememberPagerState { tabs.size }
    var showInviteDialog by rememberSaveable { mutableStateOf(false) }
    // DM 확인 다이얼로그 대상 — 멤버 탭에서 타인을 탭하면 채워진다
    var dmTargetMember by remember { mutableStateOf<GroupMember?>(null) }
    // 컴포지션에서 한 번만 선언해 카드마다 재사용한다
    val share = rememberShareLauncher()

    // 상세 진입 시 신선화 — VM들이 탭 전환에도 유지되므로 재진입 때도 최신화된다
    LaunchedEffect(viewModel) {
        viewModel.onAction(GroupDetailViewModel.Action.Refresh)
        membersViewModel.onAction(GroupMembersViewModel.Action.Refresh)
    }
    // 작성 화면에서 돌아온 결과 — 피드·앨범을 첫 페이지부터 다시 읽는다(프레젠터 직접 refresh)
    LaunchedEffect(refreshRequested) {
        if (refreshRequested) {
            lazyPagingItems.refresh()
            photoLazyPagingItems.refresh()
            onRefreshHandled()
        }
    }
    // 멤버 탭의 일회성 이벤트 — DM 방 확보 성공 시 채팅방으로 이동
    LaunchedEffect(membersViewModel) {
        membersViewModel.event.collect { event ->
            when (event) {
                is GroupMembersViewModel.Event.DmOpened -> {
                    dmTargetMember = null
                    // DM 방은 groupId 없이 접근한다(/api/dm 경로) — 제목은 상대 이름
                    onOpenChatRoom(event.chatRoomId, null, event.title)
                }
            }
        }
    }
    SgCollapsingTabScaffold(
        // 로드 전엔 빈 제목 — 커버 그라데이션(groupId 기반)은 즉시 그려진다
        title = uiState.group?.name.orEmpty(),
        tabs = tabs,
        pagerState = pagerState,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        // 그룹 채팅방 진입 — 상단바 액션(레거시 group.xml action_chat·웹 커버 "채팅" 버튼 미러).
        // 기본 방 id는 상세 로드에 실려 온다 — 로드 전/실패 시엔 버튼이 숨는다
        actions = {
            uiState.defaultChatRoomId?.let { chatRoomId ->
                IconButton(onClick = {
                    // 방 제목은 허브(그룹 방 목록)와 동일하게 그룹명을 쓴다
                    onOpenChatRoom(chatRoomId, viewModel.groupId, uiState.group?.name.orEmpty())
                }) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "채팅")
                }
            }
        },
        // 레거시 isTabPositionZero 미러 — 글쓰기 FAB는 소식 탭에서만
        floatingActionButton = if (pagerState.currentPage == 0) {
            {
                // 레거시 fragment_group_detail.xml의 fab 미러
                FloatingActionButton(
                    onClick = onCreatePost,
                    backgroundColor = sg.accent,
                    contentColor = sg.onAccent
                ) {
                    Icon(Icons.Default.Add, contentDescription = "글쓰기")
                }
            }
        } else null,
        // 스피너는 데이터가 이미 있는 갱신에만(첫 로드는 각 탭의 중앙 스피너 담당 — 기존 규칙 유지)
        isRefreshing = uiState.isLoading && uiState.group != null,
        // 현재 탭 무관하게 상세+멤버+피드+앨범 함께 갱신 — iOS .refreshable과 대칭(단순 우선)
        onRefresh = {
            viewModel.onAction(GroupDetailViewModel.Action.Refresh)
            membersViewModel.onAction(GroupMembersViewModel.Action.Refresh)
            lazyPagingItems.refresh()
            photoLazyPagingItems.refresh()
        },
        header = { _ ->
            // ...기존 헤더 블록 그대로(수정 없음)...
        },
        modifier = modifier
    ) { page ->
        when (page) {
            0 -> GroupFeedTab(
                detailError = uiState.error,
                lazyPagingItems = lazyPagingItems,
                onRetryDetail = { viewModel.onAction(GroupDetailViewModel.Action.Refresh) },
                onToggleLike = { feedViewModel.onAction(GroupFeedViewModel.Action.ToggleLike(it)) },
                onShare = { share(postShareText(it)) },
                onOpenPostDetail = onOpenPostDetail
            )
            1 -> GroupAlbumTab(
                lazyPagingItems = photoLazyPagingItems,
                onOpenPostDetail = onOpenPostDetail
            )
            2 -> SgEmptyState(
                title = "일정",
                subtitle = "준비 중입니다.",
                modifier = Modifier.fillMaxSize()
            )
            3 -> GroupMembersTab(
                uiState = membersUiState,
                canModerate = uiState.canModerate,
                onRetry = { membersViewModel.onAction(GroupMembersViewModel.Action.Refresh) },
                onApprove = { membersViewModel.onAction(GroupMembersViewModel.Action.ApproveJoinRequest(it)) },
                onReject = { membersViewModel.onAction(GroupMembersViewModel.Action.RejectJoinRequest(it)) },
                onShowInvite = { showInviteDialog = true },
                onMemberClick = { dmTargetMember = it }
            )
            else -> SgEmptyState(
                title = "설정",
                subtitle = "준비 중입니다.",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    if (showInviteDialog) {
        InviteDialog(
            invite = membersUiState.createdInvite,
            isLoading = membersUiState.isCreatingInvite,
            error = membersUiState.inviteError,
            onDismiss = {
                showInviteDialog = false
                // 닫을 때 결과를 비워 다음에 열면 다시 생성 폼부터 시작한다
                membersViewModel.onAction(GroupMembersViewModel.Action.DismissInvite)
            },
            onCreate = { maxUses, expiresInDays ->
                membersViewModel.onAction(GroupMembersViewModel.Action.CreateInvite(maxUses, expiresInDays))
            }
        )
    }
    dmTargetMember?.let { member ->
        DmConfirmDialog(
            memberName = member.name,
            isLoading = membersUiState.isOpeningDm,
            error = membersUiState.dmError,
            onDismiss = {
                dmTargetMember = null
                membersViewModel.onAction(GroupMembersViewModel.Action.DismissDm)
            },
            onConfirm = {
                membersViewModel.onAction(GroupMembersViewModel.Action.OpenDm(member.userId, member.name))
            }
        )
    }
    feedUiState.likeError?.let { message ->
        AlertDialog(
            onDismissRequest = { feedViewModel.onAction(GroupFeedViewModel.Action.DismissLikeError) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { feedViewModel.onAction(GroupFeedViewModel.Action.DismissLikeError) }) {
                    Text("확인", color = SgTheme.colors.accent)
                }
            }
        )
    }
}
```

주의: `header = { _ -> ... }` 블록은 **기존 코드를 그대로 유지**한다(커버 이미지+스크림+
그룹명/설명/RoleChip — Task "탭바 투명 배경"에서 정리된 상태 무변경).

- [ ] **Step 4: GroupFeedTab 시그니처 변경** — `uiState: GroupDetailViewModel.UiState` 파라미터를
  `detailError: String?`로 교체(내부에서 `uiState.error` → `detailError`로 치환, 나머지 동일):

```kotlin
@Composable
private fun GroupFeedTab(
    // 상세(커버) 로드 실패 문구 — 피드 목록 위에 재시도와 함께 그린다(기존 배치 유지)
    detailError: String?,
    lazyPagingItems: LazyPagingItems<Post>,
    onRetryDetail: () -> Unit,
    onToggleLike: (Post) -> Unit,
    onShare: (Post) -> Unit,
    onOpenPostDetail: (postId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // 본문 변경: if (uiState.error != null) → if (detailError != null),
    // Text(uiState.error.orEmpty(), ...) → Text(detailError, ...) — 이외 전부 기존 유지
```

- [ ] **Step 5: GroupMembersTab 시그니처 변경** — `uiState: GroupMembersViewModel.UiState`+
  `canModerate: Boolean`+`onRetry: () -> Unit` 소비. 본문 변경점:
  - `uiState.canModerate` → 파라미터 `canModerate`
  - 그리드 최상단에 로딩/에러 상태 추가(멤버 로드가 이제 이 VM 소유):

```kotlin
@Composable
private fun GroupMembersTab(
    uiState: GroupMembersViewModel.UiState,
    canModerate: Boolean,
    onRetry: () -> Unit,
    onApprove: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onShowInvite: () -> Unit,
    onMemberClick: (GroupMember) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 멤버 로드 실패 — 탭 자체가 자기 로드를 소유하므로 여기서 재시도를 준다
        if (uiState.error != null) {
            item(key = "members-error", span = { GridItemSpan(maxLineSpan) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(uiState.error, style = SgTheme.typography.bodyMedium, color = sg.rust)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRetry) {
                        Text("다시 시도", color = sg.accent)
                    }
                }
            }
        }
        if (uiState.isLoading && uiState.members.isEmpty()) {
            item(key = "members-loading", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = sg.accent)
                }
            }
        }
        // 이하 기존 본문 유지: joinRequests 인박스 → canModerate 초대코드 버튼 → "멤버 N" → 그리드
        // (uiState.canModerate 참조만 파라미터 canModerate로 치환)
```

- [ ] **Step 6: import 정리 후 컴파일+데스크톱 스모크**

Run: Task 4 Step 3와 동일 명령
Expected: BUILD SUCCESSFUL
이후(선택, 시간 되면): `gradlew.bat :composeApp:run`으로 그룹 상세 진입 → 소식/앨범/멤버 탭이
기존과 동일 동작(피드 로드·멤버 그리드·탭 전환 시 스크롤 유지) 확인 후 종료.

- [ ] **Step 7: 스테이징** (Task 5 몫 포함)

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupFeedViewModel.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupAlbumViewModel.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupMembersViewModel.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailViewModel.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt
```

---

### Task 7: Compose 일정 탭(GroupEventsViewModel + GroupEventsTab)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupEventsViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupEventsTab.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt` (팩토리+파라미터+2페이지 연결+풀리프레시)

**Interfaces:**
- Consumes: Task 2 UseCase 6종+`GetCurrentUserIdUseCase`, Task 4 날짜 유틸, Task 6 화면 구조.
- Produces: `GroupEventsTab(uiState: GroupEventsViewModel.UiState, canModerate: Boolean, onAction: (GroupEventsViewModel.Action) -> Unit, modifier)` — Task 8·iOS 미러가 구조 참조.

- [ ] **Step 1: GroupEventsViewModel.kt 작성**:

```kotlin
package kr.hhp227.storygroup.ui.screens.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.EventAttendee
import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.model.RsvpStatus
import kr.hhp227.storygroup.shared.domain.usecase.CancelEventRsvpUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreateEventUseCase
import kr.hhp227.storygroup.shared.domain.usecase.DeleteEventUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetCurrentUserIdUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetEventDetailUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupEventsUseCase
import kr.hhp227.storygroup.shared.domain.usecase.RsvpEventUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel
import kr.hhp227.storygroup.ui.util.dateKeyOf
import kr.hhp227.storygroup.ui.util.isoToLocal
import kr.hhp227.storygroup.ui.util.localToIso
import kr.hhp227.storygroup.ui.util.todayLocal

/**
 * 일정 탭 — 웹 /groups/[id]/events 캘린더 페이지 미러(탭별 VM 분리, 레거시 탭 Fragment VM 구조).
 * 월 앵커 기준 [월초, 다음달 초) 범위를 조회하고, 날짜 귀속·선택 키는 기기 로컬 타임존
 * yyyy-MM-dd(웹 ymd 미러). RSVP는 낙관적 갱신 없이 서버가 돌려준 집계 갱신 일정으로 카드를
 * 교체하고, 같은 상태 재탭은 취소다. 생성 폼의 날짜는 캘린더 선택일을 그대로 쓴다(M2에
 * DatePicker가 없음) — 그래서 새 일정은 항상 보고 있는 달에 속하고, 웹의 "다른 달이면 이동"
 * 분기가 필요 없다. iosApp GroupEventsViewModel.swift와 1:1 미러
 */
class GroupEventsViewModel(
    val groupId: Long,
    private val getGroupEventsUseCase: GetGroupEventsUseCase,
    private val getEventDetailUseCase: GetEventDetailUseCase,
    private val createEventUseCase: CreateEventUseCase,
    private val deleteEventUseCase: DeleteEventUseCase,
    private val rsvpEventUseCase: RsvpEventUseCase,
    private val cancelEventRsvpUseCase: CancelEventRsvpUseCase,
    getCurrentUserIdUseCase: GetCurrentUserIdUseCase
) : ViewModel(), MviViewModel<GroupEventsViewModel.UiState, GroupEventsViewModel.Action, Nothing> {
    private val _uiState: MutableStateFlow<UiState>
    override val uiState: StateFlow<UiState>

    override val event: Flow<Nothing> = emptyFlow()

    init {
        val today = todayLocal()

        _uiState = MutableStateFlow(
            UiState(
                myUserId = getCurrentUserIdUseCase(),
                year = today.year,
                month = today.month,
                selectedDay = today.dateKey
            )
        )
        uiState = _uiState.asStateFlow()
        loadMonth()
    }

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> loadMonth()
            is Action.MoveMonth -> moveMonth(action.delta)
            Action.GoToday -> goToday()
            is Action.SelectDay -> _uiState.update { it.copy(selectedDay = action.dateKey) }
            Action.ToggleCreateForm -> _uiState.update {
                it.copy(showCreateForm = !it.showCreateForm, createError = null)
            }
            is Action.CreateEvent -> createEvent(action)
            is Action.DeleteEvent -> deleteEvent(action.eventId)
            is Action.Rsvp -> rsvp(action.event, action.status)
            is Action.ToggleAttendees -> toggleAttendees(action.eventId)
            Action.DismissActionError -> _uiState.update { it.copy(actionError = null) }
        }
    }

    /** 현재 앵커 월 재조회 — [월초, 다음달 초) ISO(웹 loadMonth 미러) */
    private fun loadMonth() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val state = _uiState.value
            val (nextYear, nextMonth) =
                if (state.month == 12) state.year + 1 to 1 else state.year to state.month + 1

            runCatching {
                getGroupEventsUseCase(
                    groupId,
                    localToIso(state.year, state.month, 1, 0, 0),
                    localToIso(nextYear, nextMonth, 1, 0, 0)
                )
            }.onSuccess { events ->
                _uiState.update { it.copy(isLoading = false, events = events) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "일정을 불러오지 못했습니다.") }
            }
        }
    }

    /** 월 이동 — 이동한 달의 1일을 선택해 아래 목록이 이전 달 잔상을 가리키지 않게 한다(웹 미러) */
    private fun moveMonth(delta: Int) {
        val state = _uiState.value
        // -1/+1만 쓰지만 산술은 일반화해 둔다(0~11 순환)
        val zeroBased = state.year * 12 + (state.month - 1) + delta
        val year = zeroBased / 12
        val month = zeroBased % 12 + 1

        _uiState.update {
            it.copy(year = year, month = month, selectedDay = dateKeyOf(year, month, 1), events = null, isLoading = false)
        }
        loadMonth()
    }

    /** 오늘로 — 다른 달이면 재조회, 같은 달이면 선택만 이동(웹 goToday 미러) */
    private fun goToday() {
        val today = todayLocal()
        val state = _uiState.value

        if (today.year != state.year || today.month != state.month) {
            _uiState.update {
                it.copy(year = today.year, month = today.month, selectedDay = today.dateKey, events = null, isLoading = false)
            }
            loadMonth()
        } else {
            _uiState.update { it.copy(selectedDay = today.dateKey) }
        }
    }

    /** 생성 — 날짜는 캘린더 선택일 고정이라 결과는 항상 현재 달: 목록에 넣고 정렬만 하면 된다 */
    private fun createEvent(action: Action.CreateEvent) {
        if (_uiState.value.isCreating) return

        val state = _uiState.value
        val (year, month, day) = state.selectedDay.split("-").map { it.toInt() }
        val endsAtIso = if (action.endHour != null && action.endMinute != null) {
            // 종료가 시작보다 빠르면 폼 검증에서 걸렀어야 한다 — 방어적으로 한 번 더
            if (action.endHour < action.startHour ||
                (action.endHour == action.startHour && action.endMinute < action.startMinute)
            ) {
                _uiState.update { it.copy(createError = "종료 시각은 시작 시각보다 빠를 수 없습니다") }
                return
            }
            localToIso(year, month, day, action.endHour, action.endMinute)
        } else null

        _uiState.update { it.copy(isCreating = true, createError = null) }
        viewModelScope.launch {
            runCatching {
                createEventUseCase(
                    groupId = groupId,
                    title = action.title,
                    description = action.description.ifBlank { null },
                    location = action.location.ifBlank { null },
                    startsAtIso = localToIso(year, month, day, action.startHour, action.startMinute),
                    endsAtIso = endsAtIso
                )
            }.onSuccess { created ->
                _uiState.update {
                    it.copy(
                        isCreating = false,
                        showCreateForm = false,
                        events = ((it.events ?: emptyList()) + created).sortedBy(GroupEvent::startsAt)
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isCreating = false, createError = e.message ?: "일정을 만들지 못했습니다.") }
            }
        }
    }

    /** 삭제(작성자/모더레이터 — 서버 검증) — 성공 시 목록에서 제거(웹 handleDelete 미러) */
    private fun deleteEvent(eventId: Long) {
        if (_uiState.value.busyEventId != null) return

        _uiState.update { it.copy(busyEventId = eventId, actionError = null) }
        viewModelScope.launch {
            runCatching { deleteEventUseCase(groupId, eventId) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(busyEventId = null, events = state.events?.filterNot { it.id == eventId })
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(busyEventId = null, actionError = e.message ?: "일정 삭제에 실패했습니다.") }
                }
        }
    }

    /** RSVP — 같은 상태 재탭=취소. 서버가 돌려준 집계 갱신 일정으로 교체(웹 handleRsvp 미러) */
    private fun rsvp(event: GroupEvent, status: RsvpStatus) {
        if (_uiState.value.busyEventId != null) return

        _uiState.update { it.copy(busyEventId = event.id, actionError = null) }
        viewModelScope.launch {
            runCatching {
                if (event.myRsvp == status) cancelEventRsvpUseCase(groupId, event.id)
                else rsvpEventUseCase(groupId, event.id, status)
            }.onSuccess { updated ->
                _uiState.update { state ->
                    state.copy(
                        busyEventId = null,
                        events = state.events?.map { if (it.id == updated.id) updated else it },
                        // 명단이 바뀌었으니 펼쳐볼 때 다시 불러온다(웹 setAttendees(null) 미러)
                        attendeesByEvent = state.attendeesByEvent - event.id
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(busyEventId = null, actionError = e.message ?: "참석 응답에 실패했습니다.") }
            }
        }
    }

    /** 참석자 펼침 — 캐시 없으면 단건 조회, 실패는 조용히 빈 목록(부가 정보 — 웹 미러) */
    private fun toggleAttendees(eventId: Long) {
        val state = _uiState.value

        if (eventId in state.expandedEventIds) {
            _uiState.update { it.copy(expandedEventIds = it.expandedEventIds - eventId) }
            return
        }
        _uiState.update { it.copy(expandedEventIds = it.expandedEventIds + eventId) }
        if (eventId !in state.attendeesByEvent) {
            viewModelScope.launch {
                val attendees = runCatching { getEventDetailUseCase(groupId, eventId).attendees }
                    .getOrDefault(emptyList())

                _uiState.update { it.copy(attendeesByEvent = it.attendeesByEvent + (eventId to attendees)) }
            }
        }
    }

    data class UiState(
        // 일정 카드의 "작성자 본인" 삭제 판정 — 세션이 있는 한 null이 아니다
        val myUserId: Long? = null,
        // 월 앵커(연/월) — 범위 조회와 캘린더 그리드의 기준
        val year: Int = 0,
        val month: Int = 0,
        // 선택일 키(yyyy-MM-dd, 로컬) — 셀 강조와 아래 목록·생성 폼 날짜의 기준
        val selectedDay: String = "",
        // null=이 달을 아직 못 읽음(로딩/실패) — 빈 목록과 구분한다(웹 events === null 미러)
        val events: List<GroupEvent>? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val showCreateForm: Boolean = false,
        val isCreating: Boolean = false,
        val createError: String? = null,
        // 카드 액션(RSVP/삭제) 진행 중 일정 — 동시에 하나만(멤버 탭 processingRequestUserId 선례)
        val busyEventId: Long? = null,
        // RSVP/삭제 실패 문구 — 로드 에러(error)와 달리 캘린더를 대체하지 않는다
        val actionError: String? = null,
        val expandedEventIds: Set<Long> = emptySet(),
        // 일정별 참석자 캐시 — RSVP가 바뀌면 그 일정 키를 비워 다음 펼침에 재조회한다
        val attendeesByEvent: Map<Long, List<EventAttendee>> = emptyMap()
    ) {
        /** 로컬 날짜 키별 일정 — 캘린더 점·선택일 목록의 원천(웹 eventsByDay 미러) */
        val eventsByDay: Map<String, List<GroupEvent>>
            get() = (events ?: emptyList()).groupBy { event ->
                isoToLocal(event.startsAt)?.dateKey ?: event.startsAt.take(10)
            }

        val selectedEvents: List<GroupEvent> get() = eventsByDay[selectedDay] ?: emptyList()
    }

    sealed interface Action {
        data object Refresh : Action
        data class MoveMonth(val delta: Int) : Action
        data object GoToday : Action
        data class SelectDay(val dateKey: String) : Action
        data object ToggleCreateForm : Action
        data class CreateEvent(
            val title: String,
            val location: String,
            val description: String,
            val startHour: Int,
            val startMinute: Int,
            val endHour: Int?,
            val endMinute: Int?
        ) : Action
        data class DeleteEvent(val eventId: Long) : Action
        data class Rsvp(val event: GroupEvent, val status: RsvpStatus) : Action
        data class ToggleAttendees(val eventId: Long) : Action
        data object DismissActionError : Action
    }
}
```

- [ ] **Step 2: GroupEventsTab.kt 작성** — 웹 캘린더 페이지 미러 UI 전체:

```kotlin
package kr.hhp227.storygroup.ui.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.shared.domain.model.GroupEvent
import kr.hhp227.storygroup.shared.domain.model.RsvpStatus
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.dateKeyOf
import kr.hhp227.storygroup.ui.util.dayOfWeek
import kr.hhp227.storygroup.ui.util.firstDayOfWeekOfMonth
import kr.hhp227.storygroup.ui.util.isoToLocal
import kr.hhp227.storygroup.ui.util.monthLength
import kr.hhp227.storygroup.ui.util.todayLocal

// 웹 DAY_LABELS 미러 — 일=rust, 토=accent 강조는 셀에서 인덱스로 판정
private val DayLabels = listOf("일", "월", "화", "수", "목", "금", "토")

private val RsvpOptions = listOf(
    RsvpStatus.GOING to "참석",
    RsvpStatus.MAYBE to "미정",
    RsvpStatus.NOT_GOING to "불참"
)

/** "HH:mm" — 이벤트 카드 시각 표기(웹 formatTime 미러, 로컬 타임존) */
private fun formatTime(iso: String): String = isoToLocal(iso)?.let {
    "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}"
}.orEmpty()

/**
 * 일정 탭 — 웹 /groups/[id]/events 미러: 월 캘린더 카드+인라인 생성 폼+선택일 일정 카드 목록.
 * 생성 폼의 날짜는 캘린더 선택일을 그대로 쓴다(M2에 DatePicker가 없음 — 시각만 HH:MM 입력).
 * iosApp GroupEventsTab.swift와 1:1 미러
 */
@Composable
internal fun GroupEventsTab(
    uiState: GroupEventsViewModel.UiState,
    // 일정 삭제 버튼 노출(작성자 본인 외) — 화면이 상세 VM의 canModerate로 게이트
    canModerate: Boolean,
    onAction: (GroupEventsViewModel.Action) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "calendar") {
            CalendarCard(uiState = uiState, onAction = onAction, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (uiState.showCreateForm) {
            item(key = "create-form") {
                CreateEventForm(
                    selectedDay = uiState.selectedDay,
                    isCreating = uiState.isCreating,
                    createError = uiState.createError,
                    onCreate = { title, location, description, startHour, startMinute, endHour, endMinute ->
                        onAction(
                            GroupEventsViewModel.Action.CreateEvent(
                                title, location, description, startHour, startMinute, endHour, endMinute
                            )
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        item(key = "selected-day-header") {
            // "M월 d일 (요일)" — 웹 선택일 헤더 미러
            val parts = uiState.selectedDay.split("-").mapNotNull { it.toIntOrNull() }

            if (parts.size == 3) {
                val (year, month, day) = parts

                Text(
                    "${month}월 ${day}일 (${DayLabels[dayOfWeek(year, month, day)]})",
                    style = SgTheme.typography.titleSmall,
                    color = sg.ink,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        uiState.actionError?.let { message ->
            item(key = "action-error") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillParentMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text(message, style = SgTheme.typography.bodySmall, color = sg.rust, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onAction(GroupEventsViewModel.Action.DismissActionError) }) {
                        Text("닫기", color = sg.accent)
                    }
                }
            }
        }
        when {
            uiState.events == null && uiState.error != null -> item(key = "events-error") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillParentMaxWidth().padding(vertical = 24.dp)
                ) {
                    Text(uiState.error, style = SgTheme.typography.bodyMedium, color = sg.rust)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onAction(GroupEventsViewModel.Action.Refresh) }) {
                        Text("다시 시도", color = sg.accent)
                    }
                }
            }
            uiState.events == null -> item(key = "events-loading") {
                Box(Modifier.fillParentMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = sg.accent)
                }
            }
            uiState.selectedEvents.isEmpty() -> item(key = "events-empty") {
                SgEmptyState(
                    title = "이 날짜에는 일정이 없습니다",
                    subtitle = "일정 만들기로 첫 일정을 등록해 보세요.",
                    modifier = Modifier.fillParentMaxWidth().padding(vertical = 24.dp)
                )
            }
            else -> items(uiState.selectedEvents.size, key = { uiState.selectedEvents[it].id }) { index ->
                val event = uiState.selectedEvents[index]

                EventCard(
                    event = event,
                    isMine = event.userId == uiState.myUserId,
                    canModerate = canModerate,
                    isBusy = uiState.busyEventId != null,
                    isExpanded = event.id in uiState.expandedEventIds,
                    attendees = uiState.attendeesByEvent[event.id],
                    onAction = onAction,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

/** 월 이동 헤더+7열 그리드 — 웹 캘린더 카드 미러(오늘=테두리, 선택=accent 배경, 점 최대 3개) */
@Composable
private fun CalendarCard(
    uiState: GroupEventsViewModel.UiState,
    onAction: (GroupEventsViewModel.Action) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    val todayKey = todayLocal().dateKey
    val leadingBlanks = firstDayOfWeekOfMonth(uiState.year, uiState.month)
    val daysInMonth = monthLength(uiState.year, uiState.month)

    SgCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onAction(GroupEventsViewModel.Action.MoveMonth(-1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "이전 달", tint = sg.inkSoft)
                }
                Text(
                    "${uiState.year}년 ${uiState.month}월",
                    style = SgTheme.typography.titleSmall,
                    color = sg.ink,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onAction(GroupEventsViewModel.Action.MoveMonth(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달", tint = sg.inkSoft)
                }
                TextButton(onClick = { onAction(GroupEventsViewModel.Action.GoToday) }) {
                    Text("오늘", color = sg.accent)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onAction(GroupEventsViewModel.Action.ToggleCreateForm) }) {
                    Text(if (uiState.showCreateForm) "닫기" else "일정 만들기", color = sg.accent, fontWeight = FontWeight.Bold)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                DayLabels.forEachIndexed { index, label ->
                    Text(
                        label,
                        style = SgTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (index) {
                            0 -> sg.rust
                            6 -> sg.accent
                            else -> sg.inkFaint
                        },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                    )
                }
            }
            // 앞쪽 빈 칸(월 시작 요일)+일자들을 7개씩 끊어 행으로 — LazyColumn 안이라 그리드 대신 수동 행
            val cells: List<Int?> = List(leadingBlanks) { null } + (1..daysInMonth).toList()

            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(Modifier.weight(1f).height(52.dp))
                        } else {
                            val key = dateKeyOf(uiState.year, uiState.month, day)
                            val isSelected = key == uiState.selectedDay
                            val isToday = key == todayKey
                            val dayEvents = uiState.eventsByDay[key] ?: emptyList()

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) sg.accent else androidx.compose.ui.graphics.Color.Transparent)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isToday) sg.accent else androidx.compose.ui.graphics.Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onAction(GroupEventsViewModel.Action.SelectDay(key)) }
                                    .padding(top = 6.dp)
                            ) {
                                Text(
                                    day.toString(),
                                    style = SgTheme.typography.bodySmall,
                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) sg.onAccent else sg.ink
                                )
                                if (dayEvents.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        dayEvents.take(3).forEach { _ ->
                                            Box(
                                                Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) sg.onAccent else sg.accent)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // 마지막 주가 7칸 미만이면 남은 칸을 빈 칸으로 채워 폭을 맞춘다
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(52.dp)) }
                }
            }
            if (uiState.isLoading && uiState.events != null) {
                // 월 재조회 중(데이터 있는 갱신) — 캘린더는 그대로 두고 아래에 가는 줄만
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = sg.accent, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** 인라인 생성 폼 — 웹 CreateEventForm 미러. 날짜=캘린더 선택일 고정, 시각만 HH:MM 텍스트 */
@Composable
private fun CreateEventForm(
    selectedDay: String,
    isCreating: Boolean,
    createError: String?,
    onCreate: (
        title: String, location: String, description: String,
        startHour: Int, startMinute: Int, endHour: Int?, endMinute: Int?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    var title by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    // 웹 기본값 19:00 미러 — 종료는 빈칸이면 "종료 시각 없는 일정"
    var startTime by rememberSaveable { mutableStateOf("19:00") }
    var endTime by rememberSaveable { mutableStateOf("") }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    // "HH:MM"/"H:MM" 허용 — 그 외는 null(형식 오류)
    fun parseTime(text: String): Pair<Int, Int>? {
        val parts = text.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].trim().toIntOrNull() ?: return null
        val minute = parts[1].trim().toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }

    SgCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("새 일정 — $selectedDay", style = SgTheme.typography.titleSmall, color = sg.ink, fontWeight = FontWeight.Bold)
            SgTextField(value = title, onValueChange = { title = it.take(100) }, label = "일정 제목")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SgTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = "시작(HH:MM)",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
                Text("~", style = SgTheme.typography.bodyMedium, color = sg.inkFaint)
                SgTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = "종료(선택)",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }
            SgTextField(value = location, onValueChange = { location = it.take(200) }, label = "장소 (선택)")
            SgTextField(
                value = description,
                onValueChange = { description = it.take(2000) },
                label = "설명 (선택)",
                singleLine = false,
                minLines = 3
            )
            (formError ?: createError)?.let {
                Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
            }
            SgPrimaryButton(
                text = "등록",
                onClick = {
                    val start = parseTime(startTime)
                    val end = if (endTime.isBlank()) null else parseTime(endTime)

                    formError = when {
                        start == null -> "시작 시각은 HH:MM 형식으로 입력해 주세요"
                        endTime.isNotBlank() && end == null -> "종료 시각은 HH:MM 형식으로 입력해 주세요"
                        end != null && (end.first < start.first ||
                            (end.first == start.first && end.second < start.second)) ->
                            "종료 시각은 시작 시각보다 빠를 수 없습니다"
                        else -> null
                    }
                    if (formError == null && start != null) {
                        onCreate(title, location, description, start.first, start.second, end?.first, end?.second)
                    }
                },
                enabled = title.isNotBlank(),
                isLoading = isCreating,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 일정 카드 — 웹 EventCard 미러: 제목·시각·삭제, 장소, 설명, RSVP 3버튼, 집계+참석자 펼침, 작성자 캡션 */
@Composable
private fun EventCard(
    event: GroupEvent,
    isMine: Boolean,
    canModerate: Boolean,
    isBusy: Boolean,
    isExpanded: Boolean,
    // null=아직 못 읽음(펼치면 lazy 조회 중), 빈 목록=응답한 멤버 없음
    attendees: List<kr.hhp227.storygroup.shared.domain.model.EventAttendee>?,
    onAction: (GroupEventsViewModel.Action) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    SgCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    event.title,
                    style = SgTheme.typography.titleSmall,
                    color = sg.ink,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(event.startsAt) + (event.endsAt?.let { " ~ ${formatTime(it)}" } ?: ""),
                    style = SgTheme.typography.labelSmall,
                    color = sg.inkFaint
                )
                Spacer(Modifier.weight(1f))
                if (isMine || canModerate) {
                    TextButton(
                        onClick = { onAction(GroupEventsViewModel.Action.DeleteEvent(event.id)) },
                        enabled = !isBusy
                    ) {
                        Text("삭제", color = sg.rust, style = SgTheme.typography.labelSmall)
                    }
                }
            }
            event.location?.takeIf { it.isNotBlank() }?.let {
                Text("📍 $it", style = SgTheme.typography.bodySmall, color = sg.inkSoft)
            }
            event.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = SgTheme.typography.bodyMedium, color = sg.ink)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RsvpOptions.forEach { (status, label) ->
                    val selected = event.myRsvp == status

                    if (selected) {
                        Button(
                            onClick = { onAction(GroupEventsViewModel.Action.Rsvp(event, status)) },
                            enabled = !isBusy,
                            shape = SgTheme.shapes.button,
                            colors = ButtonDefaults.buttonColors(backgroundColor = sg.accent, contentColor = sg.onAccent)
                        ) { Text(label, style = SgTheme.typography.labelSmall) }
                    } else {
                        OutlinedButton(
                            onClick = { onAction(GroupEventsViewModel.Action.Rsvp(event, status)) },
                            enabled = !isBusy,
                            shape = SgTheme.shapes.button
                        ) { Text(label, color = sg.ink, style = SgTheme.typography.labelSmall) }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onAction(GroupEventsViewModel.Action.ToggleAttendees(event.id)) }
            ) {
                Text(
                    "참석 ${event.goingCount} · 미정 ${event.maybeCount} · 불참 ${event.notGoingCount} " +
                        if (isExpanded) "▲" else "▼",
                    style = SgTheme.typography.labelSmall,
                    color = sg.inkSoft
                )
            }
            if (isExpanded) {
                when {
                    attendees == null -> Text("불러오는 중...", style = SgTheme.typography.labelSmall, color = sg.inkFaint)
                    attendees.isEmpty() -> Text("아직 응답한 멤버가 없습니다.", style = SgTheme.typography.labelSmall, color = sg.inkFaint)
                    else -> attendees.forEach { attendee ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SgAvatar(attendee.name, imageUrl = attendee.profileImg)
                            Text(attendee.name, style = SgTheme.typography.bodySmall, color = sg.ink)
                            Text(
                                RsvpOptions.first { it.first == attendee.status }.second,
                                style = SgTheme.typography.labelSmall,
                                color = sg.inkFaint
                            )
                        }
                    }
                }
            }
            Text("${event.authorName}님이 만든 일정", style = SgTheme.typography.labelSmall, color = sg.inkFaint)
        }
    }
}
```

주의: `items(...)` 확장은 `androidx.compose.foundation.lazy.items`가 아니라 count 오버로드를
썼다 — key 람다가 인덱스를 받으므로 위 코드 그대로 사용. `SgAvatar` 시그니처가 다르면
멤버 탭(GroupDetailScreen의 기존 SgAvatar 호출)과 같은 형태로 맞춘다.

- [ ] **Step 3: GroupDetailScreen 연결** — Task 6 구조에 추가:

```kotlin
// 팩토리 추가:
@Composable
private fun groupEventsViewModel(groupId: Long): GroupEventsViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "group-events-$groupId") {
        GroupEventsViewModel(
            groupId = groupId,
            getGroupEventsUseCase = container.getGroupEventsUseCase,
            getEventDetailUseCase = container.getEventDetailUseCase,
            createEventUseCase = container.createEventUseCase,
            deleteEventUseCase = container.deleteEventUseCase,
            rsvpEventUseCase = container.rsvpEventUseCase,
            cancelEventRsvpUseCase = container.cancelEventRsvpUseCase,
            getCurrentUserIdUseCase = container.getCurrentUserIdUseCase
        )
    }
}

// GroupDetailScreen 파라미터에 추가(membersViewModel 뒤):
    eventsViewModel: GroupEventsViewModel = groupEventsViewModel(groupId)
// GroupDetailContent로 전달, Content 파라미터에도 추가.

// Content에서:
    val eventsUiState by eventsViewModel.uiState.collectAsState()
// 풀리프레시 onRefresh에 추가:
    eventsViewModel.onAction(GroupEventsViewModel.Action.Refresh)
// 페이지 2 교체:
    2 -> GroupEventsTab(
        uiState = eventsUiState,
        canModerate = uiState.canModerate,
        onAction = eventsViewModel::onAction
    )
```

- [ ] **Step 4: 컴파일+데스크톱 스모크**

Run: Task 4 Step 3와 동일 명령 → BUILD SUCCESSFUL 확인 후
`gradlew.bat :composeApp:run`으로 그룹 상세 → 일정 탭: 캘린더 표시·월 이동·일정 만들기
(제목+시각)·점 표시·RSVP 토글·참석자 펼침·삭제 확인(qa 계정), 종료.

- [ ] **Step 5: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupEventsViewModel.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupEventsTab.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt
```

---

### Task 8: Compose 설정 탭(GroupSettingsViewModel + GroupSettingsTab) + 닫힘 내비 배선

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupSettingsViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupSettingsTab.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/MainShell.kt`(+`TabShell.kt`/`DrawerShell.kt` — `homeRefreshRequested` 드릴링과 같은 지점, grep으로 확인)
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupsScreen.kt`

**Interfaces:**
- Consumes: Task 3 UseCase 3종+`UploadImageUseCase`+`GetGroupUseCase`, `rememberImagePickerLauncher`(기존).
- Produces: `GroupDetailScreen`에 **`onGroupClosed: () -> Unit` 파라미터 추가**(onRefreshHandled 뒤,
  modifier 앞), `GroupsScreen`에 `refreshRequested: Boolean`/`onRefreshHandled: () -> Unit` 추가.

- [ ] **Step 1: GroupSettingsViewModel.kt 작성**:

```kotlin
package kr.hhp227.storygroup.ui.screens.group

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
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.shared.domain.model.GroupRole
import kr.hhp227.storygroup.shared.domain.usecase.DeleteGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LeaveGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UpdateGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadImageUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 설정 탭 — 웹 /groups/[id]/settings 미러+레거시 설정 탭의 탈퇴 복원(탭별 VM 분리).
 * 진입 시 GetGroup으로 self-load(웹 설정 페이지 미러 — 상세 VM과 독립)해 폼을 시드한다.
 * OWNER=수정 폼+위험 구역(그룹 삭제), 비OWNER=그룹 나가기, 라운지=가입 방식·삭제·나가기 숨김.
 * ⚠️PATCH /api/groups/{id}는 name/description/image 전체 교체 계약 — 폼이 로드해 온 값을
 * 항상 실어 보낸다(joinType만 null=유지, 라운지가 이 경로를 쓴다).
 * iosApp GroupSettingsViewModel.swift와 1:1 미러
 */
class GroupSettingsViewModel(
    val groupId: Long,
    private val getGroupUseCase: GetGroupUseCase,
    private val updateGroupUseCase: UpdateGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val leaveGroupUseCase: LeaveGroupUseCase,
    private val uploadImageUseCase: UploadImageUseCase
) : ViewModel(), MviViewModel<GroupSettingsViewModel.UiState, GroupSettingsViewModel.Action, GroupSettingsViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            is Action.SetName -> _uiState.update { it.copy(name = action.name.take(100), saved = false) }
            is Action.SetDescription -> _uiState.update { it.copy(description = action.description.take(1000), saved = false) }
            is Action.SetJoinType -> _uiState.update { it.copy(joinType = action.joinType, saved = false) }
            is Action.ChangeImage -> changeImage(action.bytes, action.fileName, action.contentType)
            Action.Save -> save()
            Action.Delete -> close { deleteGroupUseCase(groupId) }
            Action.Leave -> close { leaveGroupUseCase(groupId) }
            Action.DismissCloseError -> _uiState.update { it.copy(closeError = null) }
        }
    }

    /** 진입(init)·재시도 시 발화 — 그룹을 읽어 폼을 시드한다(웹 설정 페이지 getGroup 미러) */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { getGroupUseCase(groupId) }
                .onSuccess { group ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            group = group,
                            name = group.name,
                            description = group.description.orEmpty(),
                            image = group.image,
                            joinType = group.joinType
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "그룹 정보를 불러오지 못했습니다.")
                    }
                }
        }
    }

    /** 대표 이미지 교체 — 계정 설정 아바타 패턴(업로드 성공 시 URL만 폼 상태에 반영, 저장은 별도) */
    private fun changeImage(bytes: ByteArray, fileName: String, contentType: String) {
        if (_uiState.value.isUploadingImage) return

        _uiState.update { it.copy(isUploadingImage = true, saveError = null) }
        viewModelScope.launch {
            runCatching { uploadImageUseCase(bytes, fileName, contentType) }
                .onSuccess { url ->
                    _uiState.update { it.copy(isUploadingImage = false, image = url, saved = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isUploadingImage = false, saveError = e.message ?: "이미지 업로드에 실패했습니다.")
                    }
                }
        }
    }

    /** 저장 — ⚠️전체 교체 계약이라 4필드 전부 전송, 라운지는 joinType을 안 보낸다(null=유지) */
    private fun save() {
        val state = _uiState.value
        val group = state.group ?: return
        if (state.isSaving || state.name.isBlank()) return

        _uiState.update { it.copy(isSaving = true, saveError = null, saved = false) }
        viewModelScope.launch {
            runCatching {
                updateGroupUseCase(
                    groupId = groupId,
                    name = state.name.trim(),
                    description = state.description.trim().ifBlank { null },
                    image = state.image?.ifBlank { null },
                    joinType = if (group.isLounge) null else state.joinType
                )
            }.onSuccess { updated ->
                _uiState.update { it.copy(isSaving = false, saved = true, group = updated) }
                // 상세 커버·제목 즉시 갱신 — 화면이 GroupDetailViewModel.Refresh를 트리거한다
                _event.tryEmit(Event.Saved)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, saveError = e.message ?: "저장에 실패했습니다.") }
            }
        }
    }

    /** 삭제/나가기 공용 — 성공하면 이 화면 자체가 닫힌다(Closed → pop+목록 갱신) */
    private fun close(operation: suspend () -> Unit) {
        if (_uiState.value.isClosing) return

        _uiState.update { it.copy(isClosing = true, closeError = null) }
        viewModelScope.launch {
            runCatching { operation() }
                .onSuccess {
                    _uiState.update { it.copy(isClosing = false) }
                    _event.tryEmit(Event.Closed)
                }
                .onFailure { e ->
                    // OWNER 나가기 거부("그룹 삭제를 이용하세요") 등 서버 문구를 그대로 보여준다
                    _uiState.update { it.copy(isClosing = false, closeError = e.message ?: "처리에 실패했습니다.") }
                }
        }
    }

    init {
        refresh()
    }

    data class UiState(
        // 로드 원본 — 역할(OWNER 폼/비OWNER 나가기)·라운지 분기의 기준
        val group: Group? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        // 폼 상태 — 로드 성공 시 시드, 이후 사용자 입력이 이긴다(재시도 Refresh는 다시 시드)
        val name: String = "",
        val description: String = "",
        val image: String? = null,
        val joinType: GroupJoinType = GroupJoinType.AUTO_APPROVE,
        val isUploadingImage: Boolean = false,
        val isSaving: Boolean = false,
        // 저장 성공 문구("저장했습니다.") — 다음 편집이 시작되면 사라진다(웹 saved 미러)
        val saved: Boolean = false,
        val saveError: String? = null,
        // 삭제/나가기 진행 — 확인 UI(2단계/다이얼로그)는 화면 로컬 상태
        val isClosing: Boolean = false,
        val closeError: String? = null
    ) {
        val isOwner: Boolean get() = group?.myRole == GroupRole.OWNER
        val isLounge: Boolean get() = group?.isLounge == true
    }

    sealed interface Action {
        data object Refresh : Action
        data class SetName(val name: String) : Action
        data class SetDescription(val description: String) : Action
        data class SetJoinType(val joinType: GroupJoinType) : Action
        data class ChangeImage(val bytes: ByteArray, val fileName: String, val contentType: String) : Action
        data object Save : Action
        data object Delete : Action
        data object Leave : Action
        data object DismissCloseError : Action
    }

    sealed interface Event {
        /** 저장 성공 — 화면이 상세 VM Refresh를 트리거해 커버·제목을 즉시 갱신한다 */
        data object Saved : Event
        /** 삭제/나가기 성공 — 화면이 onGroupClosed로 pop+그룹 목록 갱신을 요청한다 */
        data object Closed : Event
    }
}
```

- [ ] **Step 2: GroupSettingsTab.kt 작성**:

```kotlin
package kr.hhp227.storygroup.ui.screens.group

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgEmptyState
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.rememberImagePickerLauncher

/**
 * 설정 탭 — 웹 /groups/[id]/settings 미러: OWNER=수정 폼+위험 구역(그룹 삭제 2단계 확인),
 * 비OWNER=그룹 나가기(레거시 설정 탭 ll_withdrawal 미러), 라운지=가입 방식·삭제·나가기 숨김.
 * iosApp GroupSettingsTab.swift와 1:1 미러
 */
@Composable
internal fun GroupSettingsTab(
    uiState: GroupSettingsViewModel.UiState,
    onAction: (GroupSettingsViewModel.Action) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    // 웹 confirmingDelete 미러 — 확인 UI는 화면 로컬 상태
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var confirmingLeave by rememberSaveable { mutableStateOf(false) }
    val pickCoverImage = rememberImagePickerLauncher { picked ->
        onAction(GroupSettingsViewModel.Action.ChangeImage(picked.bytes, picked.fileName, picked.contentType))
    }

    when {
        uiState.group == null && uiState.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = sg.accent)
        }
        uiState.group == null -> Column(
            modifier.fillMaxSize().padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(uiState.error ?: "그룹 정보를 불러오지 못했습니다.", style = SgTheme.typography.bodyMedium, color = sg.rust)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onAction(GroupSettingsViewModel.Action.Refresh) }) {
                Text("다시 시도", color = sg.accent)
            }
        }
        else -> Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isOwner) {
                // 그룹 정보 수정 폼 — 웹 GroupSettingsForm 미러
                SgCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("그룹 설정", style = SgTheme.typography.titleSmall, color = sg.ink, fontWeight = FontWeight.Bold)
                        SgTextField(
                            value = uiState.name,
                            onValueChange = { onAction(GroupSettingsViewModel.Action.SetName(it)) },
                            label = "그룹 이름"
                        )
                        SgTextField(
                            value = uiState.description,
                            onValueChange = { onAction(GroupSettingsViewModel.Action.SetDescription(it)) },
                            label = "설명",
                            singleLine = false,
                            minLines = 3
                        )
                        // 대표 이미지 — 현재 값 미리보기+피커(계정 설정 아바타 패턴 재사용)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (uiState.image != null) {
                                AsyncImage(
                                    model = uiState.image,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                )
                            }
                            OutlinedButton(
                                onClick = pickCoverImage,
                                enabled = !uiState.isUploadingImage,
                                shape = SgTheme.shapes.button
                            ) {
                                Text(
                                    if (uiState.isUploadingImage) "업로드 중..." else "대표 이미지 변경",
                                    color = sg.accent
                                )
                            }
                        }
                        // 가입 방식 — 라운지는 숨김(웹 !group.isLounge 미러)
                        if (!uiState.isLounge) {
                            Column {
                                Text("가입 방식", style = SgTheme.typography.labelLarge, color = sg.inkSoft)
                                listOf(
                                    GroupJoinType.AUTO_APPROVE to "바로 가입",
                                    GroupJoinType.APPROVAL_REQUIRED to "승인 후 가입"
                                ).forEach { (type, label) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = uiState.joinType == type,
                                                onClick = { onAction(GroupSettingsViewModel.Action.SetJoinType(type)) }
                                            )
                                    ) {
                                        RadioButton(
                                            selected = uiState.joinType == type,
                                            onClick = { onAction(GroupSettingsViewModel.Action.SetJoinType(type)) },
                                            // M2 기본 선택색=secondary 함정 — accent로 고정(기존 규칙)
                                            colors = RadioButtonDefaults.colors(selectedColor = sg.accent)
                                        )
                                        Text(label, style = SgTheme.typography.bodyMedium, color = sg.ink)
                                    }
                                }
                            }
                        }
                        uiState.saveError?.let {
                            Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                        }
                        if (uiState.saved) {
                            Text("저장했습니다.", style = SgTheme.typography.bodySmall, color = sg.accent)
                        }
                        SgPrimaryButton(
                            text = "저장",
                            onClick = { onAction(GroupSettingsViewModel.Action.Save) },
                            enabled = uiState.name.isNotBlank() && !uiState.isUploadingImage,
                            isLoading = uiState.isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                // 위험 구역 — 라운지는 삭제 불가(웹 미러)
                if (!uiState.isLounge) {
                    SgCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("위험 구역", style = SgTheme.typography.titleSmall, color = sg.rust, fontWeight = FontWeight.Bold)
                            Text(
                                "그룹을 삭제하면 되돌릴 수 없습니다. 게시글, 채팅, 파일이 모두 사라집니다.",
                                style = SgTheme.typography.bodySmall,
                                color = sg.inkSoft
                            )
                            uiState.closeError?.let {
                                Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                            }
                            if (!confirmingDelete) {
                                OutlinedButton(onClick = { confirmingDelete = true }, shape = SgTheme.shapes.button) {
                                    Text("그룹 삭제", color = sg.rust)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("정말 삭제할까요?", style = SgTheme.typography.bodySmall, color = sg.ink)
                                    SgPrimaryButton(
                                        text = "삭제 확정",
                                        onClick = { onAction(GroupSettingsViewModel.Action.Delete) },
                                        isLoading = uiState.isClosing
                                    )
                                    OutlinedButton(
                                        onClick = { confirmingDelete = false },
                                        enabled = !uiState.isClosing,
                                        shape = SgTheme.shapes.button
                                    ) {
                                        Text("취소", color = sg.ink)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (!uiState.isLounge) {
                // 비OWNER — 그룹 나가기(레거시 설정 탭 ll_withdrawal 미러, POST /leave 소비)
                SgCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("그룹 나가기", style = SgTheme.typography.titleSmall, color = sg.ink, fontWeight = FontWeight.Bold)
                        Text(
                            "나가면 이 그룹의 게시글·채팅에 더는 참여할 수 없습니다.",
                            style = SgTheme.typography.bodySmall,
                            color = sg.inkSoft
                        )
                        uiState.closeError?.let {
                            Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                        }
                        if (!confirmingLeave) {
                            OutlinedButton(onClick = { confirmingLeave = true }, shape = SgTheme.shapes.button) {
                                Text("그룹 나가기", color = sg.rust)
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("정말 나갈까요?", style = SgTheme.typography.bodySmall, color = sg.ink)
                                SgPrimaryButton(
                                    text = "나가기",
                                    onClick = { onAction(GroupSettingsViewModel.Action.Leave) },
                                    isLoading = uiState.isClosing
                                )
                                OutlinedButton(
                                    onClick = { confirmingLeave = false },
                                    enabled = !uiState.isClosing,
                                    shape = SgTheme.shapes.button
                                ) {
                                    Text("취소", color = sg.ink)
                                }
                            }
                        }
                    }
                }
            } else {
                // 라운지 비OWNER — 설정할 항목이 없다(나가기도 서버가 거부)
                SgEmptyState(
                    title = "설정",
                    subtitle = "라운지에는 설정할 항목이 없습니다.",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp)
                )
            }
        }
    }
}
```

주의: `SgPrimaryButton` 시그니처는 기존 사용처(InviteDialog 등)와 동일하게 `(text, onClick,
enabled=, isLoading=, modifier=)` — enabled 파라미터가 없다면 기존 정의를 확인해 맞춘다.

- [ ] **Step 3: GroupDetailScreen 연결**:

```kotlin
// 팩토리 추가:
@Composable
private fun groupSettingsViewModel(groupId: Long): GroupSettingsViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "group-settings-$groupId") {
        GroupSettingsViewModel(
            groupId = groupId,
            getGroupUseCase = container.getGroupUseCase,
            updateGroupUseCase = container.updateGroupUseCase,
            deleteGroupUseCase = container.deleteGroupUseCase,
            leaveGroupUseCase = container.leaveGroupUseCase,
            uploadImageUseCase = container.uploadImageUseCase
        )
    }
}

// GroupDetailScreen: onRefreshHandled 뒤에 콜백 추가 + settingsViewModel default param:
    onGroupClosed: () -> Unit,
    ...
    settingsViewModel: GroupSettingsViewModel = groupSettingsViewModel(groupId)
// GroupDetailContent에도 동일 전달.

// Content에서:
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    // 설정 탭 일회성 이벤트 — 저장=상세 갱신, 삭제/나가기=화면 닫기
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.event.collect { event ->
            when (event) {
                GroupSettingsViewModel.Event.Saved ->
                    viewModel.onAction(GroupDetailViewModel.Action.Refresh)
                GroupSettingsViewModel.Event.Closed -> onGroupClosed()
            }
        }
    }
// 페이지 4(else 분기) 교체:
    else -> GroupSettingsTab(
        uiState = settingsUiState,
        onAction = settingsViewModel::onAction
    )
```

- [ ] **Step 4: App.kt + 셸 배선** — 홈 미러(`homeRefreshPending` 패턴)로 그룹 목록 갱신 신호 추가:

```kotlin
// App.kt SessionContent: homeRefreshPending 옆에
    var groupsRefreshPending by remember { mutableStateOf(false) }
// MainShell 호출부에 파라미터 추가(homeRefreshRequested 계열 옆):
    groupsRefreshRequested = groupsRefreshPending,
    onGroupsRefreshHandled = { groupsRefreshPending = false },
// GroupDetailRoute composable의 GroupDetailScreen 호출에 추가(onRefreshHandled 뒤):
    onGroupClosed = {
        // 나간/삭제한 그룹이 목록에 남지 않게 — 셸의 그룹 탭이 신호를 소비해 refresh한다
        groupsRefreshPending = true
        navController.popBackStack()
    },
```

`grep -n "homeRefreshRequested\|onHomeRefreshHandled" composeApp/src/commonMain/kotlin -r`로
드릴링 지점(MainShell→Tab/DrawerShell→DestinationContent)을 찾아 **같은 자리마다**
`groupsRefreshRequested: Boolean`/`onGroupsRefreshHandled: () -> Unit`을 나란히 추가하고,
DestinationContent의 GROUPS 분기에서 GroupsScreen으로 전달한다.

```kotlin
// GroupsScreen(2계층이면 Content 쪽)에 파라미터 추가 후, 프레젠터 수집부 근처에:
    // 상세에서 나가기/삭제 후 복귀 — 목록을 첫 페이지부터 다시 읽는다(홈 refreshRequested 미러)
    LaunchedEffect(refreshRequested) {
        if (refreshRequested) {
            lazyPagingItems.refresh()
            onRefreshHandled()
        }
    }
```

- [ ] **Step 5: 컴파일+데스크톱 스모크**

Run: Task 4 Step 3와 동일 명령 → BUILD SUCCESSFUL.
`gradlew.bat :composeApp:run`: OWNER 그룹에서 설정 탭 폼 로드·이름 수정 저장→커버 제목
즉시 갱신, 비OWNER 그룹(qa 계정)에서 나가기→목록 복귀+그룹 사라짐 확인.

- [ ] **Step 6: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupSettingsViewModel.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupSettingsTab.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/MainShell.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/TabShell.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/shell/DrawerShell.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupsScreen.kt
```

---

### Task 9: iOS 기존 3탭 ViewModel 분리 + GroupDetailView 재배선

**Files:**
- Create: `iosApp/iosApp/UI/Screens/Group/GroupFeedViewModel.swift`
- Create: `iosApp/iosApp/UI/Screens/Group/GroupAlbumViewModel.swift`
- Create: `iosApp/iosApp/UI/Screens/Group/GroupMembersViewModel.swift`
- Modify: `iosApp/iosApp/UI/Screens/Group/GroupDetailViewModel.swift` (축소 — 전체 교체)
- Modify: `iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift` (재배선)
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj` (신규 3파일 등록)

**Interfaces:**
- Consumes: Task 5의 Kotlin VM 경계(1:1 미러 대상).
- Produces: `GroupFeedViewModel`/`GroupAlbumViewModel`/`GroupMembersViewModel`/`GroupDetailViewModel`(축소) — Task 10·11이 같은 소유 구조에 합류.
- **미러 원본**: 이 시점에 리포에 이미 구현된 `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/`의 같은 이름 Kotlin VM들 — 착수 전 반드시 읽고 1:1로 맞춘다.

- [ ] **Step 1: GroupFeedViewModel.swift** — 기존 GroupDetailViewModel.swift의 피드 부분 이식:

```swift
import Combine
import Foundation
import Shared

/// 소식 탭 — composeApp GroupFeedViewModel.kt와 1:1 미러(탭별 VM 분리, 레거시 탭 Fragment VM 구조).
/// 피드는 UiState에 담기는 최신 PagingData. 갱신은 화면이 프레젠터 refresh()로 수행하므로 이벤트가 없다.
final class GroupFeedViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    let groupId: Int64

    // 목록 카드용 좋아요 토글 — 상세용 setPostLikedUseCase와 달리 좋아요 목록을 다시 읽지 않는다
    private let togglePostLikeUseCase: TogglePostLikeUseCase

    private var cancellables = Set<AnyCancellable>()

    private func setPagingData(_ pagingData: PagingData<Post>) {
        uiState.pagingData = pagingData
    }

    /// 수정/차단/삭제는 재조회 대신 현재 스냅샷에서 그 항목만 패치한다(Kotlin과 동일 —
    /// suspend 변환이라 Swift 클로저를 못 넘겨 PostBridges 브리지 함수 사용)
    private func applyPostUpdate(_ post: Post) {
        uiState.pagingData = PostBridgesKt.postPagingDataWithUpdate(pagingData: uiState.pagingData, post: post)
    }

    private func removeBlockedAuthorPosts(_ userId: Int64) {
        uiState.pagingData = PostBridgesKt.postPagingDataWithoutAuthor(pagingData: uiState.pagingData, userId: userId)
    }

    private func removeDeletedPost(_ postId: Int64) {
        uiState.pagingData = PostBridgesKt.postPagingDataWithoutPost(pagingData: uiState.pagingData, postId: postId)
    }

    func onAction(_ action: Action) {
        switch action {
        case .toggleLike(let post): toggleLike(post)
        case .dismissLikeError: uiState.likeError = nil
        }
    }

    /// 성공 반영은 리포지토리의 postUpdates 알림(applyPostUpdate)이 담당 — 여기선 실패만 다룬다
    private func toggleLike(_ post: Post) {
        Task { @MainActor in
            do {
                try await togglePostLikeUseCase.invoke(groupId: post.groupId, postId: post.id, liked: !post.likedByMe)
            } catch {
                uiState.likeError = error.kotlinMessage(fallback: "좋아요 처리에 실패했습니다.")
            }
        }
    }

    init(
        groupId: Int64,
        getGroupPostsPagingDataUseCase: GetGroupPostsPagingDataUseCase,
        observePostUpdatesUseCase: ObservePostUpdatesUseCase,
        observeUserBlocksUseCase: ObserveUserBlocksUseCase,
        observePostDeletionsUseCase: ObservePostDeletionsUseCase,
        togglePostLikeUseCase: TogglePostLikeUseCase
    ) {
        self.groupId = groupId
        self.togglePostLikeUseCase = togglePostLikeUseCase

        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getGroupPostsPagingDataUseCase(groupId: groupId)
            .cachedIn()
            .sink { [weak self] in self?.setPagingData($0) }
            .store(in: &cancellables)
        KotlinFlowPublisher<Post> { onEach in
            observePostUpdatesUseCase.updatesFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] post in self?.applyPostUpdate(post) }
        .store(in: &cancellables)
        KotlinFlowPublisher<KotlinLong> { onEach in
            observeUserBlocksUseCase.blocksFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] userId in self?.removeBlockedAuthorPosts(userId.int64Value) }
        .store(in: &cancellables)
        KotlinFlowPublisher<KotlinLong> { onEach in
            observePostDeletionsUseCase.deletionsFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] postId in self?.removeDeletedPost(postId.int64Value) }
        .store(in: &cancellables)
    }

    struct UiState {
        var pagingData: PagingData<Post> = PostBridgesKt.emptyPostPagingData()
        // 카드 좋아요 실패 안내 — 서버 확정 방식이라 실패해도 되돌릴 UI 상태가 없다
        var likeError: String? = nil
    }

    enum Action {
        case toggleLike(Post)
        case dismissLikeError
    }
}
```

- [ ] **Step 2: GroupAlbumViewModel.swift**:

```swift
import Combine
import Foundation
import Shared

/// 앨범 탭 — composeApp GroupAlbumViewModel.kt와 1:1 미러. 페이징 스트림 하나가 전부라
/// 액션·이벤트가 둘 다 없다(Action/Event=Never — 호출 불가).
final class GroupAlbumViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    let groupId: Int64

    private var cancellables = Set<AnyCancellable>()

    func onAction(_ action: Never) {}

    init(groupId: Int64, getGroupPhotosPagingDataUseCase: GetGroupPhotosPagingDataUseCase) {
        self.groupId = groupId

        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getGroupPhotosPagingDataUseCase(groupId: groupId)
            .cachedIn()
            .sink { [weak self] in self?.uiState.photosPagingData = $0 }
            .store(in: &cancellables)
    }

    struct UiState {
        var photosPagingData: PagingData<GroupPhoto> = GroupBridgesKt.emptyGroupPhotoPagingData()
    }

    enum Action {}
}
```

주의: `enum Action {}`(케이스 없는 enum=거주자 없음)과 `func onAction(_ action: Never)`가
프로토콜 요구와 충돌하면 `typealias Action = Never`+`func onAction(_ action: Never) {}`로
통일한다(Compose ACTION=Nothing 미러) — 둘 중 컴파일되는 쪽.

- [ ] **Step 3: GroupMembersViewModel.swift** — 기존 VM의 멤버/인박스/초대/DM 부분 이식.
  Kotlin 미러: 인박스는 role 게이트 없이 항상 시도(403=빈 목록):

```swift
import Combine
import Foundation
import Shared

/// 멤버 탭 — composeApp GroupMembersViewModel.kt와 1:1 미러(탭별 VM 분리).
/// 인박스는 모더레이터 전용 API지만 role 게이트 없이 항상 시도하고 403은 빈 목록으로
/// 흡수한다(초대코드 버튼 노출만 화면이 상세 VM의 canModerate로 게이트).
final class GroupMembersViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    let groupId: Int64

    private let getGroupMembersUseCase: GetGroupMembersUseCase

    private let getJoinRequestsUseCase: GetJoinRequestsUseCase

    private let approveJoinRequestUseCase: ApproveJoinRequestUseCase

    private let rejectJoinRequestUseCase: RejectJoinRequestUseCase

    private let createGroupInviteUseCase: CreateGroupInviteUseCase

    private let openDirectRoomUseCase: OpenDirectRoomUseCase

    private let getBlockedUsersUseCase: GetBlockedUsersUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        case .approveJoinRequest(let userId): approveJoinRequest(userId: userId)
        case .rejectJoinRequest(let userId): rejectJoinRequest(userId: userId)
        case .createInvite(let maxUses, let expiresInDays):
            createInvite(maxUses: maxUses, expiresInDays: expiresInDays)
        case .dismissInvite:
            uiState.createdInvite = nil
            uiState.inviteError = nil
        case .openDm(let userId, let userName): openDm(userId: userId, userName: userName)
        case .dismissDm: uiState.dmError = nil
        }
    }

    /// 화면 진입 onAppear가 발화 — 멤버+인박스+차단 목록 로드(재진입 신선화 겸용)
    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let members = try await getGroupMembersUseCase.invoke(groupId: groupId)
                // 모더레이터 전용 API — 권한이 없으면 403이라 빈 목록으로 흡수(인박스만 안 그려진다)
                let joinRequests = (try? await getJoinRequestsUseCase.invoke(groupId: groupId)) ?? []
                // 서버는 멤버 목록에서 차단 사용자를 빼주지 않는다 — 그리드에서 직접 걸러낸다
                let blockedUserIds = Set(((try? await getBlockedUsersUseCase.invoke()) ?? []).map { $0.userId })
                uiState.isLoading = false
                uiState.members = members
                uiState.blockedUserIds = blockedUserIds
                uiState.joinRequests = joinRequests
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "멤버를 불러오지 못했습니다.")
            }
        }
    }

    /// 가입 신청 승인 — 성공 시 인박스에서 제거하고 새 멤버를 목록에 반영한다(웹 handleApprove 미러)
    private func approveJoinRequest(userId: Int64) {
        if uiState.processingRequestUserId != nil { return }

        uiState.processingRequestUserId = userId
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await approveJoinRequestUseCase.invoke(groupId: groupId, userId: userId)
                // 승인은 확정됐으므로 멤버 재조회 실패는 무시한다 — 다음 refresh가 따라잡는다
                if let members = try? await getGroupMembersUseCase.invoke(groupId: groupId) {
                    uiState.members = members
                }
                uiState.joinRequests.removeAll { $0.userId == userId }
                uiState.processingRequestUserId = nil
            } catch {
                uiState.processingRequestUserId = nil
                uiState.actionError = error.kotlinMessage(fallback: "가입 승인에 실패했습니다.")
            }
        }
    }

    /// 가입 신청 거절 — 성공 시 인박스에서만 제거한다(웹 handleReject 미러)
    private func rejectJoinRequest(userId: Int64) {
        if uiState.processingRequestUserId != nil { return }

        uiState.processingRequestUserId = userId
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await rejectJoinRequestUseCase.invoke(groupId: groupId, userId: userId)
                uiState.joinRequests.removeAll { $0.userId == userId }
                uiState.processingRequestUserId = nil
            } catch {
                uiState.processingRequestUserId = nil
                uiState.actionError = error.kotlinMessage(fallback: "가입 거절에 실패했습니다.")
            }
        }
    }

    /// 초대코드 생성(모더레이터 전용) — 성공 시 다이얼로그가 결과(코드) 뷰로 전환된다
    private func createInvite(maxUses: Int?, expiresInDays: Int?) {
        if uiState.isCreatingInvite { return }

        uiState.isCreatingInvite = true
        uiState.inviteError = nil
        Task { @MainActor in
            do {
                let invite = try await createGroupInviteUseCase.invoke(
                    groupId: groupId,
                    maxUses: maxUses.map { KotlinInt(int: Int32($0)) },
                    expiresInDays: expiresInDays.map { KotlinInt(int: Int32($0)) }
                )
                uiState.isCreatingInvite = false
                uiState.createdInvite = invite
            } catch {
                uiState.isCreatingInvite = false
                uiState.inviteError = error.kotlinMessage(fallback: "초대코드 생성에 실패했습니다.")
            }
        }
    }

    /// 멤버와 1:1 DM 열기 — get-or-create(멱등)라 이미 방이 있으면 그 방으로 간다(웹 handleDm 미러)
    private func openDm(userId: Int64, userName: String) {
        if uiState.isOpeningDm { return }

        uiState.isOpeningDm = true
        uiState.dmError = nil
        Task { @MainActor in
            do {
                let chatRoomId = try await openDirectRoomUseCase.invoke(otherUserId: userId)
                uiState.isOpeningDm = false
                // 방 이름은 서버가 "DM" 고정이라 상대 이름을 제목으로 넘긴다(허브와 동일)
                event.send(.dmOpened(chatRoomId: chatRoomId.int64Value, title: userName))
            } catch {
                // 차단 관계(403 BLOCKED) 등 — 다이얼로그 안에 표시된다
                uiState.isOpeningDm = false
                uiState.dmError = error.kotlinMessage(fallback: "DM을 열지 못했습니다.")
            }
        }
    }

    init(
        groupId: Int64,
        getGroupMembersUseCase: GetGroupMembersUseCase,
        getJoinRequestsUseCase: GetJoinRequestsUseCase,
        approveJoinRequestUseCase: ApproveJoinRequestUseCase,
        rejectJoinRequestUseCase: RejectJoinRequestUseCase,
        createGroupInviteUseCase: CreateGroupInviteUseCase,
        openDirectRoomUseCase: OpenDirectRoomUseCase,
        getBlockedUsersUseCase: GetBlockedUsersUseCase,
        getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    ) {
        self.groupId = groupId
        self.getGroupMembersUseCase = getGroupMembersUseCase
        self.getJoinRequestsUseCase = getJoinRequestsUseCase
        self.approveJoinRequestUseCase = approveJoinRequestUseCase
        self.rejectJoinRequestUseCase = rejectJoinRequestUseCase
        self.createGroupInviteUseCase = createGroupInviteUseCase
        self.openDirectRoomUseCase = openDirectRoomUseCase
        self.getBlockedUsersUseCase = getBlockedUsersUseCase
        uiState.myUserId = getCurrentUserIdUseCase.invoke()?.int64Value
    }

    struct UiState {
        // 멤버 그리드에서 본인을 구분(본인은 DM 대상이 아니다) — 세션이 있는 한 nil이 아니다
        var myUserId: Int64? = nil
        var members: [GroupMember] = []
        /// 내가 차단한 사용자 — 서버가 멤버 목록에선 걸러주지 않아 화면이 직접 뺀다
        var blockedUserIds: Set<Int64> = []
        // 모더레이터에게만 채워진다 — 일반 멤버는 403이 빈 목록으로 흡수돼 인박스가 안 그려진다
        var joinRequests: [GroupJoinRequest] = []
        // 승인/거절 버튼 로딩 표시용 — 동시에 하나만 처리(웹 busyFor 미러)
        var processingRequestUserId: Int64? = nil
        var isLoading = false
        var error: String? = nil
        var actionError: String? = nil
        var createdInvite: GroupInvite? = nil
        var isCreatingInvite = false
        var inviteError: String? = nil
        var isOpeningDm = false
        var dmError: String? = nil

        /// 멤버 그리드에 그릴 멤버 — 차단한 사용자는 뺀다(차단=내 화면에서 숨김)
        var visibleMembers: [GroupMember] { members.filter { !blockedUserIds.contains($0.userId) } }
    }

    enum Action {
        case refresh
        case approveJoinRequest(userId: Int64)
        case rejectJoinRequest(userId: Int64)
        case createInvite(maxUses: Int?, expiresInDays: Int?)
        case dismissInvite
        case openDm(userId: Int64, userName: String)
        case dismissDm
    }

    enum Event {
        /// DM 방 확보 성공 — 화면이 채팅방(groupId=nil)으로 push한다
        case dmOpened(chatRoomId: Int64, title: String)
    }
}
```

- [ ] **Step 4: GroupDetailViewModel.swift 축소** — 파일 전체를 Compose Task 5 Step 4의
  Kotlin 축소판과 1:1로 교체(UiState=group/defaultChatRoomId/isLoading/error+canModerate 파생,
  Action=refresh만, Event=Never, 주입=getGroupUseCase/getGroupDefaultChatRoomUseCase 2개):

```swift
import Combine
import Foundation
import Shared

/// 그룹 상세 화면 수준 VM — composeApp GroupDetailViewModel.kt(축소판)와 1:1 미러.
/// 커버(이름/설명/역할)+상단바 채팅 버튼용 기본 방 id만 담당 — 탭 상태는 탭별 VM 5개가 소유.
final class GroupDetailViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    let groupId: Int64

    private let getGroupUseCase: GetGroupUseCase

    private let getGroupDefaultChatRoomUseCase: GetGroupDefaultChatRoomUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        }
    }

    /// 상세 진입 시 발화 — VM이 탭 전환에도 유지되므로 재진입 때도 최신화된다
    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let group = try await getGroupUseCase.invoke(groupId: groupId)
                // 상단바 채팅 버튼용 기본 방 id — 실패해도 상세는 그린다(버튼만 숨는다)
                let defaultChatRoomId = ((try? await getGroupDefaultChatRoomUseCase.invoke(groupId: groupId)) ?? nil)?.int64Value
                uiState.isLoading = false
                uiState.group = group
                uiState.defaultChatRoomId = defaultChatRoomId
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "그룹을 불러오지 못했습니다.")
            }
        }
    }

    init(groupId: Int64, getGroupUseCase: GetGroupUseCase, getGroupDefaultChatRoomUseCase: GetGroupDefaultChatRoomUseCase) {
        self.groupId = groupId
        self.getGroupUseCase = getGroupUseCase
        self.getGroupDefaultChatRoomUseCase = getGroupDefaultChatRoomUseCase
    }

    struct UiState {
        var group: Group? = nil
        var defaultChatRoomId: Int64? = nil
        var isLoading = false
        var error: String? = nil

        // 초대코드 버튼·일정 삭제(모더레이터) 노출 조건 — 웹 lib/roles canModerate 미러(라운지 제외)
        var canModerate: Bool {
            guard let group = group else { return false }
            return !group.isLounge && group.myRole != .member
        }
    }

    enum Action {
        case refresh
    }
}
```

- [ ] **Step 5: GroupDetailView.swift 재배선** — 변경 지도(파일을 열고 아래 매핑대로 치환):
  - `GroupDetailView`: @StateObject 4개(`viewModel`/`groupFeedViewModel`/`groupAlbumViewModel`/`groupMembersViewModel`)를 init에서 생성(주입 목록은 위 각 VM init과 1:1), Content에 전부 전달.
  - `GroupDetailContent`: @ObservedObject 4개. `lazyPagingItems`/`photoLazyPagingItems` 초기화의
    상태 원천을 `viewModel` → 각각 `groupFeedViewModel`/`groupAlbumViewModel`로 교체(프레젠터
    소유는 Content 유지 — 탭 전환 keep-alive).
  - 상태 참조 치환: `viewModel.uiState.pagingData`→`groupFeedViewModel.uiState.pagingData`,
    `photosPagingData`→`groupAlbumViewModel...`, `members/visibleMembers/blockedUserIds/joinRequests/processingRequestUserId/actionError/createdInvite/isCreatingInvite/inviteError/isOpeningDm/dmError/myUserId`→`groupMembersViewModel.uiState...`,
    `likeError`·`toggleLike`→`groupFeedViewModel`, `canModerate`→`viewModel.uiState.canModerate`(유지).
  - `.onAppear { viewModel.onAction(.refresh) }` → 상세+멤버 둘 다:
    `viewModel.onAction(.refresh); groupMembersViewModel.onAction(.refresh)`.
  - `.onReceive(viewModel.event)`의 `.refreshFeed` 분기 삭제(이벤트 자체가 Never) →
    `.onReceive(groupMembersViewModel.event)`로 `.dmOpened`만 수신.
  - `.refreshable`: `viewModel.onAction(.refresh); groupMembersViewModel.onAction(.refresh)` +
    기존 `awaitRefresh()` 2건 유지.
  - 멤버 탭 인박스/초대/DM 다이얼로그의 onAction 대상도 `groupMembersViewModel`로.

- [ ] **Step 6: pbxproj 등록(3파일)** — `grep -o "A101[0-9A-F]\{4\}AABBCCDDEEFF[0-9A-F]\{4\}" iosApp/iosApp.xcodeproj/project.pbxproj | sort -u | tail`로 마지막 사용 ID를 확인하고, 다음 번호부터
  `GroupFeedViewModel.swift`/`GroupAlbumViewModel.swift`/`GroupMembersViewModel.swift`를
  PBXBuildFile+PBXFileReference+Group 그룹(children)+Sources 4곳에 기존 Group 파일들과 같은
  형식으로 추가. 등록 후 정합성 검증: 디스크 .swift 수 == PBXFileReference .swift 수 == Sources 항목 수.

- [ ] **Step 7: 스테이징**

```bash
git add iosApp/iosApp/UI/Screens/Group/GroupFeedViewModel.swift \
  iosApp/iosApp/UI/Screens/Group/GroupAlbumViewModel.swift \
  iosApp/iosApp/UI/Screens/Group/GroupMembersViewModel.swift \
  iosApp/iosApp/UI/Screens/Group/GroupDetailViewModel.swift \
  iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift \
  iosApp/iosApp.xcodeproj/project.pbxproj
```

---

### Task 10: iOS 일정 탭(GroupEventsViewModel.swift + GroupEventsTab.swift)

**Files:**
- Create: `iosApp/iosApp/UI/Screens/Group/GroupEventsViewModel.swift`
- Create: `iosApp/iosApp/UI/Screens/Group/GroupEventsTab.swift`
- Modify: `iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift` (case 2 연결+refreshable)
- Modify: `iosApp/iosApp/DI/AppContainer.swift` (이벤트 유스케이스 6종 노출)
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj` (신규 2파일)

**Interfaces:**
- Consumes: shared UseCase 6종(Task 2).
- **미러 원본**: 리포에 이미 구현된 `composeApp/.../ui/screens/group/GroupEventsViewModel.kt`+`GroupEventsTab.kt` — 착수 전 반드시 읽고 상태·액션·문구를 1:1로 맞춘다.

- [ ] **Step 1: AppContainer.swift에 유스케이스 노출** — 기존 그룹 유스케이스 선언·조립과 같은
  형식으로 `getGroupEventsUseCase`/`getEventDetailUseCase`/`createEventUseCase`/`deleteEventUseCase`/`rsvpEventUseCase`/`cancelEventRsvpUseCase` 6개 추가(EventRepositoryImpl 조립 포함 — 파일 안의
  GroupRepositoryImpl 조립 라인을 그대로 본떠 `EventRepositoryImpl(client:)` 인자 이름을 맞춘다).

- [ ] **Step 2: GroupEventsViewModel.swift** — Compose Task 7 Step 1의 Kotlin과 1:1 미러.
  날짜 계산은 `Calendar.current`(로컬 타임존):

```swift
import Combine
import Foundation
import Shared

/// 일정 탭 — composeApp GroupEventsViewModel.kt와 1:1 미러(웹 캘린더 페이지 미러).
/// 날짜 귀속·선택 키는 로컬 yyyy-MM-dd. RSVP는 서버가 돌려준 집계 갱신 일정으로 카드 교체.
final class GroupEventsViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState: UiState

    let groupId: Int64

    private let getGroupEventsUseCase: GetGroupEventsUseCase

    private let getEventDetailUseCase: GetEventDetailUseCase

    private let createEventUseCase: CreateEventUseCase

    private let deleteEventUseCase: DeleteEventUseCase

    private let rsvpEventUseCase: RsvpEventUseCase

    private let cancelEventRsvpUseCase: CancelEventRsvpUseCase

    // 서버 ISO(소수부 자릿수 초과 가능) → Date — TimeFormats.swift의 기존 파서를 재사용한다.
    // 없으면 같은 파일의 소수부 스트립 정규식 방식을 private로 복사해 온다.
    static func parseIso(_ iso: String) -> Date? { TimeFormats.parseServerDate(iso) }

    static func dateKey(_ date: Date) -> String {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", parts.year ?? 0, parts.month ?? 0, parts.day ?? 0)
    }

    /// 로컬 연월일시분 → 서버로 보낼 ISO — Compose localToIso 미러
    static func localToIso(year: Int, month: Int, day: Int, hour: Int, minute: Int) -> String {
        var components = DateComponents()
        components.year = year; components.month = month; components.day = day
        components.hour = hour; components.minute = minute
        let date = Calendar.current.date(from: components) ?? Date(timeIntervalSince1970: 0)
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.string(from: date)
    }

    func onAction(_ action: Action) {
        switch action {
        case .refresh: loadMonth()
        case .moveMonth(let delta): moveMonth(delta)
        case .goToday: goToday()
        case .selectDay(let dateKey): uiState.selectedDay = dateKey
        case .toggleCreateForm:
            uiState.showCreateForm.toggle()
            uiState.createError = nil
        case .createEvent(let title, let location, let description, let startHour, let startMinute, let endHour, let endMinute):
            createEvent(title: title, location: location, description: description,
                        startHour: startHour, startMinute: startMinute, endHour: endHour, endMinute: endMinute)
        case .deleteEvent(let eventId): deleteEvent(eventId)
        case .rsvp(let event, let status): rsvp(event, status)
        case .toggleAttendees(let eventId): toggleAttendees(eventId)
        case .dismissActionError: uiState.actionError = nil
        }
    }

    /// 현재 앵커 월 재조회 — [월초, 다음달 초) ISO(웹 loadMonth 미러)
    private func loadMonth() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        let (nextYear, nextMonth) = uiState.month == 12 ? (uiState.year + 1, 1) : (uiState.year, uiState.month + 1)
        Task { @MainActor in
            do {
                let events = try await getGroupEventsUseCase.invoke(
                    groupId: groupId,
                    fromIso: Self.localToIso(year: uiState.year, month: uiState.month, day: 1, hour: 0, minute: 0),
                    toIso: Self.localToIso(year: nextYear, month: nextMonth, day: 1, hour: 0, minute: 0)
                )
                uiState.isLoading = false
                uiState.events = events
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "일정을 불러오지 못했습니다.")
            }
        }
    }

    private func moveMonth(_ delta: Int) {
        let zeroBased = uiState.year * 12 + (uiState.month - 1) + delta
        uiState.year = zeroBased / 12
        uiState.month = zeroBased % 12 + 1
        uiState.selectedDay = String(format: "%04d-%02d-01", uiState.year, uiState.month)
        uiState.events = nil
        uiState.isLoading = false
        loadMonth()
    }

    private func goToday() {
        let today = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        guard let year = today.year, let month = today.month, let day = today.day else { return }
        let key = String(format: "%04d-%02d-%02d", year, month, day)

        if year != uiState.year || month != uiState.month {
            uiState.year = year
            uiState.month = month
            uiState.selectedDay = key
            uiState.events = nil
            uiState.isLoading = false
            loadMonth()
        } else {
            uiState.selectedDay = key
        }
    }

    /// 생성 — 날짜는 캘린더 선택일 고정이라 결과는 항상 현재 달(목록 삽입+정렬만)
    private func createEvent(
        title: String, location: String, description: String,
        startHour: Int, startMinute: Int, endHour: Int?, endMinute: Int?
    ) {
        if uiState.isCreating { return }

        let parts = uiState.selectedDay.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return }
        var endsAtIso: String? = nil
        if let endHour = endHour, let endMinute = endMinute {
            if endHour < startHour || (endHour == startHour && endMinute < startMinute) {
                uiState.createError = "종료 시각은 시작 시각보다 빠를 수 없습니다"
                return
            }
            endsAtIso = Self.localToIso(year: parts[0], month: parts[1], day: parts[2], hour: endHour, minute: endMinute)
        }

        uiState.isCreating = true
        uiState.createError = nil
        Task { @MainActor in
            do {
                let created = try await createEventUseCase.invoke(
                    groupId: groupId,
                    title: title,
                    description: description.isEmpty ? nil : description,
                    location: location.isEmpty ? nil : location,
                    startsAtIso: Self.localToIso(year: parts[0], month: parts[1], day: parts[2], hour: startHour, minute: startMinute),
                    endsAtIso: endsAtIso
                )
                uiState.isCreating = false
                uiState.showCreateForm = false
                uiState.events = ((uiState.events ?? []) + [created]).sorted { $0.startsAt < $1.startsAt }
            } catch {
                uiState.isCreating = false
                uiState.createError = error.kotlinMessage(fallback: "일정을 만들지 못했습니다.")
            }
        }
    }

    private func deleteEvent(_ eventId: Int64) {
        if uiState.busyEventId != nil { return }

        uiState.busyEventId = eventId
        uiState.actionError = nil
        Task { @MainActor in
            do {
                try await deleteEventUseCase.invoke(groupId: groupId, eventId: eventId)
                uiState.busyEventId = nil
                uiState.events = uiState.events?.filter { $0.id != eventId }
            } catch {
                uiState.busyEventId = nil
                uiState.actionError = error.kotlinMessage(fallback: "일정 삭제에 실패했습니다.")
            }
        }
    }

    /// RSVP — 같은 상태 재탭=취소(웹 handleRsvp 미러)
    private func rsvp(_ event: GroupEvent, _ status: RsvpStatus) {
        if uiState.busyEventId != nil { return }

        uiState.busyEventId = event.id
        uiState.actionError = nil
        Task { @MainActor in
            do {
                let updated = event.myRsvp == status
                    ? try await cancelEventRsvpUseCase.invoke(groupId: groupId, eventId: event.id)
                    : try await rsvpEventUseCase.invoke(groupId: groupId, eventId: event.id, status: status)
                uiState.busyEventId = nil
                uiState.events = uiState.events?.map { $0.id == updated.id ? updated : $0 }
                // 명단이 바뀌었으니 펼쳐볼 때 다시 불러온다(웹 setAttendees(null) 미러)
                uiState.attendeesByEvent.removeValue(forKey: event.id)
            } catch {
                uiState.busyEventId = nil
                uiState.actionError = error.kotlinMessage(fallback: "참석 응답에 실패했습니다.")
            }
        }
    }

    /// 참석자 펼침 — 캐시 없으면 단건 조회, 실패는 조용히 빈 목록(부가 정보 — 웹 미러)
    private func toggleAttendees(_ eventId: Int64) {
        if uiState.expandedEventIds.contains(eventId) {
            uiState.expandedEventIds.remove(eventId)
            return
        }
        uiState.expandedEventIds.insert(eventId)
        if uiState.attendeesByEvent[eventId] == nil {
            Task { @MainActor in
                let attendees = (try? await getEventDetailUseCase.invoke(groupId: groupId, eventId: eventId).attendees) ?? []
                uiState.attendeesByEvent[eventId] = attendees
            }
        }
    }

    init(
        groupId: Int64,
        getGroupEventsUseCase: GetGroupEventsUseCase,
        getEventDetailUseCase: GetEventDetailUseCase,
        createEventUseCase: CreateEventUseCase,
        deleteEventUseCase: DeleteEventUseCase,
        rsvpEventUseCase: RsvpEventUseCase,
        cancelEventRsvpUseCase: CancelEventRsvpUseCase,
        getCurrentUserIdUseCase: GetCurrentUserIdUseCase
    ) {
        self.groupId = groupId
        self.getGroupEventsUseCase = getGroupEventsUseCase
        self.getEventDetailUseCase = getEventDetailUseCase
        self.createEventUseCase = createEventUseCase
        self.deleteEventUseCase = deleteEventUseCase
        self.rsvpEventUseCase = rsvpEventUseCase
        self.cancelEventRsvpUseCase = cancelEventRsvpUseCase
        let today = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        uiState = UiState(
            myUserId: getCurrentUserIdUseCase.invoke()?.int64Value,
            year: today.year ?? 2026,
            month: today.month ?? 1,
            selectedDay: String(format: "%04d-%02d-%02d", today.year ?? 0, today.month ?? 0, today.day ?? 0)
        )
        loadMonth()
    }

    struct UiState {
        var myUserId: Int64? = nil
        var year: Int = 0
        var month: Int = 0
        var selectedDay: String = ""
        // nil=이 달을 아직 못 읽음(로딩/실패) — 빈 목록과 구분(웹 events===null 미러)
        var events: [GroupEvent]? = nil
        var isLoading = false
        var error: String? = nil
        var showCreateForm = false
        var isCreating = false
        var createError: String? = nil
        var busyEventId: Int64? = nil
        var actionError: String? = nil
        var expandedEventIds: Set<Int64> = []
        var attendeesByEvent: [Int64: [EventAttendee]] = [:]

        /// 로컬 날짜 키별 일정 — 캘린더 점·선택일 목록의 원천(웹 eventsByDay 미러)
        var eventsByDay: [String: [GroupEvent]] {
            Dictionary(grouping: events ?? []) { event in
                GroupEventsViewModel.parseIso(event.startsAt).map(GroupEventsViewModel.dateKey)
                    ?? String(event.startsAt.prefix(10))
            }
        }

        var selectedEvents: [GroupEvent] { eventsByDay[selectedDay] ?? [] }
    }

    enum Action {
        case refresh
        case moveMonth(Int)
        case goToday
        case selectDay(String)
        case toggleCreateForm
        case createEvent(title: String, location: String, description: String,
                         startHour: Int, startMinute: Int, endHour: Int?, endMinute: Int?)
        case deleteEvent(Int64)
        case rsvp(GroupEvent, RsvpStatus)
        case toggleAttendees(Int64)
        case dismissActionError
    }
}
```

주의: `TimeFormats.parseServerDate`는 실제 헬퍼 이름을 `UI/Util/TimeFormats.swift`에서 확인해
맞춘다(소수부 스트립 파서가 이미 있음 — 이름만 다를 수 있다). shared 브리징에서
`getGroupEventsUseCase.invoke(...)`의 파라미터 라벨은 Kotlin 파라미터명 그대로다.

- [ ] **Step 3: GroupEventsTab.swift** — Compose GroupEventsTab과 1:1(캘린더 카드=LazyVGrid 7열,
  인라인 생성 폼, 선택일 카드 목록). 구조 요약이 아닌 전체 뷰를 작성하되 다음 명세를 지킨다:
  - `struct GroupEventsTab: View` 파라미터: `@ObservedObject var viewModel: GroupEventsViewModel`,
    `let canModerate: Bool` (뷰 생성자 순서=Compose 파라미터 순서).
  - 캘린더 카드: 헤더 Row(chevron.left/right 버튼, "yyyy년 M월", "오늘", Spacer, "일정 만들기"/"닫기"),
    요일 라벨 7개(일=rust·토=accent·나머지 inkFaint), `LazyVGrid(columns: 7×flexible, spacing 2)`에
    앞쪽 빈 칸(`Color.clear.frame(height: 52)`)+일자 셀 버튼(오늘=accent 테두리 1.5,
    선택=accent 배경+onAccent 글자, 점 최대 3개 HStack(spacing 2) 5×5 Circle).
  - 생성 폼(showCreateForm일 때): 제목 TextField, **시각은 네이티브 `DatePicker`(.hourAndMinute)**
    2개(시작 기본 19:00, 종료는 "종료 시각 추가" Toggle 뒤) — 플랫폼 관용 예외(Compose는 HH:MM 텍스트),
    장소·설명 TextField, 에러 문구, 등록 버튼(제목 비면 disabled) →
    `viewModel.onAction(.createEvent(...))`에 hour/minute을 `Calendar.current.dateComponents`로 추출해 전달.
  - 선택일 헤더("M월 d일 (요일)"), 로딩/에러(재시도)/빈 상태, EventCard들(제목·시각 범위·삭제
    (isMine||canModerate, 확인 alert)·장소 📍·설명·RSVP 3버튼(선택=accent 채움)·"참석 n · 미정 n ·
    불참 n ▼/▲" 탭=참석자 펼침(SGComponents 아바타)·"OO님이 만든 일정" 캡션).
  - 색은 전부 `@Environment(\.sgColors)`.

- [ ] **Step 4: GroupDetailView.swift 연결** — `GroupDetailView` init에 `groupEventsViewModel`
  @StateObject 추가(컨테이너 유스케이스 6종+getCurrentUserIdUseCase 주입), Content의
  `tabContent` `case 2:`를 `GroupEventsTab(viewModel: groupEventsViewModel, canModerate: viewModel.uiState.canModerate)`로 교체, `.refreshable`에 `groupEventsViewModel.onAction(.refresh)` 추가.

- [ ] **Step 5: pbxproj 등록(2파일)** — Task 9 Step 6과 같은 절차로 `GroupEventsViewModel.swift`/`GroupEventsTab.swift` 추가+정합성 검증.

- [ ] **Step 6: 스테이징**

```bash
git add iosApp/iosApp/UI/Screens/Group/GroupEventsViewModel.swift \
  iosApp/iosApp/UI/Screens/Group/GroupEventsTab.swift \
  iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift \
  iosApp/iosApp/DI/AppContainer.swift \
  iosApp/iosApp.xcodeproj/project.pbxproj
```

---

### Task 11: iOS 설정 탭(GroupSettingsViewModel.swift + GroupSettingsTab.swift) + 닫힘 배선

**Files:**
- Create: `iosApp/iosApp/UI/Screens/Group/GroupSettingsViewModel.swift`
- Create: `iosApp/iosApp/UI/Screens/Group/GroupSettingsTab.swift`
- Modify: `iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift`
- Modify: `iosApp/iosApp/UI/Shell/MainShellView.swift`
- Modify: `iosApp/iosApp/UI/Screens/Group/GroupsView.swift`
- Modify: `iosApp/iosApp/DI/AppContainer.swift` (updateGroup/deleteGroup/leaveGroup 3종 노출 — uploadImageUseCase는 기존 확인)
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj` (신규 2파일)

**미러 원본**: 리포에 이미 구현된 `composeApp/.../ui/screens/group/GroupSettingsViewModel.kt`+`GroupSettingsTab.kt` — 착수 전 반드시 읽고 상태·액션·문구를 1:1로 맞춘다.

- [ ] **Step 1: GroupSettingsViewModel.swift** — Compose GroupSettingsViewModel.kt(디스크)와 1:1 미러:
  UiState(group/isLoading/error/name/description/image/joinType/isUploadingImage/isSaving/saved/
  saveError/isClosing/closeError + isOwner/isLounge 파생), Action(refresh/setName/setDescription/
  setJoinType/changeImage(bytes 데이터는 iOS 기존 Data→KotlinByteArray 브리지 사용)/save/delete/
  leave/dismissCloseError), Event(saved/closed — PassthroughSubject). save()는 ⚠️전체 교체 계약
  주석과 함께 4필드 전송, 라운지는 joinType=nil. delete/leave는 공용 close 헬퍼(성공 시
  event.send(.closed), 실패 closeError=서버 문구). init에서 refresh() 자가 로드.
  `updateGroupUseCase.invoke(groupId:name:description:image:joinType:)` 라벨은 Kotlin 파라미터명.

- [ ] **Step 2: GroupSettingsTab.swift** — Compose GroupSettingsTab과 1:1:
  `@ObservedObject var viewModel: GroupSettingsViewModel`만 받는 뷰.
  OWNER=폼(이름/설명 TextField, 대표 이미지 AsyncImage 56pt+PHPicker 버튼(계정 설정 아바타
  패턴 재사용 — 기존 iOS 이미지 피커 유틸), 가입 방식 Picker(라운지 숨김), saveError/"저장했습니다."
  문구, 저장 버튼)+위험 구역 카드(라운지 아님 — confirmingDelete 2단계 @State), 비OWNER(라운지
  아님)=그룹 나가기 카드(confirmingLeave), 라운지 비OWNER=SGEmptyState("라운지에는 설정할
  항목이 없습니다."). 색은 sgColors, 문구는 Compose와 동일.

- [ ] **Step 3: GroupDetailView.swift 연결** — `groupSettingsViewModel` @StateObject 추가,
  `GroupDetailView`/`GroupDetailContent`에 **`onGroupClosed: () -> Void` 파라미터 추가**
  (Compose 시그니처 순서 미러 — chatViewModel 뒤), `tabContent` `default:`(설정)를
  `GroupSettingsTab(viewModel: groupSettingsViewModel)`로 교체, Content에
  `.onReceive(groupSettingsViewModel.event)` 추가: `.saved`→`viewModel.onAction(.refresh)`,
  `.closed`→`onGroupClosed()`.

- [ ] **Step 4: 셸 배선** — `MainShellView.swift`:
  - `@State private var groupsRefreshPending = false` 추가.
  - `GroupDetailView(groupId:container:chatViewModel:)` 호출에 `onGroupClosed: { selectedGroupId = nil; groupsRefreshPending = true }` 전달.
  - `GroupsView` 호출부에 `refreshRequested: groupsRefreshPending, onRefreshHandled: { groupsRefreshPending = false }` 추가(두 셸 다 — Compose homeRefreshRequested 드릴링 미러).
  - `GroupsView.swift`: 파라미터 2개 추가 후 `.onChange(of: refreshRequested) { if $0 { lazyPagingItems.refresh(); onRefreshHandled() } }`
    (iOS 15라 구형 onChange 시그니처 — 프레젠터를 소유한 Content 계층에 단다).

- [ ] **Step 5: pbxproj 등록(2파일)+정합성 검증** — Task 9 Step 6 절차.

- [ ] **Step 6: 스테이징**

```bash
git add iosApp/iosApp/UI/Screens/Group/GroupSettingsViewModel.swift \
  iosApp/iosApp/UI/Screens/Group/GroupSettingsTab.swift \
  iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift \
  iosApp/iosApp/UI/Shell/MainShellView.swift \
  iosApp/iosApp/UI/Screens/Group/GroupsView.swift \
  iosApp/iosApp/DI/AppContainer.swift \
  iosApp/iosApp.xcodeproj/project.pbxproj
```

---

### Task 12: 최종 검증 + 스테이징 정리 + 커밋 메시지 전달

**Files:** 신규 파일 없음(검증·정리 전용)

- [ ] **Step 1: Kotlin 3타깃 컴파일+테스트**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat :shared:jvmTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinJvm :shared:compileKotlinIosSimulatorArm64"`
Expected: BUILD SUCCESSFUL (⚠️Swift/Mac은 검증 불가 — 결과 보고에 명시)

- [ ] **Step 2: 데스크톱 E2E 스모크** — `gradlew.bat :composeApp:run`(백그라운드)으로:
  ① 그룹 상세 진입 → 5탭 전환(피드 스크롤 유지) ② 일정 탭: 월 이동·오늘·일정 만들기·점·
  RSVP 토글·같은 상태 재탭(취소)·참석자 펼침·삭제 ③ 설정 탭: OWNER 폼 로드·이름 수정 저장→
  커버 제목 갱신·(테스트 그룹에서) 삭제 2단계 ④ 비OWNER 그룹 나가기→목록 복귀+refresh.
  종료: PowerShell `Get-Process | Where-Object {$_.MainWindowTitle -eq "StoryGroup"} | Stop-Process`

- [ ] **Step 3: EOL·스테이징 최종 확인**

```bash
# 신규/수정 파일에 CRLF 유입이 없는지
git diff --cached --name-only | while read f; do file "$f" | grep -q CRLF && echo "CRLF! $f"; done
# 스테이징 목록이 이 계획의 파일들+계획/스펙 문서로만 구성됐는지(무관 파일 유입 금지)
git diff --cached --name-only
git add docs/superpowers/plans/2026-08-12-group-events-settings-tabs.md
```

- [ ] **Step 4: 커밋 메시지 전달(커밋은 사용자)** — 사용자에게 아래 2벌 제안:

```
① 그룹 상세 탭별 ViewModel 분리(소식/앨범/멤버) — 레거시 탭 Fragment VM 구조 미러
   경로: shared 무관, composeApp group/ 5파일 + iosApp Group/ 5파일 + pbxproj
② 그룹 상세 일정·설정 탭 구현(웹 캘린더+RSVP / 설정 폼+삭제+나가기, 서버 수정 0)
   경로: shared Event·Group 계층 + composeApp 일정/설정 + iosApp 미러 + App/셸 배선 + 문서
```
(한 커밋으로 합칠지 2벌로 나눌지는 사용자 선택 — 나누려면 위 경로 한정 커밋)

- [ ] **Step 5: 결과 보고** — 검증 통과 항목/미검증(Swift)/실기기 QA 포인트(캘린더 셀 터치
  크기, iOS DatePicker 로케일, 나가기 후 목록 갱신)를 요약해 사용자에게 전달
