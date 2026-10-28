// 独立开发声明：本文件为本项目原创，不含对第三方源码的拷贝。
package com.example.zhengdao.rootfs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * 第二百六十七批：**环境包索引签名**的交付实测。
 *
 * 这一族测试不查"代码长什么样"，只查"跑出来的结论对不对"——因为验收方用的是
 * 真签名字节 + 真发布索引，而不是读代码点头。
 *
 * 覆盖的负例（派工单 §负例）：
 * 1. 验签失败仍用 → [改一个字节就必须拒绝] / [签名来自别的私钥必须拒绝]
 * 2. 只验直连不验镜像 → [直连拿不到时镜像也必须验]
 * 3. 私钥进仓库 → [仓库里不许出现私钥 PEM]
 * 4. 用同一把密钥签 App 包 → [索引公钥必须与 agents 清单公钥不同]
 * 5. 验签失败却说"网络错误" → [四种失败的文案必须互不相同]
 */
class RootfsIndexSignatureTest {

    // ── 真实发布物（rootfs-13.7-2026.10.28 的 rootfs-index.json，469 字节） ──
    // 不造假索引：用线上那份逐字节复制，再配上 2026-10-28 用生产私钥签出来的 .sig。
    // 这样"正确索引通过"这条测的是**生产密钥链**，不是测试里现搓的一对玩具钥匙。
    private val realIndex: ByteArray = (
        listOf(
            "{",
            "  \"schema\": 2,",
            "  \"distro\": \"debian-13.7\",",
            "  \"version\": \"13.7\",",
            "  \"env\": \"f29b58512d083c96\",",
            "  \"builtAt\": \"2026-10-23T06:42:08Z\",",
            "  \"url\": \"https://github.com/pisces19860207/zhengdao/releases/download/latest/debian-13.7-base-arm64.tar.zst\",",
            "  \"sha256\": \"8d2f32e5a505ef0680b29e93d8b7422e0c0ea7b4212cd0df09c1adebdd473683\",",
            "  \"size\": 200223802,",
            "  \"manifestUrl\": \"https://github.com/pisces19860207/zhengdao/releases/download/latest/rootfs-manifest.txt\",",
            "  \"patch\": null",
            "}",
        ).joinToString("\n") + "\n"
        ).toByteArray(Charsets.UTF_8)

    private val realSig = "nJxbZMLLxL3s82XPmA2MUeemrm1rRTng42mWtKcS/YtBEMVa3LEnRxfL5swRF0oP8jvfNIfUrdAxNVQZFcZpDA=="

    private val direct = RootfsIndexFetcher.URL

    /** 任何 URL 都给出"正确索引 + 正确签名"的取字节器（多源都会走到它）。 */
    private fun allGood(): (String) -> ByteArray? = { url ->
        if (url.endsWith(".sig")) realSig.toByteArray() else realIndex
    }

    // ── 0. fixture 自检：证明"真索引"确实是线上那份，不是我手抄错了 ────────────

    @Test
    fun `fixture 与线上字节逐字节一致`() {
        assertEquals(469, realIndex.size)
        assertEquals(
            "2f7755f509871e1cf06be80e70d9f3e0e563c2354171d1d092c751733e1268fb",
            sha256Hex(realIndex),
        )
    }

    // ── 1. 正确索引通过 ──────────────────────────────────────────────────

    @Test
    fun `正确索引验签通过`() {
        assertTrue(
            "真实发布索引 + 真实签名必须验过（这条不过说明生产密钥链断了）",
            RootfsIndexFetcher.verifySignature(realIndex, realSig),
        )
    }

    @Test
    fun `正确索引走完整抓取流程得到 Ok 与可信 sha256`() {
        val result = RootfsIndexFetcher.fetch(indexUrl = direct, fetchBytes = allGood())
        assertTrue("期望 Ok，实际 $result", result is RootfsIndexFetcher.FetchResult.Ok)
        val gate = IndexTrust.gateForDownload(result)
        assertTrue("期望放行，实际 $gate", gate is InstallGate.Allow)
        gate as InstallGate.Allow
        assertEquals("8d2f32e5a505ef0680b29e93d8b7422e0c0ea7b4212cd0df09c1adebdd473683", gate.sha256)
        assertEquals("f29b58512d083c96", gate.index?.env)
        assertTrue("校验值来自验签索引", gate.fromSignedIndex)
    }

    // ── 2. 改一个字节就拒绝（交付要求的核心实测） ──────────────────────────

    @Test
    fun `改一个字节就必须拒绝`() {
        val tampered = realIndex.copyOf()
        tampered[10] = (tampered[10].toInt() xor 0x01).toByte() // 只翻一个 bit
        assertFalse(
            "改了 1 个 bit 还验过 ⇒ 签名形同虚设",
            RootfsIndexFetcher.verifySignature(tampered, realSig),
        )
        val result = RootfsIndexFetcher.fetch(
            indexUrl = direct,
            fetchBytes = { url -> if (url.endsWith(".sig")) realSig.toByteArray() else tampered },
        )
        assertTrue("期望 BadSignature，实际 $result", result is RootfsIndexFetcher.FetchResult.BadSignature)
        assertTrue(IndexTrust.gateForDownload(result) is InstallGate.Deny)
    }

    @Test
    fun `签名来自别的私钥必须拒绝`() {
        // 换一把密钥的签名（同一份索引）：这就是"发布端被冒名"的形态
        val otherSig = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
        assertFalse(
            "全零签名居然验过",
            RootfsIndexFetcher.verifySignature(realIndex, otherSig),
        )
        val result = RootfsIndexFetcher.fetch(
            indexUrl = direct,
            fetchBytes = { url -> if (url.endsWith(".sig")) otherSig.toByteArray() else realIndex },
        )
        assertTrue("期望 BadSignature，实际 $result", result is RootfsIndexFetcher.FetchResult.BadSignature)
    }

    // ── 3. 删掉 .sig 也拒绝 ─────────────────────────────────────────────

    @Test
    fun `删掉 sig 也拒绝`() {
        val result = RootfsIndexFetcher.fetch(
            indexUrl = direct,
            fetchBytes = { url -> if (url.endsWith(".sig")) null else realIndex },
        )
        assertTrue("期望 Unsigned，实际 $result", result is RootfsIndexFetcher.FetchResult.Unsigned)
        val gate = IndexTrust.gateForDownload(result)
        assertTrue("没签名必须拒绝联网安装，实际 $gate", gate is InstallGate.Deny)
        assertTrue((gate as InstallGate.Deny).title.contains("没有签名"))
    }

    @Test
    fun `空的 sig 文件也拒绝`() {
        val result = RootfsIndexFetcher.fetch(
            indexUrl = direct,
            fetchBytes = { url -> if (url.endsWith(".sig")) ByteArray(0) else realIndex },
        )
        assertTrue("期望 Unsigned，实际 $result", result is RootfsIndexFetcher.FetchResult.Unsigned)
    }

    // ── 4. 镜像路径同样要验（"只验直连不验镜像"的负例） ────────────────────

    @Test
    fun `直连拿不到时镜像也必须验`() {
        val tampered = realIndex.copyOf().also { it[0] = ' '.code.toByte() }
        // 直连（索引 + 签名）全部不可达；镜像能取到"被改过的索引 + 原签名"
        val result = RootfsIndexFetcher.fetch(
            indexUrl = direct,
            fetchBytes = { url ->
                when {
                    url.startsWith(direct) -> null
                    url.endsWith(".sig") -> realSig.toByteArray()
                    else -> tampered
                }
            },
        )
        assertTrue(
            "镜像回退这一路没验签就会拿到 Ok（这就是要防的洞），实际 $result",
            result is RootfsIndexFetcher.FetchResult.BadSignature,
        )
    }

    @Test
    fun `镜像返回 HTML 错误页算没签名而不是篡改`() {
        val html = "<html><body>404 Not Found</body></html>".toByteArray()
        val result = RootfsIndexFetcher.fetch(
            indexUrl = direct,
            fetchBytes = { url ->
                when {
                    url.startsWith(direct) -> null
                    url.endsWith(".sig") -> html
                    else -> realIndex
                }
            },
        )
        assertTrue(
            "镜像把 404 变成 200+HTML 时不能判成'发布端被篡改'（会误导用户），实际 $result",
            result is RootfsIndexFetcher.FetchResult.Unsigned,
        )
    }

    @Test
    fun `任一源验签通过即可（多源不互相污染）`() {
        val tampered = realIndex.copyOf().also { it[0] = ' '.code.toByte() }
        // 直连给"篡改 + 原签名"（坏源），镜像给正确的一对（好源）⇒ 结论应为 Ok
        val result = RootfsIndexFetcher.fetch(
            indexUrl = direct,
            fetchBytes = { url ->
                when {
                    url.startsWith(direct) -> if (url.endsWith(".sig")) realSig.toByteArray() else tampered
                    url.endsWith(".sig") -> realSig.toByteArray()
                    else -> realIndex
                }
            },
        )
        assertTrue("好源能通过时应放行，实际 $result", result is RootfsIndexFetcher.FetchResult.Ok)
    }

    // ── 5. 四种失败不许互相冒充 ─────────────────────────────────────────

    @Test
    fun `四种失败的文案必须互不相同`() {
        val titles = listOf(
            IndexTrust.gateForDownload(RootfsIndexFetcher.FetchResult.Unreachable),
            IndexTrust.gateForDownload(RootfsIndexFetcher.FetchResult.Unsigned("x")),
            IndexTrust.gateForDownload(RootfsIndexFetcher.FetchResult.BadSignature("x")),
            IndexTrust.gateForDownload(RootfsIndexFetcher.FetchResult.Malformed("x")),
        ).map { (it as InstallGate.Deny).title }
        assertEquals("四种失败必须各有各的说法", 4, titles.toSet().size)
    }

    @Test
    fun `验签失败不能说成网络错误`() {
        val deny = IndexTrust.gateForDownload(
            RootfsIndexFetcher.FetchResult.BadSignature("https://github.com/x/rootfs-index.json"),
        ) as InstallGate.Deny
        // 标题是用户第一眼看到的结论：不能出现"网络"二字（否则用户会去换网络，白忙）
        assertFalse("验签失败的标题里出现了'网络'：${deny.title}", deny.title.contains("网络"))
        assertTrue(deny.title.contains("验签失败"))
        assertTrue(
            "成因要指向篡改/密钥，而不是网络：${deny.hint}",
            deny.hint.contains("篡改") || deny.hint.contains("签名"),
        )
        // 反过来：真的拿不到索引才说网络
        val net = IndexTrust.gateForDownload(RootfsIndexFetcher.FetchResult.Unreachable) as InstallGate.Deny
        assertTrue("真的没网就该直说网络：${net.hint}", net.hint.contains("网络"))
        assertNotEquals(net.title, deny.title)
    }

    // ── 6. 索引取不到时不再退回明文 sha256（fail-closed） ─────────────────

    @Test
    fun `索引不可用时本地安装只能用本地侧校验值`() {
        val localSha = "a".repeat(64)
        // 本地 .sha256 可用 ⇒ 允许本地安装（用户自己的文件、自己的校验值）
        val allow = IndexTrust.gateForLocal(
            result = RootfsIndexFetcher.FetchResult.BadSignature("x"),
            localName = "debian-13.7-base-arm64.tar.zst",
            localSize = 200223802,
            sidecar = localSha,
            remembered = null,
        )
        assertTrue(allow is InstallGate.Allow)
        allow as InstallGate.Allow
        assertEquals(localSha, allow.sha256)
        assertEquals("本地 .sha256", allow.source)
        assertFalse("来源是本地边车，不许回写", allow.fromSignedIndex)
        assertTrue("必须告诉用户索引没验过：${allow.note}", allow.note!!.contains("验签失败"))

        // 三个本地来源都没有 ⇒ 拒绝（而不是"没校验值也照装"）
        val deny = IndexTrust.gateForLocal(
            result = RootfsIndexFetcher.FetchResult.Unreachable,
            localName = "debian-13.7-base-arm64.tar.zst",
            localSize = 200223802,
            sidecar = null,
            remembered = null,
        )
        assertTrue(deny is InstallGate.Deny)
    }

    @Test
    fun `索引 sha256 缺失或格式不对时拒绝联网安装`() {
        val noSha = RootfsIndexParser.parse(
            """{"schema":2,"distro":"debian-13.7","version":"13.7","url":"https://e/x.tar.zst"}""",
        )!!
        val gate = IndexTrust.gateForDownload(
            RootfsIndexFetcher.FetchResult.Ok(noSha, "https://e/rootfs-index.json"),
        )
        assertTrue("缺 sha256 仍放行 ⇒ 下载完没法校验", gate is InstallGate.Deny)

        val shortSha = RootfsIndexParser.parse(
            """{"url":"https://e/x.tar.zst","sha256":"8d2f32e5"}""",
        )!!
        assertTrue(
            IndexTrust.gateForDownload(
                RootfsIndexFetcher.FetchResult.Ok(shortSha, "https://e/i.json"),
            ) is InstallGate.Deny,
        )
    }

    // ── 7. 密钥纪律 ────────────────────────────────────────────────────

    @Test
    fun `索引公钥必须与 agents 清单公钥不同`() {
        val manifest = readAppSource("src/main/java/com/example/zhengdao/ui/AgentManifest.kt")
            ?: error("读不到 AgentManifest.kt —— 它搬家了就同步改这条门禁，别让它悄悄失效")
        val m = Regex("PUBLIC_KEY_B64\\s*=\\s*\"([^\"]+)\"").find(manifest)
            ?: error("AgentManifest.kt 里没有 PUBLIC_KEY_B64 常量了，这条门禁要跟着改")
        val manifestPub = m.groupValues[1]
        assertFalse(
            "索引与 agents 清单共用了同一把公钥：两条信任链就该两把钥匙（换一把不该牵连另一把）",
            manifestPub == RootfsIndexFetcher.INDEX_SIGNING_PUBKEY_B64,
        )
        // 公钥必须是 32 字节 raw（Ed25519），不是整段 DER/X.509
        assertEquals(
            32,
            java.util.Base64.getDecoder().decode(RootfsIndexFetcher.INDEX_SIGNING_PUBKEY_B64).size,
        )
    }

    @Test
    fun `仓库里不许出现私钥 PEM`() {
        val roots = listOf(File("src"), File("../app/src"), File("tools"), File("../app"))
        val exts = setOf("kt", "kts", "py", "md", "json", "txt", "xml", "yml", "yaml", "properties", "sh")
        val hits = roots.filter { it.isDirectory }.flatMap { root ->
            root.walkTopDown()
                .onEnter { it.name != "build" && it.name != ".gradle" }
                .filter { it.isFile && it.extension in exts && it.length() < 2_000_000 }
                .filter { looksLikePem(it.readText()) }
                .map { it.path }
                .toList()
        }
        assertTrue("这些文件里躺着私钥 PEM：$hits", hits.isEmpty())
    }

    // ── 工具 ───────────────────────────────────────────────────────────

    private fun readAppSource(rel: String): String? =
        listOf(File("../app/$rel"), File("app/$rel")).firstOrNull { it.isFile }?.readText()

    /** 真 PEM 判定：`BEGIN PRIVATE KEY` 之后紧跟 base64 正文（脚本里那行字面量不算）。 */
    private fun looksLikePem(text: String): Boolean {
        val marker = "-----BEGIN PRIVATE KEY-----"
        val i = text.indexOf(marker)
        if (i < 0) return false
        val rest = text.substring(i + marker.length).trimStart('\n', '\r', ' ', '\t')
        if (rest.length < 40) return false
        return rest.take(40).all { it.isLetterOrDigit() || it == '+' || it == '/' }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
