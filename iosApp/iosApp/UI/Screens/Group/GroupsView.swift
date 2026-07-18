import SwiftUI

/// 그룹 API 연동 전 표시용 모델 — Compose GroupUiModel 미러
struct GroupUiModel: Identifiable {
    let id: Int
    let name: String
    let memberCount: Int
    let recentActivity: String
}

private let sampleGroups = [
    GroupUiModel(id: 1, name: "등산 모임", memberCount: 12, recentActivity: "새 글 2 · 오늘"),
    GroupUiModel(id: 2, name: "스터디 그룹", memberCount: 5, recentActivity: "새 일정 1 · 어제"),
    GroupUiModel(id: 3, name: "맛집 탐방", memberCount: 8, recentActivity: "새 사진 4 · 3일 전")
]

/// 가입중인 그룹 목록 + 만들기/찾기 진입 — 웹 /groups·Compose GroupsScreen 미러
struct GroupsView: View {
    let colors: SGColors

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                HStack(spacing: 8) {
                    groupActionButton("그룹 만들기") { /* TODO: 그룹 만들기 */ }
                    groupActionButton("그룹 찾기") { /* TODO: 그룹 찾기 */ }
                }
                ForEach(sampleGroups) { group in
                    GroupCard(group: group, colors: colors)
                }
            }
            .padding(16)
        }
        .background(colors.paper)
    }

    private func groupActionButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.bold())
                .foregroundColor(colors.accent)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(
                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 22, style: .continuous)
                        .stroke(colors.stoneBorder, lineWidth: 1)
                )
        }
    }
}

private struct GroupCard: View {
    let group: GroupUiModel

    let colors: SGColors

    var body: some View {
        SGCard(colors: colors) {
            HStack(spacing: 12) {
                SGAvatar(name: group.name, colors: colors, size: 48, background: colors.accent2Soft, foreground: colors.accent2)
                VStack(alignment: .leading, spacing: 2) {
                    Text(group.name).font(.headline).foregroundColor(colors.ink)
                    Text("멤버 \(group.memberCount)명").font(.caption).foregroundColor(colors.inkSoft)
                    Text(group.recentActivity).font(.caption).foregroundColor(colors.inkFaint)
                }
                Spacer()
            }
            .padding(16)
        }
    }
}
