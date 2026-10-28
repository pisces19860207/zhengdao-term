// 独立开发声明：本文件为本项目原创，不含对第三方源码的拷贝。
package com.example.zhengdao.term

import com.example.zhengdao.SrcRoots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 设置页的**源码/资源门禁**（第二百六十六批 · 终端 2）。
 *
 * 为什么是文本断言：设置页要 Context/Compose，JVM 里跑不了真机交互；但派工单 §3 的五条负例
 * 里有四条是"源码形态"问题，可以直接在单测里钉死：
 * 1. 桌面图标只许 1 个（清单里 `LAUNCHER` 只出现一次、`exported="true"` 只有一个）；
 * 4. 文案必须走资源（扫描设置页源码的字面量里不许有中文，且每条 `term_settings_*` 都被引用）；
 * 5. 不许顺手改安装/解压（设置页只出现公开入口，不许出现解压内核的方法名）——
 *    安装/进度那两个文件本身由报告里的 `git diff --name-only` 逐条对账，这里管的是"别另写一套"。
 *
 * 另外钉两条交付硬指标：版本号与 `build.gradle.kts` 一致；随包许可证副本与仓根同名文件一致。
 */
class TermSettingsGateTest {

    private fun read(rel: String): String = SrcRoots.find(rel).readText()

    private fun readRepoFile(relFromRepoRoot: String): File {
        val candidates = listOf(File(relFromRepoRoot), File("..", relFromRepoRoot))
        return candidates.firstOrNull { it.isFile }
            ?: error("找不到仓根文件 $relFromRepoRoot（试过：${candidates.joinToString { it.path }}）")
    }

    private val settingsSources = listOf(
        "src/main/java/com/example/zhengdao/term/TermSettingsScreen.kt",
        "src/main/java/com/example/zhengdao/term/TermSettingsActions.kt",
        "src/main/java/com/example/zhengdao/term/MainActivity.kt",
    )

    // ── S5：版本号 / 应用 ID / 许可证 ─────────────────────────────────────

    @Test
    fun `版本号与应用 ID 跟 build_gradle_kts 一致`() {
        val gradle = read("build.gradle.kts")
        val versionName = Regex("versionName\\s*=\\s*\"([^\"]+)\"")
            .find(gradle)?.groupValues?.get(1)
            ?: error("app/build.gradle.kts 里找不到 versionName")
        val applicationId = Regex("applicationId\\s*=\\s*\"([^\"]+)\"")
            .find(gradle)?.groupValues?.get(1)
            ?: error("app/build.gradle.kts 里找不到 applicationId")

        assertEquals(
            "关于页必须显示 build.gradle.kts 的 versionName（唯一真源）",
            versionName,
            BuildConfig.VERSION_NAME,
        )
        assertEquals(
            "关于页必须显示 build.gradle.kts 的 applicationId",
            applicationId,
            BuildConfig.APPLICATION_ID,
        )
    }

    @Test
    fun `随包许可证副本必须与仓根同名文件一致`() {
        val bundled = read("src/main/assets/licenses/THIRD-PARTY-LICENSES.md")
        val root = readRepoFile("THIRD-PARTY-LICENSES.md").readText()
        fun norm(s: String) = s.replace("\r\n", "\n").trimEnd()
        assertTrue("随包许可证副本是空的", norm(bundled).isNotEmpty())
        assertEquals(
            "assets 里的许可证副本已经与仓根 THIRD-PARTY-LICENSES.md 不一致——" +
                "改许可证时要把副本一起更新（设置页「查看随包许可证」读的是副本）",
            norm(root),
            norm(bundled),
        )
    }

    // ── 负例①：桌面图标必须仍然只有一个 ──────────────────────────────────

    @Test
    fun `清单里 LAUNCHER 只出现一次且只有一个 exported 为 true 的组件`() {
        val manifest = read("src/main/AndroidManifest.xml")
        assertEquals(
            "桌面图标必须只有一个：清单里 android.intent.category.LAUNCHER 只能出现一次",
            1,
            Regex("android\\.intent\\.category\\.LAUNCHER").findAll(manifest).count(),
        )
        assertEquals(
            "只允许 MainActivity 对外暴露（exported=\"true\"）",
            1,
            Regex("android:exported=\"true\"").findAll(manifest).count(),
        )
        assertFalse(
            "设置页不许做成 Activity（哪怕不是 LAUNCHER）：它应当是 MainActivity 里的一屏",
            manifest.contains("TermSettings"),
        )
    }

    // ── 负例④：文案必须走 strings ────────────────────────────────────────

    @Test
    fun `设置页源码的字面量里没有中文`() {
        val cjk = Regex("[\\u4e00-\\u9fff\\u3000-\\u303f\\uff00-\\uffef]")
        settingsSources.forEach { rel ->
            val literals = stringLiterals(stripComments(read(rel)))
            val hits = literals.filter { cjk.containsMatchIn(it) }
            assertTrue(
                "$rel 把文案写死在代码里了（应进 strings_term.xml）：$hits",
                hits.isEmpty(),
            )
        }
    }

    @Test
    fun `每条设置的文案资源都被界面引用`() {
        val zh = read("src/main/res/values/strings_term.xml")
        val keys = Regex("<string name=\"(term_settings_[a-z0-9_]+)\"")
            .findAll(zh).map { it.groupValues[1] }.toList()
        assertTrue("设置页一条 term_settings_* 文案都没有", keys.size >= 20)
        val code = settingsSources.joinToString("\n") { read(it) }
        val orphans = keys.filter { !code.contains("R.string.$it") }
        assertTrue("这些文案定义了但界面没引用（会变成死资源）：$orphans", orphans.isEmpty())
    }

    @Test
    fun `设置页中英文案键一致`() {
        fun keys(rel: String) = Regex("<string name=\"([a-z0-9_]+)\"")
            .findAll(read(rel)).map { it.groupValues[1] }.toSet()
        val zh = keys("src/main/res/values/strings_term.xml")
        val en = keys("src/main/res/values-en/strings_term.xml")
        assertEquals("values 与 values-en 的 strings_term 键必须一致（缺一条就会中文回退）", zh, en)
    }

    @Test
    fun `设置页不在代码里取资源值`() {
        // 只钉「取 Android 资源」这一种（`getString(R.string…)`）；SharedPreferences.getString(键) 不算。
        val resourceGet = Regex("getString\\(\\s*(android\\.content\\.Context\\.)?R\\.string")
        settingsSources.forEach { rel ->
            val hits = resourceGet.findAll(stripComments(read(rel))).count()
            assertEquals(
                "$rel 在代码里 getString(R.string…) 会踩 lint（LocalContextGetResourceValueCall）：" +
                    "文案应在组合期 stringResource 解析",
                0,
                hits,
            )
        }
    }

    // ── 负例⑤：只调公开入口，不另写一套安装/解压 ────────────────────────

    @Test
    fun `重装只走 InstallFlow 与 RootfsInstaller 的公开入口`() {
        val actions = read("src/main/java/com/example/zhengdao/term/TermSettingsActions.kt")
        assertTrue(
            "重装必须复用安装流程的公开入口 InstallFlow.start（否则进度/通知/日志三条线索会断）",
            actions.contains("InstallFlow.start("),
        )
        assertTrue(
            "重装必须走 InstallFlow.finish / InstallFlow.fail 收尾",
            actions.contains("InstallFlow.finish(") && actions.contains("InstallFlow.fail("),
        )
        assertTrue(
            "解压必须复用 RootfsInstaller.install（与终端页首次安装同一台内核）",
            actions.contains("RootfsInstaller.install("),
        )
        val forbidden = listOf(
            "installInto(",
            "extractArchive(",
            "swapIntoPlace(",
            "withInstallLock(",
            "InstallProgress.",
        )
        val hits = forbidden.filter { actions.contains(it) }
        assertTrue(
            "设置页不许碰安装/解压内核的内部方法或 InstallProgress 本体（那是终端 1 的地盘）：$hits",
            hits.isEmpty(),
        )
    }

    // ── 小工具 ───────────────────────────────────────────────────────────

    /** 去掉 `//` 与块注释（字符串里的 `//` 不算注释），只留代码与字面量本身。 */
    private fun stripComments(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        var inLine = false
        var inBlock = false
        var inString = false
        while (i < src.length) {
            val c = src[i]
            val n = if (i + 1 < src.length) src[i + 1] else '\u0000'
            when {
                inLine -> if (c == '\n') {
                    inLine = false
                    out.append(c)
                }
                inBlock -> {
                    if (c == '*' && n == '/') {
                        inBlock = false
                        i++
                    } else if (c == '\n') {
                        out.append(c)
                    }
                }
                inString -> {
                    out.append(c)
                    if (c == '\\' && n != '\u0000') {
                        out.append(n)
                        i++
                    } else if (c == '"') {
                        inString = false
                    }
                }
                c == '/' && n == '/' -> inLine = true
                c == '/' && n == '*' -> inBlock = true
                c == '"' -> {
                    inString = true
                    out.append(c)
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /** 抽普通字符串字面量（设置页源码里没有 raw string，够用）。 */
    private fun stringLiterals(src: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < src.length) {
            if (src[i] != '"') {
                i++
                continue
            }
            i++
            val sb = StringBuilder()
            while (i < src.length) {
                val c = src[i]
                if (c == '\\' && i + 1 < src.length) {
                    sb.append(c).append(src[i + 1])
                    i += 2
                    continue
                }
                if (c == '"') {
                    i++
                    break
                }
                sb.append(c)
                i++
            }
            out.add(sb.toString())
        }
        return out
    }
}
