# 证道终端 · R8/ProGuard 规则
# JNI 入口（Termux terminal-emulator）：方法名不能被改
-keepclasseswithmembernames class * { native <methods>; }
# Termux 终端内核：JNI/反射/序列化点都在这个包下，整体保留
-keep class com.termux.** { *; }
# 自研终端/rootfs 入口保留类名（native 侧按类名绑定）
-keep class com.example.zhengdao.terminal.** { *; }
-keep class com.example.zhengdao.rootfs.** { *; }
