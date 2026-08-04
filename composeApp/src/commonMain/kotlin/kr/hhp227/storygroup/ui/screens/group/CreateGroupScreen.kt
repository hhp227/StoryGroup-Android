package kr.hhp227.storygroup.ui.screens.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.rememberImagePickerLauncher

@Composable
private fun createGroupViewModel(): CreateGroupViewModel {
    val container = LocalAppContainer.current

    return viewModel(key = "create-group") {
        CreateGroupViewModel(
            createGroupUseCase = container.createGroupUseCase,
            uploadImageUseCase = container.uploadImageUseCase
        )
    }
}

/**
 * 그룹 만들기 — 이름/소개/커버 이미지+가입 방식(자동 승인/승인제). NavHost 풀스크린 목적지.
 * 성공 시 세션 GroupsViewModel(그룹 탭과 동일 인스턴스 — AccountSettingsScreen이 ProfileViewModel을
 * 갱신하는 것과 동일 기법)을 refresh()시켜 새 그룹이 목록에 바로 반영되게 한다.
 * iosApp CreateGroupView.swift와 1:1 미러
 */
@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateGroupViewModel = createGroupViewModel(),
    groupsViewModel: GroupsViewModel = sessionViewModel {
        GroupsViewModel(it.getMyGroupsPagingDataUseCase, it.getMyJoinRequestedGroupsUseCase, it.cancelJoinRequestUseCase)
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var joinType by rememberSaveable { mutableStateOf(GroupJoinType.AUTO_APPROVE) }

    val pickCoverImage = rememberImagePickerLauncher { picked ->
        onAction(CreateGroupViewModel.Action.ChangeCoverImage(picked.bytes, picked.fileName, picked.contentType))
    }

    // 일회성 이벤트 수집 — 성공 시 그룹 탭을 갱신하고 복귀한다
    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is CreateGroupViewModel.Event.Created -> {
                    groupsViewModel.onAction(GroupsViewModel.Action.Refresh)
                    onBack()
                }
            }
        }
    }
    Column(modifier.fillMaxSize().background(sg.paper).imePadding()) {
        SgTopBar(
            title = "그룹 만들기",
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                }
            }
        )
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SgCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoverImagePicker(
                        image = uiState.image,
                        isUploading = uiState.isUploadingImage,
                        onClick = pickCoverImage
                    )
                    SgTextField(
                        value = name,
                        onValueChange = { if (it.length <= 100) name = it },
                        label = "그룹 이름",
                        enabled = !uiState.isSaving
                    )
                    SgTextField(
                        value = description,
                        onValueChange = { if (it.length <= 1000) description = it },
                        label = "그룹 소개",
                        singleLine = false,
                        minLines = 4,
                        enabled = !uiState.isSaving
                    )
                    Column {
                        Text(
                            "가입 방식",
                            style = SgTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = sg.inkSoft
                        )
                        JoinTypeRadioRow("자동 승인 — 바로 가입", joinType == GroupJoinType.AUTO_APPROVE) {
                            joinType = GroupJoinType.AUTO_APPROVE
                        }
                        JoinTypeRadioRow("승인제 — 신청 후 승인", joinType == GroupJoinType.APPROVAL_REQUIRED) {
                            joinType = GroupJoinType.APPROVAL_REQUIRED
                        }
                    }
                    uiState.error?.let {
                        Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                    }
                    SgPrimaryButton(
                        text = "만들기",
                        onClick = { onAction(CreateGroupViewModel.Action.Submit(name, description, joinType)) },
                        isLoading = uiState.isSaving
                    )
                }
            }
        }
    }
}

/** 커버 썸네일(탭하면 변경) — 없으면 플레이스홀더, CreatePostScreen 첨부행의 1장 버전 */
@Composable
private fun CoverImagePicker(image: String?, isUploading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sg = SgTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(sg.linen, SgTheme.shapes.field)
            .clickable(enabled = !isUploading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(SgTheme.shapes.field)
            )
        }
        if (isUploading) {
            Box(Modifier.matchParentSize().background(sg.ink.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = sg.onAccent, modifier = Modifier.size(24.dp))
            }
        } else if (image == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AddAPhoto, contentDescription = "커버 이미지 추가", tint = sg.inkSoft)
                Spacer(Modifier.height(4.dp))
                Text("커버 이미지 추가", style = SgTheme.typography.bodySmall, color = sg.inkSoft)
            }
        }
    }
}

@Composable
private fun JoinTypeRadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = SgTheme.colors.accent)
        )
        Text(label, style = SgTheme.typography.bodyMedium, color = SgTheme.colors.ink)
    }
}
