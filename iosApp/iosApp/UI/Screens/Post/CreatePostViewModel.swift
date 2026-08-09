import Combine
import Foundation
import Shared

/// 게시글 작성 — composeApp CreatePostViewModel.kt와 1:1 미러.
/// groupId가 nil이면 라운지(홈 피드)에 게시한다(웹 메인 피드 폼 미러).
/// 이미지는 선택 즉시 업로드해 URL을 UiState에 쌓아두고, 등록 시 함께 전송한다(웹 ImageUploadField 미러).
/// 성공은 Event.created 일회성 발화 — 호출부가 피드 갱신+닫기를 처리한다.
final class CreatePostViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    private let groupId: Int64?

    /// 있으면 수정 모드 — 같은 폼을 재사용한다(작성용 폼이 두 벌이 되지 않게)
    private let postId: Int64?

    private let getPostUseCase: GetPostUseCase

    private let updatePostUseCase: UpdatePostUseCase

    private let createPostUseCase: CreatePostUseCase

    private let createLoungePostUseCase: CreateLoungePostUseCase

    private let uploadImageUseCase: UploadImageUseCase

    func onAction(_ action: Action) {
        switch action {
        case .submit(let text): submit(text: text)
        case .clearError: uiState.error = nil
        case .addImage(let data, let fileName, let contentType): addImage(data: data, fileName: fileName, contentType: contentType)
        case .removeImage(let url): uiState.images.removeAll { $0 == url }
        }
    }

    private func addImage(data: Data, fileName: String, contentType: String) {
        if uiState.images.count >= Self.maxImages { return }

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
                uiState.images.append(url)
            } catch {
                uiState.isUploadingImage = false
                uiState.error = error.kotlinMessage(fallback: "이미지 업로드에 실패했습니다.")
            }
        }
    }

    private func submit(text: String) {
        if uiState.isLoading { return }

        let images = uiState.images
        // 본문/첨부 중 하나는 필수 — 백엔드 규칙과 일치(웹 폼의 required={images.length===0} 미러)
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, images.isEmpty {
            uiState.error = "내용을 입력하거나 사진을 추가해주세요."
            return
        }
        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                // 수정은 라운지 글도 그 글의 groupId로 들어오므로 groupId가 항상 있다
                if let groupId = groupId, let postId = postId {
                    _ = try await updatePostUseCase.invoke(groupId: groupId, postId: postId, text: text, images: images)
                } else if let groupId = groupId {
                    _ = try await createPostUseCase.invoke(groupId: groupId, text: text, images: images)
                } else {
                    _ = try await createLoungePostUseCase.invoke(text: text, images: images)
                }
                uiState.isLoading = false
                event.send(.created)
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(
                    fallback: postId != nil ? "게시글 수정에 실패했습니다." : "게시글 작성에 실패했습니다."
                )
            }
        }
    }

    init(
        groupId: Int64?,
        postId: Int64? = nil,
        createPostUseCase: CreatePostUseCase,
        createLoungePostUseCase: CreateLoungePostUseCase,
        uploadImageUseCase: UploadImageUseCase,
        getPostUseCase: GetPostUseCase,
        updatePostUseCase: UpdatePostUseCase
    ) {
        self.groupId = groupId
        self.postId = postId
        self.createPostUseCase = createPostUseCase
        self.createLoungePostUseCase = createLoungePostUseCase
        self.uploadImageUseCase = uploadImageUseCase
        self.getPostUseCase = getPostUseCase
        self.updatePostUseCase = updatePostUseCase
        self.uiState.isEditMode = postId != nil
        // 수정 모드면 기존 본문·첨부를 읽어와 폼을 채운다
        if let groupId = groupId, let postId = postId {
            uiState.isLoading = true
            Task { @MainActor in
                do {
                    let post = try await getPostUseCase.invoke(groupId: groupId, postId: postId)
                    uiState.isLoading = false
                    uiState.images = post.imageUrls
                    uiState.loadedText = post.text
                } catch {
                    uiState.isLoading = false
                    uiState.error = error.kotlinMessage(fallback: "게시글을 불러오지 못했습니다.")
                }
            }
        }
    }

    struct UiState {
        var isLoading = false
        var error: String? = nil
        var images: [String] = []
        var isUploadingImage = false
        var isEditMode = false
        /// 수정 모드에서 읽어온 기존 본문 — 화면이 한 번 받아 입력창에 채운다(nil이면 아직 로드 전)
        var loadedText: String? = nil
    }

    enum Action {
        case submit(text: String)
        case clearError
        case addImage(data: Data, fileName: String, contentType: String)
        case removeImage(url: String)
    }

    enum Event {
        case created
    }

    // 서버는 개수 제한이 없지만 앱은 카드 레이아웃 감안해 클라 상한을 둔다(Compose MAX_IMAGES 미러)
    private static let maxImages = 4
}
