# 구글 로그인 Mac 체크리스트 (2026-09-25)

설계: `docs/superpowers/specs/2026-09-25-google-oauth-login-design.md` §6. Swift는 WSL에서 컴파일 불가 — 정적 작성분이다.

## 변경 파일
- `iosApp.xcodeproj/project.pbxproj` — SPM `GoogleSignIn-iOS`(8.x, 제품 GoogleSignIn). ID 0A8D2B3B(패키지)·3C(제품)·3D(빌드 파일). **다음 신규 ID = 0A8D2B3E**
- `iosApp/iOSApp.swift` — `.onOpenURL { GIDSignIn.sharedInstance.handle($0) }`
- `iosApp/UI/Screens/Auth/LoginViewModel.swift`·`LoginView.swift` — "Google로 계속하기"(IOS_CLIENT_ID 비면 숨김)
- `iosApp/UI/Screens/Settings/AccountSettingsViewModel.swift`·`AccountSettingsView.swift` — `hasPassword` 분기(비밀번호 카드 숨김, 탈퇴 "탈퇴" 입력)

## 확인 순서
1. [x] GCP 콘솔 iOS OAuth 클라이언트 생성 — `476947981226-secvodgec6d81r3a92incikmv8hsukhf`
2. [x] `GoogleAuthConfig.IOS_CLIENT_ID` 채움 + 백엔드 `GOOGLE_CLIENT_IDS` 기본값에 포함(`3bf4cb1`, 배포 필요)
3. [x] `Info.plist`에 `CFBundleURLTypes` → `CFBundleURLSchemes`로 reversed client ID(`com.googleusercontent.apps.XXXX`) 추가 — 없으면 로그인 후 앱 복귀가 안 된다
4. [ ] Xcode: File > Packages > Resolve Package Versions — GoogleSignIn 8.x 해석, 빌드 성공
   - Swift 컴파일 확인 포인트: `LoginWithGoogleUseCase.withIdToken(idToken:)`, `DeleteAccountUseCase.invoke(password:confirmText:)`, `GoogleAuthConfig.shared.IOS_CLIENT_ID`, `Profile.hasPassword`, `kGIDSignInErrorDomain`/`GIDSignInError.canceled`
5. [ ] 실기기: 로그인 화면 "Google로 계속하기" → 계정 선택 → 홈 진입
6. [ ] 시트에서 취소 → 에러 문구 없음
7. [ ] 기존 이메일 계정과 같은 구글 계정 → 같은 사용자로 로그인(자동 연결)
8. [ ] 구글 전용 계정: 계정 설정에서 비밀번호 변경 카드 숨김, 탈퇴 칸에 "탈퇴" 입력 시에만 버튼 활성 → 탈퇴 성공 후 로그인 화면
9. [ ] 비밀번호 계정: 기존 비밀번호 변경·탈퇴 회귀 없음
10. [ ] App Store 가이드라인 4.8 — 구글 로그인 제공 시 Sign in with Apple 요구 여부 검토(후속 provider)
