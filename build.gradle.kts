// 证道终端 · 根构建脚本
//
// 版本口径与主仓（证道）对齐：AGP 9.4.0 / Kotlin 2.2.10 / Gradle 9.6 / SDK 36。
// 注意：AGP 9 自带 Kotlin 支持，**不要再加 kotlin-android 插件**（主仓踩过，见其根脚本注释）。
plugins {
    alias(libs.plugins.android.application) apply false
    // 第二百四十八批：:terminal-core（库模块）
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
