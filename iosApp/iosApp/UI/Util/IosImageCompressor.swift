import Foundation
import Shared

/// shared ImageCompressor(Kotlin 프로토콜) 채택 — suspend는 completionHandler 메소드로 노출된다.
/// 실패 시 원본 반환(§3-3) — 에러를 completionHandler에 싣지 않는다(업로드를 압축 실패로 막지 않기 위해).
final class IosImageCompressor: ImageCompressor {
    func compress(
        bytes: KotlinByteArray,
        contentType: String,
        completionHandler: @escaping (CompressedImage?, Error?) -> Void
    ) {
        DispatchQueue.global(qos: .userInitiated).async {
            let data = MediaBridgesKt.byteArrayToNsData(bytes: bytes)
            let original = CompressedImage(bytes: bytes, contentType: contentType)
            guard let (out, outType) = MediaCompressionQueue.compressImage(data: data, contentType: contentType) else {
                return completionHandler(original, nil)
            }
            completionHandler(CompressedImage(bytes: out.toKotlinByteArray(), contentType: outType), nil)
        }
    }
}
