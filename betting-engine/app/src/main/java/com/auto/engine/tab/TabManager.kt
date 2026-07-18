package com.auto.engine.tab

import android.content.Context
import android.webkit.WebView

class TabManager(private val context: Context) {

    private val _tabs = mutableListOf<Tab>()
    val tabs: List<Tab> get() = _tabs.toList()

    private var _currentTabIndex = -1
    val currentTab: Tab? get() = if (_currentTabIndex >= 0 && _currentTabIndex < _tabs.size) _tabs[_currentTabIndex] else null
    val currentTabIndex: Int get() = _currentTabIndex

    var onTabsChanged: ((List<Tab>) -> Unit)? = null
    var onCurrentTabChanged: ((Tab?) -> Unit)? = null

    fun openNewTab(url: String = "about:blank", isIncognito: Boolean = false): Tab {
        val webView = createWebView(isIncognito)
        val tab = Tab(url = url, isIncognito = isIncognito, webView = webView)
        _tabs.add(tab)
        _currentTabIndex = _tabs.size - 1
        if (url != "about:blank") {
            webView.loadUrl(url)
        }
        notifyTabsChanged()
        notifyCurrentTabChanged()
        return tab
    }

    fun closeTab(tabId: String) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val tab = _tabs[index]
        tab.webView?.destroy()
        _tabs.removeAt(index)
        if (_tabs.isEmpty()) {
            _currentTabIndex = -1
        } else {
            _currentTabIndex = maxOf(0, minOf(_currentTabIndex, _tabs.size - 1))
        }
        notifyTabsChanged()
        notifyCurrentTabChanged()
    }

    fun switchToTab(tabId: String) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index >= 0) {
            _currentTabIndex = index
            notifyCurrentTabChanged()
        }
    }

    fun switchToIndex(index: Int) {
        if (index >= 0 && index < _tabs.size) {
            _currentTabIndex = index
            notifyCurrentTabChanged()
        }
    }

    fun closeAllTabs() {
        _tabs.forEach { it.webView?.destroy() }
        _tabs.clear()
        _currentTabIndex = -1
        notifyTabsChanged()
        notifyCurrentTabChanged()
    }

    fun closeAllIncognitoTabs() {
        val toRemove = _tabs.filter { it.isIncognito }
        toRemove.forEach { it.webView?.destroy() }
        _tabs.removeAll(toRemove.toSet())
        _currentTabIndex = _tabs.indices.firstOrNull() ?: -1
        notifyTabsChanged()
        notifyCurrentTabChanged()
    }

    fun updateTab(tabId: String, updater: Tab.() -> Unit) {
        val tab = _tabs.firstOrNull { it.id == tabId } ?: return
        tab.updater()
        notifyTabsChanged()
        if (tab.id == currentTab?.id) notifyCurrentTabChanged()
    }

    private fun createWebView(isIncognito: Boolean): WebView {
        val webView = WebView(context)
        return webView
    }

    private fun notifyTabsChanged() = onTabsChanged?.invoke(tabs)
    private fun notifyCurrentTabChanged() = onCurrentTabChanged?.invoke(currentTab)

    fun destroy() {
        _tabs.forEach { it.webView?.destroy() }
        _tabs.clear()
    }
}
