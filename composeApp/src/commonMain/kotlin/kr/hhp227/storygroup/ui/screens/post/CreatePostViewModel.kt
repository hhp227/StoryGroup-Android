package kr.hhp227.storygroup.ui.screens.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.media.VideoCompressionPlanner
import kr.hhp227.storygroup.shared.domain.media.VideoPlan
import kr.hhp227.storygroup.shared.domain.usecase.CreateLoungePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.CreatePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetPostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UpdatePostUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadImageUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadVideoUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel
import kr.hhp227.storygroup.ui.util.CompressionState
import kr.hhp227.storygroup.ui.util.PickedImage
import kr.hhp227.storygroup.ui.util.VideoCompressor
import kr.hhp227.storygroup.ui.util.deleteFile
import kr.hhp227.storygroup.ui.util.readFileBytes

/**
 * 게시글 작성 — groupId가 null이면 라운지(홈 피드)에 게시한다(웹 메인 피드 폼 미러).
 * 이미지·동영상은 선택 즉시 업로드해 첨부한 순서대로 attachments에 쌓아두고, 등록 시
 * images/videos로 갈라 전송한다(레거시 WriteListAdapter itemList 미러 — 화면이 이 순서로 리스트에 그린다).
 * 성공은 Event.Created 일회성 발화 — 호출부(App.kt)가 복귀+피드 갱신을 처리한다.
 * iosApp CreatePostViewModel.swift와 1:1 미러
 */
class CreatePostViewModel(
    private val groupId: Long?,
    // 있으면 수정 모드 — 같은 폼을 재사용한다(작성용 폼이 두 벌이 되지 않게)
    private val postId: Long? = null,
    private val createPostUseCase: CreatePostUseCase,
    private val createLoungePostUseCase: CreateLoungePostUseCase,
    private val uploadImageUseCase: UploadImageUseCase,
    private val uploadVideoUseCase: UploadVideoUseCase,
    private val getPostUseCase: GetPostUseCase,
    private val updatePostUseCase: UpdatePostUseCase,
    // 동영상 압축 실행기(§5) — 화면이 rememberVideoCompressor()로 받아 넘긴다
    private val videoCompressor: VideoCompressor
) : ViewModel(), MviViewModel<CreatePostViewModel.UiState, CreatePostViewModel.Action, CreatePostViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState(isEditMode = postId != null))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    init {
        // 수정 모드면 기존 본문·첨부를 읽어와 폼을 채운다(라우트로 실어 나르기엔 본문이 길다)
        if (groupId != null && postId != null) {
            _uiState.update { it.copy(isLoading = true) }
            viewModelScope.launch {
                runCatching { getPostUseCase(groupId, postId) }
                    .onSuccess { post ->
                        // ⚠️videos도 반드시 채운다 — 저장이 전체 교체라 비워둔 채 보내면
                        // 웹에서 올린 동영상이 수정 한 번에 전부 삭제된다(images와 같은 이유).
                        // 서버엔 타입 간 순서 정보가 없어 이미지들 뒤에 동영상들을 잇는다(상세 표시 순서와 동일)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                attachments = post.imageUrls.map { url -> Attachment(url, isVideo = false) } +
                                    post.videoUrls.map { url -> Attachment(url, isVideo = true) },
                                loadedText = post.text
                            )
                        }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isLoading = false, error = e.message ?: "게시글을 불러오지 못했습니다.") }
                    }
            }
        }
    }

    override fun onAction(action: Action) {
        when (action) {
            is Action.Submit -> submit(action.text)
            Action.ClearError -> _uiState.update { it.copy(error = null) }
            is Action.AddImage -> addImage(action.bytes, action.fileName, action.contentType)
            is Action.AddVideo -> addVideo(action.picked)
            is Action.RemoveAttachment ->
                _uiState.update { it.copy(attachments = it.attachments.filterNot { a -> a.url == action.url }) }
        }
    }

    private fun addImage(bytes: ByteArray, fileName: String, contentType: String) {
        if (_uiState.value.images.size >= MAX_IMAGES) return

        _uiState.update { it.copy(isUploadingImage = true, error = null) }
        viewModelScope.launch {
            runCatching { uploadImageUseCase(bytes, fileName, contentType) }
                .onSuccess { url ->
                    _uiState.update {
                        it.copy(isUploadingImage = false, attachments = it.attachments + Attachment(url, isVideo = false))
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isUploadingImage = false, error = e.message ?: "이미지 업로드에 실패했습니다.") }
                }
        }
    }

    private fun addVideo(picked: PickedImage) {
        if (_uiState.value.videos.size >= MAX_VIDEOS) return
        val filePath = picked.filePath
        if (filePath == null || picked.durationMs == null) {
            _uiState.update { it.copy(error = "동영상 정보를 읽지 못했습니다.") }
            return
        }
        // 올려놓고 서버 거절을 기다리게 하지 않는다 — 판정(§2)은 선택 즉시, 압축은 백그라운드
        when (val plan = VideoCompressionPlanner.plan(picked.durationMs, picked.sizeBytes, picked.width, picked.height)) {
            VideoPlan.RejectTooLarge -> _uiState.update { it.copy(error = "파일이 너무 큽니다. (최대 500MB)") }
            VideoPlan.RejectTooLong -> _uiState.update { it.copy(error = "동영상은 최대 3분까지 첨부할 수 있습니다.") }
            // 원본이 이미 5MB 이하 — 재인코딩은 시간 낭비+화질 손실(§2-3)
            VideoPlan.SkipAlreadySmall -> uploadVideoBytes(readFileBytes(filePath), picked.fileName, picked.contentType)
            is VideoPlan.Compress -> compressAndUpload(picked, plan, isRetry = false)
        }
    }

    /** 압축 → 5MB 초과면 RETRY_MARGIN으로 1회 재플랜 → 업로드. 화면 소멸 시 viewModelScope가 압축을 취소한다 */
    private fun compressAndUpload(picked: PickedImage, plan: VideoPlan.Compress, isRetry: Boolean) {
        val filePath = picked.filePath ?: return
        _uiState.update { it.copy(compressionProgress = 0f, error = null) }
        viewModelScope.launch {
            videoCompressor.compress(filePath, plan).collect { state ->
                when (state) {
                    is CompressionState.Progress ->
                        _uiState.update { it.copy(compressionProgress = state.fraction) }
                    is CompressionState.Failed -> {
                        deleteFile(filePath)
                        _uiState.update { it.copy(compressionProgress = null, error = state.message) }
                    }
                    is CompressionState.Done -> {
                        val bytes = readFileBytes(state.outputPath)
                        deleteFile(state.outputPath)
                        when {
                            bytes.size <= VideoCompressionPlanner.TARGET_BYTES -> {
                                deleteFile(filePath)
                                _uiState.update { it.copy(compressionProgress = null) }
                                // 출력은 항상 MP4(§2) — 원본 확장자와 무관
                                uploadVideoBytes(bytes, "upload.mp4", "video/mp4")
                            }
                            !isRetry -> {
                                // 단일 패스 ABR 오버슈트 — 더 보수적인 마진으로 딱 한 번 재시도(§2-5)
                                val retryPlan = VideoCompressionPlanner.plan(
                                    picked.durationMs ?: 0L, picked.sizeBytes, picked.width, picked.height,
                                    margin = VideoCompressionPlanner.RETRY_MARGIN
                                )
                                if (retryPlan is VideoPlan.Compress) {
                                    compressAndUpload(picked, retryPlan, isRetry = true)
                                } else {
                                    deleteFile(filePath)
                                    _uiState.update { it.copy(compressionProgress = null, error = "동영상 압축에 실패했습니다.") }
                                }
                            }
                            else -> {
                                deleteFile(filePath)
                                _uiState.update { it.copy(compressionProgress = null, error = "동영상 압축에 실패했습니다.") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun uploadVideoBytes(bytes: ByteArray, fileName: String, contentType: String) {
        _uiState.update { it.copy(isUploadingVideo = true, error = null) }
        viewModelScope.launch {
            runCatching { uploadVideoUseCase(bytes, fileName, contentType) }
                .onSuccess { url ->
                    _uiState.update {
                        it.copy(isUploadingVideo = false, attachments = it.attachments + Attachment(url, isVideo = true))
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isUploadingVideo = false, error = e.message ?: "동영상 업로드에 실패했습니다.") }
                }
        }
    }

    private fun submit(text: String) {
        if (_uiState.value.isLoading) return

        val images = _uiState.value.images
        val videos = _uiState.value.videos
        // 본문/첨부 중 하나는 필수 — 백엔드 규칙과 일치(웹 폼의 required={images.length===0} 미러)
        if (text.isBlank() && images.isEmpty() && videos.isEmpty()) {
            _uiState.update { it.copy(error = "내용을 입력하거나 사진·동영상을 추가해주세요.") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                when {
                    // 수정은 라운지 글도 그 글의 groupId로 들어오므로 groupId가 항상 있다
                    groupId != null && postId != null -> updatePostUseCase(groupId, postId, text, images, videos)
                    groupId != null -> createPostUseCase(groupId, text, images, videos)
                    else -> createLoungePostUseCase(text, images, videos)
                }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                _event.tryEmit(Event.Created)
            }.onFailure { e ->
                val fallback = if (postId != null) "게시글 수정에 실패했습니다." else "게시글 작성에 실패했습니다."

                _uiState.update { it.copy(isLoading = false, error = e.message ?: fallback) }
            }
        }
    }

    /** 첨부 한 건 — 화면이 첨부한 순서 그대로 리스트에 그린다(이미지/동영상 구분은 렌더링용) */
    data class Attachment(val url: String, val isVideo: Boolean)

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        /** 첨부 목록 — 업로드 성공 순서대로 append(레거시 itemList 미러) */
        val attachments: List<Attachment> = emptyList(),
        val isUploadingImage: Boolean = false,
        val isUploadingVideo: Boolean = false,
        /** 동영상 압축 진행률(0..1) — null이면 압축 중 아님. 업로드 단계는 isUploadingVideo가 따로 표시 */
        val compressionProgress: Float? = null,
        val isEditMode: Boolean = false,
        /** 수정 모드에서 읽어온 기존 본문 — 화면이 한 번 받아 입력창에 채운다(null이면 아직 로드 전) */
        val loadedText: String? = null
    ) {
        /** 서버 계약(images/videos 분리 전송)과 타입별 상한 판정용 파생 목록 */
        val images: List<String> get() = attachments.filterNot { it.isVideo }.map { it.url }
        val videos: List<String> get() = attachments.filter { it.isVideo }.map { it.url }
    }

    sealed interface Action {
        data class Submit(val text: String) : Action
        data object ClearError : Action
        class AddImage(val bytes: ByteArray, val fileName: String, val contentType: String) : Action
        class AddVideo(val picked: PickedImage) : Action
        data class RemoveAttachment(val url: String) : Action
    }

    sealed interface Event {
        data object Created : Event
    }

    companion object {
        // 서버는 개수 제한이 없지만 앱은 카드 레이아웃 감안해 클라 상한을 둔다(화면의 추가 버튼 비활성 조건과 공유)
        const val MAX_IMAGES = 4
        const val MAX_VIDEOS = 2
    }
}
