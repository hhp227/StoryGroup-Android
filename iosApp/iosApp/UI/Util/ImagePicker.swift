import AVFoundation
import PhotosUI
import Shared
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// 무엇을 고르게 할지 — Compose ui/util/ImagePicker.kt의 PickerMode 미러
enum PickerMode {
    case image
    case video
}

/// Compose PickedImage(동영상 필드)의 Swift 미러 — 압축 판정(VideoCompressionPlanner) 입력을 함께 나른다
struct PickedVideo {
    let url: URL
    let durationMs: Int64
    let width: Int32
    let height: Int32
    let sizeBytes: Int64
    let fileName: String
    let contentType: String
}

/// 단일 이미지/동영상 피커 — PHPickerViewController(iOS 14+, 갤러리 접근 권한 불필요·Info.plist 키 불필요).
/// 이미지는 원본 bytes 그대로 전달한다 — 재인코딩·다운스케일은 shared ImageCompressor가 업로드 직전에
/// 일원화 담당한다(§7, 여기서 미리 변환하면 압축이 두 번 걸린다).
/// 동영상은 파일 경로+메타데이터로 전달한다 — 압축 큐가 경로로 받고, 원본을 메모리에 올리지 않는다.
/// Compose ui/util/ImagePicker.kt(rememberImagePickerLauncher)와 같은 역할.
///
/// 이름에 Image가 남아 있는 건 동영상까지 다루게 된 뒤에도 바꾸지 않았기 때문이다 — 리네임하면
/// 호출부와 pbxproj 경로까지 함께 움직여야 해서, 동영상과 무관한 이유로 빌드를 흔들게 된다.
struct ImagePicker: UIViewControllerRepresentable {
    /// 기본값이 있어 기존 이미지 호출부는 그대로 컴파일된다
    var mode: PickerMode = .image

    /// 동영상 선택 결과 — mode == .video일 때만 호출된다
    var onPickedVideo: ((PickedVideo) -> Void)? = nil

    /// 이미지 선택 결과 (bytes, fileName, contentType) — 트레일링 클로저 호출부 유지를 위해 마지막 파라미터
    var onPicked: (Data, String, String) -> Void = { _, _, _ in }

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration()
        config.filter = mode == .video ? .videos : .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(mode: mode, onPicked: onPicked, onPickedVideo: onPickedVideo) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        private let mode: PickerMode

        private let onPicked: (Data, String, String) -> Void

        private let onPickedVideo: ((PickedVideo) -> Void)?

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            picker.dismiss(animated: true)
            guard let provider = results.first?.itemProvider else { return }

            if mode == .video {
                loadVideo(from: provider)
            } else {
                loadImage(from: provider)
            }
        }

        private func loadImage(from provider: NSItemProvider) {
            let typeIdentifier = UTType.image.identifier
            guard provider.hasItemConformingToTypeIdentifier(typeIdentifier) else { return }

            provider.loadDataRepresentation(forTypeIdentifier: typeIdentifier) { data, _ in
                guard let data = data else { return }

                // 원본 포맷 그대로 전달(HEIC면 image/heic) — 서버는 image/* prefix만 검증한다
                let mime = provider.registeredTypeIdentifiers
                    .compactMap { UTType($0)?.preferredMIMEType }
                    .first { $0.hasPrefix("image/") } ?? "image/jpeg"
                let fileExtension = mime.components(separatedBy: "/").last ?? "jpg"
                DispatchQueue.main.async {
                    self.onPicked(data, "upload.\(fileExtension)", mime)
                }
            }
        }

        /// 동영상은 임시 파일 URL로 받아 앱 tmp로 복사한다 — 콜백이 끝나면 피커의 임시 파일이 지워지고,
        /// 압축 판정(Planner) 입력인 duration·해상도·크기를 AVAsset에서 함께 뽑는다.
        private func loadVideo(from provider: NSItemProvider) {
            let typeIdentifier = UTType.movie.identifier
            guard provider.hasItemConformingToTypeIdentifier(typeIdentifier) else { return }

            provider.loadFileRepresentation(forTypeIdentifier: typeIdentifier) { url, _ in
                guard let url = url else { return }

                let fileExtension = url.pathExtension.isEmpty ? "mp4" : url.pathExtension
                let copied = FileManager.default.temporaryDirectory
                    .appendingPathComponent("picked-\(UUID().uuidString).\(fileExtension)")
                guard (try? FileManager.default.copyItem(at: url, to: copied)) != nil else { return }

                let asset = AVAsset(url: copied)
                let durationMs = Int64(CMTimeGetSeconds(asset.duration) * 1000)
                // 표시 방향 기준 치수 — preferredTransform을 적용해 세로 영상은 세로 치수로 만든다
                let track = asset.tracks(withMediaType: .video).first
                let displaySize = track.map { $0.naturalSize.applying($0.preferredTransform) } ?? .zero
                let sizeBytes = (try? FileManager.default.attributesOfItem(atPath: copied.path)[.size] as? NSNumber)?.int64Value ?? 0
                // 서버는 저장 이름을 UUID+확장자로 다시 만든다 — 확장자만 지켜주면 된다
                let contentType = UTType(filenameExtension: fileExtension)?.preferredMIMEType ?? "video/mp4"
                let picked = PickedVideo(
                    url: copied,
                    durationMs: durationMs,
                    width: Int32(abs(displaySize.width)),
                    height: Int32(abs(displaySize.height)),
                    sizeBytes: sizeBytes,
                    fileName: "upload.\(fileExtension)",
                    contentType: contentType
                )
                DispatchQueue.main.async {
                    self.onPickedVideo?(picked)
                }
            }
        }

        init(mode: PickerMode, onPicked: @escaping (Data, String, String) -> Void, onPickedVideo: ((PickedVideo) -> Void)?) {
            self.mode = mode
            self.onPicked = onPicked
            self.onPickedVideo = onPickedVideo
        }
    }
}

/// Kotlin ByteArray 브리지 — KMP suspend 유스케이스에 Data를 그대로 못 넘겨 변환이 필요하다.
/// 변환 자체는 shared의 nsDataToByteArray가 memcpy 한 번으로 처리한다 — 여기서 KotlinByteArray를
/// 만들어 한 바이트씩 넣으면 바이트마다 Swift↔Kotlin 경계를 넘어, 10MB 동영상에서 눈에 띄게 멈춘다.
extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        MediaBridgesKt.nsDataToByteArray(data: (self as NSData) as Data)
    }
}
