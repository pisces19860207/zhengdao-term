// 独立开发声明：本文件为本项目原创，不含对第三方源码的拷贝。
package com.example.zhengdao

/**
 * 终端预填命令的二次校验（第四刀随终端页一起搬来；原在主仓 `IncomingIntentRouter.kt`）。
 *
 * 为什么两处都判：路由层是第一道闸，但 `TerminalActivity` 是真正把字节送进 pty 的地方；
 * 任何一条新入口绕过路由时，这里必须仍然兜住「不带换行/控制符」。
 * 搬家时**判据一字未改**，只是把函数与常量从主仓的路由文件挪到本仓（主仓那份随路由一起删）。
 */
internal const val MAX_TERMINAL_COMMAND_CHARS = 500

internal fun isSafeTerminalPrefillCommand(command: String): Boolean =
    command.isNotBlank() &&
        command.length <= MAX_TERMINAL_COMMAND_CHARS &&
        !containsTerminalControl(command)

/** 终端命令里的控制字符一律拒：`\n`/`\r` 会直接执行，ESC 等可以注入终端序列。 */
private fun containsTerminalControl(text: String): Boolean = text.any { c ->
    c.code < 0x20 ||
        c.code == 0x7f ||
        c.code in 0x80..0x9f ||
        c == '\u2028' ||
        c == '\u2029'
}
