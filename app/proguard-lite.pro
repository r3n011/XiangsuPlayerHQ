# ⚡ lite（no-gms）变体专属规则。
#
# lite 以「运行时存在、零激活」策略随包带入 GMS 库（liteImplementation），
# 保证方法验证期/指令级的 GMS 引用全部可解析（否则 NoClassDefFoundError）。
# 但 R8 会因 GMS_ENABLED=false 常量折叠把门控死分支的引用消掉、进而把这些类
# 当不可达剥掉——运行时残余引用（StateFlow emit/协程链路等）随即崩溃。
# 这里对 GMS 类整体名义保留；功能零激活不受影响（所有初始化仍被
# GMS_ENABLED 门控与端口 NoOp 阻断，lite 不注册任何 GMS 组件）。
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.libraries.identity.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.libraries.identity.**
