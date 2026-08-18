package kr.hhp227.storygroup.shared

import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.dto.UnifiedSearchResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchDtosTest {
    private val json = Json { ignoreUnknownKeys = true }

    // 백엔드 search/dto/SearchDtos.kt SearchResponse 5섹션과 1:1 — 대표 필드까지 확인
    @Test
    fun decodesAllFiveSections() {
        val decoded = json.decodeFromString<UnifiedSearchResponse>(
            """
            {
              "groups": [{"id": 1, "name": "산악회", "image": "https://x/img.png", "description": "설명"}],
              "posts": [{"id": 10, "groupId": 1, "groupName": "산악회", "authorName": "홍", "text": "본문", "createdAt": "2026-08-14T00:00:00Z"}],
              "files": [{"id": 20, "groupId": 1, "groupName": "산악회", "name": "a.pdf", "url": "https://x/a.pdf", "createdAt": "2026-08-14T00:00:00Z"}],
              "messages": [{"id": 30, "chatRoomId": 5, "groupId": null, "groupName": null, "authorName": "김", "text": "메시지", "createdAt": "2026-08-14T00:00:00Z"}],
              "users": [{"id": 2, "name": "이", "profileImg": null, "statusMessage": "상태"}]
            }
            """.trimIndent()
        )

        assertEquals(1, decoded.groups.size)
        assertEquals("산악회", decoded.groups[0].name)
        assertEquals(1L, decoded.posts[0].groupId)
        assertEquals("https://x/a.pdf", decoded.files[0].url)
        // DM 메시지 — groupId/groupName null
        assertNull(decoded.messages[0].groupId)
        assertEquals(5L, decoded.messages[0].chatRoomId)
        assertEquals("상태", decoded.users[0].statusMessage)
    }

    // 섹션 누락·옵셔널 누락 시 기본값으로 내려앉아야 한다(구서버·부분 응답 방어)
    @Test
    fun decodesMissingSectionsAndOptionalsToDefaults() {
        val decoded = json.decodeFromString<UnifiedSearchResponse>(
            """{"groups": [{"id": 1, "name": "이름만"}]}"""
        )

        assertEquals(emptyList(), decoded.posts)
        assertEquals(emptyList(), decoded.files)
        assertEquals(emptyList(), decoded.messages)
        assertEquals(emptyList(), decoded.users)
        assertNull(decoded.groups[0].image)
        assertNull(decoded.groups[0].description)
    }
}
