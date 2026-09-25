# 그룹 설정 탭 메뉴 구조 전환 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 그룹 상세 설정 탭을 레거시 미러의 3섹션 메뉴 리스트로 바꾸고, 수정 폼을 별도 풀스크린(GroupEdit)으로 분리한다.

**Architecture:** GroupSettingsViewModel에서 폼 로직을 GroupEditViewModel(신규)로 이동, 설정 탭은 메뉴+삭제/나가기 다이얼로그만 남긴다. Compose는 NavHost 라우트 2개(GroupEditRoute/AppSettingsRoute) 신설, iOS는 GroupDetailView 자체 push 3종. 프로필 행은 세션 ProfileViewModel 재사용.

**Tech Stack:** Compose Multiplatform(M2)+navigation-compose, SwiftUI(iOS 15 폴백), shared 모듈 무수정.

**스펙:** `docs/superpowers/specs/2026-08-13-group-settings-tab-menu-design.md`

## Global Constraints

- 서버·shared 모듈 무수정. 유스케이스는 전부 기존 재사용.
- **커밋·push 금지** — 스테이징+커밋 메시지 전달까지만(사용자 몫). `git add -A` 금지, 실수정 파일만 경로 스테이징.
- 현재 브랜치 `feature/groupschedulesettings`에 이전 작업 57파일이 스테이징돼 있다 — **유지한 채 위에 덧쓰고 재스테이징**(사용자 선택: 합침). `git reset`·`git stash` 금지.
- 컴파일 검증(WSL에서 Windows gradle — WSL·Windows gradle 동시 실행 금지):
  `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat <태스크>"`
- Swift 컴파일은 Mac 부재로 검증 불가 — Kotlin 3타깃(android/jvm/iosSimulatorArm64)까지가 검증 범위. 결과 보고에 명시.
- 이 리포 composeApp VM은 단위 테스트 관례가 없다(commonTest는 플레이스홀더뿐) — 검증은 컴파일+데스크톱 스모크로 한다(기존 관례).
- EOL: 새 파일은 LF로 작성, 수정 파일은 스테이징 전 CRLF 유입 검사.
- 문구·상수(그대로 사용): 공유 문구 `"StoryGroup — 그룹과 함께하는 이야기\n" + StoryGroupApi.DEFAULT_BASE_URL`, 개인정보처리방침 URL `StoryGroupApi.DEFAULT_BASE_URL + "/privacy"` (`DEFAULT_BASE_URL` = `https://storygroup-k4cgcgz2ya-du.a.run.app`, Swift는 `StoryGroupApi.shared.DEFAULT_BASE_URL`).

---

### Task 1: Compose — GroupEditViewModel + GroupEditScreen 신설

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupEditViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupEditScreen.kt`

**Interfaces:**
- Consumes: 기존 `GetGroupUseCase`/`UpdateGroupUseCase`/`UploadImageUseCase`(AppContainer), `MviViewModel`, Sg 컴포넌트, `rememberImagePickerLauncher`.
- Produces: `GroupEditScreen(groupId: Long, onBack: () -> Unit, onSaved: () -> Unit, modifier: Modifier)` — Task 2의 App.kt 라우트가 사용. `GroupEditViewModel.Event.Saved`.

아직 아무도 참조하지 않는 신규 파일 2개라 이 태스크만으로 컴파일이 항상 성공한다.

- [ ] **Step 1: GroupEditViewModel.kt 작성** — 현행 `GroupSettingsViewModel.kt`의 폼 로직(refresh 시드/changeImage/save)을 이동한 축소판. 전체 내용:

```kotlin
package kr.hhp227.storygroup.ui.screens.group

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
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UpdateGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UploadImageUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 그룹 정보 수정 — 웹 /groups/[id]/settings 폼 미러(설정 탭 "그룹 정보 수정" 행에서 진입하는
 * 풀스크린). 진입 시 GetGroup으로 self-load해 폼을 시드한다.
 * ⚠️PATCH /api/groups/{id}는 name/description/image 전체 교체 계약 — 폼이 로드해 온 값을
 * 항상 실어 보낸다(joinType만 null=유지, 라운지가 이 경로를 쓴다).
 * iosApp GroupEditViewModel.swift와 1:1 미러
 */
class GroupEditViewModel(
    val groupId: Long,
    private val getGroupUseCase: GetGroupUseCase,
    private val updateGroupUseCase: UpdateGroupUseCase,
    private val uploadImageUseCase: UploadImageUseCase
) : ViewModel(), MviViewModel<GroupEditViewModel.UiState, GroupEditViewModel.Action, GroupEditViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            is Action.SetName -> _uiState.update { it.copy(name = action.name.take(100)) }
            is Action.SetDescription -> _uiState.update { it.copy(description = action.description.take(1000)) }
            is Action.SetJoinType -> _uiState.update { it.copy(joinType = action.joinType) }
            is Action.ChangeImage -> changeImage(action.bytes, action.fileName, action.contentType)
            Action.Save -> save()
        }
    }

    /** 진입(init)·재시도 시 발화 — 그룹을 읽어 폼을 시드한다(웹 설정 페이지 getGroup 미러) */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { getGroupUseCase(groupId) }
                .onSuccess { group ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            group = group,
                            name = group.name,
                            description = group.description.orEmpty(),
                            image = group.image,
                            joinType = group.joinType
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "그룹 정보를 불러오지 못했습니다.")
                    }
                }
        }
    }

    /** 대표 이미지 교체 — 계정 설정 아바타 패턴(업로드 성공 시 URL만 폼 상태에 반영, 저장은 별도) */
    private fun changeImage(bytes: ByteArray, fileName: String, contentType: String) {
        if (_uiState.value.isUploadingImage) return

        _uiState.update { it.copy(isUploadingImage = true, saveError = null) }
        viewModelScope.launch {
            runCatching { uploadImageUseCase(bytes, fileName, contentType) }
                .onSuccess { url ->
                    _uiState.update { it.copy(isUploadingImage = false, image = url) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isUploadingImage = false, saveError = e.message ?: "이미지 업로드에 실패했습니다.")
                    }
                }
        }
    }

    /** 저장 — ⚠️전체 교체 계약이라 4필드 전부 전송, 라운지는 joinType을 안 보낸다(null=유지) */
    private fun save() {
        val state = _uiState.value
        val group = state.group ?: return
        if (state.isSaving || state.name.isBlank()) return

        _uiState.update { it.copy(isSaving = true, saveError = null) }
        viewModelScope.launch {
            runCatching {
                updateGroupUseCase(
                    groupId = groupId,
                    name = state.name.trim(),
                    description = state.description.trim().ifBlank { null },
                    image = state.image?.ifBlank { null },
                    joinType = if (group.isLounge) null else state.joinType
                )
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false) }
                // 성공 문구 없이 바로 닫는다 — 화면이 onSaved로 결과 신호+pop을 요청한다
                _event.tryEmit(Event.Saved)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, saveError = e.message ?: "저장에 실패했습니다.") }
            }
        }
    }

    init {
        refresh()
    }

    data class UiState(
        // 로드 원본 — 라운지 분기(가입 방식 숨김·joinType 미전송)의 기준
        val group: Group? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        // 폼 상태 — 로드 성공 시 시드, 이후 사용자 입력이 이긴다(재시도 Refresh는 다시 시드)
        val name: String = "",
        val description: String = "",
        val image: String? = null,
        val joinType: GroupJoinType = GroupJoinType.AUTO_APPROVE,
        val isUploadingImage: Boolean = false,
        val isSaving: Boolean = false,
        val saveError: String? = null
    ) {
        val isLounge: Boolean get() = group?.isLounge == true
    }

    sealed interface Action {
        data object Refresh : Action
        data class SetName(val name: String) : Action
        data class SetDescription(val description: String) : Action
        data class SetJoinType(val joinType: GroupJoinType) : Action
        data class ChangeImage(val bytes: ByteArray, val fileName: String, val contentType: String) : Action
        data object Save : Action
    }

    sealed interface Event {
        /** 저장 성공 — App.kt가 GROUP_UPDATED_KEY를 남기고 pop한다 */
        data object Saved : Event
    }
}
```

- [ ] **Step 2: GroupEditScreen.kt 작성** — 계정 설정 패턴(화면 소유 상단바+VM default parameter). 폼 UI는 현행 `GroupSettingsTab.kt` OWNER 폼 카드에서 이동(위험 구역·"저장했습니다" 문구 제외). 전체 내용:

```kotlin
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kr.hhp227.storygroup.di.LocalAppContainer
import kr.hhp227.storygroup.shared.domain.model.GroupJoinType
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgTextField
import kr.hhp227.storygroup.ui.components.SgTopBar
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.rememberImagePickerLauncher

@Composable
private fun groupEditViewModel(groupId: Long): GroupEditViewModel {
    val container = LocalAppContainer.current

    return viewModel {
        GroupEditViewModel(
            groupId = groupId,
            getGroupUseCase = container.getGroupUseCase,
            updateGroupUseCase = container.updateGroupUseCase,
            uploadImageUseCase = container.uploadImageUseCase
        )
    }
}

/**
 * 그룹 정보 수정 — 설정 탭 "그룹 정보 수정" 행에서 진입하는 풀스크린(계정 설정 패턴,
 * 상단바는 화면 소유). 저장 성공 시 onSaved — App.kt가 GROUP_UPDATED_KEY를 남기고 pop한다.
 * iosApp GroupEditView.swift와 1:1 미러
 */
@Composable
fun GroupEditScreen(
    groupId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupEditViewModel = groupEditViewModel(groupId)
) {
    val uiState by viewModel.uiState.collectAsState()
    val onAction = viewModel::onAction
    val sg = SgTheme.colors
    val pickCoverImage = rememberImagePickerLauncher { picked ->
        onAction(GroupEditViewModel.Action.ChangeImage(picked.bytes, picked.fileName, picked.contentType))
    }

    // 일회성 이벤트 수집 — 저장 성공은 결과 신호+pop(라우트 몫)
    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                GroupEditViewModel.Event.Saved -> onSaved()
            }
        }
    }
    Scaffold(
        backgroundColor = sg.paper,
        topBar = {
            SgTopBar(
                title = "그룹 정보 수정",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
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
                Text(uiState.error ?: "그룹 정보를 불러오지 못했습니다.", style = SgTheme.typography.bodyMedium, color = sg.rust)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onAction(GroupEditViewModel.Action.Refresh) }) {
                    Text("다시 시도", color = sg.accent)
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
                            label = "그룹 이름"
                        )
                        SgTextField(
                            value = uiState.description,
                            onValueChange = { onAction(GroupEditViewModel.Action.SetDescription(it)) },
                            label = "설명",
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
                                    if (uiState.isUploadingImage) "업로드 중..." else "대표 이미지 변경",
                                    color = sg.accent
                                )
                            }
                        }
                        // 가입 방식 — 라운지는 숨김(웹 !group.isLounge 미러), 문구는 CreateGroupScreen과 동일
                        if (!uiState.isLounge) {
                            Column {
                                Text("가입 방식", style = SgTheme.typography.labelLarge, color = sg.inkSoft)
                                listOf(
                                    GroupJoinType.AUTO_APPROVE to "자동 승인 — 바로 가입",
                                    GroupJoinType.APPROVAL_REQUIRED to "승인제 — 신청 후 승인"
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
                            text = "저장",
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
```

(FontWeight import는 실제 사용이 없으면 빼도 된다 — 컴파일 경고 기준으로 정리.)

- [ ] **Step 3: 컴파일 확인**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat :composeApp:compileKotlinJvm"`
Expected: BUILD SUCCESSFUL

---

### Task 2: Compose — 설정 탭 메뉴 전환 + VM 축소 + 내비게이션 배선

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupSettingsViewModel.kt` (전면 교체)
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupSettingsTab.kt` (전면 교체)
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt` (배선)
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt` (라우트 2개+결과 키)

**Interfaces:**
- Consumes: Task 1의 `GroupEditScreen(groupId, onBack, onSaved, modifier)`, 기존 `AppSettingsScreen(themeState, onBack)`, `AccountSettingsRoute`, 세션 `ProfileViewModel`(`sessionViewModel { ProfileViewModel(it.getMyProfileUseCase) }`), `rememberShareLauncher()`, `SgSectionTitle(text)`, `SgAvatar(name, size, imageUrl)`.
- Produces: `GroupSettingsViewModel`(축소판: Action=Refresh/Delete/Leave/DismissCloseError, Event=Closed), `GroupSettingsTab(uiState, profile, onAction, onOpenGroupEdit, onOpenAccountSettings, onOpenAppSettings, modifier)`, `GroupDetailScreen` 신규 파라미터 5개(groupUpdateRequested/onGroupUpdateHandled/onOpenGroupEdit/onOpenAccountSettings/onOpenAppSettings), `GroupEditRoute(groupId)`/`AppSettingsRoute`/`GROUP_UPDATED_KEY` — Task 4의 iOS 미러가 이 이름들을 따른다.

- [ ] **Step 1: GroupSettingsViewModel.kt 축소** — 폼 상태·액션(SetName/SetDescription/SetJoinType/ChangeImage/Save)·Saved 이벤트·updateGroupUseCase/uploadImageUseCase 의존을 제거. 전체 내용:

```kotlin
package kr.hhp227.storygroup.ui.screens.group

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
import kr.hhp227.storygroup.shared.domain.model.Group
import kr.hhp227.storygroup.shared.domain.model.GroupRole
import kr.hhp227.storygroup.shared.domain.usecase.DeleteGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.GetGroupUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LeaveGroupUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 설정 탭 — 레거시 SettingsFragment(item_settings.xml) 미러의 메뉴 리스트(탭별 VM 분리).
 * 진입 시 GetGroup으로 self-load해 역할(OWNER 행 구성)·라운지 분기를 판정한다.
 * 수정 폼은 GroupEditViewModel(풀스크린)로 분리 — 여기엔 삭제/나가기만 남는다.
 * iosApp GroupSettingsViewModel.swift와 1:1 미러
 */
class GroupSettingsViewModel(
    val groupId: Long,
    private val getGroupUseCase: GetGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val leaveGroupUseCase: LeaveGroupUseCase
) : ViewModel(), MviViewModel<GroupSettingsViewModel.UiState, GroupSettingsViewModel.Action, GroupSettingsViewModel.Event> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<Event>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val event: Flow<Event> = _event.asSharedFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Refresh -> refresh()
            Action.Delete -> close { deleteGroupUseCase(groupId) }
            Action.Leave -> close { leaveGroupUseCase(groupId) }
            Action.DismissCloseError -> _uiState.update { it.copy(closeError = null) }
        }
    }

    /** 진입(init)·재시도·수정 화면 복귀 시 발화 — 역할·라운지 판정용 그룹을 읽는다 */
    private fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { getGroupUseCase(groupId) }
                .onSuccess { group ->
                    _uiState.update { it.copy(isLoading = false, group = group) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "그룹 정보를 불러오지 못했습니다.")
                    }
                }
        }
    }

    /** 삭제/나가기 공용 — 성공하면 이 화면 자체가 닫힌다(Closed → pop+목록 갱신) */
    private fun close(operation: suspend () -> Unit) {
        if (_uiState.value.isClosing) return

        _uiState.update { it.copy(isClosing = true, closeError = null) }
        viewModelScope.launch {
            runCatching { operation() }
                .onSuccess {
                    _uiState.update { it.copy(isClosing = false) }
                    _event.tryEmit(Event.Closed)
                }
                .onFailure { e ->
                    // OWNER 나가기 거부("그룹 삭제를 이용하세요") 등 서버 문구를 그대로 보여준다
                    _uiState.update { it.copy(isClosing = false, closeError = e.message ?: "처리에 실패했습니다.") }
                }
        }
    }

    init {
        refresh()
    }

    data class UiState(
        // 로드 원본 — 역할(OWNER 행 구성)·라운지 분기의 기준
        val group: Group? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        // 삭제/나가기 진행 — 확인 다이얼로그 표시 여부는 화면 로컬 상태
        val isClosing: Boolean = false,
        val closeError: String? = null
    ) {
        val isOwner: Boolean get() = group?.myRole == GroupRole.OWNER
        val isLounge: Boolean get() = group?.isLounge == true
    }

    sealed interface Action {
        data object Refresh : Action
        data object Delete : Action
        data object Leave : Action
        data object DismissCloseError : Action
    }

    sealed interface Event {
        /** 삭제/나가기 성공 — 화면이 onGroupClosed로 pop+그룹 목록 갱신을 요청한다 */
        data object Closed : Event
    }
}
```

- [ ] **Step 2: GroupSettingsTab.kt 전면 교체** — 3섹션 메뉴 리스트+확인 다이얼로그. 전체 내용:

```kotlin
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
import kr.hhp227.storygroup.shared.data.network.StoryGroupApi
import kr.hhp227.storygroup.shared.domain.model.Profile
import kr.hhp227.storygroup.ui.components.SgAvatar
import kr.hhp227.storygroup.ui.components.SgCard
import kr.hhp227.storygroup.ui.components.SgPrimaryButton
import kr.hhp227.storygroup.ui.components.SgSectionTitle
import kr.hhp227.storygroup.ui.theme.SgTheme
import kr.hhp227.storygroup.ui.util.rememberShareLauncher

/** 공유 문구 — 레거시 share 미러(앱 소개+웹 주소, 폐쇄형이라 외부 공개 URL은 서비스 홈뿐) */
private const val APP_SHARE_TEXT = "StoryGroup — 그룹과 함께하는 이야기\n${StoryGroupApi.DEFAULT_BASE_URL}"

/** 레거시 privacy_policy 미러 — 웹 개인정보처리방침(웹·API 같은 서비스) */
private const val PRIVACY_POLICY_URL = "${StoryGroupApi.DEFAULT_BASE_URL}/privacy"

/**
 * 설정 탭 — 레거시 SettingsFragment(item_settings.xml) 미러의 섹션별 메뉴 리스트:
 * 유저 설정(내 프로필→계정 설정)/그룹 설정(정보 수정→풀스크린, 삭제·나가기→확인 다이얼로그)/
 * 어플리케이션 정보(앱 설정/공유하기/개인정보처리방침). 수정 폼은 GroupEditScreen으로 분리.
 * 라운지: OWNER=정보 수정만, 비OWNER=그룹 설정 섹션 숨김(웹 미러 — 나가기도 서버가 거부).
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
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    // 확인 다이얼로그 표시 여부 — 레거시 AlertDialog 미러, 화면 로컬 상태
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var confirmingLeave by rememberSaveable { mutableStateOf(false) }
    val share = rememberShareLauncher()
    val uriHandler = LocalUriHandler.current

    when {
        uiState.group == null && uiState.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = sg.accent)
        }
        uiState.group == null -> Column(
            modifier.fillMaxSize().padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(uiState.error ?: "그룹 정보를 불러오지 못했습니다.", style = SgTheme.typography.bodyMedium, color = sg.rust)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onAction(GroupSettingsViewModel.Action.Refresh) }) {
                Text("다시 시도", color = sg.accent)
            }
        }
        else -> Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 유저 설정 — 레거시 user_settings 섹션(프로필 행 → 계정 설정)
            SgSectionTitle("유저 설정")
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
                            profile?.name ?: "불러오는 중...",
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
                SgSectionTitle("그룹 설정")
                SgCard(Modifier.fillMaxWidth()) {
                    Column {
                        if (uiState.isOwner) {
                            SettingsMenuRow("그룹 정보 수정", onClick = onOpenGroupEdit, showChevron = true)
                        }
                        // 라운지는 삭제·나가기 불가(웹 미러)
                        if (!uiState.isLounge) {
                            if (uiState.isOwner) {
                                Divider(color = sg.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
                                SettingsMenuRow("그룹 삭제", onClick = { confirmingDelete = true }, tint = sg.rust)
                            } else {
                                // 비OWNER — 그룹 나가기(레거시 설정 탭 ll_withdrawal 미러, POST /leave 소비)
                                SettingsMenuRow("그룹 나가기", onClick = { confirmingLeave = true }, tint = sg.rust)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            // 어플리케이션 정보 — 레거시 application_info 섹션(KMP에 대응 화면이 있는 항목만)
            SgSectionTitle("어플리케이션 정보")
            SgCard(Modifier.fillMaxWidth()) {
                Column {
                    SettingsMenuRow("앱 설정", onClick = onOpenAppSettings, showChevron = true)
                    Divider(color = sg.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsMenuRow("공유하기", onClick = { share(APP_SHARE_TEXT) })
                    Divider(color = sg.stoneBorder, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsMenuRow("개인정보처리방침", onClick = { uriHandler.openUri(PRIVACY_POLICY_URL) })
                }
            }
        }
    }
    if (confirmingDelete) {
        CloseConfirmDialog(
            title = "그룹 삭제",
            message = "정말 삭제할까요? 게시글, 채팅, 파일이 모두 사라집니다.",
            confirmText = "삭제",
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
            title = "그룹 나가기",
            message = "정말 나갈까요? 나가면 이 그룹의 게시글·채팅에 더는 참여할 수 없습니다.",
            confirmText = "나가기",
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
                        Text("취소", color = sg.ink)
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
```

(⚠️`Profile` import 경로는 `ProfileViewModel.kt`가 쓰는 것과 동일하게 맞춘다 — `kr.hhp227.storygroup.shared.domain.model.Profile`이 아니면 그 파일의 import를 복사.)

- [ ] **Step 3: GroupDetailScreen.kt 배선** — 변경 지도:
  - `groupSettingsViewModel(groupId)` 팩토리: `updateGroupUseCase`/`uploadImageUseCase` 인자 2줄 삭제(축소된 생성자와 일치).
  - `GroupDetailScreen` 파라미터에 추가(`onGroupClosed` 다음):

```kotlin
    // 그룹 정보 수정 화면에서 돌아온 결과 — 상세·설정 탭을 다시 읽는다(GROUP_UPDATED_KEY)
    groupUpdateRequested: Boolean,
    onGroupUpdateHandled: () -> Unit,
    // 설정 탭 메뉴의 풀스크린 진입 3종 — 라우트는 App.kt가 배선한다
    onOpenGroupEdit: () -> Unit,
    onOpenAccountSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
```

  - `GroupDetailScreen` default parameter에 추가(설정 탭 유저 설정 행 — AccountSettingsScreen 선례):

```kotlin
    // 유저 설정 행(설정 탭) — 프로필 탭/드로어 헤더와 같은 세션 스코프 인스턴스
    profileViewModel: ProfileViewModel = sessionViewModel { ProfileViewModel(it.getMyProfileUseCase) }
```

  - imports 추가: `kr.hhp227.storygroup.di.sessionViewModel`, `kr.hhp227.storygroup.ui.screens.profile.ProfileViewModel`.
  - `GroupDetailContent`에 같은 파라미터 6개(profileViewModel 포함) 추가하고 Screen→Content로 전부 전달.
  - Content에 수집 추가: `val profileUiState by profileViewModel.uiState.collectAsState()`
  - Content에 LaunchedEffect 추가(기존 refreshRequested 블록 아래):

```kotlin
    // 그룹 정보 수정에서 돌아온 결과 — 커버·제목과 설정 탭 판정 그룹을 다시 읽는다
    LaunchedEffect(groupUpdateRequested) {
        if (groupUpdateRequested) {
            viewModel.onAction(GroupDetailViewModel.Action.Refresh)
            settingsViewModel.onAction(GroupSettingsViewModel.Action.Refresh)
            onGroupUpdateHandled()
        }
    }
```

  - 설정 탭 이벤트 수집: `Event.Saved` 분기 삭제(이벤트가 Closed뿐):

```kotlin
    // 설정 탭 일회성 이벤트 — 삭제/나가기 성공 시 화면 닫기(저장 갱신은 GROUP_UPDATED_KEY 경로)
    LaunchedEffect(settingsViewModel) {
        settingsViewModel.event.collect { event ->
            when (event) {
                GroupSettingsViewModel.Event.Closed -> onGroupClosed()
            }
        }
    }
```

  - 페이지 4 호출부 교체:

```kotlin
            else -> GroupSettingsTab(
                uiState = settingsUiState,
                profile = profileUiState.profile,
                onAction = settingsViewModel::onAction,
                onOpenGroupEdit = onOpenGroupEdit,
                onOpenAccountSettings = onOpenAccountSettings,
                onOpenAppSettings = onOpenAppSettings
            )
```

- [ ] **Step 4: App.kt 배선** — 변경 지도:
  - 라우트 선언부에 추가(AccountSettingsRoute 근처):

```kotlin
/** 그룹 정보 수정 — 설정 탭 메뉴에서 진입(계정 설정과 같은 셸 위 풀스크린) */
@Serializable
internal data class GroupEditRoute(val groupId: Long)

/** 앱 설정 — 셸 내부 오버레이 외에 그룹 상세(오버레이 목적지) 위에서도 열 수 있는 라우트 */
@Serializable
internal data object AppSettingsRoute
```

  - 결과 키 추가(POST_CREATED_KEY 아래):

```kotlin
/** 그룹 정보 수정 성공을 이전 백스택 엔트리(그룹 상세)로 알리는 결과 키 — POST_CREATED_KEY 패턴 */
internal const val GROUP_UPDATED_KEY = "group_updated"
```

  - `composable<GroupDetailRoute>` 블록: 결과 수집 추가+`GroupDetailScreen` 호출에 신규 인자 전달:

```kotlin
                    // 수정 화면이 남긴 결과 수신 — 상세·설정 탭이 그룹을 다시 읽는다
                    val groupUpdated by backStackEntry.savedStateHandle
                        .getStateFlow(GROUP_UPDATED_KEY, false)
                        .collectAsState()
```

```kotlin
                            groupUpdateRequested = groupUpdated,
                            onGroupUpdateHandled = { backStackEntry.savedStateHandle[GROUP_UPDATED_KEY] = false },
                            onOpenGroupEdit = { navController.navigate(GroupEditRoute(route.groupId)) },
                            onOpenAccountSettings = { navController.navigate(AccountSettingsRoute) },
                            onOpenAppSettings = { navController.navigate(AppSettingsRoute) },
```

  - NavHost에 목적지 2개 추가(AccountSettingsRoute 블록 아래):

```kotlin
                composable<GroupEditRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<GroupEditRoute>()

                    Surface(color = SgTheme.colors.paper) {
                        GroupEditScreen(
                            groupId = route.groupId,
                            onBack = { navController.popBackStack() },
                            onSaved = {
                                // 상세가 커버·제목을 다시 읽게 결과를 남기고 닫는다(CreatePost 결과 패턴)
                                navController.previousBackStackEntry?.savedStateHandle?.set(GROUP_UPDATED_KEY, true)
                                // 목록 카드의 이름·커버도 갱신되게 — 셸 그룹 탭이 신호를 소비한다
                                groupsRefreshPending = true
                                navController.popBackStack()
                            },
                            // 풀스크린이라 하단 시스템 내비바 인셋을 화면이 직접 소화
                            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        )
                    }
                }
                composable<AppSettingsRoute> {
                    Surface(color = SgTheme.colors.paper) {
                        // 셸 내부(프로필 탭)와 같은 화면 — 그룹 상세 위에서 열 때는 NavHost 목적지로 띄운다
                        AppSettingsScreen(themeState = themeState, onBack = { navController.popBackStack() })
                    }
                }
```

  - imports 추가: `kr.hhp227.storygroup.ui.screens.group.GroupEditScreen`, `kr.hhp227.storygroup.ui.screens.settings.AppSettingsScreen`(기존에 없으면).

- [ ] **Step 5: 컴파일 확인(2타깃)**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat :composeApp:compileKotlinJvm :composeApp:compileDebugKotlinAndroid"`
Expected: BUILD SUCCESSFUL

---

### Task 3: iOS — GroupEditViewModel + GroupEditView 신설 + pbxproj 등록

**Files:**
- Create: `iosApp/iosApp/UI/Screens/Group/GroupEditViewModel.swift`
- Create: `iosApp/iosApp/UI/Screens/Group/GroupEditView.swift`
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`

**Interfaces:**
- Consumes: `MviViewModel` 프로토콜, `AppContainer`(getGroupUseCase/updateGroupUseCase/uploadImageUseCase), `ImagePicker`, `SGCard`/`SGTextField`/`SGPrimaryButton`, `Data.toKotlinByteArray()`, `Error.kotlinMessage(fallback:)`.
- Produces: `GroupEditView(groupId: Int64, container: AppContainer, onSaved: @escaping () -> Void)` — Task 4의 GroupDetailView가 push. `GroupEditViewModel.Event.saved`.

- [ ] **Step 1: GroupEditViewModel.swift 작성** — 현행 `GroupSettingsViewModel.swift`의 폼 로직 이동(Task 1 Kotlin과 1:1). 전체 내용:

```swift
import Combine
import Foundation
import Shared

/// 그룹 정보 수정 — composeApp GroupEditViewModel.kt와 1:1 미러(웹 /groups/[id]/settings 폼,
/// 설정 탭 "그룹 정보 수정" 행에서 진입하는 풀스크린). 진입 시 GetGroup으로 self-load해 폼을 시드한다.
/// ⚠️PATCH /api/groups/{id}는 name/description/image 전체 교체 계약 — 폼이 로드해 온 값을
/// 항상 실어 보낸다(joinType만 nil=유지, 라운지가 이 경로를 쓴다).
final class GroupEditViewModel: MviViewModel {
    @Published private(set) var uiState = UiState()

    let event = PassthroughSubject<Event, Never>()

    let groupId: Int64

    private let getGroupUseCase: GetGroupUseCase

    private let updateGroupUseCase: UpdateGroupUseCase

    private let uploadImageUseCase: UploadImageUseCase

    func onAction(_ action: Action) {
        switch action {
        case .refresh: refresh()
        case .setName(let name):
            uiState.name = String(name.prefix(100))
        case .setDescription(let description):
            uiState.description = String(description.prefix(1000))
        case .setJoinType(let joinType):
            uiState.joinType = joinType
        case .changeImage(let bytes, let fileName, let contentType):
            changeImage(bytes: bytes, fileName: fileName, contentType: contentType)
        case .save: save()
        }
    }

    /// 진입(init)·재시도 시 발화 — 그룹을 읽어 폼을 시드한다(웹 설정 페이지 getGroup 미러)
    private func refresh() {
        if uiState.isLoading { return }

        uiState.isLoading = true
        uiState.error = nil
        Task { @MainActor in
            do {
                let group = try await getGroupUseCase.invoke(groupId: groupId)
                uiState.isLoading = false
                uiState.group = group
                uiState.name = group.name
                uiState.description = group.description_ ?? ""
                uiState.image = group.image
                uiState.joinType = group.joinType
            } catch {
                uiState.isLoading = false
                uiState.error = error.kotlinMessage(fallback: "그룹 정보를 불러오지 못했습니다.")
            }
        }
    }

    /// 대표 이미지 교체 — 계정 설정 아바타 패턴(업로드 성공 시 URL만 폼 상태에 반영, 저장은 별도)
    private func changeImage(bytes: Data, fileName: String, contentType: String) {
        if uiState.isUploadingImage { return }

        uiState.isUploadingImage = true
        uiState.saveError = nil
        Task { @MainActor in
            do {
                let url = try await uploadImageUseCase.invoke(
                    bytes: bytes.toKotlinByteArray(),
                    fileName: fileName,
                    contentType: contentType
                )
                uiState.isUploadingImage = false
                uiState.image = url
            } catch {
                uiState.isUploadingImage = false
                uiState.saveError = error.kotlinMessage(fallback: "이미지 업로드에 실패했습니다.")
            }
        }
    }

    /// 저장 — ⚠️전체 교체 계약이라 4필드 전부 전송, 라운지는 joinType을 안 보낸다(nil=유지)
    private func save() {
        guard let group = uiState.group else { return }
        if uiState.isSaving || uiState.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return }

        uiState.isSaving = true
        uiState.saveError = nil
        let trimmedName = uiState.name.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedDescription = uiState.description.trimmingCharacters(in: .whitespacesAndNewlines)
        let image = (uiState.image?.isEmpty ?? true) ? nil : uiState.image
        let joinType = group.isLounge ? nil : uiState.joinType
        Task { @MainActor in
            do {
                _ = try await updateGroupUseCase.invoke(
                    groupId: groupId,
                    name: trimmedName,
                    description: trimmedDescription.isEmpty ? nil : trimmedDescription,
                    image: image,
                    joinType: joinType
                )
                uiState.isSaving = false
                // 성공 문구 없이 바로 닫는다 — 화면이 onSaved로 결과 신호+pop을 요청한다
                event.send(.saved)
            } catch {
                uiState.isSaving = false
                uiState.saveError = error.kotlinMessage(fallback: "저장에 실패했습니다.")
            }
        }
    }

    init(
        groupId: Int64,
        getGroupUseCase: GetGroupUseCase,
        updateGroupUseCase: UpdateGroupUseCase,
        uploadImageUseCase: UploadImageUseCase
    ) {
        self.groupId = groupId
        self.getGroupUseCase = getGroupUseCase
        self.updateGroupUseCase = updateGroupUseCase
        self.uploadImageUseCase = uploadImageUseCase
        refresh()
    }

    struct UiState {
        // 로드 원본 — 라운지 분기(가입 방식 숨김·joinType 미전송)의 기준
        var group: Group? = nil
        var isLoading = false
        var error: String? = nil
        // 폼 상태 — 로드 성공 시 시드, 이후 사용자 입력이 이긴다(재시도 Refresh는 다시 시드)
        var name: String = ""
        var description: String = ""
        var image: String? = nil
        var joinType: GroupJoinType = .autoApprove
        var isUploadingImage = false
        var isSaving = false
        var saveError: String? = nil

        var isLounge: Bool { group?.isLounge == true }
    }

    enum Action {
        case refresh
        case setName(String)
        case setDescription(String)
        case setJoinType(GroupJoinType)
        case changeImage(bytes: Data, fileName: String, contentType: String)
        case save
    }

    enum Event {
        /// 저장 성공 — 화면이 onSaved로 결과 신호+pop을 요청한다
        case saved
    }
}
```

(⚠️파일 상단에 `import class Shared.Group` — 현행 GroupSettingsViewModel.swift에 있으면 그대로 복사. 도메인 Group과 SwiftUI.Group 충돌 방지.)

- [ ] **Step 2: GroupEditView.swift 작성** — 현행 `GroupSettingsTab.swift`의 ownerForm/outlinedButton/joinTypeRow를 이동. 전체 내용:

```swift
import SwiftUI
import Shared

/// 그룹 정보 수정 — composeApp GroupEditScreen.kt와 1:1 미러(설정 탭 메뉴에서 진입하는
/// 풀스크린, 계정 설정 패턴). 내비바는 루트 스택 몫. 저장 성공 시 onSaved — 부모가
/// pop+상세 갱신을 처리한다(Compose GROUP_UPDATED_KEY 미러).
struct GroupEditView: View {
    @StateObject private var viewModel: GroupEditViewModel

    /// 저장 성공 — 부모(GroupDetailContent)가 pop+상세·설정 탭 refresh+목록 갱신 신호를 처리한다
    private let onSaved: () -> Void

    @Environment(\.sgColors) private var colors

    /// 대표 이미지 피커 — 계정 설정 아바타 패턴 재사용(PHPickerViewController, 권한 불필요)
    @State private var showImagePicker = false

    var body: some View {
        ScrollView {
            content.padding(16)
        }
        .background(colors.paper.ignoresSafeArea())
        .navigationTitle("그룹 정보 수정")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showImagePicker) {
            ImagePicker { data, fileName, contentType in
                viewModel.onAction(.changeImage(bytes: data, fileName: fileName, contentType: contentType))
            }
        }
        .onReceive(viewModel.event) { event in
            switch event {
            case .saved: onSaved()
            }
        }
    }

    @ViewBuilder private var content: some View {
        let uiState = viewModel.uiState

        if uiState.group == nil, uiState.isLoading {
            ProgressView().padding(.vertical, 48)
        } else if uiState.group == nil {
            VStack(spacing: 8) {
                Text(uiState.error ?? "그룹 정보를 불러오지 못했습니다.")
                    .font(.subheadline)
                    .foregroundColor(colors.rust)
                Button("다시 시도") { viewModel.onAction(.refresh) }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 48)
        } else {
            form(uiState)
        }
    }

    /// 그룹 정보 수정 폼 — 웹 GroupSettingsForm 미러(기존 설정 탭 ownerForm 이동)
    @ViewBuilder private func form(_ uiState: GroupEditViewModel.UiState) -> some View {
        SGCard {
            VStack(alignment: .leading, spacing: 12) {
                SGTextField(
                    label: "그룹 이름",
                    text: Binding(get: { uiState.name }, set: { viewModel.onAction(.setName($0)) })
                )
                VStack(alignment: .leading, spacing: 6) {
                    Text("설명").font(.caption.bold()).foregroundColor(colors.inkSoft)
                    // 멀티라인은 TextEditor(웹 textarea 미러) — CreateGroupView/AccountSettingsView와 같은 타협
                    TextEditor(text: Binding(get: { uiState.description }, set: { viewModel.onAction(.setDescription($0)) }))
                        .frame(minHeight: 72)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(RoundedRectangle(cornerRadius: 10).stroke(colors.stoneBorder, lineWidth: 1))
                }
                // 대표 이미지 — 현재 값 미리보기+피커(계정 설정 아바타 패턴 재사용)
                HStack(spacing: 12) {
                    if let imageUrlString = uiState.image, let url = URL(string: imageUrlString) {
                        AsyncImage(url: url) { phase in
                            if case .success(let image) = phase {
                                image.resizable().scaledToFill()
                            }
                        }
                        .frame(width: 56, height: 56)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    outlinedButton(
                        uiState.isUploadingImage ? "업로드 중..." : "대표 이미지 변경",
                        color: colors.accent,
                        enabled: !uiState.isUploadingImage
                    ) { showImagePicker = true }
                }
                // 가입 방식 — 라운지는 숨김(웹 !group.isLounge 미러), 문구는 CreateGroupScreen과 동일
                if !uiState.isLounge {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("가입 방식").font(.caption.bold()).foregroundColor(colors.inkSoft)
                        joinTypeRow("자동 승인 — 바로 가입", isSelected: uiState.joinType == .autoApprove) {
                            viewModel.onAction(.setJoinType(.autoApprove))
                        }
                        joinTypeRow("승인제 — 신청 후 승인", isSelected: uiState.joinType == .approvalRequired) {
                            viewModel.onAction(.setJoinType(.approvalRequired))
                        }
                    }
                }
                if let saveError = uiState.saveError {
                    Text(saveError).font(.caption).foregroundColor(colors.rust)
                }
                SGPrimaryButton(
                    title: "저장",
                    enabled: !uiState.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !uiState.isUploadingImage,
                    isLoading: uiState.isSaving,
                    action: { viewModel.onAction(.save) }
                )
            }
            .padding(16)
        }
    }

    /// 웹 OutlinedButton 미러 — 테두리만 있는 보조 버튼(Compose OutlinedButton+shape.button 미러)
    private func outlinedButton(_ title: String, color: Color, enabled: Bool = true, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.bold())
                .foregroundColor(enabled ? color : colors.inkFaint)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .overlay(
                    RoundedRectangle(cornerRadius: colors.radiusButton ?? 20, style: .continuous)
                        .stroke(colors.stoneBorder, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    /// 가입 방식 라디오 행 — CreateGroupView.joinTypeRow와 동일 관용구(웹 라디오 미러)
    private func joinTypeRow(_ label: String, isSelected: Bool, onSelect: @escaping () -> Void) -> some View {
        Button(action: onSelect) {
            HStack(spacing: 10) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .foregroundColor(isSelected ? colors.accent : colors.inkSoft)
                Text(label).font(.subheadline).foregroundColor(colors.ink)
                Spacer()
            }
        }
        .buttonStyle(.plain)
    }

    init(groupId: Int64, container: AppContainer, onSaved: @escaping () -> Void) {
        _viewModel = StateObject(wrappedValue: GroupEditViewModel(
            groupId: groupId,
            getGroupUseCase: container.getGroupUseCase,
            updateGroupUseCase: container.updateGroupUseCase,
            uploadImageUseCase: container.uploadImageUseCase
        ))
        self.onSaved = onSaved
    }
}
```

- [ ] **Step 3: pbxproj 등록(2파일)** — 마지막 사용 인덱스 확인:
  `grep -o "A101[0-9A-F]\{4\}AABBCCDDEEFF[0-9A-F]\{4\}" iosApp/iosApp.xcodeproj/project.pbxproj | sort -u | tail`
  (현재 마지막은 0040). 다음 인덱스로 `GroupEditViewModel.swift`(0041)/`GroupEditView.swift`(0042)를 4곳에 추가 — PBXBuildFile/PBXFileReference/Group 그룹 children(기존 `GroupSettingsTab.swift` 항목 근처)/Sources:

```
A1010041AABBCCDDEEFF0041 /* GroupEditViewModel.swift in Sources */ = {isa = PBXBuildFile; fileRef = A1011041AABBCCDDEEFF0041 /* GroupEditViewModel.swift */; };
A1010042AABBCCDDEEFF0042 /* GroupEditView.swift in Sources */ = {isa = PBXBuildFile; fileRef = A1011042AABBCCDDEEFF0042 /* GroupEditView.swift */; };
A1011041AABBCCDDEEFF0041 /* GroupEditViewModel.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = GroupEditViewModel.swift; sourceTree = "<group>"; };
A1011042AABBCCDDEEFF0042 /* GroupEditView.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = GroupEditView.swift; sourceTree = "<group>"; };
```

  등록 후 정합성 검증: 디스크 .swift 수 == PBXFileReference .swift 수 == Sources 항목 수.

```bash
find iosApp/iosApp -name "*.swift" | wc -l
grep -c "isa = PBXFileReference; lastKnownFileType = sourcecode.swift" iosApp/iosApp.xcodeproj/project.pbxproj
grep -c "in Sources \*/ = {isa = PBXBuildFile" iosApp/iosApp.xcodeproj/project.pbxproj
```

Expected: 세 수가 같은 만큼씩 증가(+2). ⚠️Swift 컴파일 검증 불가 — 문법·API는 기존 파일 미러로 담보.

---

### Task 4: iOS — 설정 탭 메뉴 전환 + VM 축소 + GroupDetailView/MainShellView 배선

**Files:**
- Modify: `iosApp/iosApp/UI/Screens/Group/GroupSettingsViewModel.swift` (전면 교체)
- Modify: `iosApp/iosApp/UI/Screens/Group/GroupSettingsTab.swift` (전면 교체)
- Modify: `iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift` (배선)
- Modify: `iosApp/iosApp/UI/Shell/MainShellView.swift` (파라미터 전달)

**Interfaces:**
- Consumes: Task 3의 `GroupEditView(groupId:container:onSaved:)`, 기존 `AccountSettingsView(container:profileViewModel:)`, `SGSettingsView(theme:)`, `SGSectionTitle(text:)`, `SGAvatar(name:size:imageUrl:)`, `ShareItem(text:)`, Task 2의 Kotlin 미러 시그니처.
- Produces: `GroupDetailView.init(groupId:container:chatViewModel:theme:profileViewModel:onGroupClosed:onGroupUpdated:)` — MainShellView가 호출.

- [ ] **Step 1: GroupSettingsViewModel.swift 축소** — Task 2 Step 1 Kotlin과 1:1 미러. 남기는 것: `groupId`/`getGroupUseCase`/`deleteGroupUseCase`/`leaveGroupUseCase`, `refresh()`(그룹만 시드 — name/description/image/joinType 시드 4줄 삭제), `close(_:)`, `UiState(group/isLoading/error/isClosing/closeError, isOwner/isLounge)`, `Action(refresh/delete/leave/dismissCloseError)`, `Event(closed)`. 삭제: setName/setDescription/setJoinType/changeImage/save 케이스와 메서드, 폼 상태 7종(name/description/image/joinType/isUploadingImage/isSaving/saved/saveError), `.saved` 이벤트, `updateGroupUseCase`/`uploadImageUseCase` 프로퍼티·init 인자. 독 코멘트를 Kotlin 신판과 동일 취지로 갱신.

- [ ] **Step 2: GroupSettingsTab.swift 전면 교체** — 전체 내용:

```swift
import SwiftUI
import Shared

/// 설정 탭 — composeApp GroupSettingsTab.kt와 1:1 미러: 레거시 SettingsFragment(item_settings.xml)
/// 미러의 섹션별 메뉴 리스트(유저 설정/그룹 설정/어플리케이션 정보). 수정 폼은 GroupEditView로 분리.
/// 라운지: OWNER=정보 수정만, 비OWNER=그룹 설정 섹션 숨김(웹 미러 — 나가기도 서버가 거부).
struct GroupSettingsTab: View {
    @ObservedObject var viewModel: GroupSettingsViewModel

    /// 유저 설정 행 — 셸 세션 ProfileViewModel의 프로필(프로필 탭/드로어 헤더와 동일 원천)
    let profile: Profile?

    let onOpenGroupEdit: () -> Void

    let onOpenAccountSettings: () -> Void

    let onOpenAppSettings: () -> Void

    /// 공유하기 — 부모의 ShareItem 시트를 연다(피드 카드 공유와 같은 경로)
    let onShareApp: () -> Void

    @Environment(\.sgColors) private var colors

    /// 확인 다이얼로그 표시 여부 — 레거시 AlertDialog 미러, 화면 로컬 상태
    @State private var confirmingDelete = false

    @State private var confirmingLeave = false

    /// 레거시 privacy_policy 미러 — 웹 개인정보처리방침(웹·API 같은 서비스)
    private static let privacyPolicyUrl = URL(string: "\(StoryGroupApi.shared.DEFAULT_BASE_URL)/privacy")!

    var body: some View {
        content
            .frame(maxWidth: .infinity)
            // 레거시 AlertDialog 미러 — iOS는 .alert(확인 즉시 닫힘), 실패 문구는 아래 별도 알럿
            .alert("그룹 삭제", isPresented: $confirmingDelete) {
                Button("삭제", role: .destructive) { viewModel.onAction(.delete) }
                Button("취소", role: .cancel) {}
            } message: {
                Text("정말 삭제할까요? 게시글, 채팅, 파일이 모두 사라집니다.")
            }
            .alert("그룹 나가기", isPresented: $confirmingLeave) {
                Button("나가기", role: .destructive) { viewModel.onAction(.leave) }
                Button("취소", role: .cancel) {}
            } message: {
                Text("정말 나갈까요? 나가면 이 그룹의 게시글·채팅에 더는 참여할 수 없습니다.")
            }
            // 삭제/나가기 실패 — 서버 문구 그대로(likeError 알럿 관용구)
            .alert("처리 실패", isPresented: Binding(
                get: { viewModel.uiState.closeError != nil },
                set: { if !$0 { viewModel.onAction(.dismissCloseError) } }
            )) {
                Button("확인", role: .cancel) {}
            } message: {
                Text(viewModel.uiState.closeError ?? "")
            }
    }

    @ViewBuilder private var content: some View {
        let uiState = viewModel.uiState

        if uiState.group == nil, uiState.isLoading {
            ProgressView().padding(.vertical, 48)
        } else if uiState.group == nil {
            VStack(spacing: 8) {
                Text(uiState.error ?? "그룹 정보를 불러오지 못했습니다.")
                    .font(.subheadline)
                    .foregroundColor(colors.rust)
                Button("다시 시도") { viewModel.onAction(.refresh) }
                    .font(.subheadline)
                    .foregroundColor(colors.accent)
            }
            .padding(.vertical, 48)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                // 유저 설정 — 레거시 user_settings 섹션(프로필 행 → 계정 설정)
                SGSectionTitle(text: "유저 설정")
                SGCard {
                    profileRow
                }
                Spacer().frame(height: 8)
                // 그룹 설정 — 라운지 비OWNER는 항목이 없어 섹션째 숨긴다
                if uiState.isOwner || !uiState.isLounge {
                    SGSectionTitle(text: "그룹 설정")
                    SGCard {
                        VStack(spacing: 0) {
                            if uiState.isOwner {
                                menuRow("그룹 정보 수정", showChevron: true, action: onOpenGroupEdit)
                            }
                            // 라운지는 삭제·나가기 불가(웹 미러)
                            if !uiState.isLounge {
                                if uiState.isOwner {
                                    divider
                                    menuRow("그룹 삭제", tint: colors.rust) { confirmingDelete = true }
                                } else {
                                    // 비OWNER — 그룹 나가기(레거시 설정 탭 ll_withdrawal 미러, POST /leave 소비)
                                    menuRow("그룹 나가기", tint: colors.rust) { confirmingLeave = true }
                                }
                            }
                        }
                    }
                    Spacer().frame(height: 8)
                }
                // 어플리케이션 정보 — 레거시 application_info 섹션(KMP에 대응 화면이 있는 항목만)
                SGSectionTitle(text: "어플리케이션 정보")
                SGCard {
                    VStack(spacing: 0) {
                        menuRow("앱 설정", showChevron: true, action: onOpenAppSettings)
                        divider
                        menuRow("공유하기", action: onShareApp)
                        divider
                        menuRow("개인정보처리방침") { UIApplication.shared.open(Self.privacyPolicyUrl) }
                    }
                }
            }
            .padding(16)
        }
    }

    /// 내 프로필 행 — 아바타+이름+이메일, 탭하면 계정 설정(레거시 ll_profile 미러)
    private var profileRow: some View {
        Button(action: onOpenAccountSettings) {
            HStack(spacing: 12) {
                SGAvatar(name: profile?.name ?? "?", size: 44, imageUrl: profile?.profileImg)
                VStack(alignment: .leading, spacing: 2) {
                    Text(profile?.name ?? "불러오는 중...")
                        .font(.subheadline.bold())
                        .foregroundColor(colors.ink)
                    Text(profile?.email ?? "")
                        .font(.caption)
                        .foregroundColor(colors.inkSoft)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundColor(colors.inkFaint)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .buttonStyle(.plain)
    }

    /// 메뉴 행 — 레거시 item_settings 50dp 행 미러(Compose SettingsMenuRow 미러)
    private func menuRow(
        _ label: String,
        tint: Color? = nil,
        showChevron: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack {
                Text(label)
                    .font(.subheadline)
                    .foregroundColor(tint ?? colors.ink)
                Spacer()
                if showChevron {
                    Image(systemName: "chevron.right")
                        .font(.caption)
                        .foregroundColor(colors.inkFaint)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
        }
        .buttonStyle(.plain)
    }

    private var divider: some View {
        Rectangle()
            .fill(colors.stoneBorder)
            .frame(height: 1)
            .padding(.horizontal, 16)
    }
}
```

(⚠️`import UIKit`가 필요하면 추가 — `UIApplication.shared.open`. GroupDetailView.swift처럼 상단에 `import UIKit`.)

- [ ] **Step 3: GroupDetailView.swift 배선** — 변경 지도:
  - `GroupDetailView`에 프로퍼티 3개 추가:

```swift
    /// 앱 설정 push용 테마 상태(셸 소유) — SGSettingsView가 요구한다
    private let theme: SGThemeState

    /// 유저 설정 행+계정 설정 push용 세션 VM(셸 소유) — AccountSettingsView 선례
    private let profileViewModel: ProfileViewModel

    /// 그룹 정보 수정 저장 성공 — 목록 카드의 이름·커버 갱신 신호(pop은 하지 않는다)
    private let onGroupUpdated: () -> Void
```

  - init 시그니처 교체: `init(groupId: Int64, container: AppContainer, chatViewModel: ChatViewModel, theme: SGThemeState, profileViewModel: ProfileViewModel, onGroupClosed: @escaping () -> Void, onGroupUpdated: @escaping () -> Void)` — 본문에서 `self.theme`/`self.profileViewModel`/`self.onGroupUpdated` 할당 추가. `_groupSettingsViewModel` 초기화에서 `updateGroupUseCase`/`uploadImageUseCase` 인자 2줄 삭제.
  - `body`의 `GroupDetailContent(...)` 호출에 `theme: theme, profileViewModel: profileViewModel, onGroupUpdated: onGroupUpdated` 전달.
  - `GroupDetailContent`에 추가: `let theme: SGThemeState`, `@ObservedObject var profileViewModel: ProfileViewModel`, `let onGroupUpdated: () -> Void`, 그리고 @State 3개+공유 문구:

```swift
    /// 설정 탭 풀스크린 push 3종 — Compose GroupEditRoute/AccountSettingsRoute/AppSettingsRoute 미러
    @State private var showGroupEdit = false

    @State private var showAccountSettings = false

    @State private var showAppSettings = false

    /// 공유 문구 — 레거시 share 미러(앱 소개+웹 주소, Compose APP_SHARE_TEXT 미러)
    private static let appShareText = "StoryGroup — 그룹과 함께하는 이야기\n\(StoryGroupApi.shared.DEFAULT_BASE_URL)"
```

  - `body` 두 분기에 목적지 3개 추가 — iOS 16 분기:

```swift
                .navigationDestination(isPresented: $showGroupEdit) { groupEditDestination }
                .navigationDestination(isPresented: $showAccountSettings) { accountSettingsDestination }
                .navigationDestination(isPresented: $showAppSettings) { appSettingsDestination }
```

  iOS 15 분기(기존 숨김 NavigationLink 2개 아래 같은 형식으로 3개):

```swift
                .background(
                    NavigationLink(isActive: $showGroupEdit) {
                        groupEditDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .background(
                    NavigationLink(isActive: $showAccountSettings) {
                        accountSettingsDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
                .background(
                    NavigationLink(isActive: $showAppSettings) {
                        appSettingsDestination
                    } label: {
                        EmptyView()
                    }
                    .hidden()
                )
```

  - 목적지 3개 추가(`chatRoomDestination` 근처):

```swift
    /// 그룹 정보 수정 — 저장 성공 시 pop+상세·설정 탭 refresh+목록 갱신 신호(Compose GROUP_UPDATED_KEY 미러)
    private var groupEditDestination: some View {
        GroupEditView(groupId: viewModel.groupId, container: container) {
            showGroupEdit = false
            viewModel.onAction(.refresh)
            groupSettingsViewModel.onAction(.refresh)
            onGroupUpdated()
        }
    }

    /// 세션 ProfileViewModel을 넘겨 저장 성공 시 프로필 탭/드로어 헤더가 갱신되게 한다(셸 선례)
    private var accountSettingsDestination: some View {
        AccountSettingsView(container: container, profileViewModel: profileViewModel)
    }

    private var appSettingsDestination: some View {
        SGSettingsView(theme: theme)
    }
```

  - `tabContent`의 설정 탭 분기 교체:

```swift
        default: GroupSettingsTab(
            viewModel: groupSettingsViewModel,
            profile: profileViewModel.uiState.profile,
            onOpenGroupEdit: { showGroupEdit = true },
            onOpenAccountSettings: { showAccountSettings = true },
            onOpenAppSettings: { showAppSettings = true },
            onShareApp: { shareItem = ShareItem(text: Self.appShareText) }
        )
```

  - `.onReceive(groupSettingsViewModel.event)`: `.saved` 분기 삭제(저장 갱신은 groupEditDestination 클로저 경로):

```swift
        // 설정 탭 일회성 이벤트 — 삭제/나가기 성공 시 화면 닫기(Compose 미러)
        .onReceive(groupSettingsViewModel.event) { event in
            switch event {
            case .closed: onGroupClosed()
            }
        }
```

- [ ] **Step 4: MainShellView.swift 배선** — `groupDetailDestination`의 `GroupDetailView(...)` 호출 교체:

```swift
            GroupDetailView(
                groupId: groupId,
                container: container,
                chatViewModel: chatViewModel,
                theme: theme,
                profileViewModel: profileViewModel,
                onGroupClosed: {
                    // 나간/삭제한 그룹이 목록에 남지 않게 — 셸의 그룹 탭이 신호를 소비해 refresh한다(Compose App.kt onGroupClosed 미러)
                    selectedGroupId = nil
                    groupsRefreshPending = true
                },
                // 그룹 정보 수정 저장 — 목록 카드의 이름·커버 갱신(pop 없음, Compose groupsRefreshPending 미러)
                onGroupUpdated: { groupsRefreshPending = true }
            )
```

- [ ] **Step 5: Swift 미러 정합 셀프 체크** — 컴파일 불가라 수동 확인: ①Task 2 Kotlin과 Action/Event/UiState 필드 1:1 ②새 뷰가 쓰는 컴포넌트 시그니처(`SGSectionTitle(text:)`/`SGAvatar(name:size:imageUrl:)`/`ShareItem(text:)`/`AccountSettingsView(container:profileViewModel:)`/`SGSettingsView(theme:)`)가 기존 호출부와 동일 ③`GroupSettingsTab` 신 시그니처의 인자 순서가 tabContent 호출과 일치.

---

### Task 5: 최종 검증 + 스테이징 + 커밋 메시지 전달

**Files:** 신규 파일 없음(검증·정리 전용)

- [ ] **Step 1: Kotlin 3타깃 컴파일**

Run: `cmd.exe /c "set JAVA_HOME=C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1&& cd /d C:\Users\hong2\IntelliJIDEAProjects\StoryGroup\StoryGroup-Android&& gradlew.bat :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinJvm :shared:compileKotlinIosSimulatorArm64"`
Expected: BUILD SUCCESSFUL (⚠️Swift/Mac은 검증 불가 — 결과 보고에 명시)

- [ ] **Step 2: 데스크톱 E2E 스모크** — `gradlew.bat :composeApp:run`(백그라운드)으로:
  ① 그룹 상세 → 설정 탭: 3섹션 메뉴 렌더(OWNER 그룹=정보 수정+삭제 행, 비OWNER=나가기 행, 라운지 비OWNER=그룹 설정 섹션 없음) ② 프로필 행 → 계정 설정 push/복귀 ③ "그룹 정보 수정" → 폼 로드·이름 수정 저장 → pop 복귀+커버 제목 갱신 ④ 삭제/나가기 행 → 확인 다이얼로그(취소·실행) ⑤ "앱 설정" push/복귀 ⑥ 공유하기(Desktop=클립보드 복사 피드백) ⑦ 개인정보처리방침(브라우저 열림).
  종료: PowerShell `Get-Process | Where-Object {$_.MainWindowTitle -eq "StoryGroup"} | Stop-Process`

- [ ] **Step 3: EOL 확인+스테이징** — 이번 작업 파일만 경로 스테이징(기존 스테이징 57파일은 그대로 두고 합류):

```bash
# 수정·신규 파일에 CRLF가 섞였으면 실수정 파일만 정규화(sed -i 's/\r$//' <파일>)
git status --short  # 이번 작업 파일 외 변경이 섞여 있지 않은지 눈으로 확인
git add \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupEditViewModel.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupEditScreen.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupSettingsViewModel.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupSettingsTab.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/screens/group/GroupDetailScreen.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt \
  iosApp/iosApp/UI/Screens/Group/GroupEditViewModel.swift \
  iosApp/iosApp/UI/Screens/Group/GroupEditView.swift \
  iosApp/iosApp/UI/Screens/Group/GroupSettingsViewModel.swift \
  iosApp/iosApp/UI/Screens/Group/GroupSettingsTab.swift \
  iosApp/iosApp/UI/Screens/Group/GroupDetailView.swift \
  iosApp/iosApp/UI/Shell/MainShellView.swift \
  iosApp/iosApp.xcodeproj/project.pbxproj \
  docs/superpowers/specs/2026-08-13-group-settings-tab-menu-design.md \
  docs/superpowers/plans/2026-08-13-group-settings-tab-menu.md
# 스테이징본 CRLF 최종 검사
git diff --cached --name-only | while read f; do git show ":$f" | file - | grep -q CRLF && echo "CRLF! $f"; done
```

- [ ] **Step 4: 커밋 메시지 전달(커밋은 사용자)** — 이전 57파일 작업과 합쳐진 스테이징이므로, 기존 전달분의 ②를 아래로 갱신 제안:

```
② 그룹 상세 일정·설정 탭 구현 + 설정 탭 레거시 미러 메뉴 개편(서버 수정 0)
   - 일정 탭: 웹 캘린더+RSVP
   - 설정 탭: 유저 설정/그룹 설정/어플리케이션 정보 3섹션 메뉴(레거시 item_settings 미러),
     수정 폼은 GroupEditScreen/GroupEditView 풀스크린 분리, 삭제·나가기는 확인 다이얼로그,
     앱 설정을 그룹 상세 위에서도 열게 AppSettingsRoute 신설
```

- [ ] **Step 5: 결과 보고** — 검증 통과 항목/미검증(Swift·Mac, iOS 실기기 push 동작)/실기기 QA 포인트(iOS 15 숨김 NavigationLink 3중 push, .alert 중첩, 개인정보처리방침 외부 브라우저 전환)를 요약해 사용자에게 전달.
