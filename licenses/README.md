# 许可证正文（licenses/）

> **这个目录是什么**：本仓**随分发物提供**的第三方许可证正文。凡是聚合进本 App 的组件，
> 其许可证要求的**文本副本**放在这里，逐份可核（Apache-2.0 第 4(a) 条、GPL/LGPL 第 3 条、
> BSD 系 2/3-Clause 的「复现声明」要求）。Apache-2.0 第 4(d) 条要求的 **NOTICE 归属声明**
> 也在本目录（`NOTICES.txt`，照录上游原文）。
> **谁需要哪份正文**：见仓库根 [`THIRD-PARTY-LICENSES.md`](../THIRD-PARTY-LICENSES.md)。
> **来源与改动登记**：见仓库根 [`PROVENANCE.md`](../PROVENANCE.md)。

## 一、正文清单（与证道主仓同一批取回，逐字节相同）

| 文件 | 许可证 | 覆盖的组件 | 字节数 | 行数 | SHA256（小写） |
|---|---|---|---|---|---|
| [`Apache-2.0.txt`](Apache-2.0.txt) | Apache License 2.0 | Termux `terminal-emulator` / `terminal-view` / `libtermux.so`；OkHttp；Apache Commons Compress / Lang3；AndroidX / Compose / Kotlin | 11,358 | 202 | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` |
| [`GPL-2.0.txt`](GPL-2.0.txt) | GNU GPL v2.0 | PRoot（`libproot.so` / `libproot-loader.so`，Termux fork 发行版） | 17,984 | 338 | `edaef632cbb643e4e7a221717a6c441a4c1a7c918e6e4d56debc3d8739b233f6` |
| [`GPL-3.0.txt`](GPL-3.0.txt) | GNU GPL v3.0 | 满足 LGPL-3.0 第 2 条「并入 GPL-3.0」的要求（libtalloc） | 35,149 | 674 | `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986` |
| [`LGPL-3.0.txt`](LGPL-3.0.txt) | GNU LGPL v3.0 | libtalloc（在 proot 进程内动态加载） | 7,652 | 165 | `e3a994d82e644b03a792a930f574002658412f62407f5fee083f2555c5f23118` |
| [`BSD-2-Clause.txt`](BSD-2-Clause.txt) | BSD 2-Clause | zstd-jni（`libzstd-jni-1.5.6-4.so` ＋ Maven 包装） | 1,360 | 26 | `c6a6a8926f2f1732603e4b75779a5f6ce79238eb79bc36dcc5b7feb509bc6cd9` |
| [`BSD-3-Clause.txt`](BSD-3-Clause.txt) | BSD 3-Clause | libandroid-shmem（proot 进程内动态加载） | 1,529 | 28 | `0dd43abe17196b0224cb0b159819af798cd508a82a21e5870126fff4e899a7eb` |
| [`BSD-3-Clause-zstd.txt`](BSD-3-Clause-zstd.txt) | BSD 3-Clause | **zstd 本体**（编译进同一个 `libzstd-jni-*.so`，版权人与上一行不同） | 1,549 | 30 | `7055266497633c9025b777c78eb7235af13922117480ed5c674677adc381c9d8` |
| [`NOTICES.txt`](NOTICES.txt) | 第三方 NOTICE 汇总（**不是**许可证全文） | Apache Commons Compress 的 `META-INF/NOTICE.txt` ＋ OkHttp 内置 Public Suffix List 的 `publicsuffix/NOTICE` | 2,198 | 36 | `3ef191d960df2fd2f38120d9fb2da062491348a6fc6cb175d2eb6d9d8eea1ba4` |

> 📌 `BSD-3-Clause.txt` 是上游 `LICENSE` 的**原文照录**：第三条里的 `{organization}` 是上游自己
> 没填的占位符。本项目刻意**不改正文** —— 改了就不再是「逐字节可核」的那一份。

### 不放在本目录、但同样是「随附正文」的两份

| 正文 | 位置 | 覆盖 | 字节数 | SHA256（小写） |
|---|---|---|---|---|
| SIL OFL 1.1 | [`app/src/main/assets/fonts/OFL.txt`](../app/src/main/assets/fonts/OFL.txt) | JetBrains Maple Mono（终端默认字体） | 4,572 | `6728aae70e0be6316b28681c5a806827b4d7daafe45fb767b932c790216c2533` |
| 第一方声明 | [`../LICENSE`](../LICENSE) | 本仓自写代码（保留所有权利） | 见文件 | 见文件 |

> 字体那份**故意留在字体同目录**：OFL 的惯例是「正文随字体一起分发」，而它在本仓是
> assets 资源、不是第三方组件目录。

## 二、校验

```bash
cd <仓库根>
sha256sum licenses/*.txt app/src/main/assets/fonts/OFL.txt     # 与上表逐一比对
```

`.gitattributes` 里 `* text=auto eol=lf`，所以任何平台检出后字节数一致；
若对不上，先 `git ls-files --eol licenses` 看属性是否生效，**不要改上表的数字去迁就工作区**。

## 三、已知缺口（2026-10-27 如实登记）

1. **正文尚未装配进 APK**：证道主仓用 `app/build.gradle.kts` 的 `stageLicenseAssets` 任务
   把正文拷进 `assets/licenses/`，本仓**还没做**这一步 —— 目前正文只随源码仓分发。
   只拿到安装包、不联网的人读不到全文，这一点对 GPL/LGPL 组件是不合规的，列为下一批必办。
2. **App 内没有「开源许可」阅读页**：主仓有 `OpenSourceLicensesActivity`，本仓壳页还没有入口。
3. 缺口 1、2 一起做才完整（装配进包 ＋ 能读），单独做任一半都不算交付。

## 四、维护规则

1. 新增聚合进 APK 的第三方组件 ⇒ 先确认它要哪份正文，正文放进本目录，**同步更新上表**
   （字节数、行数、SHA256），并在 `THIRD-PARTY-LICENSES.md` 加一行。
2. 正文一律取官方原文，不自行排版、不改换行、不加 BOM。
3. 升级组件版本 ⇒ 复核许可证是否变更；换正文则同步更新摘要。
4. 本目录只放**正文**，不放第三方源码；解释性文档在仓库根两份文件里。
