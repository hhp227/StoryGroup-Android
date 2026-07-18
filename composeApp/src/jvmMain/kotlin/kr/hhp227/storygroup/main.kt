package kr.hhp227.storygroup

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kr.hhp227.storygroup.di.AppContainer
import kr.hhp227.storygroup.shared.data.storage.FileTokenStorage
import kr.hhp227.storygroup.shared.data.storage.FileKeyValueStorage

fun main() {
    val container = AppContainer(
        tokenStorage = FileTokenStorage(),
        settingsStorage = FileKeyValueStorage()
    )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "StoryGroup",
            state = rememberWindowState(width = 1100.dp, height = 800.dp)
        ) {
            App(container)
        }
    }
}
