package kr.hhp227.storygroup.ui.screens.post

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.components.SgVideoPoster
import kr.hhp227.storygroup.ui.navigation.NavResult
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.PickerMode
import kr.hhp227.storygroup.ui.util.rememberImagePickerLauncher
import kr.hhp227.storygroup.ui.util.rememberVideoCompressor

@Composable
private fun createPostViewModel(groupId: Long?, postId: Long?): CreatePostViewModel {
    val container = LocalAppContainer.current
    // viewModel{} 블록은 @Composable이 아니라 압축 실행기를 먼저 받아 클로저로 넘긴다
    val videoCompressor = rememberVideoCompressor()

    return viewModel(key = "create-post-$groupId-$postId") {
        CreatePostViewModel(
            groupId = groupId,
            postId = postId,
            createPostUseCase = container.createPostUseCase,
            createLoungePostUseCase = container.createLoungePostUseCase,
            uploadImageUseCase = container.uploadImageUseCase,
            uploadVideoUseCase = container.uploadVideoUseCase,
            getPostUseCase = container.getPostUseCase,
            updatePostUseCase = container.updatePostUseCase,
            videoCompressor = videoCompressor
        )
    }
}

/**
 * 게시글 작성 — 상단바(뒤로+등록)와 전면 본문 입력(웹 작성 폼 미러) + 하단 사진·동영상 첨부 행.
 * groupId null이면 라운지(홈 피드)에 게시. NavHost 풀스크린 목적지라 상단바는 화면이 소유하고,
 * 성공 이벤트는 화면이 수집해 결과를 publish하고 스스로 복귀한다(ConCafe CafeScreen 패턴).
 * iosApp CreatePostView.swift와 1:1 미러
 */
@Composable
fun CreatePostScreen(
    groupId: Long?,
    modifier: Modifier = Modifier,
    // 있으면 수정 모드 — 기존 본문·첨부를 불러와 채운다
    postId: Long? = null,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
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
        onAction(CreatePostViewModel.Action.AddVideo(picked))
    }

    // 일회성 이벤트 수집 — 성공 시 결과를 publish하고 스스로 복귀한다(수정이면 상세가, 신규면 피드가 읽어간다)
    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                CreatePostViewModel.Event.Created -> {
                    val result = if (postId != null && groupId != null) {
                        NavResult.PostUpdated(groupId, postId)
                    } else {
                        NavResult.PostCreated(groupId)
                    }

                    onNavigationAction(NavigationAction.PublishResult(result))
                    onNavigationAction(NavigationAction.NavigateBack)
                }
            }
        }
    }

    Column(modifier.fillMaxSize().background(sg.paper).imePadding()) {
        SgTopBar(
            title = if (uiState.isEditMode) "글 수정" else "글쓰기",
            navigationIcon = {
                IconButton(onClick = { onNavigationAction(NavigationAction.NavigateBack) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            },
            actions = {
                TextButton(
                    onClick = { onAction(CreatePostViewModel.Action.Submit(text)) },
                    // 업로드·압축이 끝나기 전에 등록하면 그 첨부가 빠진 채 저장된다
                    enabled = !uiState.isLoading && !uiState.isUploadingImage && !uiState.isUploadingVideo &&
                        uiState.compressionProgress == null
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
        // 레거시 fragment_create_post 미러 — 리스트[본문 입력 + 첨부가 순서대로 append] + 1px 구분선 + 첨부 버튼 바
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "text") {
                    // 레거시 input_text 미러 — 배경·테두리 없는 본문 입력(카드 아님)
                    TextField(
                        value = text,
                        onValueChange = {
                            text = it
                            if (uiState.error != null) onAction(CreatePostViewModel.Action.ClearError)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                        placeholder = { Text("무슨 이야기가 있나요?", color = sg.inkFaint) },
                        enabled = !uiState.isLoading,
                        colors = TextFieldDefaults.textFieldColors(
                            textColor = sg.ink,
                            disabledTextColor = sg.inkFaint,
                            backgroundColor = Color.Transparent,
                            cursorColor = sg.accent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        )
                    )
                }
                items(uiState.attachments, key = { it.url }) { attachment ->
                    AttachmentItem(
                        attachment = attachment,
                        onRemove = { onAction(CreatePostViewModel.Action.RemoveAttachment(attachment.url)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
            if (uiState.isLoading) {
                CircularProgressIndicator(color = sg.accent, modifier = Modifier.align(Alignment.Center))
            }
        }
        Divider(color = sg.stoneBorder, thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AttachBarButton(
                icon = Icons.Default.AddAPhoto,
                description = "사진 추가",
                isUploading = uiState.isUploadingImage,
                canAddMore = uiState.images.size < CreatePostViewModel.MAX_IMAGES,
                onClick = pickImage
            )
            AttachBarButton(
                icon = Icons.Default.VideoCall,
                description = "동영상 추가",
                isUploading = uiState.isUploadingVideo || uiState.compressionProgress != null,
                canAddMore = uiState.videos.size < CreatePostViewModel.MAX_VIDEOS,
                onClick = pickVideo
            )
            uiState.compressionProgress?.let { progress ->
                Text(
                    "압축 중 ${(progress * 100).toInt()}%",
                    style = SgTheme.typography.bodySmall,
                    color = sg.inkFaint
                )
            }
        }
    }
}

/** 첨부 한 아이템 — 리스트 폭을 꽉 채우는 실비율 미리보기(레거시 input_contents 미러) + 우상단 제거 버튼 */
@Composable
private fun AttachmentItem(
    attachment: CreatePostViewModel.Attachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors

    Box(modifier.fillMaxWidth()) {
        if (attachment.isVideo) {
            SgVideoPoster(url = attachment.url, modifier = Modifier.fillMaxWidth())
        } else {
            AsyncImage(
                model = attachment.url,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.padding(4.dp).size(24.dp).align(Alignment.TopEnd)
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

/** 하단 첨부 버튼 한 칸 — 업로드 중엔 그 자리에 스피너, 타입별 상한에 닿으면 비활성(레거시 ib_image/ib_video 미러) */
@Composable
private fun AttachBarButton(
    icon: ImageVector,
    description: String,
    isUploading: Boolean,
    canAddMore: Boolean,
    onClick: () -> Unit
) {
    val sg = SgTheme.colors

    if (isUploading) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = sg.accent, modifier = Modifier.size(24.dp))
        }
    } else {
        IconButton(onClick = onClick, enabled = canAddMore) {
            Icon(icon, contentDescription = description, tint = if (canAddMore) sg.inkSoft else sg.inkFaint)
        }
    }
}
