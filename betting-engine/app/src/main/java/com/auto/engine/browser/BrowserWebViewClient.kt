package com.auto.engine.browser

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*

class BrowserWebViewClient(
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String, String) -> Unit,
    private val onReceivedError: (String) -> Unit,
    private val onFaviconReceived: ((Bitmap?) -> Unit)? = null
) : WebViewClient() {

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageFinished(url, view.title ?: url)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // In production you may want to show a warning dialog instead.
        handler.proceed()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
        onReceivedError(description)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (url.startsWith("intent://") || url.startsWith("market://")) {
            return true // block non-http schemes
        }
        return false
    }
}
