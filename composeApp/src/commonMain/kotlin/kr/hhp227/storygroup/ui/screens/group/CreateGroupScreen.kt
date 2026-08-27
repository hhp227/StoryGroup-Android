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
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.di.screenViewModel
import kr.hhp227.storygroup.di.sessionViewModel
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.rememberImagePickerLauncher
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.common_back
import storygroup.composeapp.generated.resources.common_create
import storygroup.composeapp.generated.resources.create_group_add_cover
import storygroup.composeapp.generated.resources.create_group_desc
import storygroup.composeapp.generated.resources.create_group_name
import storygroup.composeapp.generated.resources.groups_create
import storygroup.composeapp.generated.resources.join_type
import storygroup.composeapp.generated.resources.join_type_approval_full
import storygroup.composeapp.generated.resources.join_type_auto_full

/**
 * 그룹 만들기 — 이름/소개/커버 이미지+가입 방식(자동 승인/승인제). NavHost 풀스크린 목적지.
 * 성공 시 세션 GroupsViewModel(그룹 탭과 동일 인스턴스 — AccountSettingsScreen이 ProfileViewModel을
 * 갱신하는 것과 동일 기법)을 refresh()시켜 새 그룹이 목록에 바로 반영되게 한다.
 * iosApp CreateGroupView.swift와 1:1 미러
 */
@Composable
fun CreateGroupScreen(
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    viewModel: CreateGroupViewModel = screenViewModel(key = "create-group") {
        CreateGroupViewModel(
            createGroupUseCase = it.createGroupUseCase,
            uploadImageUseCase = it.uploadImageUseCase
        )
    },
    groupsViewModel: GroupsViewModel = sessionViewModel {
        GroupsViewModel(it.getMyGroupsPagingDataUseCase)
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
                    onNavigationAction(NavigationAction.NavigateBack)
                }
            }
        }
    }
    Column(modifier.fillMaxSize().background(sg.paper).imePadding()) {
        SgTopBar(
            title = stringResource(Res.string.groups_create),
            navigationIcon = {
                IconButton(onClick = { onNavigationAction(NavigationAction.NavigateBack) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back))
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
                        label = stringResource(Res.string.create_group_name),
                        enabled = !uiState.isSaving
                    )
                    SgTextField(
                        value = description,
                        onValueChange = { if (it.length <= 1000) description = it },
                        label = stringResource(Res.string.create_group_desc),
                        singleLine = false,
                        minLines = 4,
                        enabled = !uiState.isSaving
                    )
                    Column {
                        Text(
                            stringResource(Res.string.join_type),
                            style = SgTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = sg.inkSoft
                        )
                        JoinTypeRadioRow(stringResource(Res.string.join_type_auto_full), joinType == GroupJoinType.AUTO_APPROVE) {
                            joinType = GroupJoinType.AUTO_APPROVE
                        }
                        JoinTypeRadioRow(stringResource(Res.string.join_type_approval_full), joinType == GroupJoinType.APPROVAL_REQUIRED) {
                            joinType = GroupJoinType.APPROVAL_REQUIRED
                        }
                    }
                    uiState.error?.let {
                        Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                    }
                    SgPrimaryButton(
                        text = stringResource(Res.string.common_create),
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
                Icon(Icons.Default.AddAPhoto, contentDescription = stringResource(Res.string.create_group_add_cover), tint = sg.inkSoft)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(Res.string.create_group_add_cover), style = SgTheme.typography.bodySmall, color = sg.inkSoft)
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
