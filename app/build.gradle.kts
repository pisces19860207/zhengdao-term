// 证道终端 · app 模块（骨架）
//
// 本刀（第二百四十七批）只立骨架：一个 Compose 壳页 + 权限声明 + 图标。
// **不搬终端代码**（第四刀整模块搬入）、**不引 :core**（第五刀再接）、**不引第三方依赖**。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    // 与主仓（证道）的 applicationId 刻意不同：两个 App 可以并存在同一台手机上，
    // 装这个不会覆盖主 APP（覆盖 = 用户丢数据，见派工单负例②）。
    namespace = "com.example.zhengdao.term"
    compileSdk {
        // 第四刀：**只有 compileSdk 抬到 37** —— 主仓发布的 zhengdao-core / zhengdao-native
        // 是在 37 下编的，AAR 元数据写死 minCompileSdk=37，36 编不过。
        // minSdk / targetSdk 仍是 36（README 的「Android 16+」不变，装机能装的还是 36+）。
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.zhengdao.term"
        minSdk = 36
        targetSdk = 36
        // 新仓从 0.1.0 起，**不跟主仓 2.2.x 混**（两个 App 的版本号各走各的）。
        versionCode = 5
        versionName = "0.1.4"

        // README 写的是「只提供 arm64 单架构包」，这条与实物一致：交付 APK 里 8 个 .so
        // 全在 lib/arm64-v8a/ 一个目录下（2026-10-28 把 zip 逐项读出来的）。第四刀搬进来的
        // proot 四件套没要求再动构建脚本 —— ABI 口径当初钉住之后一直是钉住的状态。
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        // 正式签名：与主仓同一把 release 钥匙（不同 applicationId 可以共用）。CI 用环境变量；
        // 本机回退到 ~/.zhengdao-keys/zhengdao-release.jks。密码不写进仓库。
        create("release") {
            val home = System.getProperty("user.home")
            storeFile = file(
                System.getenv("ZHENGDAO_KEYSTORE_FILE")
                    ?: "$home/.zhengdao-keys/zhengdao-release.jks",
            )
            storePassword = System.getenv("ZHENGDAO_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("ZHENGDAO_KEY_ALIAS") ?: "zhengdao"
            keyPassword = System.getenv("ZHENGDAO_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // 正式包：R8 + 资源收缩，release 签名。JNI/反射点由 proguard-rules.pro 兜住；
            // 本仓终端内核与 JNI 入口都保留，不用「整体不混淆」换平安。
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // proot 要 execve nativeLibraryDir 里的 libproot.so / libproot-loader.so，并把
    // libtalloc.so 从那儿复制改名 —— 这两件事的前提是「安装时系统真把 jniLibs 解压出来」。
    // AGP 默认 useLegacyPackaging=false（manifest 落 extractNativeLibs="false"），.so 只留在
    // APK 里给 loadLibrary 用，nativeLibraryDir 是空目录 ⇒ 0.1.3 起 proot 在真机上起不来，
    // 会话一直是「证道 - 系统 shell」。真机实测：打开这一项后 lib/arm64/ 出现 8 个带执行位的 .so，
    // 启动决策 proot=true，终端进 Debian 13.7。
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        // 派工单：JDK 21（AGP 9 / Gradle 9.6 支持；与 SDK 36 同线）
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        // 壳页要显示版本号，用 BuildConfig.VERSION_NAME 而不是硬编码 —— 版本号只有一处真源。
        buildConfig = true
    }

    testOptions {
        // 与主仓同口径：单测里 android.util.Log 之类返回默认值，不抛 "not mocked"
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // targetSdk 已是当前最新（36）⇒ 关掉"该升 targetSdk"的噪声。页面则早就不止壳页：
        // app/src/main 实测 12 个 .kt（壳页＋终端页），终端内核在 :terminal-core。
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    // 第二百四十八批（第四刀）：终端内核整模块搬进来了
    implementation(project(":terminal-core"))
    // 门禁测试直接引用终端内核的类（断言源码/行为），单测也要能看见
    testImplementation(project(":terminal-core"))
    testImplementation(libs.junit)
    testImplementation(libs.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose 基础件（BOM 统一版本；不含任何第三方 UI 库）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
