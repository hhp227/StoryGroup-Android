package kr.hhp227.storygroup.ui.screens.group

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kr.hhp227.storygroup.shared.config.AppLinks
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgSectionTitle
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.rememberShareLauncher
import org.jetbrains.compose.resources.stringResource
import storygroup.composeapp.generated.resources.*

/** 레거시 privacy_policy 미러 — 웹 개인정보처리방침(웹·API 같은 서비스) */
private const val PRIVACY_POLICY_URL = AppLinks.PRIVACY_URL

private const val TERMS_URL = AppLinks.TERMS_URL

/**
 * 설정 탭 — 레거시 SettingsFragment(item_settings.xml) 미러의 섹션별 메뉴 리스트:
 * 유저 설정(내 프로필→계정 설정)/그룹 설정(신고함=모더레이터, 정보 수정→풀스크린,
 * 삭제·나가기→확인 다이얼로그)/어플리케이션 정보(앱 설정/공유하기/이용약관/개인정보처리방침).
 * 수정 폼은 GroupEditScreen으로 분리.
 * 라운지: OWNER=신고함+정보 수정, 비OWNER=그룹 설정 섹션 숨김(웹 미러 — 나가기도 서버가 거부).
 * iosApp GroupSettingsTab.swift와 1:1 미러
 */
@Composable
internal fun GroupSettingsTab(
    uiState: GroupSettingsViewModel.UiState,
    profile: Profile?,
    onAction: (GroupSettingsViewModel.Action) -> Unit,
    onOpenGroupEdit: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenGroupReports: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    // 확인 다이얼로그 표시 여부 — 레거시 AlertDialog 미러, 화면 로컬 상태
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var confirmingLeave by rememberSaveable { mutableStateOf(false) }
    val share = rememberShareLauncher()
    // 공유 문구 — 레거시 share 미러(앱 소개+웹 주소, 폐쇄형이라 외부 공개 URL은 서비스 홈뿐)
    val shareText = stringResource(Res.string.app_share_text, AppLinks.BASE_URL)
    val uriHandler = LocalUriHandler.current

    when {
        uiState.group == null && uiState.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = sg.accent)
        }
        uiState.group == null -> Column(
            modifier.fillMaxSize().padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(uiState.error ?: stringResource(Res.string.group_error_load), style = SgTheme.typography.bodyMedium, color = sg.rust)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onAction(GroupSettingsViewModel.Action.Refresh) }) {
                Text(stringResource(Res.string.common_retry), color = sg.accent)
            }
        }
        else -> Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 유저 설정 — 레거시 user_settings 섹션(프로필 행 → 계정 설정)
            SgSectionTitle(stringResource(Res.string.group_settings_user_section))
            SgCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAccountSettings)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SgAvatar(profile?.name ?: "?", size = 44.dp, imageUrl = profile?.profileImg)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile?.name ?: stringResource(Res.string.common_loading),
                            style = SgTheme.typography.bodyLarge,
                            color = sg.ink,
                            fontWeight = FontWeight.Bold
                        )
                        Text(profile?.email.orEmpty(), style = SgTheme.typography.bodySmall, color = sg.inkSoft)
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = sg.inkFaint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // 그룹 설정 — 라운지 비OWNER는 항목이 없어 섹션째 숨긴다
            if (uiState.isOwner || !uiState.isLounge) {
                SgSectionTitle(stringResource(Res.string.group_settings_group_section))
                SgCard(Modifier.fillMaxWidth()) {
                    Column {
                        // 신고함 — 웹 커버 "신고함" 버튼의 KMP 대응(모더레이터 기능은 탭 메뉴에 모은다)
                        if (uiState.canModerate) {
                            SettingsMenuRow(stringResource(Res.string.group_reports_title), onClick = onOpenGroupReports, showChevron = true)
                        }
                        if (uiState.isOwner) {
                            // OWNER는 항상 canModerate — 위에 신고함 행이 있어 구분선이 필요하다
                            Divider(color = sg.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
                            SettingsMenuRow(stringResource(Res.string.group_edit_title), onClick = onOpenGroupEdit, showChevron = true)
                        }
                        // 라운지는 삭제·나가기 불가(웹 미러)
                        if (!uiState.isLounge) {
                            if (uiState.isOwner) {
                                Divider(color = sg.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
                                SettingsMenuRow(stringResource(Res.string.group_delete), onClick = { confirmingDelete = true }, tint = sg.rust)
                            } else {
                                if (uiState.canModerate) {
                                    Divider(color = sg.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                                // 비OWNER — 그룹 나가기(레거시 설정 탭 ll_withdrawal 미러, POST /leave 소비)
                                SettingsMenuRow(stringResource(Res.string.group_leave), onClick = { confirmingLeave = true }, tint = sg.rust)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            // 어플리케이션 정보 — 레거시 application_info 섹션(KMP에 대응 화면이 있는 항목만)
            SgSectionTitle(stringResource(Res.string.app_info_section))
            SgCard(Modifier.fillMaxWidth()) {
                Column {
                    SettingsMenuRow(stringResource(Res.string.profile_app_settings), onClick = onOpenAppSettings, showChevron = true)
                    Divider(color = sg.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsMenuRow(stringResource(Res.string.share_app), onClick = { share(shareText) })
                    Divider(color = sg.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
                    // 약관·정책 — 웹 /terms·/privacy를 외부 브라우저로 연다(설정 허브 "약관 및 정책" 미러)
                    SettingsMenuRow(stringResource(Res.string.profile_terms), onClick = { uriHandler.openUri(TERMS_URL) })
                    Divider(color = sg.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsMenuRow(stringResource(Res.string.profile_privacy), onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) })
                }
            }
        }
    }
    if (confirmingDelete) {
        CloseConfirmDialog(
            title = stringResource(Res.string.group_delete),
            message = stringResource(Res.string.group_delete_confirm),
            confirmText = stringResource(Res.string.common_delete),
            isLoading = uiState.isClosing,
            error = uiState.closeError,
            onDismiss = {
                confirmingDelete = false
                onAction(GroupSettingsViewModel.Action.DismissCloseError)
            },
            onConfirm = { onAction(GroupSettingsViewModel.Action.Delete) }
        )
    }
    if (confirmingLeave) {
        CloseConfirmDialog(
            title = stringResource(Res.string.group_leave),
            message = stringResource(Res.string.group_leave_confirm),
            confirmText = stringResource(Res.string.group_leave_action),
            isLoading = uiState.isClosing,
            error = uiState.closeError,
            onDismiss = {
                confirmingLeave = false
                onAction(GroupSettingsViewModel.Action.DismissCloseError)
            },
            onConfirm = { onAction(GroupSettingsViewModel.Action.Leave) }
        )
    }
}

/** 메뉴 행 — 레거시 item_settings 50dp 행 미러(ProfileMenuRow 관용구, 아이콘 대신 후행 화살표) */
@Composable
private fun SettingsMenuRow(
    label: String,
    onClick: () -> Unit,
    tint: Color? = null,
    showChevron: Boolean = false
) {
    val sg = SgTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = SgTheme.typography.bodyLarge, color = tint ?: sg.ink, modifier = Modifier.weight(1f))
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = sg.inkFaint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 삭제/나가기 확인 다이얼로그 — 레거시 AlertDialog 미러(DmConfirmDialog 관용구).
 * 실패 시 서버 문구를 다이얼로그 안에 그대로 보여준다(성공하면 화면째 닫혀 함께 사라진다).
 */
@Composable
private fun CloseConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sg = SgTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        SgCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = SgTheme.typography.titleMedium, color = sg.ink, fontWeight = FontWeight.Bold)
                Text(message, style = SgTheme.typography.bodyMedium, color = sg.ink)
                error?.let {
                    Text(it, style = SgTheme.typography.bodySmall, color = sg.rust)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = SgTheme.shapes.button,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.common_cancel), color = sg.ink)
                    }
                    SgPrimaryButton(
                        text = confirmText,
                        onClick = onConfirm,
                        isLoading = isLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
