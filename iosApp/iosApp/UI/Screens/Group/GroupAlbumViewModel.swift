import Combine
import Foundation
import Shared

/// 앨범 탭 — composeApp GroupAlbumViewModel.kt와 1:1 미러. 페이징 스트림 하나가 전부다.
/// 갱신은 VM이 스트림을 통째로 갈아끼우는 방식 — Compose 쪽 상세는 NavHost 목적지라 글쓰기
/// 복귀 때 화면이 새로 만들어지는데, 그 첫 프레임엔 일회성 Event도 프레젠터 refresh()도 닿지
/// 못한다(피드 탭과 동일 규약).
final class GroupAlbumViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    // 스트림을 통째로 갈아끼우는 트리거 — 복귀 첫 프레임에 유실되지 않도록 이벤트가 아닌 상태다
    private let refreshTrigger = CurrentValueSubject<Int, Never>(0)

    let groupId: Int64

    private var cancellables = Set<AnyCancellable>()

    func onAction(_ action: Action) {
        switch action {
        // 글쓰기(사진 첨부) 성공·당겨서 새로고침 — 새 PagingSource가 첫 페이지부터 다시 읽는다
        case .refresh: refreshTrigger.send(refreshTrigger.value + 1)
        }
    }

    init(groupId: Int64, getGroupPhotosPagingDataUseCase: GetGroupPhotosPagingDataUseCase = AppContainer.shared.getGroupPhotosPagingDataUseCase) {
        self.groupId = groupId

        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        // (Kotlin: refreshTrigger.flatMapLatest { useCase(groupId) }.cachedIn(viewModelScope))
        refreshTrigger
            .map { _ in getGroupPhotosPagingDataUseCase(groupId: groupId).cachedIn() }
            .switchToLatest()
            .sink { [weak self] in self?.uiState.photosPagingData = $0 }
            .store(in: &cancellables)
    }

    struct UiState {
        var photosPagingData: PagingData<GroupPhoto> = GroupBridgesKt.emptyGroupPhotoPagingData()
    }

    enum Action {
        case refresh
    }
}
