// 独立开发声明：本文件为本项目原创，不含对第三方源码的拷贝。
package com.example.zhengdao.term

import java.io.File
import java.util.Locale

/**
 * 设置页里**不碰 Android 框架**的那部分：重装闸门、清缓存白名单、镜像覆盖的文本生成。
 *
 * 全部是纯函数（最多只吃 `File`），所以 JVM 单测能直接钉住五条负例里的三条：
 * - 「重装」没有二次确认就不许 proceed（[ReinstallGate]）；
 * - 清缓存的目标永远不许是 rootfs 本体或它的系统子目录（[CachePlan]）；
 * - 镜像覆盖只生成 home 层的环境变量文本，绝不写 rootfs（[MirrorOverride]）。
 *
 * 界面与真正的删盘/解压动作在 `TermSettingsScreen` / `TermSettingsActions` 里；这里只做判定，
 * 判定错了会有单测先红，不会等到真机上把用户环境删掉才发现。
 */
object TermSettingsLogic {

    /** apt 默认镜像（派工单 §1 S4 写死的默认值）。 */
    const val DEFAULT_APT_MIRROR = "http://deb.debian.org/debian"

    /** pip 默认源（PyPI 官方）。 */
    const val DEFAULT_PIP_INDEX = "https://pypi.org/simple"

    /** 环境里 apt 源的相对路径（相对 `<filesDir>/rootfs`）。只读展示用。 */
    const val APT_SOURCES_REL = "etc/apt/sources.list"

    /** 环境里 pip 源的相对路径（相对 `<filesDir>/rootfs`）。只读展示用。 */
    const val PIP_CONF_REL = "etc/pip.conf"

    private const val MB = 1024.0 * 1024.0

    /** 人类可读体积：`12.3 MB`（一位小数）。体积只在这里换算一次，界面不各自拼字符串。 */
    fun formatMb(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / MB)

    /**
     * 从 `sources.list` 正文里取第一条 `deb` 行的 URI（只读展示用）。
     *
     * 形态：`deb [arch=arm64 signed-by=/x.gpg] http://host/path suite main` —— 方括号那段的
     * 位置不固定、可能没有；所以按"跳过注释/空行，去掉方括号段，取第三个 token"读，
     * 不用正则硬套整行（行尾可能还有 `# 注释`）。
     */
    fun firstDebUri(sourcesList: String?): String? {
        if (sourcesList.isNullOrBlank()) return null
        for (raw in sourcesList.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            if (!line.startsWith("deb ")) continue
            val tokens = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            // deb [opts] URI suite [components...]；没有 options 时 URI 是第 2 个 token。
            val uri = tokens.drop(1).firstOrNull { !it.startsWith("[") && !it.endsWith("]") }
            if (!uri.isNullOrBlank() && (uri.startsWith("http://") || uri.startsWith("https://") ||
                    uri.startsWith("file://"))
            ) {
                return uri
            }
        }
        return null
    }

    /** 从 `pip.conf` 正文里取 `index-url`（只读展示用）。 */
    fun firstIndexUrl(pipConf: String?): String? {
        if (pipConf.isNullOrBlank()) return null
        for (raw in pipConf.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            val i = line.indexOf('=')
            if (i <= 0) continue
            if (!line.substring(0, i).trim().equals("index-url", ignoreCase = true)) continue
            val value = line.substring(i + 1).trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    /**
     * 把已有的 `sources.list` 正文里每条 `deb` 行的 URI 换成 [mirror]，**其余字节一不动**
     * （suite / components / 注释 / 空行都保留）。
     *
     * 为什么不自己编一份 sources.list：发行版的 suite 名（`trixie`）、`-security`、`non-free-firmware`
     * 这类细节写错一条，apt 就整段失效；照抄环境里那份、只换主机最安全。
     */
    fun aptSourcesWithMirror(original: String, mirror: String): String {
        val m = mirror.trim().trimEnd('/')
        if (m.isEmpty()) return original
        return original.lineSequence().joinToString("\n") { raw ->
            val commentAt = raw.indexOf('#')
            val code = if (commentAt >= 0) raw.substring(0, commentAt) else raw
            val comment = if (commentAt >= 0) raw.substring(commentAt) else ""
            if (!code.trim().startsWith("deb ")) {
                raw
            } else {
                val tokens = code.trim().split(Regex("\\s+")).toMutableList()
                val uriAt = (1 until tokens.size).firstOrNull { i ->
                    val t = tokens[i]
                    !t.startsWith("[") && !t.endsWith("]") &&
                        (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://"))
                }
                if (uriAt == null) {
                    raw
                } else {
                    tokens[uriAt] = m
                    // 保留行首缩进与行尾注释（token 之间的空白归一成单个空格，与 deb 行惯例一致）
                    val indent = code.takeWhile { it == ' ' || it == '\t' }
                    indent + tokens.joinToString(" ") + comment
                }
            }
        }
    }

    /** 托管块的首尾标记（幂等替换用；用户自己写的同名字符串不认，只认整块）。 */
    private const val BLOCK_BEGIN = "# >>> zhengdao term settings (managed) >>>"
    private const val BLOCK_END = "# <<< zhengdao term settings (managed) <<<"

    /**
     * 在 `~/.profile` 里维护一个**只属于本 App** 的块：块外一个字节不动，重复写入幂等。
     *
     * 为什么是 `~/.profile`：home 层 bind 到 guest 的 `/root`，tmux 的每个 pane 都是 login shell、
     * 会读它（ProotLauncher 自己也往这里落 skel 骨架）；而 rootfs 里的 `/etc/profile.d/` 属于
     * 环境本体——派工单 §1 S4 明确「不写进 rootfs 里改文件」。
     */
    fun upsertManagedBlock(profile: String, body: String): String {
        val block = "$BLOCK_BEGIN\n$body\n$BLOCK_END"
        val without = removeManagedBlock(profile).trimEnd('\n')
        return if (without.isEmpty()) "$block\n" else "$without\n$block\n"
    }

    /** 去掉托管块（保留块外全部内容）。 */
    fun removeManagedBlock(profile: String): String {
        val lines = profile.split("\n")
        val out = ArrayList<String>(lines.size)
        var inside = false
        for (line in lines) {
            when (line.trim()) {
                BLOCK_BEGIN -> inside = true
                BLOCK_END -> inside = false
                else -> if (!inside) out.add(line)
            }
        }
        return out.joinToString("\n")
    }

    /** 托管块正文：只导出用户真的填了的项；两项都空 ⇒ 空串（= 不写块）。 */
    fun envBody(aptMirror: String?, pipIndex: String?): String {
        val lines = ArrayList<String>(4)
        val apt = aptMirror?.trim().orEmpty()
        val pip = pipIndex?.trim().orEmpty()
        if (apt.isNotEmpty()) {
            // APT_CONFIG 是 apt 官方认的环境变量：指到 home 层的配置，不动 rootfs 里任何文件。
            lines += "export APT_CONFIG=\"\$HOME/.zhengdao/apt.conf\""
        }
        if (pip.isNotEmpty()) {
            lines += "export PIP_INDEX_URL=\"$pip\""
        }
        return lines.joinToString("\n")
    }
}

/** 重装/修复环境的判定结果（负例②：没有二次确认就不能进 [Proceed]）。 */
object ReinstallGate {

    enum class Decision {
        /** 已有安装/更新任务在跑。 */
        Busy,

        /** 没有本地安装包（重装要离线解压，没包就不能走）。 */
        NoLocalArchive,

        /** 有包、没确认：必须弹二次确认。 */
        NeedConfirm,

        /** 有包 + 用户已点确认：可以动盘。 */
        Proceed,
    }

    /**
     * @param installing 进程级安装锁（[com.example.zhengdao.rootfs.RootfsInstaller.isInstalling]）
     * @param archiveBytes 本地安装包字节数；null/0 = 没有
     * @param confirmed 用户是否已在这个对话框里点过「确认重装」
     */
    fun decide(installing: Boolean, archiveBytes: Long?, confirmed: Boolean): Decision = when {
        installing -> Decision.Busy
        archiveBytes == null || archiveBytes <= 0L -> Decision.NoLocalArchive
        !confirmed -> Decision.NeedConfirm
        else -> Decision.Proceed
    }
}

/**
 * 清缓存白名单（负例③：把 rootfs 一起清了会红）。
 *
 * 只认"能重新下载 / 重新生成"的东西：
 * 1. App 私有 `cacheDir` 的**子项**（`cache/` 本义）；
 * 2. [com.example.zhengdao.terminal.CacheCleaner.agentCacheTargets] 那套 Agent 包缓存
 *    （npm/uv/pip/hermes/OpenCode 的下载缓存，全在 home 或公共缓存区）；
 * 3. `rootfs/tmp` 里有明确指纹的残件（[com.example.zhengdao.terminal.CacheCleaner.isStaleTempName]）；
 * 4. 安装包缓存里**非当前版本**的归档与 `.part` 残片（由 `RootfsCache.cleanupNonCurrent` 负责）。
 *
 * 永远不进这张表的：`rootfs/` 本体及其系统子目录（含 `var/lib/apt/lists` 这种"看起来像缓存"
 * 的索引——它属于环境本体，删了要联网重下才能恢复，不在这张单子的授权范围内）。
 */
object CachePlan {

    /** 目录体积（不跟随软链、量不到按 0）。 */
    fun dirBytes(root: File): Long {
        if (!root.exists()) return 0L
        if (root.isFile) return root.length()
        var sum = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children) {
                if (f.isDirectory && !isSymlink(f)) {
                    stack.addLast(f)
                } else if (f.isFile && !isSymlink(f)) {
                    sum += f.length()
                }
            }
        }
        return sum
    }

    private fun isSymlink(f: File): Boolean =
        runCatching { java.nio.file.Files.isSymbolicLink(f.toPath()) }.getOrDefault(false)

    /** 允许整棵删掉的目标目录（顺序即界面展示顺序不重要，去重后返回）。 */
    fun targets(
        filesDir: File,
        cacheDir: File,
        publicCacheRoot: File,
        publicRoot: File,
    ): List<File> {
        val list = ArrayList<File>()
        list += cacheDir
        list += com.example.zhengdao.terminal.CacheCleaner
            .agentCacheTargets(filesDir, publicCacheRoot, publicRoot)
            .map { it.second }
        return list
    }

    /**
     * 单个目标是否在白名单内：必须就是某个白名单根、或位于其**之下**，且不在 `files/rootfs` 里。
     *
     * 双保险的第二道：即使上面 [targets] 被改错，这里也会把 rootfs 路径挡掉
     * （负例③的变异就是往 [targets] 里塞 rootfs）。
     *
     * `rootfs/tmp` 的残件**不走本函数**：那批文件按名字指纹逐个判定
     * （[com.example.zhengdao.terminal.CacheCleaner.staleTempFiles]），不整棵删目录。
     */
    fun isSafeTarget(
        target: File,
        filesDir: File,
        cacheDir: File,
        publicCacheRoot: File,
        publicRoot: File,
    ): Boolean {
        val rootfsDir = File(filesDir, "rootfs")
        if (isInside(rootfsDir, target) || samePath(rootfsDir, target)) return false
        if (samePath(filesDir, target)) return false
        val allowedRoots = targets(filesDir, cacheDir, publicCacheRoot, publicRoot)
        return allowedRoots.any { samePath(it, target) || isInside(it, target) }
    }

    private fun canonical(f: File): String? = runCatching { f.canonicalPath }.getOrNull()

    private fun samePath(a: File, b: File): Boolean {
        val ca = canonical(a) ?: return false
        val cb = canonical(b) ?: return false
        return ca == cb
    }

    fun isInside(parent: File, child: File): Boolean {
        val p = canonical(parent) ?: return false
        val c = canonical(child) ?: return false
        return c.startsWith(p + File.separator)
    }
}
