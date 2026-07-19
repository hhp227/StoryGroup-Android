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
            // Swift에서 PagingData<T>를 접두사 없는 타입으로 다루기 위해(Paging_commonPagingData 방지)
            // — State.pagingData가 Android와 1:1 대응 (Paging-CRUD 샘플과 동일 구성)
            export(libs.cash.paging.common)
            export(libs.androidx.paging.common)
            xcframework.add(this)
        }
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // 페이징은 데이터 계층 소속 — Repository가 Flow<PagingData>를 노출하므로 api
            // (androidx.paging은 jvm 타깃 미지원이라 Cash 멀티플랫폼 포크 사용.
            //  androidx paging-common은 cash가 위임하는 실체 — 프레임워크 export 조건으로 api 선언)
            api(libs.cash.paging.common)
            api(libs.androidx.paging.common)
            // HttpClient가 AuthRepository/createApiClient의 공개 시그니처에 노출되므로 api
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.auth)
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
