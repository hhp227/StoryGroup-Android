package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp 그룹 신고함 계약과 1:1(웹 lib/api PostReport) —
// GET /api/groups/{id}/reports?status= 응답과 PATCH /api/groups/{id}/reports/{reportId} 응답이 같은 모양

@Serializable
data class PostReportResponse(
    val id: Long,
    val postId: Long,
    val postTextPreview: String = "",
    val postAuthorId: Long,
    val postAuthorName: String,
    val reporterId: Long,
    val reporterName: String,
    val reason: String? = null,
    // 서버 wire = PENDING/RESOLVED/DISMISSED — 도메인 enum 이름과 1:1
    val status: String = "PENDING",
    val createdAt: String = "",
    val processedAt: String? = null
)

// PATCH /api/groups/{id}/reports/{reportId} 요청 본문 — RESOLVED/DISMISSED만 보낸다(재처리 가능)
@Serializable
data class ProcessReportRequest(
    val status: String
)
