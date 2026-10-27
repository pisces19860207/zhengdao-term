# 证道终端 · R8/ProGuard 规则
#
# 骨架期没有需要保护的代码（一个 Compose 壳页），release 也没开混淆。
# 真正的 keep 规则随终端内核一起定（第四刀）：JNI 方法名、Termux 反射点、
# 以及 rootfs 安装器的序列化结构，都会在那时逐条落在这里。
