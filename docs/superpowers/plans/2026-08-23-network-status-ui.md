# 인터넷 미접속 알림 UI 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 인터넷이 끊기면 앱 전역(로그인 포함) 상단에 배너를 띄우고 복구 시 1.8초 안내 후 숨긴다 — Android/Desktop/iOS 3타깃.

**Architecture:** ConCafe의 NetworkStatus 스택(도메인 모델→Repository→UseCase→루트 배너)을 StoryGroup 관례로 이식한다. Koin 대신 진입점 생성자 주입(imageCompressor 관례), VM은 composeApp Kotlin ↔ iosApp Swift 1:1 미러. 스펙: `docs/superpowers/specs/2026-08-23-network-status-ui-design.md`(부모 StoryGroup 리포).

**Tech Stack:** Kotlin Multiplatform, kotlinx-coroutines(Flow), Compose Multiplatform(M2), SwiftUI+Combine, kotlinx-coroutines-test.

## Global Constraints

- **리포**: `/mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android`, 브랜치 `feature/internet`. 모든 경로는 리포 루트 기준.
- **빌드는 Windows gradlew.bat로**: WSL gradle은 EIO로 죽는다. 항상 `cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android && cmd.exe /c "gradlew.bat <task>"`.
- **커밋 금지**: 이 리포는 스테이징+커밋 메시지 기록까지만 — 커밋·push는 사용자가 직접 한다. `git commit` 절대 실행하지 말 것.
- **스테이징 규칙**: `git add -A` 금지. 워킹트리가 전면 CRLF(377개 파일이 EOL 노이즈로 M 표시)이므로, **기존 파일을 수정한 경우 스테이징 전 `sed -i 's/\r$//' <파일>`로 LF 정규화** 후 `git diff --stat <파일>`로 실변경 라인만 남았는지 확인하고 해당 경로만 add. 새로 만든 파일(Write 도구)은 LF라 정규화 불필요.
- **주의**: `composeApp/di/AppContainer.kt`·`StoryGroupApplication.kt`·`main.kt`에는 미커밋 미디어 압축 작업분이 이미 들어 있다(imageCompressor). 파일 단위 스테이징이라 두 기능 변경이 함께 스테이징되는 것은 알려진 사실 — 커밋 메시지 기록에 명시한다.
- **Swift는 컴파일 검증 불가**(Mac 없음) — 문법 자체 점검만 하고 그 사실을 기록한다. shared iosMain Kotlin은 `:shared:compileKotlinIosArm64`로 검증 가능하다.
- **패키지**: shared = `kr.hhp227.storygroup.shared.*`, composeApp = `kr.hhp227.storygroup.*`.
- **문구는 verbatim**: 오프라인 `"인터넷 연결이 원활하지 않습니다."`, 복구 `"인터넷 연결이 복구되었습니다."` (ConCafe와 동일).
- 각 태스크 끝의 "스테이징+메시지 기록" 단계에서, 제안 커밋 메시지를 이 계획 파일 하단 "커밋 메시지 기록" 섹션에 누적한다.

---

### Task 1: shared 코어 — 모델·인터페이스·Repository 구현

**Files:**
- Modify: `shared/build.gradle.kts` (commonTest에 coroutines-test 추가, 56행 근처 `commonTest.dependencies`)
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/NetworkAlertState.kt`
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/NetworkStatusRepository.kt`
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/source/NetworkStatusDataSource.kt`
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/NetworkStatusRepositoryImpl.kt`
- Test: `shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/NetworkStatusRepositoryImplTest.kt`

**Interfaces:**
- Produces: `NetworkAlertState`(data class, companion `hidden`/`offline`/`recovered`, `offlineMessage`/`onlineRecoveredMessage`), `NetworkStatusRepository.observeIsConnected(): Flow<Boolean>`, `NetworkStatusDataSource.observeIsConnected(): Flow<Boolean>`, `NetworkStatusRepositoryImpl(networkStatusDataSource: NetworkStatusDataSource?)`

- [ ] **Step 1: 테스트 의존성 추가**

`shared/build.gradle.kts`의 `commonTest.dependencies` 블록(현재 `implementation(libs.kotlin.test)`만 있음)에 한 줄 추가:

```kotlin
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // runTest 가상 시간으로 배너 상태 시퀀스를 검증한다
            implementation(libs.kotlinx.coroutines.test)
        }
```

(`libs.kotlinx.coroutines.test`는 `gradle/libs.versions.toml:40`에 이미 정의돼 있음 — toml 수정 불필요)

- [ ] **Step 2: 실패하는 테스트 작성**

`shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/NetworkStatusRepositoryImplTest.kt`:

```kotlin
package kr.hhp227.storygroup.shared

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kr.hhp227.storygroup.shared.data.repository.NetworkStatusRepositoryImpl
import kr.hhp227.storygroup.shared.data.source.NetworkStatusDataSource
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkStatusRepositoryImplTest {
    @Test
    fun nullDataSourceIsAlwaysConnected() = runTest {
        val repository = NetworkStatusRepositoryImpl(null)

        assertEquals(listOf(true), repository.observeIsConnected().toList())
    }

    @Test
    fun delegatesToDataSource() = runTest {
        val repository = NetworkStatusRepositoryImpl(object : NetworkStatusDataSource {
            override fun observeIsConnected(): Flow<Boolean> = flowOf(false)
        })

        assertEquals(listOf(false), repository.observeIsConnected().toList())
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `cd /mnt/c/Users/hong2/IntelliJIDEAProjects/StoryGroup/StoryGroup-Android && cmd.exe /c "gradlew.bat :shared:jvmTest --tests kr.hhp227.storygroup.shared.NetworkStatusRepositoryImplTest"`
Expected: FAIL — `NetworkStatusRepositoryImpl` / `NetworkStatusDataSource` unresolved (컴파일 에러도 실패로 간주)

- [ ] **Step 4: 구현 4파일 작성**

`shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/NetworkAlertState.kt`:

```kotlin
package kr.hhp227.storygroup.shared.domain.model

/** 네트워크 연결 알림 배너 상태 — Kotlin·Swift UI가 같은 문구를 공유한다(ConCafe 미러) */
data class NetworkAlertState(
    val isVisible: Boolean,
    val isConnected: Boolean,
    val message: String
) {
    companion object {
        const val offlineMessage = "인터넷 연결이 원활하지 않습니다."
        const val onlineRecoveredMessage = "인터넷 연결이 복구되었습니다."

        val hidden = NetworkAlertState(isVisible = false, isConnected = true, message = "")
        val offline = NetworkAlertState(isVisible = true, isConnected = false, message = offlineMessage)
        val recovered = NetworkAlertState(isVisible = true, isConnected = true, message = onlineRecoveredMessage)
    }
}
```

`shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/NetworkStatusRepository.kt`:

```kotlin
package kr.hhp227.storygroup.shared.domain.repository

import kotlinx.coroutines.flow.Flow

/** OS 수준 인터넷 연결 상태 스트림 — 개별 요청 실패와 무관하게 연결 여부만 관찰한다 */
interface NetworkStatusRepository {
    fun observeIsConnected(): Flow<Boolean>
}
```

`shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/source/NetworkStatusDataSource.kt`:

```kotlin
package kr.hhp227.storygroup.shared.data.source

import kotlinx.coroutines.flow.Flow

/**
 * 플랫폼 연결 감지 소스 — 구현은 플랫폼 소스셋(Android=ConnectivityManager,
 * iOS=SCNetworkReachability, Desktop=소켓 폴링)에 있고, 진입점이 컨테이너에 주입한다.
 */
interface NetworkStatusDataSource {
    fun observeIsConnected(): Flow<Boolean>
}
```

`shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/NetworkStatusRepositoryImpl.kt`:

```kotlin
package kr.hhp227.storygroup.shared.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kr.hhp227.storygroup.shared.data.source.NetworkStatusDataSource
import kr.hhp227.storygroup.shared.domain.repository.NetworkStatusRepository

/** 데이터소스 null이면 항상 온라인(배너 숨김) — Preview·미주입 폴백(imageCompressor null=무압축 관례) */
class NetworkStatusRepositoryImpl(
    private val networkStatusDataSource: NetworkStatusDataSource?
) : NetworkStatusRepository {
    override fun observeIsConnected(): Flow<Boolean> {
        return networkStatusDataSource?.observeIsConnected() ?: flowOf(true)
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cmd.exe /c "gradlew.bat :shared:jvmTest --tests kr.hhp227.storygroup.shared.NetworkStatusRepositoryImplTest"`
Expected: PASS (2 tests)

- [ ] **Step 6: 스테이징+메시지 기록**

```bash
sed -i 's/\r$//' shared/build.gradle.kts
git diff --stat shared/build.gradle.kts   # 실변경 라인(±2줄 안팎)만 남았는지 확인
git add shared/build.gradle.kts \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/model/NetworkAlertState.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/repository/NetworkStatusRepository.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/source/NetworkStatusDataSource.kt \
  shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/data/repository/NetworkStatusRepositoryImpl.kt \
  shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/NetworkStatusRepositoryImplTest.kt
```

⚠️ `shared/build.gradle.kts`에 미디어 압축 미커밋 변경이 이미 있으면 함께 스테이징됨 — 기록에 명시.

---

### Task 2: ObserveNetworkAlertStateUseCase — 배너 상태 매핑

**Files:**
- Create: `shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/ObserveNetworkAlertStateUseCase.kt`
- Test: `shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/ObserveNetworkAlertStateUseCaseTest.kt`

**Interfaces:**
- Consumes: Task 1의 `NetworkStatusRepository`, `NetworkAlertState`
- Produces: `ObserveNetworkAlertStateUseCase(networkStatusRepository: NetworkStatusRepository)` / `operator fun invoke(): Flow<NetworkAlertState>`

- [ ] **Step 1: 실패하는 테스트 작성**

`shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/ObserveNetworkAlertStateUseCaseTest.kt`:

```kotlin
package kr.hhp227.storygroup.shared

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kr.hhp227.storygroup.shared.domain.model.NetworkAlertState
import kr.hhp227.storygroup.shared.domain.repository.NetworkStatusRepository
import kr.hhp227.storygroup.shared.domain.usecase.ObserveNetworkAlertStateUseCase
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveNetworkAlertStateUseCaseTest {
    private val connection = MutableSharedFlow<Boolean>()

    private val useCase = ObserveNetworkAlertStateUseCase(object : NetworkStatusRepository {
        override fun observeIsConnected(): Flow<Boolean> = connection
    })

    @Test
    fun firstOnlineEmitsHidden() = runTest {
        val states = mutableListOf<NetworkAlertState>()

        backgroundScope.launch { useCase().collect { states += it } }
        runCurrent()
        connection.emit(true)
        runCurrent()

        assertEquals(listOf(NetworkAlertState.hidden), states)
    }

    @Test
    fun offlineEmitsOfflineOnce() = runTest {
        val states = mutableListOf<NetworkAlertState>()

        backgroundScope.launch { useCase().collect { states += it } }
        runCurrent()
        connection.emit(false)
        runCurrent()
        // distinctUntilChanged — 같은 값 반복은 무시된다
        connection.emit(false)
        runCurrent()

        assertEquals(listOf(NetworkAlertState.offline), states)
    }

    @Test
    fun recoveryShowsRecoveredThenHidesAfterDelay() = runTest {
        val states = mutableListOf<NetworkAlertState>()

        backgroundScope.launch { useCase().collect { states += it } }
        runCurrent()
        connection.emit(false)
        runCurrent()
        connection.emit(true)
        runCurrent()

        assertEquals(listOf(NetworkAlertState.offline, NetworkAlertState.recovered), states)

        advanceTimeBy(1_800)
        runCurrent()

        assertEquals(
            listOf(NetworkAlertState.offline, NetworkAlertState.recovered, NetworkAlertState.hidden),
            states
        )
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cmd.exe /c "gradlew.bat :shared:jvmTest --tests kr.hhp227.storygroup.shared.ObserveNetworkAlertStateUseCaseTest"`
Expected: FAIL — `ObserveNetworkAlertStateUseCase` unresolved

- [ ] **Step 3: 구현**

`shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/ObserveNetworkAlertStateUseCase.kt`:

```kotlin
package kr.hhp227.storygroup.shared.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kr.hhp227.storygroup.shared.domain.model.NetworkAlertState
import kr.hhp227.storygroup.shared.domain.repository.NetworkStatusRepository

/**
 * 연결 스트림 → 배너 상태 매핑(ConCafe 이식): 오프라인=지속 표시, 복구=1.8초 표시 후 숨김.
 * 복구 표시 중(delay) 재끊김은 delay 뒤로 밀린다 — ConCafe와 동일한 알려진 특성(스펙 §6).
 */
class ObserveNetworkAlertStateUseCase(
    private val networkStatusRepository: NetworkStatusRepository
) {
    operator fun invoke(): Flow<NetworkAlertState> {
        return flow {
            var previousIsConnected: Boolean? = null

            networkStatusRepository.observeIsConnected()
                .distinctUntilChanged()
                .collect { isConnected ->
                    if (isConnected) {
                        if (previousIsConnected == false) {
                            emit(NetworkAlertState.recovered)
                            delay(RECOVERED_MESSAGE_DURATION_MS)
                            emit(NetworkAlertState.hidden)
                        } else {
                            emit(NetworkAlertState.hidden)
                        }
                    } else {
                        emit(NetworkAlertState.offline)
                    }
                    previousIsConnected = isConnected
                }
        }
    }

    private companion object {
        const val RECOVERED_MESSAGE_DURATION_MS = 1_800L
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cmd.exe /c "gradlew.bat :shared:jvmTest --tests kr.hhp227.storygroup.shared.ObserveNetworkAlertStateUseCaseTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: 스테이징+메시지 기록**

```bash
git add shared/src/commonMain/kotlin/kr/hhp227/storygroup/shared/domain/usecase/ObserveNetworkAlertStateUseCase.kt \
  shared/src/commonTest/kotlin/kr/hhp227/storygroup/shared/ObserveNetworkAlertStateUseCaseTest.kt
```

---

### Task 3: 플랫폼 데이터소스 3종 + Android 권한

**Files:**
- Create: `shared/src/androidMain/kotlin/kr/hhp227/storygroup/shared/data/source/AndroidNetworkStatusDataSource.kt`
- Create: `shared/src/iosMain/kotlin/kr/hhp227/storygroup/shared/data/source/IosNetworkStatusDataSource.kt`
- Create: `shared/src/jvmMain/kotlin/kr/hhp227/storygroup/shared/data/source/JvmNetworkStatusDataSource.kt`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml` (4행 INTERNET 아래에 권한 1줄)

**Interfaces:**
- Consumes: Task 1의 `NetworkStatusDataSource`
- Produces: `AndroidNetworkStatusDataSource(context: Context)`, `IosNetworkStatusDataSource()`, `JvmNetworkStatusDataSource()` — 전부 `NetworkStatusDataSource` 구현

폴링·소켓 로직은 실기기 없이 단위 테스트가 안 되므로(OS API·실네트워크 의존) 이 태스크는 타깃별 컴파일 검증으로 갈음한다.

- [ ] **Step 1: Android 구현 (ConCafe androidMain 이식, Koin→생성자 주입)**

`shared/src/androidMain/kotlin/kr/hhp227/storygroup/shared/data/source/AndroidNetworkStatusDataSource.kt`:

```kotlin
package kr.hhp227.storygroup.shared.data.source

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android 연결 감지 — 기본 네트워크 콜백(ConCafe 이식, Koin 대신 생성자 Context 주입).
 * Doze·화면 잠금 복귀 시 콜백 누락 대비 5초 폴링 보완. INTERNET+VALIDATED 둘 다 있어야 온라인.
 */
class AndroidNetworkStatusDataSource(context: Context) : NetworkStatusDataSource {
    private val appContext = context.applicationContext

    override fun observeIsConnected(): Flow<Boolean> {
        return callbackFlow {
            val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(connectivityManager.isCurrentlyConnected())
                }

                override fun onLost(network: Network) {
                    trySend(connectivityManager.isCurrentlyConnected())
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    trySend(connectivityManager.isCurrentlyConnected())
                }

                override fun onUnavailable() {
                    trySend(false)
                }
            }

            trySend(connectivityManager.isCurrentlyConnected())
            connectivityManager.registerDefaultNetworkCallback(callback)

            val pollingJob = launch {
                while (isActive) {
                    delay(POLL_INTERVAL_MS)
                    trySend(connectivityManager.isCurrentlyConnected())
                }
            }

            awaitClose {
                pollingJob.cancel()
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.distinctUntilChanged()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}

private fun ConnectivityManager.isCurrentlyConnected(): Boolean {
    val capabilities = getNetworkCapabilities(activeNetwork)
    val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val hasValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    return hasInternet && hasValidated
}
```

- [ ] **Step 2: Android 매니페스트 권한 추가**

`composeApp/src/androidMain/AndroidManifest.xml` 4행 `<uses-permission android:name="android.permission.INTERNET" />` 바로 아래에 추가:

```xml
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

- [ ] **Step 3: iOS 구현 (ConCafe iosMain 이식 + CFRelease 누수 보완)**

`shared/src/iosMain/kotlin/kr/hhp227/storygroup/shared/data/source/IosNetworkStatusDataSource.kt`:

```kotlin
package kr.hhp227.storygroup.shared.data.source

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import platform.CoreFoundation.CFRelease
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithName
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsConnectionRequired
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable

/** iOS 연결 감지 — SCNetworkReachability 2초 폴링(ConCafe 이식 — 검증된 코드라 NWPathMonitor 미채택) */
class IosNetworkStatusDataSource : NetworkStatusDataSource {
    override fun observeIsConnected(): Flow<Boolean> {
        return flow {
            while (currentCoroutineContext().isActive) {
                emit(isCurrentlyConnected())
                delay(POLL_INTERVAL_MS)
            }
        }.distinctUntilChanged()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun isCurrentlyConnected(): Boolean {
        // Create-rule CF 객체 — K/N은 자동 해제하지 않으므로 폴링마다 직접 release한다
        val reachability = SCNetworkReachabilityCreateWithName(null, REACHABILITY_HOST) ?: return false

        try {
            val flagsHolder = UIntArray(1)
            val didGetFlags = flagsHolder.usePinned { pinned ->
                SCNetworkReachabilityGetFlags(reachability, pinned.addressOf(0))
            }
            if (!didGetFlags) return false

            val flags = flagsHolder[0].toULong()
            val isReachable = (flags and kSCNetworkReachabilityFlagsReachable.toULong()) != 0uL
            val requiresConnection = (flags and kSCNetworkReachabilityFlagsConnectionRequired.toULong()) != 0uL
            return isReachable && !requiresConnection
        } finally {
            CFRelease(reachability)
        }
    }

    private companion object {
        const val REACHABILITY_HOST = "www.apple.com"
        const val POLL_INTERVAL_MS = 2_000L
    }
}
```

(만약 Step 6의 iosArm64 컴파일에서 `CFRelease(reachability)` 타입 불일치가 나면 ConCafe 원본대로 CFRelease 없이 가고 그 사실을 기록한다 — 기능엔 지장 없음)

- [ ] **Step 4: Desktop(jvm) 구현 (신규 — 실제 감지)**

`shared/src/jvmMain/kotlin/kr/hhp227/storygroup/shared/data/source/JvmNetworkStatusDataSource.kt`:

```kotlin
package kr.hhp227.storygroup.shared.data.source

import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Desktop 연결 감지 — OS 콜백 API가 없어 공용 DNS(1.1.1.1→8.8.8.8 폴백)로 TCP 연결을 5초 폴링한다
 * (ConCafe jvm은 항상 온라인 스텁이었음 — 여기선 실제 감지). 소켓 예외는 오프라인 판정으로 흡수.
 */
class JvmNetworkStatusDataSource : NetworkStatusDataSource {
    override fun observeIsConnected(): Flow<Boolean> {
        return flow {
            while (currentCoroutineContext().isActive) {
                emit(isCurrentlyConnected())
                delay(POLL_INTERVAL_MS)
            }
        }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
    }

    private fun isCurrentlyConnected(): Boolean {
        return PROBE_ADDRESSES.any { (host, port) -> canConnect(host, port) }
    }

    private fun canConnect(host: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        val PROBE_ADDRESSES = listOf("1.1.1.1" to 53, "8.8.8.8" to 53)
        const val CONNECT_TIMEOUT_MS = 1_500
        const val POLL_INTERVAL_MS = 5_000L
    }
}
```

- [ ] **Step 5: Android·jvm 컴파일 검증**

Run: `cmd.exe /c "gradlew.bat :shared:compileDebugKotlinAndroid :shared:compileKotlinJvm"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: iOS Kotlin 컴파일 검증**

Run: `cmd.exe /c "gradlew.bat :shared:compileKotlinIosArm64"`
Expected: BUILD SUCCESSFUL (klib 컴파일은 Windows/WSL에서도 가능 — 링킹만 Mac 필요)

- [ ] **Step 7: 스테이징+메시지 기록**

```bash
sed -i 's/\r$//' composeApp/src/androidMain/AndroidManifest.xml
git diff --stat composeApp/src/androidMain/AndroidManifest.xml   # +1줄만인지 확인
git add shared/src/androidMain/kotlin/kr/hhp227/storygroup/shared/data/source/AndroidNetworkStatusDataSource.kt \
  shared/src/iosMain/kotlin/kr/hhp227/storygroup/shared/data/source/IosNetworkStatusDataSource.kt \
  shared/src/jvmMain/kotlin/kr/hhp227/storygroup/shared/data/source/JvmNetworkStatusDataSource.kt \
  composeApp/src/androidMain/AndroidManifest.xml
```

---

### Task 4: composeApp 배선 — AppContainer + 진입점 주입

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt` (생성자 120-125행, repo 블록 137행 뒤, use case 마지막 234행 뒤)
- Modify: `composeApp/src/androidMain/kotlin/kr/hhp227/storygroup/StoryGroupApplication.kt`
- Modify: `composeApp/src/jvmMain/kotlin/kr/hhp227/storygroup/main.kt`

**Interfaces:**
- Consumes: Task 1~3의 `NetworkStatusDataSource`, `NetworkStatusRepositoryImpl`, `ObserveNetworkAlertStateUseCase`, `AndroidNetworkStatusDataSource`, `JvmNetworkStatusDataSource`
- Produces: `AppContainer.observeNetworkAlertStateUseCase: ObserveNetworkAlertStateUseCase` (public val), 생성자 파라미터 `networkStatusDataSource: NetworkStatusDataSource? = null`

- [ ] **Step 1: AppContainer.kt 수정**

imports에 추가 (기존 알파벳 정렬 위치에):

```kotlin
import kr.hhp227.storygroup.shared.data.repository.NetworkStatusRepositoryImpl
import kr.hhp227.storygroup.shared.data.source.NetworkStatusDataSource
import kr.hhp227.storygroup.shared.domain.repository.NetworkStatusRepository
import kr.hhp227.storygroup.shared.domain.usecase.ObserveNetworkAlertStateUseCase
```

생성자 — `imageCompressor: ImageCompressor? = null` 뒤에 파라미터 추가:

```kotlin
    imageCompressor: ImageCompressor? = null,
    // 인터넷 연결 감지 — 플랫폼 진입점이 주입, 프리뷰는 null(항상 온라인 취급 — 배너 숨김)
    networkStatusDataSource: NetworkStatusDataSource? = null
```

repository 블록 — `private val searchRepository ...` 줄 아래에 추가:

```kotlin
    private val networkStatusRepository: NetworkStatusRepository = NetworkStatusRepositoryImpl(networkStatusDataSource)
```

use case 블록 — 마지막 `val searchUseCase = ...` 줄 아래에 추가:

```kotlin
    // 인터넷 연결 배너 — 앱 루트(App.kt)가 구독한다. iosApp AppContainer.swift 미러
    val observeNetworkAlertStateUseCase = ObserveNetworkAlertStateUseCase(networkStatusRepository)
```

- [ ] **Step 2: StoryGroupApplication.kt 수정**

import 추가: `import kr.hhp227.storygroup.shared.data.source.AndroidNetworkStatusDataSource`

`container = AppContainer(...)` 호출에 인자 추가:

```kotlin
        container = AppContainer(
            tokenStorage = SharedPreferencesTokenStorage(this),
            settingsStorage = SharedPreferencesKeyValueStorage(this),
            imageCompressor = AndroidImageCompressor(this),
            networkStatusDataSource = AndroidNetworkStatusDataSource(this)
        )
```

- [ ] **Step 3: main.kt 수정**

import 추가: `import kr.hhp227.storygroup.shared.data.source.JvmNetworkStatusDataSource`

`val container = AppContainer(...)` 호출에 인자 추가:

```kotlin
    val container = AppContainer(
        tokenStorage = FileTokenStorage(),
        settingsStorage = FileKeyValueStorage(),
        imageCompressor = JvmImageCompressor(),
        networkStatusDataSource = JvmNetworkStatusDataSource()
    )
```

- [ ] **Step 4: 컴파일 검증**

Run: `cmd.exe /c "gradlew.bat :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinJvm"`
Expected: BUILD SUCCESSFUL (MainActivity Preview는 기본값 null이라 무수정)

- [ ] **Step 5: 스테이징+메시지 기록**

```bash
sed -i 's/\r$//' composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt \
  composeApp/src/androidMain/kotlin/kr/hhp227/storygroup/StoryGroupApplication.kt \
  composeApp/src/jvmMain/kotlin/kr/hhp227/storygroup/main.kt
git diff --stat composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt   # 실변경만인지 확인
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/di/AppContainer.kt \
  composeApp/src/androidMain/kotlin/kr/hhp227/storygroup/StoryGroupApplication.kt \
  composeApp/src/jvmMain/kotlin/kr/hhp227/storygroup/main.kt
```

⚠️ 세 파일 모두 미디어 압축 미커밋 변경(imageCompressor)이 함께 스테이징됨 — 기록에 명시.

---

### Task 5: NetworkStatusViewModel (composeApp)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/components/NetworkStatusViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/kr/hhp227/storygroup/ui/components/NetworkStatusViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2의 `ObserveNetworkAlertStateUseCase`
- Produces: `NetworkStatusViewModel(observeNetworkAlertStateUseCase)` — `uiState: StateFlow<UiState>`, `UiState(val networkAlertState: NetworkAlertState = NetworkAlertState.hidden)`

- [ ] **Step 1: 실패하는 테스트 작성**

`composeApp/src/commonTest/kotlin/kr/hhp227/storygroup/ui/components/NetworkStatusViewModelTest.kt` (coroutines-test는 composeApp commonTest에 이미 있음 — `composeApp/build.gradle.kts:68`):

```kotlin
package kr.hhp227.storygroup.ui.components

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import kr.hhp227.storygroup.shared.domain.model.NetworkAlertState
import kr.hhp227.storygroup.shared.domain.repository.NetworkStatusRepository
import kr.hhp227.storygroup.shared.domain.usecase.ObserveNetworkAlertStateUseCase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkStatusViewModelTest {
    private val connection = MutableSharedFlow<Boolean>()

    private val useCase = ObserveNetworkAlertStateUseCase(object : NetworkStatusRepository {
        override fun observeIsConnected(): Flow<Boolean> = connection
    })

    /** viewModelScope가 Main을 쓰므로 테스트 디스패처로 치환한다 */
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startsHidden() = runTest {
        val viewModel = NetworkStatusViewModel(useCase)

        assertEquals(NetworkAlertState.hidden, viewModel.uiState.value.networkAlertState)
    }

    @Test
    fun offlineUpdatesUiState() = runTest {
        val viewModel = NetworkStatusViewModel(useCase)

        yield()
        connection.emit(false)
        yield()

        assertEquals(NetworkAlertState.offline, viewModel.uiState.value.networkAlertState)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cmd.exe /c "gradlew.bat :composeApp:jvmTest --tests kr.hhp227.storygroup.ui.components.NetworkStatusViewModelTest"`
Expected: FAIL — `NetworkStatusViewModel` unresolved

- [ ] **Step 3: 구현**

`composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/components/NetworkStatusViewModel.kt`:

```kotlin
package kr.hhp227.storygroup.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kr.hhp227.storygroup.shared.domain.model.NetworkAlertState
import kr.hhp227.storygroup.shared.domain.usecase.ObserveNetworkAlertStateUseCase
import kr.hhp227.storygroup.ui.mvi.MviViewModel

/**
 * 네트워크 연결 배너 상태 — 로그인 화면 포함 전역이라 세션이 아닌 앱 루트 스코프(App.kt viewModel {}).
 * 사용자 액션·일회성 이벤트가 없어 ACTION/EVENT 모두 Nothing.
 * iosApp NetworkStatusViewModel.swift와 1:1 미러
 */
class NetworkStatusViewModel(
    observeNetworkAlertStateUseCase: ObserveNetworkAlertStateUseCase
) : ViewModel(), MviViewModel<NetworkStatusViewModel.UiState, Nothing, Nothing> {
    private val _uiState = MutableStateFlow(UiState())
    override val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    override val event: Flow<Nothing> = emptyFlow()

    override fun onAction(action: Nothing) = Unit

    init {
        // 구독 수명 = 앱 루트 VM 수명 — 프로세스가 살아 있는 동안 감지가 유지된다
        observeNetworkAlertStateUseCase()
            .onEach { state -> _uiState.update { it.copy(networkAlertState = state) } }
            .launchIn(viewModelScope)
    }

    data class UiState(
        val networkAlertState: NetworkAlertState = NetworkAlertState.hidden
    )
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cmd.exe /c "gradlew.bat :composeApp:jvmTest --tests kr.hhp227.storygroup.ui.components.NetworkStatusViewModelTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: 스테이징+메시지 기록**

```bash
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/components/NetworkStatusViewModel.kt \
  composeApp/src/commonTest/kotlin/kr/hhp227/storygroup/ui/components/NetworkStatusViewModelTest.kt
```

---

### Task 6: NetworkStatusBanner 컴포저블 + App.kt 오버레이

**Files:**
- Create: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/components/NetworkStatusBanner.kt`
- Modify: `composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt` (imports + 66-81행의 로그인 게이트)

**Interfaces:**
- Consumes: Task 5의 `NetworkStatusViewModel`, Task 1의 `NetworkAlertState`
- Produces: `@Composable fun NetworkStatusBanner(networkAlertState: NetworkAlertState, modifier: Modifier = Modifier)`

- [ ] **Step 1: 배너 컴포저블 작성 (ConCafe 이식, SgTheme 토큰)**

`composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/components/NetworkStatusBanner.kt` — 이 프로젝트는 Material2(`androidx.compose.material`)를 쓴다(IncomingCallBanner와 동일):

```kotlin
package kr.hhp227.storygroup.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kr.hhp227.storygroup.shared.domain.model.NetworkAlertState
import kr.hhp227.storygroup.ui.theme.SgTheme

/** 네트워크 연결 배너 — 오프라인=rust, 복구=moss. iosApp NetworkStatusBannerView.swift와 1:1 미러 */
@Composable
fun NetworkStatusBanner(
    networkAlertState: NetworkAlertState,
    modifier: Modifier = Modifier
) {
    val sg = SgTheme.colors
    val tint = if (networkAlertState.isConnected) sg.moss else sg.rust

    AnimatedVisibility(
        visible = networkAlertState.isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.statusBarsPadding()
    ) {
        Surface(
            // 반투명 틴트를 paper 위에 합성 — 어떤 화면 위에서도 불투명하게 보인다
            color = tint.copy(alpha = 0.14f).compositeOver(sg.paper),
            contentColor = tint,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = networkAlertState.message,
                style = SgTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}
```

- [ ] **Step 2: App.kt에 오버레이 배치**

imports 추가:

```kotlin
import kr.hhp227.storygroup.ui.components.NetworkStatusBanner
import kr.hhp227.storygroup.ui.components.NetworkStatusViewModel
```

`App()` 안의 로그인 게이트 블록을 다음으로 교체 — 기존:

```kotlin
            if (loginUiState.isLoggedIn) {
                SessionContent(
                    themeState = themeState,
                    onLogout = { loginViewModel.onAction(LoginViewModel.Action.Logout) }
                )
            } else {
                AuthFlow()
            }
```

교체 후:

```kotlin
            // 네트워크 배너 — 로그인 화면 포함 전역 오버레이(ConCafe App.kt 미러)라 세션 아닌 앱 루트 VM
            val networkStatusViewModel = viewModel {
                NetworkStatusViewModel(container.observeNetworkAlertStateUseCase)
            }
            val networkUiState by networkStatusViewModel.uiState.collectAsState()

            Box(Modifier.fillMaxSize()) {
                if (loginUiState.isLoggedIn) {
                    SessionContent(
                        themeState = themeState,
                        onLogout = { loginViewModel.onAction(LoginViewModel.Action.Logout) }
                    )
                } else {
                    AuthFlow()
                }
                NetworkStatusBanner(
                    networkAlertState = networkUiState.networkAlertState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
```

(`Box`·`Modifier`·`Alignment`·`fillMaxSize`·`viewModel`·`collectAsState`·`getValue`는 App.kt에 이미 import돼 있음)

- [ ] **Step 3: 컴파일 검증**

Run: `cmd.exe /c "gradlew.bat :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinJvm"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: (선택) Desktop 수동 확인**

가능하면 `cmd.exe /c "gradlew.bat :composeApp:run"`으로 데스크톱 앱을 띄우고, Windows에서 Wi-Fi/이더넷을 잠깐 끊어 배너 표시→복구 문구→자동 숨김을 확인한다. 환경상 어려우면 생략하고 미검증으로 기록.

- [ ] **Step 5: 스테이징+메시지 기록**

```bash
sed -i 's/\r$//' composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt
git diff --stat composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt   # 실변경만인지 확인
git add composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/ui/components/NetworkStatusBanner.kt \
  composeApp/src/commonMain/kotlin/kr/hhp227/storygroup/App.kt
```

---

### Task 7: shared iosMain 브리지 — Swift Flow 소비 어댑터

**Files:**
- Create: `shared/src/iosMain/kotlin/kr/hhp227/storygroup/shared/bridge/NetworkBridges.kt`

**Interfaces:**
- Consumes: Task 2의 `ObserveNetworkAlertStateUseCase`, 기존 `FlowSubscription`(`kr.hhp227.storygroup.shared.bridge` 패키지에 이미 존재)
- Produces: `NetworkAlertStateFlowAdapter.subscribe(onEach: (NetworkAlertState) -> Unit): FlowSubscription`, 확장 `fun ObserveNetworkAlertStateUseCase.statesFlow(): NetworkAlertStateFlowAdapter` — Task 8의 Swift VM이 `statesFlow().subscribe(onEach:)`로 소비

- [ ] **Step 1: 브리지 작성 (PersonalEventFlowAdapter 규약 미러)**

`shared/src/iosMain/kotlin/kr/hhp227/storygroup/shared/bridge/NetworkBridges.kt`:

```kotlin
package kr.hhp227.storygroup.shared.bridge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kr.hhp227.storygroup.shared.domain.model.NetworkAlertState
import kr.hhp227.storygroup.shared.domain.usecase.ObserveNetworkAlertStateUseCase

/** 네트워크 배너 상태 Flow 대응 핸들 — PersonalEventFlowAdapter와 동일 규약(타입별 어댑터) */
class NetworkAlertStateFlowAdapter internal constructor(
    private val source: Flow<NetworkAlertState>
) {
    fun subscribe(onEach: (NetworkAlertState) -> Unit): FlowSubscription {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope.launch { source.collect { onEach(it) } }
        return FlowSubscription(scope)
    }
}

/** Kotlin의 observeNetworkAlertStateUseCase() 호출 대응 — Swift KotlinFlowPublisher가 감싼다 */
fun ObserveNetworkAlertStateUseCase.statesFlow(): NetworkAlertStateFlowAdapter =
    NetworkAlertStateFlowAdapter(invoke())
```

- [ ] **Step 2: 컴파일 검증**

Run: `cmd.exe /c "gradlew.bat :shared:compileKotlinIosArm64"`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 스테이징+메시지 기록**

```bash
git add shared/src/iosMain/kotlin/kr/hhp227/storygroup/shared/bridge/NetworkBridges.kt
```

---

### Task 8: iosApp Swift 미러 — 컨테이너·VM·배너·루트 배치·pbxproj

**Files:**
- Modify: `iosApp/iosApp/DI/AppContainer.swift` (프로퍼티 93행 근처, init 142행·214행 근처)
- Create: `iosApp/iosApp/UI/Components/NetworkStatusViewModel.swift`
- Create: `iosApp/iosApp/UI/Components/NetworkStatusBannerView.swift`
- Modify: `iosApp/iosApp/AppRootView.swift`
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj` (4개 섹션에 등록)

**Interfaces:**
- Consumes: Task 7의 `statesFlow().subscribe(onEach:)`, 기존 `KotlinFlowPublisher`(KmpInterop.swift), `MviViewModel` 프로토콜, `@Environment(\.sgColors)`
- Produces: `AppContainer.observeNetworkAlertStateUseCase`, `NetworkStatusViewModel(observeNetworkAlertStateUseCase:)`, `NetworkStatusBannerView(message:isConnected:)`

⚠️ Swift는 컴파일 검증 불가(Mac 없음) — 작성 후 문법 자체 점검만 하고 미검증으로 기록.

- [ ] **Step 1: AppContainer.swift에 use case 추가**

프로퍼티 선언부 — `let observePersonalEventsUseCase: ObservePersonalEventsUseCase`(93행) 아래에:

```swift
    // 인터넷 연결 배너 — AppRootView가 구독한다(composeApp App.kt 미러)
    let observeNetworkAlertStateUseCase: ObserveNetworkAlertStateUseCase
```

init — 다른 repository 로컬 변수들(142행 `let notificationRepository = ...` 근처)과 같은 자리에:

```swift
        let networkStatusRepository = NetworkStatusRepositoryImpl(networkStatusDataSource: IosNetworkStatusDataSource())
```

init — use case 대입부(214행 `observePersonalEventsUseCase = ...` 근처)에:

```swift
        observeNetworkAlertStateUseCase = ObserveNetworkAlertStateUseCase(networkStatusRepository: networkStatusRepository)
```

- [ ] **Step 2: NetworkStatusViewModel.swift 작성 (IncomingCallViewModel 관용구)**

`iosApp/iosApp/UI/Components/NetworkStatusViewModel.swift`:

```swift
import Combine
import Foundation
import Shared

/// 네트워크 연결 배너 상태 — composeApp NetworkStatusViewModel.kt와 1:1 미러.
/// 사용자 액션·일회성 이벤트가 없어 Action/Event 모두 Never.
final class NetworkStatusViewModel: MviViewModel {
    typealias Action = Never

    typealias Event = Never

    @Published private(set) var uiState = UiState()

    private var cancellables = Set<AnyCancellable>()

    func onAction(_ action: Never) {}

    init(observeNetworkAlertStateUseCase: ObserveNetworkAlertStateUseCase) {
        // 구독 수명 = 루트 VM 수명 — 앱이 살아 있는 동안 감지가 유지된다
        KotlinFlowPublisher<NetworkAlertState> { onEach in
            observeNetworkAlertStateUseCase.statesFlow().subscribe(onEach: onEach)
        }
        .sink { [weak self] state in self?.uiState.networkAlertState = state }
        .store(in: &cancellables)
    }

    struct UiState {
        var networkAlertState: NetworkAlertState = NetworkAlertState.companion.hidden
    }
}
```

- [ ] **Step 3: NetworkStatusBannerView.swift 작성 (ConCafe 뷰 이식, SGColors)**

`iosApp/iosApp/UI/Components/NetworkStatusBannerView.swift`:

```swift
import SwiftUI

/// 네트워크 연결 배너 — composeApp NetworkStatusBanner.kt와 1:1 미러(오프라인=rust, 복구=moss)
struct NetworkStatusBannerView: View {
    let message: String

    let isConnected: Bool

    @Environment(\.sgColors) private var colors

    private var tint: Color { isConnected ? colors.moss : colors.rust }

    var body: some View {
        Text(message)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(tint.opacity(0.14))
            .background(colors.paper)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .accessibilityLabel(message)
    }
}
```

- [ ] **Step 4: AppRootView.swift에 오버레이 배치**

`@StateObject private var loginViewModel: LoginViewModel` 아래에 추가:

```swift
    @StateObject private var networkStatusViewModel: NetworkStatusViewModel
```

`var body`를 다음으로 교체 — 기존:

```swift
    var body: some View {
        Group {
            if loginViewModel.uiState.isLoggedIn {
                MainShellView(container: container, theme: theme, onLogout: { loginViewModel.onAction(.logout) })
            } else {
                AuthFlowView(container: container, loginViewModel: loginViewModel)
            }
        }
        // Compose CompositionLocalProvider(LocalSgColors provides sg) 미러 — 하위 전체에 테마 전파
        .environment(\.sgColors, colors)
        .preferredColorScheme(theme.nightMode == .system ? nil : (isDark ? .dark : .light))
    }
```

교체 후:

```swift
    var body: some View {
        // 네트워크 배너 — 로그인 화면 포함 전역 오버레이(composeApp App.kt Box 미러)
        ZStack(alignment: .top) {
            Group {
                if loginViewModel.uiState.isLoggedIn {
                    MainShellView(container: container, theme: theme, onLogout: { loginViewModel.onAction(.logout) })
                } else {
                    AuthFlowView(container: container, loginViewModel: loginViewModel)
                }
            }
            if networkStatusViewModel.uiState.networkAlertState.isVisible {
                NetworkStatusBannerView(
                    message: networkStatusViewModel.uiState.networkAlertState.message,
                    isConnected: networkStatusViewModel.uiState.networkAlertState.isConnected
                )
                .transition(.move(edge: .top).combined(with: .opacity))
                .zIndex(1)
            }
        }
        .animation(
            .easeInOut(duration: 0.2),
            value: networkStatusViewModel.uiState.networkAlertState.isVisible
        )
        // Compose CompositionLocalProvider(LocalSgColors provides sg) 미러 — 하위 전체에 테마 전파
        .environment(\.sgColors, colors)
        .preferredColorScheme(theme.nightMode == .system ? nil : (isDark ? .dark : .light))
    }
```

`init(container:)`의 `_loginViewModel = StateObject(...)` 아래에 추가:

```swift
        _networkStatusViewModel = StateObject(wrappedValue: NetworkStatusViewModel(
            observeNetworkAlertStateUseCase: container.observeNetworkAlertStateUseCase
        ))
```

- [ ] **Step 5: pbxproj 등록 (ID 0058/0059 — 미사용 확인됨)**

`iosApp/iosApp.xcodeproj/project.pbxproj` 4개 섹션에 추가. 기존 규칙: BuildFile=`A10100XX...FF00XX`, FileReference=`A10110XX...FF00XX`.

① PBXBuildFile 섹션 — 59-60행(`...FF0056`/`...FF0057` 항목) 뒤에:

```
		A1010058AABBCCDDEEFF0058 /* NetworkStatusViewModel.swift in Sources */ = {isa = PBXBuildFile; fileRef = A1011058AABBCCDDEEFF0058 /* NetworkStatusViewModel.swift */; };
		A1010059AABBCCDDEEFF0059 /* NetworkStatusBannerView.swift in Sources */ = {isa = PBXBuildFile; fileRef = A1011059AABBCCDDEEFF0059 /* NetworkStatusBannerView.swift */; };
```

② PBXFileReference 섹션 — 143-144행(`...FF0056`/`...FF0057` 항목) 뒤에:

```
		A1011058AABBCCDDEEFF0058 /* NetworkStatusViewModel.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = NetworkStatusViewModel.swift; sourceTree = "<group>"; };
		A1011059AABBCCDDEEFF0059 /* NetworkStatusBannerView.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = NetworkStatusBannerView.swift; sourceTree = "<group>"; };
```

③ `A1013002AABBCCDDEEFF0002 /* Components */` 그룹(244행 근처)의 children 목록에 (`SGComponents.swift` 항목 뒤):

```
				A1011058AABBCCDDEEFF0058 /* NetworkStatusViewModel.swift */,
				A1011059AABBCCDDEEFF0059 /* NetworkStatusBannerView.swift */,
```

④ PBXSourcesBuildPhase files 목록 — 598-599행(`...FF0056`/`...FF0057` 항목) 뒤에:

```
				A1010058AABBCCDDEEFF0058 /* NetworkStatusViewModel.swift in Sources */,
				A1010059AABBCCDDEEFF0059 /* NetworkStatusBannerView.swift in Sources */,
```

등록 후 `grep -c "FF0058\|FF0059" iosApp/iosApp.xcodeproj/project.pbxproj` 결과가 8인지 확인(파일당 4곳).

- [ ] **Step 6: 스테이징+메시지 기록**

```bash
sed -i 's/\r$//' iosApp/iosApp/DI/AppContainer.swift iosApp/iosApp/AppRootView.swift iosApp/iosApp.xcodeproj/project.pbxproj
git diff --stat iosApp/iosApp/DI/AppContainer.swift iosApp/iosApp/AppRootView.swift   # 실변경만인지 확인
git add iosApp/iosApp/DI/AppContainer.swift \
  iosApp/iosApp/UI/Components/NetworkStatusViewModel.swift \
  iosApp/iosApp/UI/Components/NetworkStatusBannerView.swift \
  iosApp/iosApp/AppRootView.swift \
  iosApp/iosApp.xcodeproj/project.pbxproj
```

---

### Task 9: 최종 검증 + 인수인계

**Files:**
- Modify: 없음 (검증·기록만)

- [ ] **Step 1: 전체 테스트·컴파일 일괄 재실행**

Run: `cmd.exe /c "gradlew.bat :shared:jvmTest :composeApp:jvmTest :shared:compileDebugKotlinAndroid :composeApp:compileDebugKotlinAndroid :shared:compileKotlinIosArm64"`
Expected: BUILD SUCCESSFUL, 신규 테스트 7개 포함 전체 그린

- [ ] **Step 2: 스테이징 상태 최종 확인**

Run: `git status --short | grep -v "^ M"` — 이 기능의 파일들이 전부 스테이징(A/M)됐는지, 의도치 않은 파일이 없는지 확인. (`^ M`으로 걸러지는 것은 CRLF 노이즈 미수정분)

- [ ] **Step 3: 계획 문서 스테이징 + 사용자 인수인계**

```bash
git add docs/superpowers/plans/2026-08-23-network-status-ui.md
```

사용자에게 보고: 구현 요약, 신규/수정 파일 목록, 테스트 결과, Swift 미검증·Desktop 수동 확인 결과, 그리고 아래 "커밋 메시지 기록"의 제안 메시지. 커밋·push는 사용자 몫.

---

## 커밋 메시지 기록

구현 중 태스크가 끝날 때마다 여기에 누적한다. 전 태스크가 한 기능이므로 기본 제안은 단일 커밋:

```
feat: 인터넷 미접속 알림 배너 (Android/Desktop/iOS)

- shared: NetworkAlertState + NetworkStatusRepository/Impl + ObserveNetworkAlertStateUseCase (ConCafe 이식, 복구 1.8초 표시)
- 플랫폼 감지: Android ConnectivityManager(+5초 폴링 보완), iOS SCNetworkReachability 2초 폴링(원본 대비 CFRelease 누수 보완+flowOn(Default) 메인스레드 회피), Desktop 소켓 폴링(1.1.1.1/8.8.8.8:53) — ConCafe와 달리 Desktop도 실감지
- composeApp: AppContainer 주입(imageCompressor 관례)+NetworkStatusViewModel(앱 루트 스코프)+NetworkStatusBanner(App.kt 전역 오버레이, rust/moss)
- iosApp: AppContainer/VM/배너 뷰/AppRootView ZStack 미러+NetworkBridges statesFlow()+pbxproj 등록(0058/0059)
- ACCESS_NETWORK_STATE 권한 추가, 테스트 7개(usecase 시퀀스·repo 폴백·VM)
- ⚠️ Swift 컴파일 미검증(Mac 없음), Desktop 실기 수동 확인 미수행

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```

구현 결과 메모(2026-08-23 실행 완료): 미디어 압축 변경은 이미 HEAD에 커밋돼 있어 섞임 없음. 배너 겹침(수신통화 배너와 동시 표시 시 네트워크 배너가 위)은 사용자 결정으로 플랜대로 수용. Mac 확보 시 체크리스트 — Swift 전체 컴파일 → `NetworkAlertState.companion.hidden` 접근(리포 첫 .companion 사례) → 배너 겹침 육안 → recovered 페이드아웃 문구.
