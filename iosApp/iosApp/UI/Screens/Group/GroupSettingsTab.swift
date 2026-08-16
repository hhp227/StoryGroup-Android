import SwiftUI
import UIKit
import Shared

/// 설정 탭 — composeApp GroupSettingsTab.kt와 1:1 미러: 레거시 SettingsFragment(item_settings.xml)
/// 미러의 섹션별 메뉴 리스트(유저 설정/그룹 설정/어플리케이션 정보). 수정 폼은 GroupEditView로 분리.
/// 라운지: OWNER=신고함+정보 수정, 비OWNER=그룹 설정 섹션 숨김(웹 미러 — 나가기도 서버가 거부).
struct GroupSettingsTab: View {
    @ObservedObject var viewModel: GroupSettingsViewModel

    /// 유저 설정 행 — 셸 세션 ProfileViewModel의 프로필(프로필 탭/드로어 헤더와 동일 원천)
    let profile: Profile?

    let onOpenGroupEdit: () -> Void

    let onOpenAccountSettings: () -> Void

    let onOpenAppSettings: () -> Void

    /// 신고함 push(모더레이터) — 웹 커버 "신고함" 버튼의 KMP 대응(GroupDetailView로 위임)
    let onOpenGroupReports: () -> Void

    /// 공유하기 — 부모의 ShareItem 시트를 연다(피드 카드 공유와 같은 경로)
    let onShareApp: () -> Void

    @Environment(\.sgColors) private var colors

    /// 확인 다이얼로그 표시 여부 — 레거시 AlertDialog 미러, 화면 로컬 상태
    @State private var confirmingDelete = false

    @State private var confirmingLeave = false

    /// 레거시 privacy_policy 미러 — 웹 개인정보처리방침(웹·API 같은 서비스)
    private static let privacyPolicyUrl = URL(string: "\(StoryGroupApi.shared.DEFAULT_BASE_URL)/privacy")!

    /// 약관 — 웹 /terms(설정 허브 "약관 및 정책" 미러)
    private static let termsUrl = URL(string: "\(StoryGroupApi.shared.DEFAULT_BASE_URL)/terms")!

    var body: some View {
        content
            .frame(maxWidth: .infinity)
            // 레거시 AlertDialog 미러 — iOS는 .alert(확인 즉시 닫힘), 실패 문구는 아래 별도 알럿
            .alert("그룹 삭제", isPresented: $confirmingDelete) {
                Button("삭제", role: .destructive) { viewModel.onAction(.delete) }
                Button("취소", role: .cancel) {}
            } message: {
                Text("정말 삭제할까요? 게시글, 채팅, 파일이 모두 사라집니다.")
            }
            .alert("그룹 나가기", isPresented: $confirmingLeave) {
                Button("나가기", role: .destructive) { viewModel.onAction(.leave) }
                Button("취소", role: .cancel) {}
            } message: {
                Text("정말 나갈까요? 나가면 이 그룹의 게시글·채팅에 더는 참여할 수 없습니다.")
            }
            // 삭제/나가기 실패 — 서버 문구 그대로(likeError 알럿 관용구)
            .alert("처리 실패", isPresented: Binding(
                get: { viewModel.uiState.closeError != nil },
                set: { if !$0 { viewModel.onAction(.dismissCloseError) } }
            )) {
                Button("확인", role: .cancel) {}
            } message: {
                Text(viewModel.uiState.closeError ?? "")
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
            VStack(alignment: .leading, spacing: 8) {
                // 유저 설정 — 레거시 user_settings 섹션(프로필 행 → 계정 설정)
                SGSectionTitle(text: "유저 설정")
                SGCard {
                    profileRow
                }
                Spacer().frame(height: 8)
                // 그룹 설정 — 라운지 비OWNER는 항목이 없어 섹션째 숨긴다
                if uiState.isOwner || !uiState.isLounge {
                    SGSectionTitle(text: "그룹 설정")
                    SGCard {
                        VStack(spacing: 0) {
                            // 신고함 — 웹 커버 "신고함" 버튼의 KMP 대응(모더레이터 기능은 탭 메뉴에 모은다)
                            if uiState.canModerate {
                                menuRow("신고함", showChevron: true, action: onOpenGroupReports)
                            }
                            if uiState.isOwner {
                                // OWNER는 항상 canModerate — 위에 신고함 행이 있어 구분선이 필요하다
                                divider
                                menuRow("그룹 정보 수정", showChevron: true, action: onOpenGroupEdit)
                            }
                            // 라운지는 삭제·나가기 불가(웹 미러)
                            if !uiState.isLounge {
                                if uiState.isOwner {
                                    divider
                                    menuRow("그룹 삭제", tint: colors.rust) { confirmingDelete = true }
                                } else {
                                    if uiState.canModerate {
                                        divider
                                    }
                                    // 비OWNER — 그룹 나가기(레거시 설정 탭 ll_withdrawal 미러, POST /leave 소비)
                                    menuRow("그룹 나가기", tint: colors.rust) { confirmingLeave = true }
                                }
                            }
                        }
                    }
                    Spacer().frame(height: 8)
                }
                // 어플리케이션 정보 — 레거시 application_info 섹션(KMP에 대응 화면이 있는 항목만)
                SGSectionTitle(text: "어플리케이션 정보")
                SGCard {
                    VStack(spacing: 0) {
                        menuRow("앱 설정", showChevron: true, action: onOpenAppSettings)
                        divider
                        menuRow("공유하기", action: onShareApp)
                        divider
                        // 약관·정책 — 웹 /terms·/privacy를 외부 브라우저로 연다(설정 허브 "약관 및 정책" 미러)
                        menuRow("이용약관") { UIApplication.shared.open(Self.termsUrl) }
                        divider
                        menuRow("개인정보처리방침") { UIApplication.shared.open(Self.privacyPolicyUrl) }
                    }
                }
            }
            .padding(16)
        }
    }

    /// 내 프로필 행 — 아바타+이름+이메일, 탭하면 계정 설정(레거시 ll_profile 미러)
    private var profileRow: some View {
        Button(action: onOpenAccountSettings) {
            HStack(spacing: 12) {
                SGAvatar(name: profile?.name ?? "?", size: 44, imageUrl: profile?.profileImg)
                VStack(alignment: .leading, spacing: 2) {
                    Text(profile?.name ?? "불러오는 중...")
                        .font(.subheadline.bold())
                        .foregroundColor(colors.ink)
                    Text(profile?.email ?? "")
                        .font(.caption)
                        .foregroundColor(colors.inkSoft)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundColor(colors.inkFaint)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .buttonStyle(.plain)
    }

    /// 메뉴 행 — 레거시 item_settings 50dp 행 미러(Compose SettingsMenuRow 미러)
    private func menuRow(
        _ label: String,
        tint: Color? = nil,
        showChevron: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack {
                Text(label)
                    .font(.subheadline)
                    .foregroundColor(tint ?? colors.ink)
                Spacer()
                if showChevron {
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundColor(colors.inkFaint)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
        .buttonStyle(.plain)
    }

    private var divider: some View {
        Rectangle()
            .fill(colors.stoneBorder)
            .frame(height: 1)
            .padding(.horizontal, 16)
    }
}
