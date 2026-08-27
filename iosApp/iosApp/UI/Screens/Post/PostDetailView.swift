import Shared
import SwiftUI

/// 공개 프로필 시트에서 고른 후속 push 대상 — 시트가 완전히 닫힌 뒤(onDismiss) 실행해야 유실되지 않는다
private enum ProfileFollowUp {
    case chatRoom(ChatRoomRef)
    case accountSettings
}

/// 게시글 상세 — composeApp PostDetailScreen.kt와 1:1 미러.
/// 본문·이미지·좋아요·댓글(답글 포함). 삭제·차단 성공은 화면을 닫기만 하고,
/// 목록 정리는 피드 VM이 삭제·차단 알림을 받아 스냅샷에서 처리한다(전체 재조회를 피한다).
struct PostDetailView: View {
    private let groupId: Int64

    private let postId: Int64

    /// 작성자 프로필 시트의 후속 push(채팅방/계정 설정)에 필요 — 셸 소유 세션 VM pass-through
    private let chatViewModel: ChatViewModel

    private let profileViewModel: ProfileViewModel

    @Environment(\.sgColors) private var colors

    @Environment(\.dismiss) private var dismiss

    @StateObject private var postDetailViewModel: PostDetailViewModel

    @State private var commentText = ""

    /// 더보기 메뉴에서 고른 "수정" — 메뉴 안에서는 NavigationLink가 동작하지 않아 상태로 push한다
    @State private var showEdit = false

    /// 본문·댓글 작성자 탭 → 공개 프로필 시트(웹 작성자 메뉴의 "프로필 보기" 직행 미러)
    @State private var selectedAuthorId: Int64? = nil

    /// 프로필 시트에서 DM 성공 후속 push — MainShellView 채팅방 미러
    @State private var selectedChatRoom: ChatRoomRef? = nil

    /// 프로필 시트에서 본인 "프로필 수정" 후속 push — MainShellView 계정 설정 미러
    @State private var showAccountSettings = false

    /// 프로필 시트의 후속 이동(채팅방/계정 설정) — 시트 dismiss 완료 후 push한다
    @State private var profileFollowUp: ProfileFollowUp? = nil

    /// 되돌릴 수 없는 액션은 확인을 받는다(웹 confirm 미러)
    @State private var confirmAction: ConfirmAction?

    /// 지금 재생 중인 동영상 URL — 한 게시글에 동영상이 여럿이어도 재생기는 하나만 뜬다.
    /// 순수 뷰 상태라 UiState가 아니라 화면이 들고 있는다(Compose playingVideoUrl 미러)
    @State private var playingVideoUrl: String?

    /// 수정·후속 push — NavigationStack은 iOS 16+라 iOS 15는 숨김 NavigationLink 폴백(그룹 상세 미러).
    /// 작성자 프로필은 push가 아니라 시트 — 후속 이동(채팅방/계정 설정)은 시트가 완전히
    /// 닫힌 뒤(onDismiss)에 push해야 유실되지 않는다
    var body: some View {
        if #available(iOS 16.0, *) {
            core
                .navigationDestination(isPresented: $showEdit) { editDestination }
                .navigationDestination(isPresented: showChatRoom) { chatRoomDestination }
                .navigationDestination(isPresented: $showAccountSettings) { accountSettingsDestination }
                .sheet(isPresented: showAuthorProfile, onDismiss: runProfileFollowUp) { authorProfileDestination }
        } else {
            core
                .background(
                    NavigationLink(isActive: $showEdit) {
                        editDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .background(
                    NavigationLink(isActive: showChatRoom) {
                        chatRoomDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .background(
                    NavigationLink(isActive: $showAccountSettings) {
                        accountSettingsDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .sheet(isPresented: showAuthorProfile, onDismiss: runProfileFollowUp) { authorProfileDestination }
        }
    }

    private func isReportAction(_ action: ConfirmAction) -> Bool {
        switch action {
        case .reportPost, .reportComment: return true
        case .blockAuthor, .blockComment: return false
        }
    }

    private func confirmTitle(_ action: ConfirmAction) -> String {
        switch action {
        case .reportPost: return "게시글 신고"
        case .reportComment: return "사용자 신고"
        case .blockAuthor, .blockComment: return "사용자 차단"
        }
    }

    private func confirmMessage(_ action: ConfirmAction) -> String {
        // 차단 문구는 어디서 눌렀든 같다 — 차단은 사용자 단위라 글·댓글이 함께 숨겨진다
        func blockMessage(_ name: String) -> String {
            "\(name)님을 차단할까요?\n차단하면 이 사용자의 글·댓글이 내 화면에서 숨겨지고 DM이 막힙니다."
        }

        switch action {
        case .reportPost:
            return "이 게시글을 신고할까요?\n접수된 신고는 그룹 관리자가 확인합니다."
        // 댓글엔 신고 API가 없어 작성자를 신고한다 — 접수처도 운영자로 달라서 문구를 구분한다
        case .reportComment(_, let authorName):
            return "\(authorName)님을 신고할까요?\n접수된 신고는 운영자가 확인합니다."
        case .blockAuthor:
            // uiState는 core의 지역 상수라 여기선 VM에서 직접 읽는다
            return blockMessage(postDetailViewModel.uiState.post?.authorName ?? "")
        case .blockComment(_, let authorName):
            return blockMessage(authorName)
        }
    }

    private func confirmedAction(_ action: ConfirmAction) -> PostDetailViewModel.Action {
        switch action {
        case .reportPost: return .reportPost
        case .blockAuthor: return .blockAuthor
        case .reportComment(let userId, _): return .reportCommentAuthor(userId: userId)
        case .blockComment(let userId, _): return .blockCommentAuthor(userId: userId)
        }
    }

    private var editDestination: some View {
        CreatePostView(groupId: groupId, postId: postId) {
            // 수정하고 돌아오면 바뀐 본문을 보여줘야 한다.
            // 목록은 갱신 신호를 받지 않는다 — 수정 알림(ObservePostUpdatesUseCase)을 받은 목록 VM이
            // 자기 스냅샷에서 그 항목만 갈아끼운다(refresh를 태우면 첫 페이지부터 전체 재조회가 된다)
            postDetailViewModel.onAction(.reload)
        }
    }

    @ViewBuilder private var authorProfileDestination: some View {
        if let authorId = selectedAuthorId {
            UserProfileView(
                userId: authorId,
                onOpenChatRoom: { room in
                    profileFollowUp = .chatRoom(room)
                    selectedAuthorId = nil
                },
                onOpenAccountSettings: {
                    profileFollowUp = .accountSettings
                    selectedAuthorId = nil
                }
            )
        }
    }

    @ViewBuilder private var chatRoomDestination: some View {
        if let room = selectedChatRoom {
            ChatRoomView(
                chatRoomId: room.chatRoomId,
                groupId: room.groupId,
                title: room.title,
                chatViewModel: chatViewModel
            )
        }
    }

    /// 세션 ProfileViewModel을 넘겨 저장 성공 시 셸 헤더가 갱신되게 한다(MainShellView 선례)
    private var accountSettingsDestination: some View {
        AccountSettingsView(profileViewModel: profileViewModel)
    }

    /// 프로필 시트 dismiss 완료 후 후속 push 실행 — 드래그로 닫으면 followUp이 nil이라 아무 일 없다
    private func runProfileFollowUp() {
        switch profileFollowUp {
        case .chatRoom(let room): selectedChatRoom = room
        case .accountSettings: showAccountSettings = true
        case nil: break
        }
        profileFollowUp = nil
    }

    /// 시트를 닫으면(X·드래그) selectedAuthorId를 nil로 되돌리는 브리지
    private var showAuthorProfile: Binding<Bool> {
        Binding(
            get: { selectedAuthorId != nil },
            set: { if !$0 { selectedAuthorId = nil } }
        )
    }

    /// pop(백 버튼/스와이프) 시 selectedChatRoom을 nil로 되돌리는 브리지(MainShellView 선례)
    private var showChatRoom: Binding<Bool> {
        Binding(
            get: { selectedChatRoom != nil },
            set: { if !$0 { selectedChatRoom = nil } }
        )
    }

    @ViewBuilder private var core: some View {
        let uiState = postDetailViewModel.uiState

        VStack(spacing: 0) {
            if let message = uiState.notice {
                HStack {
                    Text(message).font(.caption).foregroundColor(colors.moss)
                    Spacer()
                    Button("닫기") { postDetailViewModel.onAction(.clearNotice) }
                        .font(.caption.bold())
                        .foregroundColor(colors.accent)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(colors.linen)
            }

            if let message = uiState.error {
                HStack {
                    Text(message).font(.caption).foregroundColor(colors.ink)
                    Spacer()
                    Button("닫기") { postDetailViewModel.onAction(.clearError) }
                        .font(.caption.bold())
                        .foregroundColor(colors.accent)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(colors.accentSoft)
            }

            if uiState.post == nil, uiState.isLoading {
                Spacer()
                ProgressView().tint(colors.accent)
                Spacer()
            } else if uiState.post == nil {
                Spacer()
                Button("다시 시도") { postDetailViewModel.onAction(.reload) }
                    .foregroundColor(colors.accent)
                Spacer()
            } else {
                ScrollView {
                    // 항목 간격을 작게 잡는다 — 본문 블록과 댓글 행이 각자 세로 패딩을 갖고 있어
                    // 여기서 12를 더 주면 좋아요 행과 구분선 사이가 두 배로 벌어진다(Compose 미러).
                    LazyVStack(alignment: .leading, spacing: 4) {
                        postBody(uiState)
                        Divider().background(colors.stoneBorder)
                        Text("댓글 \(uiState.comments.count)")
                            .font(.subheadline.bold())
                            .foregroundColor(colors.inkSoft)
                            .padding(.horizontal, 16)
                        ForEach(uiState.topLevelComments, id: \.id) { comment in
                            commentRow(comment, isMine: comment.userId == uiState.myUserId, canReply: true)
                            // 답글은 한 단계만 들여쓴다(서버가 답글의 답글을 허용하지 않는다)
                            ForEach(uiState.repliesOf(comment.id), id: \.id) { reply in
                                commentRow(reply, isMine: reply.userId == uiState.myUserId, canReply: false)
                                    .padding(.leading, 40)
                            }
                        }
                    }
                    .padding(.bottom, 16)
                }
            }

            composer(uiState)
        }
        .background(colors.paper)
        .overlay {
            if let action = confirmAction {
                ActionConfirmDialog(
                    title: confirmTitle(action),
                    message: confirmMessage(action),
                    confirmText: isReportAction(action) ? "신고" : "차단",
                    isLoading: isReportAction(action) ? uiState.isReporting : uiState.isBlocking,
                    onDismiss: { confirmAction = nil },
                    onConfirm: {
                        confirmAction = nil
                        postDetailViewModel.onAction(confirmedAction(action))
                    }
                )
            }
        }
        .navigationTitle("게시글")
        .navigationBarTitleDisplayMode(.inline)
        // 호출 화면이 투명 바(커버 펼침) 상태로 push해도 이 화면은 기본 내비바 — 복귀 시엔 호출 화면이 재적용
        .navigationBarScrim(visible: true)
        // ⚠️조건 분기는 ToolbarItem "안"에 둔다 — ToolbarContentBuilder의 buildIf는 iOS 16+라
        // .toolbar { if ... } 는 배포 타깃 15.0에서 컴파일되지 않는다(GroupDetailView와 같은 형태)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                // 더보기는 항상 노출하고 메뉴 내용만 갈린다 — 내 글이면 수정·삭제,
                // 남의 글이면 신고·차단(Compose DropdownMenu와 미러). 권한은 서버가 판정한다.
                // 파괴적 항목은 destructive 역할로 빨갛게 — Compose는 같은 자리를 sg.rust로 칠한다
                Menu {
                    if uiState.isMyPost {
                        Button("수정") { showEdit = true }
                        Button(role: .destructive) {
                            postDetailViewModel.onAction(.deletePost)
                        } label: {
                            Text("삭제")
                        }
                    } else {
                        Button(role: .destructive) {
                            confirmAction = .reportPost
                        } label: {
                            Text("신고하기")
                        }
                        Button(role: .destructive) {
                            confirmAction = .blockAuthor
                        } label: {
                            Text("차단하기")
                        }
                        // 글이 아직 안 실렸으면 작성자를 모르므로 차단할 수 없다
                        .disabled(uiState.post == nil)
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .disabled(uiState.isDeletingPost || uiState.isBlocking)
            }
        }
        .onReceive(postDetailViewModel.event) { event in
            switch event {
            // 삭제·차단 모두 화면만 닫는다 — 목록에서 그 글을 걷어내는 일은 피드 VM이
            // 삭제·차단 알림을 받아 스냅샷에서 처리한다(전체 재조회를 피한다)
            case .postDeleted:
                dismiss()
            case .authorBlocked:
                dismiss()
            // 등록에 성공했을 때만 입력창을 비운다 — 실패하면 쓴 글이 남아 재시도할 수 있다
            case .commentCreated:
                commentText = ""
            }
        }
    }

    @ViewBuilder
    private func postBody(_ uiState: PostDetailViewModel.UiState) -> some View {
        if let post = uiState.post {
            VStack(alignment: .leading, spacing: 12) {
                // 작성자 영역만 탭 타깃(본문·첨부 제외) — 본인 글이면 본인 프로필(프로필 수정)로 간다
                HStack(spacing: 0) {
                    Button(action: { selectedAuthorId = post.userId }) {
                        HStack(spacing: 10) {
                            SGAvatar(name: post.authorName, imageUrl: post.authorProfileImg)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(post.authorName).font(.subheadline.bold()).foregroundColor(colors.ink)
                                Text(TimeFormats.relative(post.createdAt)).font(.caption).foregroundColor(colors.inkFaint)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    Spacer()
                }
                if !post.text.isEmpty {
                    Text(post.text).font(.body).foregroundColor(colors.ink)
                }
                ForEach(post.imageUrls, id: \.self) { url in
                    AsyncImage(url: URL(string: url)) { image in
                        image.resizable().scaledToFit()
                    } placeholder: {
                        Color.clear
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                // 동영상은 이미지 다음에 온다(웹 상세 페이지와 같은 순서)
                ForEach(post.videoUrls, id: \.self) { url in
                    SGVideoAttachment(
                        urlString: url,
                        isPlaying: url == playingVideoUrl,
                        onPlayRequest: { playingVideoUrl = url }
                    )
                }
                HStack(spacing: 16) {
                    Button {
                        postDetailViewModel.onAction(.toggleLike)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: uiState.isLiked ? "heart.fill" : "heart")
                                .foregroundColor(uiState.isLiked ? colors.accent : colors.inkFaint)
                            Text("좋아요 \(uiState.likeCount)").font(.caption).foregroundColor(colors.inkSoft)
                        }
                    }
                    .disabled(uiState.isTogglingLike)
                    Text("댓글 \(uiState.comments.count)").font(.caption).foregroundColor(colors.inkSoft)
                    Spacer()
                }
            }
            // 아래쪽만 좁게 — Compose는 IconButton의 48dp 터치 영역이 여백을 대신하지만
            // SwiftUI Button은 내용 크기 그대로라 최소한의 숨 쉴 틈만 남긴다.
            .padding([.top, .leading, .trailing], 16)
            .padding(.bottom, 8)
        }
    }

    private func commentRow(_ comment: Comment, isMine: Bool, canReply: Bool) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Button(action: { selectedAuthorId = comment.userId }) {
                SGAvatar(name: comment.authorName, size: 28, imageUrl: comment.authorProfileImg)
            }
            .buttonStyle(.plain)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Button(action: { selectedAuthorId = comment.userId }) {
                        Text(comment.authorName).font(.caption.bold()).foregroundColor(colors.ink)
                    }
                    .buttonStyle(.plain)
                    Text(TimeFormats.relative(comment.createdAt)).font(.caption2).foregroundColor(colors.inkFaint)
                }
                Text(comment.text).font(.subheadline).foregroundColor(colors.ink)
                if canReply {
                    Button("답글") { postDetailViewModel.onAction(.setReplyTo(comment: comment)) }
                        .font(.caption2)
                        .foregroundColor(colors.inkFaint)
                }
            }
            Spacer(minLength: 0)
            // 게시글 상단바와 같은 규칙 — 더보기는 항상 노출하고 내 댓글이면 삭제, 남의 댓글이면 신고·차단
            Menu {
                if isMine {
                    Button(role: .destructive) {
                        postDetailViewModel.onAction(.deleteComment(commentId: comment.id))
                    } label: {
                        Text("삭제")
                    }
                } else {
                    Button(role: .destructive) {
                        confirmAction = .reportComment(userId: comment.userId, authorName: comment.authorName)
                    } label: {
                        Text("신고하기")
                    }
                    Button(role: .destructive) {
                        confirmAction = .blockComment(userId: comment.userId, authorName: comment.authorName)
                    } label: {
                        Text("차단하기")
                    }
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.caption)
                    .foregroundColor(colors.inkFaint)
                    // 아이콘만으론 터치 영역이 좁다 — 행 높이를 키우지 않는 선에서 넓힌다
                    .frame(width: 28, height: 28)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    @ViewBuilder
    private func composer(_ uiState: PostDetailViewModel.UiState) -> some View {
        VStack(spacing: 0) {
            Divider().background(colors.stoneBorder)
            // 답글 대상이 정해지면 누구에게 다는지 보여주고, 그 자리에서 취소할 수 있게 한다
            if let target = uiState.replyTo {
                HStack {
                    Text("\(target.authorName)님에게 답글").font(.caption).foregroundColor(colors.ink)
                    Spacer()
                    Button {
                        postDetailViewModel.onAction(.setReplyTo(comment: nil))
                    } label: {
                        Image(systemName: "xmark").foregroundColor(colors.inkFaint)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
                .background(colors.accentSoft)
            }
            // 레거시 fragment_post_detail.xml 미러 — 헤어라인 아래 여백 얇은 한 줄:
            // 테두리 없는 입력란 + 작은 전송 버튼. 채팅방 inputBar와 같은 치수다.
            HStack(spacing: 8) {
                SGComposerField(placeholder: "댓글을 입력하세요.", text: $commentText)
                Button {
                    postDetailViewModel.onAction(.submitComment(text: commentText))
                } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundColor(
                            uiState.isSubmittingComment || commentText.isEmpty ? colors.inkFaint : colors.accent
                        )
                }
                .disabled(uiState.isSubmittingComment || commentText.isEmpty)
            }
            // 좌측 10pt + 필드 자체 6pt = 댓글 목록과 같은 16pt 글자 시작선
            .padding(.leading, 10)
            .padding(.trailing, 8)
            .padding(.vertical, 6)
        }
        .background(colors.paper)
    }
    
    init(groupId: Int64, postId: Int64, chatViewModel: ChatViewModel, profileViewModel: ProfileViewModel) {
        self.groupId = groupId
        self.postId = postId
        self.chatViewModel = chatViewModel
        self.profileViewModel = profileViewModel
        _postDetailViewModel = StateObject(wrappedValue: PostDetailViewModel(groupId: groupId, postId: postId))
    }
}

/// 더보기 메뉴의 되돌릴 수 없는 액션 — 확인 다이얼로그를 한 번 거친다(Compose ConfirmAction 미러)
private enum ConfirmAction {
    case reportPost
    case blockAuthor
    /// 댓글 신고 API는 없어 작성자를 신고한다 — 문구에 쓰려고 이름을 함께 싣는다
    case reportComment(userId: Int64, authorName: String)
    case blockComment(userId: Int64, authorName: String)
}

/// 신고·차단 확인 다이얼로그 — Compose ActionConfirmDialog 미러(GroupDetailView의 DmConfirmDialog와
/// 같은 반투명 배경+중앙 카드). 실패 메시지는 다이얼로그가 아니라 화면 상단 에러 배너에 뜬다.
private struct ActionConfirmDialog: View {
    let title: String

    let message: String

    let confirmText: String

    let isLoading: Bool

    let onDismiss: () -> Void

    let onConfirm: () -> Void

    @Environment(\.sgColors) private var colors

    var body: some View {
        ZStack {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)
            SGCard {
                VStack(alignment: .leading, spacing: 12) {
                    Text(title)
                        .font(.headline)
                        .foregroundColor(colors.ink)
                    Text(message)
                        .font(.subheadline)
                        .foregroundColor(colors.ink)
                    HStack(spacing: 8) {
                        Button(action: onDismiss) {
                            Text("취소")
                                .font(.subheadline)
                                .frame(maxWidth: .infinity)
                                // SGPrimaryButton과 같은 높이로 나란히 맞춘다
                                .frame(height: 48)
                                .overlay(
                                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                                        .stroke(colors.stoneBorder, lineWidth: 1)
                                )
                                .foregroundColor(colors.ink)
                        }
                        .buttonStyle(.plain)
                        SGPrimaryButton(title: confirmText, isLoading: isLoading, action: onConfirm)
                    }
                }
                .padding(16)
            }
            .padding(24)
        }
    }
}
