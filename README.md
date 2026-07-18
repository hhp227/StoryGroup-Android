# StoryGroup (KMP)

StoryGroup Android/iOS 앱이 함께 쓰는 **KMP 프로젝트**. UI는 각 플랫폼 네이티브(Android View/DataBinding, iOS SwiftUI)로 두고, 로직만 `:shared`에서 공유한다. (구 ConCafe 템플릿 베이스 — 패키지 `kr.hhp227.storygroup`으로 개편됨)

## 모듈

- **`:shared`** — 실제 공유 로직. 소비자는 레거시 앱 2개(StoryGroup-Android, StoryGroup-iOS).
  - **모델/DTO**: kotlinx.serialization (`auth/AuthDtos.kt` — StoryGroup-WebApp `/api/auth` 계약과 1:1)
  - **네트워크**: Ktor (`network/ApiClient.kt` — Bearer 자동 첨부, 401 시 `/api/auth/refresh` 자동 재발급)
  - **저장소**: `TokenStorage` 인터페이스 + 플랫폼 구현(Android `SharedPreferencesTokenStorage`, iOS `UserDefaultsTokenStorage`)
  - **Repository / ViewModel**: `AuthRepository`, `LoginViewModel`(androidx lifecycle KMP — Android에선 그대로 `ViewModel`)
- **`:composeApp`** — Compose Multiplatform 데모 쉘(Android/Desktop). 실제 제품 UI 아님 — shared 소비 검증·데스크톱 실험용.
- **`/iosApp`** — KMP 템플릿 iOS 데모 쉘. **실제 iOS 앱은 `../StoryGroup-iOS`**.

공유하지 않는 것(플랫폼 네이티브 유지): WebRTC/CallKit, 푸시(FCM/APNs), 미디어 픽커, 비디오 재생, UI 전부.

## 빌드 매트릭스

| 무엇 | 어디서 | 명령 |
|---|---|---|
| Android AAR/klib 컴파일 | Windows/WSL/Mac | `gradlew :shared:compileDebugKotlinAndroid` |
| iOS klib 컴파일 검증 | Windows/WSL/Mac | `gradlew :shared:compileKotlinIosArm64` (크로스 컴파일, 링크 없음) |
| iOS Framework/XCFramework 링크 | **Mac 전용** | `./gradlew :shared:assembleSharedXCFramework` |
| Compose 데모(Android APK) | Windows/WSL/Mac | `gradlew :composeApp:assembleDebug` |
| Compose 데모(Desktop 실행) | Windows/WSL/Mac | `gradlew :composeApp:run` |

Windows에서 실행 시 (WSL에서):

```bash
cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-main&& gradlew.bat :shared:compileDebugKotlinAndroid"
```

## Android(StoryGroup-Android) 연동 — 완료됨

`StoryGroup-Android/settings.gradle`의 `includeBuild('../StoryGroup-main')` + app의 `implementation 'kr.hhp227.storygroup:shared:1.0.0'` 조합(composite build 좌표 치환). 앱 쪽 진입점은 `com.hhp227.application.data.SharedServiceLocator`.

## iOS(StoryGroup-iOS) 연동 — Mac에서 할 일

1. **Xcode Build Phase 추가**: `Application` 타깃 > Build Phases > New Run Script Phase (Compile Sources보다 **앞**에 배치):
   ```bash
   cd "$SRCROOT/../StoryGroup-main"
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```
   (JDK 17+ 필요. Xcode가 PATH를 안 물려주면 `export JAVA_HOME=$(/usr/libexec/java_home -v 17)` 추가)
2. **Framework Search Paths**(타깃 Build Settings)에 추가:
   ```
   $(SRCROOT)/../StoryGroup-main/shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
   ```
3. **Other Linker Flags**에 `-framework Shared` 추가 (static framework라 embed 불필요).
4. Swift에서 `import Shared` 후 사용:
   ```swift
   let repo = AuthRepository(
       client: ApiClientKt.createApiClient(tokenStorage: UserDefaultsTokenStorage(), baseUrl: StoryGroupApi.shared.DEFAULT_BASE_URL),
       tokenStorage: UserDefaultsTokenStorage()
   )
   ```
5. **SKIE 적용(권장, Mac에서 첫 링크 확인 후)**: `shared/build.gradle.kts` plugins에 `id("co.touchlab.skie") version "0.10.6"` 추가 — `StateFlow`→`AsyncSequence`, sealed→enum 변환으로 SwiftUI에서 `for await state in viewModel.uiState` 형태 구독이 가능해진다. Windows 호스트 빌드에 영향 없는지 확인 후 커밋할 것.

## 메모

- iOS 토큰 저장은 MVP로 NSUserDefaults — 출시 전 Keychain 전환.
- `expiresIn`은 초 단위(백엔드 TokenResponse). 클라이언트는 만료 추적 없이 401 → refresh 흐름에 의존한다.
- 새 공유 코드는 `commonMain`에 추가하고, 플랫폼 분기가 필요할 때만 `androidMain`/`iosMain`에 구현을 둔다(expect/actual보다 인터페이스+주입 선호).
- 이 프로젝트는 `StoryGroup-Shared`(2026-07-13 부트스트랩)를 대체한다 — 인증 슬라이스는 그대로 이식됨.
