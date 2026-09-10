import Combine
import Foundation
import Shared

/// 앨범 탭 — composeApp GroupAlbumViewModel.kt와 1:1 미러. 페이징 스트림 하나가 전부다.
/// 갱신은 화면이 Event를 받아 프레젠터 refresh()로 수행한다 — 뷰가 직접 refresh()를 부르면
/// 복귀 직후 프레젠터가 아직 첫 PagingData를 받기 전이라 호출이 유실된다(피드 탭과 동일 규약).
final class GroupAlbumViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    let groupId: Int64

    private var cancellables = Set<AnyCancellable>()

    func onAction(_ action: Action) {
        switch action {
        // 글쓰기(사진 첨부) 성공·당겨서 새로고침 시 발화 — 화면이 refresh()로 첫 페이지부터 다시 읽는다
        case .refresh: event.send(.refresh)
        }
    }

    init(groupId: Int64, getGroupPhotosPagingDataUseCase: GetGroupPhotosPagingDataUseCase = AppContainer.shared.getGroupPhotosPagingDataUseCase) {
        self.groupId = groupId

        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        getGroupPhotosPagingDataUseCase(groupId: groupId)
            .cachedIn()
            .sink { [weak self] in self?.uiState.photosPagingData = $0 }
            .store(in: &cancellables)
    }

    struct UiState {
        var photosPagingData: PagingData<GroupPhoto> = GroupBridgesKt.emptyGroupPhotoPagingData()
    }

    enum Action {
        case refresh
    }

    enum Event {
        case refresh
    }
}
