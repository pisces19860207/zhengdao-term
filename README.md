# 证道终端（Zhengdao Terminal）

从 [证道](https://github.com/pisces19860207/zhengdao) 拆出的**独立终端 App**：真 PTY、Debian 13.7 rootfs、PRoot 免 root。证道本体只保留认知型 harness（抱朴）与浏览器（玄览）；要 shell 的时候，用这个。

> **当前状态**：仓库刚拆出，代码迁移中；首个可安装版本以 [Releases](https://github.com/pisces19860207/zhengdao-term/releases) 为准（还没发就是还没有）。

## 它是什么

一个装在手机上的 Linux 终端，不是远程 SSH，也不是网页终端：

- **真 PTY**：终端仿真基于 Termux `TerminalView`，vim / htop / tmux 这类全屏程序正常跑。
- **真 Debian**：首次在 App 内下载 Debian 13.7（trixie）rootfs（约 200 MB，需联网），之后 `apt` 可用。
- **免 root**：PRoot 把 rootfs 映射进来，不需要 root、不需要 Termux、不需要 Shizuku，也不用连电脑。
- **单会话 tmux**：固定一个 tmux 会话；App 被杀重开后会话能接回来（不丢现场）。
- **Agent 安装卡**：Hermes / Claude Code / Codex / AGY CLI 都**不是预装**，点了安装才有；不装就不占地方。

## 与证道的关系

| | 证道（主仓） | 证道终端（本仓） |
|---|---|---|
| 定位 | 认知型 harness：抱朴 ＋ 玄览 | 终端：PTY ＋ rootfs ＋ proot |
| 跑命令 | 不跑 shell、不起子进程 | 就是用来跑命令的 |
| 安装包 | 一个 APK，Agent 不依赖终端 | 独立 APK，可与证道并存 |

两边可以装在同一台手机上，互不依赖；证道不会替终端做决定，终端也不接管抱朴。

## 要求

- **Android 16（API 36）或更高版本**
- **arm64-v8a** 设备（只提供 arm64 单架构包）
- 首次装环境需联网（约 200 MB）；之后终端可离线用

## 使用

1. 从 [Releases](https://github.com/pisces19860207/zhengdao-term/releases) 下载 APK 安装。
2. 首次打开，授权存储等权限；在 App 内下载 Debian 环境。
3. 下载完成后进入终端；`apt update`、`apt install` 按需装包。
4. 需要第三方 Agent 时，在安装卡里点安装，装好才有。

环境包支持断点续传与差分更新；网络抖动不用从头下。

## 安全（照实说）

⚠️ **终端不是安全沙箱**：里面的进程与 App 是同一个 Android UID、同一个内核与网络栈，能读写 App 能读写的文件。只运行你信任的命令；不要拿它跑来源不明的脚本。

## 许可与第三方

- 本仓第一方代码为项目原创；自 2026-10-11 起「保留所有权利」，只允许浏览/克隆源码与安装官方包，不允许自行构建、修改、反向工程或再分发（法律强制允许的除外）；商业使用需事先取得书面许可。
- 随包第三方组件各按其许可证分发：
  - Termux `terminal-emulator` / `terminal-view`（Apache-2.0）
  - PRoot（GPL-2.0，聚合分发）
  - Debian rootfs 及其中的软件包（各自许可证）
  - JetBrains Maple Mono 字体（OFL-1.1）
- 许可证正文随安装包内置；完整清单见主仓 `THIRD-PARTY-LICENSES.md` 与 `PROVENANCE.md`。

## 致谢

终端层建立在 Termux、PRoot、Debian 与这些开源项目之上；没有它们就没有「手机上免 root 跑 Linux」这件事。

---

**English (short)**: Zhengdao Terminal is a standalone Android terminal app split out of [Zhengdao](https://github.com/pisces19860207/zhengdao): a real PTY, a Debian 13.7 rootfs and PRoot, no root, no Termux, no Shizuku. Zhengdao itself keeps only the agent harness (Baopu) and the browser (Xuanlan). Requires Android 16+ on arm64. The terminal is **not a security sandbox** — run only commands you trust. Code migration is in progress; installable builds will appear under Releases.
