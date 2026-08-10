import AVFoundation
import SwiftUI
import UIKit

/// 동영상 첫 프레임 로더 — Compose ui/util/VideoFrame.kt(rememberVideoFrame)와 같은 역할.
///
/// 서버가 포스터를 만들어주지 않아(런타임 이미지에 ffmpeg가 없다) 클라이언트가 직접 뽑는다.
/// AVAssetImageGenerator는 전체 파일을 받지 않고 필요한 구간만 스트리밍한다.
enum VideoPosterLoader {
    /// 프레임 한 변의 상한 — 가장 큰 쓰임이 화면 폭 16:9라 이 정도면 충분하고, 원본 1080p를 그대로 들면 메모리가 아깝다
    private static let maxFramePixels: CGFloat = 640

    /// 피드를 오르내려도 다시 뽑지 않게 들고 있는다. NSCache라 메모리 압박 시 알아서 비워진다
    private static let cache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.countLimit = 24
        return cache
    }()

    static func cached(_ urlString: String) -> UIImage? {
        cache.object(forKey: urlString as NSString)
    }

    /// 첫 프레임 한 장. 실패는 조용히 nil로 흘린다 — 코덱 미지원·네트워크 실패 모두
    /// "포스터가 없다"로 같게 취급하고 호출부가 검은 자리로 폴백한다.
    static func load(_ urlString: String) async -> UIImage? {
        if let hit = cached(urlString) { return hit }
        guard let url = URL(string: urlString) else { return nil }

        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
        // 세로로 찍은 영상이 눕지 않게 한다
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: maxFramePixels, height: maxFramePixels)

        let image = await withCheckedContinuation { (continuation: CheckedContinuation<UIImage?, Never>) in
            // 정확히 0을 요구하면 디코딩이 비싸다 — 0 근처 아무 프레임이나 받는다(Compose OPTION_CLOSEST_SYNC와 같은 뜻)
            generator.requestedTimeToleranceBefore = .positiveInfinity
            generator.requestedTimeToleranceAfter = .positiveInfinity
            generator.generateCGImagesAsynchronously(forTimes: [NSValue(time: .zero)]) { _, cgImage, _, _, _ in
                continuation.resume(returning: cgImage.map(UIImage.init(cgImage:)))
            }
        }
        if let image = image {
            cache.setObject(image, forKey: urlString as NSString)
        }

        return image
    }
}

/// 첫 프레임 + ▶ 뱃지 — Compose VideoPoster 미러.
/// 프레임을 아직 못 읽었으면 검은 바탕만 깔린다. ▶는 항상 있어서 "동영상"이라는 건 알 수 있다.
/// 프레임 로딩은 호출부 몫이다(VideoPosterLoader) — 상자 종횡비 계산에 호출부도 프레임이 필요해서다.
struct VideoPoster: View {
    let image: UIImage?

    let badgeSize: CGFloat

    var body: some View {
        ZStack {
            Color.black.opacity(0.85)
            if let image = image {
                Image(uiImage: image)
                    .resizable()
                    // 칸을 꽉 채운다 — 세로 영상이 정사각 칸에서 letterbox로 남으면 더 알아보기 어렵다
                    .scaledToFill()
            }
            Circle()
                .fill(Color.black.opacity(0.45))
                .frame(width: badgeSize, height: badgeSize)
            Image(systemName: "play.fill")
                .foregroundColor(.white)
                .font(badgeSize > 44 ? .title3 : .subheadline)
        }
        .clipped()
    }
}
