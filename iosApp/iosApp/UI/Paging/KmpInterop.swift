//
//  KmpInterop.swift
//  iosApp
//
//  Kotlin Flow ↔ Combine Publisher 대응 (Paging-CRUD 샘플과 동일 패턴).
//  UseCase 호출은 Combine Publisher를 반환하고, ViewModel은 표준 Combine
//  (.sink / .store(in: &cancellables))으로 소비한다 — Kotlin의
//  .onEach { }.launchIn(viewModelScope)에 해당하는 Combine 관용구.
//

import Combine
import Foundation
import Shared
// Paging 모듈 전체를 import하면 PagingData/LoadState 등이 Shared와 겹쳐 모호해지므로,
// 이 파일에 필요한 LazyPagingItems만 스코프 임포트한다
import class Paging.LazyPagingItems

// shared 프레임워크는 의존 모듈(paging-common) 클래스를 모듈 접두사로 노출한다
// (모듈 전체 export는 ObjC 헤더 생성을 깨뜨려서 쓰지 않음). 실체는 androidx PagingData —
// 이름만 복원해 Android(State.pagingData: PagingData<Post>)와 1:1 표기를 유지한다.
typealias PagingData<T: AnyObject> = Paging_commonPagingData<T>

// Kotlin: getLoungePostsPagingDataUseCase() → Flow<PagingData<Post>>
extension GetLoungePostsPagingDataUseCase {
    func callAsFunction() -> PostPagingPublisher {
        PostPagingPublisher(adapter: pagingFlow())
    }
}

// Kotlin: getGroupPostsPagingDataUseCase(groupId) → Flow<PagingData<Post>>
extension GetGroupPostsPagingDataUseCase {
    func callAsFunction(groupId: Int64) -> PostPagingPublisher {
        PostPagingPublisher(adapter: pagingFlow(groupId: groupId))
    }
}

// Kotlin: getMyGroupsPagingDataUseCase() → Flow<PagingData<Group>>
extension GetMyGroupsPagingDataUseCase {
    func callAsFunction() -> GroupPagingPublisher {
        GroupPagingPublisher(adapter: pagingFlow())
    }
}

// Kotlin: getDiscoverGroupsPagingDataUseCase(query, sort) → Flow<PagingData<DiscoverGroup>>
extension GetDiscoverGroupsPagingDataUseCase {
    func callAsFunction(query: String, sort: DiscoverSort) -> DiscoverGroupPagingPublisher {
        DiscoverGroupPagingPublisher(adapter: pagingFlow(query: query, sort: sort))
    }
}

// Kotlin: getNotificationsPagingDataUseCase() → Flow<PagingData<AppNotification>>
extension GetNotificationsPagingDataUseCase {
    func callAsFunction() -> AppNotificationPagingPublisher {
        AppNotificationPagingPublisher(adapter: pagingFlow())
    }
}
// Kotlin의 Flow<PagingData<Group>> 대응 퍼블리셔 — PostPagingPublisher의 Group 타입 대응
struct GroupPagingPublisher: Publisher {
    typealias Output = PagingData<Group>

    typealias Failure = Never

    fileprivate let adapter: GroupPagingFlowAdapter

    func cachedIn() -> GroupPagingPublisher {
        GroupPagingPublisher(adapter: adapter.cachedIn())
    }

    func receive<S>(subscriber: S) where S: Subscriber, S.Input == Output, S.Failure == Never {
        KotlinFlowPublisher<Output> { onEach in
            self.adapter.subscribe(onEach: onEach)
        }
        .receive(subscriber: subscriber)
    }
}

// Kotlin의 Flow<PagingData<DiscoverGroup>> 대응 퍼블리셔 — GroupPagingPublisher의 DiscoverGroup 타입 대응
struct DiscoverGroupPagingPublisher: Publisher {
    typealias Output = PagingData<DiscoverGroup>

    typealias Failure = Never

    fileprivate let adapter: DiscoverGroupPagingFlowAdapter

    func cachedIn() -> DiscoverGroupPagingPublisher {
        DiscoverGroupPagingPublisher(adapter: adapter.cachedIn())
    }

    func receive<S>(subscriber: S) where S: Subscriber, S.Input == Output, S.Failure == Never {
        KotlinFlowPublisher<Output> { onEach in
            self.adapter.subscribe(onEach: onEach)
        }
        .receive(subscriber: subscriber)
    }
}

// Kotlin의 Flow<PagingData<AppNotification>> 대응 퍼블리셔 — GroupPagingPublisher의 AppNotification 타입 대응
struct AppNotificationPagingPublisher: Publisher {
    typealias Output = PagingData<AppNotification>

    typealias Failure = Never

    fileprivate let adapter: AppNotificationPagingFlowAdapter

    func cachedIn() -> AppNotificationPagingPublisher {
        AppNotificationPagingPublisher(adapter: adapter.cachedIn())
    }

    func receive<S>(subscriber: S) where S: Subscriber, S.Input == Output, S.Failure == Never {
        KotlinFlowPublisher<Output> { onEach in
            self.adapter.subscribe(onEach: onEach)
        }
        .receive(subscriber: subscriber)
    }
}

// Kotlin의 Flow<PagingData<Post>> 대응 퍼블리셔.
// cachedIn()은 cachedIn(viewModelScope) 대응 — 캐시가 구독(cancellables) 수명에 묶인다
struct PostPagingPublisher: Publisher {
    typealias Output = PagingData<Post>

    typealias Failure = Never

    fileprivate let adapter: PostPagingFlowAdapter

    func cachedIn() -> PostPagingPublisher {
        PostPagingPublisher(adapter: adapter.cachedIn())
    }

    func receive<S>(subscriber: S) where S: Subscriber, S.Input == Output, S.Failure == Never {
        KotlinFlowPublisher<Output> { onEach in
            self.adapter.subscribe(onEach: onEach)
        }
        .receive(subscriber: subscriber)
    }
}

// Kotlin: getGroupPhotosPagingDataUseCase(groupId) → Flow<PagingData<GroupPhoto>>
extension GetGroupPhotosPagingDataUseCase {
    func callAsFunction(groupId: Int64) -> GroupPhotoPagingPublisher {
        GroupPhotoPagingPublisher(adapter: pagingFlow(groupId: groupId))
    }
}

// Kotlin의 Flow<PagingData<GroupPhoto>> 대응 퍼블리셔 — GroupPagingPublisher의 GroupPhoto 타입 대응
struct GroupPhotoPagingPublisher: Publisher {
    typealias Output = PagingData<GroupPhoto>

    typealias Failure = Never

    fileprivate let adapter: GroupPhotoPagingFlowAdapter

    func cachedIn() -> GroupPhotoPagingPublisher {
        GroupPhotoPagingPublisher(adapter: adapter.cachedIn())
    }

    func receive<S>(subscriber: S) where S: Subscriber, S.Input == Output, S.Failure == Never {
        KotlinFlowPublisher<Output> { onEach in
            self.adapter.subscribe(onEach: onEach)
        }
        .receive(subscriber: subscriber)
    }
}

// Compose의 pagingDataFlow.collectAsLazyPagingItems()와 동일한 소비 지점.
// State에서 꺼낸 PagingData 퍼블리셔를 presenter 브리지(PagingDataSubject)로 밀어넣는다.
// Output 제약 없이 받고 원소를 런타임 캐스팅한다(ObjC 제네릭 인자는 소거되므로 항상 성공).
// Paging 라이브러리의 동명 확장(Output == Paging.PagingData<T> 요구)과는 제약 불일치로 구분된다.
extension Publisher where Failure == Never {
    func collectAsLazyPagingItems() -> LazyPagingItems<Post> {
        let subject = PagingDataSubject<Post>()
        let bridge = unsafeDowncast(subject.bridge, to: SwiftUiPagingBridge<Post>.self)
        let adapter = KmpPagingBridgeAdapter(bridge)

        adapter.retained = sink { subject.send(pagingData: $0 as! PagingData<Post>) }
        return LazyPagingItems(bridge: adapter)
    }

    // Group 타입 대응 — 반환 타입 오버로드(호출부의 LazyPagingItems<Group> 프로퍼티 타입으로 선택된다)
    func collectAsLazyPagingItems() -> LazyPagingItems<Group> {
        let subject = PagingDataSubject<Group>()
        let bridge = unsafeDowncast(subject.bridge, to: SwiftUiPagingBridge<Group>.self)
        let adapter = KmpPagingBridgeAdapter(bridge)

        adapter.retained = sink { subject.send(pagingData: $0 as! PagingData<Group>) }
        return LazyPagingItems(bridge: adapter)
    }

    // DiscoverGroup 타입 대응 — 반환 타입 오버로드(호출부의 LazyPagingItems<DiscoverGroup> 프로퍼티 타입으로 선택된다)
    func collectAsLazyPagingItems() -> LazyPagingItems<DiscoverGroup> {
        let subject = PagingDataSubject<DiscoverGroup>()
        let bridge = unsafeDowncast(subject.bridge, to: SwiftUiPagingBridge<DiscoverGroup>.self)
        let adapter = KmpPagingBridgeAdapter(bridge)

        adapter.retained = sink { subject.send(pagingData: $0 as! PagingData<DiscoverGroup>) }
        return LazyPagingItems(bridge: adapter)
    }

    // AppNotification 타입 대응 — 반환 타입 오버로드(호출부의 LazyPagingItems<AppNotification> 프로퍼티 타입으로 선택된다)
    func collectAsLazyPagingItems() -> LazyPagingItems<AppNotification> {
        let subject = PagingDataSubject<AppNotification>()
        let bridge = unsafeDowncast(subject.bridge, to: SwiftUiPagingBridge<AppNotification>.self)
        let adapter = KmpPagingBridgeAdapter(bridge)

        adapter.retained = sink { subject.send(pagingData: $0 as! PagingData<AppNotification>) }
        return LazyPagingItems(bridge: adapter)
    }

    // GroupPhoto 타입 대응 — 반환 타입 오버로드(호출부의 LazyPagingItems<GroupPhoto> 프로퍼티 타입으로 선택된다)
    func collectAsLazyPagingItems() -> LazyPagingItems<GroupPhoto> {
        let subject = PagingDataSubject<GroupPhoto>()
        let bridge = unsafeDowncast(subject.bridge, to: SwiftUiPagingBridge<GroupPhoto>.self)
        let adapter = KmpPagingBridgeAdapter(bridge)

        adapter.retained = sink { subject.send(pagingData: $0 as! PagingData<GroupPhoto>) }
        return LazyPagingItems(bridge: adapter)
    }
}

// 당겨서 새로고침(refreshable)용 — refresh 패스가 끝날 때까지 시스템 스피너를 유지한다.
// 브리지에 완료 콜백이 없어 LoadState를 폴링한다 — 이벤트 경유 refresh()가 반영될 틈을 먼저 준다.
extension LazyPagingItems {
    @MainActor func awaitRefresh() async {
        try? await Task.sleep(nanoseconds: 300_000_000)
        while loadState.refresh is LoadState.Loading {
            try? await Task.sleep(nanoseconds: 100_000_000)
        }
    }
}

// Kotlin FlowAdapter(콜드 Flow)를 Combine Publisher로 감싸는 어댑터
struct KotlinFlowPublisher<Output>: Publisher {
    typealias Failure = Never

    private let subscribe: (@escaping (Output) -> Void) -> FlowSubscription

    func receive<S>(subscriber: S) where S: Subscriber, S.Input == Output, S.Failure == Never {
        subscriber.receive(subscription: KotlinFlowSubscription(subscribe: subscribe, subscriber: subscriber))
    }

    init(_ subscribe: @escaping (@escaping (Output) -> Void) -> FlowSubscription) {
        self.subscribe = subscribe
    }
}

private final class KotlinFlowSubscription<S: Subscriber>: Subscription where S.Failure == Never {
    private let subscribe: (@escaping (S.Input) -> Void) -> FlowSubscription

    private var subscriber: S?

    private var kotlinSubscription: FlowSubscription?

    func request(_ demand: Subscribers.Demand) {
        guard kotlinSubscription == nil, let subscriber = subscriber else { return }

        kotlinSubscription = subscribe { value in
            _ = subscriber.receive(value)
        }
    }

    func cancel() {
        kotlinSubscription?.cancel()
        kotlinSubscription = nil
        subscriber = nil
    }

    init(subscribe: @escaping (@escaping (S.Input) -> Void) -> FlowSubscription, subscriber: S) {
        self.subscribe = subscribe
        self.subscriber = subscriber
    }
}
