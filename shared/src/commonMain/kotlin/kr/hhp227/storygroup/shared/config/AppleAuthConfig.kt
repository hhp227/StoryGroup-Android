package kr.hhp227.storygroup.shared.config

/**
 * 애플 로그인(설계 2026-09-27 §5.1). Android·Desktop이 인가 URL의 client_id로 쓰는 Services ID(공개값).
 * 비우면 두 플랫폼 버튼을 숨긴다. iOS 네이티브는 번들 ID로 동작해 이 값이 필요 없다.
 */
object AppleAuthConfig {
    const val SERVICES_ID = "com.hhp227.Application.web"
    const val CALLBACK_URL = "${AppLinks.BASE_URL}/api/auth/apple/callback"
    const val ANDROID_REDIRECT_SCHEME = "storygroup"
}
