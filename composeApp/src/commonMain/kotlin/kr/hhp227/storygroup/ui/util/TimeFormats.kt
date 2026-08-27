package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.date_chat_format
import storygroup.composeapp.generated.resources.date_month_day_weekday
import storygroup.composeapp.generated.resources.date_year_month
import storygroup.composeapp.generated.resources.day_names_full
import storygroup.composeapp.generated.resources.day_names_short
import storygroup.composeapp.generated.resources.month_names
import storygroup.composeapp.generated.resources.time_days_ago
import storygroup.composeapp.generated.resources.time_hours_ago
import storygroup.composeapp.generated.resources.time_just_now
import storygroup.composeapp.generated.resources.time_minutes_ago
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 서버 ISO-8601(OffsetDateTime) 문자열 → 피드용 상대 시각.
 * 웹은 절대 시각을 쓰지만 모바일 피드 관례에 맞춰 7일까지는 상대 표기, 그 이후는 날짜만.
 * iosApp TimeFormats.swift와 1:1 미러 — 표기는 언어 리소스(en/ko/ja)를 따른다
 */
@OptIn(ExperimentalTime::class)
@Composable
fun formatRelativeTime(isoDateTime: String): String {
    val instant = runCatching { Instant.parse(isoDateTime) }.getOrNull()
        ?: return isoDateTime.dateOnly()
    val elapsed = Clock.System.now() - instant

    return when {
        elapsed.inWholeMinutes < 1 -> stringResource(Res.string.time_just_now)
        elapsed.inWholeHours < 1 -> elapsed.inWholeMinutes.toInt()
            .let { pluralStringResource(Res.plurals.time_minutes_ago, it, it) }
        elapsed.inWholeDays < 1 -> elapsed.inWholeHours.toInt()
            .let { pluralStringResource(Res.plurals.time_hours_ago, it, it) }
        elapsed.inWholeDays < 7 -> elapsed.inWholeDays.toInt()
            .let { pluralStringResource(Res.plurals.time_days_ago, it, it) }
        // 타임존 변환 없이 서버(KST) 오프셋 기준 날짜를 그대로 쓴다 — 날짜 단위 표기라 오차 허용
        else -> isoDateTime.dateOnly()
    }
}

/**
 * 가입일 표기 — 웹 공개 프로필의 toLocaleDateString("ko-KR") 미러("2026. 7. 1." — 선행 0 없음).
 * " 가입" 접미는 화면 몫(user_joined 등 포맷 리소스). 숫자 표기라 로케일 무관.
 * iosApp TimeFormats.swift joinDate와 1:1 미러
 */
fun formatJoinDate(isoDateTime: String): String {
    val parts = isoDateTime.substringBefore('T').split('-').mapNotNull { it.toIntOrNull() }

    if (parts.size != 3) return isoDateTime.dateOnly()
    return "${parts[0]}. ${parts[1]}. ${parts[2]}."
}

/** 월 표기(1~12) — en은 "August", ko/ja는 "8월"/"8月" (리소스 배열) */
@Composable
fun monthName(month: Int): String = stringArrayResource(Res.array.month_names)[month - 1]

/** 요일 축약 표기 — dayOfWeek(일=0…토=6) 인덱스와 1:1 */
@Composable
fun dayNameShort(dayIndex: Int): String = stringArrayResource(Res.array.day_names_short)[dayIndex]

/** "2026년 8월" / "August 2026" / "2026年8月" — 앨범 월 헤더·일정 달력 헤더 공용 */
@Composable
fun formatYearMonth(year: Int, month: Int): String =
    stringResource(Res.string.date_year_month, year, monthName(month))

/** "8월 16일 (토)" / "August 16 (Sat)" — 일정 탭 선택일 헤더 */
@Composable
fun formatMonthDayWeekday(year: Int, month: Int, day: Int): String =
    stringResource(
        Res.string.date_month_day_weekday,
        monthName(month),
        day,
        dayNameShort(dayOfWeek(year, month, day))
    )

/** 채팅 날짜 구분 그룹 키 — 기기 로컬 기준 yyyy-MM-dd(파싱 실패 시 서버 오프셋 날짜부 폴백) */
fun chatDateKey(isoDateTime: String): String =
    isoToLocal(isoDateTime)?.dateKey ?: isoDateTime.substringBefore('T')

/**
 * 채팅 날짜 구분 버블 라벨 — "2026년 8월 16일 토요일"(카카오톡 관례, 기기 로컬 기준).
 * iosApp TimeFormats.swift chatDate와 1:1 미러 — 어순은 언어별 date_chat_format이 정한다
 */
@Composable
fun formatChatDate(isoDateTime: String): String {
    val local = isoToLocal(isoDateTime) ?: return isoDateTime.dateOnly()

    return stringResource(
        Res.string.date_chat_format,
        local.year,
        monthName(local.month),
        local.day,
        stringArrayResource(Res.array.day_names_full)[dayOfWeek(local.year, local.month, local.day)]
    )
}

private fun String.dateOnly(): String = substringBefore('T').replace('-', '.')
