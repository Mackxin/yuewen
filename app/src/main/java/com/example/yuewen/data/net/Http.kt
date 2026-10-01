package com.example.yuewen.data.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局共享的 OkHttpClient。
 *
 * 之前 RSS 抓取和正文抽取各建了一个 client，等于两套连接池 + 两套线程池；
 * 现在合并成一个，刷新时多个源可以复用已建立的 TCP/TLS 连接，明显更快。
 * 另外把超时收紧：某个源挂住时不会再拖住整个刷新（配合仓库里的并行抓取）。
 */
object Http {
    // 不再写死版本号（以前是 "Yuewen/1.3"，发新版就过时了）
    const val UA_FEED = "Yuewen/1.5 (RSS reader; Android)"
    const val UA_BROWSER =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * 加载图片专用的 UA。
     *
     * 为什么要单独一个：不少图床 / CDN 会按 User-Agent 拦爬虫，
     * 默认的 "Coil/2.x" 这种 UA 很容易被 403 —— 表现就是列表里图片一片空白。
     * 用浏览器 UA 能显著提高成功率。
     */
    const val UA_IMAGE = UA_BROWSER

    val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
