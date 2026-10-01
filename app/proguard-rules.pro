# ---- 阅闻 News App：release(R8) 保留规则 ----

# XML 解析（部分 ROM 的实现类不在编译期 classpath 上）
-dontwarn org.xmlpull.v1.**
-keep class org.xmlpull.v1.** { *; }

# 正文抽取：Readability4J + jsoup。
# 这两者里有较多字符串驱动的逻辑（选择器、实体表），整体保留最稳。
-keep class net.dankito.readability4j.** { *; }
-dontwarn net.dankito.readability4j.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# slf4j：readability4j 用它打日志，我们挂的是 nop 实现
-dontwarn org.slf4j.**

# Room：实体 / DAO 由注解处理器生成实现，泛型与注解需要保留元数据
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep class com.example.yuewen.data.model.** { *; }

# OkHttp / Okio 的可选平台类
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# 崩溃兜底里会用到堆栈信息，保留行号便于排查
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 保留自家代码的类名/方法名（只影响「改名」，不影响裁剪与优化）。
# 否则 release 包崩溃日志全是混淆符号（例如 V2.b@ab3d1f2），
# 每次排查都得翻 mapping.txt 才能知道是哪个类。保留后可读性大幅提升，体积影响很小。
-keepnames class com.example.yuewen.** { *; }
