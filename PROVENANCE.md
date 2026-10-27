# 来源与出处声明（PROVENANCE）

本仓（`zhengdao-term`）是「证道」拆出的独立终端 App。这份文件回答一个问题：
**仓库里的东西都是从哪儿来的、各是什么许可、我们改了什么。**
README 的「许可与第三方」一节指向这里；正文在 [`licenses/`](licenses/)，义务对照在
[`THIRD-PARTY-LICENSES.md`](THIRD-PARTY-LICENSES.md)。

> **当前状态（2026-10-27，第四刀交付后）**：终端内核与终端页已整批从证道主仓搬入，
> 主仓已断链（两边只通过 Maven 构件 `com.example.zhengdao:zhengdao-core` /
> `zhengdao-native:2.2.1` 相连）。本文件按**实物**登记，不再有「将来会迁入」的条目。

## 1. 第一方代码（本项目原创，保留所有权利）

| 路径 | 内容 | 规模 |
|---|---|---|
| `app/src/main/java/com/example/zhengdao/term/**` | 壳页（三步清单：授权 → 装环境 → 打开终端）、主题 | 12 个文件 |
| `app/src/main/res/**`、`app/src/main/assets/fonts/**` | 图标、文案、主题、颜色、终端字体（字体本体是第三方，见 §5） | — |
| `terminal-core/src/main/java/com/example/zhengdao/terminal/**` | proot 启动、会话管理、包管理/缓存清理、体检依赖类、输入法探针、Ed25519 验签 | 45 个文件（含 `rootfs/**`） |
| `terminal-core/src/main/java/com/example/zhengdao/rootfs/**` | 环境包下载 / 校验 / 解压 / 安装（`RootfsInstaller` 一族） | 同上 |
| `terminal-core/src/main/res/raw/hermes_env_repair.sh` | 依赖修复脚本（本仓自写，随资源打包） | 1 个文件 |
| `terminal-core/build.gradle.kts`、`app/build.gradle.kts`、`gradle/**` | 工程配置 | — |
| `README.md`、`LICENSE`、`PROVENANCE.md`、`THIRD-PARTY-LICENSES.md`、`licenses/` | 文档与许可证正文 | — |

上述代码**从证道主仓搬来**（第二百四十一 / 二百四十八批），搬家时只改包引用、不改行为；
git 历史在证道主仓，本仓历史从骨架期（第二百四十七批）开始。作者同一人 ⇒ 不涉及第三方义务。

图标来源：`assets/icon/`（本仓自建，SVG 源 + 512 PNG + 自适应图标）。
**不是**从证道主仓拷的 —— 主仓是白底「证」+ 绿光标，本仓是暗底 `>_`，刻意不同色系。

## 2. 第一方复用（同一作者的另两个构件，从 `mavenLocal()` 取）

| 构件 | 版本 | 内容 | 许可 |
|---|---|---|---|
| `com.example.zhengdao:zhengdao-core` | 2.2.1 | 证道主仓的 `:core`（`RunLog` / `ZhengdaoDirs` / `AppPrefs` / `TimeFormats` / `kb` / `notify` / `PathGuard`） | 第一方，保留所有权利 |
| `com.example.zhengdao:zhengdao-native` | 2.2.1 | 主仓的 `:native-core`，AAR 内含 `jniLibs/arm64-v8a/libzhengdao_core.so`（Rust：sha256 / 解压 / 验签，**人工构建入库、CI 不编**） | 第一方，同上 |

> 这两个 AAR 由主仓 `maven-publish` 发布（`./gradlew :core:publishReleasePublicationToMavenLocal
> :native-core:publishReleasePublicationToMavenLocal`）。**源码不进本仓** —— 这是"断链"的定义：
> 主仓删掉终端代码、本仓不复制主仓源码，两边只剩构件依赖。

## 3. 第三方源码（在仓，Apache-2.0）

| 组件 | 版本 | 路径 | 许可 | 改动 |
|---|---|---|---|---|
| Termux `terminal-emulator` / `terminal-view` / `textselection` | v0.119.0-beta.3 | `terminal-core/src/main/java/com/termux/**`（29 个文件） | **Apache-2.0** | 包名保留 `com.termux.**`；为接入本项目的会话/视图做了最小适配（见各文件头注释）。**未**聚合 termux-app 主应用本体（那是 GPL-3.0） |
| Termux 伪终端 JNI 的 native 半边 | 同上 | `terminal-core/src/main/cpp/termux/termux.c` → `libtermux.so` | Apache-2.0 | 随本仓 CMake 构建（`-Wl,-z,max-page-size=16384`，16KB 页对齐） |

> ⚠️ 许可证更正（2026-10 记）：这两个库曾在本项目早期被误标为 GPL-3.0。
> 按上游 `termux-app/LICENSE.md` 的 Exceptions 一节，`terminal-emulator` 与 `terminal-view`
> 是 **Apache-2.0**；只有 termux-app 主应用本体是 GPL-3.0，而本项目没有聚合它。

## 4. 第三方二进制（在仓，`terminal-core/src/main/jniLibs/arm64-v8a/`）

预编入库，**CI 不编译**；运行时由 `ProotBinaries` 释放到应用私有目录再 `execve` / `dlopen`。

| 文件 | 字节 | 许可 | 用法 | SHA256（小写） |
|---|---|---|---|---|
| `libproot.so` | 247,488 | GPL-2.0-or-later | 独立进程 `execve`（聚合分发，不链接第一方代码） | `1545b85b312505db6eb6908ff8b2ded0a77a3bd689c50ae85aa7c1d8445dd717` |
| `libproot-loader.so` | 18,136 | GPL-2.0-or-later | 同上 | `cbdef0e652c2b78af25d867e1719fdebbb0915e25aae2dd35b3b5c1835f6b551` |
| `libtalloc.so` | 33,592 | LGPL-3.0-or-later | proot 进程内动态加载（允许替换该库） | `742b438c4d09e276985a61d44164c9de207b6d8cc268f3018cb3001961bcb309` |
| `libandroid-shmem.so` | 14,432 | BSD-3-Clause | 同上 | `84475798e07c8174dbbfaec70a827fdb02f19ffa69a589380c13e7507fd0e731` |
| `libzstd-jni-1.5.6-4.so` | 600,064 | BSD-2-Clause（zstd-jni）＋ BSD-3-Clause（zstd 本体） | JNI 调用，解 `.zst` 环境包 | `81a68678c1aec773d665e0049d4c49c3a90701caf1636aa72427c26a3d8d599e` |

> proot 四件套是**上游 4KB 构建**（被 `execve` 的独立 ELF，不受 Android 16KB 页约束），
> 所以 CI 只对它们做存在性检查；`libtermux.so`（本仓自编）与 `libzhengdao_core.so`（主仓入库）
> 必须 `PT_LOAD.p_align >= 0x4000`，CI 里硬判。

## 5. 第三方资源

| 资源 | 路径 | 许可 | 正文 |
|---|---|---|---|
| JetBrains Maple Mono NF（Regular，6,773,564 B，sha256 `10c8ea51ab6bb2df9a615424df1054ad6b3c08ad60a47e8bb3368039b92b5089`） | `app/src/main/assets/fonts/JetBrainsMapleMono-NF-Regular.ttf` | SIL OFL 1.1 | 同目录 `OFL.txt`（4,572 B） |

## 6. 不在仓里、但属于分发物的东西

| 东西 | 形态 | 说明 |
|---|---|---|
| Debian 13.7 rootfs | **运行时下载**（或用户自备本地缓存包），进 `Download/证道` 与私有目录，**不进 git** | 各 Debian 包各自许可，随包附带其 `/usr/share/doc/*/copyright`；本仓不重打包、不修改其内容 |
| 证道主仓的源码 | 不在本仓 | 见 §2：只以 AAR 形式复用 |

## 7. 与证道主仓的关系

本仓**不包含**证道主仓的任何源码（`terminal-core` 里 `com.example.zhengdao.**` 是搬家的产物，
git 历史留在主仓；`com.termux.**` 是上游）。两边关系：

- 同一个作者、同一个品牌、**可并存安装**（`applicationId` 不同：`com.example.zhengdao.term`）；
- 单向依赖：本仓 → 主仓发布的 AAR；主仓**不**依赖本仓（主仓已删终端代码与模块）；
- 主仓的 `THIRD-PARTY-LICENSES.md` / `PROVENANCE.md` 只覆盖主仓（抱朴 ＋ 玄览），不覆盖本仓。

## 8. 已知缺口（如实登记，别当已完成）

1. 许可证正文**已入库** `licenses/`，但**还没装配进 APK**（主仓的 `stageLicenseAssets` 本仓未做）；
   App 内也还没有「开源许可」阅读页。两件事要一起做才算合规闭环。
2. proot / libtalloc 的**源码获取方式**目前只写在文档里（上游仓库地址），App 的关于页还没有这句话。
3. `PROOT_PINNED` 式的四件套基线在主仓 `tools/`，本仓尚无对应脚本 —— 上面的 sha256 是**人工登记**，
   CI 只查存在性。
