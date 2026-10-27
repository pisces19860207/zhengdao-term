// 证道终端（zhengdao-term）· Gradle 设置
//
// 本仓是**独立 APP 仓**：只有 :app 一个模块。
// 刻意**不预建** :core / :terminal-core —— 终端内核在第四刀整模块搬入，
// 共享层（证道的 :core）第五刀再接；现在建空壳只会让人以为"已经分好层了"。
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
        // 主仓把 :core / :native-core 发布到 mavenLocal（com.example.zhengdao:zhengdao-core:2.2.1）。
        // 两仓**不共享源码**，只共享这个产物（设计稿 §1 选型①）。
        mavenLocal()
    }
}

rootProject.name = "zhengdao-term"
include(":app")
// 终端内核（第四刀从主仓整模块搬入；主仓已删）
include(":terminal-core")
