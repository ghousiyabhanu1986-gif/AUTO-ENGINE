package com.auto.engine.browser

import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import com.auto.engine.extensions.ChromeApisBridge
import java.io.File
import java.io.FileInputStream

class BrowserWebViewClient(
    private val context: Context,
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (String, String) -> Unit,
    private val onReceivedError: (String) -> Unit,
    private val onFaviconReceived: ((Bitmap?) -> Unit)? = null
) : WebViewClient() {

    companion object {
        const val EXTENSION_SCHEME = "chrome-extension://"
    }

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

    /**
     * Intercept chrome-extension:// resource requests and serve them from
     * the installed extension directories. This enables loading extension
     * images, fonts, and other assets via chrome.runtime.getURL().
     */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (url.startsWith(EXTENSION_SCHEME)) {
            // Strip the scheme and authority to get the resource path
            // Format: chrome-extension://auto-engine-extension/<resource_path>
            val afterScheme = url.removePrefix(EXTENSION_SCHEME)
            val resourcePath = afterScheme.substringAfter("/")
            if (resourcePath.isNotEmpty()) {
                val file = ChromeApisBridge.findExtensionResource(context, resourcePath)
                if (file != null && file.exists()) {
                    val mimeType = when {
                        file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") -> "image/jpeg"
                        file.name.endsWith(".png") -> "image/png"
                        file.name.endsWith(".gif") -> "image/gif"
                        file.name.endsWith(".svg") -> "image/svg+xml"
                        file.name.endsWith(".webp") -> "image/webp"
                        file.name.endsWith(".css") -> "text/css"
                        file.name.endsWith(".js") -> "application/javascript"
                        file.name.endsWith(".html") -> "text/html"
                        file.name.endsWith(".json") -> "application/json"
                        file.name.endsWith(".woff2") -> "font/woff2"
                        file.name.endsWith(".woff") -> "font/woff"
                        file.name.endsWith(".ttf") -> "font/ttf"
                        else -> "application/octet-stream"
                    }
                    try {
                        val inputStream = FileInputStream(file)
                        return WebResourceResponse(mimeType, "UTF-8", inputStream)
                    } catch (e: Exception) {
                        return null
                    }
                }
            }
        }
        return super.shouldInterceptRequest(view, request)
    }
}
