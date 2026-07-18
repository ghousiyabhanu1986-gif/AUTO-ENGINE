package com.auto.engine.extensions

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.gson.Gson
import java.io.File
import java.lang.ref.WeakReference

class ChromeApisBridge(
    private val activity: Activity,
    private val isIncognito: Boolean
) {
    private val gson = Gson()
    private val context: Context = activity
    private val storage = context.getSharedPreferences("ext_storage", Context.MODE_PRIVATE)
    private val syncStorage = context.getSharedPreferences("ext_storage_sync", Context.MODE_PRIVATE)
    private val extensionsDir = File(context.filesDir, "extensions")

    @Volatile
    private var webViewRef: WeakReference<WebView>? = null

    fun attachWebView(webView: WebView) {
        webViewRef = WeakReference(webView)
    }

    fun detachWebView() {
        webViewRef = null
    }

    /**
     * Simulate a real native Android tap using the WebView's internal touch handler.
     * 
     * We dispatch MotionEvent directly to the WebView's onTouchEvent — this is what
     * Android calls internally when a real finger touches the screen. It bypasses
     * the view hierarchy routing and hits the WebView's Chromium touch handler directly.
     *
     * Additionally, we try dispatching through the Activity's decor view as fallback.
     */
    @JavascriptInterface
    fun simulateNativeTap(x: Double, y: Double) {
        val webView = webViewRef?.get() ?: return
        val density = context.resources.displayMetrics.density
        val px = (x * density).toFloat()
        val py = (y * density).toFloat()

        // Also account for WebView position within its parent if needed
        val webViewLocation = IntArray(2)
        webView.getLocationOnScreen(webViewLocation)

        val downTime = SystemClock.uptimeMillis()
        
        // Try 1: Use WebView.onTouchEvent directly (this is what Android calls internally)
        try {
            val down = MotionEvent.obtain(
                downTime, downTime,
                MotionEvent.ACTION_DOWN, px, py, 0
            )
            webView.onTouchEvent(down)
            down.recycle()

            Thread.sleep(60)

            val up = MotionEvent.obtain(
                downTime, downTime + 60,
                MotionEvent.ACTION_UP, px, py, 0
            )
            webView.onTouchEvent(up)
            up.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Try 2: Also dispatch through the Activity's window decor view
        // This ensures the touch goes through the full Android view hierarchy
        try {
            val decorView = activity.window?.decorView
            if (decorView != null) {
                val screenX = webViewLocation[0] + px
                val screenY = webViewLocation[1] + py
                val down2 = MotionEvent.obtain(
                    downTime + 200, downTime + 200,
                    MotionEvent.ACTION_DOWN, screenX.toFloat(), screenY.toFloat(), 0
                )
                decorView.dispatchTouchEvent(down2)
                down2.recycle()

                Thread.sleep(60)

                val up2 = MotionEvent.obtain(
                    downTime + 200, downTime + 260,
                    MotionEvent.ACTION_UP, screenX.toFloat(), screenY.toFloat(), 0
                )
                decorView.dispatchTouchEvent(up2)
                up2.recycle()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ─── chrome.storage.local ────────────────────────────────────────────────

    @JavascriptInterface
    fun storageLocalGet(keysJson: String): String {
        return try {
            val keys = gson.fromJson(keysJson, Array<String>::class.java)
            val result = mutableMapOf<String, Any?>()
            val all = storage.all
            keys.forEach { key -> result[key] = all[key] }
            gson.toJson(result)
        } catch (e: Exception) {
            gson.toJson(storage.all)
        }
    }

    @JavascriptInterface
    fun storageLocalSet(dataJson: String) {
        try {
            val map = gson.fromJson(dataJson, Map::class.java)
            val edit = storage.edit()
            map.forEach { (k, v) -> edit.putString(k.toString(), v?.toString() ?: "") }
            edit.apply()
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun storageLocalRemove(keysJson: String) {
        try {
            val keys = gson.fromJson(keysJson, Array<String>::class.java)
            val edit = storage.edit()
            keys.forEach { edit.remove(it) }
            edit.apply()
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun storageLocalClear() = storage.edit().clear().apply()

    @JavascriptInterface
    fun storageSyncGet(keysJson: String): String {
        return try {
            val keys = gson.fromJson(keysJson, Array<String>::class.java)
            val result = mutableMapOf<String, Any?>()
            val all = syncStorage.all
            keys.forEach { key -> result[key] = all[key] }
            gson.toJson(result)
        } catch (e: Exception) {
            gson.toJson(syncStorage.all)
        }
    }

    @JavascriptInterface
    fun storageSyncSet(dataJson: String) {
        try {
            val map = gson.fromJson(dataJson, Map::class.java)
            val edit = syncStorage.edit()
            map.forEach { (k, v) -> edit.putString(k.toString(), v?.toString() ?: "") }
            edit.apply()
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun tabsGetCurrent(): String {
        val tab = mapOf(
            "id" to 1, "url" to "about:blank", "title" to "Tab",
            "active" to true, "incognito" to isIncognito
        )
        return gson.toJson(tab)
    }

    @JavascriptInterface
    fun tabsCreate(url: String): String {
        val tab = mapOf("id" to 2, "url" to url, "active" to true)
        return gson.toJson(tab)
    }

    @JavascriptInterface
    fun runtimeGetId(): String = "betting_engine_runtime"

    @JavascriptInterface
    fun runtimeGetManifest(): String = """{"name":"BettingEngine","version":"1.0","manifest_version":3}"""

    @JavascriptInterface
    fun runtimeSendMessage(message: String): String = """{"success":true}"""

    @JavascriptInterface
    fun runtimeGetURL(resourcePath: String): String = "chrome-extension://auto-engine-extension/$resourcePath"

    @JavascriptInterface
    fun webNavigationGetCurrentTab(): String = tabsGetCurrent()

    @JavascriptInterface
    fun isIncognitoMode(): Boolean = isIncognito

    @JavascriptInterface
    fun getVersion(): String = "1.0.0"

    companion object {
        fun findExtensionResource(context: Context, resourcePath: String): File? {
            val extensionsDir = File(context.filesDir, "extensions")
            if (!extensionsDir.exists()) return null
            extensionsDir.listFiles { f -> f.isDirectory }?.forEach { extDir ->
                val direct = File(extDir, resourcePath)
                if (direct.exists()) return direct
                extDir.listFiles { f -> f.isDirectory }?.forEach { subDir ->
                    val nested = File(subDir, resourcePath)
                    if (nested.exists()) return nested
                }
            }
            return null
        }

        val BOOTSTRAP_JS = """
(function() {
  if (typeof window.AndroidBridge === 'undefined') return;
  var bridge = window.AndroidBridge;
  
  window.chrome = window.chrome || {};
  
  window.chrome.storage = {
    local: {
      get: function(keys, cb) {
        var k = Array.isArray(keys) ? JSON.stringify(keys) : JSON.stringify(Object.keys(keys || {}));
        var result = JSON.parse(bridge.storageLocalGet(k));
        if(cb) cb(result); return result;
      },
      set: function(items, cb) { bridge.storageLocalSet(JSON.stringify(items)); if(cb) cb(); },
      remove: function(keys, cb) {
        bridge.storageLocalRemove(JSON.stringify(Array.isArray(keys) ? keys : [keys]));
        if(cb) cb();
      },
      clear: function(cb) { bridge.storageLocalClear(); if(cb) cb(); }
    },
    sync: {
      get: function(keys, cb) {
        var k = Array.isArray(keys) ? JSON.stringify(keys) : JSON.stringify(Object.keys(keys || {}));
        var result = JSON.parse(bridge.storageSyncGet(k));
        if(cb) cb(result); return result;
      },
      set: function(items, cb) { bridge.storageSyncSet(JSON.stringify(items)); if(cb) cb(); }
    }
  };
  
  window.chrome.runtime = {
    id: bridge.runtimeGetId(),
    getManifest: function() { return JSON.parse(bridge.runtimeGetManifest()); },
    getURL: function(path) { return bridge.runtimeGetURL(path); },
    sendMessage: function(msg, cb) { var r = JSON.parse(bridge.runtimeSendMessage(JSON.stringify(msg))); if(cb) cb(r); },
    onMessage: { addListener: function(fn) { window._chromeOnMessageListeners = window._chromeOnMessageListeners || []; window._chromeOnMessageListeners.push(fn); } }
  };
  
  window.chrome.tabs = {
    getCurrent: function(cb) { var t = JSON.parse(bridge.tabsGetCurrent()); if(cb) cb(t); return t; },
    create: function(props, cb) { var t = JSON.parse(bridge.tabsCreate(props.url || '')); if(cb) cb(t); return t; },
    query: function(q, cb) { var t = [JSON.parse(bridge.tabsGetCurrent())]; if(cb) cb(t); return t; },
    update: function(id, props) {},
    sendMessage: function(id, msg, cb) { var r = JSON.parse(bridge.runtimeSendMessage(JSON.stringify(msg))); if(cb) cb(r); }
  };
  
  window.chrome.windows = { update: function(id, props) {} };
  
  window.chrome.action = {
    setTitle: function() {}, setBadgeText: function() {}, setBadgeBackgroundColor: function() {},
    onClicked: { addListener: function(fn) { window._chromeActionListener = fn; } }
  };
  
  window.chrome.scripting = { executeScript: function(details) { if(details.func) details.func(); } };
  
  window.chrome.cookies = { getAll: function(d,cb) { if(cb) cb([]); } };
  
  window.chrome.notifications = {
    create: function(id, opts, cb) { bridge.notificationsCreate(id||'', JSON.stringify(opts)); if(cb) cb(id||''); }
  };
  
  window.chrome.contextMenus = { create: function(){}, remove: function(){}, onClicked: { addListener: function(){} } };
  window.chrome.webNavigation = { onCompleted: { addListener: function(){} } };
  window.chrome.permissions = { contains: function(p,cb){ if(cb) cb(true); }, request: function(p,cb){ if(cb) cb(true); } };
  window.chrome.i18n = { getMessage: function(key) { return key; }, getUILanguage: function() { return navigator.language; } };
  window.chrome.alarms = { create: function(){}, clear: function(){}, onAlarm: { addListener: function(){} } };
  window.chrome.downloads = {
    download: function(opts, cb) {
      var a = document.createElement('a'); a.href = opts.url || ''; a.download = opts.filename || ''; a.click(); if(cb) cb(1);
    }
  };
  window.chrome.webRequest = {
    onBeforeRequest: { addListener: function(){} }, onHeadersReceived: { addListener: function(){} }
  };

  // Native tap — uses Android MotionEvent via WebView.onTouchEvent for real OS touch
  window.__nativeTap = function(x, y) { bridge.simulateNativeTap(x, y); };
  
  console.log('[BettingEngine] Chrome Extension APIs + native tap ready');
})();
        """.trimIndent()
    }
}
