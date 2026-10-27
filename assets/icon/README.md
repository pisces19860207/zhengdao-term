# 证道终端 · 图标

从证道本体拆出后，终端 App 用**自己的图标**，避免两个 App 在桌面/多任务里认错。

| | 证道（主仓） | 证道终端（本仓） |
|---|---|---|
| 图标 | 白底、黑「证」、绿色光标 | 暗底 `#0E1114`、绿色 `>_` |
| 含义 | 认知型 harness（抱朴） | 命令行 |

## 文件

- `terminal-icon.svg`：矢量源文件（512×512，圆角 112）。
- `terminal-icon-512.png`：PNG 预览（商店/文档用）。
- `android/ic_launcher_foreground.xml`：Android 自适应图标前景（108dp viewport，绿色 chevron + 下划线）。
- `android/ic_launcher.xml` / `ic_launcher_round.xml`：自适应图标壳。
- 背景色：`#0E1114`，落到 `res/values/colors.xml` 的 `ic_launcher_background`。

## 接入（终端 App 代码就位后）

1. 把 `android/ic_launcher_foreground.xml` 放到 `app/src/main/res/drawable/`；
2. 把 `android/ic_launcher*.xml` 放到 `app/src/main/res/mipmap-anydpi/`；
3. 在 `res/values/colors.xml` 加 `<color name="ic_launcher_background">#FF0E1114</color>`；
4. `AndroidManifest.xml` 的 `android:icon` / `android:roundIcon` 指到 `@mipmap/ic_launcher` / `@mipmap/ic_launcher_round`。

> 与主仓的图标刻意不同色系：主仓白底「证」，本仓暗底 `>_`。两边的 `monochrome` 也用同一套前景，主题图标不会串。
