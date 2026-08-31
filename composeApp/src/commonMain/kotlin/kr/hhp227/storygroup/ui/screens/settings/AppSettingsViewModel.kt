package kr.hhp227.storygroup.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.PushPreferences
import kr.hhp227.storygroup.shared.domain.usecase.GetPushPreferencesUseCase
import kr.hhp227.storygroup.shared.domain.usecase.UpdatePushPreferencesUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel
import org.jetbrains.compose.resources.getString
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.app_settings_push_error_load
import storygroup.composeapp.generated.resources.app_settings_push_error_save

/**
 * 앱 설정 — 알림(푸시 on/off) 섹션의 상태. 테마는 ThemeState가 그대로 담당하고 이 VM은 서버 저장 설정만 든다.
 * 진입 시 로드, 토글은 낙관적 갱신(즉시 반영 → PUT 실패 시 호출 직전 값으로 롤백+에러 문구).
 * 두 플래그를 항상 함께 보낸다(전체 교체 계약). 계정 단위 설정이라 다른 기기에서 바꾼 값은 다음 진입 로드 때 반영된다.
 * 이벤트 없음(Event = Nothing). iosApp SGSettingsView.swift의 AppSettingsViewModel과 1:1 미러
 */
class AppSettingsViewModel(
    private val getPushPreferencesUseCase: GetPushPreferencesUseCase,
    private val updatePushPreferencesUseCase: UpdatePushPreferencesUseCase
) : ViewModel(), MviViewModel<AppSettingsViewModel.UiState, AppSettingsViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Action) {
        when (action) {
            Action.Load -> load()
            is Action.SetChatEnabled -> save { it.copy(chatEnabled = action.enabled) }
            is Action.SetActivityEnabled -> save { it.copy(activityEnabled = action.enabled) }
        }
    }

    private fun load() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            runCatching { getPushPreferencesUseCase() }
                .onSuccess { prefs ->
                    _uiState.update { it.copy(isLoading = false, pushPreferences = prefs) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, loadError = e.message ?: getString(Res.string.app_settings_push_error_load)) }
                }
        }
    }

    // 낙관적 갱신 — 실패 시 호출 직전 값으로 롤백. 로드 전(null)에는 토글이 비활성이라 도달하지 않는다
    private fun save(transform: (PushPreferences) -> PushPreferences) {
        val previous = _uiState.value.pushPreferences ?: return
        val next = transform(previous)
        _uiState.update { it.copy(pushPreferences = next, saveError = null) }
        viewModelScope.launch {
            runCatching { updatePushPreferencesUseCase(next.chatEnabled, next.activityEnabled) }
                .onFailure { e ->
                    _uiState.update { it.copy(pushPreferences = previous, saveError = e.message ?: getString(Res.string.app_settings_push_error_save)) }
                }
        }
    }

    init {
        load()
    }

    data class UiState(
        // 로드 전 null — 화면은 로딩/에러 행만 그린다
        val pushPreferences: PushPreferences? = null,
        val isLoading: Boolean = false,
        val loadError: String? = null,
        val saveError: String? = null
    )

    sealed interface Action {
        data object Load : Action
        data class SetChatEnabled(val enabled: Boolean) : Action
        data class SetActivityEnabled(val enabled: Boolean) : Action
    }
}
