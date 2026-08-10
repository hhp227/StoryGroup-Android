import AVKit
import SwiftUI
import UIKit

/// 첫 프레임이 오기 전(또는 못 뽑았을 때)의 자리 표시 종횡비
private let fallbackVideoAspectRatio: CGFloat = 16.0 / 9.0

/// 게시글 첨부 동영상 한 칸 — Compose SgVideoAttachment.kt와 1:1 미러.
/// 평소엔 첫 프레임 위에 ▶를 얹은 자리 표시, isPlaying이면 그 자리에 AVKit 재생기를 띄운다.
/// 상자는 가로를 꽉 채우고 세로는 동영상 실제 종횡비(첫 프레임 크기)를 따른다 — 세로 영상이 16:9 상자에서
/// 작게 letterbox 되지 않게.
///
/// 재생 상태를 스스로 갖지 않는 이유: 한 게시글에 동영상이 여럿일 때 재생기가 여럿 뜨지 않도록
/// 호출부가 "지금 재생 중인 URL" 하나만 들고 판단하기 때문이다.
struct SGVideoAttachment: View {
    let urlString: String

    let isPlaying: Bool

    let onPlayRequest: () -> Void

    /// 프레임을 여기서 들고 있어야 재생기로 바뀐 뒤에도 상자가 같은 비율을 유지한다
    @State private var poster: UIImage?

    private var aspectRatio: CGFloat {
        guard let size = poster?.size, size.height > 0 else { return fallbackVideoAspectRatio }
        return size.width / size.height
    }

    var body: some View {
        Group {
            if isPlaying, let url = URL(string: urlString) {
                SGVideoPlayer(url: url)
            } else {
                Button(action: onPlayRequest) {
                    VideoPoster(image: poster, badgeSize: 56)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("동영상 재생")
            }
        }
        .aspectRatio(aspectRatio, contentMode: .fit)
        .frame(maxWidth: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .task(id: urlString) {
            if poster == nil {
                poster = await VideoPosterLoader.load(urlString)
            }
        }
    }

    init(urlString: String, isPlaying: Bool, onPlayRequest: @escaping () -> Void) {
        self.urlString = urlString
        self.isPlaying = isPlaying
        self.onPlayRequest = onPlayRequest
        _poster = State(initialValue: VideoPosterLoader.cached(urlString))
    }
}

/// AVPlayer 소유자 — VideoPlayer(player:)에 매번 새 AVPlayer를 만들어 넘기면 다시 그릴 때마다
/// 재생이 처음으로 돌아가므로, 뷰가 인스턴스를 들고 있어야 한다.
private struct SGVideoPlayer: View {
    let url: URL

    @State private var player: AVPlayer?

    var body: some View {
        VideoPlayer(player: player)
            .onAppear {
                // 자리 표시를 눌러야 여기 오므로, 나타난 시점엔 이미 사용자가 재생을 요청한 상태다
                let player = AVPlayer(url: url)
                self.player = player
                player.play()
            }
            .onDisappear {
                // 화면을 벗어나거나 다른 동영상으로 넘어가면 반드시 멈춘다 — 안 하면 소리가 남는다
                player?.pause()
                player = nil
            }
    }
}

/// 피드 카드·작성 폼용 동영상 썸네일 — 이미지 썸네일과 같은 정사각 칸에 첫 프레임과 ▶를 얹는다.
/// 여기서는 재생하지 않는다(피드 카드는 전체가 상세로 가는 링크라 탭이 겹친다).
struct SGVideoThumbnail: View {
    let urlString: String

    let size: CGFloat

    @State private var poster: UIImage?

    var body: some View {
        VideoPoster(image: poster, badgeSize: 36)
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .task(id: urlString) {
                if poster == nil {
                    poster = await VideoPosterLoader.load(urlString)
                }
            }
    }

    init(urlString: String, size: CGFloat) {
        self.urlString = urlString
        self.size = size
        _poster = State(initialValue: VideoPosterLoader.cached(urlString))
    }
}
