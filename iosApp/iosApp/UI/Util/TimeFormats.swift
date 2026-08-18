import Foundation

/// 서버 ISO-8601(OffsetDateTime) 문자열 → 피드용 상대 시각 — composeApp ui/util/TimeFormats.kt와 1:1 미러.
/// 웹은 절대 시각을 쓰지만 모바일 피드 관례에 맞춰 7일까지는 상대 표기, 그 이후는 날짜만.
enum TimeFormats {
    private static let iso: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    static func relative(_ isoDateTime: String) -> String {
        guard let date = parse(isoDateTime) else { return dateOnly(isoDateTime) }
        let elapsed = Date().timeIntervalSince(date)
        let minutes = Int(elapsed / 60)
        let hours = Int(elapsed / 3600)
        let days = Int(elapsed / 86400)

        if minutes < 1 { return "방금" }
        if hours < 1 { return "\(minutes)분 전" }
        if days < 1 { return "\(hours)시간 전" }
        if days < 7 { return "\(days)일 전" }
        // 타임존 변환 없이 서버(KST) 오프셋 기준 날짜를 그대로 쓴다 — 날짜 단위 표기라 오차 허용
        return dateOnly(isoDateTime)
    }

    // 서버는 마이크로초 소수부를 내려주는데 ISO8601DateFormatter는 3자리 초과를 못 읽는다
    // — 초 단위 정밀도면 충분하니 소수부를 떼고 파싱한다
    private static func parse(_ isoDateTime: String) -> Date? {
        let secondsOnly = isoDateTime.replacingOccurrences(of: "\\.\\d+", with: "", options: .regularExpression)

        return iso.date(from: secondsOnly)
    }

    private static func dateOnly(_ isoDateTime: String) -> String {
        String(isoDateTime.prefix(while: { $0 != "T" })).replacingOccurrences(of: "-", with: ".")
    }

    private static let chatDateKeyFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private static let chatDateLabelFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "yyyy년 M월 d일 EEEE"
        return formatter
    }()

    /// 채팅 날짜 구분 그룹 키 — 기기 로컬 기준 yyyy-MM-dd(파싱 실패 시 서버 오프셋 날짜부 폴백).
    /// composeApp chatDateKey와 1:1 미러
    static func chatDateKey(_ isoDateTime: String) -> String {
        guard let date = parse(isoDateTime) else { return String(isoDateTime.prefix(while: { $0 != "T" })) }
        return chatDateKeyFormatter.string(from: date)
    }

    /// 채팅 날짜 구분 버블 라벨 — "2026년 8월 16일 토요일"(카카오톡 관례, 기기 로컬 기준).
    /// composeApp formatChatDate와 1:1 미러
    static func chatDate(_ isoDateTime: String) -> String {
        guard let date = parse(isoDateTime) else { return dateOnly(isoDateTime) }
        return chatDateLabelFormatter.string(from: date)
    }

    /// 가입일 표기 — 웹 공개 프로필의 toLocaleDateString("ko-KR") 미러("2026. 7. 1." — 선행 0 없음).
    /// " 가입" 접미는 화면 몫. composeApp formatJoinDate와 1:1 미러
    static func joinDate(_ isoDateTime: String) -> String {
        let parts = String(isoDateTime.prefix(while: { $0 != "T" })).split(separator: "-").compactMap { Int($0) }

        guard parts.count == 3 else { return dateOnly(isoDateTime) }
        return "\(parts[0]). \(parts[1]). \(parts[2])."
    }
}
