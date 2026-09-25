# 게시글 카드 하단 액션 바 (좋아요·댓글·공유) 디자인

날짜: 2026-08-11 / 브랜치: `feature/postui` (KMP), `develop` (백엔드)

## 목표

홈(라운지)·그룹 상세 피드의 게시글 카드 하단에 레거시 `item_post.xml`처럼 액션 바를 추가한다.
버튼은 **좋아요 / 댓글 / 공유하기** 3개(사용자 확정). 좋아요·댓글에는 수를 표시하고(사용자 확정),
카드에서 좋아요를 바로 토글할 수 있게 한다. 공유는 본문 텍스트 공유(사용자 확정).

레거시 참고: `Legacy/StoryGroup-Android/app/src/main/res/layout/item_post.xml` —
구분선 + 등분 버튼 행(좋아요 하트·수는 0이면 숨김), 공유 버튼은 레거시에 없던 신규.

## 결정 사항 요약

| 항목 | 결정 |
|---|---|
| 버튼 구성 | 좋아요 + 댓글 + 공유하기 (3버튼, 각 1/3 폭) |
| 카운트 | 표시. 백엔드 목록 응답에 `likeCount`/`replyCount`/`likedByMe` 추가 |
| 좋아요 반영 | 서버 확정 방식(1안) — 낙관적 업데이트 없음, 실패 롤백 불요 |
| 공유 내용 | `"작성자 — 본문"` 텍스트. 본문 없는 첨부 전용 글은 첫 첨부 URL로 대체 |
| 공유 수단 | Android `ACTION_SEND` 시트 / iOS `UIActivityViewController` / Desktop 클립보드+피드백 |
| 댓글 버튼 | 게시글 상세로 이동(카드 탭과 동일 목적지, 입력 포커스 없음 — YAGNI) |

## 1. 백엔드 (StoryGroup-WebApp, develop)

`post/PostMapper.kt`의 `findFeedByGroup`·`findFeedRowById` 두 쿼리에 서브쿼리 3개 추가:

- `like_count`: `post_likes` 집계 — **차단 필터 없음**(상세의 좋아요 목록 쿼리
  `LikeMapper.findFeedByPost`가 차단을 거르지 않으므로, 일치 원칙상 카운트도 안 거른다)
- `reply_count`: 댓글 집계 — `replys` + `user_replys` 조인(CommentMapper의 목록 쿼리와 동일 조건,
  대댓글 포함, **차단 사용자 제외**) 기준으로 세어 상세 댓글 목록 개수와 일치시킨다
- `liked_by_me`: `post_likes`에 내(viewerId) 행 EXISTS

원칙은 "각 카운트는 상세 화면의 해당 목록 쿼리와 같은 조건으로 센다" — 댓글 목록만 차단을
거르므로 reply_count만 차단 필터가 붙는다.

`PostFeedRow`를 반환하는 쿼리는 3개(`findFeedByGroup`·`findFeedRowById`·`findNotices`) —
생성자 매핑이라 컬럼 누락 시 매핑이 깨지므로 **셋 다** 새 컬럼을 채운다(공지 목록은 값을
소비하지 않지만 컬럼은 필요).

`PostFeedRow`에 3필드 추가 → `PostResponse.from`이 그대로 실어 목록·단건·작성/수정 응답이
한 번에 확장된다. additive 변경이라 웹 프론트·기존 앱 클라이언트와 호환(모르는 필드 무시).

검증: 백엔드 리포에 실질적인 통합 테스트 스위트가 없음(contextLoads+JWT 단위 테스트뿐, DB 필요) —
Cloud Build 컴파일 검증(로컬 JDK 없음) + 배포 후 curl E2E(좋아요/댓글 후 목록 카운트 확인)로 대체.

## 2. shared (KMP)

- `PostDtos.kt`의 `PostResponse`와 `domain/model/Post.kt`에
  `likeCount: Int = 0`, `replyCount: Int = 0`, `likedByMe: Boolean = false` 추가.
  기본값 덕에 카운트 미배포 서버와도 역호환.
- `PostRepositoryImpl.setPostLiked` 성공 시 **단건 재조회(getPost) 후 `_postUpdates.tryEmit`** —
  기존 "수정 후 목록 스냅샷 교체" 규약 재사용. 부수 효과로 상세 화면에서 좋아요를 눌러도
  목록 카운트가 자동 동기화된다(현재는 동기화 없음). 재조회 실패는 무시(토글 자체는 성공 —
  다음 갱신 기회에 맞춰짐), 토글 실패는 그대로 던져 화면 onError로 간다.
- 카드용 유스케이스 `TogglePostLikeUseCase(groupId, postId, liked)` 신설 —
  기존 `SetPostLikedUseCase`는 좋아요 목록 재조회까지 하므로(상세 전용) 카드에는 과하다.
- (최종 리뷰 반영) `setPostLiked`는 POST의 409+`ALREADY_LIKED`를 성공으로 흡수(멱등) —
  연타 시 "성공했는데 에러 다이얼로그" 방지. unlike(DELETE)는 서버가 무조건 삭제라 에러 케이스 없음.
- (최종 리뷰 반영) `createComment`/`deleteComment`도 성공 시 단건 재조회+`postUpdates` emit —
  카드가 댓글 수를 그리므로 좋아요와 같은 동기화 규약을 적용(비대칭 제거).

## 3. Compose (composeApp — Android/Desktop)

- `SgPostCard`: 본문/첨부 아래 구분선 + 3버튼 가로 바.
  - 좋아요: `likedByMe`면 하트 채움+accent 색, 아니면 윤곽 하트+기본 색. 수>0이면 수 표시.
  - 댓글: 수>0이면 수 표시. 탭 = `onClick`(상세 이동)과 동일 콜백 재사용.
  - 공유: 탭 = 공유 런처 실행.
  - 새 파라미터 `onToggleLike: () -> Unit = {}`, `onShare: () -> Unit = {}` — 기본 no-op으로
    기존 호출부(미리보기 등) 보호. 버튼 자체 clickable이라 카드 onClick과 탭이 겹치지 않는다.
- `ui/util/Share.kt` 신설: `expect fun rememberShareLauncher(): (String) -> Unit`
  (`FilePicker.kt` 선례). androidMain = `Intent.ACTION_SEND` chooser,
  jvmMain = 클립보드 복사 + "복사됨" 피드백(스낵바 또는 임시 표시).
- `HomeViewModel`·`GroupDetailViewModel`에 `Action.ToggleLike(post)` 추가 — API 호출만 하고
  목록 반영은 기존 `ObservePostUpdatesUseCase` → `PagingData.map` 경로가 담당(신규 메커니즘 없음).
  실패는 기존 Event/onError 경로로 표출.

## 4. iOS (iosApp — SwiftUI 미러)

- `SGPostCard`(SGComponents.swift)에 동일 액션 바. 뷰 생성자 파라미터 순서는 Compose와 1:1 규칙 준수.
- 공유: `UIActivityViewController` 래핑(ShareLink는 iOS16+라 배제 — 구현 시 배포 타깃 재확인).
- Swift `HomeViewModel`·`GroupDetailViewModel`에 toggleLike 액션 추가(UseCase 생성자 주입 규칙),
  목록 반영은 기존 postUpdates 브리지 경로.
- 카드가 `NavigationLink` 안에 있으므로 내부 버튼은 `.buttonStyle(.borderless)` 등으로
  네비게이션 탭과 분리(구현 시 검증). 신규 Swift 파일 없이 기존 파일 수정으로 마쳐 pbxproj 무변경 목표.

## 5. 에러 처리

- 좋아요 실패: 각 화면의 기존 onError 표출(스낵바/알럿). 서버 확정 방식이라 UI 롤백 불요.
- 공유: 로컬 동작이라 실패 경로 없음. Desktop 클립보드는 완료 피드백만.
- 구서버+신클라: 카운트 필드 기본값(0/false)으로 동작 — 버튼은 보이되 수만 0. 크래시 없음.

## 6. 제외 범위

- 웹 프론트(storygroup-web) 무변경 — 새 필드는 무시된다.
- 공지 목록·앨범·검색 응답 무변경(카운트 불요).
- 상세 화면의 좋아요 목록 시트·댓글 UI 무변경.
- 딥링크/공개 URL 공유 없음(폐쇄형 그룹 — 별도 과제).

## 7. 검증·커밋 절차

- 백엔드: Cloud Build 컴파일 검증(`gcloud builds submit`, 로컬 JDK 없음) + 배포 후 curl E2E 절차 문서화.
- KMP: jvm(Desktop) 컴파일 + Android APK 빌드로 검증. Swift/Mac은 이 환경에서 미검증(관례 명시).
- 두 리포 모두 **스테이징 + 커밋 메시지 전달까지만** — 커밋·push는 사용자 직접(워크플로 규칙).
  CRLF 노이즈 방지: 실수정 파일만 경로 스테이징, 수정 파일 EOL 정규화 후 스테이징.
- 서버 배포(Cloud Run)는 커밋 후 사용자 지시에 따름 — 클라이언트는 기본값 역호환이라
  배포 순서 제약 없음(서버 먼저든 클라 먼저든 안전).
