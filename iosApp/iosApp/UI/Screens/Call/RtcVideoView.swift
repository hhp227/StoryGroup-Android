import SwiftUI
import WebRTC

/// WebRTC 비디오 타일 — RTCMTLVideoView(Metal 렌더러) 래퍼(Compose RtcVideoView의 iOS판).
/// 거울 모드는 로컬 미리보기만 — 상대에게는 원본이 간다(웹 VideoTile 미러).
struct RtcVideoView: UIViewRepresentable {
    let track: RTCVideoTrack

    var mirror = false

    func makeUIView(context: Context) -> RTCMTLVideoView {
        let view = RTCMTLVideoView()

        view.videoContentMode = .scaleAspectFill
        view.clipsToBounds = true
        return view
    }

    func updateUIView(_ view: RTCMTLVideoView, context: Context) {
        // 트랙 교체(재연결) 대응 — 이전 트랙에서 떼고 새 트랙을 붙인다
        if context.coordinator.attachedTrack !== track {
            context.coordinator.attachedTrack?.remove(view)
            track.add(view)
            context.coordinator.attachedTrack = track
        }
        view.transform = mirror ? CGAffineTransform(scaleX: -1, y: 1) : .identity
    }

    static func dismantleUIView(_ view: RTCMTLVideoView, coordinator: Coordinator) {
        coordinator.attachedTrack?.remove(view)
        coordinator.attachedTrack = nil
    }

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator {
        var attachedTrack: RTCVideoTrack?
    }
}
