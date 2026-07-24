import PhotosUI
import Shared
import SwiftUI
import UIKit

/// 단일 이미지 피커 — PHPickerViewController(iOS 14+, 갤러리 접근 권한 불필요·Info.plist 키 불필요).
/// HEIC 등 원본 포맷과 무관하게 JPEG로 통일해 서버 컨텐츠타입 처리를 단순화한다.
/// Compose ui/util/ImagePicker.kt(rememberImagePickerLauncher)와 같은 역할.
struct ImagePicker: UIViewControllerRepresentable {
    let onPicked: (Data, String, String) -> Void

    func makeUIViewController(context: Context) -> PHPickerViewController {
        var config = PHPickerConfiguration()
        config.filter = .images
        config.selectionLimit = 1
        let picker = PHPickerViewController(configuration: config)
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: PHPickerViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(onPicked: onPicked) }

    final class Coordinator: NSObject, PHPickerViewControllerDelegate {
        private let onPicked: (Data, String, String) -> Void

        init(onPicked: @escaping (Data, String, String) -> Void) {
            self.onPicked = onPicked
        }

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            picker.dismiss(animated: true)
            guard let provider = results.first?.itemProvider, provider.canLoadObject(ofClass: UIImage.self) else { return }

            provider.loadObject(ofClass: UIImage.self) { object, _ in
                guard let image = object as? UIImage, let data = image.jpegData(compressionQuality: 0.85) else { return }
                DispatchQueue.main.async {
                    self.onPicked(data, "upload.jpg", "image/jpeg")
                }
            }
        }
    }
}

/// Kotlin ByteArray 브리지 — KMP suspend 유스케이스에 Data를 그대로 못 넘겨 바이트 단위로 옮겨 담는다
extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let result = KotlinByteArray(size: Int32(count))
        withUnsafeBytes { (buffer: UnsafeRawBufferPointer) in
            let bytes = buffer.bindMemory(to: UInt8.self)
            for (index, byte) in bytes.enumerated() {
                result.set(index: Int32(index), value: Int8(bitPattern: byte))
            }
        }
        return result
    }
}
