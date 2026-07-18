package com.auto.engine.tab

import android.graphics.Bitmap
import android.webkit.WebView

data class Tab(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String = "New Tab",
    var url: String = "about:blank",
    var favicon: Bitmap? = null,
    var isIncognito: Boolean = false,
    var isLoading: Boolean = false,
    var progress: Int = 0,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    var webView: WebView? = null,
    val createdAt: Long = System.currentTimeMillis()
)
