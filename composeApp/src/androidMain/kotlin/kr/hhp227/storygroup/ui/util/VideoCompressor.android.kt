package kr.hhp227.storygroup.ui.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.media.VideoPlan
import java.io.File
import java.util.UUID

/** WorkManager 실행기(§6) — enqueue 후 WorkInfo Flow를 CompressionState로 중계, 수집 취소 시 작업도 취소 */
class AndroidVideoCompressor(private val context: Context) : VideoCompressor {

    override fun compress(inputPath: String, plan: VideoPlan.Compress): Flow<CompressionState> = callbackFlow {
        val outputPath = File(context.cacheDir, "compressed-${UUID.randomUUID()}.mp4").absolutePath
        val request = OneTimeWorkRequestBuilder<MediaCompressionWorker>()
            .setInputData(
                workDataOf(
                    MediaCompressionWorker.KEY_INPUT to inputPath,
                    MediaCompressionWorker.KEY_OUTPUT to outputPath,
                    MediaCompressionWorker.KEY_VIDEO_BITRATE to plan.videoBitrate,
                    MediaCompressionWorker.KEY_WIDTH to plan.targetWidth,
                    MediaCompressionWorker.KEY_HEIGHT to plan.targetHeight
                )
            )
            .build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(request)
        val job = launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                when (info?.state) {
                    WorkInfo.State.RUNNING ->
                        trySend(CompressionState.Progress(info.progress.getFloat(MediaCompressionWorker.KEY_PROGRESS, 0f)))
                    WorkInfo.State.SUCCEEDED -> {
                        trySend(CompressionState.Done(info.outputData.getString(MediaCompressionWorker.KEY_OUTPUT) ?: outputPath))
                        close()
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        trySend(CompressionState.Failed("동영상 압축에 실패했습니다."))
                        close()
                    }
                    else -> Unit
                }
            }
        }
        awaitClose {
            job.cancel()
            // 정상 종료 후엔 no-op, 수집 취소(화면 이탈)면 실행 중인 압축을 멈춘다
            workManager.cancelWorkById(request.id)
        }
    }
}

@Composable
actual fun rememberVideoCompressor(): VideoCompressor {
    val context = LocalContext.current.applicationContext
    return remember { AndroidVideoCompressor(context) }
}

actual fun readFileBytes(path: String): ByteArray = File(path).readBytes()

actual fun deleteFile(path: String) {
    runCatching { File(path).delete() }
}
