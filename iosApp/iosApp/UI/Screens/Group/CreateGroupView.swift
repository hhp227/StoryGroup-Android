import SwiftUI
import Shared

/// 그룹 만들기 — Compose CreateGroupScreen 미러(이름/소개/커버 이미지+가입 방식).
/// GroupsView가 풀스크린 push로 표시(Compose NavHost CreateGroupRoute 미러) — 내비바는 루트 스택 몫.
/// 그룹 탭과 같은 GroupsViewModel 인스턴스를 그대로 전달받아 성공 시 refresh()한다.
struct CreateGroupView: View {
    @StateObject private var viewModel = CreateGroupViewModel()

    /// 갱신용 — 그룹 탭과 같은 인스턴스(GroupsView가 소유). onAction 호출만 하므로 관찰 불필요
    private let groupsViewModel: GroupsViewModel

    @Environment(\.dismiss) private var dismiss

    @Environment(\.sgColors) private var colors

    @State private var name = ""

    @State private var description = ""

    @State private var joinType: GroupJoinType = .autoApprove

    @State private var showImagePicker = false

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                SGCard {
                    VStack(alignment: .leading, spacing: 12) {
                        coverImagePicker
                        SGTextField(label: "그룹 이름", text: $name, enabled: !viewModel.uiState.isSaving)
                            .onChange(of: name) { if $0.count > 100 { name = String($0.prefix(100)) } }
                        VStack(alignment: .leading, spacing: 6) {
                            Text("그룹 소개").font(.caption.bold()).foregroundColor(colors.inkSoft)
                            TextEditor(text: $description)
                                .frame(minHeight: 96)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(RoundedRectangle(cornerRadius: 10).stroke(colors.stoneBorder, lineWidth: 1))
                                .disabled(viewModel.uiState.isSaving)
                                .onChange(of: description) { if $0.count > 1000 { description = String($0.prefix(1000)) } }
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            Text("가입 방식").font(.caption.bold()).foregroundColor(colors.inkSoft)
                            joinTypeRow("자동 승인 — 바로 가입", isSelected: joinType == .autoApprove) { joinType = .autoApprove }
                            joinTypeRow("승인제 — 신청 후 승인", isSelected: joinType == .approvalRequired) { joinType = .approvalRequired }
                        }
                        if let error = viewModel.uiState.error {
                            Text(error).font(.caption).foregroundColor(colors.rust)
                        }
                    }
                    .padding(16)
                }
            }
            .padding(16)
        }
        .background(colors.paper)
        .navigationTitle("그룹 만들기")
        .navigationBarTitleDisplayMode(.inline)
        // 호출 화면이 투명 바(커버 펼침) 상태로 push해도 이 화면은 기본 내비바 — 복귀 시엔 호출 화면이 재적용
        .navigationBarScrim(visible: true)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("만들기") { viewModel.onAction(.submit(name: name, description: description, joinType: joinType)) }
                    .disabled(viewModel.uiState.isSaving || viewModel.uiState.isUploadingImage)
            }
        }
        .sheet(isPresented: $showImagePicker) {
            ImagePicker { data, fileName, contentType in
                viewModel.onAction(.changeCoverImage(data: data, fileName: fileName, contentType: contentType))
            }
        }
        .onReceive(viewModel.event) { event in
            switch event {
            case .created:
                groupsViewModel.onAction(.refresh)
                dismiss()
            }
        }
    }

    private var coverImagePicker: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10).fill(colors.linen)
            if let imageUrlString = viewModel.uiState.image, let url = URL(string: imageUrlString) {
                AsyncImage(url: url) { phase in
                    if case .success(let image) = phase {
                        image.resizable().scaledToFill()
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 120)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            if viewModel.uiState.isUploadingImage {
                Color.black.opacity(0.4)
                ProgressView().tint(colors.onAccent)
            } else if viewModel.uiState.image == nil {
                VStack(spacing: 4) {
                    Image(systemName: "camera.fill").foregroundColor(colors.inkSoft)
                    Text("커버 이미지 추가").font(.caption).foregroundColor(colors.inkSoft)
                }
            }
        }
        .frame(height: 120)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .contentShape(RoundedRectangle(cornerRadius: 10))
        .onTapGesture {
            if !viewModel.uiState.isUploadingImage { showImagePicker = true }
        }
    }

    private func joinTypeRow(_ label: String, isSelected: Bool, onSelect: @escaping () -> Void) -> some View {
        Button(action: onSelect) {
            HStack(spacing: 10) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .foregroundColor(isSelected ? colors.accent : colors.inkSoft)
                Text(label).font(.subheadline).foregroundColor(colors.ink)
                Spacer()
            }
        }
        .buttonStyle(.plain)
    }

    init(groupsViewModel: GroupsViewModel) {
        self.groupsViewModel = groupsViewModel
    }
}
