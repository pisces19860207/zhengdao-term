// 独立开发声明：本文件为本项目原创，不含对第三方源码的拷贝。
package com.example.zhengdao.term

import com.example.zhengdao.SrcRoots
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「回退会话不自动重装」门禁（2026-10-29 · 0.1.5）。
 *
 * 为什么是文本断言：`promptInstallOnce` 要 Activity/Context，JVM 里跑不了；但这条缺陷的形态
 * 就是"某个提前返回丢了"，直接在源码里钉住顺序即可：
 *
 * 1. 判定"环境已装"（标记文件在读）的**提前返回**必须排在 `startInstallFromFile(local)` 之前——
 *    丢了它，环境已装但 proot 起不来时每次回退都会再装一遍（真机 5 秒一轮、连装十余次）。
 * 2. 自动安装必须被 `autoInstallTried` 的一次性 CAS 挡住——它兜的是"标记不在但装也修不好"。
 *
 * 负例：删掉任何一条提前返回，本测试必红（改红记录见交付报告）。
 */
class TerminalAutoInstallGateTest {

    private fun activitySource(): String =
        SrcRoots.find("src/main/java/com/example/zhengdao/TerminalActivity.kt").readText()

    /** 截出 `private fun promptInstallOnce()` 的函数体（到下一个同缩进的成员声明为止）。 */
    private fun promptInstallOnceBody(src: String): String {
        val start = src.indexOf("private fun promptInstallOnce()")
        check(start >= 0) { "TerminalActivity 里找不到 promptInstallOnce" }
        val rest = src.substring(start)
        val end = Regex("\n {4}(private|override|internal|public) ")
            .find(rest)?.range?.first ?: rest.length
        return rest.substring(0, end)
    }

    @Test
    fun `环境已装时提前返回必须排在自动安装之前`() {
        val body = promptInstallOnceBody(activitySource())
        val markerGuard = body.indexOf("RootfsMarker.read")
        val autoInstall = body.indexOf("startInstallFromFile(local)")
        assertTrue("promptInstallOnce 里找不到环境标记判定（RootfsMarker.read）", markerGuard >= 0)
        assertTrue("promptInstallOnce 里找不到 startInstallFromFile(local)", autoInstall >= 0)
        assertTrue(
            "环境标记判定的提前返回必须排在自动安装之前——否则已装环境每次回退都会再装一遍",
            markerGuard < autoInstall,
        )
    }

    @Test
    fun `自动安装必须被一次性 CAS 挡住`() {
        val body = promptInstallOnceBody(activitySource())
        val guard = body.indexOf("autoInstallTried.compareAndSet(false, true)")
        val autoInstall = body.indexOf("startInstallFromFile(local)")
        assertTrue("promptInstallOnce 里找不到 autoInstallTried 的一次性 CAS", guard >= 0)
        assertTrue("autoInstallTried 的 CAS 必须排在自动安装之前", guard < autoInstall)
    }
}
