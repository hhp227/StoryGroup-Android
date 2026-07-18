package kr.hhp227.storygroup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kr.hhp227.storygroup.di.AppContainer
import kr.hhp227.storygroup.shared.data.storage.InMemoryTokenStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as StoryGroupApplication).container

        setContent {
            App(container)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(AppContainer(InMemoryTokenStorage()))
}
