// 独立开发声明：本文件为本项目原创，不含对第三方源码的拷贝。
package com.example.zhengdao.terminal

import com.example.zhengdao.SrcRoots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1 P1（2026-10-28）的门禁：**联网安装路径上，下载完不再单独整包哈希一遍**。
 *
 * 为什么这条只能用源码门禁钉：`TerminalActivity.startInstall` 是一个跑在工作线程里的
 * 私有方法，要执行它就得有 Context、网络、191MB 的发布资产和一台真机 —— JVM 单测里
 * 一个都造不出来。能钉住的是**接线**：那一遍 sha 有没有被 [RootfsInstaller.needsPreExtractVerify]
 * 这道开关管起来、开关查的是不是 Rust 可用性、以及它在「下载完 → 开始解压」之间。
 * 真正的耗时读数与「改一字节仍被拦下」由真机取证给（见交付报告）。
 *
 * 完整性没有降级，只是换了地方：`RootfsInstaller.install(…, expectedSha)` 把索引的 sha256
 * 交给 Rust，`CoreNative.extract` 开解前对**恰好被解压的那些字节**做一遍流式哈希并对账
 * （不匹配抛 ShaMismatch ⇒ 安装失败）。信任锚因此比"另一次遍历"更硬，而不是更松。
 */
class InstallShaGateTest {

    /** CRLF 归一：本仓文件在盘上是 CRLF，多行匹配一律按 `\n` 比。 */
    private val src =
        SrcRoots.find("src/main/java/com/example/zhengdao/TerminalActivity.kt").readText()
            .replace("\r\n", "\n")

    /** `startInstall(url)` 那一段（到下一个 `private fun` 为止）。 */
    private val startInstall: String = run {
        val i = src.indexOf("private fun startInstall(url: String)")
        assertTrue("找不到 startInstall（函数改名了？门禁要跟着改）", i >= 0)
        val j = src.indexOf("\n    private fun ", i + 1)
        assertTrue("找不到 startInstall 的结尾（下一个 private fun）", j > i)
        src.substring(i, j)
    }

    @Test
    fun `门禁：下载完那遍无条件全量 sha 已经去掉，不是被注释掉也不是被挪走`() {
        val removedUnconditionalPair =
            "                    RootfsDownloader.verifySha256(archive, expectedSha)\n" +
                "                    installStatus(\"SHA256 校验通过（来源：\${allow.source}）\")"
        assertFalse(
            "旧代码是「下载 → 无条件 verifySha256 → 报校验通过」；T1 P1 之后这一对只许出现在 if 分支里（缩进更深）",
            src.contains(removedUnconditionalPair),
        )
        assertEquals(
            "联网安装路径上只该剩两处 verifySha256：缓存命中检查（判断要不要重下）＋ Rust 不可用时的补校验",
            2,
            Regex("RootfsDownloader\\.verifySha256\\(").findAll(startInstall).count(),
        )
    }

    @Test
    fun `门禁：补不补那一遍 sha 由 needsPreExtractVerify 决定，查的是 Rust 可用性`() {
        assertTrue(
            "开关必须是 RootfsInstaller.needsPreExtractVerify（纯函数，:terminal-core 侧有单测钉住口径）",
            startInstall.contains("RootfsInstaller.needsPreExtractVerify("),
        )
        assertTrue(
            "开关的输入必须是 Rust 核心的可用性，不是别的什么标志",
            startInstall.contains("com.example.zhengdao.rust.CoreNative.isRustAvailable()"),
        )
        assertTrue(
            "Rust 不可用那一支仍要自己校验一遍（那条路上没有第二道完整性关）",
            startInstall.contains("RootfsDownloader.verifySha256(archive, expectedSha)"),
        )
    }

    @Test
    fun `门禁：开关的位置在「下载完 → 开始解压」之间，且期望值照样交给安装内核`() {
        val iDownload = startInstall.indexOf("RootfsDownloader.download(")
        val iGate = startInstall.indexOf("needsPreExtractVerify(")
        val iExtractStatus = startInstall.indexOf("installStatus(\"开始解压")
        val iInstall = startInstall.indexOf("RootfsInstaller.install(")
        assertTrue("下载调用不见了", iDownload >= 0)
        assertTrue("「开始解压」那行状态不见了", iExtractStatus >= 0)
        assertTrue("安装调用不见了", iInstall >= 0)
        assertTrue(
            "开关必须在下载之后：下载前还没文件可校验（顺序 下载=$iDownload 开关=$iGate 解压=$iExtractStatus 安装=$iInstall）",
            iDownload < iGate,
        )
        assertTrue(
            "开关必须在开始解压之前：解压之后才校验就等于白解一遍（$iGate vs $iExtractStatus）",
            iGate < iExtractStatus,
        )
        assertTrue(
            "索引的 sha256 必须照样传进安装内核，由 Rust 在解包时对账（完整性没被去掉，只是换了地方）",
            startInstall.contains("RootfsInstaller.install(appContext, archive, envToWrite, expectedSha)"),
        )
    }

    @Test
    fun `门禁：Rust 可用时用户看到的那行状态要说清「完整性随解包校验」`() {
        assertTrue(
            "不能什么都不说——用户刚看完下载百分比，接着静默几分钟会以为卡死了",
            startInstall.contains("installStatus(\"下载完成，完整性随解包流式校验（来源：\${allow.source}）\")"),
        )
    }

    @Test
    fun `门禁：那道开关的条件就是 needsPreExtractVerify(Rust 可用性)，不是被写死的真假`() {
        // 只断言"两个符号都出现过"是不够的：把它们分别挪进 if (true) / 注释里也能骗过去。
        // 这里把连续空白压平后比**整条条件**，写死真假或换掉输入都会当场红。
        assertTrue(
            "条件必须逐字是 needsPreExtractVerify(CoreNative.isRustAvailable())——写成 if (true) 就等于把 T1 P1 撤了",
            flat(startInstall).contains(
                "if (RootfsInstaller.needsPreExtractVerify( com.example.zhengdao.rust.CoreNative.isRustAvailable(), ) ) {",
            ),
        )
    }

    /** 把连续空白压成一个空格：门禁比的是接线形状，不该被换行/缩进的改动带着走。 */
    private fun flat(s: String): String = s.replace(Regex("\\s+"), " ")
}
