package kr.hhp227.storygroup.ui.navigation

import kotlinx.serialization.Serializable

/**
 * 목적지 — 기존 App.kt에 흩어져 있던 15종을 한곳으로 모았다.
 * ⚠️ private/internal이면 안 된다: Desktop(JVM)에서 kotlinx.serialization 리플렉션이
 * 패키지 전용 클래스에 접근하지 못해 시작 즉시 IllegalAccessException으로 죽는다(Android는 통과).
 * 화면들이 ui.navigation 밖에서 참조하므로 public이 맞다.
 * iosApp UI/Navigation/Route.swift와 1:1 미러.
 */
@Serializable
sealed interface Route {

    /** NavHost 시작 목적지 — 아무것도 그리지 않는 빈 오버레이. 셸은 NavHost 밖에서 항상 살아있다 */
    @Serializable
    data object Shell : Route

    @Serializable
    data class GroupDetail(val groupId: Long) : Route

    /**
     * 게시글 상세 — 라운지 글도 라운지 그룹 id로 들어오므로 홈·그룹 피드가 같은 목적지를 쓴다
     */
    @Serializable
    data class PostDetail(val groupId: Long, val postId: Long) : Route

    /** 채팅방 — 그룹 채팅(groupId 있음)/DM(null) 공용. title은 허브가 아는 표시명 */
    @Serializable
    data class ChatRoom(val chatRoomId: Long, val groupId: Long?, val title: String) : Route

    /** 공개 프로필 — 카드 다이얼로그(iOS 시트 미러) */
    @Serializable
    data class UserProfile(val userId: Long) : Route

    /**
     * 게시글 작성 — groupId null이면 라운지(홈 피드)에 게시.
     * postId가 있으면 같은 폼이 수정 모드로 동작한다
     */
    @Serializable
    data class CreatePost(val groupId: Long?, val postId: Long? = null) : Route

    @Serializable
    data class GroupEdit(val groupId: Long) : Route

    /** 그룹 신고함(모더레이터) */
    @Serializable
    data class GroupReports(val groupId: Long) : Route

    /**
     * 방 통화 — DM 1:1·그룹 방 공용.
     * ring=true는 발신(입장+벨울림), false는 수신 배너 수락으로 진입.
     * video=false면 보이스톡(카메라 OFF·수화구 시작)
     */
    @Serializable
    data class Call(
        val chatRoomId: Long,
        val title: String,
        val ring: Boolean,
        val video: Boolean = true
    ) : Route

    @Serializable
    data object AccountSettings : Route

    @Serializable
    data object AppSettings : Route

    @Serializable
    data object BlockedUsers : Route

    @Serializable
    data object CreateGroup : Route

    @Serializable
    data object DiscoverGroups : Route

    @Serializable
    data object PendingGroups : Route

    @Serializable
    data object Search : Route
}
