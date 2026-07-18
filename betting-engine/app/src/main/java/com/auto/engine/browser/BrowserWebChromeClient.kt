package com.auto.engine.browser

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.*
import androidx.core.content.ContextCompat

class BrowserWebChromeClient(
    private val context: Context,
    private val onProgressChanged: (Int) -> Unit,
    private val onTitleReceived: (String) -> Unit,
    private val onFaviconReceived: (Bitmap?) -> Unit,
    private val onPermissionRequest: (PermissionRequest) -> Unit,
    private val onFileChooser: (ValueCallback<Array<Uri>>, FileChooserParams) -> Boolean,
    private val onShowCustomView: (View, CustomViewCallback) -> Unit,
    private val onHideCustomView: () -> Unit,
    private val onGeolocationPermissionsShowPrompt: ((String, GeolocationPermissions.Callback) -> Unit)? = null
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String) {
        onTitleReceived(title)
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap) {
        onFaviconReceived(icon)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        onPermissionRequest(request)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean = onFileChooser(filePathCallback, fileChooserParams)

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        onShowCustomView(view, callback)
    }

    override fun onHideCustomView() {
        onHideCustomView()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        onGeolocationPermissionsShowPrompt?.invoke(origin, callback)
            ?: callback.invoke(origin, false, false)
    }

    override fun onJsAlert(
        view: WebView,
        url: String,
        message: String,
        result: JsResult
    ): Boolean {
        // Let the default dialog handle it
        return false
    }

    override fun onJsConfirm(
        view: WebView,
        url: String,
        message: String,
        result: JsResult
    ): Boolean {
        return false
    }
}
