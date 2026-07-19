import Combine
import Foundation
import Shared

/// 그룹 상세 — composeApp GroupDetailViewModel.kt와 1:1 미러(Paging-CRUD 샘플 패턴).
/// 커버+멤버는 UiState 필드, 피드는 UiState에 담기는 최신 PagingData.
/// groupId만 받아 스스로 로드한다 — 목록이 페이징으로 바뀌어 스냅샷 lookup이 불가(로드 전 group은 nil).
final class GroupDetailViewModel: MviViewModel {
    typealias Event = Never

    @Published private(set) var uiState = UiState()

    let groupId: Int64

    private let getGroupUseCase: GetGroupUseCase

    private let getGroupMembersUseCase: GetGroupMembersUseCase

    private var cancellables = Set<AnyCancellable>()

    private func setPagingData(_ pagingData: PagingData<Post>) {
        uiState.pagingData = pagingData
    }

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        }
    }

    /// 상세 진입 시 발화 — 그룹+멤버 로드(피드는 Pager가 자체 로드/재시도)
    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let group = try await getGroupUseCase.invoke(groupId: groupId)
                let members = try await getGroupMembersUseCase.invoke(groupId: groupId)
                uiState.isLoading = false
                uiState.group = group
                uiState.members = members
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "그룹을 불러오지 못했습니다.")
            }
        }
    }

    init(container: AppContainer, groupId: Int64) {
        self.groupId = groupId
        getGroupUseCase = container.getGroupUseCase
        getGroupMembersUseCase = container.getGroupMembersUseCase

        // UseCase는 cachedIn 없는 Flow를 반환하므로 프레젠테이션 경계인 여기서 캐시를 적용한다
        // (Kotlin: useCase(groupId).cachedIn(viewModelScope).onEach(::setPagingData).launchIn)
        container.getGroupPostsPagingDataUseCase(groupId: groupId)
            .cachedIn()
            .sink { [weak self] in self?.setPagingData($0) }
            .store(in: &cancellables)
    }

    struct UiState {
        // 로드 전 nil — 화면은 그룹 정보 자리만 비워 두고 커버/피드를 먼저 그린다
        var group: Group? = nil
        // Kotlin의 PagingData.empty() 대응 — ObjC 제네릭 클래스에는 static 확장을 못 붙여 브리지 함수 직접 호출
        var pagingData: PagingData<Post> = PostBridgesKt.emptyPostPagingData()
        var members: [GroupMember] = []
        var isLoading = false
        var error: String? = nil
    }

    enum Action {
        case refresh
    }
}
