package kr.hhp227.storygroup

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import kr.hhp227.storygroup.shared.di.AppContainer
import kr.hhp227.storygroup.shared.data.source.JvmNetworkStatusDataSource
import kr.hhp227.storygroup.shared.data.storage.FileTokenStorage
import kr.hhp227.storygroup.shared.data.storage.FileKeyValueStorage
import kr.hhp227.storygroup.ui.DesktopLaunchScreen
import kr.hhp227.storygroup.ui.theme.StoryGroupTheme
import kr.hhp227.storygroup.ui.util.JvmImageCompressor

fun main() {
    val container = AppContainer(
        tokenStorage = FileTokenStorage(),
        settingsStorage = FileKeyValueStorage(),
        imageCompressor = JvmImageCompressor(),
        networkStatusDataSource = JvmNetworkStatusDataSource()
    )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "StoryGroup",
            state = rememberWindowState(width = 1100.dp, height = 800.dp)
        ) {
            // 데스크톱 스플래시(ConCafe App.kt 미러) — 잠깐 브랜드 화면을 보여준 뒤 본 화면으로 전환.
            // 스플래시는 설정 테마가 로드되기 전이라 기본 무드+시스템 다크로 그린다
            var isLaunchScreenVisible by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(800)
                isLaunchScreenVisible = false
            }
            if (isLaunchScreenVisible) {
                StoryGroupTheme { DesktopLaunchScreen() }
            } else {
                App(container)
            }
        }
    }
}
