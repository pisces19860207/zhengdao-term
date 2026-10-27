# 来源与出处声明（PROVENANCE）

本仓（`zhengdao-term`）是「证道」拆出的独立终端 App。这份文件回答一个问题：
**仓库里的东西都是从哪儿来的、各是什么许可。** README 的「许可与第三方」一节指向这里。

当前状态：**骨架期（0.1.0）**——仓里只有本仓自己写的壳页、图标与构建脚本。

## 1. 第一方代码（本项目原创）

| 路径 | 内容 | 许可 |
|---|---|---|
| `app/src/main/java/com/example/zhengdao/term/**` | 壳页（`MainActivity` + Compose 主题） | 见 `LICENSE`（保留所有权利） |
| `app/src/main/res/**` | 图标、文案、主题、颜色 | 同上 |
| `app/src/main/AndroidManifest.xml`、`*.gradle.kts`、`gradle/**` | 工程配置 | 同上 |
| `README.md`、`LICENSE`、`PROVENANCE.md`、`THIRD-PARTY-LICENSES.md` | 文档 | 同上 |

图标来源：`assets/icon/`（本仓自建，SVG 源 + 512 PNG + Android 自适应图标）。
**不是**从证道主仓拷的——主仓是白底「证」+ 绿光标，本仓是暗底 `>_`，刻意不同色系。

## 2. 依赖（Maven，构建时拉取，源码不进仓）

| 组件 | 版本 | 许可 |
|---|---|---|
| AndroidX `core-ktx` / `activity-compose` | 见 `gradle/libs.versions.toml` | Apache-2.0 |
| Compose BOM / `ui` / `ui-graphics` / `material3` | 同上 | Apache-2.0 |
| Kotlin 标准库与 Compose 编译器插件 | 2.2.10 | Apache-2.0 |

骨架期**刻意不引**任何第三方库（OkHttp / commons-compress / zstd 等）——它们属于终端内核，
随第四刀一起进来。

## 3. 将来会迁入的第三方组件（现在仓里**没有**）

终端内核整模块迁入时（第四刀），下列组件会随代码/二进制进入本仓，届时：

| 组件 | 许可 | 形态 |
|---|---|---|
| Termux `terminal-emulator` / `terminal-view` | Apache-2.0 | 源码（`com.termux.**`）＋ 自编 JNI |
| PRoot | GPL-2.0-or-later | 预编二进制，聚合分发（独立进程 exec，不链接） |
| libtalloc | LGPL-3.0-or-later | 预编二进制，proot 进程内动态加载 |
| libandroid-shmem | BSD-3-Clause | 预编二进制 |
| zstd / zstd-jni | BSD-3-Clause / BSD-2-Clause | 预编二进制 + Maven |
| JetBrains Maple Mono（子集） | SIL OFL 1.1 | 字体资源 |
| Debian 13.7 rootfs | 各包各自许可 | Release 资产（不进 git） |

**迁入时的义务**（写在这里，免得到时候忘）：
1. 许可证正文落到 `licenses/`，并随 APK 内置（App 内可离线阅读）；
2. `THIRD-PARTY-LICENSES.md` 逐组件补"用了什么、怎么链接、义务是什么"；
3. 二进制入库时记录构建来源（编译器/NDK 版本、sha256），与主仓 `tools/so-provenance.json` 同口径。

## 4. 与证道主仓的关系

本仓**不包含**证道主仓的任何代码。两边的关系只是"同一个作者、同一个品牌、可并存的安装包"。
主仓的 `THIRD-PARTY-LICENSES.md` / `PROVENANCE.md` 覆盖的是主仓（抱朴 ＋ 玄览），不覆盖本仓。
