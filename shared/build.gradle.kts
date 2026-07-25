import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

// StoryGroup-Android가 composite build에서 kr.hhp227.storygroup:shared 좌표로 소비한다
group = "kr.hhp227.storygroup"
version = "1.0.0"

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // XCFramework 빌드(assembleSharedXCFramework)와 embedAndSignAppleFrameworkForXcode는 Mac에서만 실행 가능
    val xcframework = XCFramework("Shared")

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64()
    ).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // ⚠️paging-common은 export 금지(cash·androidx 모두): alpha02 모듈 전체를 ObjC로
            //   내보내면 헤더 생성이 깨져 앱 컴파일이 실패한다(사용자 Mac에서 확인).
            //   PagingData는 접두사 이름(Paging_commonPagingData)으로 노출되고,
            //   앱의 KmpInterop.swift가 typealias로 PagingData 이름을 복원한다.
            xcframework.add(this)
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // 페이징은 데이터 계층 소속 — Repository가 Flow<PagingData>를 노출하므로 api
            // (androidx.paging은 jvm 타깃 미지원이라 Cash 멀티플랫폼 포크 사용)
            api(libs.cash.paging.common)
            // HttpClient가 AuthRepository/createApiClient의 공개 시그니처에 노출되므로 api
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.auth)
            // STOMP 채팅 실시간 수신(웹소켓) — okhttp/darwin 엔진 모두 지원
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinxJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
    }
}

android {
    namespace = "kr.hhp227.storygroup.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
