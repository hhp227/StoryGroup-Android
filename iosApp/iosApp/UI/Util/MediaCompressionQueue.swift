import AVFoundation
import Shared
import UIKit

/// 미디어 압축 큐 — Compose VideoCompressor(WorkManager)의 Swift 쌍둥이(§10).
/// OperationQueue(동시 1건)에 enqueue → 진행률 콜백 → 완료(경로)/실패, 화면 이탈 시 cancelAll.
/// 동영상은 AVAssetReader/Writer로 플랜 비트레이트를 직접 지정한다(프리셋만 되는 AVAssetExportSession 대신).
/// 단일 인코딩 시도만 담당 — 5MB 초과 재시도는 호출부가 RETRY_MARGIN 플랜으로 다시 enqueue(Compose와 동일 규약).
final class MediaCompressionQueue {
    static let shared = MediaCompressionQueue()

    private let queue: OperationQueue = {
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1
        queue.qualityOfService = .userInitiated
        return queue
    }()

    func compressVideo(
        inputURL: URL,
        plan: VideoPlanCompress,
        onProgress: @escaping (Float) -> Void,
        completion: @escaping (Result<URL, Error>) -> Void
    ) {
        let operation = VideoCompressionOperation(inputURL: inputURL, plan: plan, onProgress: onProgress, completion: completion)
        queue.addOperation(operation)
    }

    func cancelAll() {
        queue.cancelAllOperations()
    }

    /// 이미지 재인코딩(§3) — 판정은 shared Planner, 실패하면 nil(호출부가 원본 폴백).
    /// 반환: (재인코딩 bytes, 결과 contentType)
    static func compressImage(data: Data, contentType: String) -> (Data, String)? {
        guard let image = UIImage(data: data) else { return nil }
        let width = Int32(image.size.width * image.scale)
        let height = Int32(image.size.height * image.scale)
        let plan = ImageCompressionPlanner.shared.plan(
            contentType: contentType, sizeBytes: Int64(data.count), width: width, height: height
        )
        guard let recompress = plan as? ImagePlanRecompress else { return nil } // Skip → 원본 사용
        let targetSize = CGSize(width: CGFloat(recompress.targetWidth), height: CGFloat(recompress.targetHeight))
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
        let scaled = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: targetSize)) }
        if recompress.format == ImageFormat.png {
            guard let out = scaled.pngData() else { return nil }
            return (out, "image/png")
        }
        guard let out = scaled.jpegData(compressionQuality: 0.85) else { return nil }
        return (out, "image/jpeg")
    }
}

enum VideoCompressionError: Error {
    case failed
    case cancelled
}

/// 단일 인코딩 시도 — reader→writer 샘플 펌프로 영상은 재인코딩, 오디오는 PCM으로 풀어 AAC 64k로 다시 싼다
private final class VideoCompressionOperation: Operation {
    private let inputURL: URL

    private let plan: VideoPlanCompress

    private let onProgress: (Float) -> Void

    private let completion: (Result<URL, Error>) -> Void

    override func main() {
        if isCancelled { return finish(.failure(VideoCompressionError.cancelled)) }
        let outputURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("compressed-\(UUID().uuidString).mp4")
        do {
            try encode(to: outputURL)
            finish(.success(outputURL))
        } catch {
            try? FileManager.default.removeItem(at: outputURL)
            finish(.failure(error))
        }
    }

    private func finish(_ result: Result<URL, Error>) {
        let completion = completion
        DispatchQueue.main.async { completion(result) }
    }

    private func encode(to outputURL: URL) throws {
        let asset = AVAsset(url: inputURL)
        guard let videoTrack = asset.tracks(withMediaType: .video).first else { throw VideoCompressionError.failed }
        let audioTrack = asset.tracks(withMediaType: .audio).first
        let durationSeconds = CMTimeGetSeconds(asset.duration)

        let reader = try AVAssetReader(asset: asset)
        let writer = try AVAssetWriter(outputURL: outputURL, fileType: .mp4)
        writer.shouldOptimizeForNetworkUse = true // faststart(§2)

        // 플랜 치수는 표시 방향 기준 — 버퍼는 회전 전이라 transform이 90/270이면 되돌려 준다
        let transform = videoTrack.preferredTransform
        let rotated = abs(transform.b) == 1 && abs(transform.c) == 1
        let encodeWidth = rotated ? Int(plan.targetHeight) : Int(plan.targetWidth)
        let encodeHeight = rotated ? Int(plan.targetWidth) : Int(plan.targetHeight)

        let videoOutput = AVAssetReaderTrackOutput(
            track: videoTrack,
            outputSettings: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange]
        )
        reader.add(videoOutput)
        let videoInput = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: encodeWidth,
            AVVideoHeightKey: encodeHeight,
            AVVideoScalingModeKey: AVVideoScalingModeResize, // 플랜이 비율을 이미 지켰다
            AVVideoCompressionPropertiesKey: [
                AVVideoAverageBitRateKey: plan.videoBitrate,
                AVVideoProfileLevelKey: AVVideoProfileLevelH264MainAutoLevel
            ]
        ])
        videoInput.transform = transform
        writer.add(videoInput)

        var audioOutput: AVAssetReaderTrackOutput?
        var audioInput: AVAssetWriterInput?
        if let audioTrack = audioTrack {
            let output = AVAssetReaderTrackOutput(
                track: audioTrack,
                outputSettings: [AVFormatIDKey: kAudioFormatLinearPCM] // 재인코딩하려면 PCM으로 풀어 읽어야 한다
            )
            reader.add(output)
            let input = AVAssetWriterInput(mediaType: .audio, outputSettings: [
                AVFormatIDKey: kAudioFormatMPEG4AAC,
                AVNumberOfChannelsKey: 2,
                AVSampleRateKey: 44100,
                AVEncoderBitRateKey: plan.audioBitrate
            ])
            writer.add(input)
            audioOutput = output
            audioInput = input
        }

        guard reader.startReading() else { throw VideoCompressionError.failed }
        guard writer.startWriting() else {
            reader.cancelReading()
            throw VideoCompressionError.failed
        }
        writer.startSession(atSourceTime: .zero)

        let group = DispatchGroup()
        pump(from: videoOutput, to: videoInput, group: group, label: "video") { [weak self] time in
            guard let self = self, durationSeconds > 0 else { return }
            let fraction = Float(CMTimeGetSeconds(time) / durationSeconds)
            let onProgress = self.onProgress
            DispatchQueue.main.async { onProgress(min(max(fraction, 0), 1)) }
        }
        if let audioOutput = audioOutput, let audioInput = audioInput {
            pump(from: audioOutput, to: audioInput, group: group, label: "audio", onSampleTime: nil)
        }
        group.wait()

        if isCancelled {
            reader.cancelReading()
            writer.cancelWriting()
            throw VideoCompressionError.cancelled
        }
        let finishGroup = DispatchGroup()
        finishGroup.enter()
        writer.finishWriting { finishGroup.leave() }
        finishGroup.wait()
        guard writer.status == .completed else { throw VideoCompressionError.failed }
    }

    /// reader→writer 샘플 펌프 — requestMediaDataWhenReady 콜백에서 소진될 때까지 복사.
    /// markAsFinished 후 return이 leave 중복을 막는다(콜백 재진입 시에도 한 번만 leave)
    private func pump(
        from output: AVAssetReaderTrackOutput,
        to input: AVAssetWriterInput,
        group: DispatchGroup,
        label: String,
        onSampleTime: ((CMTime) -> Void)?
    ) {
        group.enter()
        let serial = DispatchQueue(label: "media-compression-\(label)")
        input.requestMediaDataWhenReady(on: serial) { [weak self] in
            while input.isReadyForMoreMediaData {
                if self?.isCancelled == true {
                    input.markAsFinished()
                    group.leave()
                    return
                }
                guard let sample = output.copyNextSampleBuffer() else {
                    input.markAsFinished()
                    group.leave()
                    return
                }
                onSampleTime?(CMSampleBufferGetPresentationTimeStamp(sample))
                if !input.append(sample) {
                    input.markAsFinished()
                    group.leave()
                    return
                }
            }
        }
    }

    init(
        inputURL: URL,
        plan: VideoPlanCompress,
        onProgress: @escaping (Float) -> Void,
        completion: @escaping (Result<URL, Error>) -> Void
    ) {
        self.inputURL = inputURL
        self.plan = plan
        self.onProgress = onProgress
        self.completion = completion
    }
}
