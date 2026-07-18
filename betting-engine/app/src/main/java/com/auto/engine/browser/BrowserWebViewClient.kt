package com.auto.engine.browser

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import androidx.webkit.WebViewClientCompat

class BrowserWebViewClient(
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String, String) -> Unit,
    private val onReceivedError: (String) -> Unit,
    private val onFaviconReceived: ((Bitmap?) -> Unit)? = null
) : WebViewClientCompat() {

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageFinished(url, view.title ?: url)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // In production, you may want to show a dialog. For now, proceed.
        handler.proceed()
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        if (request.isForMainFrame) {
            onReceivedError(error.description.toString())
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        // Handle intent:// and market:// schemes
        if (url.startsWith("intent://") || url.startsWith("market://")) {
            return true // Block these
        }
        return false // Let WebView handle http/https
    }
}
