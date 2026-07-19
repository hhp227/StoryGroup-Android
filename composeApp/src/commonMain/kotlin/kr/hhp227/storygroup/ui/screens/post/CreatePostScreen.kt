package kr.hhp227.storygroup.ui.screens.post

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.SgTheme

@Composable
private fun createPostViewModel(groupId: Long?): CreatePostViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "create-post-$groupId") {
        CreatePostViewModel(
            groupId = groupId,
            createPostUseCase = container.createPostUseCase,
            createLoungePostUseCase = container.createLoungePostUseCase
        )
    }
}

/**
 * 게시글 작성 — 상단바(뒤로+등록)와 전면 본문 입력(웹 작성 폼 미러, 첨부는 후속).
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
    viewModel: CreatePostViewModel = createPostViewModel(groupId)
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    var text by rememberSaveable { mutableStateOf("") }

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
            title = "글쓰기",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            },
            actions = {
                TextButton(
                    onClick = { onAction(CreatePostViewModel.Action.Submit(text)) },
                    enabled = !uiState.isLoading
                ) {
                    Text(
                        "등록",
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
    }
}
