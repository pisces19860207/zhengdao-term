// 独立开发声明：本文件为本项目原创，不含对第三方源码的拷贝。
package com.example.zhengdao.term

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.zhengdao.TerminalActivity
import com.example.zhengdao.rootfs.RootfsInstaller
import com.example.zhengdao.terminal.TerminalPrefs
import com.example.zhengdao.ui.InstallFlow

/**
 * 设置页（第二百六十六批 · 终端 2）。五项：字体/行高、重装/修复环境、清缓存、镜像与源、关于。
 *
 * 三条硬约定（派工单 2026-10-28）：
 * 1. 它**不是**第二个桌面图标：本页是 [MainActivity] 里的一屏（没有新 Activity、没有 LAUNCHER）。
 * 2. 文案全部走 `strings_term.xml`：本文件不出现中文字面量（门禁测试扫描字面量）。
 * 3. 安装链路只调公开入口：重装走 [TermSettingsActions.reinstall] → `InstallFlow` + `RootfsInstaller`，
 *    没有第二套解压实现。
 *
 * 所有用户可见字符串都在**组合期**用 [stringResource] 解析好再传给回调（不在回调里 `getString`）。
 */
private data class Notice(val resId: Int, val arg: String? = null)

private sealed interface DialogState {
    data object None : DialogState
    data object NoLocalArchive : DialogState
    data class ConfirmReinstall(val archiveBytes: Long) : DialogState
}

@Composable
internal fun TermSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var licenseOpen by remember { mutableStateOf(false) }
    BackHandler { onBack() }
    if (licenseOpen) {
        LicenseScreen(onBack = { licenseOpen = false })
        return
    }

    var sizeDp by remember { mutableIntStateOf(TerminalPrefs.sizeDp(context)) }
    var spacingId by remember { mutableStateOf(TerminalPrefs.spacing(context).id) }
    var env by remember { mutableStateOf<TermSettingsActions.EnvSnapshot?>(null) }
    var cacheBytes by remember { mutableLongStateOf(-1L) }
    var envBusy by remember { mutableStateOf(false) }
    var cacheBusy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    var dialog by remember { mutableStateOf<DialogState>(DialogState.None) }

    val overridePair = remember { TermSettingsActions.mirrorOverride(context) }
    var aptInput by remember { mutableStateOf(overridePair.first) }
    var pipInput by remember { mutableStateOf(overridePair.second) }

    fun refreshEnv() {
        TermSettingsActions.loadSnapshot(context, onDone = { env = it })
    }

    fun refreshCache() {
        TermSettingsActions.loadCacheBytes(context, onDone = { cacheBytes = it })
    }

    LaunchedEffect(Unit) {
        refreshEnv()
        refreshCache()
    }

    val runningText = stringResource(R.string.term_settings_reinstall_running)
    val doneText = stringResource(R.string.term_settings_reinstall_done)
    val failText = stringResource(R.string.term_settings_reinstall_fail)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.term_settings_back))
            }
            Text(
                text = stringResource(R.string.term_settings_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        notice?.let { n ->
            Text(
                text = if (n.arg == null) stringResource(n.resId) else stringResource(n.resId, n.arg),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        SectionTitle(stringResource(R.string.term_settings_section_font))
        Text(
            text = stringResource(R.string.term_settings_font_size) + " " +
                stringResource(R.string.term_settings_font_size_value, "$sizeDp dp"),
            style = MaterialTheme.typography.bodyMedium,
        )
        TerminalPrefs.SIZE_OPTIONS.chunked(3).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    OptionButton(
                        text = option.toString(),
                        selected = option == sizeDp,
                        enabled = true,
                        onClick = {
                            TerminalPrefs.saveSize(context, option)
                            sizeDp = option
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.term_settings_line_height),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionButton(
                text = stringResource(R.string.term_settings_line_height_comfortable),
                selected = spacingId == TerminalPrefs.Spacing.COMFORTABLE.id,
                enabled = true,
                onClick = {
                    TerminalPrefs.saveSpacing(context, TerminalPrefs.Spacing.COMFORTABLE)
                    spacingId = TerminalPrefs.Spacing.COMFORTABLE.id
                },
            )
            OptionButton(
                text = stringResource(R.string.term_settings_line_height_compact),
                selected = spacingId == TerminalPrefs.Spacing.COMPACT.id,
                enabled = true,
                onClick = {
                    TerminalPrefs.saveSpacing(context, TerminalPrefs.Spacing.COMPACT)
                    spacingId = TerminalPrefs.Spacing.COMPACT.id
                },
            )
        }
        Hint(stringResource(R.string.term_settings_font_hint))

        SectionDivider()
        SectionTitle(stringResource(R.string.term_settings_section_env))
        val snapshot = env
        if (snapshot == null || !snapshot.installed) {
            InfoRow(
                label = stringResource(R.string.term_settings_env_version),
                value = stringResource(R.string.term_settings_env_not_installed),
            )
        } else {
            InfoRow(
                label = stringResource(R.string.term_settings_env_version),
                value = snapshot.distro ?: stringResource(R.string.term_settings_env_unrecorded),
            )
            InfoRow(
                label = stringResource(R.string.term_settings_env_fingerprint),
                value = snapshot.env ?: stringResource(R.string.term_settings_env_unrecorded),
            )
            InfoRow(
                label = stringResource(R.string.term_settings_env_installed_at),
                value = snapshot.installedAt ?: stringResource(R.string.term_settings_env_unrecorded),
            )
            InfoRow(
                label = stringResource(R.string.term_settings_env_size),
                value = TermSettingsLogic.formatMb(snapshot.rootfsBytes),
            )
        }
        InfoRow(
            label = stringResource(R.string.term_settings_env_archive),
            value = if ((snapshot?.archiveBytes ?: 0L) > 0L) {
                stringResource(
                    R.string.term_settings_env_archive_value,
                    TermSettingsLogic.formatMb(snapshot!!.archiveBytes),
                )
            } else {
                stringResource(R.string.term_settings_env_archive_missing)
            },
        )
        Hint(stringResource(R.string.term_settings_env_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { refreshEnv() }, enabled = !envBusy) {
                Text(stringResource(R.string.term_settings_env_refresh))
            }
            Button(
                // 快照还没到就不知道有没有本地包，先禁着（否则会误报"没有安装包"）。
                enabled = !envBusy && env != null,
                onClick = {
                    val decision = ReinstallGate.decide(
                        installing = RootfsInstaller.isInstalling(),
                        archiveBytes = env?.archiveBytes,
                        confirmed = false,
                    )
                    when (decision) {
                        ReinstallGate.Decision.Busy ->
                            notice = Notice(R.string.term_settings_reinstall_busy)
                        ReinstallGate.Decision.NoLocalArchive ->
                            dialog = DialogState.NoLocalArchive
                        ReinstallGate.Decision.NeedConfirm ->
                            dialog = DialogState.ConfirmReinstall(env?.archiveBytes ?: 0L)
                        // 未确认时闸门不给 Proceed；这里留空是"结构上到不了"的兜底。
                        ReinstallGate.Decision.Proceed -> Unit
                    }
                },
            ) {
                Text(stringResource(R.string.term_settings_reinstall))
            }
        }
        // 安装进度行复用终端页那一条（InstallProgress 单一状态 + 通知栏），不另做一套进度。
        InstallFlow.StatusLine()

        SectionDivider()
        SectionTitle(stringResource(R.string.term_settings_section_cache))
        Hint(stringResource(R.string.term_settings_cache_desc))
        InfoRow(
            label = stringResource(R.string.term_settings_cache_cleanable),
            value = when {
                cacheBytes < 0L -> ""
                cacheBytes == 0L -> stringResource(R.string.term_settings_cache_none)
                else -> TermSettingsLogic.formatMb(cacheBytes)
            },
        )
        Button(
            enabled = !cacheBusy,
            onClick = {
                cacheBusy = true
                notice = null
                TermSettingsActions.clearCache(context) { freed, err ->
                    cacheBusy = false
                    notice = if (err == null) {
                        Notice(
                            R.string.term_settings_cache_freed,
                            TermSettingsLogic.formatMb(freed),
                        )
                    } else {
                        Notice(R.string.term_settings_cache_error, err)
                    }
                    refreshCache()
                }
            },
        ) {
            Text(
                stringResource(
                    if (cacheBusy) R.string.term_settings_cache_cleaning
                    else R.string.term_settings_cache_clean
                )
            )
        }

        SectionDivider()
        SectionTitle(stringResource(R.string.term_settings_section_mirror))
        InfoRow(
            label = stringResource(R.string.term_settings_mirror_current) + " " +
                stringResource(R.string.term_settings_mirror_apt),
            value = snapshot?.aptMirror ?: TermSettingsLogic.DEFAULT_APT_MIRROR,
        )
        InfoRow(
            label = stringResource(R.string.term_settings_mirror_current) + " " +
                stringResource(R.string.term_settings_mirror_pip),
            value = snapshot?.pipIndex ?: TermSettingsLogic.DEFAULT_PIP_INDEX,
        )
        Hint(stringResource(R.string.term_settings_mirror_override))
        OutlinedTextField(
            value = aptInput,
            onValueChange = { aptInput = it },
            label = { Text(stringResource(R.string.term_settings_mirror_apt_label)) },
            singleLine = true,
            enabled = snapshot?.installed == true,
            modifier = Modifier.fillMaxWidth(),
        )
        Hint(stringResource(R.string.term_settings_mirror_apt_hint))
        OutlinedTextField(
            value = pipInput,
            onValueChange = { pipInput = it },
            label = { Text(stringResource(R.string.term_settings_mirror_pip_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val ok = TermSettingsActions.saveMirrorOverride(context, aptInput, pipInput)
                    notice = if (ok) Notice(R.string.term_settings_mirror_saved)
                    else Notice(R.string.term_settings_mirror_error, aptInput.trim())
                },
            ) {
                Text(stringResource(R.string.term_settings_mirror_save))
            }
            OutlinedButton(
                onClick = {
                    aptInput = ""
                    pipInput = ""
                    val ok = TermSettingsActions.saveMirrorOverride(context, "", "")
                    notice = if (ok) Notice(R.string.term_settings_mirror_cleared)
                    else Notice(R.string.term_settings_mirror_error, "")
                },
            ) {
                Text(stringResource(R.string.term_settings_mirror_reset))
            }
        }

        SectionDivider()
        SectionTitle(stringResource(R.string.term_settings_section_about))
        InfoRow(
            label = stringResource(R.string.term_settings_about_version),
            value = BuildConfig.VERSION_NAME,
        )
        InfoRow(
            label = stringResource(R.string.term_settings_about_app_id),
            value = BuildConfig.APPLICATION_ID,
        )
        TextButton(onClick = { licenseOpen = true }) {
            Text(stringResource(R.string.term_settings_about_license_open))
        }
        Hint(stringResource(R.string.term_settings_about_license))
    }

    when (val d = dialog) {
        DialogState.None -> Unit
        DialogState.NoLocalArchive -> AlertDialog(
            onDismissRequest = { dialog = DialogState.None },
            title = { Text(stringResource(R.string.term_settings_reinstall_no_archive_title)) },
            text = { Text(stringResource(R.string.term_settings_reinstall_no_archive_message)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = DialogState.None
                    context.startActivity(Intent(context, TerminalActivity::class.java))
                }) {
                    Text(stringResource(R.string.term_settings_reinstall_go_terminal))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = DialogState.None }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
        is DialogState.ConfirmReinstall -> AlertDialog(
            onDismissRequest = { dialog = DialogState.None },
            title = { Text(stringResource(R.string.term_settings_reinstall_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.term_settings_reinstall_message,
                        TermSettingsLogic.formatMb(d.archiveBytes),
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = DialogState.None
                    envBusy = true
                    notice = null
                    TermSettingsActions.reinstall(
                        ctx = context,
                        runningText = runningText,
                        doneText = doneText,
                        failText = failText,
                    ) { ok, err ->
                        envBusy = false
                        notice = when {
                            ok -> Notice(R.string.term_settings_reinstall_done)
                            err != null -> Notice(R.string.term_settings_reinstall_error, err)
                            else -> Notice(R.string.term_settings_reinstall_busy)
                        }
                        refreshEnv()
                        refreshCache()
                    }
                }) {
                    Text(stringResource(R.string.term_settings_reinstall_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = DialogState.None }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** 随包许可证正文（assets 里的副本；与仓根 `THIRD-PARTY-LICENSES.md` 由门禁钉住一致性）。 */
@Composable
private fun LicenseScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val text = remember { TermSettingsActions.licenseText(context) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.term_settings_back))
            }
            Text(
                text = stringResource(R.string.term_settings_license_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = text ?: stringResource(R.string.term_settings_license_missing),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(top = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun OptionButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val content: @Composable () -> Unit = { Text(text) }
    if (selected) {
        Button(onClick = onClick, enabled = enabled) { content() }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) { content() }
    }
}
