# 홈 검색(통합검색) 설계 — 2026-08-14

## 배경

- 홈 상단바 검색 아이콘이 `TODO: 검색` 스텁으로 남아 있다(Compose `HomeScreen.kt`, iOS 셸 툴바 동일).
- 백엔드 `GET /api/search`(PRD 13번)는 이미 배포되어 5섹션(그룹·게시글·파일·메시지·사용자)을 반환한다.
  친구 탭(V-친구, `8491eb8`)이 이 API의 users 섹션만 소비 중이고, 나머지는 홈 통합검색 몫으로 유보되어 있었다.
- 레거시 Android 앱에는 통합검색이 없다(검색 UI는 친구 검색뿐) — **이번 기능의 미러 원본은 웹 `/search` 페이지**다.

## 확정 사항 (사용자 선택)

| 결정 | 선택 |
|---|---|
| 검색 범위 | **5섹션 풀 미러**(그룹·게시글·파일·메시지·사용자) — 서버 수정 0 |
| 검색 트리거 | **제출 시 검색**(IME 검색 액션) — 웹·친구 탭과 일관 |
| 초기 화면 | **빈 상태 안내만**(웹의 검색 전 친구 목록은 친구 탭과 중복이라 미러하지 않음) |
| 접근 방식 | 단일 요청 5섹션 원페이지. 섹션 탭+섹션별 페이징 안은 서버 신설이 필요해 기각 |

## 범위

**포함**: shared 검색 계층 신설, Compose `SearchRoute` 풀스크린 화면, iOS `SearchView` 미러, 결과 탭 내비게이션(그룹/게시글/채팅방/파일 URL), users 섹션 친구 추가·해제.

**제외**: 백엔드 수정 일절, 섹션별 "더보기"/페이징, 최근 검색어 저장, 특정 메시지 위치로 점프, 사용자 공개 프로필 화면(친구 탭 때와 동일하게 유보), `FriendRepository.searchUsers` 리팩터링(무수정 유지).

## 백엔드 계약 (기존, 수정 0)

`GET /api/search?query={q}&limit={n}` — limit 1..20 클램프, 기본 10. 앱은 **limit 생략(기본 10)** — 5섹션이 쌓이는 화면이라 섹션당 10이면 충분.

응답 `SearchResponse` (섹션은 항상 존재, 빈 배열 가능):

```
groups:   [{ id, name, image?, description? }]
posts:    [{ id, groupId, groupName, authorName, text, createdAt(ISO) }]
files:    [{ id, groupId, groupName, name, url, createdAt }]
messages: [{ id, chatRoomId, groupId?, groupName?, authorName, text, createdAt }]
users:    [{ id, name, profileImg?, statusMessage? }]
```

- 그룹/게시글/파일/메시지는 내가 속한 그룹 범위, users는 같은 그룹 소속만 노출(서버 정책).
- LIKE 와일드카드 이스케이프·차단 사용자 필터는 서버 몫(기구현).
- 빈 검색어는 서버가 400 — 클라이언트는 trim 후 빈 문자열이면 요청 자체를 막는다.

## shared 설계

- `data/network/dto/SearchDtos.kt` 신설: 위 5섹션 1:1 `@Serializable` DTO.
  ⚠️ 같은 패키지 `FriendDtos.kt`에 이미 `SearchResponse`(users만)·`SearchUserResponse`가 있다 — 이름 충돌을 피해
  신규 응답은 **`UnifiedSearchResponse`** 로 명명하고, users 섹션은 기존 `SearchUserResponse`를 재사용한다.
  FriendDtos.kt·FriendRepository는 무수정.
- `domain/model/Search.kt` 신설:
  - `SearchResults(groups, posts, files, messages, users)`
  - 섹션 모델 4종: `GroupSearchHit`, `PostSearchHit`, `FileSearchHit`, `MessageSearchHit`
    (서버 DTO명 `XxxSearchResult`와 도메인을 구분하고, 기존 도메인 `UserSearchResult`와의 혼동을 피하는 네이밍)
  - users 섹션은 **기존 `UserSearchResult` 도메인 재사용**(친구 탭과 동일 타입 → AddFriend/RemoveFriend 유스케이스 그대로 적용)
  - `createdAt`은 ISO 문자열 그대로 노출(포맷팅은 플랫폼 UI — 기존 Post 관용구)
- `domain/repository/SearchRepository` + `data/repository/SearchRepositoryImpl`: `suspend fun search(query: String): SearchResults`
- `domain/usecase/SearchUseCase`: `@Throws` suspend, 단건 위임.
- AppContainer(양 플랫폼)에 searchRepository(내부)+searchUseCase 노출.

## Compose 설계

- **라우트**: `App.kt`에 `@Serializable SearchRoute` 풀스크린 목적지 신설(CreatePost/AccountSettings 패턴, `Surface(color = paper)`로 하층 셸 터치 차단). 진입은 `HomeScreen` 상단바 검색 아이콘 스텁 → `onOpenSearch` 콜백(기존 onOpenNotifications 드릴링 경로와 동일 체인).
- **`SearchScreen`**: 상단바 = 뒤로가기 + 인라인 검색 필드(자동 포커스) 구성. IME 검색 액션으로 제출. 검색어는 화면 로컬 `rememberSaveable`(CreatePost 관용구).
  - 검색 전/검색어 비움: 빈 상태 안내("그룹, 게시글, 파일, 메시지, 사용자를 검색해보세요").
  - 결과: LazyColumn — 섹션 순서는 웹 미러(사용자→그룹→게시글→파일→메시지), `SgSectionTitle` + 행, **빈 섹션은 숨김**, 전 섹션 빈이면 "검색 결과가 없습니다".
  - 행 구성은 웹 미러: 그룹=커버(폴백 그라데이션)+이름+설명, 게시글=텍스트+그룹·작성자·상대시각, 파일=파일명+그룹명, 메시지=텍스트+작성자·방 이름, 사용자=아바타+이름+상태메시지+친구 추가/해제 버튼.
- **`SearchViewModel`** (백스택 스코프, 화면 default param `viewModel(key)` — GroupDetail 패턴, MVI 3요소):
  - UiState: `results: SearchResults?`, `isSearching`, `error`, `friendIds: Set<Long>`, `friendActionError`
  - Action: `Search(query)`, `ClearResults`(검색어 비움), `AddFriend(user)`, `RemoveFriend(user)`, `ClearFriendActionError`
  - Event: 없음(`Nothing` + emptyFlow 관용구)
  - init에서 `GetFriendsUseCase`로 friendIds 로드(버튼 상태 판정). **실패는 조용히 무시** — 검색 기능은 동작해야 한다.
  - AddFriend/RemoveFriend는 friendIds 낙관적 갱신, 409(ALREADY_FRIEND)는 서버 문구 노출(친구 탭 관용구 미러).
  - 검색 중 재제출은 무시(`isSearching` 가드 — 친구 탭 미러).

## 결과 탭 내비게이션 매핑

| 섹션 | 동작 | 콜백 |
|---|---|---|
| 그룹 | 그룹 상세 push | `onOpenGroupDetail(groupId)` → `GroupDetailRoute` |
| 게시글 | 게시글 상세 push | `onOpenPostDetail(groupId, postId)` → `PostDetailRoute` |
| 파일 | URL 브라우저 열기 | 화면 내 `LocalUriHandler`(콜백 아님) |
| 메시지 | 채팅방 push | `onOpenChatRoom(chatRoomId, groupId, title)` → `ChatRoomRoute` — title: 그룹방=groupName, DM=authorName |
| 사용자 | 행 탭 무동작, 버튼만 | (친구 탭·웹 미러) |

⚠️ 그룹 상세 진입 시 기존 `GroupDetailRoute` 흐름은 그룹 목록 lookup에 의존하지 않고 groupId self-load(기구현)라 검색 발 진입도 동작한다.

## iOS 미러

- `UI/Screens/Search/SearchView.swift` + `SearchViewModel.swift` 신규 — **pbxproj 등록 2파일**(다음 가용 합성 ID 0044/0045 계열, 정합성 카운트 검증 필수).
- 진입: 셸 툴바 검색 버튼(Tab/DrawerShellView) → `MainShellView @State showSearch` + 숨김 NavigationLink(iOS 15 폴백 패턴) — 셸 전체(탭바 포함)를 덮는 push.
- 검색 필드는 콘텐츠 인라인(`.searchable` 금지 — keep-alive ZStack 함정 회피, 친구 탭 미러).
- 결과 탭 push는 **SearchView 자체 소유 3종**(그룹 상세·게시글 상세·채팅방 — GroupDetailView 자체 push 선례). GroupDetailView가 요구하는 `container`/`theme`/`profileViewModel`을 MainShellView→SearchView로 전달.
- 파일 = `UIApplication.shared.open(url)`. VM은 init(useCase 주입) 규칙(2026-07-24 지시), 프로퍼티명 풀네임 `searchViewModel`.
- KMP suspend는 @MainActor 호출 제약 준수. `SearchResults`의 도메인 모델이 SwiftUI 타입과 이름 충돌 시 스코프 임포트 규칙 적용.

## 에러 처리

- 검색 실패: 결과 영역 인라인 에러 + 재시도 버튼(친구 탭 미러). `CancellationException`류 오염 없음(단발 suspend).
- 친구 추가/해제 실패: 인라인 문구로 서버 메시지(409 등) 노출(친구 탭 관용구 미러).
- 파일 URL 열기 실패: 무시(플랫폼 기본 동작).

## 검증 계획

- shared jvmTest: `SearchDtos` 매핑 테스트(PostDtosTest 선례 — 5섹션 파싱, 옵셔널 null, 알 수 없는 키 무시).
- 컴파일 3타깃: `:shared:jvmTest`+jvm, Windows gradle `assembleDebug`(WSL aapt2 부재), `:shared:compileKotlinIosSimulatorArm64`(klib).
- 데스크톱 스모크: `:composeApp:run` 기동 후 검색 화면 진입·검색 1회.
- ⚠️ Swift/Mac 컴파일 미검증(관례) — Mac 검증 1순위 항목: SearchView push 3종·pbxproj 정합.
- 커밋: 실수정 파일만 경로 스테이징(CRLF 노이즈 회피)+커밋 메시지 전달, 커밋·push는 사용자.

## 알려진 한계 (의도적)

- 섹션당 10건 고정, 더보기 없음(웹과 동일).
- DM 메시지 결과의 채팅방 title은 authorName — 내가 쓴 메시지가 검색되면 방 제목이 내 이름으로 뜰 수 있다(웹도 authorName 표시, 서버 응답에 상대 이름 없음 — 서버 수정 없이는 해결 불가라 수용).
- 특정 메시지 위치로 점프 불가(채팅방 최신 진입).
- 파일 결과는 앱 내 목적지가 없어 URL 열기로 대체.
