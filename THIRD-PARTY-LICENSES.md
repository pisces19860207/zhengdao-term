# 第三方组件清单（THIRD-PARTY-LICENSES）

本仓的第三方组件、它们的许可证、以及我们**怎么用**它们（链接方式决定义务范围）。
README 的「许可与第三方」一节指向这里；来源说明见 `PROVENANCE.md`，第一方许可见 `LICENSE`。

> **当前状态：骨架期（0.1.0）。** 仓里还没有终端组件——只有 Maven 依赖。
> 下表第 2 节是**现在就有**的；第 3 节是**迁入后会有**的，义务已提前写清。

## 1. 一句话结论

- 骨架期的第三方件全是 **Apache-2.0**（AndroidX / Compose / Kotlin），义务只有两条：
  保留版权与许可声明、附带 NOTICE（若有）。两者都由 APK 的 Maven 元数据与 `licenses/` 承担。
- 终端内核迁入后新增 **GPL-2.0（proot）** 与 **LGPL-3.0（libtalloc）** 两类**二进制**：
  它们都以「独立进程 exec / 动态加载」的方式使用，属聚合分发，**不传染**第一方代码。

## 2. 现在就有（构建期依赖）

| 组件 | 许可 | 用法 | 义务 |
|---|---|---|---|
| AndroidX `core-ktx` | Apache-2.0 | 编译进 APK | 保留声明 |
| AndroidX `activity-compose` | Apache-2.0 | 编译进 APK | 保留声明 |
| Compose（`ui` / `ui-graphics` / `material3` / BOM） | Apache-2.0 | 编译进 APK | 保留声明 |
| Kotlin 标准库 / Compose 编译器插件 | Apache-2.0 | 编译进 APK | 保留声明 |

版本以 `gradle/libs.versions.toml` 为准（**单一真源**，本文件不重复版本号，免得两处漂移）。

## 3. 迁入后会有（第四刀，现在仓里没有）

| 组件 | 许可 | 用法 | 义务 |
|---|---|---|---|
| Termux `terminal-emulator` / `terminal-view` | Apache-2.0 | 源码衍生（`com.termux.**`）＋ 自编 `libtermux.so` | 保留版权/NOTICE；标注改动 |
| PRoot（`libproot.so` 等四件套） | GPL-2.0-or-later | **预编二进制**，运行时释放到磁盘、以独立进程 `exec` | 聚合分发：提供对应源码获取方式（上游仓库地址），不要求第一方开源 |
| libtalloc | LGPL-3.0-or-later | 预编二进制，proot 进程内 `dlopen` | 允许替换该库；提供其源码/获取方式 |
| libandroid-shmem | BSD-3-Clause | 预编二进制 | 保留版权与免责声明 |
| zstd（`libzstd-jni-*.so`） | BSD-3-Clause | 预编二进制 | 保留版权与免责声明 |
| zstd-jni（Java 包装） | BSD-2-Clause | Maven | 保留版权与免责声明 |
| JetBrains Maple Mono（子集） | SIL OFL 1.1 | 字体资源 | 保留字体名与版权；不改名分发需注意 OFL 的保留字体名条款 |
| Debian 13.7 rootfs | 各包各自许可 | Release 资产（不进 git） | 随包附带其许可证目录 |

### 义务怎么落地

1. 每个组件的许可证正文放 `licenses/<组件>.txt`，并随 APK 内置（App 内可离线阅读）；
2. GPL/LGPL 组件的**源码获取方式**写在 README 与 App 的关于页（上游仓库 + 我们改了什么）；
3. 二进制入库时记 sha256 与构建输入（编译器/NDK 版本），与主仓 `tools/so-provenance.json` 同口径；
4. 每次升级组件版本，同步改本表与 `PROVENANCE.md`。

## 4. 不属于第三方的东西

- `assets/icon/**` 与 `app/src/main/res/**` 的图标：本仓自建，属第一方。
- 证道主仓的代码：**不在本仓**。本仓不复制、不引用主仓源码（`:core` 的复用方式在第五刀定，
  届时按"同一作者的第一方代码"处理，不涉及第三方义务）。
