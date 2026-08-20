package kr.hhp227.storygroup.ui.util

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kr.hhp227.storygroup.shared.domain.media.VideoPlan

/** 압축 진행 상태 — 수집 코루틴이 취소되면 실행 중인 작업도 함께 취소된다(화면 이탈 시 정리) */
sealed interface CompressionState {
    data class Progress(val fraction: Float) : CompressionState
    data class Done(val outputPath: String) : CompressionState
    data class Failed(val message: String) : CompressionState
}

/**
 * 동영상 압축 실행기 — 단일 시도만 담당(5MB 초과 재시도는 호출부가 RETRY_MARGIN으로 재플랜).
 * Android=WorkManager+media3 Transformer, Desktop=jave2(번들 ffmpeg). iOS는 Swift 쌍둥이
 * MediaCompressionQueue가 같은 역할(ImagePicker 미러 관례).
 */
interface VideoCompressor {
    fun compress(inputPath: String, plan: VideoPlan.Compress): Flow<CompressionState>
}

@Composable
expect fun rememberVideoCompressor(): VideoCompressor

/** 압축 결과 파일을 업로드용으로 읽는다(≤5MB라 메모리 부담 없음) — commonMain엔 파일 API가 없어 expect */
expect fun readFileBytes(path: String): ByteArray

/** 압축 입력/출력 임시 파일 정리 — 실패해도 조용히 무시한다 */
expect fun deleteFile(path: String)
