// 独立开发声明：本文件为本项目原创，不含对第三方源码的拷贝。
package com.example.zhengdao.term

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 证道终端的**唯一入口**（第二百四十七批：骨架壳页）。
 *
 * 这一版刻意只有一个页面，因为这一批只立骨架：
 * - 终端页（PTY + rootfs + proot）随内核一起进来，那是第四刀；
 * - 与证道主仓共享的 `:core`（RunLog / ZhengdaoDirs 等）第五刀再接。
 *
 * 页面上写的每一句都是**给用户的分辨信息**，不是占位文案：
 * 「证道本体不在此仓」防的是装错了 App 却以为是终端坏了；
 * 「终端内核待搬入」防的是有人把空壳当成"终端不能用"报 bug。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZhengdaTermTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AboutScreen()
                }
            }
        }
    }
}

/**
 * 壳页：图标 + 名称 + 版本 + 三句定位 + 许可 + 仓库。
 *
 * 没有按钮、没有输入框——骨架期不提供任何"看起来能用其实是空的"入口。
 */
@Composable
private fun AboutScreen() {
    val accent = MaterialTheme.colorScheme.primary
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
            modifier = Modifier.size(120.dp),
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
            color = accent,
            modifier = Modifier.padding(top = 4.dp),
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 28.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        Text(
            text = stringResource(R.string.about_scope),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Text(
            text = stringResource(R.string.about_pending),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )

        Text(
            text = stringResource(R.string.about_license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 40.dp),
        )

        Text(
            text = stringResource(R.string.about_repo_label) + " · " + stringResource(R.string.about_repo),
            style = MaterialTheme.typography.bodySmall,
            color = accent,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * 暗底 + 绿光标，与图标（`#0E1114` 底 / `#6BE675` 前景）同一色系。
 *
 * 只用 Material3 的 `darkColorScheme`，不引任何第三方主题库 —— 骨架期的视觉
 * 目标只有一个：点开不像"半成品"，且与证道主 APP（浅色）在任务切换里一眼分得开。
 */
@Composable
private fun ZhengdaTermTheme(content: @Composable () -> Unit) {
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
