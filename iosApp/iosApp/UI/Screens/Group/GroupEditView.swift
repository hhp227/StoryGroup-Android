import SwiftUI
import Shared

/// 그룹 정보 수정 — composeApp GroupEditScreen.kt와 1:1 미러(설정 탭 메뉴에서 진입하는
/// 풀스크린, 계정 설정 패턴). 내비바는 루트 스택 몫. 저장 성공 시 onSaved — 부모가
/// pop+상세 갱신을 처리한다(Compose NavResult.GroupUpdated pendingResults 미러).
struct GroupEditView: View {
    @StateObject private var viewModel: GroupEditViewModel

    /// 저장 성공 — 부모(GroupDetailContent)가 pop+상세·설정 탭 refresh+목록 갱신 신호를 처리한다
    private let onSaved: () -> Void

    @Environment(\.sgColors) private var colors

    /// 대표 이미지 피커 — 계정 설정 아바타 패턴 재사용(PHPickerViewController, 권한 불필요)
    @State private var showImagePicker = false

    var body: some View {
        ScrollView {
            content.padding(16)
        }
        .background(colors.paper.ignoresSafeArea())
        .navigationTitle("그룹 정보 수정")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showImagePicker) {
            ImagePicker { data, fileName, contentType in
                viewModel.onAction(.changeImage(bytes: data, fileName: fileName, contentType: contentType))
            }
        }
        .onReceive(viewModel.event) { event in
            switch event {
            case .saved: onSaved()
            }
        }
    }

    @ViewBuilder private var content: some View {
        let uiState = viewModel.uiState

        if uiState.group == nil, uiState.isLoading {
            ProgressView().padding(.vertical, 48)
        } else if uiState.group == nil {
            VStack(spacing: 8) {
                Text(uiState.error ?? "그룹 정보를 불러오지 못했습니다.")
                    .font(.subheadline)
                    .foregroundColor(colors.rust)
                Button("다시 시도") { viewModel.onAction(.refresh) }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 48)
        } else {
            form(uiState)
        }
    }

    /// 그룹 정보 수정 폼 — 웹 GroupSettingsForm 미러(기존 설정 탭 ownerForm 이동)
    @ViewBuilder private func form(_ uiState: GroupEditViewModel.UiState) -> some View {
        SGCard {
            VStack(alignment: .leading, spacing: 12) {
                SGTextField(
                    label: "그룹 이름",
                    text: Binding(get: { uiState.name }, set: { viewModel.onAction(.setName($0)) })
                )
                VStack(alignment: .leading, spacing: 6) {
                    Text("설명").font(.caption.bold()).foregroundColor(colors.inkSoft)
                    // 멀티라인은 TextEditor(웹 textarea 미러) — CreateGroupView/AccountSettingsView와 같은 타협
                    TextEditor(text: Binding(get: { uiState.description }, set: { viewModel.onAction(.setDescription($0)) }))
                        .frame(minHeight: 72)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(RoundedRectangle(cornerRadius: 10).stroke(colors.stoneBorder, lineWidth: 1))
                }
                // 대표 이미지 — 현재 값 미리보기+피커(계정 설정 아바타 패턴 재사용)
                HStack(spacing: 12) {
                    if let imageUrlString = uiState.image, let url = URL(string: imageUrlString) {
                        AsyncImage(url: url) { phase in
                            if case .success(let image) = phase {
                                image.resizable().scaledToFill()
                            }
                        }
                        .frame(width: 56, height: 56)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    outlinedButton(
                        uiState.isUploadingImage ? "업로드 중..." : "대표 이미지 변경",
                        color: colors.accent,
                        enabled: !uiState.isUploadingImage
                    ) { showImagePicker = true }
                }
                // 가입 방식 — 라운지는 숨김(웹 !group.isLounge 미러), 문구는 CreateGroupScreen과 동일
                if !uiState.isLounge {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("가입 방식").font(.caption.bold()).foregroundColor(colors.inkSoft)
                        joinTypeRow("자동 승인 — 바로 가입", isSelected: uiState.joinType == .autoApprove) {
                            viewModel.onAction(.setJoinType(.autoApprove))
                        }
                        joinTypeRow("승인제 — 신청 후 승인", isSelected: uiState.joinType == .approvalRequired) {
                            viewModel.onAction(.setJoinType(.approvalRequired))
                        }
                    }
                }
                if let saveError = uiState.saveError {
                    Text(saveError).font(.caption).foregroundColor(colors.rust)
                }
                SGPrimaryButton(
                    title: "저장",
                    enabled: !uiState.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !uiState.isUploadingImage,
                    isLoading: uiState.isSaving,
                    action: { viewModel.onAction(.save) }
                )
            }
            .padding(16)
        }
    }

    /// 웹 OutlinedButton 미러 — 테두리만 있는 보조 버튼(Compose OutlinedButton+shape.button 미러)
    private func outlinedButton(_ title: String, color: Color, enabled: Bool = true, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.bold())
                .foregroundColor(enabled ? color : colors.inkFaint)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .overlay(
                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                        .stroke(colors.stoneBorder, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    /// 가입 방식 라디오 행 — CreateGroupView.joinTypeRow와 동일 관용구(웹 라디오 미러)
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

    init(groupId: Int64, container: AppContainer, onSaved: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: GroupEditViewModel(
            groupId: groupId,
            getGroupUseCase: container.getGroupUseCase,
            updateGroupUseCase: container.updateGroupUseCase,
            uploadImageUseCase: container.uploadImageUseCase
        ))
        self.onSaved = onSaved
    }
}
