package kr.hhp227.storygroup.shared.data.repository

import app.cash.paging.Pager
import app.cash.paging.PagingData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.json.Json
import kr.hhp227.storygroup.shared.data.network.StompSessionEvent
import kr.hhp227.storygroup.shared.data.network.StompSocket
import kr.hhp227.storygroup.shared.data.network.StoryGroupApi
import kr.hhp227.storygroup.shared.data.network.dto.NotificationResponse
import kr.hhp227.storygroup.shared.data.network.dto.PersonalSocketEventResponse
import kr.hhp227.storygroup.shared.data.network.dto.UnreadCountResponse
import kr.hhp227.storygroup.shared.data.paging.PagePagingConfig
import kr.hhp227.storygroup.shared.data.paging.PagePagingSource
import kr.hhp227.storygroup.shared.data.storage.TokenStorage
import kr.hhp227.storygroup.shared.domain.model.AppNotification
import kr.hhp227.storygroup.shared.domain.model.NotificationTargetType
import kr.hhp227.storygroup.shared.domain.model.NotificationType
import kr.hhp227.storygroup.shared.domain.model.PersonalEvent
import kr.hhp227.storygroup.shared.domain.model.PersonalEventType
import kr.hhp227.storygroup.shared.domain.repository.NotificationRepository

class NotificationRepositoryImpl(
    private val client: HttpClient,
    tokenStorage: TokenStorage,
    baseUrl: String = StoryGroupApi.DEFAULT_BASE_URL
) : NotificationRepository {

    private val socket = StompSocket(client, baseUrl, tokenStorage)

    // STOMP 프레임 본문 디코드용 — ApiClient의 ContentNegotiation 설정과 동일 정책
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    // 알림 VM과 채팅 허브 VM이 동시에 구독해도 개인 큐 소켓은 1개 — 구독자 0이 되면(로그아웃으로
    // 세션 VM들이 정리되면) 업스트림 수집이 취소돼 연결도 닫힌다.
    private val personalEvents: Flow<PersonalEvent> =
        socket.subscribe("/user/queue/notifications")
            .mapNotNull { event ->
                when (event) {
                    StompSessionEvent.Connected -> PersonalEvent(PersonalEventType.CONNECTED)
                    StompSessionEvent.Disconnected -> PersonalEvent(PersonalEventType.DISCONNECTED)
                    is StompSessionEvent.Message ->
                        runCatching { json.decodeFromString<PersonalSocketEventResponse>(event.body) }
                            .getOrNull()?.toDomain()
                }
            }
            .shareIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.WhileSubscribed())

    override fun observePersonalEvents(): Flow<PersonalEvent> = personalEvents

    override fun getNotificationsPagingData(): Flow<PagingData<AppNotification>> =
        Pager(PagePagingConfig) {
            PagePagingSource { page, size ->
                client.get("/api/notifications") {
                    parameter("page", page)
                    parameter("size", size)
                }.body<List<NotificationResponse>>().mapNotNull { it.toDomain() }
            }
        }.flow

    override suspend fun getUnreadCount(): Result<Long> =
        runCatching { client.get("/api/notifications/unread-count").body<UnreadCountResponse>().count }

    override suspend fun markAsRead(notificationId: Long): Result<Unit> =
        runCatching {
            client.patch("/api/notifications/$notificationId/read")
            Unit
        }

    override suspend fun markAllAsRead(): Result<Unit> =
        runCatching {
            client.post("/api/notifications/read-all")
            Unit
        }
}

// 서버가 새 타입을 추가해도 목록 조회가 통째로 깨지지 않게, 모르는 타입은 항목째 걸러낸다
// (표시할 라벨이 없는 알림이라 기본값 흡수 대신 제외가 맞다 — 그룹류 enum 흡수 정책과 구분)
private fun NotificationResponse.toDomain(): AppNotification? {
    val domainType = NotificationType.entries.firstOrNull { it.name == type } ?: return null

    return AppNotification(
        id = id,
        type = domainType,
        targetType = targetType?.let { raw -> NotificationTargetType.entries.firstOrNull { it.name == raw } },
        targetId = targetId,
        isRead = isRead,
        createdAt = createdAt
    )
}

/** 모르는 봉투 타입은 항목째 제외 — 목록의 미지 알림 타입 정책과 동일 */
private fun PersonalSocketEventResponse.toDomain(): PersonalEvent? = when (type) {
    "NOTIFICATION" -> notification?.toDomain()
        ?.let { PersonalEvent(PersonalEventType.NOTIFICATION, notification = it) }
    "CHAT_MESSAGE" -> chatRoomId
        ?.let { PersonalEvent(PersonalEventType.CHAT_MESSAGE, chatRoomId = it, messageId = messageId, senderId = senderId) }
    // 통화 벨울림(휘발, DM·그룹 방) — 발신자 필드명이 서버 CallInviteEvent 계약(fromUserId/fromUserName)이라 별도 매핑
    "CALL_INVITE" -> chatRoomId
        ?.let {
            PersonalEvent(
                PersonalEventType.CALL_INVITE,
                chatRoomId = it,
                senderId = fromUserId,
                senderName = fromUserName,
                groupId = groupId,
                roomName = roomName
            )
        }
    else -> null
}
