import Combine
import Foundation
import Shared

/// 앨범 탭 — composeApp GroupAlbumViewModel.kt와 1:1 미러. 페이징 스트림 하나가 전부라
/// 액션·이벤트가 둘 다 없다(Action/Event=Never — 호출 불가).
final class GroupAlbumViewModel: MviViewModel {
    typealias Action = Never
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    let groupId: Int64

    private var cancellables = Set<AnyCancellable>()

    func onAction(_ action: Never) {}

    init(groupId: Int64, getGroupPhotosPagingDataUseCase: GetGroupPhotosPagingDataUseCase) {
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
}
