# 친구 프레즌스(온라인 표시) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** "개인 알림 큐 구독 중=온라인"으로 정의한 전역 프레즌스를 서버에 얹고, KMP 친구 탭·웹 친구 목록 아바타에 온라인 도트를 단다.

**Architecture:** 서버는 기존 인메모리 트래커 패턴(SUBSCRIBE/UNSUBSCRIBE/DISCONNECT 리스너+`synchronized` 맵)의 세 번째 트래커 `UserPresenceTracker`를 신설해, 전환 시 "나를 친구로 등록한 사람들"의 개인 큐로 `PRESENCE_CHANGED`를 팬아웃하고 친구 목록 응답에 `online` 스냅샷을 싣는다. 오프라인은 10초 유예 재검사로 확정(취소 관리 없음). 클라(KMP·웹)는 기존 개인 큐 구독을 그대로 쓰므로 연결 코드 변경 0 — 이벤트 파싱 분기+스냅샷 패치+도트 UI만 추가한다.

**Tech Stack:** Spring Boot(Kotlin)+STOMP simple broker+MyBatis / KMP(Ktor STOMP, Compose, SwiftUI) / Next.js(@stomp/stompjs)

**Spec:** `docs/superpowers/specs/2026-08-13-friend-presence-design.md` (StoryGroup-Android 리포. 서버·웹 리포에는 없다 — 실행자는 반드시 함께 읽을 것)

## Global Constraints

- 리포 3개: 서버 `/mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-WebApp`(브랜치 develop), KMP `/mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android`(브랜치 feature/friend), 웹 `/mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/storygroup-web`.
- **커밋 정책**: 서버 리포만 develop에 직접 커밋한다(push는 사용자). **KMP·웹 리포는 절대 커밋하지 않는다** — 실수정 파일만 경로 지정 `git add`(스테이징)까지, 커밋 메시지는 채팅으로 전달. `git add -A` 금지.
- **CRLF**: 세 리포 모두 커밋본은 LF, 워킹트리는 CRLF로 보일 수 있다. 수정한 기존 파일은 스테이징 전 `sed -i 's/\r$//' <file>`로 LF 정규화(신규 파일은 Write 시 LF라 불필요). pbxproj 수정 없음(이번 차수 iOS 신규 파일 0).
- **DB 마이그레이션 0건** — 스키마·인덱스 추가 금지(스펙 결정).
- 상수: 오프라인 유예 `OFFLINE_GRACE_MS = 10_000L`, 판정 destination `"/user/queue/notifications"`(정확 일치), 이벤트 type 문자열 `"PRESENCE_CHANGED"`.
- 도트 색: `#34C759`(양 클라 공통), 테두리는 Compose `sg.paper` / iOS `colors.paper` / 웹 `var(--linen)`.
- KMP gradle은 Windows 경유: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat <tasks>"`. 서버 gradle도 같은 패턴(`cd /d ...\StoryGroup-WebApp&& gradlew.bat <tasks>`). `| tail` 등 파이프로 감싸려면 `set -o pipefail` 필수.
- 서버 배포: GCP 프로젝트 `application-bb416`, Cloud Run 서비스 `storygroup`(리전 `asia-northeast3`), gcloud CLI는 이미 인증됨. 서비스 URL `https://storygroup-k4cgcgz2ya-du.a.run.app`.

---

### Task 1: 서버 — UserPresenceTracker + PresenceSocketEvent + findFollowerIds (TDD)

**Files:**
- Create: `StoryGroup-WebApp/src/main/kotlin/kr/hhp227/groupsns_webapp/realtime/PresenceSocketEvent.kt`
- Create: `StoryGroup-WebApp/src/main/kotlin/kr/hhp227/groupsns_webapp/realtime/UserPresenceTracker.kt`
- Modify: `StoryGroup-WebApp/src/main/kotlin/kr/hhp227/groupsns_webapp/friend/UserFriendMapper.kt` (findFriends 아래에 메서드 1개 추가)
- Test: `StoryGroup-WebApp/src/test/kotlin/kr/hhp227/groupsns_webapp/realtime/UserPresenceTrackerTest.kt`

**Interfaces:**
- Consumes: `UserPrincipal(id: Long, name: String, email: String)`(security 패키지), `wsHeartbeatTaskScheduler` 빈(`ThreadPoolTaskScheduler`, WebSocketConfig.kt에 이미 있음 — `@Qualifier` 필수, 이름 바꾸면 기동 실패).
- Produces: `UserPresenceTracker.isOnline(userId: Long): Boolean`(Task 2가 사용), `UserFriendMapper.findFollowerIds(friendId: Long): List<Long>`, 개인 큐 페이로드 `PresenceSocketEvent(userId: Long, online: Boolean)`+`type="PRESENCE_CHANGED"`(Task 4·8이 파싱).

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/kotlin/kr/hhp227/groupsns_webapp/realtime/UserPresenceTrackerTest.kt` 전체:

```kotlin
package kr.hhp227.groupsns_webapp.realtime

import kr.hhp227.groupsns_webapp.friend.UserFriendMapper
import kr.hhp227.groupsns_webapp.security.UserPrincipal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import org.springframework.web.socket.messaging.SessionSubscribeEvent
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent
import java.time.Instant

// 리스너 메서드 직접 호출 + Mockito(스케줄러의 Runnable을 캡처해 동기 실행)로 유예 로직까지 검증.
// Kotlin에서 Mockito 매처는 Java 메서드(SimpMessagingTemplate/ThreadPoolTaskScheduler) 대상이라 null 반환이 무해하다.
class UserPresenceTrackerTest {
    private val messagingTemplate = Mockito.mock(SimpMessagingTemplate::class.java)
    private val userFriendMapper = Mockito.mock(UserFriendMapper::class.java)
    private val taskScheduler = Mockito.mock(ThreadPoolTaskScheduler::class.java)
    private val tracker = UserPresenceTracker(messagingTemplate, userFriendMapper, taskScheduler)

    @Test
    fun `구독하면 팔로워 전원에게 온라인을 한 번만 발행한다`() {
        Mockito.`when`(userFriendMapper.findFollowerIds(1L)).thenReturn(listOf(2L, 3L))

        tracker.onSubscribe(subscribeEvent("s1", "sub-0", userId = 1L))
        // 두 번째 기기(세션) — 이미 온라인이라 추가 발행 없음
        tracker.onSubscribe(subscribeEvent("s2", "sub-0", userId = 1L))

        val payload = ArgumentCaptor.forClass(Any::class.java)
        Mockito.verify(messagingTemplate)
            .convertAndSendToUser(Mockito.eq("2"), Mockito.eq("/queue/notifications"), payload.capture())
        Mockito.verify(messagingTemplate)
            .convertAndSendToUser(Mockito.eq("3"), Mockito.eq("/queue/notifications"), Mockito.any())
        val event = payload.value as PresenceSocketEvent
        assertEquals(1L, event.userId)
        assertTrue(event.online)
        assertEquals("PRESENCE_CHANGED", event.type)
        assertTrue(tracker.isOnline(1L))
    }

    @Test
    fun `마지막 세션이 끊기면 즉시가 아니라 유예 재검사 후에 오프라인을 발행한다`() {
        Mockito.`when`(userFriendMapper.findFollowerIds(1L)).thenReturn(listOf(2L))
        tracker.onSubscribe(subscribeEvent("s1", "sub-0", userId = 1L))
        Mockito.clearInvocations(messagingTemplate)

        tracker.onDisconnect(disconnectEvent("s1"))

        Mockito.verifyNoInteractions(messagingTemplate) // 즉시 발행 없음 — 유예 태스크만
        val runnable = ArgumentCaptor.forClass(Runnable::class.java)
        Mockito.verify(taskScheduler).schedule(runnable.capture(), Mockito.any(Instant::class.java))
        runnable.value.run() // 유예 재검사 — 여전히 세션 0

        val payload = ArgumentCaptor.forClass(Any::class.java)
        Mockito.verify(messagingTemplate)
            .convertAndSendToUser(Mockito.eq("2"), Mockito.eq("/queue/notifications"), payload.capture())
        assertFalse((payload.value as PresenceSocketEvent).online)
        assertFalse(tracker.isOnline(1L))
    }

    @Test
    fun `유예 안에 다시 접속하면 오프라인도 온라인도 발행하지 않는다`() {
        Mockito.`when`(userFriendMapper.findFollowerIds(1L)).thenReturn(listOf(2L))
        tracker.onSubscribe(subscribeEvent("s1", "sub-0", userId = 1L))
        Mockito.clearInvocations(messagingTemplate)

        tracker.onDisconnect(disconnectEvent("s1"))
        val runnable = ArgumentCaptor.forClass(Runnable::class.java)
        Mockito.verify(taskScheduler).schedule(runnable.capture(), Mockito.any(Instant::class.java))
        tracker.onSubscribe(subscribeEvent("s2", "sub-0", userId = 1L)) // 유예 내 재연결
        runnable.value.run() // 재검사 — 세션이 살아 있음

        Mockito.verifyNoInteractions(messagingTemplate) // 상태 연속(친구들은 끊김을 못 본다)
        assertTrue(tracker.isOnline(1L))
    }

    @Test
    fun `구독 해제도 마지막 세션이면 유예 후 오프라인을 발행한다`() {
        Mockito.`when`(userFriendMapper.findFollowerIds(1L)).thenReturn(listOf(2L))
        tracker.onSubscribe(subscribeEvent("s1", "sub-0", userId = 1L))
        Mockito.clearInvocations(messagingTemplate)

        tracker.onUnsubscribe(unsubscribeEvent("s1", "sub-0"))
        val runnable = ArgumentCaptor.forClass(Runnable::class.java)
        Mockito.verify(taskScheduler).schedule(runnable.capture(), Mockito.any(Instant::class.java))
        runnable.value.run()

        Mockito.verify(messagingTemplate)
            .convertAndSendToUser(Mockito.eq("2"), Mockito.eq("/queue/notifications"), Mockito.any())
    }

    @Test
    fun `개인 큐가 아닌 destination 구독은 무시한다`() {
        tracker.onSubscribe(subscribeEvent("s1", "sub-0", userId = 1L, destination = "/topic/chat-rooms/5"))

        Mockito.verifyNoInteractions(messagingTemplate)
        assertFalse(tracker.isOnline(1L))
    }

    private fun subscribeEvent(
        sessionId: String,
        subscriptionId: String,
        userId: Long,
        destination: String = "/user/queue/notifications"
    ): SessionSubscribeEvent {
        val accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE)
        accessor.sessionId = sessionId
        accessor.subscriptionId = subscriptionId
        accessor.destination = destination
        val user = UsernamePasswordAuthenticationToken(
            UserPrincipal(userId, "유저$userId", "u$userId@test.local"), null, emptyList()
        )
        return SessionSubscribeEvent(this, MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders), user)
    }

    private fun unsubscribeEvent(sessionId: String, subscriptionId: String): SessionUnsubscribeEvent {
        val accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE)
        accessor.sessionId = sessionId
        accessor.subscriptionId = subscriptionId
        return SessionUnsubscribeEvent(this, MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders))
    }

    private fun disconnectEvent(sessionId: String): SessionDisconnectEvent {
        val accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT)
        accessor.sessionId = sessionId
        return SessionDisconnectEvent(
            this, MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders), sessionId, CloseStatus.NORMAL
        )
    }
}
```

- [ ] **Step 2: 테스트가 컴파일 실패(클래스 없음)로 실패하는지 확인**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-WebApp&& gradlew.bat test --tests kr.hhp227.groupsns_webapp.realtime.UserPresenceTrackerTest"`
Expected: FAIL — `unresolved reference: UserPresenceTracker` / `findFollowerIds`

- [ ] **Step 3: 구현 — PresenceSocketEvent**

`src/main/kotlin/kr/hhp227/groupsns_webapp/realtime/PresenceSocketEvent.kt` 전체:

```kotlin
package kr.hhp227.groupsns_webapp.realtime

// 개인 큐(/user/queue/notifications)로 나가는 친구 프레즌스 전환 이벤트 —
// "이 유저를 친구로 등록한 사람들"에게만 발행된다(단방향 친구의 역방향 팬아웃).
// 전환 순간의 증분만 싣는다: 전체 상태 복구는 친구 목록 응답의 online 스냅샷이 담당.
data class PresenceSocketEvent(
    val userId: Long,
    val online: Boolean
) {
    val type: String = "PRESENCE_CHANGED"
}
```

- [ ] **Step 4: 구현 — UserFriendMapper.findFollowerIds**

`friend/UserFriendMapper.kt`의 `findFriends` 메서드 아래에 추가:

```kotlin
    // 역방향 조회 — "이 유저를 친구로 등록한 사람들"(프레즌스 팬아웃 대상).
    // friend_id 단독 인덱스는 없지만 소규모라 seq scan으로 충분(인덱스 추가 없음 — 마이그레이션 0건 유지).
    // 앱 DB 롤이 테이블 소유자라 RLS에 걸리지 않는다 — 세션 userId 설정 없이 리스너 스레드에서 호출된다.
    @Select("SELECT user_id FROM user_friends WHERE friend_id = #{friendId}")
    fun findFollowerIds(@Param("friendId") friendId: Long): List<Long>
```

- [ ] **Step 5: 구현 — UserPresenceTracker**

`src/main/kotlin/kr/hhp227/groupsns_webapp/realtime/UserPresenceTracker.kt` 전체:

```kotlin
package kr.hhp227.groupsns_webapp.realtime

import kr.hhp227.groupsns_webapp.friend.UserFriendMapper
import kr.hhp227.groupsns_webapp.security.UserPrincipal
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import org.springframework.web.socket.messaging.SessionSubscribeEvent
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent
import java.time.Instant

// 전역 프레즌스 — "개인 알림 큐 구독 중 = 온라인"으로 정의한다(RoomPresenceTracker의 전역판).
// KMP·웹 모두 이 큐를 전역 상시 구독하므로 클라 연결 변경 없이 성립한다.
// 인메모리 단일 인스턴스 전제(D2) — 다중 인스턴스로 가면 ChatEventBroadcaster 교체와 묶어 재설계.
// 서버 재시작이면 전원 오프라인에서 시작 — 클라 재구독이 다시 쌓고, 친구 목록 스냅샷이 복구 경로다.
@Component
class UserPresenceTracker(
    private val messagingTemplate: SimpMessagingTemplate,
    private val userFriendMapper: UserFriendMapper,
    // Spring 내장 messageBrokerTaskScheduler와 타입이 겹치므로 우리 빈을 이름으로 지정한다.
    @Qualifier("wsHeartbeatTaskScheduler") private val taskScheduler: ThreadPoolTaskScheduler
) {
    // sessionId -> 개인 큐 subscriptionId 집합: UNSUBSCRIBE 프레임엔 subscriptionId만 실려 와서 필요.
    private val sessionSubs = mutableMapOf<String, MutableSet<String>>()

    // sessionId -> userId: DISCONNECT 프레임엔 principal이 없을 수 있어 역추적용으로 보관.
    private val sessionUser = mutableMapOf<String, Long>()

    // userId -> sessionId 집합: 같은 유저가 기기 2개로 접속해도 세션 단위로 추적한다.
    private val userSessions = mutableMapOf<Long, MutableSet<String>>()

    // 마지막으로 발행한 온라인 유저 집합 — 실제 전환에만 발행한다(유예 내 재접속 시 재발행 억제).
    private val broadcastOnline = mutableSetOf<Long>()

    fun isOnline(userId: Long): Boolean = synchronized(this) { userSessions[userId]?.isNotEmpty() == true }

    @EventListener
    fun onSubscribe(event: SessionSubscribeEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val sessionId = accessor.sessionId ?: return
        val subscriptionId = accessor.subscriptionId ?: return
        if (accessor.destination != PRESENCE_DESTINATION) return
        val principal = (event.user as? UsernamePasswordAuthenticationToken)?.principal as? UserPrincipal ?: return
        val becameOnline = synchronized(this) {
            sessionSubs.getOrPut(sessionId) { mutableSetOf() }.add(subscriptionId)
            sessionUser[sessionId] = principal.id
            userSessions.getOrPut(principal.id) { mutableSetOf() }.add(sessionId)
            broadcastOnline.add(principal.id)
        }
        if (becameOnline) publish(principal.id, online = true)
    }

    @EventListener
    fun onUnsubscribe(event: SessionUnsubscribeEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val sessionId = accessor.sessionId ?: return
        val subscriptionId = accessor.subscriptionId ?: return
        val offlineCandidate = synchronized(this) {
            val subs = sessionSubs[sessionId] ?: return
            // 다른 destination(채팅방 토픽 등)의 구독 해제는 우리 집합에 없다 — 무시.
            if (!subs.remove(subscriptionId)) return
            // 같은 세션이 개인 큐를 중복 구독한 경우, 마지막 구독이 풀릴 때만 세션을 뺀다.
            if (subs.isNotEmpty()) return
            sessionSubs.remove(sessionId)
            val userId = sessionUser.remove(sessionId) ?: return
            removeSession(userId, sessionId)
        } ?: return
        scheduleOfflineCheck(offlineCandidate)
    }

    @EventListener
    fun onDisconnect(event: SessionDisconnectEvent) {
        // DISCONNECT 프레임과 전송 종료로 이벤트가 두 번 올 수 있다 — remove가 null이면 이미 처리된 것.
        val offlineCandidate = synchronized(this) {
            sessionSubs.remove(event.sessionId)
            val userId = sessionUser.remove(event.sessionId) ?: return
            removeSession(userId, event.sessionId)
        } ?: return
        scheduleOfflineCheck(offlineCandidate)
    }

    // userSessions에서 세션을 빼고, 그 유저의 마지막 세션이었으면 userId를 돌려준다(오프라인 후보).
    private fun removeSession(userId: Long, sessionId: String): Long? {
        val sessions = userSessions[userId] ?: return null
        sessions.remove(sessionId)
        if (sessions.isNotEmpty()) return null
        userSessions.remove(userId)
        return userId
    }

    // 마지막 세션이 사라져도 곧바로 오프라인을 알리지 않는다 — 클라 STOMP 재연결 주기(5초)의 2배를
    // 기다렸다가 재검사해, 네트워크 순단으로 온/오프라인이 튀는 것을 흡수한다(취소 관리 없이 재검사만).
    private fun scheduleOfflineCheck(userId: Long) {
        taskScheduler.schedule({
            val wentOffline = synchronized(this) {
                if (userSessions[userId]?.isNotEmpty() == true) false
                else broadcastOnline.remove(userId)
            }
            if (wentOffline) publish(userId, online = false)
        }, Instant.now().plusMillis(OFFLINE_GRACE_MS))
    }

    // 트랜잭션이 없는 휘발성 이벤트라 @TransactionalEventListener 경유 금지(조용히 버려짐) —
    // ChatEventBroadcaster.relay()처럼 직접 발행한다. 수신 대상은 "이 유저를 친구로 등록한 사람들".
    private fun publish(userId: Long, online: Boolean) {
        userFriendMapper.findFollowerIds(userId).forEach { followerId ->
            messagingTemplate.convertAndSendToUser(
                followerId.toString(), "/queue/notifications", PresenceSocketEvent(userId, online)
            )
        }
    }

    companion object {
        private const val OFFLINE_GRACE_MS = 10_000L
        private const val PRESENCE_DESTINATION = "/user/queue/notifications"
    }
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: Step 2와 같은 명령.
Expected: PASS (5 tests)

- [ ] **Step 7: 커밋 (서버 리포는 커밋 O)**

```bash
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-WebApp
git add src/main/kotlin/kr/hhp227/groupsns_webapp/realtime/PresenceSocketEvent.kt \
        src/main/kotlin/kr/hhp227/groupsns_webapp/realtime/UserPresenceTracker.kt \
        src/main/kotlin/kr/hhp227/groupsns_webapp/friend/UserFriendMapper.kt \
        src/test/kotlin/kr/hhp227/groupsns_webapp/realtime/UserPresenceTrackerTest.kt
git commit -m "전역 프레즌스 트래커 — 개인 알림 큐 구독=온라인, 친구 역방향 팬아웃(PRESENCE_CHANGED, 오프라인 10초 유예)"
```

(커밋 전 수정 파일 `UserFriendMapper.kt`만 `sed -i 's/\r$//'` 정규화 — 신규 3파일은 불필요)

---

### Task 2: 서버 — 친구 목록 online 스냅샷

**Files:**
- Modify: `StoryGroup-WebApp/src/main/kotlin/kr/hhp227/groupsns_webapp/friend/dto/FriendDtos.kt`
- Modify: `StoryGroup-WebApp/src/main/kotlin/kr/hhp227/groupsns_webapp/friend/FriendService.kt`

**Interfaces:**
- Consumes: `UserPresenceTracker.isOnline(userId: Long): Boolean` (Task 1)
- Produces: `GET /api/users/me/friends` 응답 항목에 `"online": Boolean` 추가 (Task 4·8이 소비)

- [ ] **Step 1: FriendResponse에 online 추가**

`friend/dto/FriendDtos.kt` 전체를 다음으로 교체:

```kotlin
package kr.hhp227.groupsns_webapp.friend.dto

import kr.hhp227.groupsns_webapp.friend.FriendRow
import java.time.OffsetDateTime

data class FriendResponse(
    val userId: Long,
    val name: String,
    val profileImg: String?,
    val statusMessage: String?,
    val friendedAt: OffsetDateTime,
    // 전역 프레즌스 스냅샷(인메모리 트래커 조회) — 실시간 전환은 개인 큐 PRESENCE_CHANGED가 증분으로 나간다
    val online: Boolean
) {
    companion object {
        fun from(row: FriendRow, online: Boolean) = FriendResponse(
            userId = row.userId,
            name = row.name,
            profileImg = row.profileImg,
            statusMessage = row.statusMessage,
            friendedAt = row.friendedAt,
            online = online
        )
    }
}
```

- [ ] **Step 2: FriendService에 트래커 주입**

`friend/FriendService.kt` — 생성자에 `private val userPresenceTracker: UserPresenceTracker` 추가(import `kr.hhp227.groupsns_webapp.realtime.UserPresenceTracker`), `listFriends`를 다음으로 교체:

```kotlin
    @Transactional
    fun listFriends(userId: Long): List<FriendResponse> {
        dbSessionMapper.setCurrentUserId(userId)
        return userFriendMapper.findFriends(userId)
            .map { FriendResponse.from(it, online = userPresenceTracker.isOnline(it.userId)) }
    }
```

- [ ] **Step 3: 전체 테스트+컴파일 확인**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-WebApp&& gradlew.bat test"`
Expected: BUILD SUCCESSFUL (기존 테스트 포함 전부 통과)

- [ ] **Step 4: 커밋**

```bash
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-WebApp
sed -i 's/\r$//' src/main/kotlin/kr/hhp227/groupsns_webapp/friend/dto/FriendDtos.kt \
                 src/main/kotlin/kr/hhp227/groupsns_webapp/friend/FriendService.kt
git add src/main/kotlin/kr/hhp227/groupsns_webapp/friend/dto/FriendDtos.kt \
        src/main/kotlin/kr/hhp227/groupsns_webapp/friend/FriendService.kt
git commit -m "친구 목록 응답에 online 스냅샷 추가(프레즌스 트래커 조회)"
```

---

### Task 3: 서버 — 배포 + 프레즌스 E2E

**Files:**
- Create: `<scratchpad>/presence-e2e/e2e.mjs` (스크래치패드 — 리포에 넣지 않는 검증 스크립트)

**Interfaces:**
- Consumes: Task 1·2가 배포된 서버. QA 계정 2개 — `/home/hong227/.claude/projects/-mnt-c-Users-hong2/memory/storygroup_kmp_403_refresh_trap.md`의 QA 계정 검증 절차를 먼저 읽고, 없으면 `POST /api/auth/register`로 신규 생성(`{name, email, password}`).
- Produces: 배포된 리비전 + E2E 통과 리포트.

- [ ] **Step 1: Cloud Run 배포**

```bash
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-WebApp
gcloud run deploy storygroup --source . --region asia-northeast3 --project application-bb416 --allow-unauthenticated
gcloud run services describe storygroup --region asia-northeast3 --project application-bb416 --format 'value(status.latestReadyRevisionName)'
```

Expected: 새 리비전 READY. (503/429가 나오면 결제 계정 이슈 선례 확인 — 회복 신호는 health 401)

- [ ] **Step 2: E2E 스크립트 작성**

스크래치패드에 `presence-e2e/` 디렉터리를 만들고 `npm init -y && npm install @stomp/stompjs ws` 후 `e2e.mjs` 작성:

```js
// 친구 프레즌스 E2E — A가 B를 친구로 등록한 상태에서: B 접속→A가 online 수신,
// B 종료→10초 유예 후 A가 offline 수신, 각 시점 스냅샷(GET friends) 대조.
// 실행: node e2e.mjs <BASE_URL> <A_EMAIL> <A_PW> <B_EMAIL> <B_PW>
import { Client } from "@stomp/stompjs";
import WebSocket from "ws";
Object.assign(globalThis, { WebSocket });

const [BASE, A_EMAIL, A_PW, B_EMAIL, B_PW] = process.argv.slice(2);
const WS_URL = BASE.replace(/^http/, "ws") + "/ws";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function login(email, password) {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw new Error(`login ${email}: ${res.status}`);
  const body = await res.json();
  // accessToken 필드명은 실제 응답으로 확인 — auth 응답이 {accessToken, refreshToken} 계약
  const token = body.accessToken;
  const userId = Number(JSON.parse(Buffer.from(token.split(".")[1], "base64url").toString()).sub);
  return { token, userId };
}

function connect(token, onEvent) {
  return new Promise((resolve, reject) => {
    const client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 0,
      onConnect: () => {
        client.subscribe("/user/queue/notifications", (f) => onEvent(JSON.parse(f.body)));
        resolve(client);
      },
      onStompError: (f) => reject(new Error(f.headers.message)),
    });
    client.activate();
  });
}

async function friendsSnapshot(token) {
  const res = await fetch(`${BASE}/api/users/me/friends`, { headers: { Authorization: `Bearer ${token}` } });
  return res.json();
}

const a = await login(A_EMAIL, A_PW);
const b = await login(B_EMAIL, B_PW);

// A가 B를 친구로 등록(이미면 409 — 무시)
await fetch(`${BASE}/api/users/${b.userId}/friend`, { method: "POST", headers: { Authorization: `Bearer ${a.token}` } });

const received = [];
const clientA = await connect(a.token, (e) => {
  if (e.type === "PRESENCE_CHANGED") { received.push({ ...e, at: Date.now() }); console.log("A 수신:", e); }
});

console.log("1) B 접속 →");
const clientB = await connect(b.token, () => {});
await sleep(3000);
if (!received.some((e) => e.userId === b.userId && e.online === true)) throw new Error("FAIL: online 이벤트 미수신");
const snap1 = await friendsSnapshot(a.token);
if (snap1.find((f) => f.userId === b.userId)?.online !== true) throw new Error("FAIL: 스냅샷 online=true 아님");
console.log("PASS: online 이벤트+스냅샷");

console.log("2) B 종료 → 유예 10초 대기");
const disconnectAt = Date.now();
await clientB.deactivate();
await sleep(7000);
if (received.some((e) => e.online === false)) throw new Error("FAIL: 유예(10초) 전에 offline 발행됨");
await sleep(8000); // 총 15초 대기
const offline = received.find((e) => e.userId === b.userId && e.online === false);
if (!offline) throw new Error("FAIL: offline 이벤트 미수신(15초)");
console.log(`PASS: offline 수신(+${((offline.at - disconnectAt) / 1000).toFixed(1)}s)`);
const snap2 = await friendsSnapshot(a.token);
if (snap2.find((f) => f.userId === b.userId)?.online !== false) throw new Error("FAIL: 스냅샷 online=false 아님");
console.log("PASS: 스냅샷 복원 — E2E 전체 통과");
await clientA.deactivate();
```

- [ ] **Step 3: E2E 실행**

Run: `node e2e.mjs https://storygroup-k4cgcgz2ya-du.a.run.app <A계정> <A비번> <B계정> <B비번>`
Expected: `PASS` 3회 + "E2E 전체 통과". 로그인 응답 필드명이 다르면 스크립트의 `body.accessToken`만 실제 계약에 맞춰 수정.

- [ ] **Step 4: 결과 보고**

배포 리비전명+E2E 출력(offline까지 걸린 초 포함)을 채팅으로 보고. push는 사용자 몫임을 명시.

---

### Task 4: KMP shared — PersonalEvent/Friend에 프레즌스 추가

**Files:**
- Modify: `StoryGroup-Android/shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/PersonalEvent.kt`
- Modify: `StoryGroup-Android/shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/Friend.kt`
- Modify: `StoryGroup-Android/shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/NotificationDtos.kt`
- Modify: `StoryGroup-Android/shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/FriendDtos.kt`
- Modify: `StoryGroup-Android/shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/NotificationRepositoryImpl.kt`
- Modify: `StoryGroup-Android/shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/FriendRepositoryImpl.kt`

**Interfaces:**
- Consumes: 서버 개인 큐 `{"type":"PRESENCE_CHANGED","userId":N,"online":bool}` (Task 1), 친구 목록 응답 `online` (Task 2)
- Produces: `PersonalEventType.PRESENCE_CHANGED` + `PersonalEvent.userId: Long?`/`PersonalEvent.online: Boolean`, `Friend.online: Boolean`(기본 false) — Task 5·6이 소비

- [ ] **Step 1: PersonalEvent 확장**

`PersonalEvent.kt` — enum에 `PRESENCE_CHANGED` 추가(`CALL_INVITE` 뒤):

```kotlin
enum class PersonalEventType {
    CONNECTED, DISCONNECTED,
    NOTIFICATION, CHAT_MESSAGE, CALL_INVITE, PRESENCE_CHANGED
}
```

data class 마지막 파라미터(`video`) 뒤에 추가:

```kotlin
    // PRESENCE_CHANGED 전용 — 전환한 친구의 id. 스냅샷 복구는 친구 목록 응답 online이 담당
    val userId: Long? = null,
    // PRESENCE_CHANGED 전용 — true=온라인 전환, false=오프라인 전환(서버 10초 유예 후 확정)
    val online: Boolean = false
```

- [ ] **Step 2: DTO+파싱 분기**

`NotificationDtos.kt`의 `PersonalSocketEventResponse` 마지막 필드(`video`) 뒤에 추가:

```kotlin
    // PRESENCE_CHANGED 전용
    val userId: Long? = null,
    val online: Boolean? = null
```

`NotificationRepositoryImpl.kt`의 `PersonalSocketEventResponse.toDomain()`에서 `"CALL_INVITE"` 분기 뒤, `else -> null` 앞에 추가:

```kotlin
    // 친구 프레즌스 전환(휘발) — 스냅샷은 친구 목록 응답의 online 필드가 담당
    "PRESENCE_CHANGED" -> userId
        ?.let { PersonalEvent(PersonalEventType.PRESENCE_CHANGED, userId = it, online = online ?: false) }
```

- [ ] **Step 3: Friend.online**

`domain/model/Friend.kt`의 `Friend`에 필드 추가(`friendedAt` 뒤):

```kotlin
    // 전역 프레즌스 스냅샷 — 구서버(필드 없음)는 false. 실시간 전환은 PersonalEvent PRESENCE_CHANGED
    val online: Boolean = false
```

`data/network/dto/FriendDtos.kt`의 `FriendResponse`에도 `val online: Boolean = false` 추가(`friendedAt` 뒤), `FriendRepositoryImpl.kt`의 `FriendResponse.toDomain()`에 `online = online` 추가.

- [ ] **Step 4: shared 컴파일 확인**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat :shared:compileKotlinJvm :shared:compileKotlinIosArm64"`
Expected: BUILD SUCCESSFUL. 커밋 없음(KMP는 스테이징 정책 — Task 7에서 일괄).

---

### Task 5: KMP Compose — FriendsViewModel 구독 + 온라인 도트

**Files:**
- Modify: `StoryGroup-Android/composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/friend/FriendsViewModel.kt`
- Modify: `StoryGroup-Android/composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/friend/FriendsScreen.kt`

**Interfaces:**
- Consumes: `PersonalEventType.PRESENCE_CHANGED`/`PersonalEvent.userId`/`PersonalEvent.online`, `Friend.online` (Task 4), `AppContainer.observePersonalEventsUseCase`(이미 존재 — DI 파일 무수정)
- Produces: 없음 (말단 UI)

- [ ] **Step 1: FriendsViewModel에 프레즌스 구독**

imports에 추가: `kr.hhp227.storygroup.shared.domain.model.PersonalEventType`, `kr.hhp227.storygroup.shared.domain.usecase.ObservePersonalEventsUseCase`.

생성자 마지막 파라미터로 추가: `private val observePersonalEventsUseCase: ObservePersonalEventsUseCase`.

`init` 블록(현재 `init { refresh() }`)을 다음으로 교체:

```kotlin
    init {
        refresh()
        // 친구 프레즌스 전환 실시간 반영 — 해당 친구의 online만 스냅샷 패치(재조회 없음, postUpdates 관용구).
        // 목록에 없는 userId(친구 아님)는 무시. 끊김 공백 복구는 친구 목록 재조회(스냅샷)가 담당한다.
        viewModelScope.launch {
            observePersonalEventsUseCase().collect { event ->
                val userId = event.userId

                if (event.type == PersonalEventType.PRESENCE_CHANGED && userId != null) {
                    _uiState.update {
                        it.copy(friends = it.friends.map { friend ->
                            if (friend.userId == userId) friend.copy(online = event.online) else friend
                        })
                    }
                }
            }
        }
    }
```

- [ ] **Step 2: sessionFriendsViewModel 배선**

`FriendsScreen.kt`의 `sessionFriendsViewModel` 헬퍼에 인자 추가:

```kotlin
        openDirectRoomUseCase = it.openDirectRoomUseCase,
        observePersonalEventsUseCase = it.observePersonalEventsUseCase
```

- [ ] **Step 3: FriendRow 아바타 도트**

`FriendsScreen.kt` imports에 추가: `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.ui.graphics.Color`.

`FriendRow`의 `SgAvatar(friend.name, imageUrl = friend.profileImg)` 한 줄을 다음으로 교체:

```kotlin
            Box {
                SgAvatar(friend.name, imageUrl = friend.profileImg)
                // 온라인 도트 — 친구 탭 전용이라 공용 SgAvatar는 건드리지 않는다(화면 로컬 오버레이)
                if (friend.online) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .background(sg.paper, CircleShape)
                            .padding(2.dp)
                            .background(Color(0xFF34C759), CircleShape)
                    )
                }
            }
```

- [ ] **Step 4: 컴파일 확인**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat :composeApp:compileKotlinJvm"`
Expected: BUILD SUCCESSFUL. 커밋 없음(Task 7에서 일괄 스테이징).

---

### Task 6: KMP iOS — FriendsViewModel 구독 + 온라인 도트

**Files:**
- Modify: `StoryGroup-Android/iosApp/iosApp/UI/Screens/Friend/FriendsViewModel.swift`
- Modify: `StoryGroup-Android/iosApp/iosApp/UI/Screens/Friend/FriendsView.swift`

**Interfaces:**
- Consumes: `PersonalEventType.presenceChanged`(Kotlin enum 브리징), `PersonalEvent.userId: KotlinLong?`/`online: Bool`, `Friend` 생성자(online 파라미터 추가됨 — Kotlin 기본 인자는 ObjC로 안 나가므로 **기존 생성자 호출부도 전부 online 명시 필요**), `KotlinFlowPublisher`+`eventsFlow()` 브리지(ChatViewModel.swift 관용구), `container.observePersonalEventsUseCase`(이미 존재)
- Produces: 없음 (말단 UI). ⚠️Swift는 Mac 없어 컴파일 미검증 — 관용구를 ChatViewModel.swift와 글자 단위로 맞출 것

- [ ] **Step 1: FriendsViewModel.swift 프레즌스 구독**

프로퍼티에 추가(`openDirectRoomUseCase` 아래):

```swift
    private var cancellables: Set<AnyCancellable> = []
```

`init` — 파라미터 `observePersonalEventsUseCase: ObservePersonalEventsUseCase`를 마지막에 추가하고, 본문 `refresh()` 호출 앞에 구독 추가(저장 프로퍼티는 불필요 — 구독만 하고 버린다, ChatViewModel 선례):

```swift
        KotlinFlowPublisher<PersonalEvent> { onEach in
            observePersonalEventsUseCase.eventsFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] in self?.handlePresence($0) }
        .store(in: &cancellables)
        refresh()
```

메서드 추가(`openDm` 아래):

```swift
    /// 친구 프레즌스 전환 실시간 반영 — 해당 친구의 online만 스냅샷 패치(재조회 없음).
    /// Kotlin data class copy()는 ObjC로 안 나가 생성자로 재조립한다.
    private func handlePresence(_ event: PersonalEvent) {
        guard event.type == .presenceChanged, let userId = event.userId?.int64Value else { return }
        uiState.friends = uiState.friends.map { friend in
            guard friend.userId == userId else { return friend }
            return Friend(
                userId: friend.userId,
                name: friend.name,
                profileImg: friend.profileImg,
                statusMessage: friend.statusMessage,
                friendedAt: friend.friendedAt,
                online: event.online
            )
        }
    }
```

`addFriend`의 낙관적 갱신 `Friend(...)` 생성자 호출에 `online: false` 추가(마지막 인자 — Kotlin 기본 인자 미노출이라 명시 필수):

```swift
                let added = Friend(
                    userId: user.id,
                    name: user.name,
                    profileImg: user.profileImg,
                    statusMessage: user.statusMessage,
                    friendedAt: "",
                    online: false
                )
```

- [ ] **Step 2: FriendsView.swift 배선+도트**

`FriendsView.init`의 VM 생성에 인자 추가:

```swift
            openDirectRoomUseCase: container.openDirectRoomUseCase,
            observePersonalEventsUseCase: container.observePersonalEventsUseCase
```

`FriendRow`의 `SGAvatar(name: friend.name, imageUrl: friend.profileImg)` 한 줄을 다음으로 교체:

```swift
                ZStack(alignment: .bottomTrailing) {
                    SGAvatar(name: friend.name, imageUrl: friend.profileImg)
                    // 온라인 도트 — 친구 탭 전용이라 공용 SGAvatar는 건드리지 않는다(Compose FriendRow 미러)
                    if friend.online {
                        Circle()
                            .fill(Color(red: 52 / 255, green: 199 / 255, blue: 89 / 255))
                            .frame(width: 10, height: 10)
                            .overlay(Circle().stroke(colors.paper, lineWidth: 2))
                    }
                }
```

- [ ] **Step 3: iOS klib 컴파일 확인 (Swift는 검증 불가)**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat :shared:compileKotlinIosArm64"`
Expected: BUILD SUCCESSFUL (Task 4에서 통과했으면 재확인 성격). Swift 컴파일은 Mac 미보유로 불가 — 보고에 ⚠️Swift/Mac 미검증 명시.

---

### Task 7: KMP — 3타깃 최종 검증+CRLF 정규화+스테이징

**Files:**
- Modify 없음(검증·스테이징만). 대상 파일 8개: Task 4의 shared 6개+Task 5의 composeApp 2개+Task 6의 iosApp 2개 = 10개.

**Interfaces:**
- Consumes: Task 4~6의 모든 변경.
- Produces: 스테이징 완료된 KMP 워킹트리+커밋 메시지(커밋은 사용자).

- [ ] **Step 1: 3타깃 전체 검증**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat :composeApp:compileKotlinJvm :composeApp:assembleDebug :shared:compileKotlinIosArm64"`
Expected: BUILD SUCCESSFUL. (`local.properties`가 없으면 `sdk.dir=C\:\\Users\\hong2\\AppData\\Local\\Android\\Sdk`로 생성 — gitignore 대상)

- [ ] **Step 2: CRLF 정규화+경로 스테이징**

```bash
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android
sed -i 's/\r$//' \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/PersonalEvent.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/Friend.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/NotificationDtos.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/FriendDtos.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/NotificationRepositoryImpl.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/FriendRepositoryImpl.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/friend/FriendsViewModel.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/friend/FriendsScreen.kt \
  iosApp/iosApp/UI/Screens/Friend/FriendsViewModel.swift \
  iosApp/iosApp/UI/Screens/Friend/FriendsView.swift
git add \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/PersonalEvent.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/Friend.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/NotificationDtos.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/FriendDtos.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/NotificationRepositoryImpl.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/FriendRepositoryImpl.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/friend/FriendsViewModel.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/friend/FriendsScreen.kt \
  iosApp/iosApp/UI/Screens/Friend/FriendsViewModel.swift \
  iosApp/iosApp/UI/Screens/Friend/FriendsView.swift
git diff --cached --stat
```

Expected: 10파일, 실변경만(파일 전체 diff로 뜨는 파일이 있으면 정규화 누락 — 다시 sed).

- [ ] **Step 3: 커밋 메시지 채팅 전달 (커밋 금지)**

```
친구 탭 프레즌스(온라인 도트) — 개인 큐 PRESENCE_CHANGED 소비(클라 연결 변경 0)
    - shared: PersonalEvent PRESENCE_CHANGED 분기+Friend.online(구서버 기본 false)
    - 양 플랫폼 FriendsViewModel이 개인 큐 이벤트로 해당 친구 online만 스냅샷 패치(재조회 없음)
    - FriendRow 아바타 온라인 도트(#34C759, 공용 아바타 컴포넌트 무수정)
```

---

### Task 8: 웹 — 타입+헤더 가드 픽스+친구 카드 도트

**Files:**
- Modify: `storygroup-web/src/lib/ws.ts`
- Modify: `storygroup-web/src/lib/api.ts` (Friend 인터페이스)
- Modify: `storygroup-web/src/components/app-header.tsx`
- Modify: `storygroup-web/src/app/search/page.tsx`

**Interfaces:**
- Consumes: 서버 `PRESENCE_CHANGED` 이벤트+`online` 스냅샷(Task 1·2), `useNotificationSocket(token, onConnect, onEvent)` 훅(기존).
- Produces: 없음 (말단 UI).

- [ ] **Step 1: ws.ts 타입 확장**

`CallInviteEvent` 인터페이스 뒤, `NotificationQueueEvent` 앞에 추가:

```ts
// 개인 큐 채팅 뱃지 — 방 토픽을 구독하지 않은 수신자에게 "어느 방에 새 메시지" 사실만 싣는다.
// (서버는 2026-07-29부터 이 큐로 보내고 있었으나 union에 없었다 — 계약을 현실과 맞춘다)
export interface ChatBadgeEvent {
  type: "CHAT_MESSAGE";
  chatRoomId: number;
  messageId: number;
  senderId: number;
}

// 친구 프레즌스 전환 — "이 유저를 친구로 등록한 사람"에게만 온다. 스냅샷은 친구 목록 API의 online 필드.
export interface PresenceChangedEvent {
  type: "PRESENCE_CHANGED";
  userId: number;
  online: boolean;
}
```

`NotificationQueueEvent`를 다음으로 교체:

```ts
export type NotificationQueueEvent = NotificationSocketEvent | CallInviteEvent | ChatBadgeEvent | PresenceChangedEvent;
```

- [ ] **Step 2: api.ts Friend에 online**

`Friend` 인터페이스(`friendedAt` 아래)에 추가:

```ts
  // 전역 프레즌스 스냅샷 — 구서버(필드 없음) 방어로 옵셔널. 실시간 전환은 PRESENCE_CHANGED 이벤트
  online?: boolean;
```

- [ ] **Step 3: app-header 가드 (잠복 버그 픽스)**

`useNotificationSocket` 핸들러에서 `if (event.type === "NOTIFICATION") {...}` 블록 바로 뒤, `// CALL_INVITE —` 주석 앞에 추가:

```tsx
    // CHAT_MESSAGE(2026-07-29부터 이 큐로 옴)·PRESENCE_CHANGED가 통화 배너로 오인되지 않게 명시 가드
    if (event.type !== "CALL_INVITE") return;
```

- [ ] **Step 4: 검색 페이지 실시간 패치+도트**

`search/page.tsx` — import에 `useNotificationSocket` 추가(`@/hooks/use-notification-socket`).

기존 `useEffect(() => { ... listFriends ... }, [accessToken]);` 바로 아래(⚠️`if (!isReady)` 조기 return보다 위 — 훅은 무조건 호출돼야 한다)에 추가:

```tsx
  // 친구 프레즌스 실시간 반영 — 해당 친구의 online만 패치. (재)연결 시엔 끊긴 사이 전환을
  // 놓쳤을 수 있어 스냅샷을 다시 읽는다(헤더 refetchUnread와 같은 복구 관용구).
  useNotificationSocket(
    accessToken,
    () => {
      if (accessToken) listFriends(accessToken).then(setFriends).catch(() => {});
    },
    (event) => {
      if (event.type !== "PRESENCE_CHANGED") return;
      setFriends((prev) => prev?.map((f) => (f.userId === event.userId ? { ...f, online: event.online } : f)) ?? prev);
    }
  );
```

친구 카드의 `<div className="avatar sm">{friend.name.slice(0, 1)}</div>` 한 줄을 다음으로 교체:

```tsx
              <div style={{ position: "relative" }}>
                <div className="avatar sm">{friend.name.slice(0, 1)}</div>
                {friend.online && (
                  <span
                    style={{ position: "absolute", bottom: 0, right: 0, width: 10, height: 10, borderRadius: "50%", background: "#34c759", border: "2px solid var(--linen)" }}
                  />
                )}
              </div>
```

- [ ] **Step 5: 빌드 검증**

⚠️dev 서버가 켜져 있으면 `next build` 금지(dev/prod 산출물 섞여 500 — WSL 캐시 함정 선례). 먼저 확인:

```bash
pgrep -f "next dev" && echo "dev 서버 켜짐 — 죽이거나 빌드 생략하고 타입체크만" || true
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/storygroup-web
npx tsc --noEmit
```

Expected: 타입 에러 0. (dev 서버가 꺼져 있으면 `npm run build`까지 — 통과 후 `.next` 상태는 건드리지 않는다)

- [ ] **Step 6: CRLF 정규화+스테이징+커밋 메시지 전달 (커밋 금지)**

```bash
cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/storygroup-web
sed -i 's/\r$//' src/lib/ws.ts src/lib/api.ts src/components/app-header.tsx src/app/search/page.tsx
git add src/lib/ws.ts src/lib/api.ts src/components/app-header.tsx src/app/search/page.tsx
git diff --cached --stat
```

커밋 메시지(채팅 전달):

```
친구 목록 프레즌스 도트 + 알림 큐 가드 픽스
    - /search 친구 카드에 온라인 도트(PRESENCE_CHANGED 실시간 패치+재연결 스냅샷 복구)
    - app-header: 비-CALL_INVITE를 통화 배너로 오인하던 잠복 버그 가드(CHAT_MESSAGE 포함)
    - ws.ts union에 실재하는 CHAT_MESSAGE·PRESENCE_CHANGED 타입 추가
```

---

## Self-Review 결과

- 스펙 커버리지: 서버 트래커/유예/팬아웃/스냅샷=Task 1·2, 테스트 4케이스=Task 1(5케이스로 확장), 배포+E2E=Task 3, shared=Task 4, KMP 클라=Task 5·6, 웹(가드 픽스 포함)=Task 8, 순서·커밋 정책=Global Constraints — 누락 없음.
- 타입 일관성: `findFollowerIds(friendId)`·`isOnline(userId)`·`PresenceSocketEvent(userId, online)`·`PersonalEventType.PRESENCE_CHANGED`·`Friend.online` 전 태스크 동일. iOS `Friend` 생성자 online 인자는 Task 4의 Kotlin 필드 순서(마지막)와 일치.
- 남는 위험: Swift 컴파일 미검증(관례), E2E 로그인 응답 필드명(스크립트 내 주석으로 가드), Cloud Run 배포권한(기인증 gcloud 전제).
