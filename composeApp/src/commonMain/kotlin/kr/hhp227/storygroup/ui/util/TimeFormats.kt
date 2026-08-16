package kr.hhp227.storygroup.ui.util

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 서버 ISO-8601(OffsetDateTime) 문자열 → 피드용 상대 시각.
 * 웹은 절대 시각을 쓰지만 모바일 피드 관례에 맞춰 7일까지는 상대 표기, 그 이후는 날짜만.
 * iosApp TimeFormats.swift와 1:1 미러
 */
@OptIn(ExperimentalTime::class)
fun formatRelativeTime(isoDateTime: String): String {
    val instant = runCatching { Instant.parse(isoDateTime) }.getOrNull()
        ?: return isoDateTime.dateOnly()
    val elapsed = Clock.System.now() - instant

    return when {
        elapsed.inWholeMinutes < 1 -> "방금"
        elapsed.inWholeHours < 1 -> "${elapsed.inWholeMinutes}분 전"
        elapsed.inWholeDays < 1 -> "${elapsed.inWholeHours}시간 전"
        elapsed.inWholeDays < 7 -> "${elapsed.inWholeDays}일 전"
        // 타임존 변환 없이 서버(KST) 오프셋 기준 날짜를 그대로 쓴다 — 날짜 단위 표기라 오차 허용
        else -> isoDateTime.dateOnly()
    }
}

/**
 * 가입일 표기 — 웹 공개 프로필의 toLocaleDateString("ko-KR") 미러("2026. 7. 1." — 선행 0 없음).
 * " 가입" 접미는 화면 몫. iosApp TimeFormats.swift joinDate와 1:1 미러
 */
fun formatJoinDate(isoDateTime: String): String {
    val parts = isoDateTime.substringBefore('T').split('-').mapNotNull { it.toIntOrNull() }

    if (parts.size != 3) return isoDateTime.dateOnly()
    return "${parts[0]}. ${parts[1]}. ${parts[2]}."
}

// 채팅 날짜 버블 요일 표기 — dayOfWeek(일=0…토=6) 인덱스와 1:1
private val DAY_NAMES = listOf("일", "월", "화", "수", "목", "금", "토")

/** 채팅 날짜 구분 그룹 키 — 기기 로컬 기준 yyyy-MM-dd(파싱 실패 시 서버 오프셋 날짜부 폴백) */
fun chatDateKey(isoDateTime: String): String =
    isoToLocal(isoDateTime)?.dateKey ?: isoDateTime.substringBefore('T')

/**
 * 채팅 날짜 구분 버블 라벨 — "2026년 8월 16일 토요일"(카카오톡 관례, 기기 로컬 기준).
 * iosApp TimeFormats.swift chatDate와 1:1 미러
 */
fun formatChatDate(isoDateTime: String): String {
    val local = isoToLocal(isoDateTime) ?: return isoDateTime.dateOnly()

    return "${local.year}년 ${local.month}월 ${local.day}일 " +
        "${DAY_NAMES[dayOfWeek(local.year, local.month, local.day)]}요일"
}

private fun String.dateOnly(): String = substringBefore('T').replace('-', '.')
