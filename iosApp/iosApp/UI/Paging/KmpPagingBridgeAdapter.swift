//
//  KmpPagingBridgeAdapter.swift
//  iosApp
//
//  shared 프레임워크의 SwiftUiPagingBridge(androidx paging-common 엔진)를
//  Jetpack-Paging-for-SwiftUI의 PagingBridgeDataSource에 어댑팅한다.
//  (라이브러리 docs/KMP.md의 샘플 어댑터)
//

import Paging
import Shared

// Shared는 androidx paging-common을 export하므로 LoadState가 SPM Paging 라이브러리와
// 같은 이름으로 노출된다. 뷰가 참조하는 LoadState는 LazyPagingItems.loadState(Paging 쪽)
// 타입이므로, 모듈 수준 별칭으로 Paging 쪽에 고정해 모호성을 없앤다.
typealias LoadState = Paging.LoadState

final class KmpPagingBridgeAdapter<T: AnyObject>: PagingBridgeDataSource {
    private let bridge: SwiftUiPagingBridge<T>

    var onPagesUpdated: (() -> Void)?

    var onLoadStatesUpdated: ((PagingBridgeCombinedLoadStates) -> Void)?

    // 이 어댑터의 수명에 묶어야 하는 리소스 (예: State→PagingData 퍼블리셔 구독의 AnyCancellable)
    var retained: Any?

    var count: Int { Int(bridge.count) }

    func item(at index: Int) -> Any? { bridge.item(index: Int32(index)) }

    func peekItem(at index: Int) -> Any? { bridge.peekItem(index: Int32(index)) }

    func refresh() { bridge.refresh() }

    func retry() { bridge.retry() }

    func start() { bridge.start() }

    init(_ bridge: SwiftUiPagingBridge<T>) {
        self.bridge = bridge
        bridge.onPagesUpdated = { [weak self] in self?.onPagesUpdated?() }
        bridge.onLoadStatesUpdated = { [weak self] states in
            self?.onLoadStatesUpdated?(states.toSwift())
        }
    }

    deinit { bridge.dispose() }
}

private extension BridgeCombinedLoadStates {
    func toSwift() -> PagingBridgeCombinedLoadStates {
        PagingBridgeCombinedLoadStates(
            refresh: refresh.toSwift(),
            prepend: prepend.toSwift(),
            append: append.toSwift()
        )
    }
}

private extension BridgeLoadState {
    func toSwift() -> PagingBridgeLoadState {
        PagingBridgeLoadState(
            isLoading: isLoading,
            errorMessage: errorMessage,
            endOfPaginationReached: endOfPaginationReached
        )
    }
}