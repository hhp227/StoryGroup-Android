import SwiftUI

/// 게시글 작성 — Compose CreatePostScreen 미러(웹 작성 폼 + 하단 사진 첨부 행).
/// 홈/그룹 상세가 풀스크린 push로 표시(Compose NavHost CreatePostRoute 미러) — 내비바는 루트 스택 몫.
/// 성공 Event 수신 시 onCreated(피드 갱신) 후 닫힌다 — Paging-CRUD 샘플 CreateView 미러.
struct CreatePostView: View {
    @StateObject private var viewModel: CreatePostViewModel

    @Environment(\.dismiss) private var dismiss

    @Environment(\.sgColors) private var colors

    @State private var text = ""

    @State private var showImagePicker = false

    private let onCreated: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                VStack(alignment: .leading, spacing: 8) {
                    if let error = viewModel.uiState.error {
                        Text(error)
                            .font(.caption)
                            .foregroundColor(colors.rust)
                    }
                    TextEditor(text: Binding(
                        get: { text },
                        set: {
                            text = $0
                            if viewModel.uiState.error != nil { viewModel.onAction(.clearError) }
                        }
                    ))
                    .disabled(viewModel.uiState.isLoading)
                    .overlay(alignment: .topLeading) {
                        if text.isEmpty {
                            Text("무슨 이야기가 있나요?")
                                .foregroundColor(colors.inkFaint)
                                .padding(.top, 8)
                                .padding(.leading, 4)
                                .allowsHitTesting(false)
                        }
                    }
                }
                .padding()
                if viewModel.uiState.isLoading {
                    ProgressView()
                }
            }
            imageAttachmentRow
        }
        .background(colors.paper)
        .navigationTitle("글쓰기")
        .navigationBarTitleDisplayMode(.inline)
        // 홈이 최상단(투명 바) 상태에서 push돼도 기본 내비바로 표시 — 복귀 시엔 호출 화면이 재적용
        .navigationBarScrim(visible: true)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("등록") { viewModel.onAction(.submit(text: text)) }
                    .disabled(viewModel.uiState.isLoading || viewModel.uiState.isUploadingImage)
            }
        }
        .sheet(isPresented: $showImagePicker) {
            ImagePicker { data, fileName, contentType in
                viewModel.onAction(.addImage(data: data, fileName: fileName, contentType: contentType))
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

    /// 첨부 미리보기(가로 스크롤 썸네일+제거)+추가 버튼 — 웹 ImageUploadField 미러(다중 첨부용으로 확장)
    private var imageAttachmentRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(viewModel.uiState.images, id: \.self) { urlString in
                    ZStack(alignment: .topTrailing) {
                        if let url = URL(string: urlString) {
                            AsyncImage(url: url) { phase in
                                if case .success(let image) = phase {
                                    image.resizable().scaledToFill()
                                } else {
                                    colors.linen
                                }
                            }
                            .frame(width: 72, height: 72)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                        Button(action: { viewModel.onAction(.removeImage(url: urlString)) }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(.white)
                                .background(Circle().fill(colors.ink))
                        }
                        .padding(4)
                    }
                }
                let canAddMore = viewModel.uiState.images.count < 4
                Button(action: { showImagePicker = true }) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 10).fill(colors.linen)
                        if viewModel.uiState.isUploadingImage {
                            ProgressView()
                        } else {
                            Image(systemName: "camera.fill")
                                .foregroundColor(canAddMore ? colors.inkSoft : colors.inkFaint)
                        }
                    }
                    .frame(width: 72, height: 72)
                }
                .disabled(!canAddMore || viewModel.uiState.isUploadingImage)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
    }

    init(container: AppContainer, groupId: Int64?, onCreated: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: CreatePostViewModel(
            groupId: groupId,
            createPostUseCase: container.createPostUseCase,
            createLoungePostUseCase: container.createLoungePostUseCase,
            uploadImageUseCase: container.uploadImageUseCase
        ))
        self.onCreated = onCreated
    }
}
