# 그룹 설정 탭 메뉴 구조 전환 설계 (2026-08-13)

## 배경·문제

그룹 상세의 설정 탭이 진입하자마자 OWNER용 수정 폼(이름/설명/대표 이미지/가입 방식)과
위험 구역을 그대로 노출한다. 탭 하나에 입력 폼이 통째로 박혀 있어 훑어보기 어렵고,
레거시 앱(`SettingsFragment` + `item_settings.xml`)의 "섹션별 메뉴 리스트 → 탭하면 이동/다이얼로그"
구조와도 어긋난다.

**목표**: 설정 탭을 레거시 미러의 메뉴 리스트로 바꾸고, 수정 폼은 행을 탭해 들어가는
별도 화면으로 분리한다. 서버·shared 모듈 수정 없음.

## 사용자 결정 사항

1. **메뉴 범위**: 레거시 풀 미러 3섹션(유저 설정 / 그룹 설정 / 어플리케이션 정보).
2. **수정 폼 진입**: 별도 화면 push(계정 설정 화면과 같은 패턴).
3. **삭제/나가기 확인**: 확인 다이얼로그(레거시 AlertDialog 미러) — 기존 인라인 2단계 버튼 폐기.
4. **선행 작업 처리**: feature/groupschedulesettings에 스테이징만 된 일정·설정 탭 57파일 위에
   이어서 작업하고 같이 재스테이징해 합친다(사용자 선택).

## 화면 설계

### 설정 탭 (GroupSettingsTab — 메뉴 리스트로 전면 교체)

레거시 `item_settings.xml`의 3섹션 미러. 행/섹션 타이틀 스타일은 프로필 탭의
`ProfileMenuRow`·`SgSectionTitle` 관용구를 재사용한다.

| 섹션 | 행 | 노출 조건 | 동작 |
|---|---|---|---|
| 유저 설정 | 내 프로필(아바타+이름+이메일) | 항상 | 계정 설정 화면 push(기존 재사용) |
| 그룹 설정 | 그룹 정보 수정 › | OWNER | 그룹 정보 수정 화면 push(신규) |
| | 그룹 삭제(빨간 텍스트) | OWNER · 비라운지 | 확인 다이얼로그 → 삭제 실행 |
| | 그룹 나가기(빨간 텍스트) | 비OWNER · 비라운지 | 확인 다이얼로그 → 나가기 실행 |
| 어플리케이션 정보 | 앱 설정 › | 항상 | 앱 설정(테마) 화면 push |
| | 공유하기 | 항상 | 시스템 공유 시트 — `"StoryGroup — 그룹과 함께하는 이야기\n{웹URL}"` |
| | 개인정보처리방침 | 항상 | 외부 브라우저로 `{웹URL}/privacy` |

- 라운지 처리(웹 미러 유지): OWNER는 그룹 설정 섹션에 "그룹 정보 수정"만,
  비OWNER는 그룹 설정 섹션 자체를 숨긴다. 유저 설정·어플리케이션 정보 섹션은 항상
  있으므로 기존 라운지용 `SgEmptyState`는 제거한다.
- 웹 URL은 `StoryGroupApi.DEFAULT_BASE_URL`(`https://storygroup-k4cgcgz2ya-du.a.run.app`)을
  그대로 쓴다(웹과 API가 같은 서비스).
- 레거시의 공지사항/피드백/앱스토어/버전정보 행은 KMP에 대응 화면·스토어 등록이 없어 **제외**.
- 로딩/에러: 그룹 정보를 아직 못 읽었을 때의 기존 로딩 스피너·재시도 처리는 유지한다.

### 그룹 정보 수정 화면 (신규)

- 기존 인라인 폼을 그대로 옮긴 전체 화면: 이름/설명/대표 이미지 피커/가입 방식(비라운지)/저장.
- 위험 구역은 두지 않는다 — 삭제·나가기는 탭의 행이 담당.
- 저장 성공 시: pop 하고 그룹 상세를 refresh(`POST_CREATED_KEY` savedStateHandle 결과 패턴
  미러, 새 키 `GROUP_UPDATED_KEY`) + 그룹 목록 refresh 신호(`groupsRefreshPending`)로
  이름/커버 변경을 목록에도 반영.
- Compose: `GroupEditRoute(groupId)` — `AccountSettingsRoute`와 같은 쉘 위 풀스크린,
  `SgTopBar`+뒤로, 시스템 내비바 인셋 직접 소화.
- iOS: `GroupEditView` + `GroupEditViewModel` 신규 파일(→ pbxproj 등록 필요),
  GroupDetailView에서 push. 저장 성공은 onSaved 클로저로 pop+상세 refresh.

### 확인 다이얼로그

- "정말 삭제할까요? 게시글, 채팅, 파일이 모두 사라집니다." / "정말 나갈까요? 나가면 이
  그룹의 게시글·채팅에 더는 참여할 수 없습니다." + 확인(빨강)/취소.
- Compose는 기존 `InviteDialog`/`DmConfirmDialog` 관용구, iOS는 `.alert`.
- 다이얼로그 표시 여부는 화면 로컬 상태(`rememberSaveable` / `@State`).
- 실행 중(isClosing) 표시와 실패 에러 문구는 다이얼로그 안에서 보여준다.

## ViewModel 재편 (양 플랫폼 1:1 미러)

- **GroupSettingsViewModel(탭)**: 폼 상태(name/description/image/joinType/save류) 제거.
  잔류: group 로드(권한·라운지 판정), 삭제/나가기 액션과 isClosing/closeError,
  성공 시 Closed 이벤트(기존 onGroupClosed 배선 유지).
  유저 설정 행의 내 프로필(이름/이메일/아바타)은 새 로드 없이 세션 ProfileViewModel을
  재사용한다(Compose=sessionViewModel, iOS=셸 profileViewModel 전달 — AccountSettings 선례).
- **GroupEditViewModel(신규)**: 폼 로직 이동 — `GetGroupUseCase`+`UpdateGroupUseCase`+
  `UploadImageUseCase`. 저장 성공은 Event로 화면에 알린다(Action→Event→pop 관용구).
- iOS는 같은 분리에 `init(useCase:)` 생성자 주입 규칙(컨테이너 주입 금지) 준수.
- shared 모듈·서버 수정 없음. 유스케이스는 전부 기존 것 재사용.

## 내비게이션 배선

- Compose `App.kt`: `GroupEditRoute(groupId)`·`AppSettingsRoute` composable 신규 2개.
  앱 설정은 현재 쉘 내부 상태로만 열려 그룹 상세(NavHost 오버레이) 위에 못 뜨므로
  NavHost 라우트로도 연다(`AppSettingsScreen(themeState, onBack)` 재사용 — themeState는
  SessionContent가 이미 들고 있다).
- `GroupDetailScreen` → `GroupSettingsTab`으로 콜백 3개 추가:
  onOpenGroupEdit / onOpenAccountSettings / onOpenAppSettings.
- iOS `GroupDetailView`: iOS 15 숨김 NavigationLink 폴백 관용구로 push 3종
  (GroupEditView / AccountSettingsView / SGSettingsView). `SGSettingsView`는
  `SGThemeState`가 필요하므로 셸 → GroupsView → GroupDetailView로 전달 경로를 잇는다.
- 공유하기: `rememberShareLauncher()`(Compose) / iOS 공유 시트 기존 관용구.
- 개인정보처리방침: Compose `LocalUriHandler.openUri`, iOS `UIApplication.shared.open`.

## 제외 범위

- 공지사항/피드백/앱스토어/버전정보 행(레거시 전용 — KMP 대응 화면 없음).
- 멤버 탭의 가입 신청 인박스·초대코드는 그대로 둔다(설정 탭으로 옮기지 않음).
- 광고 배너(레거시 AdView) 없음.

## 검증·커밋 절차

- Kotlin 3타깃 컴파일 검증(Android APK + desktop jvm + iosSimulatorArm64 컴파일).
  ⚠️Swift는 Mac이 없어 미검증 — 사용자 Mac 빌드 몫.
- CRLF 노이즈 규칙: 실수정 파일만 경로 스테이징, `git add -A` 금지.
- 기존 스테이징 57파일 위에 이번 수정분을 재스테이징해 합치고, 커밋·push는 사용자 몫
  (커밋 메시지 전달까지만).
