package kr.hhp227.storygroup.ui.util

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * 동영상 압축 워커(§6) — Transformer는 Looper 스레드를 요구해 시작·진행률 폴링·취소를 Main에서 한다.
 * 입력·출력은 경로로 받는다(WorkManager Data 10KB 제한). 단일 인코딩 시도만 한다 —
 * 5MB 초과 재시도는 VM이 RETRY_MARGIN으로 재플랜해 다시 enqueue.
 * fps 상한은 Transformer 1.4.1에 간단한 API가 없어 원본 유지 — 크기는 비트레이트가 결정한다.
 */
class MediaCompressionWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val input = inputData.getString(KEY_INPUT) ?: return Result.failure()
        val output = inputData.getString(KEY_OUTPUT) ?: return Result.failure()
        val videoBitrate = inputData.getInt(KEY_VIDEO_BITRATE, 0)
        val width = inputData.getInt(KEY_WIDTH, 0)
        val height = inputData.getInt(KEY_HEIGHT, 0)
        if (videoBitrate <= 0 || width <= 0 || height <= 0) return Result.failure()

        val done = CompletableDeferred<Result>()
        val transformer = withContext(Dispatchers.Main) {
            val encoderFactory = DefaultEncoderFactory.Builder(applicationContext)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(videoBitrate).build())
                .build()
            val transformer = Transformer.Builder(applicationContext)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        done.complete(Result.success(workDataOf(KEY_OUTPUT to output)))
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        File(output).delete()
                        done.complete(Result.failure())
                    }
                })
                .build()
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(File(input))))
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(Presentation.createForWidthAndHeight(width, height, Presentation.LAYOUT_SCALE_TO_FIT))
                    )
                )
                .build()
            transformer.start(edited, output)
            transformer
        }
        try {
            val holder = ProgressHolder()
            while (!done.isCompleted && coroutineContext.isActive) {
                withContext(Dispatchers.Main) { transformer.getProgress(holder) }
                setProgress(workDataOf(KEY_PROGRESS to holder.progress / 100f))
                delay(500)
            }
            return done.await()
        } finally {
            // 취소(화면 이탈)든 실패든 인코더를 정리한다 — cancel()은 시작 스레드(Main)에서만 허용
            if (!done.isCompleted) {
                withContext(NonCancellable + Dispatchers.Main) { transformer.cancel() }
                File(output).delete()
            }
        }
    }

    companion object {
        const val KEY_INPUT = "input"
        const val KEY_OUTPUT = "output"
        const val KEY_VIDEO_BITRATE = "videoBitrate"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_PROGRESS = "progress"
    }
}
