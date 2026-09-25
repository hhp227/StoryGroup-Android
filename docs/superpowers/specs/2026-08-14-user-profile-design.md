# 사용자 공개 프로필 화면 설계 — 2026-08-14

## 배경

- 웹에는 V21(작성자 액션 메뉴)부터 공개 프로필 페이지 `/users/[userId]`가 있고 백엔드 `GET /api/users/{userId}`(PublicProfileController)가 기배포되어 있다. KMP 앱에는 이 화면이 없어 세 곳이 유보 상태다:
  - 친구 탭: 행 탭 무동작("공개 프로필 화면은 범위 제외" — 2026-08-13)
  - 홈 통합검색 users 섹션: 행 탭 무동작(같은 이유 — 2026-08-14)
  - 게시글 상세: 본문·댓글 작성자 아바타/이름 탭 무동작
- 이번 기능의 미러 원본은 **웹 `/users/[userId]` 페이지**다. 레거시 앱에는 대응 화면 없음.

## 확정 사항 (사용자 선택)

| 결정 | 선택 |
|---|---|
| 진입점 | **3곳**: 친구 탭 행 탭, 검색 users 행 탭, 게시글 상세 본문·댓글 작성자 탭 (피드 카드 작성자는 제외) |
| 작성자 탭 동작 | **바로 프로필 push** — 웹의 4항목 메뉴(프로필/DM/신고/차단)는 미러하지 않음. 신고·차단은 앱 상세의 기존 더보기 메뉴가 이미 담당(본문·댓글 각각), DM은 프로필 화면 버튼이 담당하므로 중복 없음 |

## 범위

**포함**: shared 공개 프로필 계층, Compose `UserProfileRoute` 풀스크린 화면, iOS `UserProfileView` 미러, 진입점 3곳 배선, 프로필 내 액션(본인=프로필 수정 이동, 타인=1:1 DM+친구 추가/해제).

**제외**: 백엔드 수정 일절, 피드 카드(SgPostCard) 작성자 탭, 프로필 내 신고·차단(웹 프로필에도 없음 — 게시글 더보기 몫), 프로필에 게시글 목록·공통 그룹 등 확장 정보(서버 응답에 없음), 프레즌스 도트(친구 탭 전용 유지).

## 백엔드 계약 (기존, 수정 0)

`GET /api/users/{userId}` → `PublicProfileResponse`:

```
{ id, name, profileImg?, bio?, statusMessage?, createdAt(ISO OffsetDateTime) }
```

- 인증 필요(다른 API와 동일). 404 = 없는 사용자.
- 친구 여부는 응답에 없음 — 웹처럼 클라이언트가 친구 목록과 대조한다.

## shared 설계

- `data/network/dto/UserDtos.kt`에 `PublicProfileResponse` DTO 추가(위 계약 1:1, 옵셔널 기본 null).
- `domain/model/PublicProfile.kt` 신설: `PublicProfile(id, name, profileImg?, bio?, statusMessage?, createdAt: String)` — createdAt은 ISO 문자열 그대로(포맷팅은 플랫폼 UI, Post 관용구). 기존 `Profile`(내 프로필, email 포함)과 별개 타입 — 화면 계약이 다르다.
- `UserRepository.getPublicProfile(userId): Result<PublicProfile>` + `UserRepositoryImpl` 구현(404는 예외 → 화면 로드 에러 문구).
- `GetPublicProfileUseCase`(@Throws suspend). AppContainer(양 플랫폼)에 노출.
- DM·친구·본인 판정은 기존 유스케이스 재사용: `OpenDirectRoomUseCase`, `GetFriendsUseCase`/`AddFriendUseCase`/`RemoveFriendUseCase`, `GetCurrentUserIdUseCase`(JWT sub — 웹 getUserIdFromToken 미러).

## 프로필 화면 (웹 미러)

- **라우트**: `@Serializable internal data class UserProfileRoute(val userId: Long)` NavHost 풀스크린(Surface(paper)+navigationBars 인셋 — 기존 관용구).
- **`UserProfileScreen`**: SgTopBar(뒤로가기, 제목 "프로필") + 카드형 본문:
  - 헤더: 아바타 72dp + 이름 + 상태메시지 + 가입일 — 웹 `toLocaleDateString("ko-KR")` 미러로 **"YYYY. M. D. 가입"**(예: "2026. 7. 1. 가입"). ISO 문자열의 날짜부만 파싱하는 소형 유틸을 양 플랫폼에 추가(기존 TimeFormats 파일 확장)
  - bio: 있으면 구분선 아래 여러 줄 표시(pre-wrap)
  - 액션 행:
    - **본인**(GetCurrentUserId == userId): "프로필 수정" 버튼 → 기존 `AccountSettingsRoute`(웹 /settings/profile 미러)
    - **타인**: "1:1 DM"(primary, OpenDirectRoom 성공 → `ChatRoomRoute(roomId, null, 상대 이름)`) + "친구 추가/해제"(secondary) — **친구 목록 로드 실패 시 친구 버튼 숨김**(웹 isFriend===null 미러, DM 버튼은 유지)
  - 로드 에러 = 인라인 문구+다시 시도, 액션 에러 = 인라인 문구(웹 actionError 미러).
- **`UserProfileViewModel`**(백스택 스코프 default param, MVI 3요소):
  - UiState: `profile: PublicProfile?`, `isLoading`, `loadError`, `isFriend: Boolean?`(null=판정 불가→버튼 숨김), `isBusy`(친구 토글), `isOpeningDm`, `actionError`, `myUserId: Long?`
  - Action: `Refresh`, `ToggleFriend`, `OpenDm`, `DismissActionError`
  - Event: `DmOpened(chatRoomId, title)`(친구 탭 관용구 미러 — 화면이 수집해 채팅방 push)
  - init: 프로필 로드 + 친구 목록 로드(실패 조용히 무시 → isFriend null 유지) + 내 ID 확보.
  - ToggleFriend: 성공 시 isFriend 토글(웹 미러), 실패 시 actionError 서버 문구(409 등).

## 진입점 배선

| 진입점 | Compose | iOS |
|---|---|---|
| 친구 탭 행 탭 | FriendRow 행 탭 → `onOpenUserProfile(userId)` — 셸 드릴링 체인(onOpenSearch와 동일 경로: MainShell→Tab/DrawerShell→DestinationContent→FriendsScreen). 기존 메시지/해제 버튼 동작 불변 | keep-alive ZStack이라 자체 push 불가 → **MainShellView 7번째 push(`selectedUserId`)** + FriendsView로 콜백 드릴링 |
| 검색 users 행 탭 | SearchScreen `onOpenUserProfile` 콜백 추가(App.kt에서 navigate). UserRow 행 탭 활성화, 친구 추가/해제 버튼 불변(버튼 영역 탭은 버튼 우선) | SearchView 자체 push 4번째(기존 이중 분기에 추가) |
| 게시글 상세 본문·댓글 작성자 | 작성자 아바타+이름 영역만 탭 타깃(본문 텍스트·더보기 제외) → `onOpenUserProfile(userId)` — PostDetailRoute composable에서 navigate | PostDetailView 자체 push 추가(숨김 링크/navigationDestination 이중 분기) |

⚠️ 게시글·댓글 도메인 모델에 작성자 userId가 있어야 한다 — `Post.userId`·`Comment`의 작성자 id 존재를 플랜에서 확인(백엔드 PostResponse에 userId 존재 확인됨, 댓글도 동일 예상 — 없으면 해당 진입점은 본문 작성자만으로 축소하고 스펙 갱신).

- 프로필 화면 자체의 후속 내비: DM 성공 → 채팅방 push(Compose는 onOpenChatRoom 콜백→ChatRoomRoute, iOS는 UserProfileView 자체 push), 본인 프로필 수정 → AccountSettings(Compose는 onOpenAccountSettings 콜백→기존 라우트).
- **iOS 본인 프로필 수정 경로(확정)**: UserProfileView가 `AccountSettingsView(container:profileViewModel:)`를 **자체 push**(GroupDetailView 선례). 이를 위해 UserProfileView는 `profileViewModel`(셸 세션 VM)을 받는다 — 친구 탭 발(MainShellView 보유 ✓)·검색 발(SearchView 보유 ✓)은 그대로 전달, **게시글 상세 발은 PostDetailView init에 profileViewModel 파라미터 추가**(호출부 3곳: HomeView·GroupDetailView·SearchView — HomeView는 셸에서 profileViewModel을 새로 내려받는 1파라미터 추가 동반). 본인 프로필 진입은 사실상 게시글 상세(내 글·내 댓글)에서만 발생한다(친구 목록·검색 users엔 본인이 없음).

## iOS 미러

- `UI/Screens/User/UserProfileView.swift`+`UserProfileViewModel.swift` 신규 — pbxproj 등록 2파일(파일 ID 0046/0047 계열 + **User 그룹 A1013015 계열 신설**, Screens children 등록, 정합성 카운트 검증 필수 — 홈 검색 때 0044/0045·A1013014까지 사용됨).
- VM은 init(useCase 주입) 규칙, 프로퍼티 풀네임 `userProfileViewModel`, KMP suspend는 @MainActor, Event는 PassthroughSubject(DmOpened).
- 진입 push는 위 표 참조. UserProfileView 자체 push: 채팅방 1종(+프로필 수정 처리 방식은 플랜에서 확정).

## 에러 처리

- 프로필 로드 실패(404 포함): 인라인 에러+다시 시도.
- DM 실패(403 BLOCKED 등)·친구 토글 실패(409 등): actionError 인라인 서버 문구(친구 탭·웹 미러).
- 친구 목록 로드 실패: 조용히 무시, 친구 버튼 숨김(웹 미러 — 검색 화면의 "기본 친구 추가" 처리와 다름에 주의: 여기는 해제가 오동작하면 안 되므로 숨김이 맞다).

## 검증 계획

- shared jvmTest: PublicProfileResponse 매핑 테스트(옵셔널 null 기본값).
- 컴파일 3타깃 + Windows assembleDebug + 데스크톱 부트 스모크. ⚠️Swift/Mac 미검증(관례) — Mac 1순위: 진입점 push 4곳(친구/검색/상세/프로필 내 채팅방)·pbxproj.
- 스테이징+커밋 메시지 전달까지(커밋·push는 사용자). 브랜치는 사용자 결정(제안: feature/userprofile).

## 알려진 한계 (의도적)

- 프로필에 게시글·공통 그룹 등 확장 정보 없음(서버 응답 그대로).
- 피드 카드 작성자 탭은 미개방(카드 탭=상세 이동과의 중첩 회피 — 사용자 확정).
- 차단당한/탈퇴 사용자 프로필 조회 동작은 서버 응답을 따름(별도 처리 없음).
