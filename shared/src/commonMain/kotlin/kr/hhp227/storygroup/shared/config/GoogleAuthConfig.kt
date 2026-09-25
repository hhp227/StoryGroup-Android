package kr.hhp227.storygroup.shared.config

/**
 * 구글 로그인 OAuth 클라이언트 ID(공개값). 빈 문자열인 플랫폼은 구글 버튼을 숨긴다 —
 * GCP 콘솔(application-bb416)에서 클라이언트를 만든 뒤 채운다(설계 2026-09-25 §7).
 * Android는 WEB_CLIENT_ID를 serverClientId로 쓴다(ID 토큰 aud=웹).
 */
object GoogleAuthConfig {
    const val WEB_CLIENT_ID = "476947981226-8p3os2079vk0uueh9v70lgr8cdvi32i0.apps.googleusercontent.com"
    const val DESKTOP_CLIENT_ID = "476947981226-stj1pv66nvnu239895klmi8jqql2vsqu.apps.googleusercontent.com"
    const val IOS_CLIENT_ID = "476947981226-secvodgec6d81r3a92incikmv8hsukhf.apps.googleusercontent.com"
}
