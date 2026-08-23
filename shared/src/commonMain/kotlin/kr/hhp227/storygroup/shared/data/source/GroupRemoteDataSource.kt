package kr.hhp227.storygroup.shared.data.source

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kr.hhp227.storygroup.shared.data.network.dto.CreateGroupRequest
import kr.hhp227.storygroup.shared.data.network.dto.CreateInviteRequest
import kr.hhp227.storygroup.shared.data.network.dto.DiscoverGroupResponse
import kr.hhp227.storygroup.shared.data.network.dto.GroupPhotosPageResponse
import kr.hhp227.storygroup.shared.data.network.dto.GroupResponse
import kr.hhp227.storygroup.shared.data.network.dto.InviteResponse
import kr.hhp227.storygroup.shared.data.network.dto.JoinGroupResponse
import kr.hhp227.storygroup.shared.data.network.dto.JoinRequestResponse
import kr.hhp227.storygroup.shared.data.network.dto.MemberResponse
import kr.hhp227.storygroup.shared.data.network.dto.PostReportResponse
import kr.hhp227.storygroup.shared.data.network.dto.ProcessReportRequest
import kr.hhp227.storygroup.shared.data.network.dto.UpdateGroupRequest

/** 그룹 원격 소스 — 전송·DTO만 담당, 예외는 그대로 던진다(Result 래핑·도메인 매핑·페이징 구성·에러 문구 변환은 리포지토리 몫) */
interface GroupRemoteDataSource {
    /** 내가 가입한 그룹 전체 목록(라운지 포함, 페이징 파라미터 없이 전체 반환) — GET /api/groups */
    suspend fun getMyGroups(): List<GroupResponse>

    /** 내 그룹 목록 페이지 조회(홈 탭 Pager loadPage 전용) — GET /api/groups?page&size */
    suspend fun getMyGroups(page: Int, size: Int): List<GroupResponse>

    suspend fun getGroup(groupId: Long): GroupResponse
    suspend fun getMembers(groupId: Long): List<MemberResponse>
    suspend fun getGroupPhotos(groupId: Long, page: Int, size: Int): GroupPhotosPageResponse
    suspend fun createGroup(name: String, description: String?, image: String?, joinType: String): GroupResponse
    suspend fun getDiscoverGroups(query: String, sort: String, page: Int, size: Int): List<DiscoverGroupResponse>
    suspend fun joinGroup(groupId: Long): JoinGroupResponse
    suspend fun cancelJoinRequest(groupId: Long)
    suspend fun getMyJoinRequestedGroups(): List<DiscoverGroupResponse>
    suspend fun getJoinRequests(groupId: Long): List<JoinRequestResponse>
    suspend fun approveJoinRequest(groupId: Long, userId: Long)
    suspend fun rejectJoinRequest(groupId: Long, userId: Long)
    suspend fun createInvite(groupId: Long, maxUses: Int?, expiresInDays: Int?): InviteResponse
    suspend fun joinByCode(code: String): GroupResponse
    suspend fun updateGroup(groupId: Long, name: String, description: String?, image: String?, joinType: String?): GroupResponse
    suspend fun deleteGroup(groupId: Long)
    suspend fun leaveGroup(groupId: Long)
    suspend fun getGroupReports(groupId: Long, status: String?): List<PostReportResponse>
    suspend fun processGroupReport(groupId: Long, reportId: Long, status: String): PostReportResponse
}

class GroupRemoteDataSourceImpl(private val client: HttpClient) : GroupRemoteDataSource {
    override suspend fun getMyGroups(): List<GroupResponse> =
        client.get("/api/groups").body()

    override suspend fun getMyGroups(page: Int, size: Int): List<GroupResponse> =
        client.get("/api/groups") {
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun getGroup(groupId: Long): GroupResponse =
        client.get("/api/groups/$groupId").body()

    override suspend fun getMembers(groupId: Long): List<MemberResponse> =
        client.get("/api/groups/$groupId/members").body()

    override suspend fun getGroupPhotos(groupId: Long, page: Int, size: Int): GroupPhotosPageResponse =
        client.get("/api/groups/$groupId/photos") {
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun createGroup(name: String, description: String?, image: String?, joinType: String): GroupResponse =
        client.post("/api/groups") {
            contentType(ContentType.Application.Json)
            setBody(CreateGroupRequest(name = name, description = description, image = image, joinType = joinType))
        }.body()

    override suspend fun getDiscoverGroups(query: String, sort: String, page: Int, size: Int): List<DiscoverGroupResponse> =
        client.get("/api/groups/discover") {
            parameter("query", query)
            parameter("sort", sort)
            parameter("page", page)
            parameter("size", size)
        }.body()

    override suspend fun joinGroup(groupId: Long): JoinGroupResponse =
        client.post("/api/groups/$groupId/join").body()

    override suspend fun cancelJoinRequest(groupId: Long) {
        client.delete("/api/groups/$groupId/join")
    }

    override suspend fun getMyJoinRequestedGroups(): List<DiscoverGroupResponse> =
        client.get("/api/groups/join-requests/mine").body()

    override suspend fun getJoinRequests(groupId: Long): List<JoinRequestResponse> =
        client.get("/api/groups/$groupId/join-requests").body()

    override suspend fun approveJoinRequest(groupId: Long, userId: Long) {
        client.post("/api/groups/$groupId/join-requests/$userId/approve")
    }

    override suspend fun rejectJoinRequest(groupId: Long, userId: Long) {
        client.delete("/api/groups/$groupId/join-requests/$userId")
    }

    override suspend fun createInvite(groupId: Long, maxUses: Int?, expiresInDays: Int?): InviteResponse =
        client.post("/api/groups/$groupId/invites") {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(maxUses = maxUses, expiresInDays = expiresInDays))
        }.body()

    override suspend fun joinByCode(code: String): GroupResponse =
        client.post("/api/groups/join/$code").body()

    override suspend fun updateGroup(
        groupId: Long,
        name: String,
        description: String?,
        image: String?,
        joinType: String?
    ): GroupResponse =
        client.patch("/api/groups/$groupId") {
            contentType(ContentType.Application.Json)
            setBody(UpdateGroupRequest(name = name, description = description, image = image, joinType = joinType))
        }.body()

    override suspend fun deleteGroup(groupId: Long) {
        client.delete("/api/groups/$groupId")
    }

    override suspend fun leaveGroup(groupId: Long) {
        client.post("/api/groups/$groupId/leave")
    }

    override suspend fun getGroupReports(groupId: Long, status: String?): List<PostReportResponse> =
        client.get("/api/groups/$groupId/reports") {
            // null=전체 — 쿼리 자체를 뺀다(웹과 동일)
            if (status != null) parameter("status", status)
        }.body()

    override suspend fun processGroupReport(groupId: Long, reportId: Long, status: String): PostReportResponse =
        client.patch("/api/groups/$groupId/reports/$reportId") {
            contentType(ContentType.Application.Json)
            setBody(ProcessReportRequest(status))
        }.body()
}
