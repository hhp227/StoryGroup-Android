# 그룹 상세: 일정·설정 탭 실구현 + 5탭 ViewModel 분리 설계

- 날짜: 2026-08-12
- 브랜치: feature/tablayout (탭바 투명 배경 커밋 이후)
- 결정자: 사용자 확정 — ① 일정 탭=웹 캘린더 풀 미러, ② 설정 탭=웹 미러+그룹 나가기,
  ③ 레거시(탭 Fragment마다 ViewModel)처럼 기존 소식/앨범/멤버 탭도 전용 ViewModel로 분리

## 1. 배경과 목표

그룹 상세 5탭(소식/앨범/일정/멤버/설정) 중 일정·설정은 "준비 중입니다" 빈 화면이다.
백엔드에는 일정(events CRUD+RSVP)과 그룹 설정(PATCH/DELETE/leave) API가 전부 배포돼
있고 웹 UI(/groups/[id]/events, /groups/[id]/settings)도 있다 — **서버 수정 0**으로
두 탭을 웹 미러로 채운다. 동시에 현재 GroupDetailViewModel 하나(유스케이스 16개)에
몰린 탭 상태를 레거시 구조(PostFragment/AlbumFragment/MemberFragment/SettingsFragment가
각자 ViewModel 소유)처럼 **탭별 전용 ViewModel 5개 + 화면 ViewModel 1개**로 재편한다.

## 2. 서버 계약 (기배포 — 수정 없음)

### 일정 (EventController, V19·리비전 00066부터 서빙)

| 메서드 | 경로 | 비고 |
|---|---|---|
| GET | `/api/groups/{gid}/events?from=&to=` | ISO OffsetDateTime, starts_at 기준 [from, to) |
| POST | `/api/groups/{gid}/events` | 멤버 누구나. 작성자 자동 GOING |
| GET | `/api/groups/{gid}/events/{eid}` | EventDetailResponse = {event, attendees} |
| DELETE | `/api/groups/{gid}/events/{eid}` | 작성자/방장/부방장 |
| PUT | `/api/groups/{gid}/events/{eid}/rsvp` | body {status}, upsert. 응답=집계 갱신된 EventResponse |
| DELETE | `/api/groups/{gid}/events/{eid}/rsvp` | 응답 취소. 응답=EventResponse |

- `CreateEventRequest`: title(필수, ≤100) / description?(≤2000) / location?(≤200) /
  startsAt(ISO) / endsAt?(null=종료 시각 없는 일정)
- `EventResponse`: id, groupId, userId, authorName, authorProfileImg?, title, description?,
  location?, startsAt, endsAt?, createdAt, goingCount, maybeCount, notGoingCount,
  myRsvp?(null=미응답). `RsvpStatus` = GOING | MAYBE | NOT_GOING
- `EventAttendeeResponse`: userId, name, profileImg?, status
- 미사용: PATCH 수정(웹에도 UI 없음 — 동일하게 제외), GET /upcoming(사이드바용)

### 그룹 설정 (GroupController)

| 메서드 | 경로 | 권한 |
|---|---|---|
| PATCH | `/api/groups/{gid}` | OWNER 전용 |
| DELETE | `/api/groups/{gid}` | OWNER 전용(라운지 불가) |
| POST | `/api/groups/{gid}/leave` | 멤버/부방장(OWNER·라운지는 서버가 거부) |

- ⚠️ `UpdateGroupRequest`는 **name/description/image 전체 교체 계약**(null 전송=null로
  덮어씀 — PATCH /users/me와 같은 함정). 폼이 로드해 온 기존 값을 항상 실어 보낸다.
  joinType만 null=기존 유지 시맨틱. 웹 폼도 4필드 전부 전송한다.

## 3. shared 신규

### 일정
- `data/network/dto/EventDtos.kt` — EventResponse/EventDetailResponse/EventAttendeeResponse/
  CreateEventRequest/RsvpRequest (서버 1:1, 시각은 ISO String)
- `domain/model/GroupEvent.kt` — GroupEvent(카운트·myRsvp 포함, 시각은 관례대로 ISO 원문 —
  포맷팅·로컬 날짜 귀속은 플랫폼 UI), RsvpStatus, EventAttendee, GroupEventDetail
- `EventRepository`(+Impl): listEvents(groupId, fromIso, toIso) / getEvent / createEvent /
  deleteEvent / rsvp(status) / cancelRsvp
- UseCase 6종(@Throws suspend): `GetGroupEventsUseCase` `GetEventDetailUseCase`
  `CreateEventUseCase` `DeleteEventUseCase` `RsvpEventUseCase` `CancelEventRsvpUseCase`
- 페이징 아님(월 단위 List) → iosMain 브리지 파일 불필요

### 설정
- `GroupRepository`에 3메서드 추가: updateGroup(groupId, name, description?, image?,
  joinType) / deleteGroup(groupId) / leaveGroup(groupId)
- UseCase 3종: `UpdateGroupUseCase` `DeleteGroupUseCase` `LeaveGroupUseCase`
- 대표 이미지 업로드는 기존 `UploadImageUseCase`(POST /api/images) 재사용

## 4. ViewModel 분리 (레거시 탭별 VM 미러)

현 GroupDetailViewModel(유스케이스 16개)을 화면 1 + 탭 5로 재편. Compose·iOS 모두
같은 경계. MVI 3요소(UiState/Action/Event, 이벤트 없으면 Nothing/Never) 관례 유지.

| VM | 책임 | 주입 UseCase |
|---|---|---|
| GroupDetailViewModel | 그룹 단건(커버·제목·역할)+defaultChatRoomId(상단바 채팅)+로드 에러 | GetGroup, GetGroupDefaultChatRoom |
| GroupFeedViewModel | 피드 PagingData+스냅샷 패치 3종(수정/차단/삭제 옵저버)+좋아요 토글 | GetGroupPostsPagingData, ObservePostUpdates, ObserveUserBlocks, ObservePostDeletions, TogglePostLike |
| GroupAlbumViewModel | 앨범 PagingData | GetGroupPhotosPagingData |
| GroupMembersViewModel | 멤버 목록+차단 필터+가입 신청 인박스(승인/거절)+초대코드+DM 열기+myUserId | GetGroupMembers, GetJoinRequests, ApproveJoinRequest, RejectJoinRequest, CreateGroupInvite, OpenDirectRoom, GetBlockedUsers, GetCurrentUserId |
| GroupEventsViewModel (신규) | 월 앵커·이벤트 목록·선택일·참석자 펼침 캐시·생성/삭제/RSVP+myUserId(작성자 삭제 판정) | GetGroupEvents, GetEventDetail, CreateEvent, DeleteEvent, RsvpEvent, CancelEventRsvp, GetCurrentUserId |
| GroupSettingsViewModel (신규) | 그룹 self-load(웹 설정 페이지 미러)+폼 상태+저장/삭제/나가기 | GetGroup, UpdateGroup, DeleteGroup, LeaveGroup, UploadImage |

분리 규칙:
- **멤버 탭의 인박스는 role 게이트 없이 항상 시도하고 실패=빈 목록**(지금도 runCatching
  흡수 구조 — 비모더레이터 403이면 인박스가 안 그려질 뿐). 초대코드 버튼 노출(canModerate)과
  일정 카드의 모더레이터 삭제 판정은 **화면이 GroupDetailViewModel의 group 상태로 게이트**
  — 멤버/일정 VM이 GetGroup을 중복 호출하지 않는다. 일정 카드의 "작성자 본인" 삭제
  판정은 일정 VM의 myUserId(GetCurrentUserId — JWT 디코드, 로컬 연산)로 한다.
- Action.RefreshFeed/Event.RefreshFeed 중계는 detail VM에서 **삭제** — 작성 복귀
  (refreshRequested)·풀리프레시 때 화면(Content)이 각 탭 VM 액션과 프레젠터
  refresh()를 직접 조율한다(현재도 화면이 이벤트 받아 refresh() 호출하던 구조의 단순화).
- 풀리프레시는 상세+피드+앨범(현행)+일정(현재 월 재조회)을 함께 갱신. 설정 탭은
  폼 편집 중일 수 있어 제외(진입 시 self-load).

### 선언·소유 (keep-alive)
- **Compose**: GroupDetailScreen의 default parameter로 6개 VM 전부 선언
  (`viewModel(key="group-feed-$groupId")` 등 private 팩토리 — 기존 관례). 백스택 엔트리
  스코프라 HorizontalPager가 페이지를 dispose해도 VM 생존. 피드/앨범 lazyPagingItems
  수집은 지금처럼 **Content 수준(페이저 밖)** 유지 — 프레젠터를 dispose 레벨 안에 두면
  탭 복귀 때 스크롤·스냅샷을 잃는다(홈 탭 교훈).
- **iOS**: GroupDetailView(외곽)가 @StateObject로 6개 VM 소유 → Content에 전달.
  LazyPagingItems 2개도 현행대로 Content 소유. tabContent switch가 탭 뷰를 소멸시켜도
  상태 생존.

## 5. 일정 탭 UI (웹 /groups/[id]/events 미러)

LazyColumn(iOS ScrollView 탭 콘텐츠) 구성:
1. **캘린더 카드**: 헤더(◀ N년 N월 ▶ · 오늘 · 일정 만들기 토글), 7열 그리드 —
   요일 라벨(일=rust, 토=accent), 앞쪽 빈 칸=월 시작 요일, 일자 셀(오늘=accent 테두리,
   선택=accent 배경+onAccent 글자, 일정 있는 날=점 최대 3개)
2. **생성 폼**(토글 시 인라인 카드): 제목(필수 ≤100)/장소(≤200)/설명(≤2000)/시작·종료
   시각. **날짜는 캘린더 선택일을 그대로 사용**(M2에 DatePicker 없음 — 폼엔 선택일 표시만).
   Compose 시각 입력=HH:MM 텍스트 필드 2개(시작 필수·기본 19:00, 종료 선택,
   종료≥시작 검증은 클라에서 웹과 동일 문구). iOS 시각 입력=네이티브 DatePicker
   (hourAndMinute) — 플랫폼 관용 예외. 등록 성공 → 폼 닫고 해당 일자 선택, 그 달이면
   목록 반영·다른 달이면 그 달로 이동(웹 onCreated 미러)
3. **선택일 목록**: 날짜 헤더(M월 d일 요일) + EventCard들 — 제목·시각(시작~종료),
   장소(📍), 설명, RSVP 3버튼(참석/미정/불참 — 내 상태=primary, **같은 상태 재탭=취소**,
   낙관적 갱신 없이 서버 응답 EventResponse로 카드 교체), "참석 n · 미정 n · 불참 n" 탭
   시 참석자 명단 펼침(단건 API lazy 조회, 실패=조용히 빈 목록), 삭제(작성자 본인 또는
   모더레이터 — 확인 다이얼로그), "OO님이 만든 일정" 캡션. 일정 없는 날="이 날짜에는
   일정이 없습니다."
4. 시각/날짜 귀속은 **기기 로컬 타임존**(웹 미러). 월 이동 시 [월초, 다음달 초) ISO로
   범위 재조회, "오늘"=현재 월 복귀+오늘 선택.

### 날짜 유틸 (Compose)
`ui/util/LocalDates.kt` **expect/actual** — androidMain/jvmMain 둘 다 java.time으로 동일
구현(신규 의존성 0, kotlinx-datetime 도입 안 함): 월 일수, 월 1일 요일(일=0), 오늘
로컬 날짜, ISO→로컬 날짜 키(yyyy-MM-dd), 로컬 날짜+시각→ISO(생성 요청용).
iOS는 Calendar.current/ISO8601DateFormatter(기존 소수부 스트립 유틸 재사용).

## 6. 설정 탭 UI (웹 /groups/[id]/settings 미러 + 레거시 나가기)

진입 시 GetGroupUseCase self-load(웹 미러 — 상세 VM과 독립, 폼 시드). 역할별 렌더:
- **OWNER**: 수정 폼 — 그룹 이름(필수 ≤100)/설명(≤1000)/대표 이미지(기존 이미지 피커+
  UploadImageUseCase, 계정 설정 아바타 패턴)/가입 방식(라운지는 숨김) + 저장 버튼
  (저장 성공="저장했습니다." 문구) + **위험 구역** 카드: 안내 문구+그룹 삭제(2단계 확인,
  웹 confirmingDelete 미러). 라운지는 위험 구역 자체를 숨긴다.
- **부방장·멤버**: "그룹 나가기" 카드(확인 다이얼로그 — 레거시 설정 탭 ll_withdrawal
  미러). 라운지는 숨김(서버도 거부).
- 저장 요청은 ⚠️교체 계약 대응으로 **로드해 온 기존 값 기반 4필드 전부 전송**.

### 동기화·내비게이션
- 저장 성공 → `Event.Saved` → 화면이 GroupDetailViewModel Refresh 트리거(커버·제목 즉시
  갱신). 그룹 목록 커버는 다음 진입/새로고침이 따라잡는다.
- 삭제·나가기 성공 → `Event.Closed` → GroupDetailScreen 신규 콜백 `onGroupClosed` →
  App.kt가 pop + 그룹 목록 refresh(작성 복귀 savedStateHandle 결과 패턴 재사용).
  iOS는 dismiss + GroupsViewModel refresh.

## 7. iOS 미러

- 신규 파일 4개: `GroupEventsTab.swift` `GroupEventsViewModel.swift`
  `GroupSettingsTab.swift` `GroupSettingsViewModel.swift` → **pbxproj 등록 4건**
  (Group 그룹, 기존 ID 규칙 이어서). VM 분리분은 GroupDetailViewModel.swift 분할로
  신규 3파일(GroupFeedViewModel/GroupAlbumViewModel/GroupMembersViewModel.swift) 추가 등록
  — 총 pbxproj 신규 7파일.
- 캘린더=LazyVGrid 7열+Calendar.current. 뷰 생성자 순서·프로퍼티 풀네임 규칙 준수.
- VM 생성자는 UseCase 주입(init(container:) 금지 규칙).

## 8. 에러 처리

- 목록/폼 로드 실패: 문구+다시 시도 버튼(기존 탭 패턴)
- 액션 실패(생성/삭제/RSVP/저장/나가기): 해당 카드·폼 인라인 에러 문구(웹 미러),
  진행 중 버튼 잠금(busy 가드 — processingRequestUserId 선례)
- CancellationException은 rethrow(기존 규칙)

## 9. 제외 범위

- 일정 수정 UI(백엔드 PATCH만 존재 — 웹과 동일하게 제외), upcoming API, 일정 알림,
  멀티데이 표시(시작일 귀속 — 웹 미러), 소유권 이전, 멤버 강퇴/역할 변경 UI(백엔드만
  존재), 그룹 목록 즉시 갱신(다음 진입이 따라잡음)

## 10. 검증 계획

- shared: EventDtos 직렬화 테스트(PostDtosTest 선례) + 기존 테스트 회귀
- 컴파일: Windows gradle `:composeApp:compileDebugKotlinAndroid` `:composeApp:compileKotlinJvm`
  + `:shared:compileKotlinIosSimulatorArm64` — ⚠️Swift는 Mac 부재로 미검증
- 데스크톱 스모크(가능하면): 일정 생성→캘린더 점→RSVP→삭제 흐름
- 워크플로: 실수정 파일만 경로 스테이징+커밋 메시지 전달, 커밋·push는 사용자
