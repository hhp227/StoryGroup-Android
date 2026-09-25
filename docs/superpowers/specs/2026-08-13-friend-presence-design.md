# 친구 프레즌스(온라인 표시) 설계 (2026-08-13)

## 배경·문제

친구 탭(2026-08-13 완성, KMP)과 웹 친구 목록(/search, V20)은 친구가 지금 접속해 있는지
알 길이 없다. 서버에는 전역 온라인 상태 코드가 0줄이고, 통화 벨울림(CALL_INVITE)은
오프라인 상대에게도 발사된다(phase7 문서는 "상대가 온라인이면 벨울림"을 전제로 썼지만
판정 로직이 없다). 반면 프레즌스에 필요한 조각은 전부 검증돼 있다:

- 같은 골격의 인메모리 트래커가 이미 2개 — `RoomPresenceTracker`(채팅방 열람자),
  `RtcRoomTracker`(통화 로스터). 3종 리스너(SUBSCRIBE/UNSUBSCRIBE/DISCONNECT)+
  `synchronized` 맵+지연 방송 패턴.
- STOMP 세션 Principal이 userId(`StompUserToken.getName()`) — 개인 큐 라우팅 준비 완료.
- 개인 큐 `/user/queue/notifications` 봉투에 type 분기 3종(CHAT_MESSAGE/NOTIFICATION/
  CALL_INVITE)이 이미 흐르고, KMP(PersonalEvent 스트림)·웹(useNotificationSocket) 모두
  이 큐를 전역 상시 구독 중이다.
- phase6 설계 문서가 "프레즌스는 인스턴스 1개 전제(D2)라 인메모리 가능"이라고 예고.

**목표**: "개인 알림 큐 구독 중=온라인"으로 정의한 전역 프레즌스를 서버에 얹고,
KMP 친구 탭과 웹 친구 목록의 아바타에 온라인 도트를 단다.

## 사용자 결정 사항

1. **클라 범위**: KMP 친구 탭+웹 /search 친구 카드 동시(3개 리포).
2. **표시 수준**: 온라인 도트만(순수 인메모리, DB 마이그레이션 0건). last_seen("N분 전")은
   나중에 필요해지면 별도 차수. 서버 재시작 시 전원 오프라인으로 보이는 건 감수
   (스냅샷 재조회로 복구).
3. **접근**: A안 — 구독 기반 트래커+개인 큐 PRESENCE_CHANGED 푸시+FriendResponse.online
   스냅샷+오프라인 10초 유예.

## 서버 설계 (StoryGroup-WebApp, develop)

### UserPresenceTracker (신규, realtime/)

기존 두 트래커의 세 번째 형제. 판정 대상 destination은
`/user/queue/notifications` 하나(클라이언트가 보낸 원본 문자열과 정확히 일치 비교).

자료구조(전부 `synchronized(this)`):

| 맵 | 용도 |
|---|---|
| `sessionSubs: Map<sessionId, MutableSet<subscriptionId>>` | UNSUBSCRIBE 프레임엔 subscriptionId만 오므로 역추적용 |
| `sessionUser: Map<sessionId, Long>` | DISCONNECT 시 userId 역추적 |
| `userSessions: Map<Long, MutableSet<sessionId>>` | 온라인 판정(비어 있지 않으면 온라인) — 멀티 기기/탭 대응 |
| `broadcastOnline: MutableSet<Long>` | 마지막으로 발행한 상태 — 실제 전환에만 발행(중복 발행 억제) |

전환 규칙:

- **온라인**: 구독으로 `userSessions[userId]`가 0→1이 되고 `broadcastOnline`에 없으면
  추가+온라인 발행(즉시).
- **오프라인**: UNSUBSCRIBE/DISCONNECT로 마지막 세션이 사라지면 즉시 발행하지 않고
  `wsHeartbeatTaskScheduler`(기존 빈 재사용)에 **10초 지연 재검사 태스크**를 건다.
  실행 시점에 여전히 세션 0이고 `broadcastOnline`에 있으면 제거+오프라인 발행.
  취소 관리 없음 — 유예 중 재접속하면 재검사가 그냥 빈손으로 끝난다.
  10초는 클라 STOMP 재연결 주기(5초)의 2배로 네트워크 순단 플랩을 흡수한다.
- DISCONNECT 중복 수신 방어: `sessionUser.remove(sessionId) ?: return`
  (RoomPresenceTracker 선례).

### 발행 — PresenceSocketEvent (신규, realtime/)

```json
{ "userId": 7, "online": true, "type": "PRESENCE_CHANGED" }
```

- 수신 대상: "이 유저를 친구로 등록한 사람들" — `UserFriendMapper`에 역방향 조회 추가
  `findFollowerIds(friendId: Long): List<Long>`
  (`SELECT user_id FROM user_friends WHERE friend_id = #{friendId}`).
  `friend_id` 단독 인덱스는 없지만 소규모 서비스 seq scan으로 충분 — 인덱스 추가는
  하지 않는다(마이그레이션 0건 유지).
- 발행 방식: 트래커가 `SimpMessagingTemplate.convertAndSendToUser(followerId.toString(),
  "/queue/notifications", event)` **직접 호출**. 트랜잭션이 없는 휘발성 이벤트라
  `@TransactionalEventListener` 경유 금지(조용히 버려짐 — ChatEventBroadcaster.relay() 선례).
- destination이 기존 개인 큐이므로 `StompAuthChannelInterceptor` 화이트리스트 변경 불필요.
- 팬아웃 시점의 DB 조회는 리스너 스레드에서 1회 — 접속/해제 전환에만 발생하고
  유예·중복 억제로 빈도가 낮다.

### 스냅샷 — FriendResponse.online

- `FriendResponse`에 `online: Boolean` 추가(기본 false).
- `FriendService.listFriends()`가 매퍼 결과에 `tracker.isOnline(userId)`를 채워 반환.
  트래커는 읽기 전용 `isOnline(userId): Boolean` 공개 메서드 제공.
- 이 스냅샷이 클라의 "서버 재시작·이벤트 유실 복구" 경로다 — 친구 목록을 다시 읽을
  때마다 전체 상태가 맞춰진다.

### 서버 테스트

`UserPresenceTrackerTest`(단위, 리스너 메서드 직접 호출+가짜 스케줄러/템플릿):
1. 구독 → 온라인 1회 발행, 같은 유저 두 번째 세션 구독 → 추가 발행 없음.
2. 마지막 세션 DISCONNECT → 즉시 발행 없음, 유예 재검사 후 오프라인 1회 발행.
3. 유예 내 재구독 → 오프라인·온라인 어느 쪽도 발행 없음(상태 연속).
4. 팬아웃 대상이 followerIds 전원인지.

## KMP shared 설계

- `PersonalEvent`(sealed)에 `PresenceChanged(userId: Long, online: Boolean)` 추가.
  `NotificationRepositoryImpl`의 개인 큐 JSON 파싱에 `"PRESENCE_CHANGED"` 분기 1개 추가.
- `Friend.online: Boolean = false` 필드 추가+`FriendResponse` DTO에 `online: Boolean = false`
  (구서버 호환 기본값 — 서버 배포 전 클라가 먼저 나가도 안전).
- 유스케이스 신설 0 — 기존 `ObservePersonalEventsUseCase` 재사용. 소켓도 기존
  shareIn 1개 그대로(구독 추가 없음).

## KMP 클라 설계 (composeApp + iosApp, 1:1 미러)

- `FriendsViewModel` 양쪽: 생성자에 `observePersonalEventsUseCase` 추가(호출부
  sessionFriendsViewModel/AppContainer.swift 전달). init에서 구독해
  `PresenceChanged(userId, online)` 수신 시 `friends` 목록에서 해당 userId의 `online`만
  교체(스냅샷 패치 — 재조회 없음, postUpdates 관용구). 목록에 없는 userId는 무시.
  검색 결과에는 반영하지 않는다(범위 외).
- UI: `FriendRow` 아바타에 온라인 도트 오버레이 — 초록 원 10(dp/pt/px)+linen(카드 배경)색
  테두리 2(dp/pt/px), 아바타 우하단, 외곽 지오메트리는 14(dp/pt/px)로 통일(카드 배경이
  paper가 아니라 linen이라 테두리도 linen이어야 카드색과 일치). 공용 `SgAvatar`/`SGAvatar`는
  무수정(화면 로컬 오버레이 — Compose는 Box, iOS는 ZStack bottomTrailing).
- 세션 스코프 VM이라 구독 수명=로그인 세션. 탭 재진입 시 재조회는 기존 동작 그대로
  (스냅샷이 최신화 경로).

## 웹 설계 (storygroup-web)

- `lib/api.ts` `Friend`에 `online?: boolean`(옵셔널 — 구서버 방어, videos 선례).
- `lib/ws.ts` `NotificationQueueEvent`에 `"PRESENCE_CHANGED"` type+`userId`/`online` 필드
  추가(기존 union 확장).
- `/search` 페이지: 마운트 시 `listFriends` 스냅샷은 기존 그대로. 페이지에서
  `useNotificationSocket`을 추가 마운트(페이지별 소켓 연결이 웹 관례 — 헤더 것과 별개
  연결이지만 서버 트래커는 세션 단위 집계라 정확성 무관)해 `PRESENCE_CHANGED` 수신 시
  friends 상태의 해당 유저 `online`만 패치. 친구 카드 아바타(첫 글자 원형)에 온라인
  도트 오버레이. 검색 결과·공개 프로필은 범위 외.
- **app-header 가드 수정(기존 잠복 버그 픽스 포함)**: 현재 핸들러는 "NOTIFICATION이
  아니면 전부 CALL_INVITE"로 처리해서, 같은 큐로 이미 흐르는 CHAT_MESSAGE(2026-07-29
  추가)와 이번 PRESENCE_CHANGED가 가짜 통화 배너를 띄운다. NOTIFICATION 분기 뒤에
  `if (event.type !== "CALL_INVITE") return;` 가드 1줄 추가. `NotificationQueueEvent`
  union에도 실재하는 CHAT_MESSAGE·PRESENCE_CHANGED 타입을 추가해 계약을 현실과 맞춘다.

## 범위 제외 (이번 차수 아님)

- last_seen("N분 전 접속") — DB 컬럼+RLS 필요, 별도 차수.
- CALL_INVITE 오프라인 억제·채팅방/멤버 목록·공개 프로필·검색 결과의 온라인 표시.
- 다중 인스턴스 대응(D2 교체 시점과 묶음 — phase6 문서의 LISTEN/NOTIFY 계획).

## 에러·엣지 케이스

- 서버 재시작: 전원 오프라인 시작 — 클라 재접속(구독)이 다시 온라인을 쌓고, 친구
  목록 재조회가 스냅샷을 맞춘다. 유실 이벤트 복구 경로도 동일(스냅샷 우선).
- 친구 등록 직후: 상대의 현재 상태는 다음 목록 조회 때 반영(등록 시점 낙관적 갱신은
  online=false 기본 — 웹/KMP 동일). 실시간 이벤트는 등록 이후 전환부터 수신.
- 차단 관계: 친구 목록에 남아 있는 한 프레즌스도 흐른다(기존 친구 목록이 차단을
  거르지 않는 동작과 일관 — 서버 필터 추가 시 함께 손본다).
- 유예 중 서버 종료: 오프라인 발행 유실 — 클라 스냅샷 재조회로 복구(위와 동일).

## 순서·검증·커밋

1. **서버**: 트래커+이벤트+DTO+매퍼+테스트 → 단위 테스트 통과+Cloud Build 컴파일 검증
   → develop 커밋(푸시는 사용자) → Cloud Run 배포 → QA 계정 2개로 접속/해제 E2E
   (온라인 발행·유예 오프라인·스냅샷 확인).
2. **KMP**: shared+양 플랫폼 → 3타깃 컴파일 검증 → 실수정 파일만 경로 스테이징
   (CRLF 정규화, 커밋은 사용자).
3. **웹**: 타입+검색 페이지 → 빌드 검증 → 스테이징(커밋은 사용자).

클라를 먼저 내보내는 건 `online` 기본값 false로 안전하다. 서버 먼저는 KMP엔 안전하지만
(모르는 type은 항목째 제외 — `toDomain()` else null), **구버전 웹 헤더는 비-NOTIFICATION을
전부 통화 배너로 처리하는 잠복 버그가 있어 PRESENCE_CHANGED를 가짜 배너로 오인한다** —
웹 가드 수정을 서버 배포와 같은 차수에 반영한다(웹은 dev 터널 운용이라 실위험은 낮음).
