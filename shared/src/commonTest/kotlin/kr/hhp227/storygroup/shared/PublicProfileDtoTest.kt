package kr.hhp227.storygroup.shared

import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.dto.PublicProfileResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicProfileDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    // 백엔드 user/dto PublicProfileResponse와 1:1 — 전체 필드
    @Test
    fun decodesFullResponse() {
        val decoded = json.decodeFromString<PublicProfileResponse>(
            """{"id":2,"name":"홍희표","profileImg":"https://x/a.png","bio":"소개글","statusMessage":"상태","createdAt":"2026-07-01T09:00:00+09:00"}"""
        )

        assertEquals(2L, decoded.id)
        assertEquals("홍희표", decoded.name)
        assertEquals("소개글", decoded.bio)
        assertEquals("2026-07-01T09:00:00+09:00", decoded.createdAt)
    }

    // 옵셔널 누락 시 기본값으로 내려앉아야 한다
    @Test
    fun decodesMissingOptionalsToDefaults() {
        val decoded = json.decodeFromString<PublicProfileResponse>(
            """{"id":2,"name":"홍희표","createdAt":"2026-07-01T09:00:00+09:00"}"""
        )

        assertNull(decoded.profileImg)
        assertNull(decoded.bio)
        assertNull(decoded.statusMessage)
    }
}
