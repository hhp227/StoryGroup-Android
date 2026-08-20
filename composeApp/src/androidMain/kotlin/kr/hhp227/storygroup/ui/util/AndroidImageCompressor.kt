package kr.hhp227.storygroup.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kr.hhp227.storygroup.shared.domain.media.CompressedImage
import kr.hhp227.storygroup.shared.domain.media.ImageCompressionPlanner
import kr.hhp227.storygroup.shared.domain.media.ImageCompressor
import kr.hhp227.storygroup.shared.domain.media.ImageFormat
import kr.hhp227.storygroup.shared.domain.media.ImagePlan
import java.io.File
import java.util.UUID

/**
 * 이미지 압축(§3·§8) — 동영상과 같은 WorkManager 경유. bytes↔경로 변환은 캐시 임시 파일로(Data 10KB 제한).
 * 실패하면 원본 반환(§3-3 원본 폴백) — 예외를 밖으로 내보내지 않는다.
 * 이미지 1장은 수백 ms라 진행률 UI 없이 종료까지 대기한다(기존 "업로드 중" 상태로 충분).
 */
class AndroidImageCompressor(private val context: Context) : ImageCompressor {

    override suspend fun compress(bytes: ByteArray, contentType: String): CompressedImage {
        val original = CompressedImage(bytes, contentType)
        return runCatching {
            val input = File(context.cacheDir, "img-${UUID.randomUUID()}").apply { writeBytes(bytes) }
            val output = File(context.cacheDir, "img-out-${UUID.randomUUID()}")
            try {
                val request = OneTimeWorkRequestBuilder<ImageCompressionWorker>()
                    .setInputData(
                        workDataOf(
                            ImageCompressionWorker.KEY_INPUT to input.absolutePath,
                            ImageCompressionWorker.KEY_OUTPUT to output.absolutePath,
                            ImageCompressionWorker.KEY_CONTENT_TYPE to contentType
                        )
                    )
                    .build()
                val workManager = WorkManager.getInstance(context)
                workManager.enqueue(request)
                val info = workManager.getWorkInfoByIdFlow(request.id).first { it?.state?.isFinished == true }
                if (info != null && info.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                    val outType = info.outputData.getString(ImageCompressionWorker.KEY_CONTENT_TYPE) ?: contentType
                    CompressedImage(output.readBytes(), outType)
                } else {
                    original // 판정 Skip·디코딩 실패 모두 워커가 failure로 보고 → 원본 사용
                }
            } finally {
                input.delete()
                output.delete()
            }
        }.getOrDefault(original)
    }
}

/** 이미지 재인코딩 워커 — 판정(ImageCompressionPlanner)→다운샘플→스케일→압축. Skip·실패는 Result.failure(호출부가 원본 사용) */
class ImageCompressionWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val inputPath = inputData.getString(KEY_INPUT) ?: return@withContext Result.failure()
        val outputPath = inputData.getString(KEY_OUTPUT) ?: return@withContext Result.failure()
        val contentType = inputData.getString(KEY_CONTENT_TYPE) ?: "image/jpeg"
        runCatching {
            val inputFile = File(inputPath)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(inputPath, bounds)
            val plan = ImageCompressionPlanner.plan(contentType, inputFile.length(), bounds.outWidth, bounds.outHeight)
            val recompress = plan as? ImagePlan.Recompress ?: return@runCatching Result.failure()
            // inSampleSize 다운샘플(2의 거듭제곱) 후 정확한 목표 치수로 스케일 — 대형 원본 메모리 방지
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= recompress.targetWidth &&
                bounds.outHeight / (sample * 2) >= recompress.targetHeight
            ) sample *= 2
            val decoded = BitmapFactory.decodeFile(inputPath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@runCatching Result.failure()
            val scaled = Bitmap.createScaledBitmap(decoded, recompress.targetWidth, recompress.targetHeight, true)
            val isPng = recompress.format == ImageFormat.PNG
            File(outputPath).outputStream().use {
                scaled.compress(
                    if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    ImageCompressionPlanner.JPEG_QUALITY,
                    it
                )
            }
            Result.success(workDataOf(KEY_CONTENT_TYPE to if (isPng) "image/png" else "image/jpeg"))
        }.getOrDefault(Result.failure())
    }

    companion object {
        const val KEY_INPUT = "input"
        const val KEY_OUTPUT = "output"
        const val KEY_CONTENT_TYPE = "contentType"
    }
}
