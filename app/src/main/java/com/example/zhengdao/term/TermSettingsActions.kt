// 独立开发声明：本文件为本项目原创，不含对第三方源码的拷贝。
package com.example.zhengdao.term

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import com.example.zhengdao.core.ZhengdaoDirs
import com.example.zhengdao.rootfs.RootfsCache
import com.example.zhengdao.rootfs.RootfsInstaller
import com.example.zhengdao.rootfs.RootfsMarker
import com.example.zhengdao.terminal.CacheCleaner
import com.example.zhengdao.terminal.ProotLauncher
import com.example.zhengdao.terminal.SessionManager
import com.example.zhengdao.ui.InstallFlow
import com.example.zhengdao.ui.RootfsState
import java.io.File

/**
 * 设置页的**动手部分**：读快照、重装、清缓存、写镜像覆盖。
 *
 * 三条硬约定（派工单 2026-10-28）在这里落实：
 * 1. **不动安装链路**：[InstallFlow] / [com.example.zhengdao.ui.InstallProgress] 一个字不改，
 *    「重装」只调它们的公开入口（`start/update/finish/fail`）+ [RootfsInstaller.install] ——
 *    与终端页首次安装走同一台内核，没有第二套解压实现。
 * 2. 文案全部由界面在组合期解析成字符串传进来（本文件不 `getString`、不拼中文）。
 * 3. 镜像覆盖只写 home 层（`filesDir/home/.zhengdao`），不写 rootfs 里的任何文件。
 *
 * 所有重活都在后台线程；结果一律回主线程。
 */
object TermSettingsActions {

    private const val PREFS = "zhengdao-term-settings"
    private const val KEY_APT_MIRROR = "mirror_apt_override"
    private const val KEY_PIP_INDEX = "mirror_pip_override"

    /** 随包的第三方许可证副本（与仓根 `THIRD-PARTY-LICENSES.md` 同源，门禁钉住一致性）。 */
    const val LICENSE_ASSET = "licenses/THIRD-PARTY-LICENSES.md"

    private val main = Handler(Looper.getMainLooper())

    /** 环境快照（版本/指纹/占用/本地包/当前源），全部只读。 */
    data class EnvSnapshot(
        val installed: Boolean,
        val distro: String?,
        val env: String?,
        val installedAt: String?,
        val rootfsBytes: Long,
        val archive: File?,
        val archiveBytes: Long,
        val aptMirror: String,
        val pipIndex: String,
    )

    private fun rootfsDir(ctx: Context): File = File(ctx.filesDir, "rootfs")

    private fun readTextOrNull(file: File): String? = runCatching {
        if (file.isFile) file.readText() else null
    }.getOrNull()

    /** 本地安装包：与终端页同一判定（[RootfsCache.findLocalArchive] 只看真归档名 + 够大）。 */
    private fun findArchive(ctx: Context): File? {
        if (!ProotLauncher.storageGranted(ctx)) return null
        return runCatching {
            RootfsCache.findLocalArchive(
                ctx,
                preferredName = ProotLauncher.DEFAULT_ROOTFS_URL.substringAfterLast('/'),
            )
        }.getOrNull()
    }

    /** 读快照。会走一遍 rootfs 目录树算体积，**必须在后台线程**调用。 */
    fun snapshot(ctx: Context): EnvSnapshot {
        val rootfs = rootfsDir(ctx)
        val marker = RootfsMarker.read(rootfs)
        val archive = findArchive(ctx)
        val aptFile = File(rootfs, TermSettingsLogic.APT_SOURCES_REL)
        val pipFile = File(rootfs, TermSettingsLogic.PIP_CONF_REL)
        return EnvSnapshot(
            installed = marker != null,
            distro = marker?.distro,
            env = marker?.env,
            installedAt = marker?.installedAt,
            rootfsBytes = CachePlan.dirBytes(rootfs),
            archive = archive,
            archiveBytes = archive?.length() ?: 0L,
            aptMirror = TermSettingsLogic.firstDebUri(readTextOrNull(aptFile))
                ?: TermSettingsLogic.DEFAULT_APT_MIRROR,
            pipIndex = TermSettingsLogic.firstIndexUrl(readTextOrNull(pipFile))
                ?: TermSettingsLogic.DEFAULT_PIP_INDEX,
        )
    }

    /**
     * 重装/修复环境：**复用** [RootfsInstaller.install]（与终端页首次安装同一台内核）。
     *
     * env 的写回走 [RootfsInstaller.envForReinstall]（安装内核用实际校验出的 sha 回调），
     * 这样"重装同一个包"不会把增量基线抹掉；说不清来源时它返回 null ⇒ 照装但不写 env 行。
     *
     * 调用方必须先过 [ReinstallGate]（二次确认）。这里只做一次进程级 busy 复核。
     */
    fun reinstall(
        ctx: Context,
        runningText: String,
        doneText: String,
        failText: String,
        onDone: (ok: Boolean, error: String?) -> Unit,
    ) {
        val app = ctx.applicationContext
        if (RootfsInstaller.isInstalling()) {
            onDone(false, null)
            return
        }
        val archive = findArchive(app)
        if (archive == null) {
            onDone(false, null)
            return
        }
        io(
            block = {
                InstallFlow.start(app, runningText, fromLocal = true)
                try {
                    val rootfs = rootfsDir(app)
                    RootfsInstaller.install(
                        context = app,
                        archive = archive,
                        resolveEnv = { actualSha ->
                            RootfsInstaller.envForReinstall(rootfs, actualSha)
                        },
                    ) { }
                    runCatching { RootfsCache.pruneKeep(app) }
                    RootfsState.markInstalled()
                    // 换完树，旧会话跑的是已作废的那棵（终端页装完也是先 kill 再重开，见 relaunchDebian）。
                    runCatching { SessionManager.kill(app) }
                    InstallFlow.finish(app, doneText)
                    null
                } catch (t: Throwable) {
                    InstallFlow.fail(app, failText)
                    t.message ?: t.javaClass.simpleName
                }
            },
            onOk = { error -> onDone(error == null, error) },
            onErr = { message -> onDone(false, message) },
        )
    }

    /** 界面用的快照加载（后台线程 + 回主线程）。 */
    fun loadSnapshot(ctx: Context, onDone: (EnvSnapshot) -> Unit, onErr: (String) -> Unit = {}) =
        io({ snapshot(ctx) }, onDone, onErr)

    /** 界面用的"可释放"加载（后台线程 + 回主线程）。 */
    fun loadCacheBytes(ctx: Context, onDone: (Long) -> Unit, onErr: (String) -> Unit = {}) =
        io({ clearableBytes(ctx) }, onDone, onErr)

    /** 可释放字节数（与 [clearCache] 同一套白名单口径）。后台线程调用。 */
    fun clearableBytes(ctx: Context): Long = cacheBytes(ctx)

    /**
     * 清缓存：只清白名单里的缓存与残件，**不碰 rootfs 本体**。
     *
     * 释放量 = 清前/清后实测之差（不是"打算删多少"），删不掉的部分因此不会虚报。
     */
    fun clearCache(ctx: Context, onDone: (freedBytes: Long, error: String?) -> Unit) {
        val app = ctx.applicationContext
        io(
            block = {
                val before = cacheBytes(app)
                // 1) cache/ 本体（App 私有缓存目录的全部子项）
                app.cacheDir.listFiles()?.forEach { f ->
                    if (f.isDirectory) f.deleteRecursively() else f.delete()
                }
                // 2) Agent 包缓存（npm/uv/pip/hermes/OpenCode 的下载缓存）
                runCatching { CacheCleaner.cleanAgentCaches(app) }
                // 3) rootfs/tmp 里有明确指纹的残件（按名字逐个删，不整棵删目录）
                runCatching { CacheCleaner.cleanTempFiles(app) }
                // 4) 安装包缓存里非当前版本的归档与 .part 残片
                runCatching { RootfsCache.cleanupNonCurrent(app) }
                (before - cacheBytes(app)).coerceAtLeast(0L)
            },
            onOk = { freed -> onDone(freed, null) },
            onErr = { message -> onDone(0L, message) },
        )
    }

    private fun cacheBytes(ctx: Context): Long {
        val filesDir = ctx.filesDir
        val cacheDir = ctx.cacheDir
        val publicCacheRoot = runCatching { ZhengdaoDirs.cacheRoot(ctx) }
            .getOrElse { File(filesDir, "cache-public") }
        val publicRoot = runCatching { ZhengdaoDirs.root(ctx) }
            .getOrElse { File(filesDir, "public") }
        val targets = CachePlan.targets(filesDir, cacheDir, publicCacheRoot, publicRoot)
            .distinct()
            .filter { CachePlan.isSafeTarget(it, filesDir, cacheDir, publicCacheRoot, publicRoot) }
        var sum = targets.filter { it.isDirectory }.sumOf { CachePlan.dirBytes(it) }
        // 安装包缓存：非当前版本的归档 + .part 残片（与 RootfsCache.cleanupNonCurrent 同口径）
        runCatching {
            sum += RootfsCache.listArchives(ctx).drop(2).sumOf { it.length() }
            RootfsCache.dir(ctx).listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".part")) sum += f.length()
            }
        }
        // 残件：真正会被删掉的那批（不是整个 /tmp）
        sum += runCatching {
            CacheCleaner
                .staleTempFilesIn(File(filesDir, "rootfs/tmp"), System.currentTimeMillis())
                .sumOf { it.length() }
        }.getOrDefault(0L)
        return sum
    }

    // ── 镜像与源（只读展示 + 环境变量式覆盖）────────────────────────────────

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 用户填的覆盖值（空串 = 不覆盖）。 */
    fun mirrorOverride(ctx: Context): Pair<String, String> =
        prefs(ctx).getString(KEY_APT_MIRROR, "").orEmpty() to
            prefs(ctx).getString(KEY_PIP_INDEX, "").orEmpty()

    /**
     * 保存覆盖：写 `home/.zhengdao/env.sh`（+ apt 覆盖时的 `apt.conf` / `sources.list`），
     * 并在 `home/.profile` 里维护托管块。**rootfs 里的文件一个都不碰。**
     *
     * apt 覆盖的 sources 由环境里那份 `sources.list` **只换 URI** 得到；环境没装/读不到时
     * 只写 pip 部分（界面已经把 apt 输入框禁用了，这里是第二道闸）。
     */
    fun saveMirrorOverride(ctx: Context, aptMirror: String, pipIndex: String): Boolean {
        val apt = aptMirror.trim()
        val pip = pipIndex.trim()
        prefs(ctx).edit {
            putString(KEY_APT_MIRROR, apt)
            putString(KEY_PIP_INDEX, pip)
        }
        val home = File(ctx.filesDir, "home")
        val zd = File(home, ".zhengdao")
        val body = TermSettingsLogic.envBody(apt, pip)
        return runCatching {
            if (body.isBlank()) {
                // 清除覆盖：删掉自己的文件 + 托管块（块外内容保留）
                File(zd, "env.sh").delete()
                File(zd, "apt.conf").delete()
                File(zd, "sources.list").delete()
                val profile = File(home, ".profile")
                if (profile.isFile) {
                    profile.writeText(
                        TermSettingsLogic.removeManagedBlock(profile.readText()).trimEnd('\n') + "\n"
                    )
                }
                return@runCatching true
            }
            if (!zd.isDirectory && !zd.mkdirs()) return@runCatching false
            File(zd, "env.sh").writeText(body + "\n")
            if (apt.isNotEmpty()) {
                val original = readTextOrNull(File(rootfsDir(ctx), TermSettingsLogic.APT_SOURCES_REL))
                    ?: return@runCatching false
                File(zd, "sources.list").writeText(
                    TermSettingsLogic.aptSourcesWithMirror(original, apt)
                )
                File(zd, "apt.conf").writeText(
                    "Dir::Etc::sourcelist \"\$HOME/.zhengdao/sources.list\";\n" +
                        "Dir::Etc::sourceparts \"-\";\n"
                )
            } else {
                File(zd, "sources.list").delete()
                File(zd, "apt.conf").delete()
            }
            val profile = File(home, ".profile")
            val existing = readTextOrNull(profile) ?: skelProfile(ctx).orEmpty()
            profile.writeText(
                TermSettingsLogic.upsertManagedBlock(existing, body)
            )
            true
        }.getOrDefault(false)
    }

    /**
     * `~/.profile` 不存在时先照 [ProotLauncher] 的做法从 `/etc/skel/.profile` 落一份骨架
     * （那份里有 `~/.local/bin` 进 PATH 的逻辑）。骨架也读不到就给空串——PATH 兜底还有
     * ProotLauncher 写的 `/etc/profile.d/zz-local-bin-path.sh`，不会因此丢命令。
     */
    private fun skelProfile(ctx: Context): String? =
        readTextOrNull(File(rootfsDir(ctx), "etc/skel/.profile"))

    /** 第三方许可证正文（assets 随包副本）；读不到返回 null。 */
    fun licenseText(ctx: Context): String? = runCatching {
        ctx.assets.open(LICENSE_ASSET).bufferedReader().use { it.readText() }
    }.getOrNull()

    // ── 线程 ────────────────────────────────────────────────────────────────

    private fun <T> io(block: () -> T, onOk: (T) -> Unit, onErr: (String) -> Unit) {
        Thread {
            val outcome = runCatching(block)
            main.post {
                outcome.fold(
                    onSuccess = onOk,
                    onFailure = { t -> onErr(t.message ?: t.javaClass.simpleName) },
                )
            }
        }.apply { isDaemon = true; name = "zd-term-settings" }.start()
    }
}
