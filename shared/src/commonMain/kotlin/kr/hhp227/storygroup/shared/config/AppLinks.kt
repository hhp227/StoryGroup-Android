package kr.hhp227.storygroup.shared.config

/**
 * 서비스 링크 상수 — 계층 중립(config)이라 data(ApiClient 기본값)와 UI(약관/공유 링크) 양쪽에서 참조한다.
 * UI가 data.network의 API 상수를 직접 import하던 계층 위반을 해소하며 이 오브젝트로 일원화.
 */
object AppLinks {
    const val BASE_URL = "https://storygroup-k4cgcgz2ya-du.a.run.app"
    const val TERMS_URL = "$BASE_URL/terms"
    const val PRIVACY_URL = "$BASE_URL/privacy"
}
