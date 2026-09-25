package kr.hhp227.storygroup.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.usecase.IsLoggedInUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LoginUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LoginWithGoogleUseCase
import kr.hhp227.storygroup.shared.domain.usecase.LogoutUseCase
import kr.hhp227.storygroup.ui.auth.GoogleCredential
import kr.hhp227.storygroup.ui.mvi.MviViewModel
import org.jetbrains.compose.resources.getString
import storygroup.composeapp.generated.resources.Res
import storygroup.composeapp.generated.resources.login_error
import storygroup.composeapp.generated.resources.login_google_error

/** 세션 홀더 — iosApp LoginViewModel.swift와 1:1 미러(같은 UiState·Action·로직, shared 유스케이스 소비) */
class LoginViewModel(
    isLoggedInUseCase: IsLoggedInUseCase,
    private val loginUseCase: LoginUseCase,
    private val loginWithGoogleUseCase: LoginWithGoogleUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel(), MviViewModel<LoginViewModel.UiState, LoginViewModel.Action, Nothing> {
    private val _uiState = MutableStateFlow(UiState(isLoggedIn = isLoggedInUseCase()))
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // 세션 전환은 일회성이 아니라 상태(isLoggedIn) — 이벤트 없음
    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Action) {
        when (action) {
            is Action.Login -> login(action.email, action.password)
            is Action.GoogleLogin -> googleLogin(action.credential)
            is Action.GoogleLoginFailed -> viewModelScope.launch {
                _uiState.update { it.copy(error = action.message ?: getString(Res.string.login_google_error)) }
            }
            Action.Logout -> logout()
            Action.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun login(email: String, password: String) {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { loginUseCase(email, password) }
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: getString(Res.string.login_error)) }
                }
        }
    }

    // 자격 증명 획득(플랫폼 UI)은 화면 몫 — VM은 받은 값을 서버 로그인으로만 잇는다(플랫폼 무관)
    private fun googleLogin(credential: GoogleCredential) {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                when (credential) {
                    is GoogleCredential.IdToken -> loginWithGoogleUseCase.withIdToken(credential.idToken)
                    is GoogleCredential.AuthCode ->
                        loginWithGoogleUseCase.withAuthCode(credential.code, credential.codeVerifier, credential.redirectUri)
                }
            }.onSuccess {
                _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: getString(Res.string.login_google_error)) }
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _uiState.update { it.copy(isLoggedIn = false) }
        }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val isLoggedIn: Boolean = false,
        val error: String? = null
    )

    sealed interface Action {
        data class Login(val email: String, val password: String) : Action
        data class GoogleLogin(val credential: GoogleCredential) : Action
        /** 런처(플랫폼 UI) 자체가 실패한 경우 — 취소는 여기로 오지 않는다 */
        data class GoogleLoginFailed(val message: String?) : Action
        data object Logout : Action
        data object ClearError : Action
    }
}
