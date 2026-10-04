plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.example.yuewen"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.yuewen"
        minSdk = 26
        targetSdk = 34
        versionCode = 29
        versionName = "2.7.4"
    }

    signingConfigs {
        // 复用 Android 默认的 debug 签名（~/.android/debug.keystore）。
        // 好处：release 包（开了 R8，明显更流畅）和之前的 debug 包签名一致，
        // 可以直接覆盖安装，不用卸载、不丢数据。
        create("shared") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            // 开启代码压缩/混淆与资源裁剪：Compose 的 debug 包本身就很重，
            // 这是消除「整体卡顿」最有效的一步。
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * 产物统一命名成 `app-v<版本号>.apk`，不再用默认的 `app-debug` / `app-release`。
 *
 * 为什么用 `androidComponents.onVariants` 而不是老的 `android.applicationVariants.all`：
 * 后者在 AGP 8.x 已进入弃用通道，用它会往构建输出里塞一条 deprecation 警告，
 * 而本项目要求「零编译警告」。
 *
 * ⚠️ 为什么必须强转成 `VariantOutputImpl`：
 * `com.android.build.api.variant.VariantOutput` 这个**公开接口只暴露了 versionName**，
 * 没有改名的方法；真正带 `outputFileName` 的是 impl 包里的实现类。
 *
 * 版本号取自「当前 variant 自己」而不是写死 defaultConfig —— 这样将来给 release
 * 做 versionName 后缀（比如 `2.7.4` + `-beta`）时，产物名会跟着自动变。
 *
 * 目录不变：debug 仍在 `app/build/outputs/apk/debug/`，
 * release 在 `.../apk/release/`，两者同名但不同目录，不会互相覆盖。
 */
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val impl = output as? com.android.build.api.variant.impl.VariantOutputImpl ?: return@forEach
            impl.outputFileName.set(impl.versionName.map { "app-v$it.apk" })
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // 正文抽取：Readability4J（Mozilla Readability.js 的 Kotlin 移植，Firefox 阅读模式同款算法）
    // + jsoup HTML 解析。用于「进详情页直接看全文」，不再跳浏览器。
    implementation("net.dankito.readability4j:readability4j:1.0.8")
    implementation("org.jsoup:jsoup:1.16.2")
    implementation("org.slf4j:slf4j-nop:1.7.36")

    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // v2.0：朗读通知条用 MediaStyle（暂停 / 停止 / 回到文章三个按钮收在通知里）
    implementation("androidx.media:media:1.7.0")
}
