package kr.hhp227.storygroup.ui.screens.post

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.components.SgVideoThumbnail
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.PickerMode
import kr.hhp227.storygroup.ui.util.rememberImagePickerLauncher

@Composable
private fun createPostViewModel(groupId: Long?, postId: Long?): CreatePostViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "create-post-$groupId-$postId") {
        CreatePostViewModel(
            groupId = groupId,
            postId = postId,
            createPostUseCase = container.createPostUseCase,
            createLoungePostUseCase = container.createLoungePostUseCase,
            uploadImageUseCase = container.uploadImageUseCase,
            uploadVideoUseCase = container.uploadVideoUseCase,
            getPostUseCase = container.getPostUseCase,
            updatePostUseCase = container.updatePostUseCase
        )
    }
}

/**
 * 게시글 작성 — 상단바(뒤로+등록)와 전면 본문 입력(웹 작성 폼 미러) + 하단 사진·동영상 첨부 행.
 * groupId null이면 라운지(홈 피드)에 게시. NavHost 풀스크린 목적지라 상단바는 화면이 소유하고,
 * 성공 이벤트는 화면이 수집해 onCreated로 알린다(ConCafe CafeScreen 패턴).
 * iosApp CreatePostView.swift와 1:1 미러
 */
@Composable
fun CreatePostScreen(
    groupId: Long?,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    // 있으면 수정 모드 — 기존 본문·첨부를 불러와 채운다
    postId: Long? = null,
    viewModel: CreatePostViewModel = createPostViewModel(groupId, postId)
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    var text by rememberSaveable { mutableStateOf("") }

    // 수정 모드에서 기존 본문이 도착하면 입력창에 한 번 채운다(그 뒤 편집은 사용자 몫)
    LaunchedEffect(uiState.loadedText) {
        uiState.loadedText?.let { text = it }
    }

    val pickImage = rememberImagePickerLauncher { picked ->
        onAction(CreatePostViewModel.Action.AddImage(picked.bytes, picked.fileName, picked.contentType))
    }
    val pickVideo = rememberImagePickerLauncher(PickerMode.Video) { picked ->
        onAction(CreatePostViewModel.Action.AddVideo(picked.bytes, picked.fileName, picked.contentType))
    }

    // 일회성 이벤트 수집 — 성공 시 호출부(App.kt)가 피드 갱신+복귀를 처리한다
    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                CreatePostViewModel.Event.Created -> onCreated()
            }
        }
    }

    Column(modifier.fillMaxSize().background(sg.paper).imePadding()) {
        SgTopBar(
            title = if (uiState.isEditMode) "글 수정" else "글쓰기",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            },
            actions = {
                TextButton(
                    onClick = { onAction(CreatePostViewModel.Action.Submit(text)) },
                    // 업로드가 끝나기 전에 등록하면 그 첨부가 빠진 채 저장된다
                    enabled = !uiState.isLoading && !uiState.isUploadingImage && !uiState.isUploadingVideo
                ) {
                    Text(
                        if (uiState.isEditMode) "수정" else "등록",
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isLoading) sg.inkFaint else sg.accent
                    )
                }
            }
        )
        uiState.error?.let { error ->
            Spacer(Modifier.height(12.dp))
            Text(
                error,
                style = SgTheme.typography.bodySmall,
                color = sg.rust,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Box(Modifier.weight(1f)) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    if (uiState.error != null) onAction(CreatePostViewModel.Action.ClearError)
                },
                modifier = Modifier.fillMaxSize().padding(16.dp),
                placeholder = { Text("무슨 이야기가 있나요?", color = sg.inkFaint) },
                enabled = !uiState.isLoading,
                shape = SgTheme.shapes.field,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = sg.ink,
                    disabledTextColor = sg.inkFaint,
                    backgroundColor = sg.linen,
                    cursorColor = sg.accent,
                    focusedBorderColor = sg.accent,
                    unfocusedBorderColor = sg.stoneBorder,
                    disabledBorderColor = sg.stoneBorder
                )
            )
            if (uiState.isLoading) {
                CircularProgressIndicator(color = sg.accent, modifier = Modifier.align(Alignment.Center))
            }
        }
        AttachmentRow(
            urls = uiState.images,
            isUploading = uiState.isUploadingImage,
            canAddMore = uiState.images.size < CreatePostViewModel.MAX_IMAGES,
            addIcon = Icons.Default.AddAPhoto,
            addDescription = "사진 추가",
            onAddClick = pickImage,
            onRemove = { url -> onAction(CreatePostViewModel.Action.RemoveImage(url)) },
            thumbnail = { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(SgTheme.shapes.field)
                )
            }
        )
        AttachmentRow(
            urls = uiState.videos,
            isUploading = uiState.isUploadingVideo,
            canAddMore = uiState.videos.size < CreatePostViewModel.MAX_VIDEOS,
            addIcon = Icons.Default.VideoCall,
            addDescription = "동영상 추가",
            onAddClick = pickVideo,
            onRemove = { url -> onAction(CreatePostViewModel.Action.RemoveVideo(url)) },
            thumbnail = { url -> SgVideoThumbnail(url = url, size = ATTACHMENT_SIZE) }
        )
    }
}

/** 첨부 썸네일 한 칸의 크기 — 사진 행과 동영상 행이 같은 높이로 서도록 값을 공유한다 */
private val ATTACHMENT_SIZE = 72.dp

/**
 * 첨부 미리보기(가로 스크롤 썸네일+제거)+추가 버튼 — 웹 ImageUploadField 미러(다중 첨부용으로 확장).
 * 사진 행과 동영상 행이 칸 모양만 다르고 나머지가 같아 [thumbnail]만 갈아끼워 공유한다.
 */
@Composable
private fun AttachmentRow(
    urls: List<String>,
    isUploading: Boolean,
    canAddMore: Boolean,
    addIcon: ImageVector,
    addDescription: String,
    onAddClick: () -> Unit,
    onRemove: (String) -> Unit,
    thumbnail: @Composable (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    LazyRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(urls) { url ->
            Box(Modifier.size(ATTACHMENT_SIZE)) {
                thumbnail(url)
                IconButton(
                    onClick = { onRemove(url) },
                    modifier = Modifier.size(24.dp).align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "제거",
                        tint = sg.onAccent,
                        modifier = Modifier.background(sg.ink, CircleShape)
                    )
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .size(ATTACHMENT_SIZE)
                    .background(sg.linen, SgTheme.shapes.field),
                contentAlignment = Alignment.Center
            ) {
                if (isUploading) {
                    CircularProgressIndicator(color = sg.accent, modifier = Modifier.size(24.dp))
                } else {
                    IconButton(onClick = onAddClick, enabled = canAddMore) {
                        Icon(
                            addIcon,
                            contentDescription = addDescription,
                            tint = if (canAddMore) sg.inkSoft else sg.inkFaint
                        )
                    }
                }
            }
        }
    }
}
