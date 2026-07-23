package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp /api/notifications 계약과 1:1 (notification/dto/NotificationDtos.kt NotificationResponse)
@Serializable
data class NotificationResponse(
    val id: Long,
    val type: String,
    val targetType: String? = null,
    val targetId: Long? = null,
    val isRead: Boolean,
    val createdAt: String
)

// GET /api/notifications/unread-count 응답 — notification/dto/NotificationDtos.kt UnreadCountResponse와 1:1
@Serializable
data class UnreadCountResponse(
    val count: Long
)
