// 独立开发声明：本文件为本项目原创。
//
// 2026-10-27：从主仓 `NetworkSecurityConfigTest.guest 的明文 apt 源不进白名单` **搬过来**。
// 为什么搬：那条用例同时断言「主仓 network_security_config 的白名单只有环回」和
// 「rootfs/build-rootfs.sh 的 MIRROR 是 http」；终端整仓搬出主仓后，前半段留在主仓，
// 后半段的真相（build-rootfs.sh）只在本仓 ⇒ 拆成两半，各自守自己的真源。
// **不用 @Ignore、不用 Assume**：文件不在就是真错，必须红。
package com.example.zhengdao.term

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RootfsMirrorTest {

    /** rootfs 构建脚本（本仓根目录下）。 */
    private fun buildScript(): File {
        val candidates = listOf(File("../rootfs/build-rootfs.sh"), File("rootfs/build-rootfs.sh"))
        return candidates.firstOrNull { it.exists() }
            ?: error("找不到 rootfs/build-rootfs.sh（镜像源与明文口径的真相源）")
    }

    /**
     * 记录口径：**guest 里 apt 的 http 源与 Android 的明文策略无关。**
     *
     * 明文策略只在平台网络栈生效（HttpURLConnection / WebView / OkHttp 的 AndroidPlatform），
     * 不拦 bionic 的 connect()；proot 的 guest 是 glibc + GnuTLS 原生程序，走自己的 socket。
     *
     * 这条同时是**扳机**：哪天镜像源换成 https（或换了主机），它会红，逼着人回来把
     * 主仓 `network_security_config.xml` 顶部那段记录性注释一起改对。
     */
    @Test
    fun `apt 镜像源仍是明文 http（扳机：换源必须回来改口径）`() {
        val script = buildScript()
        assertTrue("找不到 rootfs/build-rootfs.sh", script.exists())
        val m = Regex("MIRROR=\"(https?)://([^/\"]+)").find(script.readText())
        assertNotNull("rootfs/build-rootfs.sh 里找不到 MIRROR=\"…\"", m)
        val scheme = m!!.groupValues[1]
        val host = m.groupValues[2]
        assertEquals(
            "镜像的 apt 源不再是明文 http 了（现在是 $scheme://$host）——请顺手更新本用例与" +
                "主仓 network_security_config.xml 顶部那段配套注释",
            "http",
            scheme,
        )
        assertTrue("镜像主机为空", host.isNotBlank())
    }
}
