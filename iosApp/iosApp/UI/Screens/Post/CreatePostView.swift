import SwiftUI

/// 게시글 작성 — Compose CreatePostScreen 미러(웹 작성 폼 + 하단 사진·동영상 첨부 행).
/// 홈/그룹 상세가 풀스크린 push로 표시(Compose NavHost CreatePostRoute 미러) — 내비바는 루트 스택 몫.
/// 성공 Event 수신 시 onCreated(피드 갱신) 후 닫힌다 — Paging-CRUD 샘플 CreateView 미러.
struct CreatePostView: View {
    @StateObject private var viewModel: CreatePostViewModel

    @Environment(\.dismiss) private var dismiss

    @Environment(\.sgColors) private var colors

    @State private var text = ""

    /// 지금 열려 있는 피커 — .sheet를 두 개 달면 뒤엣것이 앞엣것을 덮어써서 하나로 합쳤다
    @State private var activePicker: ActivePicker?

    private let onCreated: () -> Void

    var body: some View {
        // 레거시 fragment_create_post 미러 — 리스트[본문 입력 + 첨부가 순서대로 append] + 1px 구분선 + 첨부 버튼 바
        VStack(spacing: 0) {
            ZStack {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        if let error = viewModel.uiState.error {
                            Text(error)
                                .font(.caption)
                                .foregroundColor(colors.rust)
                        }
                        growingTextEditor
                        ForEach(viewModel.uiState.attachments, id: \.url) { attachment in
                            attachmentItem(attachment)
                        }
                    }
                    .padding()
                }
                if viewModel.uiState.isLoading {
                    ProgressView()
                }
            }
            Divider().background(colors.stoneBorder)
            attachBar
        }
        .background(colors.paper)
        .navigationTitle(viewModel.uiState.isEditMode ? "글 수정" : "글쓰기")
        // 수정 모드에서 기존 본문이 도착하면 입력창에 한 번 채운다(그 뒤 편집은 사용자 몫)
        .onChange(of: viewModel.uiState.loadedText) { loaded in
            if let loaded = loaded { text = loaded }
        }
        .navigationBarTitleDisplayMode(.inline)
        // 홈이 최상단(투명 바) 상태에서 push돼도 기본 내비바로 표시 — 복귀 시엔 호출 화면이 재적용
        .navigationBarScrim(visible: true)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(viewModel.uiState.isEditMode ? "수정" : "등록") { viewModel.onAction(.submit(text: text)) }
                    // 업로드가 끝나기 전에 등록하면 그 첨부가 빠진 채 저장된다
                    .disabled(
                        viewModel.uiState.isLoading
                            || viewModel.uiState.isUploadingImage
                            || viewModel.uiState.isUploadingVideo
                    )
            }
        }
        .sheet(item: $activePicker) { picker in
            switch picker {
            case .image:
                ImagePicker { data, fileName, contentType in
                    viewModel.onAction(.addImage(data: data, fileName: fileName, contentType: contentType))
                }
            case .video:
                ImagePicker(mode: .video) { data, fileName, contentType in
                    viewModel.onAction(.addVideo(data: data, fileName: fileName, contentType: contentType))
                }
            }
        }
        .onReceive(viewModel.event) { event in
            switch event {
            case .created:
                onCreated()
                dismiss()
            }
        }
    }

    /// 배경·테두리 없는 본문 입력(레거시 input_text 미러) — TextEditor는 iOS 15에서 내용만큼 자라지 않아
    /// 같은 글꼴의 보이지 않는 Text를 사이징 미러로 깔아 높이를 만든다(내부 스크롤이 생기지 않게)
    private var growingTextEditor: some View {
        ZStack(alignment: .topLeading) {
            Text(text.isEmpty ? " " : text)
                .padding(.vertical, 8)
                .padding(.horizontal, 5)
                .frame(maxWidth: .infinity, alignment: .leading)
                .opacity(0)
            TextEditor(text: Binding(
                get: { text },
                set: {
                    text = $0
                    if viewModel.uiState.error != nil { viewModel.onAction(.clearError) }
                }
            ))
            .disabled(viewModel.uiState.isLoading)
            if text.isEmpty {
                Text("무슨 이야기가 있나요?")
                    .foregroundColor(colors.inkFaint)
                    .padding(.top, 8)
                    .padding(.leading, 4)
                    .allowsHitTesting(false)
            }
        }
        .frame(minHeight: 160, alignment: .topLeading)
    }

    /// 첨부 한 아이템 — 리스트 폭을 꽉 채우는 실비율 미리보기(레거시 input_contents 미러) + 우상단 제거 버튼
    @ViewBuilder
    private func attachmentItem(_ attachment: CreatePostViewModel.Attachment) -> some View {
        ZStack(alignment: .topTrailing) {
            if attachment.isVideo {
                SGVideoPoster(urlString: attachment.url)
            } else if let url = URL(string: attachment.url) {
                AsyncImage(url: url) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFit()
                    } else {
                        colors.linen.frame(height: 180)
                    }
                }
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            Button(action: { viewModel.onAction(.removeAttachment(url: attachment.url)) }) {
                Image(systemName: "xmark.circle.fill")
                    .foregroundColor(.white)
                    .background(Circle().fill(colors.ink))
            }
            .padding(8)
        }
    }

    /// 하단 첨부 버튼 바 — 레거시 ib_image/ib_video 미러(업로드 중 스피너, 타입별 상한 도달 시 비활성)
    private var attachBar: some View {
        HStack(spacing: 4) {
            attachBarButton(
                systemImage: "camera.fill",
                isUploading: viewModel.uiState.isUploadingImage,
                canAddMore: viewModel.uiState.images.count < CreatePostViewModel.maxImages
            ) { activePicker = .image }
            attachBarButton(
                systemImage: "video.badge.plus",
                isUploading: viewModel.uiState.isUploadingVideo,
                canAddMore: viewModel.uiState.videos.count < CreatePostViewModel.maxVideos
            ) { activePicker = .video }
            Spacer()
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }

    private func attachBarButton(
        systemImage: String,
        isUploading: Bool,
        canAddMore: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            ZStack {
                if isUploading {
                    ProgressView()
                } else {
                    Image(systemName: systemImage)
                        .foregroundColor(canAddMore ? colors.inkSoft : colors.inkFaint)
                }
            }
            .frame(width: 44, height: 44)
        }
        .disabled(!canAddMore || isUploading)
    }

    /// postId가 있으면 같은 폼이 수정 모드로 동작한다(Compose CreatePostScreen 미러)
    init(container: AppContainer, groupId: Int64?, postId: Int64? = nil, onCreated: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: CreatePostViewModel(
            groupId: groupId,
            postId: postId,
            createPostUseCase: container.createPostUseCase,
            createLoungePostUseCase: container.createLoungePostUseCase,
            uploadImageUseCase: container.uploadImageUseCase,
            uploadVideoUseCase: container.uploadVideoUseCase,
            getPostUseCase: container.getPostUseCase,
            updatePostUseCase: container.updatePostUseCase
        ))
        self.onCreated = onCreated
    }

    private enum ActivePicker: Int, Identifiable {
        case image
        case video

        var id: Int { rawValue }
    }
}
