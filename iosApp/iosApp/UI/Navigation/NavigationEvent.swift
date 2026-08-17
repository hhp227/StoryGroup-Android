import Foundation

/// VM → 호스트 이동 지시 — composeApp ui/navigation/NavigationEvent.kt 미러.
/// 둘이면 충분하다 — 프로필은 시트라 스택 밖이다: 다른 방으로 갈 때는 시트를 먼저 닫고 push한다(ProfileFollowUp 패턴).
/// 탭은 이벤트가 아니라 UiState.currentTab이다.
enum NavigationEvent {
    case navigateTo(Route)
    case navigateBack
}
