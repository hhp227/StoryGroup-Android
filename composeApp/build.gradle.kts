import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    // Navigation Compose 타입 세이프 라우트(@Serializable)용
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            // 레거시 앱과 같은 M2. M3 전환 시 compose.material3로 교체 — 절차는 ui/theme/Theme.kt 주석 참조
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.compose.materialIconsExtended)
            // ViewModel은 이제 composeApp 소유 — shared는 lifecycle에 의존하지 않는다
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            // 풀스크린 목적지(그룹 상세 등)의 백스택 — 탭 전환은 여전히 셸 enum이 담당
            implementation(libs.jetbrains.navigation.compose)
            // 게시글 피드 페이징 UI — paging-common은 shared가 api로 노출(데이터 계층 소속)
            implementation(libs.cash.paging.compose)
            // 프로필/커버/게시글 첨부 이미지 로딩 — composeApp은 Android+Desktop 타깃뿐이라
            // network-okhttp(JVM 엔진)로 충분(iOS는 SwiftUI 네이티브 AsyncImage 별도 사용)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.okhttp)
            implementation(projects.shared)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

android {
    namespace = "kr.hhp227.storygroup"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.hhp227.application"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "kr.hhp227.storygroup.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "kr.hhp227.storygroup"
            packageVersion = "1.0.0"
        }
    }
}
