// 独立开发声明：本文件为本项目原创，不含对第三方源码的拷贝。
// 可参考的官方资料清单见仓库根目录 PROVENANCE.md。
package com.example.zhengdao.rootfs

import com.example.zhengdao.rootfs.RootfsIndexFetcher.FetchResult

/**
 * 安装准入结论（纯数据，UI 层只负责把 [Deny] 的文案摆到用户面前）。
 *
 * 为什么要单独一个类型：267 之前"索引取不到"与"索引验不过"都折叠成 `null`，
 * 调用方于是统一退到远端明文 `$url.sha256` 兜底 —— 而那份 sha256 与被校验的包同源，
 * **能改包的人也能顺手改它**，签名等于白签。本类型存在的唯一目的：
 * 让"没通过验签"变成一条**没有任何替代路径**的拒绝。
 */
sealed interface InstallGate {

    /**
     * @param sha256 本次允许使用的校验值（小写十六进制，64 位）
     * @param index 验签通过的索引（决定能否把 env 写进环境标记；本地包路径可能为 null）
     * @param source 校验值来源的人话名（"签名索引" / "本地 .sha256" / "上次装过的包"）
     * @param note 需要额外告知用户的情况（如"索引不可信，只能用本地校验值"），无则 null
     * @param fromSignedIndex 校验值是否来自**验签通过的索引**（是才允许回写本地过期边车）
     */
    data class Allow(
        val sha256: String,
        val index: RootfsIndex?,
        val source: String,
        val note: String? = null,
        val fromSignedIndex: Boolean = false,
    ) : InstallGate

    /** @param title 一句话结论（用户第一眼看到的） @param hint 怎么办 */
    data class Deny(val title: String, val hint: String) : InstallGate
}

/**
 * 索引 → 安装准入的**纯函数**（无网络、无 Android，可在 JVM 单测里直接跑）。
 *
 * 三条不可动摇的规矩：
 * 1. 联网安装**只认验签通过**的索引；验签失败 / 没签名 / 索引损坏 / 索引取不到**一律拒绝**，
 *    不再回退到远端明文 `.sha256`（那条路等于没校验，是 267 前的真实后门）。
 * 2. 本地文件安装允许"用户自己负责"：验签索引 / 本地 `.sha256` / 本机上次装成的 sha 三者
 *    任一可用即放行 —— 但这三者都**不是远端可注入的**，所以不构成后门。
 * 3. 四种失败必须给出**不同**的文案。把"验签失败"说成"网络错误"会把用户引去换网络，
 *    而真实情况是发布端被篡改 / 密钥轮换（负例明确禁止）。
 */
object IndexTrust {

    /** SHA256 的严格形态：64 位十六进制。索引里写别的（短串、带前缀、空串）都不当校验值用。 */
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

    /**
     * 联网下载运行环境的准入。
     *
     * @param result [RootfsIndexFetcher.fetch] 的结论
     */
    fun gateForDownload(result: FetchResult): InstallGate = when (result) {
        is FetchResult.Ok -> {
            val sha = result.index.sha256.trim().lowercase()
            if (!SHA256_HEX.matches(sha)) {
                InstallGate.Deny(
                    "环境索引里没有可用的校验值",
                    "索引已经验签通过，但里面的 sha256 缺失或格式不对，无法确认下载到的安装包" +
                        "就是官方那一份。已停止安装；稍后重试，若一直如此属于发布方问题。",
                )
            } else {
                InstallGate.Allow(
                    sha256 = sha,
                    index = result.index,
                    source = "签名索引",
                    fromSignedIndex = true,
                )
            }
        }

        is FetchResult.Unsigned -> InstallGate.Deny(
            "环境索引没有签名，已拒绝使用",
            "索引下载到了，但发布源上没有配套的 .sig 签名文件（或镜像返回的不是签名）。" +
                "没签名的索引证明不了它来自发布方，不能拿它当校验依据。\n" +
                "这不是网络错误。可稍后重试；急用请用本地已有的安装包「从文件安装」。",
        )

        is FetchResult.BadSignature -> InstallGate.Deny(
            "环境索引验签失败，已拒绝使用",
            "索引内容与发布方签名不符：要么发布源被篡改，要么签名密钥已经更换。" +
                "请先更新 App 再试（新密钥随新版 App 下发）；如果刚换过密钥，旧版 App 会一直拒绝。\n" +
                "这不是网络错误，换网络不会好。",
        )

        is FetchResult.Malformed -> InstallGate.Deny(
            "环境索引内容损坏",
            "索引验签通过但读不出来（多半是半截文件或发布时写坏了）。稍后重试；" +
                "若一直如此请用本地安装包。",
        )

        FetchResult.Unreachable -> InstallGate.Deny(
            "取不到环境索引",
            "网络不通或发布源不可达，一个字节都没拿到。没有索引就无法确认安装包的校验值，" +
                "已停止联网安装（不校验就装 = 谁都能替换包）。\n" +
                "可换网络/开代理后重试，或用本地已有的安装包「从文件安装」。",
        )
    }

    /**
     * 本地文件安装的准入：三个**本地侧**来源里任一个有值就放行。
     *
     * 为什么本地路径不需要验签索引：用户手上这个包是用户自己的文件，风险由用户承担；
     * 这里只做"别在完全没有校验值的情况下解压"（P3-6 的老约定）。但两个原则不变：
     * - **远端明文 `.sha256` 不再参与**（它和包同源，等于没校验）；
     * - 索引不可信时要明说一句，别让用户以为已经核对过官方值。
     *
     * @param localName 待装包的文件名（用于判断索引里的 url 是不是同一个包）
     * @param localSize 待装包的字节数（≤0 表示未知，此时不做大小对齐判断）
     */
    fun gateForLocal(
        result: FetchResult,
        localName: String,
        localSize: Long,
        sidecar: String?,
        remembered: String?,
    ): InstallGate {
        val idx = (result as? FetchResult.Ok)?.index
        val choice = RootfsCache.pickExpectedSha(
            localName = localName,
            localSize = localSize,
            indexUrl = idx?.url,
            indexSize = idx?.size ?: 0L,
            indexSha = idx?.sha256,
            sidecar = sidecar,
            onlineSha = null,
            rememberedSha = remembered,
        )
        val sha = choice.sha?.trim()?.lowercase()
        if (sha.isNullOrEmpty() || !SHA256_HEX.matches(sha)) {
            return InstallGate.Deny(
                "拿不到这个安装包的校验值",
                "签名索引、本地同名 .sha256、本机上次装成的记录都没有可用值，" +
                    "无法确认这个包有没有被替换。为安全起见停止安装。\n" +
                    "若包是官方渠道拿到的，可以在它旁边放一份同名 .sha256 文件再装；" +
                    "或联网后改用「联网下载运行环境」。",
            )
        }
        val note = when (result) {
            is FetchResult.Ok -> null
            is FetchResult.BadSignature ->
                "环境索引验签失败，无法核对官方校验值，只能按${choice.source}校验"
            is FetchResult.Unsigned ->
                "环境索引没有签名，无法核对官方校验值，只能按${choice.source}校验"
            is FetchResult.Malformed ->
                "环境索引损坏，无法核对官方校验值，只能按${choice.source}校验"
            FetchResult.Unreachable ->
                "取不到环境索引，无法核对官方校验值，只能按${choice.source}校验"
        }
        return InstallGate.Allow(
            sha256 = sha,
            index = idx,
            source = choice.source,
            note = note,
            fromSignedIndex = result is FetchResult.Ok && choice.source == "索引",
        )
    }
}
