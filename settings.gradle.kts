// 证道终端（zhengdao-term）· Gradle 设置
//
// 本仓是**独立 APP 仓**：两个模块 —— :app（壳与终端页）＋ :terminal-core（PTY / rootfs / proot）。
// 终端内核已于第四刀（2026-10-27）整模块从主仓搬入，不再是"空壳待迁"。
// 共享层（证道的 :core / :native-core）**不建成本仓模块**：只以随仓 AAR 复用（见下方 repositories 注释）。
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 依赖随仓 `libs/` 携带：terminal-core/build.gradle.kts 以 files("../libs/*.aar") 引用
        // zhengdao-core-2.2.1.aar 与 zhengdao-native-2.2.1.aar（由主仓 248 批发布后拷入、手动同步）。
        // 因此**不依赖 mavenLocal**，也不要求主仓先发布 —— 克隆本仓直接能编，CI 同理。
        // 两仓仍**不共享源码**：AAR 的源码留在主仓，这是"断链"的定义。
    }
}

rootProject.name = "zhengdao-term"
include(":app")
// 终端内核（第四刀从主仓整模块搬入；主仓已删）
include(":terminal-core")
// 共享层已整模块搬入本仓（2026-10-28，彻底断开主仓）
include(":core")
include(":native-core")
