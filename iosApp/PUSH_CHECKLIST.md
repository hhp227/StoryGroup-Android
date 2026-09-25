# 푸시 알림 Mac 인수 체크리스트 (2026-08-28)

Windows(WSL)에서는 Swift 컴파일이 불가해 iOS 푸시는 **정적 작성 + pbxproj 등록**까지만 끝났다.
아래는 Mac/Xcode에서 반드시 확인해야 하는 항목이다. 위에서부터 순서대로 진행한다.

## 0. 사전 준비 (Apple/Firebase 콘솔)

- [ ] Apple Developer(유료 멤버십)에서 **APNs 인증 키(.p8)** 발급 → Key ID·Team ID 기록
- [ ] Firebase 콘솔 > 프로젝트 설정 > 클라우드 메시징 > Apple 앱 구성에 .p8 업로드 (설계 §0)
- [ ] Firebase에 iOS 앱이 번들 ID `com.hhp227.Application`로 등록돼 있는지 확인
      (현재 `iosApp/iosApp/GoogleService-Info.plist`의 BUNDLE_ID가 이 값이다)

## 1. 프로젝트 파일 등록 (Xcode에서만 가능한 것)

- [ ] **`GoogleService-Info.plist`를 Xcode로 프로젝트에 추가하고 타깃 멤버십을 체크한다.**
      파일은 이미 `iosApp/iosApp/GoogleService-Info.plist`에 있지만 **pbxproj에는 일부러 등록하지 않았다**
      (git 미추적 파일이라 미리 등록하면 이 파일이 없는 체크아웃에서 빌드가 깨진다).
      Xcode > iosApp 그룹에 Add Files… > Copy items 해제 > Target `iosApp` 체크.
      ⚠️ 이걸 빼먹으면 `FirebaseApp.configure()`가 앱 시작 즉시 크래시한다.
- [ ] Signing & Capabilities 탭에서 **Push Notifications** capability가 보이는지 확인
      (`iosApp/iosApp.entitlements`의 `aps-environment`를 Xcode가 인식하면 자동 표시된다)
- [ ] Swift Package 해석: `firebase-ios-sdk` 11.x가 받아지고 `FirebaseMessaging`이 타깃에 링크됐는지 확인
      (File > Packages > Resolve Package Versions). Package.resolved가 새로 생기면 커밋 대상이다.
- [ ] `import FirebaseCore`가 실패하면 `FirebaseCore` product도 타깃에 추가한다
      (보통은 FirebaseMessaging이 전이 의존으로 끌어온다)

## 2. 컴파일 확인 — 브리지 표기가 불확실한 지점

- [ ] `Push/PushRegistrar.swift`의 **`PushPlatform.ios`** — Kotlin `enum class PushPlatform { ANDROID, IOS, WEB }`의
      ObjC 브리지 케이스명. 코틀린/네이티브는 보통 camelCase(`ios`)로 내보내지만 실제 이름을 확인해 맞춘다.
- [ ] `Push/PushRegistrar.swift`의 **`try await ...invoke(token:platform:)` / `invoke(token:)`** —
      `@Throws suspend operator fun invoke`의 async 노출 형태. 기존 `loginUseCase.invoke(email:password:)`와
      같은 관례를 따랐으나 completionHandler 형태로만 노출되면 그쪽으로 바꾼다.
- [ ] `UI/Shell/MainShellView.swift`의 `.onReceive(PendingPushRoute.shared.$route)` 두 블록이
      Combine 연산자 없이 컴파일되는지 확인(별도 `import Combine` 없이 작성했다).

## 3. 실기기 E2E (시뮬레이터로는 APNs 수신 불가)

- [ ] 첫 실행: 알림 권한 프롬프트가 뜨고, 허용하면 FCM 토큰이 서버에 등록된다
      (미로그인 중 발급된 토큰은 401로 실패하고, 로그인 진입 시 `MainShellView.task`가 재등록한다)
- [ ] **백그라운드 수신**: 앱을 백그라운드로 보낸 뒤 채팅/알림 발생 → 배너 표시
- [ ] **종료 상태 수신**: 앱을 완전히 종료한 뒤 발생 → 배너 표시
- [ ] **탭 라우팅**: 채팅 알림 탭 → 해당 채팅방 진입 / 게시글 알림 탭 → 게시글 상세 진입
      (콜드 스타트에서 로그인 화면을 거쳐도 로그인 직후 목적지로 이동해야 한다)
- [ ] **폴백**: 라우팅 정보 없는 알림 탭 → 알림 탭으로 전환(설계 §9)
- [ ] **포그라운드 억제**: 앱 사용 중에는 배너가 뜨지 않는다(인앱 STOMP가 표시 담당, 설계 §7)
- [ ] **로그아웃**: 로그아웃 후에는 이 기기로 푸시가 오지 않는다(토큰 해제)

## 4. 배포 전

- [ ] TestFlight/App Store 빌드는 `iosApp/iosApp.entitlements`의 `aps-environment`를
      `development` → `production`으로 바꾸거나 구성별로 분리한다

## 5. 알림 종류별 푸시 on/off (2026-08-29 추가)
1. 앱 설정(SGSettingsView) 하단 "알림" 섹션 — 토글 2개가 서버 값으로 로드되는지(웹 /settings/notifications와 같은 값, 계정 단위).
2. 토글 → 즉시 반영. 비행기 모드에서 토글 → 이전 값으로 롤백 + 빨간 문구 "알림 설정을 저장하지 못했습니다."
3. 브리지 확인: `GetPushPreferencesUseCase.invoke()` / `UpdatePushPreferencesUseCase.invoke(chatEnabled:activityEnabled:)`가 Shared에 그대로 노출되는지(Kotlin Boolean ↔ Swift Bool). `PushPreferences(chatEnabled:activityEnabled:)` 생성자도 확인.
4. `AppSettingsViewModel`은 SGSettingsView.swift 안에 동거(pbxproj 무수정 결정). 분리하려면 Xcode에서 새 파일 추가로 등록.
5. 채팅 알림 OFF 상태에서 실기기 DM 수신 → 배너 없음, 앱 내 채팅 뱃지는 갱신.
