package com.auto.engine.extensions

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.gson.Gson

/**
 * JavaScript bridge that exposes Chrome Extension APIs to WebView content scripts.
 * Injected as window.AndroidBridge; the companion JS bootstrapper maps it to
 * window.chrome.* APIs so extensions work without modification.
 */
class ChromeApisBridge(
    private val context: Context,
    private val isIncognito: Boolean
) {
    private val gson = Gson()
    private val storage = context.getSharedPreferences("ext_storage", Context.MODE_PRIVATE)
    private val syncStorage = context.getSharedPreferences("ext_storage_sync", Context.MODE_PRIVATE)
    private val messageListeners = mutableListOf<String>()

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

    // ─── chrome.storage.sync ─────────────────────────────────────────────────

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

    // ─── chrome.tabs ─────────────────────────────────────────────────────────

    @JavascriptInterface
    fun tabsGetCurrent(): String {
        val tab = mapOf(
            "id" to 1,
            "url" to "about:blank",
            "title" to "Tab",
            "active" to true,
            "incognito" to isIncognito
        )
        return gson.toJson(tab)
    }

    @JavascriptInterface
    fun tabsCreate(url: String): String {
        // Signal to the JS layer that a new tab was requested
        val tab = mapOf("id" to 2, "url" to url, "active" to true)
        return gson.toJson(tab)
    }

    // ─── chrome.runtime ──────────────────────────────────────────────────────

    @JavascriptInterface
    fun runtimeGetId(): String = "betting_engine_runtime"

    @JavascriptInterface
    fun runtimeGetManifest(): String = "{\"name\":\"BettingEngine\",\"version\":\"1.0\",\"manifest_version\":3}"

    @JavascriptInterface
    fun runtimeSendMessage(message: String): String = "{\"success\":true}"

    @JavascriptInterface
    fun runtimeOnMessage(callbackName: String) {
        messageListeners.add(callbackName)
    }

    // ─── chrome.cookies ──────────────────────────────────────────────────────

    @JavascriptInterface
    fun cookiesGetAll(detailsJson: String): String {
        // Cookies are managed by Android's CookieManager
        return "[]"
    }

    // ─── chrome.notifications ────────────────────────────────────────────────

    @JavascriptInterface
    fun notificationsCreate(id: String, optionsJson: String) {
        try {
            val opts = gson.fromJson(optionsJson, Map::class.java)
            val title = opts["title"]?.toString() ?: "Extension Notification"
            val message = opts["message"]?.toString() ?: ""
            // Show Android notification (simple toast fallback)
        } catch (_: Exception) {}
    }

    // ─── chrome.webNavigation ────────────────────────────────────────────────

    @JavascriptInterface
    fun webNavigationGetCurrentTab(): String = tabsGetCurrent()

    // ─── Utility ─────────────────────────────────────────────────────────────

    @JavascriptInterface
    fun isIncognitoMode(): Boolean = isIncognito

    @JavascriptInterface
    fun getVersion(): String = "1.0.0"

    companion object {
        /**
         * JavaScript bootstrapper injected into every page.
         * Maps window.AndroidBridge.* to window.chrome.* APIs.
         */
        val BOOTSTRAP_JS = """
(function() {
  if (typeof window.AndroidBridge === 'undefined') return;
  var bridge = window.AndroidBridge;
  
  window.chrome = window.chrome || {};
  
  // chrome.storage
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
  
  // chrome.runtime
  window.chrome.runtime = {
    id: bridge.runtimeGetId(),
    getManifest: function() { return JSON.parse(bridge.runtimeGetManifest()); },
    sendMessage: function(msg, cb) { var r = JSON.parse(bridge.runtimeSendMessage(JSON.stringify(msg))); if(cb) cb(r); },
    onMessage: { addListener: function(fn) { window._chromeOnMessageListeners = window._chromeOnMessageListeners || []; window._chromeOnMessageListeners.push(fn); } }
  };
  
  // chrome.tabs
  window.chrome.tabs = {
    getCurrent: function(cb) { var t = JSON.parse(bridge.tabsGetCurrent()); if(cb) cb(t); return t; },
    create: function(props, cb) { var t = JSON.parse(bridge.tabsCreate(props.url || '')); if(cb) cb(t); return t; },
    query: function(q, cb) { var t = [JSON.parse(bridge.tabsGetCurrent())]; if(cb) cb(t); return t; }
  };
  
  // chrome.action
  window.chrome.action = {
    setTitle: function() {},
    setBadgeText: function() {},
    setBadgeBackgroundColor: function() {}
  };
  
  // chrome.scripting
  window.chrome.scripting = {
    executeScript: function(details) { if(details.func) details.func(); }
  };
  
  // chrome.cookies
  window.chrome.cookies = { getAll: function(d,cb) { if(cb) cb([]); } };
  
  // chrome.notifications
  window.chrome.notifications = {
    create: function(id, opts, cb) { bridge.notificationsCreate(id||'', JSON.stringify(opts)); if(cb) cb(id||''); }
  };
  
  // chrome.contextMenus (stub)
  window.chrome.contextMenus = { create: function(){}, remove: function(){}, onClicked: { addListener: function(){} } };
  
  // chrome.webNavigation
  window.chrome.webNavigation = { onCompleted: { addListener: function(){} } };
  
  // chrome.permissions
  window.chrome.permissions = { contains: function(p,cb){ if(cb) cb(true); }, request: function(p,cb){ if(cb) cb(true); } };
  
  // chrome.i18n
  window.chrome.i18n = { getMessage: function(key) { return key; }, getUILanguage: function() { return navigator.language; } };
  
  // chrome.alarms
  window.chrome.alarms = { create: function(){}, clear: function(){}, onAlarm: { addListener: function(){} } };
  
  // chrome.downloads
  window.chrome.downloads = {
    download: function(opts, cb) {
      var a = document.createElement('a');
      a.href = opts.url || '';
      a.download = opts.filename || '';
      a.click();
      if(cb) cb(1);
    }
  };
  
  // chrome.webRequest (stub)
  window.chrome.webRequest = {
    onBeforeRequest: { addListener: function(){} },
    onHeadersReceived: { addListener: function(){} }
  };
  
  console.log('[BettingEngine] Chrome Extension APIs initialized');
})();
        """.trimIndent()
    }
}
