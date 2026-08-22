import AVFoundation
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

    /// 타인 아바타 탭 → 공개 프로필 시트 — Compose UserProfileRoute 다이얼로그 미러
    @State private var selectedProfileUserId: Int64? = nil

    /// 프로필 시트에서 DM 성공 후속 push 대상 — 시트 dismiss 완료 후 push한다(PostDetailView 선례).
    /// 본인 "프로필 수정" 후속은 없다 — 채팅방 아바타는 타인 전용(내 메시지엔 아바타가 없다)
    @State private var profileFollowUpRoom: ChatRoomRef? = nil

    /// 프로필 시트에서 연 DM 방 push — 채팅방 위에 새 채팅방이 쌓인다(Compose navigate 미러)
    @State private var pushedChatRoom: ChatRoomRef? = nil

    /// false면 보이스톡(카메라 OFF·수화구 시작) — 첨부 패널에서만 갈리고 상단바·참가는 페이스톡
    @State private var callVideo = true

    @State private var input = ""

    @State private var showImagePicker = false

    @State private var showFilePicker = false

    /// + 버튼 첨부 패널(카톡 미러) — 열 때 키보드를 내리고 그 자리에 나타난다
    @State private var showAttachments = false

    /// 첨부 패널 안의 이모지 페이지(카톡 미러) — 패널을 새로 열면 첨부 목록으로 되돌아온다
    @State private var showEmojiPicker = false

    /// 우측 사이드 드로어(카톡 미러) — 대화상대·사진·통화. 순수 UI 상태라 VM에 두지 않는다
    @State private var showDrawer = false

    /// 드로어 사진 탭 → 그 메시지로 이동. proxy는 ScrollViewReader 안에만 있어 id를 실어 보낸다
    @State private var jumpToMessageId: Int64? = nil

    /// 관측한 키보드 높이(하단 안전영역 제외) — 첨부·이모지 패널을 키보드 자리에 같은 높이로 띄운다(카톡 미러)
    @State private var keyboardHeight: CGFloat = 0

    @Environment(\.sgColors) private var colors

    /// 통화·DM 방 push — NavigationStack은 iOS 16+라 iOS 15는 숨김 NavigationLink 폴백(그룹 상세 미러).
    /// 공개 프로필은 push가 아니라 시트 — DM 후속 push는 시트가 완전히 닫힌 뒤(onDismiss)에 한다
    var body: some View {
        if #available(iOS 16.0, *) {
            coreWithDrawer
                .navigationDestination(isPresented: $showCall) { callDestination }
                .navigationDestination(isPresented: showPushedChatRoom) { pushedChatRoomDestination }
                .sheet(isPresented: showProfile, onDismiss: runProfileFollowUp) { profileDestination }
        } else {
            coreWithDrawer
                .background(
                    NavigationLink(isActive: $showCall) {
                        callDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .background(
                    NavigationLink(isActive: showPushedChatRoom) {
                        pushedChatRoomDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .sheet(isPresented: showProfile, onDismiss: runProfileFollowUp) { profileDestination }
        }
    }

    private var callDestination: some View {
        CallView(chatRoomId: chatRoomId, title: title, ring: true, video: callVideo, container: container)
    }

    /// 타인 아바타 탭 → 공개 프로필 시트(그룹 상세 멤버 스트립·게시글 작성자 탭과 같은 진입 규칙)
    @ViewBuilder private var profileDestination: some View {
        if let userId = selectedProfileUserId {
            UserProfileView(
                userId: userId,
                container: container,
                onOpenChatRoom: { room in
                    // 이미 이 방이면(멱등 DM 열기가 같은 id를 돌려준다) 또 쌓지 않는다 —
                    // 시트만 닫아 복귀(카카오톡 방식, Compose App.kt 미러)
                    if room.chatRoomId != chatRoomId {
                        profileFollowUpRoom = room
                    }
                    selectedProfileUserId = nil
                },
                // 채팅방 아바타는 타인 전용 — 본인 "프로필 수정"은 도달 불가라 배선하지 않는다
                onOpenAccountSettings: {}
            )
        }
    }

    @ViewBuilder private var pushedChatRoomDestination: some View {
        if let room = pushedChatRoom {
            ChatRoomView(
                chatRoomId: room.chatRoomId,
                groupId: room.groupId,
                title: room.title,
                container: container,
                chatViewModel: chatViewModel
            )
        }
    }

    /// 프로필 시트 dismiss 완료 후 DM 방 push — 드래그로 닫으면 followUp이 nil이라 아무 일 없다
    private func runProfileFollowUp() {
        if let room = profileFollowUpRoom {
            pushedChatRoom = room
            profileFollowUpRoom = nil
        }
    }

    /// 시트를 닫으면(X·드래그) selectedProfileUserId를 nil로 되돌리는 브리지(MainShellView 선례)
    private var showProfile: Binding<Bool> {
        Binding(
            get: { selectedProfileUserId != nil },
            set: { if !$0 { selectedProfileUserId = nil } }
        )
    }

    /// pop(백 버튼/스와이프) 시 pushedChatRoom을 nil로 되돌리는 브리지(MainShellView 선례)
    private var showPushedChatRoom: Binding<Bool> {
        Binding(
            get: { pushedChatRoom != nil },
            set: { if !$0 { pushedChatRoom = nil } }
        )
    }

    /// core + 우측 드로어 — 스크림과 패널을 **별개 오버레이**로 얹는다.
    ///
    /// ⚠️한 ZStack에 같이 넣으면 안 된다: 스크림의 .ignoresSafeArea()가 ZStack을 안전영역 밖까지
    /// 넓혀 패널이 상태바 밑으로 딸려 올라가고, 그렇다고 스크림을 빼면 ZStack이 패널 폭(288)으로
    /// 쪼그라들어 오버레이 기본 정렬대로 화면 가운데에 뜬다. 둘로 나누면 스크림은 화면 전체를
    /// 덮고 패널은 alignment: .trailing으로 안전영역 안 우측에 붙는다.
    @ViewBuilder private var coreWithDrawer: some View {
        core
            .overlay { drawerScrim }
            .overlay(alignment: .trailing) { drawerPanelLayer }
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
                    Button("참가") {
                        callVideo = true
                        showCall = true
                    }
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
                                        // 최신순 목록이라 시간상 직전 메시지는 다음 인덱스(Compose 미러)
                                        let previous = index == messages.count - 1 ? nil : messages[index + 1]

                                        VStack(spacing: 4) {
                                            // 날짜가 바뀌는 첫 메시지 위에 날짜 버블 — 별도 행이 아니라 행 안에
                                            // 합성한다(행을 끼우면 플립 위치 유지가 흔들린다 — Compose 키 앵커 미러).
                                            // 이전 페이지가 위로 끼면 판정이 다시 돌아 버블이 더 오래된 첫 메시지로 옮겨 붙는다
                                            if previous.map({ TimeFormats.chatDateKey($0.createdAt) }) != TimeFormats.chatDateKey(message.createdAt) {
                                                dateBubble(message.createdAt)
                                            }
                                            MessageRow(
                                                message: message,
                                                isMine: isMine,
                                                showAuthor: previous?.userId != message.userId,
                                                // "읽음 N" = 내 메시지에 대해, 위치가 그 메시지 이상인 타인 수(웹 미러)
                                                readCount: isMine
                                                    ? otherReadPositions.filter { $0 >= message.id }.count
                                                    : 0,
                                                onAuthorTap: { selectedProfileUserId = message.userId }
                                            )
                                        }
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
                        // 드로어 사진 탭 → 그 메시지로 이동(플립이라 anchor는 .top)
                        .onChange(of: jumpToMessageId) { target in
                            guard let target else { return }

                            withAnimation { proxy.scrollTo(target, anchor: .top) }
                            jumpToMessageId = nil
                        }
                    }
                }
            }
            // 목록과 입력 영역을 가르는 헤어라인 — 레거시 1px darker_gray 미러
            Divider().background(colors.stoneBorder)
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
            if let progress = uiState.compressionProgress {
                Text("동영상 압축 중 \(Int(progress * 100))%")
                    .font(.caption2)
                    .foregroundColor(colors.inkFaint)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 2)
            }
            // 압축 중에도 isSending 취급 — 전송·추가 첨부를 막는다(완료되면 압축본이 대기 첨부로 채워진다)
            inputBar(
                isSending: uiState.isSending || uiState.compressionProgress != nil,
                hasPendingAttachment: uiState.pendingAttachment != nil
            )
            if showAttachments {
                if showEmojiPicker {
                    emojiPanel
                } else {
                    attachmentPanel
                }
            }
        }
        .background(colors.paper.ignoresSafeArea())
        // 드로어가 열리면 제목도 비운다 — UIKit 내비바는 SwiftUI 콘텐츠보다 항상 위에 그려져서
        // 오버레이로 덮을 수가 없다. 대신 배경을 투명으로 돌리고(아래 navigationBarScrim) 제목·버튼을
        // 걷어내면, 그 자리를 드로어 스크림과 패널이 채워 덮인 것처럼 보인다(셸 드로어와 같은 수법)
        .navigationTitle(showDrawer ? "" : title)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(showDrawer)
        // 평소엔 기본 내비바(호출 화면이 투명 바 상태로 push해도 이 화면은 불투명),
        // 드로어가 열린 동안만 투명 — 복귀 시엔 호출 화면이 자기 값을 재적용한다
        .navigationBarScrim(visible: !showDrawer)
        // 우측 사이드 드로어(카톡 미러) — 통화는 드로어 하단과 + 첨부 패널 두 곳에 남는다
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                // ⚠️조건 분기는 ToolbarItem "안"에 둔다 — ToolbarContentBuilder의 buildIf는 iOS 16+라
                // .toolbar { if ... } 는 배포 타깃 15.0에서 컴파일되지 않는다(PostDetailView와 같은 형태)
                if !showDrawer {
                    Button(action: {
                        // ⚠️withAnimation 없이 상태만 바꾸면 transition이 안 걸려 툭 나타난다
                        withAnimation(.easeOut(duration: 0.25)) { showDrawer = true }
                        viewModel.onAction(.loadMembers)
                    }) {
                        Image(systemName: "line.3.horizontal")
                    }
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
        ) { notification in
            // 키보드 높이(안전영역 제외)를 기억 — 다음에 패널을 열면 같은 높이로 띄운다
            if let frame = (notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue {
                keyboardHeight = frame.height - bottomSafeInset
            }
            // 패널 제거를 키보드 상승과 같은 시간으로 애니메이션 — 즉시 지우면 패널 높이가
            // 한 번에 꺼져 레이아웃이 튄다(카톡 미러)
            let duration = notification
                .userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? Double

            withAnimation(.easeOut(duration: duration ?? 0.25)) {
                showAttachments = false
            }
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
            let contentType = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType
                ?? "application/octet-stream"

            if contentType.hasPrefix("video/") {
                // 동영상은 압축 대상(§4-b) — tmp 복사+AVAsset 메타로 PickedVideo를 만든다(ImagePicker.loadVideo 미러)
                let fileExtension = url.pathExtension.isEmpty ? "mp4" : url.pathExtension
                let copied = FileManager.default.temporaryDirectory
                    .appendingPathComponent("picked-\(UUID().uuidString).\(fileExtension)")
                guard (try? FileManager.default.copyItem(at: url, to: copied)) != nil else { return }
                let asset = AVAsset(url: copied)
                let durationMs = Int64(CMTimeGetSeconds(asset.duration) * 1000)
                let track = asset.tracks(withMediaType: .video).first
                let displaySize = track.map { $0.naturalSize.applying($0.preferredTransform) } ?? .zero
                let sizeBytes = (try? FileManager.default.attributesOfItem(atPath: copied.path)[.size] as? NSNumber)?.int64Value ?? 0
                viewModel.onAction(.attachVideo(picked: PickedVideo(
                    url: copied,
                    durationMs: durationMs,
                    width: Int32(abs(displaySize.width)),
                    height: Int32(abs(displaySize.height)),
                    sizeBytes: sizeBytes,
                    fileName: url.lastPathComponent,
                    contentType: contentType
                )))
                return
            }
            guard let data = try? Data(contentsOf: url) else { return }

            viewModel.onAction(.attach(data: data, fileName: url.lastPathComponent, contentType: contentType))
        }
    }

    /// 날짜 구분 버블 — 그날 첫 메시지 위 가로 중앙 pill(카카오톡 관례, 기기 로컬 기준).
    /// Compose ChatDateBubble과 1:1 미러
    private func dateBubble(_ isoDateTime: String) -> some View {
        Text(TimeFormats.chatDate(isoDateTime))
            .font(.caption2)
            .foregroundColor(colors.inkSoft)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(
                RoundedRectangle(cornerRadius: 999, style: .continuous)
                    .fill(colors.linen)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 999, style: .continuous)
                    .stroke(colors.stoneBorder, lineWidth: 1)
            )
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
    }

    /// 로드된 이력 안의 이미지 첨부 — 서버 사진함 API가 없어 화면이 파생한다(위로 더 불러오면 늘어난다).
    /// VM 목록이 최신순이라 그대로 최신순, 메시지 id는 탭했을 때의 이동 대상이다
    private var drawerPhotos: [DrawerPhoto] {
        viewModel.uiState.messages.compactMap { message in
            guard let attachment = message.attachment, attachment.isImage else { return nil }

            return DrawerPhoto(id: message.id, url: attachment.url)
        }
    }

    /// 그룹 방은 그룹 멤버, DM 방은 메시지에서 집은 상대 1명 — 채팅방 참여자 API가 없다
    private var drawerPeer: ChatMessage? {
        viewModel.uiState.messages.first { $0.userId != viewModel.uiState.myUserId }
    }

    /// 드로어 뒤 스크림 — 화면 전체(안전영역 포함)를 덮고, 탭하면 닫힌다(셸 드로어 미러)
    @ViewBuilder private var drawerScrim: some View {
        if showDrawer {
            Color.black.opacity(0.35)
                .ignoresSafeArea()
                .onTapGesture { closeDrawer() }
                .transition(.opacity)
        }
    }

    /// 우측 사이드 드로어 패널(카카오톡 채팅방 서랍 미러) — 대화상대 / 사진 / 통화.
    /// 우측 끝에 붙어 높이를 꽉 채우고, 열고 닫을 때 옆에서 밀려 나온다(DrawerShellView 미러)
    @ViewBuilder private var drawerPanelLayer: some View {
        if showDrawer {
            drawerPanel
                .frame(width: 288)
                .frame(maxHeight: .infinity)
                // 칠만 아래 안전영역까지 내린다 — 레이아웃은 그대로라 헤더 위치엔 영향이 없고,
                // 홈 인디케이터 자리에 스크림만 남아 어두운 띠가 보이는 것을 막는다.
                // 위쪽(내비바 자리)은 헤더가 자기 linen을 끌어올려 채운다
                .background(colors.paper.ignoresSafeArea(edges: .bottom))
                .transition(.move(edge: .trailing))
        }
    }

    /// 드로어 닫기 — 여는 쪽과 같은 애니메이션으로 묶어 슬라이드가 양방향으로 걸리게 한다
    private func closeDrawer() {
        withAnimation(.easeOut(duration: 0.25)) { showDrawer = false }
    }

    private var drawerPanel: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.subheadline.bold())
                        .foregroundColor(colors.ink)
                        .lineLimit(1)
                    Text(groupId == nil ? "1:1 대화" : "그룹 대화")
                        .font(.caption2)
                        .foregroundColor(colors.inkFaint)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    closeDrawer()
                } label: {
                    Image(systemName: "xmark").foregroundColor(colors.inkSoft)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            // 칠만 위 안전영역까지 끌어올린다 — 드로어가 열리면 내비바가 투명해지므로 그 자리를
            // 헤더 linen이 채워, 오른쪽 288pt가 화면 맨 위부터 드로어로 보인다.
            // 레이아웃은 그대로라 방 이름은 내비바 아래 제자리에 남는다
            .background(colors.linen.ignoresSafeArea(edges: .top))
            Divider().background(colors.stoneBorder)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    drawerSectionTitle("대화상대", count: drawerMemberCountLabel)
                    drawerMembers
                    Divider().background(colors.stoneBorder).padding(.vertical, 8)
                    drawerSectionTitle("사진", count: drawerPhotos.isEmpty ? nil : "\(drawerPhotos.count)장")
                    drawerPhotoGrid
                    Spacer().frame(height: 16)
                }
            }
            Divider().background(colors.stoneBorder)
            // 통화 — 상단바에서 뺀 진입점을 여기로 옮겼다(+ 첨부 패널에도 그대로 있다)
            HStack(spacing: 8) {
                drawerCallButton("phone.fill", "보이스톡") { startCallFromDrawer(video: false) }
                drawerCallButton("video.fill", "페이스톡") { startCallFromDrawer(video: true) }
            }
            .padding(8)
        }
    }

    private var drawerMemberCountLabel: String? {
        if groupId == nil { return "2명" }
        guard let members = viewModel.uiState.members else { return nil }

        return "\(members.count)명"
    }

    @ViewBuilder private var drawerMembers: some View {
        let uiState = viewModel.uiState

        if groupId == nil {
            // DM 방엔 참여자 API도 groupId도 없다 — 상대는 내 것이 아닌 첫 메시지에서 집는다
            if let peer = drawerPeer {
                drawerMemberRow(
                    name: peer.authorName,
                    profileImg: peer.authorProfileImg,
                    role: nil,
                    isMe: false
                ) { openProfileFromDrawer(userId: peer.userId) }
            }
        } else if uiState.isLoadingMembers && uiState.members == nil {
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
        } else if let members = uiState.members {
            ForEach(members, id: \.userId) { member in
                drawerMemberRow(
                    name: member.name,
                    profileImg: member.profileImg,
                    role: member.role == .member ? nil : member.role,
                    isMe: member.userId == uiState.myUserId
                ) { openProfileFromDrawer(userId: member.userId) }
            }
        } else {
            Text("대화상대를 불러오지 못했습니다.")
                .font(.caption)
                .foregroundColor(colors.rust)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
        }
    }

    @ViewBuilder private var drawerPhotoGrid: some View {
        if drawerPhotos.isEmpty {
            Text("주고받은 사진이 없습니다.")
                .font(.caption)
                .foregroundColor(colors.inkFaint)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
        } else {
            // 미리보기는 최신 9장 — 더 보려면 이력을 위로 더 불러오면 된다
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 3), spacing: 4) {
                ForEach(Array(drawerPhotos.prefix(9))) { photo in
                    Button {
                        closeDrawer()
                        jumpToMessageId = photo.id
                    } label: {
                        // 앨범 탭과 같은 정사각 셀 관용구 — scaledToFill은 명시 프레임이 있어야 크롭된다
                        GeometryReader { geometry in
                            AsyncImage(url: URL(string: photo.url)) { phase in
                                if case .success(let image) = phase {
                                    image.resizable().scaledToFill()
                                } else {
                                    colors.linen
                                }
                            }
                            .frame(width: geometry.size.width, height: geometry.size.width)
                            .clipped()
                        }
                        .aspectRatio(1, contentMode: .fit)
                    }
                    .buttonStyle(.plain)
                    .cornerRadius(6)
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private func drawerSectionTitle(_ title: String, count: String?) -> some View {
        HStack {
            Text(title).font(.caption.bold()).foregroundColor(colors.inkSoft)
            Spacer()
            if let count {
                Text(count).font(.caption2).foregroundColor(colors.inkFaint)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 12)
        .padding(.bottom, 4)
    }

    private func drawerMemberRow(
        name: String,
        profileImg: String?,
        role: GroupRole?,
        isMe: Bool,
        onTap: @escaping () -> Void
    ) -> some View {
        Button(action: onTap) {
            HStack(spacing: 10) {
                SGAvatar(name: name, size: 32, imageUrl: profileImg)
                Text(name)
                    .font(.subheadline)
                    .foregroundColor(colors.ink)
                    .lineLimit(1)
                if isMe {
                    Text("나").font(.caption2).foregroundColor(colors.inkFaint)
                }
                Spacer()
                // 역할 칩은 그룹 목록·상세와 같은 것(방장/부방장) — 일반 멤버는 칩을 달지 않는다
                if let role {
                    RoleChip(role: role)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func drawerCallButton(_ systemImage: String, _ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: systemImage).foregroundColor(colors.accent)
                Text(label).font(.caption2).foregroundColor(colors.inkSoft)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// 드로어를 닫고 프로필 시트를 연다 — 드로어 위에 시트를 겹치면 닫힘 처리가 꼬인다
    private func openProfileFromDrawer(userId: Int64) {
        closeDrawer()
        selectedProfileUserId = userId
    }

    private func startCallFromDrawer(video: Bool) {
        closeDrawer()
        callVideo = video
        showCall = true
    }

    private func scrollToLatest(_ proxy: ScrollViewProxy, duration: Double? = nil) {
        if let latest = viewModel.uiState.messages.first?.id {
            withAnimation(duration.map { Animation.easeOut(duration: $0) } ?? .default) {
                // 플립에선 레이아웃 top = 화면 맨 아래
                proxy.scrollTo(latest, anchor: .top)
            }
        }
    }

    /// 전송 대기 첨부(웹 pending chip 미러) — 이미지는 썸네일, 파일은 이름 칩. 취소하면 업로드 없이 그냥 버려진다
    @ViewBuilder private func pendingAttachmentChip(_ pending: ChatRoomViewModel.PendingAttachment) -> some View {
        HStack(spacing: 6) {
            // 이미지면 썸네일로 — 깨진 데이터는 UIImage가 nil이라 이름 칩 폴백(Compose 미러)
            if pending.isImage, let thumbnail = UIImage(data: pending.data) {
                Image(uiImage: thumbnail)
                    .resizable()
                    .scaledToFill()
                    // 높이 기준 원본 비율 폭 — 파노라마는 200pt에서 잘라낸다(Compose 미러)
                    .frame(
                        width: min(200, 64 * thumbnail.size.width / max(thumbnail.size.height, 1)),
                        height: 64
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                Spacer()
            } else {
                Image(systemName: pending.isImage ? "photo" : "doc")
                    .font(.system(size: 14))
                    .foregroundColor(colors.inkSoft)
                Text("\(pending.fileName) (\(formatFileSize(pending.data.count)))")
                    .font(.caption)
                    .foregroundColor(colors.ink)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
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
            // 라벨이 아닌 placeholder로 알린다 — 라벨은 필드 위에 줄을 더해 첨부할 때만 바가 튄다
            SGComposerField(
                placeholder: hasPendingAttachment ? "메시지 (선택)" : "메시지를 입력하세요.",
                text: $input
            )
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
            // 항상 첨부 목록부터 — 이모지 페이지는 패널 안에서 전환된다.
            // 키보드가 내려가는 동안 패널이 같은 리듬으로 나타나게 애니메이션(카톡 미러)
            showEmojiPicker = false
            withAnimation(.easeOut(duration: 0.25)) {
                showAttachments = true
            }
        }
    }

    /// 첨부·이모지 패널 높이 — 키보드를 본 적 없으면 기본 높이(Compose 미러)
    private var panelHeight: CGFloat {
        keyboardHeight > 0 ? keyboardHeight : 280
    }

    private var bottomSafeInset: CGFloat {
        UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first?.safeAreaInsets.bottom ?? 0
    }

    /// + 버튼으로 여는 첨부 패널(카톡 미러, Compose AttachmentPanel 1:1) —
    /// 키보드 자리에 같은 높이로 나타나는 4열 그리드(5번째부터 다음 줄)
    private var attachmentPanel: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 4), spacing: 24) {
            // 패널이 이모지 페이지로 전환된다(닫히지 않음) — 선택은 입력창에 덧붙는다
            attachmentPanelItem(systemImage: "face.smiling", label: "이모지") {
                showEmojiPicker = true
            }
            attachmentPanelItem(systemImage: "photo", label: "사진") {
                showAttachments = false
                showImagePicker = true
            }
            attachmentPanelItem(systemImage: "paperclip", label: "파일") {
                showAttachments = false
                showFilePicker = true
            }
            // 통화 발신과 같은 경로(카톡 미러) — 보이스톡=카메라 OFF·수화구 시작, 페이스톡=영상 통화
            attachmentPanelItem(systemImage: "phone.fill", label: "보이스톡") {
                showAttachments = false
                callVideo = false
                showCall = true
            }
            attachmentPanelItem(systemImage: "video.fill", label: "페이스톡") {
                showAttachments = false
                callVideo = true
                showCall = true
            }
        }
        .padding(.vertical, 24)
        .frame(maxWidth: .infinity, minHeight: panelHeight, maxHeight: panelHeight, alignment: .top)
        .background(colors.linen)
    }

    /// 이모지 페이지(카톡 미러, Compose EmojiPanel 1:1) — 선택할 때마다 입력창에 덧붙는다(패널 유지).
    /// 타이핑 신호는 input onChange가 함께 처리한다
    private var emojiPanel: some View {
        ScrollView {
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 8)) {
                ForEach(Self.chatEmojis, id: \.self) { emoji in
                    Button(action: { input += emoji }) {
                        Text(emoji)
                            .font(.system(size: 24))
                            .padding(.vertical, 8)
                    }
                }
            }
            .padding(12)
        }
        .frame(height: panelHeight)
        .background(colors.linen)
    }

    /// 이모지 팔레트 — composeApp CHAT_EMOJIS와 1:1 동일 목록(웹엔 없는 모바일 전용)
    private static let chatEmojis = [
        "😀", "😂", "🤣", "😊", "😍", "😘", "😎", "🤔",
        "😅", "😭", "😢", "😡", "😱", "🥳", "😴", "🤗",
        "👍", "👎", "👏", "🙏", "💪", "🤝", "✌️", "👌",
        "❤️", "💕", "💖", "💔", "🔥", "⭐", "✨", "🎉",
        "🎂", "🎁", "🌸", "🌈", "☀️", "🌙", "☕", "🍺",
        "🍕", "🍗", "🍜", "🍰", "⚽", "🏀", "🎮", "🎵",
        "🚗", "✈️", "🏠", "💻", "📱", "💤", "💯", "🆗"
    ]

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
            getGroupMembersUseCase: container.getGroupMembersUseCase,
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

/// 드로어 사진 한 칸 — ForEach 식별자용(Swift 튜플엔 키패스를 못 쓴다).
/// id는 메시지 id라 탭했을 때 이동 대상이 그대로 된다(메시지당 첨부 1개라 중복 없음)
private struct DrawerPhoto: Identifiable {
    let id: Int64

    let url: String
}

/// 웹 MessageBubble 미러 — 내 메시지는 우측 accent, 타인은 좌측 linen+작성자 변경 시 아바타/이름
private struct MessageRow: View {
    let message: ChatMessage

    let isMine: Bool

    let showAuthor: Bool

    let readCount: Int

    /// 타인 아바타 탭 → 공개 프로필 시트(내 메시지엔 아바타가 없다)
    let onAuthorTap: () -> Void

    @Environment(\.sgColors) private var colors

    var body: some View {
        // 아바타는 상단 정렬 — Compose Row 기본값(Top) 미러
        HStack(alignment: .top, spacing: 8) {
            if isMine {
                Spacer(minLength: 48)
            } else {
                if showAuthor {
                    // 아바타 탭 → 공개 프로필(그룹 상세 멤버 스트립·게시글 작성자 탭과 같은 진입 규칙)
                    Button(action: onAuthorTap) {
                        SGAvatar(name: message.authorName, size: 32, imageUrl: message.authorProfileImg)
                    }
                    .buttonStyle(.plain)
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
                        // 이름 탭도 아바타와 같은 프로필 진입
                        Button(action: onAuthorTap) {
                            Text(message.authorName)
                                .font(.caption.bold())
                                .foregroundColor(colors.inkSoft)
                        }
                        .buttonStyle(.plain)
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
