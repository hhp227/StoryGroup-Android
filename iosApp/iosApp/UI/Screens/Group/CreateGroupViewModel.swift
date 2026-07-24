import Combine
import Foundation
import Shared

/// 그룹 만들기 — composeApp CreateGroupViewModel.kt와 1:1 미러.
/// 이름/소개/가입방식+커버 이미지(CreatePost 첨부와 동일하게 선택 즉시 업로드).
/// 성공은 Event.created 일회성 발화 — 화면이 세션 GroupsViewModel을 갱신하고 닫는다.
final class CreateGroupViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    private let createGroupUseCase: CreateGroupUseCase

    private let uploadImageUseCase: UploadImageUseCase

    func onAction(_ action: Action) {
        switch action {
        case .submit(let name, let description, let joinType):
            submit(name: name, description: description, joinType: joinType)
        case .clearError: uiState.error = nil
        case .changeCoverImage(let data, let fileName, let contentType):
            changeCoverImage(data: data, fileName: fileName, contentType: contentType)
        }
    }

    private func changeCoverImage(data: Data, fileName: String, contentType: String) {
        if uiState.isUploadingImage { return }

        uiState.isUploadingImage = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let url = try await uploadImageUseCase.invoke(
                    bytes: data.toKotlinByteArray(),
                    fileName: fileName,
                    contentType: contentType
                )
                uiState.isUploadingImage = false
                uiState.image = url
            } catch {
                uiState.isUploadingImage = false
                uiState.error = error.kotlinMessage(fallback: "이미지 업로드에 실패했습니다.")
            }
        }
    }

    private func submit(name: String, description: String, joinType: GroupJoinType) {
        if uiState.isSaving { return }

        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmedName.isEmpty {
            uiState.error = "그룹 이름을 입력해주세요."
            return
        }
        uiState.isSaving = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let group = try await createGroupUseCase.invoke(
                    name: trimmedName,
                    description: description.isEmpty ? nil : description,
                    image: uiState.image,
                    joinType: joinType
                )
                uiState.isSaving = false
                event.send(.created(group))
            } catch {
                uiState.isSaving = false
                uiState.error = error.kotlinMessage(fallback: "그룹 생성에 실패했습니다.")
            }
        }
    }

    init(container: AppContainer) {
        createGroupUseCase = container.createGroupUseCase
        uploadImageUseCase = container.uploadImageUseCase
    }

    struct UiState {
        var isSaving = false
        var error: String? = nil
        var image: String? = nil
        var isUploadingImage = false
    }

    enum Action {
        case submit(name: String, description: String, joinType: GroupJoinType)
        case clearError
        case changeCoverImage(data: Data, fileName: String, contentType: String)
    }

    enum Event {
        case created(Group)
    }
}
