package kr.hhp227.storygroup.ui.screens.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.di.screenViewModel
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.navigation.NavResult
import kr.hhp227.storygroup.ui.navigation.NavigationAction
import kr.hhp227.storygroup.ui.navigation.sessionNavigationViewModel
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.rememberImagePickerLauncher
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.common_back
import storygroup.composeapp.generated.resources.common_retry
import storygroup.composeapp.generated.resources.common_save
import storygroup.composeapp.generated.resources.common_uploading
import storygroup.composeapp.generated.resources.create_group_name
import storygroup.composeapp.generated.resources.group_edit_change_image
import storygroup.composeapp.generated.resources.group_edit_desc
import storygroup.composeapp.generated.resources.group_edit_title
import storygroup.composeapp.generated.resources.group_error_load
import storygroup.composeapp.generated.resources.join_type
import storygroup.composeapp.generated.resources.join_type_approval_full
import storygroup.composeapp.generated.resources.join_type_auto_full

/**
 * 그룹 정보 수정 — 설정 탭 "그룹 정보 수정" 행에서 진입하는 풀스크린(계정 설정 패턴,
 * 상단바는 화면 소유). 저장 성공 시 화면이 스스로 GroupUpdated·GroupsChanged를 publish하고 pop한다.
 * iosApp GroupEditView.swift와 1:1 미러
 */
@Composable
fun GroupEditScreen(
    groupId: Long,
    modifier: Modifier = Modifier,
    onNavigationAction: (NavigationAction) -> Unit = sessionNavigationViewModel()::onAction,
    viewModel: GroupEditViewModel = screenViewModel {
        GroupEditViewModel(
            groupId = groupId,
            getGroupUseCase = it.getGroupUseCase,
            updateGroupUseCase = it.updateGroupUseCase,
            uploadImageUseCase = it.uploadImageUseCase
        )
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    val pickCoverImage = rememberImagePickerLauncher { picked ->
        onAction(GroupEditViewModel.Action.ChangeImage(picked.bytes, picked.fileName, picked.contentType))
    }

    // 일회성 이벤트 수집 — 저장 성공은 결과 신호+pop(상세는 커버·제목을, 목록은 카드를 다시 읽는다)
    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                GroupEditViewModel.Event.Saved -> {
                    onNavigationAction(NavigationAction.PublishResult(NavResult.GroupUpdated(groupId)))
                    onNavigationAction(NavigationAction.PublishResult(NavResult.GroupsChanged))
                    onNavigationAction(NavigationAction.NavigateBack)
                }
            }
        }
    }
    Scaffold(
        backgroundColor = sg.paper,
        topBar = {
            SgTopBar(
                title = stringResource(Res.string.group_edit_title),
                navigationIcon = {
                    IconButton(onClick = { onNavigationAction(NavigationAction.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back))
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        when {
            uiState.group == null && uiState.isLoading -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = sg.accent)
            }
            uiState.group == null -> Column(
                Modifier.padding(padding).fillMaxSize().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(uiState.error ?: stringResource(Res.string.group_error_load), style = SgTheme.typography.bodyMedium, color = sg.rust)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onAction(GroupEditViewModel.Action.Refresh) }) {
                    Text(stringResource(Res.string.common_retry), color = sg.accent)
                }
            }
            else -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
            ) {
                SgCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SgTextField(
                            value = uiState.name,
                            onValueChange = { onAction(GroupEditViewModel.Action.SetName(it)) },
                            label = stringResource(Res.string.create_group_name)
                        )
                        SgTextField(
                            value = uiState.description,
                            onValueChange = { onAction(GroupEditViewModel.Action.SetDescription(it)) },
                            label = stringResource(Res.string.group_edit_desc),
                            singleLine = false,
                            minLines = 3
                        )
                        // 대표 이미지 — 현재 값 미리보기+피커(계정 설정 아바타 패턴 재사용)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (uiState.image != null) {
                                AsyncImage(
                                    model = uiState.image,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                )
                            }
                            OutlinedButton(
                                onClick = pickCoverImage,
                                enabled = !uiState.isUploadingImage,
                                shape = SgTheme.shapes.button
                            ) {
                                Text(
                                    if (uiState.isUploadingImage) stringResource(Res.string.common_uploading) else stringResource(Res.string.group_edit_change_image),
                                    color = sg.accent
                                )
                            }
                        }
                        // 가입 방식 — 라운지는 숨김(웹 !group.isLounge 미러), 문구는 CreateGroupScreen과 동일
                        if (!uiState.isLounge) {
                            Column {
                                Text(stringResource(Res.string.join_type), style = SgTheme.typography.labelLarge, color = sg.inkSoft)
                                listOf(
                                    GroupJoinType.AUTO_APPROVE to stringResource(Res.string.join_type_auto_full),
                                    GroupJoinType.APPROVAL_REQUIRED to stringResource(Res.string.join_type_approval_full)
                                ).forEach { (type, label) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = uiState.joinType == type,
                                                onClick = { onAction(GroupEditViewModel.Action.SetJoinType(type)) }
                                            )
                                    ) {
                                        RadioButton(
                                            selected = uiState.joinType == type,
                                            onClick = { onAction(GroupEditViewModel.Action.SetJoinType(type)) },
                                            // M2 기본 선택색=secondary 함정 — accent로 고정(기존 규칙)
                                            colors = RadioButtonDefaults.colors(selectedColor = sg.accent)
                                        )
                                        Text(label, style = SgTheme.typography.bodyMedium, color = sg.ink)
                                    }
                                }
                            }
                        }
                        uiState.saveError?.let {
                            Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                        }
                        SgPrimaryButton(
                            text = stringResource(Res.string.common_save),
                            onClick = { onAction(GroupEditViewModel.Action.Save) },
                            enabled = uiState.name.isNotBlank() && !uiState.isUploadingImage,
                            isLoading = uiState.isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
