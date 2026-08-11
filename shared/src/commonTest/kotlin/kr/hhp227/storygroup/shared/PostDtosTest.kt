package kr.hhp227.storygroup.shared

import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.dto.PostResponse
import kotlin.test.Test
import kotlin.test.assertEquals

class PostDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    // 카운트 미배포 서버 호환 — 필드가 없으면 기본값으로 내려앉아야 한다
    @Test
    fun decodesLegacyResponseWithoutCountFields() {
        val decoded = json.decodeFromString<PostResponse>(
            """{"id":1,"groupId":2,"userId":3,"authorName":"홍","text":"본문","createdAt":"2026-08-11T00:00:00Z"}"""
        )
        assertEquals(0, decoded.likeCount)
        assertEquals(0, decoded.replyCount)
        assertEquals(false, decoded.likedByMe)
    }

    @Test
    fun decodesCountFields() {
        val decoded = json.decodeFromString<PostResponse>(
            """{"id":1,"groupId":2,"userId":3,"authorName":"홍","text":"본문","createdAt":"2026-08-11T00:00:00Z","likeCount":5,"replyCount":2,"likedByMe":true}"""
        )
        assertEquals(5, decoded.likeCount)
        assertEquals(2, decoded.replyCount)
        assertEquals(true, decoded.likedByMe)
    }
}
