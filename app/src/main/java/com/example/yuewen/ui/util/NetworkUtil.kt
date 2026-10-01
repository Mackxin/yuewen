package com.example.yuewen.ui.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** 是否连着 Wi-Fi（用于「仅 Wi-Fi 加载图片」设置）。 */
fun isWifi(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val net = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(net) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
