// 独立开发声明：本文件为本项目原创，不含对第三方源码的拷贝。
package com.example.zhengdao.term

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 设置页纯逻辑的单测（第二百六十六批 · 终端 2）。
 *
 * 这里钉的是**五条负例里能纯函数钉住的三条**：
 * - 负例②：「重装」没有二次确认 ⇒ [ReinstallGate] 永远给 `NeedConfirm`，到不了 `Proceed`；
 * - 负例③：清缓存白名单里没有 rootfs 本体，并且第二道闸 [CachePlan.isSafeTarget] 直接拒绝；
 * - 负例④的文本侧：镜像覆盖只生成 home 层的 export 行，不出现任何 rootfs 路径。
 */
class TermSettingsLogicTest {

    // ── 体积换算 ──────────────────────────────────────────────────────────

    @Test
    fun `体积只在这里换算成一位小数`() {
        assertEquals("0.0 MB", TermSettingsLogic.formatMb(0))
        assertEquals("1.0 MB", TermSettingsLogic.formatMb(1024L * 1024L))
        assertEquals("1.5 MB", TermSettingsLogic.formatMb(1024L * 1024L * 3L / 2L))
    }

    // ── 只读展示的解析 ────────────────────────────────────────────────────

    @Test
    fun `sources_list 取第一条 deb 行的 URI`() {
        val text = """
            # 注释里的 deb http://should-not-count/ debian main
            deb http://deb.debian.org/debian trixie main
            deb-src http://deb.debian.org/debian trixie main
        """.trimIndent()
        assertEquals("http://deb.debian.org/debian", TermSettingsLogic.firstDebUri(text))
    }

    @Test
    fun `sources_list 带方括号选项与行尾注释也能取到 URI`() {
        val text = "deb [arch=arm64 signed-by=/root/x.gpg] https://mirror.example/debian trixie main" +
            "  # 国内镜像\n"
        assertEquals("https://mirror.example/debian", TermSettingsLogic.firstDebUri(text))
    }

    @Test
    fun `sources_list 读不到时返回 null（界面回落到默认值）`() {
        assertNull(TermSettingsLogic.firstDebUri(null))
        assertNull(TermSettingsLogic.firstDebUri(""))
        assertNull(TermSettingsLogic.firstDebUri("# 只有注释\n"))
    }

    @Test
    fun `pip_conf 取 index-url`() {
        val text = "[global]\nindex-url = https://pypi.tuna.tsinghua.edu.cn/simple\n"
        assertEquals("https://pypi.tuna.tsinghua.edu.cn/simple", TermSettingsLogic.firstIndexUrl(text))
        assertEquals(
            "https://mirror.example/pypi",
            TermSettingsLogic.firstIndexUrl("index-url=https://mirror.example/pypi  # 行尾注释"),
        )
        assertNull(TermSettingsLogic.firstIndexUrl("[global]\ntimeout = 10\n"))
    }

    @Test
    fun `apt 覆盖只换 URI 不碰 suite 与注释`() {
        val original = """
            # Debian 13
            deb http://deb.debian.org/debian trixie main contrib
            deb http://deb.debian.org/debian-security trixie-security main
        """.trimIndent() + "\n"
        val out = TermSettingsLogic.aptSourcesWithMirror(
            original,
            "https://mirrors.tuna.tsinghua.edu.cn/debian/",
        )
        assertTrue(out.contains("# Debian 13"))
        assertTrue(out.contains("deb https://mirrors.tuna.tsinghua.edu.cn/debian trixie main contrib"))
        assertTrue(out.contains("deb https://mirrors.tuna.tsinghua.edu.cn/debian trixie-security main"))
        assertFalse("旧镜像不该还留着", out.contains("deb.debian.org"))
        assertFalse("镜像末尾的斜杠必须去掉（否则拼出 //）", out.contains("debian/ trixie"))
    }

    // ── 环境变量式覆盖 ────────────────────────────────────────────────────

    @Test
    fun `两项都空就不生成任何导出`() {
        assertEquals("", TermSettingsLogic.envBody("", ""))
        assertEquals("", TermSettingsLogic.envBody("  ", null))
    }

    @Test
    fun `只填 pip 时只导出 PIP_INDEX_URL`() {
        val body = TermSettingsLogic.envBody("", "https://mirror.example/pypi")
        assertTrue(body.contains("export PIP_INDEX_URL=\"https://mirror.example/pypi\""))
        assertFalse(body.contains("APT_CONFIG"))
    }

    @Test
    fun `只填 apt 时导出 APT_CONFIG 且指向 home 层`() {
        val body = TermSettingsLogic.envBody("https://mirror.example/debian", "")
        assertTrue(body.contains("export APT_CONFIG=\"\$HOME/.zhengdao/apt.conf\""))
        assertFalse(body.contains("PIP_INDEX_URL"))
        assertFalse("覆盖文本里不许出现 rootfs 路径", body.contains("rootfs"))
        assertFalse("也不许出现 filesDir 绝对路径", body.contains("filesDir"))
    }

    @Test
    fun `托管块重复写入是幂等的，且不动块外内容`() {
        val body = TermSettingsLogic.envBody("", "https://mirror.example/pypi")
        val once = TermSettingsLogic.upsertManagedBlock("export FOO=1\n", body)
        val twice = TermSettingsLogic.upsertManagedBlock(once, body)
        assertEquals(once, twice)
        assertTrue(once.contains("export FOO=1"))
        assertEquals(
            "export FOO=1",
            TermSettingsLogic.removeManagedBlock(once).trim().trimEnd('\n'),
        )
    }

    @Test
    fun `空 profile 写入后块外为空，移除后回到空`() {
        val body = TermSettingsLogic.envBody("https://m/debian", "https://m/pypi")
        val written = TermSettingsLogic.upsertManagedBlock("", body)
        assertTrue(written.contains("export APT_CONFIG="))
        assertEquals("", TermSettingsLogic.removeManagedBlock(written).trim())
    }

    // ── 负例②：重装必须二次确认 ──────────────────────────────────────────

    @Test
    fun `没确认时重装闸门永远给 NeedConfirm`() {
        val d = ReinstallGate.decide(installing = false, archiveBytes = 300L * 1024 * 1024, confirmed = false)
        assertEquals(ReinstallGate.Decision.NeedConfirm, d)
        assertTrue("未确认不得放行", d != ReinstallGate.Decision.Proceed)
    }

    @Test
    fun `确认后才允许动盘`() {
        assertEquals(
            ReinstallGate.Decision.Proceed,
            ReinstallGate.decide(installing = false, archiveBytes = 1L, confirmed = true),
        )
    }

    @Test
    fun `安装中或没有本地包都到不了 Proceed`() {
        assertEquals(
            ReinstallGate.Decision.Busy,
            ReinstallGate.decide(installing = true, archiveBytes = 1L, confirmed = true),
        )
        assertEquals(
            ReinstallGate.Decision.NoLocalArchive,
            ReinstallGate.decide(installing = false, archiveBytes = null, confirmed = true),
        )
        assertEquals(
            ReinstallGate.Decision.NoLocalArchive,
            ReinstallGate.decide(installing = false, archiveBytes = 0L, confirmed = true),
        )
    }

    // ── 负例③：清缓存不许碰 rootfs ───────────────────────────────────────

    private val filesDir = File("/data/user/0/com.example.zhengdao.term/files")
    private val cacheDir = File("/data/user/0/com.example.zhengdao.term/cache")
    private val publicCacheRoot = File("/storage/emulated/0/Download/证道/cache")
    private val publicRoot = File("/storage/emulated/0/Download/证道")

    private fun targets() = CachePlan.targets(filesDir, cacheDir, publicCacheRoot, publicRoot)

    @Test
    fun `白名单里没有任何 rootfs 路径`() {
        val hits = targets().filter {
            it.path.replace('\\', '/').contains("/files/rootfs") ||
                it.path.replace('\\', '/').endsWith("/files/rootfs")
        }
        assertTrue("清缓存白名单里混进了 rootfs：$hits", hits.isEmpty())
    }

    @Test
    fun `rootfs 本体与它的系统子目录都不是安全目标`() {
        assertFalse(CachePlan.isSafeTarget(filesDir, filesDir, cacheDir, publicCacheRoot, publicRoot))
        assertFalse(
            CachePlan.isSafeTarget(File(filesDir, "rootfs"), filesDir, cacheDir, publicCacheRoot, publicRoot)
        )
        assertFalse(
            "apt 索引看起来像缓存，但属于环境本体",
            CachePlan.isSafeTarget(
                File(filesDir, "rootfs/var/lib/apt/lists"),
                filesDir, cacheDir, publicCacheRoot, publicRoot,
            ),
        )
        assertFalse(
            "home 里的用户配置不在白名单（缓存项有明确目录名）",
            CachePlan.isSafeTarget(File(filesDir, "home/.zhengdao"), filesDir, cacheDir, publicCacheRoot, publicRoot),
        )
    }

    @Test
    fun `cache 目录与 Agent 包缓存是安全目标`() {
        assertTrue(CachePlan.isSafeTarget(cacheDir, filesDir, cacheDir, publicCacheRoot, publicRoot))
        assertTrue(
            CachePlan.isSafeTarget(
                File(cacheDir, "rootfs-cache"),
                filesDir, cacheDir, publicCacheRoot, publicRoot,
            ),
        )
        assertTrue(
            CachePlan.isSafeTarget(
                File(filesDir, "home/.npm/_cacache"),
                filesDir, cacheDir, publicCacheRoot, publicRoot,
            ),
        )
        assertTrue(
            CachePlan.isSafeTarget(
                File(filesDir, "home/.hermes/cache"),
                filesDir, cacheDir, publicCacheRoot, publicRoot,
            ),
        )
    }

    @Test
    fun `目录体积按文件真实字节累加`() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "zd-term-settings-${System.nanoTime()}")
        try {
            File(tmp, "a/b").mkdirs()
            File(tmp, "a/b/x.bin").writeBytes(ByteArray(100))
            File(tmp, "y.bin").writeBytes(ByteArray(23))
            assertEquals(123L, CachePlan.dirBytes(tmp))
            assertEquals(0L, CachePlan.dirBytes(File(tmp, "nope")))
        } finally {
            tmp.deleteRecursively()
        }
    }
}
