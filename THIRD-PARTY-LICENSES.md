# 第三方组件清单（THIRD-PARTY-LICENSES）

本仓的第三方组件、它们的许可证、以及我们**怎么用**它们（链接方式决定义务范围）。
README 的「许可与第三方」一节指向这里；来源与改动登记见 [`PROVENANCE.md`](PROVENANCE.md)，
正文见 [`licenses/`](licenses/)，第一方许可见 [`LICENSE`](LICENSE)。

> **当前状态（2026-10-27，第四刀交付后）**：终端内核已在仓内 —— 下表 §2 是**构建期依赖**，
> §3 是**在仓的第三方源码**，§4 是**在仓的第三方二进制**，§5 是**资源**，§6 是**不在仓但随分发物**。
> 全部条目都是现在的事实，没有「将来会有」。

## 1. 一句话结论

- **Apache-2.0**（Termux 终端组件、OkHttp、Commons、AndroidX/Compose/Kotlin）：
  义务是保留版权与许可声明、附带 NOTICE（若有）⇒ 由 `licenses/Apache-2.0.txt` ＋
  `licenses/NOTICES.txt` 覆盖。
- **GPL-2.0（proot）与 LGPL-3.0（libtalloc）** 都是**二进制**，以「独立进程 `execve` /
  进程内 `dlopen`」方式使用 ⇒ 属**聚合分发**，**不传染**第一方代码；义务是随分发物提供
  对应正文与源码获取方式（见 §7）。
- **BSD 系**（libandroid-shmem / zstd / zstd-jni）：保留版权与免责声明即可。

## 2. 构建期依赖（Maven，源码不进仓）

| 组件 | 许可 | 用法 | 义务 | 正文 |
|---|---|---|---|---|
| AndroidX `core-ktx` / `activity-compose` | Apache-2.0 | 编译进 APK | 保留声明 | `Apache-2.0.txt` |
| Compose（`ui` / `ui-graphics` / `material3` / BOM） | Apache-2.0 | 编译进 APK | 保留声明 | 同上 |
| Kotlin 标准库 / Compose 编译器插件 | Apache-2.0 | 编译进 APK | 保留声明 | 同上 |
| OkHttp | Apache-2.0 | 编译进 APK（rootfs 断点续传） | 保留声明 ＋ 其内置 Public Suffix List 的 NOTICE | `Apache-2.0.txt` ＋ `NOTICES.txt` |
| Apache Commons Compress | Apache-2.0 | 编译进 APK（解 tar） | 保留声明 ＋ NOTICE | 同上 |
| Apache Commons Lang3 | Apache-2.0 | 编译进 APK | 保留声明 | `Apache-2.0.txt` |
| zstd-jni（Java 包装） | BSD-2-Clause | 编译进 APK | 保留版权与免责声明 | `BSD-2-Clause.txt` |

版本以 `gradle/libs.versions.toml` 为准（**单一真源**，本文件不重复版本号，免得两处漂移）。

## 3. 在仓的第三方源码

| 组件 | 许可 | 用法 | 义务 | 正文 |
|---|---|---|---|---|
| Termux `terminal-emulator` / `terminal-view` / `textselection`（v0.119.0-beta.3，`terminal-core/src/main/java/com/termux/**`） | Apache-2.0 | **源码衍生**，编译进 APK；做了接入适配 | 保留版权/NOTICE；**标注改动**（改动点在各文件头注释，汇总见 `PROVENANCE.md` §3）；**不得暗示上游为我们的修改背书** | `Apache-2.0.txt` |
| Termux 伪终端 JNI native 半边（`terminal-core/src/main/cpp/termux/termux.c` → `libtermux.so`） | Apache-2.0 | 随本仓 CMake 编译进 APK | 同上 | 同上 |

> 只有 termux-app **主应用本体**是 GPL-3.0，本项目**没有**聚合它。

## 4. 在仓的第三方二进制（`terminal-core/src/main/jniLibs/arm64-v8a/`）

| 组件 | 许可 | 用法 | 义务 | 正文 |
|---|---|---|---|---|
| PRoot（`libproot.so` / `libproot-loader.so`） | GPL-2.0-or-later | **预编二进制**，运行时释放到磁盘、以**独立进程** `execve` | 聚合分发：随分发物提供 GPL-2.0 正文 ＋ **源码获取方式**（上游仓库地址，见 §7）；**不要求**第一方代码开源 | `GPL-2.0.txt` |
| libtalloc（`libtalloc.so`） | LGPL-3.0-or-later | 预编二进制，proot 进程内 `dlopen` | 允许用户替换该库（它不在第一方链接链上）；提供正文 ＋ 源码获取方式；LGPL-3.0 第 2 条要求并入的 GPL-3.0 正文也随附 | `LGPL-3.0.txt` ＋ `GPL-3.0.txt` |
| libandroid-shmem（`libandroid-shmem.so`） | BSD-3-Clause | 预编二进制，proot 进程内 `dlopen` | 保留版权与免责声明；不得用作者名义背书 | `BSD-3-Clause.txt` |
| zstd 本体（编译进 `libzstd-jni-1.5.6-4.so`） | BSD-3-Clause | 预编二进制 | 同上 | `BSD-3-Clause-zstd.txt` |
| zstd-jni 的 native 半边（同一个 `.so`） | BSD-2-Clause | 预编二进制 | 保留版权与免责声明 | `BSD-2-Clause.txt` |

> 同一个 `.so` 里有两份**版权人不同**的 BSD 文本（zstd-jni 与 zstd），谁也不能替谁履行 ⇒ 两份都随附。

## 5. 在仓的第三方资源

| 组件 | 许可 | 用法 | 义务 | 正文 |
|---|---|---|---|---|
| JetBrains Maple Mono NF（子集，Regular） | SIL OFL 1.1 | 终端默认字体（`app/src/main/assets/fonts/`） | 保留字体名与版权声明；改名分发需注意 OFL 的保留字体名条款 | 同目录 `OFL.txt` |

## 6. 不在仓、但属于分发物

| 组件 | 许可 | 形态 | 义务 |
|---|---|---|---|
| Debian 13.7 rootfs | 各 Debian 包各自许可 | **运行时下载**（或用户自备缓存包），不进 git | 不修改其内容、不重打包；随包附带其 `/usr/share/doc/*/copyright`；本仓只做下载/校验/解压 |

## 7. 义务怎么落地（含**尚未落地**的部分）

已落地：

1. 第三方正文入库 `licenses/`（8 份 ＋ 字体 `OFL.txt`），逐份记字节数 / 行数 / SHA256，见 `licenses/README.md`；
2. GPL/LGPL 组件的**源码获取方式**写在 `PROVENANCE.md` §4（上游仓库 + 我们只做预编入库、未改源码）；
3. 二进制入库时记 sha256（`PROVENANCE.md` §4 表），CI 做存在性 + 16KB 页对齐检查。

**尚未落地（如实登记，别当已完成）**：

4. 正文**还没有装配进 APK**（主仓用 `stageLicenseAssets` 把 `licenses/` 拷进 `assets/licenses/`，
   本仓未做）⇒ 只拿到安装包的人读不到全文，这对 GPL/LGPL 组件是**不合规**的；
5. App 内**没有**「开源许可」阅读页（主仓有 `OpenSourceLicensesActivity`）；
6. proot 的源码获取方式**只在文档里**，App 关于页还没有这句话。

> 4、5、6 一起做才算合规闭环，列为下一批必办；本文件与 `licenses/README.md` 同步登记同一组缺口。

## 8. 不属于第三方的东西

- `assets/icon/**`、`app/src/main/res/**` 的图标与文案：本仓自建，属第一方。
- `terminal-core/src/main/java/com/example/zhengdao/**`：从证道主仓搬来的**第一方**代码
  （同一作者），不涉及第三方义务。
- 证道主仓的 `zhengdao-core` / `zhengdao-native` AAR：同一作者的第一方构件，见 `PROVENANCE.md` §2。
