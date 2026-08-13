import Combine
import Foundation
import Shared
// SwiftUI.Group(뷰)과 도메인 모델 Group의 동명 충돌 — 이 파일의 Group은 도메인 모델로 고정
import class Shared.Group

/// 그룹 정보 수정 — composeApp GroupEditViewModel.kt와 1:1 미러(웹 /groups/[id]/settings 폼,
/// 설정 탭 "그룹 정보 수정" 행에서 진입하는 풀스크린). 진입 시 GetGroup으로 self-load해 폼을 시드한다.
/// ⚠️PATCH /api/groups/{id}는 name/description/image 전체 교체 계약 — 폼이 로드해 온 값을
/// 항상 실어 보낸다(joinType만 nil=유지, 라운지가 이 경로를 쓴다).
final class GroupEditViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    let groupId: Int64

    private let getGroupUseCase: GetGroupUseCase

    private let updateGroupUseCase: UpdateGroupUseCase

    private let uploadImageUseCase: UploadImageUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        case .setName(let name):
            uiState.name = String(name.prefix(100))
        case .setDescription(let description):
            uiState.description = String(description.prefix(1000))
        case .setJoinType(let joinType):
            uiState.joinType = joinType
        case .changeImage(let bytes, let fileName, let contentType):
            changeImage(bytes: bytes, fileName: fileName, contentType: contentType)
        case .save: save()
        }
    }

    /// 진입(init)·재시도 시 발화 — 그룹을 읽어 폼을 시드한다(웹 설정 페이지 getGroup 미러)
    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let group = try await getGroupUseCase.invoke(groupId: groupId)
                uiState.isLoading = false
                uiState.group = group
                uiState.name = group.name
                uiState.description = group.description_ ?? ""
                uiState.image = group.image
                uiState.joinType = group.joinType
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "그룹 정보를 불러오지 못했습니다.")
            }
        }
    }

    /// 대표 이미지 교체 — 계정 설정 아바타 패턴(업로드 성공 시 URL만 폼 상태에 반영, 저장은 별도)
    private func changeImage(bytes: Data, fileName: String, contentType: String) {
        if uiState.isUploadingImage { return }

        uiState.isUploadingImage = true
        uiState.saveError = nil
        Task { @MainActor in
            do {
                let url = try await uploadImageUseCase.invoke(
                    bytes: bytes.toKotlinByteArray(),
                    fileName: fileName,
                    contentType: contentType
                )
                uiState.isUploadingImage = false
                uiState.image = url
            } catch {
                uiState.isUploadingImage = false
                uiState.saveError = error.kotlinMessage(fallback: "이미지 업로드에 실패했습니다.")
            }
        }
    }

    /// 저장 — ⚠️전체 교체 계약이라 4필드 전부 전송, 라운지는 joinType을 안 보낸다(nil=유지)
    private func save() {
        guard let group = uiState.group else { return }
        if uiState.isSaving || uiState.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return }

        uiState.isSaving = true
        uiState.saveError = nil
        let trimmedName = uiState.name.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedDescription = uiState.description.trimmingCharacters(in: .whitespacesAndNewlines)
        let image = (uiState.image?.isEmpty ?? true) ? nil : uiState.image
        let joinType = group.isLounge ? nil : uiState.joinType
        Task { @MainActor in
            do {
                _ = try await updateGroupUseCase.invoke(
                    groupId: groupId,
                    name: trimmedName,
                    description: trimmedDescription.isEmpty ? nil : trimmedDescription,
                    image: image,
                    joinType: joinType
                )
                uiState.isSaving = false
                // 성공 문구 없이 바로 닫는다 — 화면이 onSaved로 결과 신호+pop을 요청한다
                event.send(.saved)
            } catch {
                uiState.isSaving = false
                uiState.saveError = error.kotlinMessage(fallback: "저장에 실패했습니다.")
            }
        }
    }

    init(
        groupId: Int64,
        getGroupUseCase: GetGroupUseCase,
        updateGroupUseCase: UpdateGroupUseCase,
        uploadImageUseCase: UploadImageUseCase
    ) {
        self.groupId = groupId
        self.getGroupUseCase = getGroupUseCase
        self.updateGroupUseCase = updateGroupUseCase
        self.uploadImageUseCase = uploadImageUseCase
        refresh()
    }

    struct UiState {
        // 로드 원본 — 라운지 분기(가입 방식 숨김·joinType 미전송)의 기준
        var group: Group? = nil
        var isLoading = false
        var error: String? = nil
        // 폼 상태 — 로드 성공 시 시드, 이후 사용자 입력이 이긴다(재시도 Refresh는 다시 시드)
        var name: String = ""
        var description: String = ""
        var image: String? = nil
        var joinType: GroupJoinType = .autoApprove
        var isUploadingImage = false
        var isSaving = false
        var saveError: String? = nil

        var isLounge: Bool { group?.isLounge == true }
    }

    enum Action {
        case refresh
        case setName(String)
        case setDescription(String)
        case setJoinType(GroupJoinType)
        case changeImage(bytes: Data, fileName: String, contentType: String)
        case save
    }

    enum Event {
        /// 저장 성공 — 화면이 onSaved로 결과 신호+pop을 요청한다
        case saved
    }
}
