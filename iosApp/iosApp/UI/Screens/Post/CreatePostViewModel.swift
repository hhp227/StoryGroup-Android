import Combine
import Foundation
import Shared

/// 게시글 작성 — composeApp CreatePostViewModel.kt와 1:1 미러.
/// groupId가 nil이면 라운지(홈 피드)에 게시한다(웹 메인 피드 폼 미러).
/// 이미지·동영상은 선택 즉시 업로드해 첨부한 순서대로 attachments에 쌓아두고, 등록 시
/// images/videos로 갈라 전송한다(레거시 WriteListAdapter itemList 미러 — 화면이 이 순서로 리스트에 그린다).
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

    private let uploadVideoUseCase: UploadVideoUseCase

    func onAction(_ action: Action) {
        switch action {
        case .submit(let text): submit(text: text)
        case .clearError: uiState.error = nil
        case .addImage(let data, let fileName, let contentType): addImage(data: data, fileName: fileName, contentType: contentType)
        case .addVideo(let picked): addVideo(picked: picked)
        case .removeAttachment(let url): uiState.attachments.removeAll { $0.url == url }
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
                uiState.attachments.append(Attachment(url: url, isVideo: false))
            } catch {
                uiState.isUploadingImage = false
                uiState.error = error.kotlinMessage(fallback: "이미지 업로드에 실패했습니다.")
            }
        }
    }

    private func addVideo(picked: PickedVideo) {
        if uiState.videos.count >= Self.maxVideos { return }
        // 올려놓고 서버 거절을 기다리게 하지 않는다 — 판정(§2)은 선택 즉시, 압축은 백그라운드 큐
        let plan = VideoCompressionPlanner.shared.plan(
            durationMs: picked.durationMs, sizeBytes: picked.sizeBytes,
            width: picked.width, height: picked.height,
            margin: VideoCompressionPlanner.shared.FIRST_MARGIN
        )
        if plan is VideoPlanRejectTooLarge {
            uiState.error = "파일이 너무 큽니다. (최대 500MB)"
        } else if plan is VideoPlanRejectTooLong {
            uiState.error = "동영상은 최대 3분까지 첨부할 수 있습니다."
        } else if plan is VideoPlanSkipAlreadySmall {
            // 원본이 이미 5MB 이하 — 재인코딩은 시간 낭비+화질 손실(§2-3)
            let bytes = (try? Data(contentsOf: picked.url)) ?? Data()
            try? FileManager.default.removeItem(at: picked.url)
            uploadVideoBytes(bytes, fileName: picked.fileName, contentType: picked.contentType)
        } else if let compress = plan as? VideoPlanCompress {
            compressAndUpload(picked: picked, plan: compress, isRetry: false)
        }
    }

    /// 압축 → 5MB 초과면 RETRY_MARGIN으로 1회 재플랜 → 업로드(Compose compressAndUpload 미러)
    private func compressAndUpload(picked: PickedVideo, plan: VideoPlanCompress, isRetry: Bool) {
        uiState.compressionProgress = 0
        uiState.error = nil
        MediaCompressionQueue.shared.compressVideo(
            inputURL: picked.url,
            plan: plan,
            onProgress: { [weak self] fraction in self?.uiState.compressionProgress = fraction }
        ) { [weak self] result in
            guard let self = self else { return }
            switch result {
            case .failure:
                try? FileManager.default.removeItem(at: picked.url)
                self.uiState.compressionProgress = nil
                self.uiState.error = "동영상 압축에 실패했습니다."
            case .success(let outputURL):
                let bytes = (try? Data(contentsOf: outputURL)) ?? Data()
                try? FileManager.default.removeItem(at: outputURL)
                if Int64(bytes.count) <= VideoCompressionPlanner.shared.TARGET_BYTES {
                    try? FileManager.default.removeItem(at: picked.url)
                    self.uiState.compressionProgress = nil
                    // 출력은 항상 MP4(§2) — 원본 확장자와 무관
                    self.uploadVideoBytes(bytes, fileName: "upload.mp4", contentType: "video/mp4")
                } else if !isRetry {
                    // 단일 패스 ABR 오버슈트 — 더 보수적인 마진으로 딱 한 번 재시도(§2-5)
                    let retry = VideoCompressionPlanner.shared.plan(
                        durationMs: picked.durationMs, sizeBytes: picked.sizeBytes,
                        width: picked.width, height: picked.height,
                        margin: VideoCompressionPlanner.shared.RETRY_MARGIN
                    )
                    if let retryPlan = retry as? VideoPlanCompress {
                        self.compressAndUpload(picked: picked, plan: retryPlan, isRetry: true)
                    } else {
                        try? FileManager.default.removeItem(at: picked.url)
                        self.uiState.compressionProgress = nil
                        self.uiState.error = "동영상 압축에 실패했습니다."
                    }
                } else {
                    try? FileManager.default.removeItem(at: picked.url)
                    self.uiState.compressionProgress = nil
                    self.uiState.error = "동영상 압축에 실패했습니다."
                }
            }
        }
    }

    private func uploadVideoBytes(_ bytes: Data, fileName: String, contentType: String) {
        uiState.isUploadingVideo = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let url = try await uploadVideoUseCase.invoke(
                    bytes: bytes.toKotlinByteArray(),
                    fileName: fileName,
                    contentType: contentType
                )
                uiState.isUploadingVideo = false
                uiState.attachments.append(Attachment(url: url, isVideo: true))
            } catch {
                uiState.isUploadingVideo = false
                uiState.error = error.kotlinMessage(fallback: "동영상 업로드에 실패했습니다.")
            }
        }
    }

    private func submit(text: String) {
        if uiState.isLoading { return }

        let images = uiState.images
        let videos = uiState.videos
        // 본문/첨부 중 하나는 필수 — 백엔드 규칙과 일치(웹 폼의 required={images.length===0} 미러)
        if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, images.isEmpty, videos.isEmpty {
            uiState.error = "내용을 입력하거나 사진·동영상을 추가해주세요."
            return
        }
        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                // 수정은 라운지 글도 그 글의 groupId로 들어오므로 groupId가 항상 있다
                if let groupId = groupId, let postId = postId {
                    _ = try await updatePostUseCase.invoke(
                        groupId: groupId,
                        postId: postId,
                        text: text,
                        images: images,
                        videos: videos
                    )
                } else if let groupId = groupId {
                    _ = try await createPostUseCase.invoke(groupId: groupId, text: text, images: images, videos: videos)
                } else {
                    _ = try await createLoungePostUseCase.invoke(text: text, images: images, videos: videos)
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
        uploadVideoUseCase: UploadVideoUseCase,
        getPostUseCase: GetPostUseCase,
        updatePostUseCase: UpdatePostUseCase
    ) {
        self.groupId = groupId
        self.postId = postId
        self.createPostUseCase = createPostUseCase
        self.createLoungePostUseCase = createLoungePostUseCase
        self.uploadImageUseCase = uploadImageUseCase
        self.uploadVideoUseCase = uploadVideoUseCase
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
                    // ⚠️videos도 반드시 채운다 — 저장이 전체 교체라 비워둔 채 보내면
                    // 웹에서 올린 동영상이 수정 한 번에 전부 삭제된다(images와 같은 이유).
                    // 서버엔 타입 간 순서 정보가 없어 이미지들 뒤에 동영상들을 잇는다(상세 표시 순서와 동일)
                    uiState.attachments = post.imageUrls.map { Attachment(url: $0, isVideo: false) }
                        + post.videoUrls.map { Attachment(url: $0, isVideo: true) }
                    uiState.loadedText = post.text
                } catch {
                    uiState.isLoading = false
                    uiState.error = error.kotlinMessage(fallback: "게시글을 불러오지 못했습니다.")
                }
            }
        }
    }

    /// 첨부 한 건 — 화면이 첨부한 순서 그대로 리스트에 그린다(이미지/동영상 구분은 렌더링용)
    struct Attachment: Equatable {
        let url: String
        let isVideo: Bool
    }

    struct UiState {
        var isLoading = false
        var error: String? = nil
        /// 첨부 목록 — 업로드 성공 순서대로 append(레거시 itemList 미러)
        var attachments: [Attachment] = []
        var isUploadingImage = false
        var isUploadingVideo = false
        /// 동영상 압축 진행률(0..1) — nil이면 압축 중 아님. 업로드 단계는 isUploadingVideo가 따로 표시
        var compressionProgress: Float? = nil
        var isEditMode = false
        /// 수정 모드에서 읽어온 기존 본문 — 화면이 한 번 받아 입력창에 채운다(nil이면 아직 로드 전)
        var loadedText: String? = nil

        /// 서버 계약(images/videos 분리 전송)과 타입별 상한 판정용 파생 목록
        var images: [String] { attachments.filter { !$0.isVideo }.map(\.url) }
        var videos: [String] { attachments.filter(\.isVideo).map(\.url) }
    }

    enum Action {
        case submit(text: String)
        case clearError
        case addImage(data: Data, fileName: String, contentType: String)
        case addVideo(picked: PickedVideo)
        case removeAttachment(url: String)
    }

    enum Event {
        case created
    }

    // 서버는 개수 제한이 없지만 앱은 카드 레이아웃 감안해 클라 상한을 둔다(Compose MAX_IMAGES 미러).
    // 화면의 추가 버튼 비활성 조건과 공유하므로 private이 아니다.
    static let maxImages = 4

    static let maxVideos = 2

    deinit {
        // 화면 소멸 시 진행 중 압축 취소(§11) — Compose는 viewModelScope 취소가 같은 역할
        MediaCompressionQueue.shared.cancelAll()
    }
}
