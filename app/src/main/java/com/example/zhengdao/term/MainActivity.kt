// 独立开发声明：本文件为本项目原创，不含对第三方源码的拷贝。
package com.example.zhengdao.term

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.example.zhengdao.TerminalActivity
import com.example.zhengdao.core.EnvironmentScope
import com.example.zhengdao.rootfs.RootfsMarker

/**
 * 证道终端的**真入口**（第二百四十八批：终端从主仓搬进来之后）。
 *
 * 它只做三件事，按顺序引导：
 * 1. **授权**：`MANAGE_EXTERNAL_STORAGE`（所有文件访问）—— proot 里的进程就是本 App 的 UID，
 *    没有它终端只能在私有目录里打转。这条**不能由 App 自己申请**，必须跳系统设置让用户单独开
 *    （与 Termux 同款，见 README「权限」）。
 * 2. **装环境**：rootfs 是否已装，用 `RootfsMarker.installedEnv()` 判定（终端页写的那份标记）。
 * 3. **打开终端**：进 [TerminalActivity]；环境没装时它自己会拉起下载/安装引导（那条流程
 *    是随终端一起从主仓搬来的，本页不重复实现一份）。
 *
 * 为什么不做"一键自动装"：装环境要下载约 200 MB，中途会弹系统对话框、要选本地包、要显示进度，
 * 那套交互在终端页里已经存在且被真机验证过；入口页再造一份只会分叉。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TermTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    EntryScreen(
                        onGrant = ::openAllFilesAccessSettings,
                        onOpenTerminal = { startActivity(Intent(this, TerminalActivity::class.java)) },
                    )
                }
            }
        }
    }

    /** 跳到系统的「所有文件访问」页（本 App 的条目），让用户自己开。 */
    private fun openAllFilesAccessSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }
}

/**
 * 入口页：图标 + 名称 + 版本 + 三步清单 + 一个主按钮。
 *
 * 状态在 `ON_RESUME` 时重算 —— 用户从系统设置页授权回来、或从终端页装完环境回来，
 * 页面要立刻反映"这一步已经过了"，而不是要杀进程重进。
 */
@Composable
private fun EntryScreen(onGrant: () -> Unit, onOpenTerminal: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by mutableStateOf(EnvironmentScope.hasAllFilesAccess(context))
    var installed by mutableStateOf(RootfsMarker.installedEnv(context) != null)

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = EnvironmentScope.hasAllFilesAccess(context)
                installed = RootfsMarker.installedEnv(context) != null
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(112.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StepRow(1, stringResource(R.string.entry_step_grant), granted)
            StepRow(2, stringResource(R.string.entry_step_install), installed)
            StepRow(3, stringResource(R.string.entry_step_open), installed)
        }

        Button(
            onClick = if (granted) onOpenTerminal else onGrant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
        ) {
            Text(
                text = if (!granted) stringResource(R.string.entry_action_grant)
                else if (!installed) stringResource(R.string.entry_action_install)
                else stringResource(R.string.entry_action_open),
            )
        }

        Text(
            text = stringResource(R.string.entry_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )

        Text(
            text = stringResource(R.string.about_scope),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 40.dp),
        )
        Text(
            text = stringResource(R.string.about_repo_label) + " · " + stringResource(R.string.about_repo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 一行"第几步 / 做什么 / 过了没"。 */
@Composable
private fun StepRow(index: Int, label: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (done) "✓" else "$index.",
            style = MaterialTheme.typography.bodyLarge,
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** 暗底 + 绿光标，与图标（`#0E1114` 底 / `#6BE675` 前景）同一色系。 */
@Composable
private fun TermTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6BE675),
            onPrimary = Color(0xFF06210B),
            background = Color(0xFF0E1114),
            onBackground = Color(0xFFE6EDF3),
            surface = Color(0xFF0E1114),
            onSurface = Color(0xFFE6EDF3),
            onSurfaceVariant = Color(0xFF9BA7B4),
            outlineVariant = Color(0xFF2A3138),
        ),
        content = content,
    )
}
