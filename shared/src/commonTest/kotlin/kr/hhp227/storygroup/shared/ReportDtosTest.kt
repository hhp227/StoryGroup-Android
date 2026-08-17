package kr.hhp227.storygroup.shared

import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.dto.PostReportResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReportDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    // 대기중 행 — reason/processedAt은 null일 수 있다(웹 PostReport 계약)
    @Test
    fun decodesPendingReport() {
        val decoded = json.decodeFromString<PostReportResponse>(
            """{"id":1,"postId":10,"postTextPreview":"본문","postAuthorId":2,"postAuthorName":"홍",
               "reporterId":3,"reporterName":"김","reason":null,"status":"PENDING",
               "createdAt":"2026-08-16T00:00:00Z","processedAt":null}"""
        )
        assertEquals("PENDING", decoded.status)
        assertNull(decoded.reason)
        assertNull(decoded.processedAt)
    }

    @Test
    fun decodesProcessedReportWithReason() {
        val decoded = json.decodeFromString<PostReportResponse>(
            """{"id":1,"postId":10,"postTextPreview":"","postAuthorId":2,"postAuthorName":"홍",
               "reporterId":3,"reporterName":"김","reason":"광고 게시글","status":"RESOLVED",
               "createdAt":"2026-08-16T00:00:00Z","processedAt":"2026-08-16T01:00:00Z"}"""
        )
        assertEquals("RESOLVED", decoded.status)
        assertEquals("광고 게시글", decoded.reason)
        // 본문 없이 첨부만 있는 게시글 — 빈 미리보기는 화면이 대체 문구로 그린다
        assertEquals("", decoded.postTextPreview)
    }
}
