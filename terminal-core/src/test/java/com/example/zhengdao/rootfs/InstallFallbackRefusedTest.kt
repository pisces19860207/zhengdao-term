// 独立开发声明：本文件为本项目原创，不含对第三方源码的拷贝。
package com.example.zhengdao.rootfs

import com.example.zhengdao.SrcRoots
import com.example.zhengdao.rust.CoreNative
import com.example.zhengdao.testkit.TestAssume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * T1（2026-10-28）：**P1 去掉下载后那遍全量 sha ＋ P2 Rust 不可用时不静默回退**。
 *
 * 用户原话：「原先 rust 解压超级快，回退了超级慢」。旧行为是「Rust 不可用/失败 ⇒ 悄悄落回
 * commons-compress 慢路径」，代价有两条：用户端只看到"这次装得特别久"、一行解释都没有；
 * 而 Java 路径**根本不校验 sha256**，"回退成功"＝把一次校验过的安装悄悄换成一次没校验的安装。
 *
 * 本族测试分三层，因为三层能证的事不一样：
 * 1. **纯函数**（[RootfsInstaller.needsPreExtractVerify] / [RootfsInstaller.fallbackRefusedMessage]）
 *    —— 口径本身，任何机器都能跑，措辞逐字钉住；
 * 2. **行为** —— JVM 里 `libzhengdao_core.so` 天然加载不了，正好是 P2 的负例现场：
 *    证明它真的抛、真的一个条目都不落盘、真的不留半截 tmp 树；
 * 3. **源码门禁** —— 证明生产路径上再没有 `extractArchiveJava(` 这个调用点，
 *    且 `FALLBACK_REFUSED` 这个证据标记真的写在代码里（不是只写在交付报告里）。
 *
 * ⚠️ 第 2 层的前提是「Rust 不可用」。真机/仪器测试环境里它可能可用，那时用例按
 * [TestAssume] 的规矩记 **skipped**（并说清缺什么），不冒充通过。
 */
class InstallFallbackRefusedTest {

    // ── 1. 纯函数口径 ────────────────────────────────────────────────────────

    @Test
    fun `P1 口径：Rust 可用就不再自己整包哈希，只有不可用时才补一遍`() {
        assertFalse(
            "Rust 可用时不该再整包哈希：CoreNative.extract 开解前已对整包做流式 sha256 并与期望值对账",
            RootfsInstaller.needsPreExtractVerify(rustAvailable = true),
        )
        assertTrue(
            "Rust 不可用时必须由调用方自己校验一遍——那条路上没有第二道完整性关",
            RootfsInstaller.needsPreExtractVerify(rustAvailable = false),
        )
    }

    @Test
    fun `P2 文案：拒绝回退时必须说清「没装成」、原因、以及下一步动作`() {
        val text = RootfsInstaller.fallbackRefusedMessage(
            why = "解包组件未加载：libzhengdao_core.so 不可用",
            hint = "重装 App 后再试",
        )
        assertTrue("要带上具体原因，否则用户与排障都不知道发生了什么：$text", text.contains("解包组件未加载"))
        assertTrue("要明说没装成，不能让人以为装好了：$text", text.contains("本次安装未生效"))
        assertTrue("要明说拒绝的是哪条路：$text", text.contains("已拒绝回退慢速 Java 解包路径"))
        assertTrue("要给出下一步动作：$text", text.contains("重装 App 后再试"))
    }

    @Test
    fun `P2 文案：不传提示时用默认的「重进 App 可再试」，整句逐字钉住`() {
        val why = "Rust 解包流水线失败：tar: 意外的文件结尾"
        val text = RootfsInstaller.fallbackRefusedMessage(why)
        assertEquals(
            "这句话是直接给用户看的（终端页横幅 / 安装失败弹窗），改措辞要过眼睛 ⇒ 逐字钉住",
            "环境解包失败（$why）：已拒绝回退慢速 Java 解包路径，本次安装未生效（重进 App 可再试）",
            text,
        )
    }

    // ── 2. 行为：JVM 里 Rust 天然不可用，正好是 P2 的负例现场 ─────────────────

    @Test
    fun `P2 行为：Rust 不可用时 extractArchive 明确失败，且一个条目都不落盘`() {
        TestAssume.assume(
            !CoreNative.isRustAvailable(),
            "本机 JVM 里 Rust 核心居然可用（libzhengdao_core.so 被加载了）⇒「拒绝回退」的负例前提不成立",
        )
        val root = Files.createTempDirectory("zd-t1-refuse").toFile()
        try {
            // 内容无关紧要：拒绝发生在读归档之前（.so 没加载 ⇒ 连魔数都不看）
            val archive = File(root, "debian-13.7-base-arm64.tar.zst").apply {
                writeText("不是真归档：T1 的拒绝发生在读它之前")
            }
            val dest = File(root, RootfsInstaller.TMP_NAME)

            val e = assertThrows(RootfsInstaller.InstallFailed::class.java) {
                RootfsInstaller.extractArchive(archive, dest)
            }
            val msg = e.message ?: ""
            assertTrue("文案要说清是解包组件没加载：$msg", msg.contains("解包组件未加载"))
            assertTrue("文案要说清没有回退慢路径：$msg", msg.contains("已拒绝回退慢速 Java 解包路径"))
            assertTrue("文案要说清本次没装成：$msg", msg.contains("本次安装未生效"))
            assertTrue(
                "拒绝回退就必须一个条目都不落盘——半截树会被下次启动误认成环境：${dest.listFiles()?.map { it.name }}",
                !dest.exists() || (dest.listFiles() ?: emptyArray()).isEmpty(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `P2 行为：installInto 拒绝回退时不留半截 tmp 树，也不碰现有环境`() {
        TestAssume.assume(
            !CoreNative.isRustAvailable(),
            "本机 JVM 里 Rust 核心居然可用 ⇒「拒绝回退」的负例前提不成立",
        )
        val root = Files.createTempDirectory("zd-t1-install").toFile()
        try {
            val archive = File(root, "pkg.tar.zst").apply { writeText("不是真归档") }
            val rootfsDir = File(root, "rootfs")
            val tmpDir = File(root, RootfsInstaller.TMP_NAME)
            val trace = InstallTrace()

            val e = assertThrows(RootfsInstaller.InstallFailed::class.java) {
                RootfsInstaller.installInto(
                    rootfsDir = rootfsDir,
                    tmpDir = tmpDir,
                    archive = archive,
                    env = null,
                    archiveSha256 = null,
                    ensureSpace = {},
                    trace = trace,
                    onEntry = { },
                )
            }
            assertTrue(e.message ?: "", (e.message ?: "").contains("本次安装未生效"))
            assertFalse("R-07：失败路径必须清干净——半截 tmp 树既白占 1–2GB，又像「有环境」", tmpDir.exists())
            assertFalse("拒绝发生在解包之前 ⇒ 现有环境一个字节都不该被动", rootfsDir.exists())
            assertEquals("计时汇总里的解包路径没被写成 Java（T1 之后没有第二条路）", "", trace.extractionPath)
            assertEquals("一条都没解出来", 0L, trace.entries)
            assertEquals("一次哈希都没发生（拒绝在读归档之前）", 0, trace.hashPasses)
        } finally {
            root.deleteRecursively()
        }
    }

    // ── 3. 源码门禁 ─────────────────────────────────────────────────────────

    private val installerSrc =
        SrcRoots.find("src/main/java/com/example/zhengdao/rootfs/RootfsInstaller.kt").readText()

    @Test
    fun `门禁：FALLBACK_REFUSED 真的写在代码里，并且落进 logcat 与 RunLog 两条通道`() {
        assertTrue(
            "证据标记要能在 logcat 里 grep 到",
            installerSrc.contains("const val FALLBACK_REFUSED = \"FALLBACK_REFUSED\""),
        )
        assertTrue(
            "release 包里 RunLog 不镜像进 logcat（N-4）⇒ 必须有一条 Log.e 才能被 adb 捞到",
            installerSrc.contains("Log.e(TAG, \"\$FALLBACK_REFUSED"),
        )
        assertTrue(
            "App 内「导出日志」那一份走 RunLog",
            installerSrc.contains("RunLog.log(\"\$FALLBACK_REFUSED"),
        )
    }

    @Test
    fun `门禁：extractArchive 里再没有 Java 慢路径的调用点，Rust 不可用即抛`() {
        val body = sliceBetween(installerSrc, "internal fun extractArchive(", "private fun refuseFallback(")
        assertFalse(
            "生产解包只许有 Rust 一条路：回退等于把校验降级成「没校验」，而且慢一个数量级",
            body.contains("extractArchiveJava("),
        )
        assertTrue(
            "Rust 不可用要当场拒（这一档以前是直接走 Java 慢路径）",
            body.contains("if (!com.example.zhengdao.rust.CoreNative.isRustAvailable())"),
        )
        assertTrue(
            "流水线失败那档也要拒，并带上原文",
            body.contains("Rust 解包流水线失败："),
        )
        assertFalse(
            "签名不再回报「走了哪条路」——没有第二条路可回报了",
            body.contains("): Boolean"),
        )
    }

    @Test
    fun `门禁：Java 解包内核留在仓里当对拍基准，但全仓再没有调用点`() {
        assertTrue(
            "对拍基准不能删：Rust 的落盘语义（类型/权限/防穿越/硬链接）要有人逐项核对",
            installerSrc.contains("fun extractArchiveJava("),
        )
        assertEquals(
            "只剩声明处这一个带括号的出现，其余都是 KDoc 链接（[extractArchiveJava]）",
            1,
            Regex("extractArchiveJava\\(").findAll(installerSrc).count(),
        )
    }

    @Test
    fun `门禁：增量补丁也走同一条 Rust 路（拒绝回退对补丁同样生效）`() {
        val delta = SrcRoots.find("src/main/java/com/example/zhengdao/rootfs/RootfsDelta.kt").readText()
        assertTrue(
            "补丁包必须继续走 RootfsInstaller.extractArchive，否则 T1 的拒绝回退只护住全量安装",
            delta.contains("RootfsInstaller.extractArchive("),
        )
        assertFalse(
            "增量路径不许自己绕一条 Java 解包出来",
            delta.contains("extractArchiveJava("),
        )
    }

    /** 取 [from] 到 [to] 之间那一段（两端都必须在，找不到就直接失败，别静默断言空气）。 */
    private fun sliceBetween(text: String, from: String, to: String): String {
        val i = text.indexOf(from)
        assertTrue("源码里找不到起点：$from（函数改名了？门禁要跟着改）", i >= 0)
        val j = text.indexOf(to, i)
        assertTrue("源码里找不到终点：$to（函数改名了？门禁要跟着改）", j > i)
        return text.substring(i, j)
    }
}
