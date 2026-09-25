# 그룹 탭 진입 타일 + 가입 신청중 섹션 개편 설계

날짜: 2026-08-22
대상: composeApp `GroupsScreen.kt` · iosApp `GroupsView.swift` (양 플랫폼 미러)

## 배경

그룹 탭 상단의 그룹 만들기/그룹 찾기 진입 UI가 텍스트만 있는 아웃라인 버튼 2개로 단순했다.
레거시(GroupFragment)는 상단에 `그룹찾기 | 가입신청중 그룹 | 그룹 만들기` 아이콘 버튼 3개를
가로 배열했고, 가입 신청중 그룹은 전용 화면(JoinRequestGroupFragment)에서 확인·취소했다.

### 조사 결과 — 가입 신청중 그룹 확인 위치

- **현재 KMP 앱**: 그룹 탭의 "가입 신청중" 섹션(신청 건이 있을 때만 노출, 인라인 신청 취소).
  데이터는 `GET /api/groups/join-requests/mine`. 그룹 찾기 화면 카드/다이얼로그에서도
  PENDING 상태 표시 + 신청 취소 가능.
- **웹**: 전용 목록 없음 — 그룹 찾기 카드의 "신청 대기" 배지로만 보임. 백엔드
  `join-requests/mine` 엔드포인트는 웹 미소비(추후 웹 패리티 과제).
- **레거시**: 그룹 화면 상단 두 번째 버튼 → 전용 목록 화면 → 그룹정보 다이얼로그에서 신청 취소.

## 설계

레거시의 `찾기 → 신청중 → 만들기` 우선순위를 계승하되, 신청중은 별도 화면 이동 없이
액션 타일 바로 아래 인라인 섹션으로 유지한다(현행 구조가 레거시보다 나은 부분).

### 1. 진입 스트립 (GroupActionsStrip → GroupActionSegment)

- 아웃라인 버튼 2개 → **linen 풀폭 플랫 스트립**(카카오그룹 상단 액션 바 레퍼런스,
  사용자가 카드 타일 1차안을 반려하고 이 형식을 지정). 레거시 GroupFragment의 상단
  BottomNavigationView 배치와 동일 계보.
- 상단바(SgTopBar) **바로 아래 고정** — 그리드 아이템이 아니라 스크롤과 무관하게 붙박이.
  콘텐츠 패딩 밖 풀블리드, 하단 1dp `stoneBorder` 헤어라인으로 paper 콘텐츠와 구분.
- 세그먼트: 균등 분할(weight 1f), **아이콘(24dp, `accent`) 위 + 볼드 ink 라벨 아래** 세로
  배치, 사이는 세로 1dp `stoneBorder` 헤어라인. 순서는 레거시대로
  **그룹 찾기(돋보기) → 그룹 만들기(플러스)**.

### 2. 가입 신청중 섹션 (PendingGroupsSection)

- 맨몸 텍스트 나열 → **SgCard 컨테이너**로 감싼다.
- 헤더: "가입 신청중" + **건수 칩**(accent2 on accent2Soft).
- 행: 섹션 헤더가 상태를 말하므로 행마다 붙던 "신청중" 배지는 **제거**. 대신 그룹 찾기
  목록 행과 동일한 `멤버 N명 · 승인제` 서브타이틀 추가(joinTypeLabel 공유 —
  DiscoverGroupsScreen의 것을 internal로 승격). 신청 취소(rust)·단건 처리 스피너 유지.

### 변경 없음

- GroupsViewModel/도메인/API — UI만 변경.
- 섹션 노출 조건(신청 건 있을 때만), 낙관적 취소, 에러 문구.

## 구현 파일

- `composeApp/.../ui/screens/group/GroupsScreen.kt` — 타일·섹션 개편
- `composeApp/.../ui/screens/group/DiscoverGroupsScreen.kt` — `joinTypeLabel` private → internal
- `iosApp/iosApp/UI/Screens/Group/GroupsView.swift` — 1:1 미러(SGCard 재사용)

## 검증

- `:composeApp:compileKotlinJvm` / `:composeApp:compileDebugKotlinAndroid` 통과.
- Swift는 Mac 부재로 컴파일 미검증(기존 관행).
