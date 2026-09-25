# 그룹 상세 탭 재편 + 앨범 탭 (KMP) 디자인

날짜: 2026-08-11 / 브랜치: `feature/group` (KMP) / 백엔드·웹 수정 0

## 목표

웹 그룹 상세에서 볼 수 있는 앨범(게시글 첨부를 모은 **파생 뷰**, `GET /api/groups/{id}/photos` 기배포)을
KMP(Android·iOS·데스크톱)에 구현한다. 사용자 확정: 단순 이식이 아니라 레거시
`GroupDetailFragment`(CollapsingToolbar + TabLayout + ViewPager2)처럼 **그룹 상세 자체를
콜랩싱 커버 + 탭 구조로 재편**하고, 앨범을 그 탭 중 하나로 넣는다.

레거시 참고: `Legacy/StoryGroup-Android` — 탭 4개(소식/앨범/맴버/설정, `R.array.tab_name`),
탭바는 접혀도 핀 상단바 아래 고정, FAB는 소식 탭에서만(`isTabPositionZero`), 앨범은 Paging 그리드
(`AlbumFragment`+`AlbumPagingAdapter`), 각 탭 fragment가 자체 SwipeRefreshLayout 소유.

## 결정 사항 요약

| 항목 | 결정 |
|---|---|
| 탭 구성 | 소식 / 앨범 / 일정 / 멤버 / 설정 — 5탭(레거시 4탭 + 일정, 사용자 확정) |
| 이번 구현 | 소식(기존 피드 이동)·앨범(신규)·멤버(신규 탭 화면). 일정·설정은 빈 화면 placeholder |
| 앨범 그리드 | **월별 섹션 헤더 + 3열 그리드** (웹 `/groups/[id]/photos` 미러, 사용자 확정) |
| 앨범 셀 탭 | 원본 게시글 상세로 이동(맥락 보존, 웹 미러) — 라이트박스 없음 |
| 가입신청 인박스·초대코드 | **멤버 탭 상단**으로 이동(멤버 관리 성격, 사용자 확정) |
| FAB(글쓰기) | 소식 탭에서만 노출(레거시 미러) |
| 데이터 | `GET /api/groups/{id}/photos?page&size` 소비, Paging3(공용 `PagePagingConfig`), `totalCount`는 미사용(YAGNI) |
| 탭 전환 | Compose = TabRow + `HorizontalPager` 스와이프(레거시 ViewPager2 미러) / iOS = 탭 터치만(플랫폼 관용 예외 — 하프시트 선례) |

## 1. shared (도메인 + 데이터)

- `domain/model/GroupPhoto.kt` 신설: `id, postId, image, mediaType(IMAGE/VIDEO enum), userId, authorName, createdAt(ISO String — Post.createdAt과 동일 관용구)`.
  동영상은 `image` 필드에 동영상 URL이 온다(웹 `MediaThumb` 계약 — 서버 썸네일 없음).
- `GroupDtos.kt`: `GroupPhotoResponse` + `GroupPhotosPageResponse{totalCount, photos}` + 매퍼.
  미지의 `mediaType` 문자열은 IMAGE로 폴백(역호환).
- `GroupRepository.getGroupPhotosPagingData(groupId: Long): Flow<PagingData<GroupPhoto>>` —
  `Pager(PagePagingConfig) { PagePagingSource { page, size -> GET } }` 기존 관용구 그대로
  (`getMyGroupsPagingData` 미러). 읽기 전용 피드 = Paging3 규칙의 6번째 피드.
- `GetGroupPhotosPagingDataUseCase` 신설(기존 `Get*PagingDataUseCase` 네이밍).
- iosMain `bridge/GroupBridges.kt`에 `emptyGroupPhotoPagingData()` 추가(`emptyGroupPagingData` 선례 —
  ObjC 제네릭에 static 확장 불가라 브리지 함수).

## 2. Compose — `SgCollapsingTabScaffold` 신설

기존 `SgCollapsingHeaderScaffold`는 "헤더 = 단일 LazyColumn의 첫 아이템" 방식이라 탭별 독립
스크롤과 구조적으로 충돌한다. 그룹 상세용 탭 스캐폴드를 신설하고 **홈은 기존 스캐폴드 무수정**.

- 구조: `Box { Column { 콜랩서블 커버(오프셋 높이) → TabRow → HorizontalPager } + CollapsingTopBar 오버레이 + FAB }`.
  `NestedScrollConnection`으로 위 스크롤 시 헤더 먼저 접고, 목록이 맨 위일 때 아래 스크롤이면 헤더를 편다.
  스크롤이 멎으면 가까운 쪽으로 snap(레거시 `scroll|exitUntilCollapsed|snap` 미러).
- `CollapsingTopBar`(투명→linen 스크림)·패럴럭스는 기존 코드를 internal로 추출해 재사용.
  탭바는 접힘 상태에서도 핀 상단바 바로 아래 고정(레거시 toolbar marginBottom=48dp 미러).
- 당겨서 새로고침은 **스캐폴드 수준 1개**(기존 스캐폴드처럼 `SgPullRefreshBox`가 전체를 감싼다) —
  페이지 내부에 두면 nestedScroll 체인상 풀리프레시가 헤더 펼침보다 먼저 오버스크롤을 소비하는
  함정이 있다. 갱신은 현재 탭 무관하게 상세+피드+앨범을 함께(단순·iOS `.refreshable`과 대칭).
- `GroupDetailScreen` 재편:
  - **소식**: 기존 피드 items 그대로 이동(Paging3·게시글 카드·푸터 재시도). FAB는 이 탭에서만.
  - **앨범**: `LazyVerticalGrid(3열)` + `collectAsLazyPagingItems`. 월 헤더는
    `itemSnapshotList`를 월별 그룹핑해 `GridItemSpan(maxLineSpan)` 헤더 아이템으로 삽입.
    ⚠️함정: 셀 컴포지션에서 반드시 **전역 인덱스로 `lazyPagingItems[index]` 접근**해야
    append(다음 페이지) 트리거가 유지된다 — 스냅샷 아이템만 쓰면 무한 스크롤이 죽는다.
    목록이 최신순이라 월 경계는 순서대로 끊기만 하면 된다(웹 주석 미러).
  - 앨범 셀: 정사각(aspectRatio 1) crop. IMAGE = `AsyncImage`(+`.gif` 접미면 GIF 뱃지 — 웹 미러),
    VIDEO = 기존 `VideoFrame` 첫 프레임 + ▶ 오버레이(데스크톱은 다크 칸 + ▶ 폴백 — 기존 한계 그대로).
    탭 → `onOpenPostDetail(postId)` 재사용.
  - **일정 / 설정**: `SgEmptyState` "준비 중" placeholder.
  - **멤버**: 상단 `JoinRequestInbox` + 초대코드 버튼(모더레이터 전용, 기존 컴포저블 이동) +
    멤버 4열 그리드(레거시 `MemberFragment`·Minigroup 4열 미러 — 기존 가로 `MemberStrip` 대체).
    멤버 탭 터치 → 기존 DM 확인 다이얼로그 재사용.
- `GroupDetailViewModel`: 단일 VM 유지(탭들은 한 화면). UiState에 `photosPagingData` 추가
  (기존 `pagingData` 관용구 미러). 글 작성 복귀·`Event.RefreshFeed` 시 피드와 앨범 둘 다
  `refresh()` — 앨범은 게시글 첨부의 파생 뷰라 원본이 바뀌면 함께 갱신(웹 refreshKey 미러).

## 3. iOS (SwiftUI 미러)

- `GroupDetailView` 재편: 기존 단일 ScrollView + 스트레치 커버 유지,
  `LazyVStack(pinnedViews: [.sectionHeaders])`의 `Section(header: 탭바)`로 탭바 핀 고정.
  선택 탭에 따라 섹션 콘텐츠 전환(스와이프 없음).
- 파일이 이미 664줄이라 앨범 탭은 `GroupAlbumTab.swift`로 분리 신설 — **pbxproj 수동 등록 필요**.
  멤버 탭은 기존 파일 내 서브뷰로(인박스·DM 다이얼로그가 이미 그 파일에 있음).
- `GroupDetailViewModel.swift`: `GetGroupPhotosPagingDataUseCase` 생성자 주입(규칙),
  초기값 `GroupBridgesKt.emptyGroupPhotoPagingData()`, `KmpPagingBridgeAdapter`로 수집(그룹 목록 선례).
  월별 그룹핑은 뷰에서 계산.
- 앨범 셀: IMAGE = `AsyncImage`, VIDEO = 기존 `VideoPoster`(AVAssetImageGenerator) + ▶, GIF 뱃지.
  탭 → 기존 게시글 상세 push 경로 재사용.
- 뷰 생성자 파라미터 순서는 Compose와 1:1 규칙 준수.

## 4. 에러·빈 상태

- 앨범 첫 로드 실패: 그리드 중앙 에러 + 다시 시도(피드 미러). append 실패: 푸터 재시도 관용구.
- 앨범 빈 상태: "아직 사진이 없습니다. 게시글에 사진이나 동영상을 올리면 여기에 모여요."(웹 문구 미러).
- 구서버(photos API 미배포 환경)는 없음 — 이미 프로덕션 배포된 API라 호환 케이스 불요.

## 5. 제외 범위

- 일정·설정 탭의 실제 기능(빈 화면만), 라이트박스 뷰어, `totalCount`("N장") 표기,
  홈(라운지) 앨범 패널, 웹 프론트·백엔드 수정.

## 6. 검증·커밋 절차

- jvm(Desktop) 컴파일 + Android APK 빌드로 검증. Swift/Mac은 이 환경에서 미검증(관례 명시).
- 실수정 파일만 LF 정규화 후 경로 스테이징(`git add -A` 금지). **스테이징 + 커밋 메시지 전달까지만** —
  커밋·push는 사용자 직접(워크플로 규칙).
