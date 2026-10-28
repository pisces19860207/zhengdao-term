# 来源与出处声明（PROVENANCE）

本仓（`zhengdao-term`）是「证道」拆出的独立终端 App。这份文件回答一个问题：
**仓库里的东西都是从哪儿来的、各是什么许可、我们改了什么。**
README 的「许可与第三方」一节指向这里；正文在 [`licenses/`](licenses/)，义务对照在
[`THIRD-PARTY-LICENSES.md`](THIRD-PARTY-LICENSES.md)。

> **当前状态（2026-10-27，第四刀交付后）**：终端内核与终端页已整批从证道主仓搬入，
> 主仓已断链 —— 两边不共享源码，主仓产物以**随仓 AAR** 的形式带在本仓
> `libs/zhengdao-core-2.2.1.aar` / `libs/zhengdao-native-2.2.1.aar`（见 §2，手动同步）。
> 本文件按**实物**登记，不再有「将来会迁入」的条目。

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

## 2. 第一方复用（同一作者的另两个构件，随仓 `libs/` 携带）

| 随仓文件 | 构件坐标 | 版本 | 内容 | 许可 |
|---|---|---|---|---|
| `libs/zhengdao-core-2.2.1.aar` | `com.example.zhengdao:zhengdao-core` | 2.2.1 | 证道主仓的 `:core`（`RunLog` / `ZhengdaoDirs` / `AppPrefs` / `TimeFormats` / `kb` / `notify` / `PathGuard`） | 第一方，保留所有权利 |
| `libs/zhengdao-native-2.2.1.aar` | `com.example.zhengdao:zhengdao-native` | 2.2.1 | 主仓的 `:native-core`，AAR 内含 `jniLibs/arm64-v8a/libzhengdao_core.so`（Rust：sha256 / 解压 / 验签，**人工构建入库、CI 不编**） | 第一方，同上 |

> 这两个 AAR 现在**随仓携带、直接 `files(...)` 引用**（`terminal-core/build.gradle.kts:61,63`），
> **不再从 `mavenLocal()` 取**：由主仓 248 批以 `maven-publish` 发布后**拷入 `libs/`、手动同步**。
> ⇒ 克隆本仓就能编，CI 既不需要 `mavenLocal()`，也不要求主仓先发布一次。
> **源码不进本仓** —— 这是"断链"的定义：主仓删掉终端代码、本仓不复制主仓源码，两边只剩这两份二进制。
> ⚠️ 手动同步的代价：`libs/` 只是拷贝那一刻的快照，主仓 `:core` / `:native-core` 后续变了**不会自动跟**；
> 换版本要连着改 `terminal-core/build.gradle.kts` 的文件名与 §4 的 sha256 登记。

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
- 单向依赖：本仓 → 主仓发布的 AAR（现以**随仓 `libs/`** 携带，手动同步，见 §2）；
  主仓**不**依赖本仓（主仓已删终端代码与模块）；
- **本仓的许可登记以本仓这三件为准**：`LICENSE` / `THIRD-PARTY-LICENSES.md` / `PROVENANCE.md`。
  主仓的同名文件只覆盖主仓（抱朴 ＋ 玄览），**不覆盖本仓**，别去那边找清单。

## 8. 已知缺口（如实登记，别当已完成）

1. 许可证正文**已入库** `licenses/`，但**还没装配进 APK**（主仓的 `stageLicenseAssets` 本仓未做）；
   App 内也还没有「开源许可」阅读页。两件事要一起做才算合规闭环。
2. proot / libtalloc 的**源码获取方式**目前只写在文档里（上游仓库地址），App 的关于页还没有这句话。
3. proot 四件套的 **sha256 基线待补**：主仓那份 `tools/check-native-so.py` 里的 `PROOT_PINNED`
   已随终端搬离而清空（250 批改成 `{}`），本仓**还没有**对应的基线脚本（仓内没有 `tools/`）
   —— 所以上面 §4 的 sha256 目前只是**人工登记**，CI 对四件套只查存在性、不比对哈希。
   **待补**：在本仓落一份等价脚本，把四个 sha256 钉成机检；在它落地之前，别把"有基线"当成事实。
   已经就位的是 **16KB 页对齐**那一条，但它不是脚本 —— 判据内嵌在
   `.github/workflows/ci.yml` 的「入库 .so 门禁」步骤里，对会被 `loadLibrary` 的库硬判
   `PT_LOAD.p_align >= 0x4000`（与主仓脚本的判据 1 同一条，只是各写各的）。

4. **环境包的发布链现在是断的**（2026-10-28 实测，第二百六十批登记）：主仓 `d698ea03`（08:31）把「本次要不要
   重建 RootFS」改成恒 `false`，`c331db18`（09:34）又删掉主仓 `build.yml` 里构建 → Ed25519 签名 →
   `gh release upload latest` 那 8 个步骤；构建脚本本仓接手了（`rootfs/build-rootfs.sh`），但**没有任何
   workflow 跑它**（`.github/workflows/ci.yml` 只有编译／单测／两道门禁），签发脚本 `tools/sign-rootfs-index.py`
   **两仓都查无此件**（两边 `git ls-files` 各 0 命中）。⇒ 主仓 `latest`（App 取资产的那条滚动 Release，
   与固定版本 `v2.2.1` **是两条不同的 Release、资产不是一套**，逐件读数见下面改订二那段）上的 rootfs 与
   `rootfs-index.json` 是**存量资产**：新装能装上，但环境包与索引从此不会有更新。
   **待补**：要么在本仓落一条 rootfs 发布流水线（构建 → 生成索引 → 签发 → 上传），要么明确决定不再更新环境包 ——
   在两者落地前，别把「App 能装环境」当成「更新链还在」。
   **裁定（2026-10-28，第二百六十批改订二，蓝总管）**：走**前者** —— 发布链**在本仓重建**，主仓侧**声明不再更新**。
   README「环境包从哪来」那句按此写（「主仓 Releases 里的 rootfs 资产自 2026-10-28 起冻结，不再更新；新环境包见
   zhengdao-term Releases」），并且明确**不删主仓旧资产**（老版本 App 装机还要按老 URL 取，删了会把老版本的
   环境安装一起打断）。搬迁本身是她的在飞动作：把主仓那批 rootfs 资产发进 `zhengdao-term` Releases，再把
   `RootfsIndex.kt:133` 的索引 URL 改指过来。
   ⇒ **但这条缺口还没闭合**，本节仍是「断／待补」，实测现状（2026-10-28 取 `api.github.com`）：
   - 本仓 Releases：`release count: 1`，只有 `v0.1.0` 的 1 件资产（APK 38,897,816 B），**rootfs 资产 0 件**；
   - 主仓 `latest`（App 真正取资产的那条 URL）：33 件资产里 rootfs **8 件**——`debian-13.7-base-arm64.tar.zst`
     200,223,802 B 与其 `.sha256`（65 B）、`debian-13.7-copyright.tar.zst` 326,575 B 与其 `.sha256`、
     `rootfs-index.json` 469 B、`rootfs-index.json.sig` 88 B、`rootfs-manifest.txt` 2,117,771 B 与其 `.sha256`，
     `updated_at` **全是 2026-10-23T06:42**（此后再没动过）；同日动过的只有 APK
     （`zhengdao-2.2.1-release.apk` 重传于 2026-10-28T02:59:43Z，7,535,889 B）；
   - 主仓 `v2.2.1`（固定版本那条）：8 件、`updated_at` 2026-10-23T13:53Z，其中 rootfs 只有 **7 件**——
     **没有 `rootfs-index.json.sig`**。⇒ 签名资产只在 `latest` 上有，这两条不是同一套文件，登记时别合并成一个数。
   在流水线真跑通、URL 真改指之前，§8.4 这条不许读成「已解决」；B-T7 的「别真改 URL」红线仍在，本批没动那个字面量。
