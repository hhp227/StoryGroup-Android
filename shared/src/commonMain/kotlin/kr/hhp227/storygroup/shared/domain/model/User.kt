package kr.hhp227.storygroup.shared.domain.model

/** 사용자 요약 — 가입 응답 등 목록/요약 맥락에서 쓰는 도메인 모델 */
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val profileImg: String? = null
)
