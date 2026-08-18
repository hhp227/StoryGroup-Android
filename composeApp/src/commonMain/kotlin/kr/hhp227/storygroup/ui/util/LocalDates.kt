package kr.hhp227.storygroup.ui.util

/**
 * 기기 로컬 타임존 기준 날짜·시각 — 일정 캘린더의 셀 귀속/선택 키 전용(표시도 저장도 아닌
 * UI 키, 웹 ymd() 미러). composeApp은 android+jvm 타깃뿐이라 actual은 둘 다 java.time
 * 동일 구현(kotlinx-datetime 의존성 대신 expect/actual 2벌 — 프로젝트 의존성 최소 관례).
 */
data class LocalStamp(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) {
    /** 캘린더 셀 귀속/선택 키(yyyy-MM-dd) — 표시용이 아니다 */
    val dateKey: String get() = dateKeyOf(year, month, day)
}

/** yyyy-MM-dd 키 조립 — 웹 ymd() 미러 */
fun dateKeyOf(year: Int, month: Int, day: Int): String =
    "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

/** 해당 일자의 요일(일=0…토=6) — 월 1일 요일에서 상대 계산이라 expect 불필요 */
fun dayOfWeek(year: Int, month: Int, day: Int): Int =
    (firstDayOfWeekOfMonth(year, month) + day - 1) % 7

/** 지금 이 순간의 로컬 날짜·시각 */
expect fun todayLocal(): LocalStamp

/** 해당 월의 일수(28~31) */
expect fun monthLength(year: Int, month: Int): Int

/** 해당 월 1일의 요일(일=0…토=6) — 캘린더 앞쪽 빈 칸 수(웹 leadingBlanks 미러) */
expect fun firstDayOfWeekOfMonth(year: Int, month: Int): Int

/** 서버 ISO-8601(OffsetDateTime) → 로컬 날짜·시각. 파싱 실패는 null(셀 귀속 제외) */
expect fun isoToLocal(iso: String): LocalStamp?

/** 로컬 날짜·시각 → 서버로 보낼 ISO-8601(오프셋 포함) — 일정 생성 요청용 */
expect fun localToIso(year: Int, month: Int, day: Int, hour: Int, minute: Int): String
