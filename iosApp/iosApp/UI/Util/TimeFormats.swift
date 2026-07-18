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
        // 서버는 마이크로초 소수부를 내려주는데 ISO8601DateFormatter는 3자리 초과를 못 읽는다
        // — 초 단위 정밀도면 충분하니 소수부를 떼고 파싱한다
        let secondsOnly = isoDateTime.replacingOccurrences(of: "\\.\\d+", with: "", options: .regularExpression)
        guard let date = iso.date(from: secondsOnly) else { return dateOnly(isoDateTime) }
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

    private static func dateOnly(_ isoDateTime: String) -> String {
        String(isoDateTime.prefix(while: { $0 != "T" })).replacingOccurrences(of: "-", with: ".")
    }
}
