package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.media.VideoPlan
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import ws.schild.jave.encode.VideoAttributes
import ws.schild.jave.info.MultimediaInfo
import ws.schild.jave.info.VideoSize
import ws.schild.jave.progress.EncoderProgressListener
import java.io.File
import java.util.UUID

/**
 * 번들 ffmpeg(jave2) 실행기(§7) — IO 코루틴에서 인코딩, 진행률은 EncoderProgressListener(permil).
 * 수집 취소(화면 이탈) 시 abortEncoding으로 인코더를 멈춘다.
 */
private class JvmVideoCompressor : VideoCompressor {

    override fun compress(inputPath: String, plan: VideoPlan.Compress): Flow<CompressionState> = callbackFlow {
        val output = File(System.getProperty("java.io.tmpdir"), "compressed-${UUID.randomUUID()}.mp4")
        val encoder = Encoder()
        val job = launch(Dispatchers.IO) {
            val attributes = EncodingAttributes().apply {
                setOutputFormat("mp4")
                setVideoAttributes(VideoAttributes().apply {
                    setCodec("libx264")
                    setBitRate(plan.videoBitrate)
                    setSize(VideoSize(plan.targetWidth, plan.targetHeight))
                    setFrameRate(plan.fps)
                    setFaststart(true)
                })
                setAudioAttributes(AudioAttributes().apply {
                    setCodec("aac")
                    setBitRate(plan.audioBitrate)
                    setChannels(2)
                    setSamplingRate(44100)
                })
            }
            runCatching {
                encoder.encode(MultimediaObject(File(inputPath)), output, attributes, object : EncoderProgressListener {
                    override fun sourceInfo(info: MultimediaInfo?) {}

                    override fun progress(permil: Int) {
                        trySend(CompressionState.Progress(permil / 1000f))
                    }

                    override fun message(message: String?) {}
                })
            }
                .onSuccess { trySend(CompressionState.Done(output.absolutePath)) }
                .onFailure {
                    output.delete()
                    trySend(CompressionState.Failed("동영상 압축에 실패했습니다."))
                }
            close()
        }
        awaitClose {
            if (job.isActive) {
                job.cancel()
                runCatching { encoder.abortEncoding() }
                output.delete()
            }
        }
    }
}

@Composable
actual fun rememberVideoCompressor(): VideoCompressor = remember { JvmVideoCompressor() }

actual fun readFileBytes(path: String): ByteArray = File(path).readBytes()

actual fun deleteFile(path: String) {
    runCatching { File(path).delete() }
}
