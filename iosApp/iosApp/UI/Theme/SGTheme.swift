import SwiftUI

// StoryGroup 디자인 토큰(웹 globals.css 미러) — Compose 쪽 SgColors.kt와 값이 1:1로 같아야 한다

enum SGMood: String, CaseIterable, Identifiable {
    case warm, vibrant
    var id: String { rawValue }
    var label: String { self == .warm ? "다정함 (웜)" : "캐주얼 (비비드)" }
}

enum SGNightMode: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var label: String {
        switch self {
        case .system: return "시스템 설정"
        case .light: return "라이트"
        case .dark: return "다크"
        }
    }
}

enum SGNavStyle: String, CaseIterable, Identifiable {
    case tabs, drawer
    var id: String { rawValue }
    var label: String { self == .tabs ? "기본 (하단 탭)" : "레거시 (드로어)" }
}

// 테마/내비 설정 상태 — 생성 시 UserDefaults에서 복원, 변경 즉시 저장 (Compose ThemeState 미러)
final class SGThemeState: ObservableObject {
    @Published var mood: SGMood {
        didSet { defaults.set(mood.rawValue, forKey: Self.keyMood) }
    }

    @Published var nightMode: SGNightMode {
        didSet { defaults.set(nightMode.rawValue, forKey: Self.keyNightMode) }
    }

    @Published var navStyle: SGNavStyle {
        didSet { defaults.set(navStyle.rawValue, forKey: Self.keyNavStyle) }
    }

    private let defaults: UserDefaults

    private static let keyMood = "sg_theme_mood"

    private static let keyNightMode = "sg_theme_night_mode"

    private static let keyNavStyle = "sg_theme_nav_style"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        mood = defaults.string(forKey: Self.keyMood).flatMap(SGMood.init(rawValue:)) ?? .warm
        nightMode = defaults.string(forKey: Self.keyNightMode).flatMap(SGNightMode.init(rawValue:)) ?? .system
        navStyle = defaults.string(forKey: Self.keyNavStyle).flatMap(SGNavStyle.init(rawValue:)) ?? .tabs
    }
}

struct SGColors {
    let paper: Color

    let linen: Color

    let ink: Color

    let inkSoft: Color

    let inkFaint: Color

    let stoneBorder: Color

    let accent: Color

    let accentSoft: Color

    let onAccent: Color

    let accent2: Color

    let accent2Soft: Color

    let moss: Color

    let amber: Color

    let rust: Color

    /// 웹 radius-card 미러(warm 18 / vibrant 12)
    let radiusCard: CGFloat

    /// 웹 radius-btn 미러(warm은 nil=캡슐, vibrant 8)
    let radiusButton: CGFloat?

    static func palette(mood: SGMood, dark: Bool) -> SGColors {
        switch (mood, dark) {
        case (.warm, false):
            return SGColors(
                paper: Color(hex: 0xFCF3EF), linen: Color(hex: 0xFFFFFF),
                ink: Color(hex: 0x3D2B2A), inkSoft: Color(hex: 0x8A716C), inkFaint: Color(hex: 0xB29892),
                stoneBorder: Color(hex: 0xF1DCD2),
                accent: Color(hex: 0xC4577C), accentSoft: Color(hex: 0xF7E1EA), onAccent: Color(hex: 0xFFFFFF),
                accent2: Color(hex: 0xD9A441), accent2Soft: Color(hex: 0xF7EBD3),
                moss: Color(hex: 0x6B8F52), amber: Color(hex: 0xC08A2E), rust: Color(hex: 0xB23B3B),
                radiusCard: 18, radiusButton: nil
            )
        case (.warm, true):
            return SGColors(
                paper: Color(hex: 0x241A1C), linen: Color(hex: 0x2E2224),
                ink: Color(hex: 0xF7EDE9), inkSoft: Color(hex: 0xC2A9A4), inkFaint: Color(hex: 0x8C7570),
                stoneBorder: Color(hex: 0x3E2E31),
                accent: Color(hex: 0xE8839F), accentSoft: Color(hex: 0xE8839F, alpha: 0.16), onAccent: Color(hex: 0x241A1C),
                accent2: Color(hex: 0xE8BE72), accent2Soft: Color(hex: 0xE8BE72, alpha: 0.16),
                moss: Color(hex: 0x9BC17E), amber: Color(hex: 0xE0B15C), rust: Color(hex: 0xE08A78),
                radiusCard: 18, radiusButton: nil
            )
        case (.vibrant, false):
            return SGColors(
                paper: Color(hex: 0xFBF7FA), linen: Color(hex: 0xFFFFFF),
                ink: Color(hex: 0x221626), inkSoft: Color(hex: 0x6E5E7D), inkFaint: Color(hex: 0x9A8CAE),
                stoneBorder: Color(hex: 0xEBE1F0),
                accent: Color(hex: 0xE85A3D), accentSoft: Color(hex: 0xFBE3DC), onAccent: Color(hex: 0xFFFFFF),
                accent2: Color(hex: 0xC98A00), accent2Soft: Color(hex: 0xF7E7C4),
                moss: Color(hex: 0x2F8F4E), amber: Color(hex: 0xC97A2E), rust: Color(hex: 0xC23B2E),
                radiusCard: 12, radiusButton: 8
            )
        case (.vibrant, true):
            return SGColors(
                paper: Color(hex: 0x1E1626), linen: Color(hex: 0x291F33),
                ink: Color(hex: 0xF4EEFB), inkSoft: Color(hex: 0xB4A4C6), inkFaint: Color(hex: 0x7C6D93),
                stoneBorder: Color(hex: 0x3E304C),
                accent: Color(hex: 0xFF6B4A), accentSoft: Color(hex: 0xFF6B4A, alpha: 0.16), onAccent: Color(hex: 0x1E1626),
                accent2: Color(hex: 0xFFC94A), accent2Soft: Color(hex: 0xFFC94A, alpha: 0.16),
                moss: Color(hex: 0x8CE0A0), amber: Color(hex: 0xFFD37A), rust: Color(hex: 0xFF8A76),
                radiusCard: 12, radiusButton: 8
            )
        }
    }
}

extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }
}
