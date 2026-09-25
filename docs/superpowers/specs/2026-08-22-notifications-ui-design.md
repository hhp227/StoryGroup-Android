# 알림 화면 풀블리드 개편 + 대상 컨텍스트 표시 설계

날짜: 2026-08-22
대상: composeApp `NotificationsScreen.kt` · iosApp `NotificationsView.swift` · shared 알림 계약
· 백엔드 `StoryGroup-WebApp` notification 패키지

## 배경

알림 화면이 SgCard 카드 나열이었고, 내용도 타입 라벨("좋아요"/"댓글")+상대시각뿐이라
어떤 게시글에 대한 알림인지 알 수 없었다. 원인은 서버: notifications 테이블이
`type/target_type/target_id`만 저장하고, 목록 SELECT가 조인 없이 그대로 내려줬다.
COMMENT는 target_id가 **댓글 id**라 클라이언트 단독으론 게시글 역추적도 불가.

## 설계

### 1. 백엔드 — 조회 시점 컨텍스트 역추적(마이그레이션 없음)

`NotificationMapper` findByUser/findById에 LEFT JOIN 추가(파일 최상위 private const 공유):

- REPLY → `user_replys`로 post 역추적, POST → 직접, GROUP → 그룹 직결
  (`COALESCE(p.group_id, GROUP target_id)`)
- 응답 추가 필드: `postId`, `postPreview`(=`LEFT(posts.text, 60)`, 게시글엔 title 컬럼이
  없어 본문 앞부분), `groupId`, `groupName`
- 전부 LEFT JOIN + `deleted_at IS NULL`: 대상 삭제·RLS(posts_select=멤버십) 불가 시
  알림은 남고 컨텍스트만 null. findById도 동일 보강이라 WS 실시간 페이로드도 같은 계약.
- **행위자(누가 눌렀는지)는 스키마에 없음**(actor_id 미저장) — 컬럼 추가 마이그레이션이
  필요해 이번 범위에서 제외.

### 2. shared — 계약 미러

`NotificationResponse`/`AppNotification`에 4필드 추가(전부 nullable 기본 null — 구서버
호환). toDomain에서 빈 문자열 preview/groupName은 null로 정규화(이미지만 있는 게시글).

### 3. 앱 UI — 풀블리드 행 목록 (카드 폐기)

- LazyColumn 가로 contentPadding 제거, 행이 화면 폭 전체 사용, 행 사이 1dp
  `stoneBorder` 헤어라인(그룹 탭 진입 스트립과 같은 플랫 계열).
- 행 구성: 36dp `accentSoft` 원형 메달리온+타입 아이콘(accent) | 타입 라벨(볼드 ink)
  → **컨텍스트 줄 "그룹명 · 게시글 미리보기"**(inkSoft, 2줄 말줄임, null이면 숨김)
  → 상대시각(inkFaint) | 미읽음이면 "읽음" TextButton(accent)/스피너.
- 미읽음=linen 배경, 읽음=투명+0.6 흐림(기존 정책 유지).
- 타입 아이콘 매핑(Compose material ↔ iOS SF Symbol 1:1): 새 게시글=Description/doc.text,
  댓글=Chat/bubble.left, 좋아요=Favorite/heart.fill, 멘션=AlternateEmail/at,
  채팅=Forum/bubble.left.and.bubble.right, 화상회의=Videocam/video.fill,
  공지=Campaign/megaphone.fill, 초대=Email/envelope.fill,
  가입 신청=PersonAdd/person.badge.plus, 승인=CheckCircle/checkmark.circle.fill,
  거절=Cancel/xmark.circle.fill.

### 4. 행 클릭 → 게시글 상세 이동 (2차, 같은 날 추가)

- 게시글 컨텍스트(groupId+postId)가 풀린 행만 클릭 가능 — 탭 시 **읽음 처리(다른 건 처리 중이
  아닐 때)+`NavigateToPostDetail` 발화**. 풀리지 않은 행(그룹류/삭제된 대상)은 표시만.
- Compose: `NotificationsScreen`에 `onNavigationAction` 기본 파라미터(GroupsScreen 관용구),
  행 `clickable`. 안쪽 "읽음" TextButton은 자기 클릭을 우선 소비해 겹치지 않는다.
- iOS: 셸 path에 `.postDetail` 목적지가 이미 등록돼 있어 발화 배선만 추가 —
  `DestinationView.onOpenPost` 신설, Tab/DrawerShellView가
  `onNavigationAction(.navigateToPostDetail)`로 어댑팅(onOpenUserProfile 선례),
  행은 `contentShape+onTapGesture`(안쪽 Button이 탭 우선).
- ⚠️ 기지 갭(이번 범위 아님): path 경유 PostDetailView는 pendingResults(postUpdated)를
  아직 소비하지 않는다 — MainShellView 주석 현행화만 함.

### 변경 없음 / 보류

- NotificationsViewModel·유스케이스·엔드포인트 — 무수정(응답 필드만 늘어남).
- 웹 알림 목록(정적 라벨·클릭 없음)도 같은 보강을 소비하는 후속 과제.

## 구현 파일

- 백엔드: `notification/NotificationMapper.kt`(조인)·`Notification.kt`·`dto/NotificationDtos.kt`
- shared: `dto/NotificationDtos.kt`·`model/AppNotification.kt`·`NotificationRepositoryImpl.kt`
- UI: `composeApp/.../notification/NotificationsScreen.kt`·`iosApp/.../NotificationsView.swift`
- 클릭 이동(2차): `iosApp/.../Shell/MainShellView.swift`(DestinationView.onOpenPost+주석 현행화)
  ·`TabShellView.swift`·`DrawerShellView.swift`

## 검증

- 백엔드 `compileKotlin`, 앱 `:composeApp:compileKotlinJvm`/`compileDebugKotlinAndroid` 통과.
- Swift는 Mac 부재로 컴파일 미검증(기존 관행).
- **배포+스모크 E2E 통과(2026-08-22, 리비전 00091)**: QA A/B 계정(그룹 31)으로
  비인증 401 → B의 좋아요/댓글 → A 알림에 postId/postPreview/groupId/groupName 탑재
  (COMMENT의 REPLY→게시글 역추적 포함) → 게시글 삭제 시 컨텍스트 null 강등까지 확인.
