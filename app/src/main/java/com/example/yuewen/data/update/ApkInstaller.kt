package com.example.yuewen.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把下载好的 apk 交给**系统安装器**（v2.14）。
 *
 * 这里刻意不做「静默安装」——那需要 root 或设备管理员权限，正常 App 拿不到。
 * 所以流程是：我们负责下载 + 把文件安全地暴露成一个 content:// 地址，
 * 然后 `startActivity` 拉起系统的安装界面，**最后由用户点「安装」**。
 *
 * ## 两道系统关卡（都会静默失败，必须提前检查）
 *
 * 1. **`REQUEST_INSTALL_PACKAGES` 权限**（Manifest 里声明的普通权限，安装即授予，
 *    不需要运行时弹窗）；
 * 2. **「安装未知应用」开关**（API 26+ 的用户级开关，**默认关**）。
 *    没开的时候 `startActivity` 不会崩，但系统安装器会直接弹「已阻止安装」——
 *    用户看到的像「点了没反应」，所以必须在拉起前用
 *    `canRequestPackageInstalls()` 查一下，并引导到对应的系统设置页。
 */
object ApkInstaller {

    /**
     * 这台设备现在**允不允许**本应用安装 apk。
     *
     * minSdk 是 26，正好是「安装未知应用」开关引入的版本，
     * 所以不需要给低版本留兜底分支。
     */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * 拉起系统安装器。
     *
     * @return true 表示安装界面已经拉起来了；false 表示没有 App 能处理这个 Intent
     *         （极少数被裁过的 ROM）。调用方据此给提示，别让用户干等。
     */
    fun install(context: Context, apk: File): Boolean {
        if (!apk.exists()) return false
        // ⚠️ 必须走 FileProvider：从 Android 7 起，直接把 file:// 塞进 Intent 会抛
        // FileUriExposedException。authority 要和 Manifest 里注册的一致
        // （`${applicationId}.fileprovider`），路径白名单见 res/xml/file_paths.xml 的 updates 项。
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            // 只给「读」权限：安装器需要读这个文件，但不需要写
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // 从非 Activity 上下文（比如 Application）启动时必须带 NEW_TASK
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * 跳到系统的「安装未知应用」授权页，**并且直接落在本应用那一项上**。
     *
     * 不带 `package:` 参数也能跳，但会落在列表第一页，用户还得自己找到这个 App ——
     * 多一步就多一批放弃的人。
     */
    fun openInstallPermissionSettings(context: Context) {
        val byApp = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // 少数 ROM 不认带 package 的写法，退回到不带参数的列表页
        val ok = runCatching { context.startActivity(byApp) }.isSuccess
        if (!ok) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /**
     * 当前的安装能力摘要，给 UI 直接判断用。
     *
     * 写成一个函数而不是让 UI 自己拼：判断条件将来要是变了（比如目标 API 调整），
     * 只改这一处。
     */
    fun blockedReason(context: Context): String? = when {
        !canInstall(context) ->
            "系统还不允许「阅闻」安装应用。去开启一下「允许安装未知应用」，回来就能装。"
        else -> null
    }

    /** 当前系统版本描述，仅用于排查（例如「API 34」）。 */
    fun apiLevel(): Int = Build.VERSION.SDK_INT
}
