package kr.hhp227.storygroup

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.hhp227.storygroup.di.AppContainer
import kr.hhp227.storygroup.ui.screens.auth.LoginScreen
import kr.hhp227.storygroup.ui.screens.auth.LoginViewModel
import kr.hhp227.storygroup.ui.screens.auth.RegisterScreen
import kr.hhp227.storygroup.ui.screens.auth.RegisterViewModel
import kr.hhp227.storygroup.ui.screens.home.HomeViewModel
import kr.hhp227.storygroup.ui.screens.profile.ProfileViewModel
import kr.hhp227.storygroup.ui.shell.MainShell
import kr.hhp227.storygroup.ui.theme.NightMode
import kr.hhp227.storygroup.ui.theme.StoryGroupTheme
import kr.hhp227.storygroup.ui.theme.ThemeState

/** 루트 — 테마 적용 후 세션 상태(LoginViewModel)에 따라 인증 플로우/메인 쉘을 라우팅한다 */
@Composable
fun App(container: AppContainer) {
    val themeState = remember { ThemeState(container.settingsStorage) }
    val darkTheme = when (themeState.nightMode) {
        NightMode.SYSTEM -> isSystemInDarkTheme()
        NightMode.LIGHT -> false
        NightMode.DARK -> true
    }

    StoryGroupTheme(mood = themeState.mood, darkTheme = darkTheme) {
        val loginViewModel = viewModel {
            LoginViewModel(container.isLoggedInUseCase, container.loginUseCase, container.logoutUseCase)
        }
        val loginUiState by loginViewModel.uiState.collectAsState()

        if (loginUiState.isLoggedIn) {
            val profileViewModel = viewModel { ProfileViewModel(container.getMyProfileUseCase) }
            val profileUiState by profileViewModel.uiState.collectAsState()
            val homeViewModel = viewModel {
                HomeViewModel(container.getMyGroupsUseCase, container.getGroupPostsUseCase)
            }

            // 로그인 세션 진입 시마다 내 정보/홈 피드 갱신(재로그인 포함)
            LaunchedEffect(Unit) {
                profileViewModel.onAction(ProfileViewModel.Action.Load)
                homeViewModel.onAction(HomeViewModel.Action.Refresh)
            }
            MainShell(
                themeState = themeState,
                profile = profileUiState.profile,
                homeViewModel = homeViewModel,
                onLogout = { loginViewModel.onAction(LoginViewModel.Action.Logout) }
            )
        } else {
            AuthFlow(
                container = container,
                loginViewModel = loginViewModel,
                loginUiState = loginUiState
            )
        }
    }
}

private enum class AuthScreen { LOGIN, REGISTER }

/** 로그인 ↔ 가입 전환 — 가입 성공 시 웹과 동일하게 안내 문구와 함께 로그인으로 복귀 */
@Composable
private fun AuthFlow(
    container: AppContainer,
    loginViewModel: LoginViewModel,
    loginUiState: LoginViewModel.UiState
) {
    var authScreen by remember { mutableStateOf(AuthScreen.LOGIN) }
    var justRegistered by remember { mutableStateOf(false) }

    when (authScreen) {
        AuthScreen.LOGIN -> LoginScreen(
            uiState = loginUiState,
            justRegistered = justRegistered,
            onAction = loginViewModel::onAction,
            onNavigateToRegister = {
                loginViewModel.onAction(LoginViewModel.Action.ClearError)
                justRegistered = false
                authScreen = AuthScreen.REGISTER
            }
        )
        AuthScreen.REGISTER -> {
            val registerViewModel = viewModel { RegisterViewModel(container.registerUseCase) }
            val registerUiState by registerViewModel.uiState.collectAsState()

            // 일회성 이벤트 수집 — 가입 완료 시 안내 문구와 함께 로그인으로 복귀
            LaunchedEffect(registerViewModel) {
                registerViewModel.event.collect { event ->
                    when (event) {
                        RegisterViewModel.Event.Registered -> {
                            justRegistered = true
                            authScreen = AuthScreen.LOGIN
                        }
                    }
                }
            }
            RegisterScreen(
                uiState = registerUiState,
                onAction = registerViewModel::onAction,
                onNavigateToLogin = {
                    registerViewModel.onAction(RegisterViewModel.Action.ClearError)
                    authScreen = AuthScreen.LOGIN
                }
            )
        }
    }
}
