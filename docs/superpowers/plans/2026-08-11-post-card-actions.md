# 게시글 카드 하단 액션 바 (좋아요·댓글·공유) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈(라운지)·그룹 상세 피드의 게시글 카드에 레거시식 하단 액션 바(좋아요 토글+수 / 댓글+수 / 공유)를 추가한다.

**Architecture:** 백엔드 피드 응답에 `likeCount`/`replyCount`/`likedByMe`를 additive로 추가하고, shared `Post`에 기본값 필드로 태워, 카드의 좋아요 토글은 기존 `postUpdates` SharedFlow "스냅샷 한 항목 교체" 규약으로 목록에 반영한다(서버 확정 방식, 낙관적 업데이트 없음). 공유는 Compose expect/actual(Android 시트/Desktop 클립보드)과 iOS `UIActivityViewController`.

**Tech Stack:** Spring Boot 2 + MyBatis(PG) / KMP shared(Ktor+kotlinx.serialization+app.cash.paging) / Compose Multiplatform(M2) / SwiftUI(iOS 15.0)

**Spec:** `docs/superpowers/specs/2026-08-11-post-card-actions-design.md`

## Global Constraints

- 리포 2개: 백엔드 `/mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-WebApp`(브랜치 `develop`), KMP `/mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android`(브랜치 `feature/postui`).
- **커밋·push 금지 — 스테이징까지만.** 각 태스크의 "커밋" 단계는 "실수정 파일만 경로 지정 `git add`"로 대체한다. `git add -A` 금지(워킹트리 전체가 CRLF 플립 노이즈).
- 수정한 파일이 CRLF로 플립되면 스테이징 전에 LF로 정규화: `sed -i 's/\r$//' <file>` (단, 원래 CRLF인 파일은 CRLF 유지 — `git diff`로 EOL-only 변경이 없는지 확인).
- KMP 빌드는 JDK 없이는 안 됨: 모든 gradlew 앞에 `JAVA_HOME=/home/hong227/.local/jdks/jdk-17.0.19+10` 필요. gradle 출력에 파이프 금지(pipefail 함정) — 파일로 리다이렉트 후 grep.
- 백엔드는 로컬 JDK로 빌드 불가 → Cloud Build로 컴파일 검증(GCP 프로젝트 `application-bb416`, gcloud 인증돼 있음).
- Compose 아이콘은 core 세트만(`androidx.compose.material.icons.filled.*` — Favorite/FavoriteBorder/Share는 core에 있음, extended 아티팩트 추가 금지).
- iOS는 기존 파일 수정만(신규 .swift 파일 금지 → pbxproj 무변경). Swift는 이 환경에서 빌드 불가 — 문법·미러 정확성만 확보하고 미검증임을 최종 보고에 명시.
- M2 material(`androidx.compose.material.*`) 사용 — M3 import 금지.
- 뷰 파라미터 순서는 Compose↔iOS 1:1 규칙 준수.
- 아래에서 `<scratchpad>` = `/tmp/claude-1000/-mnt-c-Users-hong2-IntelliJIDEAProjects-StoryGroup/51d6a51d-f8da-4f32-bd60-fe1a546912eb/scratchpad` (세션 스크래치 디렉터리 — 로그·임시 설정 파일 전용, 리포에 넣지 않는다).

---

### Task 1: 백엔드 — 피드 쿼리·응답에 카운트 3필드

**Files:**
- Modify: `StoryGroup-WebApp/src/main/kotlin/kr/hhp227/groupsns_webapp/post/PostMapper.kt` (쿼리 3개: `findFeedRowById` ~L42, `findFeedByGroup` ~L55, `findNotices` ~L75)
- Modify: `StoryGroup-WebApp/src/main/kotlin/kr/hhp227/groupsns_webapp/post/Post.kt` (`PostFeedRow` L29-38)
- Modify: `StoryGroup-WebApp/src/main/kotlin/kr/hhp227/groupsns_webapp/post/dto/PostDtos.kt` (`PostResponse` L90-118)

**Interfaces:**
- Produces: `PostResponse`에 `likeCount: Int`, `replyCount: Int`, `likedByMe: Boolean` — Task 2의 shared DTO가 이 이름 그대로 소비.

- [ ] **Step 1: PostFeedRow에 3필드 추가**

```kotlin
data class PostFeedRow(
    val id: Long,
    val groupId: Long,
    val userId: Long,
    val authorName: String,
    val authorProfileImg: String?,
    val text: String,
    val isNotice: Boolean,
    val createdAt: OffsetDateTime,
    val likeCount: Int,
    val replyCount: Int,
    val likedByMe: Boolean
)
```

- [ ] **Step 2: 쿼리 3개에 컬럼 3개 추가**

`PostFeedRow`는 생성자 매핑이라 이 행을 반환하는 쿼리 셋 다 컬럼을 채워야 한다(하나라도 빠지면 매핑 실패). 각 쿼리의 SELECT 목록 `p.created_at` 뒤에 다음을 삽입(각 쿼리에 `#{viewerId}` 파라미터가 이미 있음):

```sql
       (SELECT COUNT(*)::int FROM post_likes pl WHERE pl.post_id = p.id) AS like_count,
       (SELECT COUNT(*)::int FROM replys r
          JOIN user_replys ur ON ur.reply_id = r.id
         WHERE ur.post_id = p.id AND r.deleted_at IS NULL
           AND NOT EXISTS (SELECT 1 FROM user_blocks ub2
                           WHERE ub2.blocker_id = #{viewerId} AND ub2.blocked_id = r.user_id)
       ) AS reply_count,
       EXISTS(SELECT 1 FROM post_likes plm
              WHERE plm.post_id = p.id AND plm.user_id = #{viewerId}) AS liked_by_me
```

주의: `COUNT(*)::int` 캐스트 필수(PG COUNT는 bigint → Int 필드 매핑 깨짐). `like_count`에는 차단 필터를 넣지 않는다(상세의 좋아요 목록 `LikeMapper.findFeedByPost`가 안 거르므로 일치 원칙). `reply_count`는 댓글 목록 쿼리(`CommentMapper.findFeedByPost`)와 동일 조건(soft delete 제외+차단 제외).

- [ ] **Step 3: PostResponse에 3필드 추가 + from() 전달**

```kotlin
data class PostResponse(
    // ...기존 필드 유지...
    val isNotice: Boolean,
    val createdAt: OffsetDateTime,
    val likeCount: Int,
    val replyCount: Int,
    val likedByMe: Boolean
) {
    companion object {
        fun from(row: PostFeedRow, attachments: List<Image>) = PostResponse(
            // ...기존 매핑 유지...
            likeCount = row.likeCount,
            replyCount = row.replyCount,
            likedByMe = row.likedByMe
        )
    }
}
```

- [ ] **Step 4: PostFeedRow 반환 쿼리가 3개뿐인지 재확인**

Run: `grep -c "PostFeedRow" src/main/kotlin/kr/hhp227/groupsns_webapp/post/PostMapper.kt`
Expected: 반환 타입으로 쓰인 곳 3곳(findFeedRowById/findFeedByGroup/findNotices) 전부 컬럼 추가됐는지 눈으로 대조. 다른 파일에서 PostFeedRow를 반환하는 쿼리가 없는지 `grep -rn "PostFeedRow" src/main --include="*.kt"`로 확인.

- [ ] **Step 5: Cloud Build 컴파일 검증**

스크래치에 설정 파일 작성(`/tmp/claude-1000/.../scratchpad/compile-check.yaml`):

```yaml
steps:
  - name: 'eclipse-temurin:11-jdk'
    entrypoint: bash
    args: ['-c', "sed -i 's/\\r$//' gradlew && chmod +x gradlew && ./gradlew compileKotlin --no-daemon"]
timeout: 600s
```

Run: `cd StoryGroup-WebApp && gcloud builds submit . --config=<scratchpad>/compile-check.yaml --project application-bb416`
Expected: `STATUS: SUCCESS` (실패 시 로그의 Kotlin 컴파일 에러 수정 후 재실행)

- [ ] **Step 6: 스테이징**

```bash
cd StoryGroup-WebApp
git diff --stat   # 3파일만 변경됐는지, EOL-only 변경 없는지 확인
git add src/main/kotlin/kr/hhp227/groupsns_webapp/post/PostMapper.kt \
        src/main/kotlin/kr/hhp227/groupsns_webapp/post/Post.kt \
        src/main/kotlin/kr/hhp227/groupsns_webapp/post/dto/PostDtos.kt
```

---

### Task 2: shared — DTO/도메인 모델 확장 (역직렬화 테스트 선행)

**Files:**
- Create: `StoryGroup-Android/shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/PostDtosTest.kt`
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/PostDtos.kt` (`PostResponse` L34-46)
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/Post.kt`
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/PostRepositoryImpl.kt` (`toDomain` L196)

**Interfaces:**
- Consumes: Task 1의 응답 필드명 `likeCount`/`replyCount`/`likedByMe`.
- Produces: `Post.likeCount: Int`, `Post.replyCount: Int`, `Post.likedByMe: Boolean` (기본값 0/0/false) — Task 4~7의 UI가 소비.

- [ ] **Step 1: 실패하는 테스트 작성**

```kotlin
package kr.hhp227.storygroup.shared

import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.dto.PostResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class PostDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    // 카운트 미배포 서버 호환 — 필드가 없으면 기본값으로 내려앉아야 한다
    @Test
    fun decodesLegacyResponseWithoutCountFields() {
        val decoded = json.decodeFromString<PostResponse>(
            """{"id":1,"groupId":2,"userId":3,"authorName":"홍","text":"본문","createdAt":"2026-08-11T00:00:00Z"}"""
        )
        assertEquals(0, decoded.likeCount)
        assertEquals(0, decoded.replyCount)
        assertEquals(false, decoded.likedByMe)
    }

    @Test
    fun decodesCountFields() {
        val decoded = json.decodeFromString<PostResponse>(
            """{"id":1,"groupId":2,"userId":3,"authorName":"홍","text":"본문","createdAt":"2026-08-11T00:00:00Z","likeCount":5,"replyCount":2,"likedByMe":true}"""
        )
        assertEquals(5, decoded.likeCount)
        assertEquals(2, decoded.replyCount)
        assertEquals(true, decoded.likedByMe)
    }
}
```

- [ ] **Step 2: 테스트가 실패(컴파일 에러)하는지 확인**

Run: `cd StoryGroup-Android && JAVA_HOME=/home/hong227/.local/jdks/jdk-17.0.19+10 ./gradlew :shared:jvmTest --tests "kr.hhp227.storygroup.shared.PostDtosTest" > /tmp/claude-1000/.../scratchpad/t2-red.log 2>&1; tail -20 /tmp/claude-1000/.../scratchpad/t2-red.log`
Expected: FAIL — `unresolved reference: likeCount` (아직 필드가 없으므로 컴파일 에러 = red 상태). 만약 `Json` 미해결 에러가 나면 `shared/build.gradle.kts`의 commonTest 의존성에 commonMain과 같은 버전의 `kotlinx-serialization-json`을 추가.

- [ ] **Step 3: DTO·도메인 모델·매핑에 필드 추가**

`PostDtos.kt`의 `PostResponse`(L34-46) 마지막에:

```kotlin
    val createdAt: String,
    // 카운트 미배포 백엔드는 이 필드들을 안 내려주므로 기본값으로 방어(videos와 같은 규약)
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val likedByMe: Boolean = false
```

`Post.kt`:

```kotlin
    // 서버 ISO-8601(OffsetDateTime) 원문 — 표시 포맷팅은 각 플랫폼 UI가 담당
    val createdAt: String,
    // 카드 하단 액션 바용 집계 — 카운트 미배포 서버에선 0/false로 내려앉는다
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val likedByMe: Boolean = false
```

`PostRepositoryImpl.kt` L196 `toDomain()`의 인자 목록에 `likeCount = likeCount, replyCount = replyCount, likedByMe = likedByMe` 추가(기존 매핑 스타일 그대로).

- [ ] **Step 4: 테스트 통과 확인**

Run: 위 Step 2와 같은 명령
Expected: `BUILD SUCCESSFUL`, 테스트 2개 PASS

- [ ] **Step 5: 스테이징**

```bash
cd StoryGroup-Android
git add shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/PostDtosTest.kt \
        shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/network/dto/PostDtos.kt \
        shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/Post.kt \
        shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/PostRepositoryImpl.kt
```

(PostRepositoryImpl는 Task 3에서 또 수정하므로 Task 3 후 재스테이징)

---

### Task 3: shared — setPostLiked 성공 시 목록 알림 + TogglePostLikeUseCase

**Files:**
- Modify: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/PostRepositoryImpl.kt` (`setPostLiked` L145-150)
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/TogglePostLikeUseCase.kt`

**Interfaces:**
- Produces: `TogglePostLikeUseCase.invoke(groupId: Long, postId: Long, liked: Boolean)` (`@Throws suspend`) — Task 6(Compose VM)·Task 7(Swift VM)이 소비. 성공 시 리포지토리가 `postUpdates`로 갱신된 `Post`를 흘림(기존 `ObservePostUpdatesUseCase` 경로).

- [ ] **Step 1: setPostLiked에 단건 재조회+emit 추가**

```kotlin
    override suspend fun setPostLiked(groupId: Long, postId: Long, liked: Boolean): Result<Unit> =
        runCatching {
            val path = "/api/groups/$groupId/posts/$postId/likes"

            if (liked) client.post(path) else client.delete(path)
        }.map { }.onSuccess {
            // 목록 카드가 카운트를 그리므로 갱신된 단건을 다시 읽어 그 항목만 갈아끼우게 알린다
            // (수정 반영과 같은 규약 — 재조회라 남이 그 사이 누른 것까지 반영된다).
            // 재조회 실패는 무시 — 토글 자체는 성공했고 다음 갱신 기회에 맞춰진다.
            getPost(groupId, postId).onSuccess { post -> _postUpdates.tryEmit(post) }
        }
```

- [ ] **Step 2: TogglePostLikeUseCase 신설**

```kotlin
package kr.hhp227.storygroup.shared.domain.usecase

import kr.hhp227.storygroup.shared.domain.repository.PostRepository

/**
 * 목록 카드용 좋아요 토글 — 상세용 SetPostLikedUseCase와 달리 좋아요 목록을 다시 읽지 않는다.
 * 성공 시 리포지토리가 단건을 재조회해 postUpdates로 알리므로 목록은 그 알림으로 갱신된다.
 */
class TogglePostLikeUseCase(private val postRepository: PostRepository) {
    @Throws(Exception::class)
    suspend operator fun invoke(groupId: Long, postId: Long, liked: Boolean) {
        postRepository.setPostLiked(groupId, postId, liked).getOrThrow()
    }
}
```

- [ ] **Step 3: 컴파일+테스트 확인**

Run: `JAVA_HOME=/home/hong227/.local/jdks/jdk-17.0.19+10 ./gradlew :shared:jvmTest > <scratchpad>/t3.log 2>&1; tail -5 <scratchpad>/t3.log`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 스테이징**

```bash
git add shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/PostRepositoryImpl.kt \
        shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/TogglePostLikeUseCase.kt
```

---

### Task 4: Compose — 공유 유틸 expect/actual

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/util/Share.kt`
- Create: `composeApp/src/androidMain/kotlin/kr/hhp227/storygroup/ui/util/Share.android.kt`
- Create: `composeApp/src/jvmMain/kotlin/kr/hhp227/storygroup/ui/util/Share.jvm.kt`

**Interfaces:**
- Produces: `@Composable rememberShareLauncher(): (String) -> Unit`, `postShareText(post: Post): String` — Task 6이 소비.

- [ ] **Step 1: commonMain expect + 공유 본문 헬퍼**

```kotlin
package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable
import kr.hhp227.storygroup.shared.domain.model.Post

/**
 * 게시글 텍스트 공유 런처 — Android는 ACTION_SEND 공유 시트, Desktop은 클립보드 복사+피드백.
 * 폐쇄형 그룹이라 외부 공개 URL이 없어 텍스트만 내보낸다(FilePicker처럼 서드파티 의존성 없음).
 */
@Composable
expect fun rememberShareLauncher(): (String) -> Unit

/** 공유 본문 — "작성자 — 본문", 본문 없는 첨부 전용 글은 첫 첨부 URL로 대체(서버가 본문/첨부 중 하나를 강제) */
fun postShareText(post: Post): String {
    val body = post.text.ifBlank { (post.imageUrls + post.videoUrls).firstOrNull().orEmpty() }
    return "${post.authorName} — $body"
}
```

- [ ] **Step 2: androidMain actual (공유 시트)**

```kotlin
package kr.hhp227.storygroup.ui.util

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberShareLauncher(): (String) -> Unit {
    val context = LocalContext.current
    return { text ->
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, null))
    }
}
```

- [ ] **Step 3: jvmMain actual (클립보드+1.5초 피드백)**

```kotlin
package kr.hhp227.storygroup.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import kr.hhp227.storygroup.ui.theme.SgTheme

/** Desktop엔 공유 시트가 없어 클립보드 복사로 대신하고, 잠깐 뜨는 팝업으로 복사됐음을 알린다 */
@Composable
actual fun rememberShareLauncher(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    var copiedTick by remember { mutableStateOf(0) }

    if (copiedTick > 0) {
        val sg = SgTheme.colors

        Popup(alignment = Alignment.BottomCenter) {
            Text(
                "클립보드에 복사됨",
                style = SgTheme.typography.bodySmall,
                color = sg.paper,
                modifier = Modifier
                    .padding(24.dp)
                    .background(sg.ink, SgTheme.shapes.button)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        LaunchedEffect(copiedTick) {
            delay(1500)
            copiedTick = 0
        }
    }
    return { text ->
        clipboard.setText(AnnotatedString(text))
        copiedTick++
    }
}
```

주의: `SgTheme.colors`의 실제 프로퍼티명(paper 등)은 테마 파일에서 확인 후 맞출 것(`sg.ink`/`sg.paper`가 없으면 유사 토큰으로 대체 — 스타일 문제로 태스크를 막지 않는다).

- [ ] **Step 4: 양 타깃 컴파일 확인**

Run: `JAVA_HOME=/home/hong227/.local/jdks/jdk-17.0.19+10 ./gradlew :composeApp:compileKotlinJvm :composeApp:compileDebugKotlinAndroid > <scratchpad>/t4.log 2>&1; tail -5 <scratchpad>/t4.log`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/util/Share.kt \
        composeApp/src/androidMain/kotlin/kr/hhp227/storygroup/ui/util/Share.android.kt \
        composeApp/src/jvmMain/kotlin/kr/hhp227/storygroup/ui/util/Share.jvm.kt
```

---

### Task 5: Compose — SgPostCard 하단 액션 바

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/components/SgPostCard.kt`

**Interfaces:**
- Consumes: Task 2의 `Post.likeCount`/`replyCount`/`likedByMe`.
- Produces: `SgPostCard(post, modifier, onToggleLike = {}, onShare = {}, onClick = {})` — 새 파라미터는 `onClick` 앞에 두어 기존 트레일링 람다 호출부(`SgPostCard(post, Modifier...) { ... }`)가 무수정으로 컴파일되게 한다. Task 6이 소비.

- [ ] **Step 1: 시그니처 확장 + 본문을 안쪽 Column으로 감싸고 하단 바 추가**

카드 전폭 구분선을 위해 기존 `Column(Modifier.padding(16.dp))`을 바깥 `Column` 안에 넣고, 패딩은 콘텐츠 쪽에만 남긴다:

```kotlin
@Composable
fun SgPostCard(
    post: Post,
    modifier: Modifier = Modifier,
    onToggleLike: () -> Unit = {},
    onShare: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val sg = SgTheme.colors

    SgCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column {
            Column(Modifier.padding(16.dp)) {
                // ...기존 헤더/본문/첨부 블록 그대로 이동(내용 무수정)...
            }
            // 레거시 item_post.xml 미러 — 전폭 구분선 + 등분 3버튼(좋아요/댓글/공유)
            Divider(color = sg.stoneBorder)
            Row {
                SgPostCardAction(
                    label = if (post.likeCount > 0) "좋아요 ${post.likeCount}" else "좋아요",
                    icon = if (post.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    iconDescription = if (post.likedByMe) "좋아요 취소" else "좋아요",
                    tint = if (post.likedByMe) sg.accent else sg.inkSoft,
                    onClick = onToggleLike,
                    modifier = Modifier.weight(1f)
                )
                SgPostCardAction(
                    label = if (post.replyCount > 0) "댓글 ${post.replyCount}" else "댓글",
                    icon = null,
                    iconDescription = null,
                    tint = sg.inkSoft,
                    onClick = onClick,
                    modifier = Modifier.weight(1f)
                )
                SgPostCardAction(
                    label = "공유",
                    icon = Icons.Default.Share,
                    iconDescription = "공유",
                    tint = sg.inkSoft,
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 액션 바 한 칸 — 카드 onClick 위에서 자체 clickable이 탭을 가로채므로 중복 반응이 없다 */
@Composable
private fun SgPostCardAction(
    label: String,
    icon: ImageVector?,
    iconDescription: String?,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = iconDescription, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = SgTheme.typography.bodySmall, color = tint)
    }
}
```

추가 import: `androidx.compose.foundation.clickable`, `androidx.compose.material.Divider`, `androidx.compose.material.Icon`, `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.filled.Favorite`, `androidx.compose.material.icons.filled.FavoriteBorder`, `androidx.compose.material.icons.filled.Share`, `androidx.compose.ui.graphics.Color`, `androidx.compose.ui.graphics.vector.ImageVector`.
`sg.inkSoft`가 테마에 없으면 `PostDetailScreen.kt` 좋아요 행(L445-460)이 쓰는 색 토큰과 동일하게 맞춘다.

- [ ] **Step 2: 컴파일 확인**

Run: `JAVA_HOME=/home/hong227/.local/jdks/jdk-17.0.19+10 ./gradlew :composeApp:compileKotlinJvm :composeApp:compileDebugKotlinAndroid > <scratchpad>/t5.log 2>&1; tail -5 <scratchpad>/t5.log`
Expected: `BUILD SUCCESSFUL` (기존 호출부 2곳 — HomeScreen.kt:210, GroupDetailScreen.kt:366 — 무수정으로 통과)

- [ ] **Step 3: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/components/SgPostCard.kt
```

---

### Task 6: Compose — VM 액션·화면 연결·컨테이너

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt` (L127 부근)
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/home/HomeViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/home/HomeScreen.kt` (VM 팩토리 L68-76, 카드 호출부 L210)
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt` (카드 호출부 L366)

**Interfaces:**
- Consumes: Task 3 `TogglePostLikeUseCase`, Task 4 `rememberShareLauncher`/`postShareText`, Task 5 `SgPostCard` 새 파라미터.
- Produces: `HomeViewModel.Action.ToggleLike(post)`·`DismissLikeError`, `UiState.likeError: String?` — iOS 미러(Task 7)가 이 이름을 따른다. GroupDetail도 동일.

- [ ] **Step 1: AppContainer에 유스케이스 등록**

`val setPostLikedUseCase = ...`(L127) 아래에:

```kotlin
    // 목록 카드 좋아요 토글 — 성공 반영은 postUpdates 알림이 담당
    val togglePostLikeUseCase = TogglePostLikeUseCase(postRepository)
```

import `kr.hhp227.storygroup.shared.domain.usecase.TogglePostLikeUseCase` 추가.

- [ ] **Step 2: HomeViewModel 확장**

생성자 마지막에 `private val togglePostLikeUseCase: TogglePostLikeUseCase` 추가. UiState/Action/onAction/처리 함수:

```kotlin
    data class UiState(
        val pagingData: PagingData<Post> = PagingData.empty(),
        // 카드 좋아요 실패 안내 — 서버 확정 방식이라 실패해도 되돌릴 UI 상태가 없다
        val likeError: String? = null
    )

    sealed interface Action {
        data object Refresh : Action
        data class ToggleLike(val post: Post) : Action
        data object DismissLikeError : Action
    }
```

```kotlin
    override fun onAction(action: Action) {
        when (action) {
            // 글쓰기 성공 시 발화 — 화면이 refresh()로 라운지를 다시 찾고 첫 페이지부터 다시 읽는다
            Action.Refresh -> _event.tryEmit(Event.Refresh)
            is Action.ToggleLike -> toggleLike(action.post)
            Action.DismissLikeError -> _uiState.update { it.copy(likeError = null) }
        }
    }

    /** 성공 반영은 리포지토리의 postUpdates 알림(applyPostUpdate)이 담당 — 여기선 실패만 다룬다 */
    private fun toggleLike(post: Post) {
        viewModelScope.launch {
            try {
                togglePostLikeUseCase(post.groupId, post.id, !post.likedByMe)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(likeError = e.message ?: "좋아요 처리에 실패했습니다.") }
            }
        }
    }
```

import: `kotlinx.coroutines.CancellationException`, `kotlinx.coroutines.launch`, `kr.hhp227.storygroup.shared.domain.usecase.TogglePostLikeUseCase`.

- [ ] **Step 3: HomeScreen 연결**

VM 팩토리(L68-76)에 `it.togglePostLikeUseCase` 인자 추가. 피드 목록을 그리는 컴포저블에서:

```kotlin
    val share = rememberShareLauncher()
```

카드 호출부(L210)를:

```kotlin
    SgPostCard(
        post,
        Modifier.padding(horizontal = 16.dp),
        onToggleLike = { viewModel.onAction(HomeViewModel.Action.ToggleLike(post)) },
        onShare = { share(postShareText(post)) }
    ) {
        onOpenPostDetail(post.groupId, post.id)
    }
```

likeError 표출(uiState를 이미 수집하는 위치에):

```kotlin
    uiState.likeError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.onAction(HomeViewModel.Action.DismissLikeError) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(HomeViewModel.Action.DismissLikeError) }) {
                    Text("확인", color = SgTheme.colors.accent)
                }
            }
        )
    }
```

주의: `viewModel`/`uiState`가 해당 스코프에 없으면 콜백을 파라미터로 끌어올린다(화면 구조에 따라 HomeContent→피드 컴포저블로 전달). M2 `androidx.compose.material.AlertDialog` import.

- [ ] **Step 4: GroupDetail 동일 적용**

`GroupDetailViewModel`: 생성자에 `private val togglePostLikeUseCase: TogglePostLikeUseCase`, UiState에 `likeError: String? = null`, Action에 `data class ToggleLike(val post: Post)`·`data object DismissLikeError`, Home과 동일한 `toggleLike` 함수(문구 포함 동일). `GroupDetailScreen`: VM 팩토리 인자 추가 + L366 카드 호출부를 Step 3과 같은 형태로(Action 타입만 `GroupDetailViewModel.Action.*`) + likeError AlertDialog + `rememberShareLauncher` 선언.

- [ ] **Step 5: jvm 컴파일 + Android APK 검증**

Run: `JAVA_HOME=/home/hong227/.local/jdks/jdk-17.0.19+10 ./gradlew :composeApp:compileKotlinJvm :composeApp:assembleDebug > <scratchpad>/t6.log 2>&1; tail -5 <scratchpad>/t6.log`
Expected: `BUILD SUCCESSFUL` (APK 산출)

- [ ] **Step 6: 스테이징**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/home/HomeViewModel.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/home/HomeScreen.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailViewModel.kt \
        composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt
```

---

### Task 7: iOS — SGPostCard 액션 바·공유·VM 미러

**Files:**
- Modify: `iosApp/iosApp/UI/Components/SGComponents.swift` (`SGPostCard` L264-, 파일 끝에 공유 헬퍼 추가)
- Modify: `iosApp/iosApp/DI/AppContainer.swift` (L33·L141 부근)
- Modify: `iosApp/iosApp/UI/Screens/Home/HomeViewModel.swift`
- Modify: `iosApp/iosApp/UI/Screens/Home/HomeView.swift` (VM 생성 L20-25, 카드 호출 L167-174)
- Modify: `iosApp/iosApp/UI/Screens/Group/GroupDetailViewModel.swift`
- Modify: `iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift` (카드 호출 L312-318)

**Interfaces:**
- Consumes: shared `TogglePostLikeUseCase`(Kotlin suspend → `try await ...invoke(groupId:postId:liked:)`), `Post.likeCount/replyCount`(Swift에선 `Int32`)·`likedByMe`(Bool).
- Produces: 없음(최종 소비자). Compose와 Action 이름 미러: `toggleLike(Post)`/`dismissLikeError`, `uiState.likeError`.

- [ ] **Step 1: SGComponents.swift — 공유 헬퍼 + 카드 액션 바**

파일 끝에 추가(신규 파일 금지 — pbxproj 무변경):

```swift
/// 공유 본문 — "작성자 — 본문", 본문 없는 첨부 전용 글은 첫 첨부 URL로 대체(Compose postShareText 미러)
func postShareText(_ post: Post) -> String {
    let body = post.text.isEmpty ? ((post.imageUrls.first ?? post.videoUrls.first) ?? "") : post.text
    return "\(post.authorName) — \(body)"
}

/// .sheet(item:)용 래퍼 — String은 Identifiable이 아니라서 감싼다
struct ShareItem: Identifiable {
    let id = UUID()
    let text: String
}

/// iOS 15 타깃이라 ShareLink(iOS 16+) 대신 UIActivityViewController를 그대로 띄운다
struct ActivityShareSheet: UIViewControllerRepresentable {
    let text: String

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [text], applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
```

`SGPostCard`에 프로퍼티 2개 추가(뷰 생성자 순서 1:1 규칙 — Compose가 (post, modifier, onToggleLike, onShare, onClick)이므로 iOS는 (post, onToggleLike, onShare) 순, 카드 탭은 NavigationLink 소유라 onClick 없음):

```swift
struct SGPostCard: View {
    let post: Post

    var onToggleLike: () -> Void = {}

    var onShare: () -> Void = {}
```

기존 `VStack(alignment: .leading, spacing: 12) { ... }.padding(16)` 구조를 바깥 VStack으로 감싸 전폭 구분선+버튼 행 추가(Compose Task 5 미러):

```swift
        SGCard {
            VStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 12) {
                    // ...기존 헤더/본문/첨부 그대로...
                }
                .padding(16)
                Divider().background(colors.stoneBorder)
                HStack(spacing: 0) {
                    // 레거시 item_post.xml 미러 — 등분 3버튼. NavigationLink 안이라 borderless로 탭을 분리한다
                    Button(action: onToggleLike) {
                        HStack(spacing: 4) {
                            Image(systemName: post.likedByMe ? "heart.fill" : "heart")
                                .font(.caption)
                            Text(post.likeCount > 0 ? "좋아요 \(post.likeCount)" : "좋아요")
                                .font(.caption)
                        }
                        .foregroundColor(post.likedByMe ? colors.accent : colors.inkSoft)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                    }
                    .buttonStyle(.borderless)
                    Text(post.replyCount > 0 ? "댓글 \(post.replyCount)" : "댓글")
                        .font(.caption)
                        .foregroundColor(colors.inkSoft)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                    Button(action: onShare) {
                        HStack(spacing: 4) {
                            Image(systemName: "square.and.arrow.up").font(.caption)
                            Text("공유").font(.caption)
                        }
                        .foregroundColor(colors.inkSoft)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                    }
                    .buttonStyle(.borderless)
                }
            }
        }
```

주의: 댓글 칸은 버튼이 아니라 Text(카드 전체 탭 = NavigationLink가 이미 상세로 가므로 중복 액션을 만들지 않는다 — Compose는 onClick 재사용이라 동작 동일). `colors.stoneBorder`/`colors.inkSoft`가 sgColors에 없으면 SGComponents 안 기존 사용 토큰과 동일한 것으로 대체.

- [ ] **Step 2: AppContainer.swift 등록**

`let setPostLikedUseCase: SetPostLikedUseCase`(L33) 아래에 `let togglePostLikeUseCase: TogglePostLikeUseCase`, init(L141)에 `togglePostLikeUseCase = TogglePostLikeUseCase(postRepository: postRepository)`.

- [ ] **Step 3: Swift VM 2개 미러**

HomeViewModel.swift — init 파라미터 마지막에 `togglePostLikeUseCase: TogglePostLikeUseCase` 추가(+`private let` 보관), UiState struct에 `var likeError: String? = nil`, Action enum에 `case toggleLike(Post)`·`case dismissLikeError`, onAction switch에 분기 추가:

```swift
    func onAction(_ action: Action) {
        switch action {
        case .refresh:
            event.send(.refresh)
        case .toggleLike(let post):
            toggleLike(post)
        case .dismissLikeError:
            uiState.likeError = nil
        }
    }

    /// 성공 반영은 리포지토리의 postUpdates 알림(applyPostUpdate)이 담당 — 여기선 실패만 다룬다
    private func toggleLike(_ post: Post) {
        Task { @MainActor in
            do {
                try await togglePostLikeUseCase.invoke(groupId: post.groupId, postId: post.id, liked: !post.likedByMe)
            } catch {
                uiState.likeError = error.kotlinMessage(fallback: "좋아요 처리에 실패했습니다.")
            }
        }
    }
```

GroupDetailViewModel.swift도 동일 패턴(기존 Action enum·UiState에 추가).

- [ ] **Step 4: HomeView/GroupDetailView 연결**

각 View의 VM 생성부에 `togglePostLikeUseCase: container.togglePostLikeUseCase` 인자 추가. 카드 호출부(HomeView L174, GroupDetailView L316)를:

```swift
    SGPostCard(
        post: post,
        onToggleLike: { homeViewModel.onAction(.toggleLike(post)) },
        onShare: { shareItem = ShareItem(text: postShareText(post)) }
    )
```

(GroupDetailView는 해당 화면 VM 프로퍼티명 사용 — iOS VM 프로퍼티 풀네임 규칙.) 리스트를 감싸는 컨테이너에 상태·시트·알럿 추가:

```swift
    @State private var shareItem: ShareItem?
```

```swift
    .sheet(item: $shareItem) { item in
        ActivityShareSheet(text: item.text)
    }
    .alert("좋아요 처리 실패", isPresented: Binding(
        get: { homeViewModel.uiState.likeError != nil },
        set: { if !$0 { homeViewModel.onAction(.dismissLikeError) } }
    )) {
        Button("확인", role: .cancel) {}
    } message: {
        Text(homeViewModel.uiState.likeError ?? "")
    }
```

주의: **한 뷰에 `.sheet` 2개 금지 함정** — 대상 뷰에 기존 `.sheet`가 있으면 새 `.sheet(item:)`은 다른 컨테이너(예: 리스트 스크롤 뷰)에 붙인다. GroupDetailView도 같은 형태로.

- [ ] **Step 5: 미러 정합 검토(빌드 불가 환경)**

Swift 빌드는 이 환경에서 불가 — 다음을 수동 대조: (1) Action/필드 이름이 Compose와 1:1인지, (2) `Int32` 보간(`\(post.likeCount)`)이 자연스러운지, (3) 기존 파일에만 손댔는지 `git status --short iosApp/ | grep "??"`가 빈 출력인지.
Expected: 신규 파일 없음(pbxproj 무변경), 최종 보고에 "Swift 미검증" 명시.

- [ ] **Step 6: 스테이징**

```bash
git add iosApp/iosApp/UI/Components/SGComponents.swift \
        iosApp/iosApp/DI/AppContainer.swift \
        iosApp/iosApp/UI/Screens/Home/HomeViewModel.swift \
        iosApp/iosApp/UI/Screens/Home/HomeView.swift \
        iosApp/iosApp/UI/Screens/Group/GroupDetailViewModel.swift \
        iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift
```

---

### Task 8: 최종 점검 — EOL·스테이징 정리·커밋 메시지·배포 후 E2E 절차

**Files:**
- 산출물: 두 리포의 스테이징 목록 + 커밋 메시지 2벌(텍스트로 사용자에게 전달)

- [ ] **Step 1: EOL 노이즈 점검**

두 리포에서 `git diff --stat`(unstaged)와 `git diff --cached --stat`(staged)를 비교해, 스테이징된 파일이 실수정 파일과 정확히 일치하는지·EOL-only 변경이 섞이지 않았는지 확인. 섞였으면 해당 파일 `sed -i 's/\r$//'` 후 재스테이징.

- [ ] **Step 2: 전체 빌드 최종 확인**

Run: `JAVA_HOME=/home/hong227/.local/jdks/jdk-17.0.19+10 ./gradlew :shared:jvmTest :composeApp:compileKotlinJvm :composeApp:assembleDebug > <scratchpad>/final.log 2>&1; tail -5 <scratchpad>/final.log`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 커밋 메시지 2벌 작성해 사용자에게 전달**

백엔드(StoryGroup-WebApp, develop):

```
feat: 게시글 응답에 좋아요/댓글 수·내 좋아요 여부 추가

- 피드/단건/공지 쿼리(PostFeedRow 반환 3종)에 like_count/reply_count/liked_by_me 서브쿼리
- reply_count는 댓글 목록과 동일 조건(soft delete·차단 제외), like_count는 좋아요 목록과 동일하게 무필터
- additive 변경 — 웹·기존 앱 클라이언트 호환
```

KMP(StoryGroup-Android, feature/postui):

```
feat: 게시글 카드 하단 액션 바(좋아요·댓글·공유)

- 레거시 item_post.xml 미러 — 구분선+등분 3버튼, 카운트 표시(Post.likeCount/replyCount/likedByMe, 미배포 서버 기본값 방어)
- 카드 좋아요 토글: TogglePostLikeUseCase + setPostLiked 성공 시 단건 재조회→postUpdates 스냅샷 교체(상세 좋아요도 목록 동기화)
- 공유: Android ACTION_SEND/Desktop 클립보드(expect/actual)/iOS UIActivityViewController, 본문 텍스트("작성자 — 본문")
- jvm+Android APK 검증, Swift 미검증
```

- [ ] **Step 4: 배포 후 E2E 절차 문서화(사용자가 push·배포한 뒤 실행)**

```bash
BASE=https://storygroup-k4cgcgz2ya-du.a.run.app
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"<QA계정>","password":"<비밀번호>"}' | jq -r .accessToken)
# 1) 목록에 새 필드 존재 확인
curl -s "$BASE/api/groups/<groupId>/posts?page=0&size=1" -H "Authorization: Bearer $TOKEN" | jq '.[0] | {likeCount, replyCount, likedByMe}'
# 2) 좋아요 후 likeCount+1·likedByMe=true 확인
curl -s -X POST "$BASE/api/groups/<groupId>/posts/<postId>/likes" -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/api/groups/<groupId>/posts/<postId>" -H "Authorization: Bearer $TOKEN" | jq '{likeCount, likedByMe}'
# 3) 좋아요 취소 후 원복 확인
curl -s -X DELETE "$BASE/api/groups/<groupId>/posts/<postId>/likes" -H "Authorization: Bearer $TOKEN"
curl -s "$BASE/api/groups/<groupId>/posts/<postId>" -H "Authorization: Bearer $TOKEN" | jq '{likeCount, likedByMe}'
```

Expected: 필드 존재, +1/true → 원복. 클라이언트는 기본값 역호환이라 서버·클라 배포 순서 제약 없음.
