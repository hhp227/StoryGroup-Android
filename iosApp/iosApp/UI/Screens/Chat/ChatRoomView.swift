import SwiftUI
import UniformTypeIdentifiers
import Shared

/// 채팅방 — 웹 MessageThread·Compose ChatRoomScreen 미러(말풍선 정렬·작성자 변경 시에만
/// 아바타/이름·첨부 렌더링, 웹처럼 시각 표기는 없음). 목록은 플립 ScrollView(세로 뒤집기+행
/// 되뒤집기)로 그리되 메시지가 적으면 Compose처럼 상단부터 채우고, 새 메시지가 오면 맨 아래로
/// 따라간다. 화면 위 근처에 닿으면 이전 페이지를 자동 로드한다(무한 스크롤 — 웹엔 없는 앱 확장).
struct ChatRoomView: View {
    @StateObject private var viewModel: ChatRoomViewModel

    /// 허브(셸 소유 세션 VM) 진입/이탈 신호용 — Compose ChatRoomScreen의 sessionChatViewModel 미러
    private let chatViewModel: ChatViewModel

    private let chatRoomId: Int64

    /// 상단바 통화 버튼 아이콘 분기용 — DM(nil)=전화, 그룹 방=화상회의(Compose 미러)
    private let groupId: Int64?

    /// 통화 화면(CallView) push의 VM 생성에 쓰인다
    private let container: AppContainer

    let title: String

    /// 통화 화면 push — Compose CallRoute(ring=true) 미러(발신=입장+벨울림)
    @State private var showCall = false

    @State private var input = ""

    @State private var showImagePicker = false

    @State private var showFilePicker = false

    /// + 버튼 첨부 패널(카톡 미러) — 열 때 키보드를 내리고 그 자리에 나타난다
    @State private var showAttachments = false

    @Environment(\.sgColors) private var colors

    /// 통화 화면 push — NavigationStack은 iOS 16+라 iOS 15는 숨김 NavigationLink 폴백(그룹 상세 미러)
    var body: some View {
        if #available(iOS 16.0, *) {
            core.navigationDestination(isPresented: $showCall) { callDestination }
        } else {
            core.background(
                NavigationLink(isActive: $showCall) {
                    callDestination
                } label: {
                    EmptyView()
                }
                .hidden()
            )
        }
    }

    private var callDestination: some View {
        CallView(chatRoomId: chatRoomId, title: title, ring: true, container: container)
    }

    @ViewBuilder private var core: some View {
        let uiState = viewModel.uiState
        // "읽음 N" 파생용 — 타인의 읽음 위치만 남긴다(내 위치는 세지 않는다, 웹 미러)
        let otherReadPositions = uiState.readPositions
            .filter { $0.key != uiState.myUserId }
            .map(\.value)

        VStack(spacing: 0) {
            // 통화 진행 중 라이브 바(웹 라이브 카드·카톡 진행 중 배너·Compose 미러) — 참가는
            // 통화 버튼과 같은 경로다(진행 중 통화 합류는 서버가 다시 울리지 않는다)
            if !uiState.callRoster.isEmpty {
                HStack(spacing: 8) {
                    Image(systemName: "video.fill")
                        .font(.system(size: 14))
                        .foregroundColor(colors.accent)
                    Text("\(uiState.callRoster.map(\.userName).joined(separator: ", "))님이 통화 중이에요")
                        .font(.caption)
                        .foregroundColor(colors.ink)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Button("참가") { showCall = true }
                        .font(.caption.bold())
                        .foregroundColor(colors.accent)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(colors.accentSoft)
            }
            ScrollViewReader { proxy in
                Group {
                    if uiState.messages.isEmpty && uiState.isLoading {
                        ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if uiState.messages.isEmpty, let error = uiState.error {
                        VStack(spacing: 8) {
                            Text(error).font(.subheadline).foregroundColor(colors.rust)
                            Button("다시 시도") { viewModel.onAction(.refresh) }
                                .foregroundColor(colors.accent)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if uiState.messages.isEmpty {
                        SGEmptyState(
                            title: "메시지가 없습니다",
                            subtitle: "첫 메시지를 보내보세요.",
                            systemImage: "bubble.left"
                        )
                    } else {
                        // 플립 리스트 — ScrollView를 세로로 뒤집고 행을 되뒤집으면 레이아웃 원점(0)이
                        // 화면 맨 아래가 된다: 진입 즉시 최신부터 보이고, 이전 페이지 append는
                        // 레이아웃 아래(화면 위)로만 자라 스크롤 보정 없이 위치가 그대로 유지된다
                        // (Compose 키 앵커와 동급). VM 최신순 목록을 그대로 쓴다(플립이 곧 reverse —
                        // 웹 reverse 미러). 인디케이터는 플립 탓에 거꾸로 움직여 숨긴다
                        GeometryReader { geo in
                            ScrollView(showsIndicators: false) {
                                LazyVStack(spacing: 4) {
                                    let messages = uiState.messages

                                    ForEach(Array(messages.enumerated()), id: \.element.id) { index, message in
                                        let isMine = message.userId == uiState.myUserId

                                        MessageRow(
                                            message: message,
                                            isMine: isMine,
                                            // 최신순 목록이라 시간상 직전 메시지는 다음 인덱스(Compose 미러)
                                            showAuthor: index == messages.count - 1
                                                || messages[index + 1].userId != message.userId,
                                            // "읽음 N" = 내 메시지에 대해, 위치가 그 메시지 이상인 타인 수(웹 미러)
                                            readCount: isMine
                                                ? otherReadPositions.filter { $0 >= message.id }.count
                                                : 0
                                        )
                                        .scaleEffect(x: 1, y: -1)
                                        .id(message.id)
                                        // 화면 위(가장 오래된 쪽) 근처 행이 나타나면 이전 페이지 자동 로드 —
                                        // 과호출은 VM이 거른다. 초기 렌더는 레이아웃 원점(최신)부터 채워져
                                        // 오래된 행이 미리 만들어지지 않으므로 진입 즉시 오발화도 없다
                                        .onAppear {
                                            if index >= messages.count - 3 {
                                                viewModel.onAction(.loadOlder)
                                            }
                                        }
                                    }
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                // 플립의 기본은 하단 정렬 — 메시지가 뷰포트보다 적으면 콘텐츠를
                                // 레이아웃 아래(화면 위)로 밀어 Compose처럼 상단부터 채운다
                                .frame(minHeight: geo.size.height, alignment: .bottom)
                            }
                            .scaleEffect(x: 1, y: -1)
                        }
                        // 이전 페이지 로딩 표시 — 플립 밖 오버레이라 똑바로 그려지고 목록도 밀지 않는다
                        .overlay(alignment: .top) {
                            if uiState.isLoadingOlder {
                                ProgressView().padding(.top, 8)
                            }
                        }
                        // 새 메시지 도착/전송 시 맨 아래로 따라간다(웹 auto-scroll 미러).
                        // 플립에선 레이아웃 top = 화면 맨 아래라 anchor도 .top
                        .onChange(of: uiState.messages.first?.id) { latest in
                            if let latest {
                                withAnimation { proxy.scrollTo(latest, anchor: .top) }
                            }
                        }
                        // 키보드가 올라와 리스트가 줄어들 때 최신 메시지가 가려지지 않게 따라간다.
                        // willShow 즉시는 SwiftUI 키보드 인셋 반영 전이라 스크롤이 짧게 끝난다 —
                        // 한 런루프 미뤄 인셋 커밋 후, 키보드와 같은 시간으로 애니메이션하면
                        // 리스트가 키보드와 동시에 밀려 올라간다. didShow는 빗나갔을 때의 보정용
                        .onReceive(
                            NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)
                        ) { notification in
                            let duration = notification
                                .userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double

                            DispatchQueue.main.async {
                                scrollToLatest(proxy, duration: duration ?? 0.25)
                            }
                        }
                        .onReceive(
                            NotificationCenter.default.publisher(for: UIResponder.keyboardDidShowNotification)
                        ) { _ in
                            scrollToLatest(proxy)
                        }
                    }
                }
            }
            if let actionError = uiState.actionError {
                Text(actionError)
                    .font(.caption)
                    .foregroundColor(colors.rust)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 4)
            }
            // 입력 중 표시 — 입력바 바로 위의 얇은 띠(웹 미러)
            if !uiState.typists.isEmpty {
                Text("\(uiState.typists.values.sorted().joined(separator: ", "))님이 입력 중...")
                    .font(.caption)
                    .foregroundColor(colors.inkSoft)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 4)
            }
            if let pending = uiState.pendingAttachment {
                pendingAttachmentChip(pending)
            }
            inputBar(isSending: uiState.isSending, hasPendingAttachment: uiState.pendingAttachment != nil)
            if showAttachments {
                attachmentPanel
            }
        }
        .background(colors.paper.ignoresSafeArea())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        // 통화 발신 — 채팅방 세션에 통화가 붙는다(페이스톡 미러, Compose ChatRoomScreen과 동일).
        // DM=상대 벨울림(웹 D6), 그룹 방=방 멤버 전원 벨울림 팬아웃(진행 중 통화 합류면 서버가 다시 울리지 않는다)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { showCall = true }) {
                    Image(systemName: groupId == nil ? "phone.fill" : "video.fill")
                }
            }
        }
        // 허브에 진입/이탈을 알린다 — 이 방의 미읽음 뱃지를 0으로 만들고 실시간 증가에서 제외
        .onAppear { chatViewModel.onAction(.roomOpened(chatRoomId: chatRoomId)) }
        .onDisappear { chatViewModel.onAction(.roomClosed(chatRoomId: chatRoomId)) }
        .onReceive(viewModel.event) { event in
            switch event {
            case .sent: input = ""
            }
        }
        // 빈 입력은 타이핑 신호를 내지 않는다(웹 미러)
        .onChange(of: input) { newValue in
            if !newValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                viewModel.onAction(.typing)
            }
        }
        // 입력창 포커스로 키보드가 다시 올라오면 첨부 패널은 닫는다(카톡 미러)
        .onReceive(
            NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)
        ) { _ in
            showAttachments = false
        }
        .sheet(isPresented: $showImagePicker) {
            ImagePicker { data, fileName, contentType in
                viewModel.onAction(.attach(data: data, fileName: fileName, contentType: contentType))
            }
        }
        // 일반 파일은 시스템 문서 피커 — 권한·Info.plist 키 불필요(사용자 선택 파일 읽기)
        .fileImporter(isPresented: $showFilePicker, allowedContentTypes: [.item]) { result in
            guard case .success(let url) = result else { return }
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            guard let data = try? Data(contentsOf: url) else { return }
            let contentType = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType
                ?? "application/octet-stream"

            viewModel.onAction(.attach(data: data, fileName: url.lastPathComponent, contentType: contentType))
        }
    }

    private func scrollToLatest(_ proxy: ScrollViewProxy, duration: Double? = nil) {
        if let latest = viewModel.uiState.messages.first?.id {
            withAnimation(duration.map { Animation.easeOut(duration: $0) } ?? .default) {
                // 플립에선 레이아웃 top = 화면 맨 아래
                proxy.scrollTo(latest, anchor: .top)
            }
        }
    }

    /// 전송 대기 첨부 칩(웹 pending chip 미러) — 취소하면 업로드 없이 그냥 버려진다
    private func pendingAttachmentChip(_ pending: ChatRoomViewModel.PendingAttachment) -> some View {
        HStack(spacing: 6) {
            Image(systemName: pending.isImage ? "photo" : "doc")
                .font(.system(size: 14))
                .foregroundColor(colors.inkSoft)
            Text("\(pending.fileName) (\(formatFileSize(pending.data.count)))")
                .font(.caption)
                .foregroundColor(colors.ink)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: { viewModel.onAction(.clearAttachment) }) {
                Image(systemName: "xmark")
                    .font(.system(size: 12))
                    .foregroundColor(colors.inkSoft)
            }
        }
        .padding(.leading, 16)
        .padding(.trailing, 8)
        .padding(.top, 6)
        .background(colors.linen)
    }

    private func inputBar(isSending: Bool, hasPendingAttachment: Bool) -> some View {
        // 첨부가 있으면 본문 없이도 전송 가능(웹 미러)
        let canSend = (!input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || hasPendingAttachment)
            && !isSending

        return HStack(spacing: 8) {
            // 첨부 진입점은 +로 모은다(카톡 미러) — 패널이 열려 있으면 닫기(×)로 바뀐다
            Button(action: toggleAttachments) {
                Image(systemName: showAttachments ? "xmark" : "plus")
                    .font(.system(size: 20))
                    .foregroundColor(colors.inkSoft)
            }
            .disabled(isSending)
            SGTextField(label: hasPendingAttachment ? "메시지 (선택)" : "메시지 입력", text: $input)
            Button(action: { viewModel.onAction(.send(text: input)) }) {
                if isSending {
                    ProgressView()
                } else {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.system(size: 28))
                        .foregroundColor(canSend ? colors.accent : colors.inkFaint)
                }
            }
            .disabled(!canSend)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(colors.linen)
    }

    /// + 토글 — 열 때는 키보드를 내리고 그 자리에 패널을 띄운다(카톡 미러, Compose 1:1)
    private func toggleAttachments() {
        if showAttachments {
            showAttachments = false
        } else {
            UIApplication.shared.sendAction(
                #selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil
            )
            showAttachments = true
        }
    }

    /// + 버튼으로 여는 첨부 패널(카톡 미러, Compose AttachmentPanel 1:1) — 사진/파일/페이스톡을 고른다
    private var attachmentPanel: some View {
        HStack {
            Spacer()
            attachmentPanelItem(systemImage: "photo", label: "사진") {
                showAttachments = false
                showImagePicker = true
            }
            Spacer()
            attachmentPanelItem(systemImage: "paperclip", label: "파일") {
                showAttachments = false
                showFilePicker = true
            }
            Spacer()
            // 통화 발신과 같은 경로(페이스톡 미러) — 아이콘은 상단바 통화 버튼과 동일 분기
            attachmentPanelItem(systemImage: groupId == nil ? "phone.fill" : "video.fill", label: "페이스톡") {
                showAttachments = false
                showCall = true
            }
            Spacer()
        }
        .padding(.vertical, 24)
        .background(colors.linen)
    }

    private func attachmentPanelItem(
        systemImage: String,
        label: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            VStack(spacing: 6) {
                ZStack {
                    Circle().fill(colors.paper).frame(width: 56, height: 56)
                    Image(systemName: systemImage)
                        .font(.system(size: 22))
                        .foregroundColor(colors.accent)
                }
                Text(label)
                    .font(.caption2)
                    .foregroundColor(colors.inkSoft)
            }
        }
    }

    /// 웹 formatFileSize 미러 — 1KB 미만 B, 1MB 미만 반올림 KB, 이상은 소수 1자리 MB
    private func formatFileSize(_ size: Int) -> String {
        if size < 1024 { return "\(size)B" }
        if size < 1024 * 1024 { return "\(Int((Double(size) / 1024).rounded()))KB" }
        return "\((Double(size) / (1024 * 1024) * 10).rounded() / 10)MB"
    }

    init(chatRoomId: Int64, groupId: Int64?, title: String, container: AppContainer, chatViewModel: ChatViewModel) {
        _viewModel = StateObject(wrappedValue: ChatRoomViewModel(
            groupId: groupId,
            chatRoomId: chatRoomId,
            getChatMessagesUseCase: container.getChatMessagesUseCase,
            sendChatMessageUseCase: container.sendChatMessageUseCase,
            markChatMessagesReadUseCase: container.markChatMessagesReadUseCase,
            uploadChatFileUseCase: container.uploadChatFileUseCase,
            sendChatTypingUseCase: container.sendChatTypingUseCase,
            getChatReadPositionsUseCase: container.getChatReadPositionsUseCase,
            getCallRosterUseCase: container.getCallRosterUseCase,
            observeChatRoomEventsUseCase: container.observeChatRoomEventsUseCase,
            getCurrentUserIdUseCase: container.getCurrentUserIdUseCase
        ))
        self.chatViewModel = chatViewModel
        self.chatRoomId = chatRoomId
        self.groupId = groupId
        self.container = container
        self.title = title
    }
}

/// 웹 MessageBubble 미러 — 내 메시지는 우측 accent, 타인은 좌측 linen+작성자 변경 시 아바타/이름
private struct MessageRow: View {
    let message: ChatMessage

    let isMine: Bool

    let showAuthor: Bool

    let readCount: Int

    @Environment(\.sgColors) private var colors

    var body: some View {
        // 아바타는 상단 정렬 — Compose Row 기본값(Top) 미러
        HStack(alignment: .top, spacing: 8) {
            if isMine {
                Spacer(minLength: 48)
            } else {
                if showAuthor {
                    SGAvatar(name: message.authorName, size: 32, imageUrl: message.authorProfileImg)
                } else {
                    // 같은 작성자 연속 메시지는 아바타 없이 자리만 맞춘다(웹 spacer 미러)
                    Spacer().frame(width: 32)
                }
            }
            // "읽음 N"은 버블 안쪽 옆·바닥 정렬 — 우측(내) 버블은 좌측에, 좌측(타인) 버블은 우측에
            HStack(alignment: .bottom, spacing: 4) {
                if isMine && readCount > 0 {
                    readCountLabel
                }
                VStack(alignment: isMine ? .trailing : .leading, spacing: 2) {
                    if !isMine && showAuthor {
                        Text(message.authorName)
                            .font(.caption.bold())
                            .foregroundColor(colors.inkSoft)
                    }
                    if let attachment = message.attachment {
                        attachmentContent(attachment)
                    }
                    if !message.text.isEmpty {
                        Text(message.text)
                            .font(.subheadline)
                            .foregroundColor(isMine ? colors.onAccent : colors.ink)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(
                                RoundedRectangle(cornerRadius: 16)
                                    .fill(isMine ? colors.accent : colors.linen)
                            )
                    }
                }
                if !isMine && readCount > 0 {
                    readCountLabel
                }
            }
            if !isMine {
                Spacer(minLength: 48)
            }
        }
        .frame(maxWidth: .infinity, alignment: isMine ? .trailing : .leading)
    }

    /// 내 메시지의 "읽음 N" — 1명이면 숫자 없이 "읽음"(웹 미러)
    private var readCountLabel: some View {
        Text(readCount > 1 ? "읽음 \(readCount)" : "읽음")
            .font(.caption2)
            .foregroundColor(colors.accent)
    }

    @ViewBuilder private func attachmentContent(_ attachment: ChatAttachment) -> some View {
        if attachment.isImage {
            // 이미지 첨부는 말풍선 배경 없이 그린다(웹 미러)
            AsyncImage(url: URL(string: attachment.url)) { phase in
                if let image = phase.image {
                    image.resizable().scaledToFill()
                } else {
                    colors.linen
                }
            }
            .frame(width: 200, height: 200)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        } else {
            HStack(spacing: 6) {
                Image(systemName: "doc")
                    .font(.system(size: 14))
                    .foregroundColor(colors.inkSoft)
                Text(attachment.name ?? "파일")
                    .font(.caption)
                    .foregroundColor(colors.ink)
                    .lineLimit(1)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(colors.linen)
            )
        }
    }
}
