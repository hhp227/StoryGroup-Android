import Combine

/// MVI 계약(UiState/Action/Event) — composeApp ui/mvi/MviViewModel.kt와 1:1 미러.
/// - uiState: 화면 상태 단일 소스(View는 이것만 그린다)
/// - onAction: 모든 사용자 액션의 단일 진입점(View → VM)
/// - event: 일회성 이벤트(VM → View, 화면 전환·토스트 등) — 구독 중에만 전달된다.
///   이벤트가 없는 VM은 `typealias Event = Never`만 선언하면 기본 구현(빈 퍼블리셔)이 적용된다.
protocol MviViewModel: ObservableObject {
    associatedtype UiState
    associatedtype Action
    associatedtype Event

    var uiState: UiState { get }

    var event: AnyPublisher<Event, Never> { get }

    func onAction(_ action: Action)
}

extension MviViewModel where Event == Never {
    var event: AnyPublisher<Never, Never> { Empty().eraseToAnyPublisher() }
}
