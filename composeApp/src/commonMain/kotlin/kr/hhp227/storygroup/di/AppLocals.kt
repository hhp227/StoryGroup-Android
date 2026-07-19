package kr.hhp227.storygroup.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 화면이 default parameter로 자기 ViewModel을 선언(ConCafe 패턴의 Koin GlobalContext 대체)할 수
 * 있게 컨테이너를 컴포지션에 흘린다. 루트 App()이 1회 제공.
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer가 제공되지 않았습니다 — App() 루트에서 CompositionLocalProvider로 감싸야 합니다.")
}

/**
 * 로그인 세션 스코프 ViewModelStore — 로그아웃 시 App이 clear()해서 세션 VM(홈/그룹/프로필)이
 * 일괄 정리된다(iOS에서 로그아웃 시 MainShellView가 통째로 소멸하는 것의 미러).
 * 화면들은 이 스코프에 VM을 선언하므로 "VM 생성 = 세션 진입 1회"가 보장되어
 * init에서 바로 로드를 시작해도 안전하다(별도 세션 진입 Refresh 코디네이션 불필요).
 */
val LocalSessionViewModelStoreOwner = staticCompositionLocalOf<ViewModelStoreOwner> {
    error("세션 스코프가 제공되지 않았습니다 — 로그인 브랜치에서만 사용할 수 있습니다.")
}

/** 세션 스코프에 ViewModel을 선언/조회한다 — 같은 타입·키면 어디서 불러도 동일 인스턴스(공유 상태) */
@Composable
inline fun <reified VM : ViewModel> sessionViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM
): VM {
    val container = LocalAppContainer.current
    return viewModel(viewModelStoreOwner = LocalSessionViewModelStoreOwner.current, key = key) {
        create(container)
    }
}
