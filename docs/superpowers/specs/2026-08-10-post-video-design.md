# 게시글 동영상 — 재생·첨부 설계

- 날짜: 2026-08-10
- 브랜치: `feature/video`
- 대상: `:shared`, `:composeApp`(Android/Desktop), `iosApp`
- 서버 변경: **없음**

## 1. 문제

게시글 상세에 동영상이 나오지 않는다.

원인은 데이터가 아니라 UI다. `:shared`는 서버 `videos`를 이미 파싱해 도메인까지 올린다.

- `PostDtos.kt:43` — `PostResponse.videos: List<VideoResponse>`
- `PostRepositoryImpl.kt:186` — `videoUrls = videos.map { it.video }`
- `Post.kt:13` — `Post.videoUrls: List<String>`

그런데 이걸 그리는 곳이 없다.

- `PostDetailScreen.kt:410` — `post.imageUrls`만 그린다
- `PostDetailView.swift:246` — iOS도 `imageUrls`만
- `SgPostCard.kt:86` / `SGComponents.swift:319` — 피드 카드는 "동영상 N개" 텍스트만 (주석: "재생은 후속")

앱 작성 폼에 동영상 첨부가 없으므로, 현재 앱에 보이는 동영상은 전부 웹에서 올린 글이다.

## 2. 전제 (조사로 확인한 사실)

| 항목 | 값 | 출처 |
|------|-----|------|
| 동영상 URL | Supabase Storage 공개 HTTPS URL (`.../object/public/<bucket>/videos/user-N/<uuid>.mp4`) | `StorageService.kt:39` |
| 스트리밍 방식 | HLS 아님 — 프로그레시브 다운로드 | 위와 동일 |
| 업로드 API | `POST /api/videos` (multipart, `video/*`만, 서버 전역 20MB) | `VideoUploadController.kt` |
| `videos` 계약 | 3상태 — null=유지 / `[]`=전부 삭제 / 값=전체 교체 | `PostDtos.kt:23` |
| Android minSdk | 24 | `libs.versions.toml:4` |
| iOS 배포 타깃 | 15.0 (AVKit `VideoPlayer`는 14+) | `project.pbxproj` |
| 프로젝트 원칙 | "공유하지 않는 것(플랫폼 네이티브 유지): … **비디오 재생**, UI 전부" | `README.md:15` |
| Desktop 선례 | `RtcVideoView.jvm.kt` = 미지원 자리 표시 | 해당 파일 |

## 3. 결정 사항

| 결정 | 선택 | 근거 |
|------|------|------|
| 범위 | 상세 + 피드 카드 + 첨부 업로드 | 사용자 선택 |
| Android 재생기 | Media3 ExoPlayer (`androidMain` 한정) | `PlayerView`가 컨트롤·시크·종횡비 resize를 제공해 코드가 짧다. 내장 `VideoView`는 비율 리사이즈가 안 되고 `MediaController`가 윈도우 팝업이라 스크롤 목록에서 거친다 |
| Desktop 재생 | 미지원 자리 표시 + 브라우저 열기 | 신규 의존성 0. VLCJ는 사용자 PC에 VLC 설치 필요, JavaFX는 Compose↔Swing 레이어 문제. `RtcVideoView.jvm.kt` 선례와 결이 같다 |
| iOS 재생기 | AVKit `VideoPlayer` | OS 내장. ExoPlayer의 iOS 대응물은 AVPlayer다 |
| 재생 UX | 탭하여 인라인 재생, 동시 재생기 1개 | 한 글에 동영상 N개일 때 재생기 N개가 뜨는 걸 막는다. 웹 `VideoAttachment`와 같은 결 |
| 포스터 프레임 | **클라이언트가 첫 프레임 추출** (2026-08-10 개정) | 처음엔 만들지 않기로 했다가 "검은 칸이라 어떤 동영상인지 모르겠다"는 지적으로 뒤집었다. §12 참조 |
| 픽커 구조 | 기존 `ImagePicker`에 기본값 있는 `mode` 인자 추가 | Android `PickVisualMedia`·iOS `PHPicker` 모두 이미 이미지/동영상 필터를 네이티브로 가진다. 기본값 덕에 기존 호출부 8곳이 안 바뀐다 |

## 4. 재생 컴포넌트

expect/actual은 하나만 둔다. 공통 껍데기가 자리·▶·모서리를 그리고 실제 재생기만 플랫폼이 채운다.

```kotlin
// composeApp/commonMain/ui/components/SgVideoAttachment.kt (신규)
@Composable
fun SgVideoAttachment(url: String, isPlaying: Boolean, onPlayRequest: () -> Unit, modifier: Modifier = Modifier)
// isPlaying=false → 16:9 어두운 자리 + 원형 ▶ 오버레이 (탭 → onPlayRequest)
// isPlaying=true  → SgVideoPlayer(url)

// composeApp/commonMain/ui/components/SgVideoPlayer.kt (신규 expect)
@Composable expect fun SgVideoPlayer(url: String, modifier: Modifier = Modifier)
```

- **android actual**: `AndroidView { PlayerView }` + `remember { ExoPlayer.Builder(context).build() }`.
  `setMediaItem(MediaItem.fromUri(url))` → `prepare()` → `playWhenReady = true`.
  `RESIZE_MODE_FIT`. `DisposableEffect`의 `onDispose`에서 `player.release()`.
- **jvm actual**: 재생 불가 안내 문구 + "브라우저에서 열기" 버튼 → `java.awt.Desktop.getDesktop().browse(URI(url))`.
  `Desktop.isDesktopSupported()`가 false면 버튼을 비활성한다.
- **iOS**: `iosApp/UI/Components/SGVideoAttachment.swift` (신규) — 같은 두 상태를 SwiftUI로.
  재생 상태는 `VideoPlayer(player: AVPlayer(url:))`. ⚠️ **pbxproj 등록이 필요한 유일한 신규 Swift 파일.**

종횡비는 메타데이터가 오기 전엔 알 수 없으므로 양쪽 다 16:9 상자에 fit으로 넣는다.

자리 표시의 바탕은 첫 프레임이다(§12) — 못 읽으면 검은 바탕으로 폴백하고 ▶는 항상 얹힌다.

## 5. 화면 적용

### 상세

재생 중인 URL은 UiState가 아니라 화면 로컬 상태다. 순수 뷰 상태이고 `menuExpanded`와 같은 성격이다.

```kotlin
var playingUrl by remember { mutableStateOf<String?>(null) }   // PostDetailScreen
```
```swift
@State private var playingUrl: String? = nil                    // PostDetailView
```

`PostBody`/`postBody`에서 이미지 루프 **다음에** `post.videoUrls`를 세로로 쌓는다.
`isPlaying = (url == playingUrl)`, `onPlayRequest = { playingUrl = url }`.
다른 항목을 탭하면 이전 재생기는 컴포지션/뷰 트리에서 빠지며 해제된다 → **재생기는 항상 1개**.

### 피드 카드

`SgPostCard.kt:86`의 `Text("동영상 N개")`와 `SGComponents.swift:319`를 ▶ 자리로 교체한다.
이미지 썸네일 줄과 같은 120dp 정사각, 어두운 배경 + 중앙 ▶.
카드 안에서는 재생하지 않는다 — 카드 전체가 이미 상세 링크이므로 탭하면 상세로 간다.

## 6. 첨부 업로드

### shared 계약 확장

```kotlin
// MediaRepository / MediaRepositoryImpl
suspend fun uploadVideo(bytes: ByteArray, fileName: String, contentType: String): Result<String>  // POST /api/videos

// MediaDtos.kt
@Serializable data class UploadedVideoResponse(val url: String)

// 신규 유스케이스
class UploadVideoUseCase(private val mediaRepository: MediaRepository)

// PostRepository / PostRepositoryImpl / 유스케이스 3종에 videos 추가
createPost(groupId, text, images, videos: List<String> = emptyList())
createLoungePost(text, images, videos: List<String> = emptyList())
updatePost(groupId, postId, text, images, videos: List<String>)
```

`createPost`는 `images.ifEmpty { null }`과 같은 규칙으로 `videos.ifEmpty { null }`을 보낸다.
`updatePost`는 폼이 들고 있는 목록을 그대로 보내 **전체 교체**한다(images와 같은 규약).

### ⚠️ 수정 폼 함정

`PostRepositoryImpl.kt:94`는 지금 `videos`를 **일부러 안 보낸다**(null=유지). 앱에 동영상 폼이 없으니
앱에서 수정할 때 웹에서 올린 동영상이 지워지지 않게 한 방어다.

이번 변경으로 전체 교체가 되므로, **`CreatePostViewModel`의 수정 모드 init이 `post.videoUrls`도
폼에 채워야 한다**(`CreatePostViewModel.kt:47`은 현재 `imageUrls`만 읽는다).
안 채우면 앱에서 글을 수정하는 순간 동영상이 전부 삭제된다. Compose·Swift 양쪽 VM 모두 해당.

### 픽커

```kotlin
// ui/util/ImagePicker.kt
enum class PickerMode { Image, Video }

@Composable
expect fun rememberImagePickerLauncher(
    mode: PickerMode = PickerMode.Image,      // expect 선언에만 기본값(actual은 반복 금지)
    onPicked: (PickedImage) -> Unit
): () -> Unit
```

기본값 + `onPicked`가 마지막 파라미터라 기존 호출부 4곳(ChatRoom·CreateGroup·CreatePost·AccountSettings)이
트레일링 람다 그대로 컴파일된다.

이름(`ImagePicker` / `PickedImage` / `rememberImagePickerLauncher`)은 **바꾸지 않는다.**
`MediaPicker`가 더 정확한 이름이긴 하지만, 리네임하면 Compose 호출부 4곳 + Swift 호출부 4곳 +
`ImagePicker.swift`의 pbxproj 경로까지 함께 움직여야 한다. Mac 검증이 불가능한 환경에서
동영상 기능과 무관한 변경으로 iOS 빌드를 흔들 이유가 없다. 파일 상단 주석에 동영상도 다룬다고 적는다.

- **android**: `PickVisualMedia.VideoOnly`, contentType은 `contentResolver.getType(uri)`.
- **jvm**: `FileDialog` 확장자 필터(`mp4/mov/webm/mkv`), contentType은 확장자 매핑.
- **iOS**: `ImagePicker`에 `mode` 프로퍼티(기본 `.image`) 추가 → `config.filter = .videos`.
  동영상은 `loadObject(ofClass: UIImage.self)`로 못 읽으므로
  `loadFileRepresentation(forTypeIdentifier: UTType.movie.identifier)`로 임시 URL을 받아 `Data`로 읽는다.
  Swift 기본 인자라 iOS 호출부 4곳도 안 바뀐다.

### 폼

`CreatePostViewModel`에 `videos: List<String>`, `isUploadingVideo`, `Action.AddVideo`, `Action.RemoveVideo` 추가.
`submit`의 "본문/첨부 중 하나 필수" 검사에 videos를 포함한다.

- **개수 상한**: `MAX_VIDEOS = 2`. `MAX_IMAGES = 4`와 같은 자리에 둔다.
- **크기 가드**: 업로드 **전에** 10MB 초과를 클라가 거른다.
  문구: "동영상은 10MB까지 올릴 수 있습니다." 11MB를 올려놓고 실패를 기다리게 하지 않는다.
  서버 multipart 상한은 20MB지만 클라는 그 절반으로 조인다(사용자 지시) — 모바일 상향 대역폭에서
  20MB는 업로드가 너무 오래 걸린다. 서버보다 엄격하므로 서버 계약과 충돌하지 않는다.

화면(`CreatePostScreen` / `CreatePostView`)에는 이미지 첨부 행과 같은 모양의 동영상 첨부 행을 추가한다.
썸네일 자리엔 업로드된 URL의 첫 프레임 + ▶를 쓴다(§12).

## 7. 함정과 대응

### ⚠️ 30초 요청 타임아웃이 업로드를 죽인다

`ApiClient.kt:42`의 `requestTimeoutMillis = 30_000`은 요청 전체(바디 전송 포함)에 걸린다.
10MB를 30초에 넣으려면 상향 2.7Mbps 이상이 계속 나와야 한다 — 여기에 Cloud Run 콜드스타트까지
겹치면 모바일에선 자주 타임아웃난다(채팅 첨부는 여전히 20MB라 더 심하다).

→ `uploadVideo` 호출에만 per-request 오버라이드를 건다.

```kotlin
client.submitFormWithBinaryData(url = "/api/videos", formData = ...) {
    timeout { requestTimeoutMillis = 180_000; socketTimeoutMillis = 180_000 }
}
```

전역 30초는 그대로 둔다 — 콜드스타트 UX 픽스로 넣은 값이다.
같은 문제가 채팅 `uploadFile`(20MB)에도 잠재해 있어 같은 오버라이드를 함께 적용한다(사용자 승인 완료).

### ⚠️ iOS `Data.toKotlinByteArray()`가 바이트 루프다

`ImagePicker.swift`의 확장은 `KotlinByteArray.set(index:value:)`를 바이트마다 호출한다.
프로필 사진 수백 KB에선 드러나지 않았지만 20MB 동영상이면 2천만 번 Swift↔Kotlin 경계를 넘는다.

→ `shared/iosMain/bridge/MediaBridges.kt`(신규)에 `NSData → ByteArray` 변환을 만들어 교체한다.
`ByteArray(data.length.toInt()).usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }`.
기존 이미지 업로드 경로도 함께 빨라진다.

### 기타

- Android `INTERNET` 권한은 이미 있고 전부 HTTPS라 cleartext 설정이 필요 없다.
- Media3는 `androidMain`에만 넣는다(`jvm`/`ios` 소스셋 무관).
- ExoPlayer 인스턴스는 반드시 `onDispose`에서 `release()` — 목록에서 스크롤로 빠질 때 누수 지점.

## 8. 변경 파일

**shared (10)**
- `data/network/dto/MediaDtos.kt` — `UploadedVideoResponse`
- `domain/repository/MediaRepository.kt` — `uploadVideo`
- `data/repository/MediaRepositoryImpl.kt` — `/api/videos` + 타임아웃 오버라이드(×2)
- `domain/usecase/UploadVideoUseCase.kt` *(신규)*
- `domain/repository/PostRepository.kt` — `videos` 파라미터
- `data/repository/PostRepositoryImpl.kt` — `videos` 전송(주석 갱신)
- `domain/usecase/{CreatePost,CreateLoungePost,UpdatePost}UseCase.kt` — `videos`
- `iosMain/bridge/MediaBridges.kt` *(신규)* — `NSData → ByteArray`

**composeApp (14)**
- `ui/components/SgVideoAttachment.kt` *(신규)*
- `ui/components/SgVideoPlayer.kt` *(신규 expect)*
- `androidMain/ui/components/SgVideoPlayer.android.kt` *(신규)*
- `jvmMain/ui/components/SgVideoPlayer.jvm.kt` *(신규)*
- `ui/components/SgPostCard.kt` — ▶ 썸네일
- `ui/screens/post/PostDetailScreen.kt` — 동영상 렌더 + `playingUrl`
- `ui/screens/post/CreatePostViewModel.kt` — videos 상태·상한·크기 가드·수정 모드 로드
- `ui/screens/post/CreatePostScreen.kt` — 동영상 첨부 행
- `ui/util/ImagePicker.kt` + `androidMain/…/ImagePicker.android.kt` + `jvmMain/…/ImagePicker.jvm.kt` — `PickerMode`
- `di/AppContainer.kt` — `uploadVideoUseCase`
- `gradle/libs.versions.toml` + `composeApp/build.gradle.kts` — media3 (`androidMain` 한정)

**iosApp (8)**
- `iosApp.xcodeproj/project.pbxproj` — 신규 Swift 파일 등록(빌드파일/파일참조/그룹/Sources 4곳)
- `UI/Components/SGVideoAttachment.swift` *(신규)*
- `UI/Components/SGComponents.swift` — 피드 카드 ▶ 썸네일
- `UI/Util/ImagePicker.swift` — `mode` + 동영상 로드 + 브리지 교체
- `UI/Screens/Post/PostDetailView.swift` — 동영상 렌더 + `playingUrl`
- `UI/Screens/Post/CreatePostView.swift` — 동영상 첨부 행
- `UI/Screens/Post/CreatePostViewModel.swift` — videos 상태·수정 모드 로드
- `DI/AppContainer.swift` — `uploadVideoUseCase`

## 9. 검증

이 환경엔 WSL 쪽 JDK가 없어 `cmd.exe`로 Windows JDK17(`C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1`)을
`JAVA_HOME`에 넣고 `gradlew.bat`을 돌린다.

**통과한 것**

- `:shared:compileKotlinJvm`, `:shared:compileDebugKotlinAndroid`
- `:composeApp:compileDebugKotlinAndroid`, `:composeApp:compileKotlinJvm`
- `:composeApp:assembleDebug` — media3 AAR이 들어오면서 매니페스트 병합·dex까지 확인
- `:shared:compileKotlinIosSimulatorArm64` — **iosMain Kotlin은 Windows에서도 컴파일된다**.
  `MediaBridges.kt`의 `usePinned`+`memcpy`가 타입 체크를 통과했다.

**미검증으로 남는 것**

- `:shared:linkDebugFrameworkIosSimulatorArm64`는 macOS 전용이라 **SKIPPED** — ObjC 헤더가 생성되지 않아
  Swift에서 `MediaBridgesKt.nsDataToByteArray(data:)`로 보이는지는 확인하지 못했다.
  이 리포의 기존 선례(`PostBridgesKt.postPagingDataWithUpdate(pagingData:post:)`, `ApiClientKt.createApiClient`)와
  같은 규칙이라 이름은 맞을 것으로 본다.
- **Swift 컴파일 전체**(Xcode 없음) — iOS 화면·피커·재생기는 전부 Mac에서 첫 빌드된다.
- 런타임: Android APK 실기기/에뮬레이터에서 웹 동영상 글 재생 + 첨부 업로드,
  Desktop에서 ▶ 탭 시 브라우저가 열리는지.

## 11. 실제 구현에서 걸린 것

- **Kotlin 블록 주석은 중첩된다.** KDoc에 `video/*만`이라고 적었더니 `/*`가 안쪽 주석을 열어
  바깥 주석이 안 닫히고 파일 전체가 깨졌다("Unclosed comment"). 주석에 MIME 와일드카드를 쓰지 말 것.
- **`.sheet`를 두 개 달면 안 된다.** iOS 작성 화면에 사진·동영상 피커 시트를 각각 달면 뒤엣것이
  앞엣것을 덮어쓴다 — `.sheet(item:)` 하나에 `ActivePicker` enum으로 합쳤다.
- **워킹트리는 CRLF, 커밋은 LF.** 손댄 파일만 `sed -i 's/\r$//'`로 정규화한 뒤에야 diff가
  실제 변경분만 남는다(정규화 전엔 25줄짜리 파일이 24삽입/18삭제로 잡혔다).

## 10. 커밋

스테이징 + 커밋 메시지 전달까지만 한다. 커밋·push는 사용자가 직접 한다.
CRLF 플립 노이즈 때문에 `git add -A`를 쓰지 않고 실수정 파일만 경로로 스테이징한다.

## 12. 후속: 첫 프레임 썸네일 (2026-08-10)

"썸네일이 검은색이라 어떤 동영상인지 알아보기 힘들다"는 지적으로 §3의 "포스터 프레임 만들지 않음"을 뒤집었다.

### 왜 클라이언트에서 뽑나

| 안 | 판단 |
|----|------|
| **클라이언트 첫 프레임 추출** | **채택.** 백엔드 수정·재배포·백필 전부 0이고, 이미 올라가 있는 동영상에도 즉시 적용된다 |
| 서버 포스터 생성 | 런타임 이미지가 `eclipse-temurin:11-jre`뿐이라 ffmpeg 설치(+100~200MB)가 필요하고, `images` 테이블 컬럼 추가 + 기존 동영상 백필 + Cloud Run 재배포가 따라온다 |
| 업로더가 포스터도 업로드 | 앱·iOS·Desktop·웹 4곳에 생성 로직이 필요하고 기존 동영상은 여전히 백필해야 한다 |

전체 파일을 받지는 않는다 — Android `MediaMetadataRetriever`는 HTTP range로 헤더와 첫 프레임만,
iOS `AVAssetImageGenerator`는 필요한 구간만 스트리밍한다.

### 구현

- **Compose**: `ui/util/VideoFrame.kt`에 `@Composable expect fun rememberVideoFrame(url): ImageBitmap?`.
  android actual은 `MediaMetadataRetriever` + 24개 LRU(메인 스레드에서만 접근), 프레임은 한 변 640px로 줄인다
  (원본 1080p를 그대로 들면 8MB짜리 비트맵이다). `getScaledFrameAtTime`은 API 27+라 그 아래선 직접 축소.
  jvm actual은 항상 null.
- **iOS**: `UI/Util/VideoPoster.swift` — `VideoPosterLoader`(NSCache 24개) + `VideoPoster` 뷰.
  `appliesPreferredTrackTransform = true`로 세로 영상이 눕지 않게 하고, `maximumSize`로 디코딩 크기를 제한한다.
  캐시 히트는 `init`에서 초기값으로 넣어 스크롤 복귀 시 깜빡이지 않게 한다.
- 공통 `VideoPoster`(Compose) / `VideoPoster`(SwiftUI)가 프레임 + ▶ 뱃지를 그리고, 상세 자리 표시·피드 카드·
  작성 폼 썸네일이 전부 이걸 쓴다. 칸은 `Crop`/`scaledToFill`로 꽉 채운다(정사각 칸에 세로 영상이
  letterbox로 남으면 오히려 더 안 보인다). ▶ 뱃지는 흰 반투명에서 **검은 반투명**으로 바꿨다 —
  밝은 프레임 위에서 흰 원은 묻힌다.

### 남는 것

- **Desktop은 여전히 검은 칸 + ▶**. JVM에 동영상 디코더가 없다(재생도 브라우저로 넘기는 플랫폼이라 결이 맞는다).
- 디스크 캐시는 없다 — 앱을 다시 켜면 프레임을 한 번 더 받는다(각 몇 개의 range 요청).
- Coil의 `ImageLoader`에 Fetcher로 끼우면 디스크 캐시까지 공짜지만, 싱글턴 구성을 잘못 건드리면
  **앱 전체 이미지 로딩이 죽는데 이 환경에선 런타임 확인이 불가능**해서 독립 컴포넌트로 뺐다.
- moov 아톰이 파일 끝에 있는 MP4(faststart 아님, 폰 촬영본이 대개 그렇다)는 range 요청이 몇 번 더 오간다.

### 추가·변경 파일

- 신규: `composeApp` `ui/util/VideoFrame.kt` + `.android.kt` + `.jvm.kt`, `iosApp` `UI/Util/VideoPoster.swift`(pbxproj 0038)
- 변경: `SgVideoAttachment.kt`(포스터 도입, `SgVideoThumbnail`에 url 파라미터 추가), `SgPostCard.kt`,
  `CreatePostScreen.kt`, `SGVideoAttachment.swift`, `SGComponents.swift`, `CreatePostView.swift`, `project.pbxproj`
