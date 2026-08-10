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

/// 단일 이미지/동영상 피커 — PHPickerViewController(iOS 14+, 갤러리 접근 권한 불필요·Info.plist 키 불필요).
/// 이미지는 HEIC 등 원본 포맷과 무관하게 JPEG로 통일해 서버 컨텐츠타입 처리를 단순화하고,
/// 동영상은 원본 파일을 그대로 올린다(재인코딩하면 시간도 오래 걸리고 화질만 떨어진다).
/// Compose ui/util/ImagePicker.kt(rememberImagePickerLauncher)와 같은 역할.
///
/// 이름에 Image가 남아 있는 건 동영상까지 다루게 된 뒤에도 바꾸지 않았기 때문이다 — 리네임하면
/// 호출부와 pbxproj 경로까지 함께 움직여야 해서, 동영상과 무관한 이유로 빌드를 흔들게 된다.
struct ImagePicker: UIViewControllerRepresentable {
    /// 기본값이 있어 기존 이미지 호출부는 그대로 컴파일된다
    var mode: PickerMode = .image

    let onPicked: (Data, String, String) -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration()
        config.filter = mode == .video ? .videos : .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(mode: mode, onPicked: onPicked) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        private let mode: PickerMode

        private let onPicked: (Data, String, String) -> Void

        init(mode: PickerMode, onPicked: @escaping (Data, String, String) -> Void) {
            self.mode = mode
            self.onPicked = onPicked
        }

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
            guard provider.canLoadObject(ofClass: UIImage.self) else { return }

            provider.loadObject(ofClass: UIImage.self) { object, _ in
                guard let image = object as? UIImage, let data = image.jpegData(compressionQuality: 0.85) else { return }
                DispatchQueue.main.async {
                    self.onPicked(data, "upload.jpg", "image/jpeg")
                }
            }
        }

        /// 동영상은 UIImage로 못 읽는다 — 임시 파일로 받아 바이트를 읽는다.
        /// 콜백이 끝나면 임시 파일이 지워지므로 그 안에서 Data로 옮겨 담아야 한다.
        private func loadVideo(from provider: NSItemProvider) {
            let typeIdentifier = UTType.movie.identifier
            guard provider.hasItemConformingToTypeIdentifier(typeIdentifier) else { return }

            provider.loadFileRepresentation(forTypeIdentifier: typeIdentifier) { url, _ in
                guard let url = url, let data = try? Data(contentsOf: url) else { return }

                // 서버는 저장 이름을 UUID+확장자로 다시 만든다 — 확장자만 지켜주면 된다
                let fileExtension = url.pathExtension.isEmpty ? "mp4" : url.pathExtension
                let contentType = UTType(filenameExtension: fileExtension)?.preferredMIMEType ?? "video/mp4"
                DispatchQueue.main.async {
                    self.onPicked(data, "upload.\(fileExtension)", contentType)
                }
            }
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
