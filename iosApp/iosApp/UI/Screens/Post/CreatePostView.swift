import SwiftUI

/// 게시글 작성 시트 — Compose CreatePostScreen 미러(웹 작성 폼, 첨부는 후속).
/// 성공 Event 수신 시 onCreated(피드 갱신) 후 닫힌다 — Paging-CRUD 샘플 CreateView 미러.
struct CreatePostView: View {
    @StateObject private var viewModel: CreatePostViewModel

    @Environment(\.dismiss) private var dismiss

    @Environment(\.sgColors) private var colors

    @State private var text = ""

    private let onCreated: () -> Void

    var body: some View {
        NavigationView {
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
            .background(colors.paper)
            .navigationTitle("글쓰기")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("등록") { viewModel.onAction(.submit(text: text)) }
                        .disabled(viewModel.uiState.isLoading)
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
        .navigationViewStyle(.stack)
    }

    init(container: AppContainer, groupId: Int64?, onCreated: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: CreatePostViewModel(container: container, groupId: groupId))
        self.onCreated = onCreated
    }
}
