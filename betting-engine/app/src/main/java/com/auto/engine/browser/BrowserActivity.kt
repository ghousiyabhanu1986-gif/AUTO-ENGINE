package com.auto.engine.browser

import android.Manifest
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.auto.engine.R
import com.auto.engine.bookmarks.BookmarkManager
import com.auto.engine.bookmarks.BookmarksActivity
import com.auto.engine.databinding.ActivityBrowserBinding
import com.auto.engine.download.DownloadsActivity
import com.auto.engine.extensions.ChromeApisBridge
import com.auto.engine.extensions.ExtensionManager
import com.auto.engine.extensions.ExtensionsActivity
import com.auto.engine.history.HistoryActivity
import com.auto.engine.history.HistoryManager
import com.auto.engine.kiosk.KioskActivity
import com.auto.engine.settings.SettingsActivity
import com.auto.engine.tab.Tab
import com.auto.engine.tab.TabManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private lateinit var tabManager: TabManager
    private lateinit var historyManager: HistoryManager
    private lateinit var bookmarkManager: BookmarkManager
    private lateinit var extensionManager: ExtensionManager

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var isFullscreen = false
    private var isDarkMode = false
    private var isDesktopMode = false
    private var currentZoom = 100

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val HOME_PAGE = "https://www.google.com"
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tabManager = TabManager(this)
        historyManager = HistoryManager(this)
        bookmarkManager = BookmarkManager(this)
        extensionManager = ExtensionManager(this)

        setupTabManager()
        setupAddressBar()
        setupNavigationButtons()
        setupMenuButton()
        setupTabsButton()

        val startUrl = intent.getStringExtra(EXTRA_URL) ?: HOME_PAGE
        tabManager.openNewTab(startUrl)

        requestPermissionsIfNeeded()
    }

    // ─── WebView Setup ────────────────────────────────────────────────────────

    private fun attachWebView(tab: Tab) {
        val webView = tab.webView ?: return
        binding.webViewContainer.removeAllViews()
        (webView.parent as? ViewGroup)?.removeView(webView)
        binding.webViewContainer.addView(
            webView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        applyWebViewSettings(webView, tab.isIncognito)
        webView.webViewClient = BrowserWebViewClient(
            context = this,
            onPageStarted = { url ->
                updateAddressBar(url)
                binding.progressBar.isVisible = true
                tab.isLoading = true
                tab.url = url
                updateNavButtons(webView)
            },
            onPageFinished = { url, title ->
                binding.progressBar.isVisible = false
                tab.isLoading = false
                tab.url = url
                tab.title = title
                updateNavButtons(webView)
                updateAddressBar(url)
                if (!tab.isIncognito) {
                    lifecycleScope.launch { historyManager.addEntry(title, url) }
                }
                extensionManager.injectExtensions(webView)
            },
            onReceivedError = { _ -> binding.progressBar.isVisible = false }
        )
        webView.webChromeClient = BrowserWebChromeClient(
            context = this,
            onProgressChanged = { progress ->
                binding.progressBar.progress = progress
                tab.progress = progress
            },
            onTitleReceived = { title -> tab.title = title; updateTabStrip() },
            onFaviconReceived = { bmp -> tab.favicon = bmp; updateTabStrip() },
            onPermissionRequest = { req ->
                req.grant(req.resources)
            },
            onFileChooser = { callback, _ ->
                filePathCallback = callback
                fileChooserLauncher.launch("*/*")
                true
            },
            onShowCustomView = { view, callback ->
                enterFullscreen(view, callback)
            },
            onHideCustomView = { exitFullscreen() }
        )
        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            startDownload(url, contentDisposition, mimeType)
        }
        updateNavButtons(webView)
        updateAddressBar(tab.url)
    }

    private fun applyWebViewSettings(webView: WebView, isIncognito: Boolean) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            loadsImagesAutomatically = true
            setGeolocationEnabled(true)
            cacheMode = if (isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            userAgentString = if (isDesktopMode) DESKTOP_UA else DEFAULT_UA
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        // Inject Chrome Extension APIs bridge
        val bridge = ChromeApisBridge(this, isIncognito)
        webView.addJavascriptInterface(bridge, "AndroidBridge")

        if (isIncognito) {
            CookieManager.getInstance().setAcceptCookie(false)
        } else {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }
    }

    // ─── Tab Management ───────────────────────────────────────────────────────

    private fun setupTabManager() {
        tabManager.onTabsChanged = { _ -> updateTabStrip() }
        tabManager.onCurrentTabChanged = { tab ->
            tab?.let { attachWebView(it) }
            updateTabStrip()
        }
    }

    private fun updateTabStrip() {
        val count = tabManager.tabs.size
        binding.tabsCountBadge.text = count.toString()
    }

    // ─── Address Bar ──────────────────────────────────────────────────────────

    private fun setupAddressBar() {
        binding.addressBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigateTo(binding.addressBar.text.toString().trim())
                true
            } else false
        }
        binding.addressBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) binding.addressBar.selectAll()
        }
        binding.btnReload.setOnClickListener {
            val webView = tabManager.currentTab?.webView ?: return@setOnClickListener
            if (tabManager.currentTab?.isLoading == true) webView.stopLoading()
            else webView.reload()
        }
    }

    private fun navigateTo(input: String) {
        val url = buildUrl(input)
        tabManager.currentTab?.webView?.loadUrl(url)
        binding.addressBar.clearFocus()
    }

    private fun buildUrl(input: String): String {
        return when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.google.com/search?q=${Uri.encode(input)}"
        }
    }

    private fun updateAddressBar(url: String) {
        if (!binding.addressBar.hasFocus()) {
            binding.addressBar.setText(if (url == "about:blank") "" else url)
        }
        val isSecure = url.startsWith("https://")
        binding.sslIcon.setImageResource(
            if (isSecure) R.drawable.ic_lock else R.drawable.ic_lock_open
        )
    }

    // ─── Navigation Buttons ───────────────────────────────────────────────────

    private fun setupNavigationButtons() {
        binding.btnBack.setOnClickListener {
            tabManager.currentTab?.webView?.let { if (it.canGoBack()) it.goBack() }
        }
        binding.btnBack.setOnLongClickListener {
            showNavigationHistory(forward = false)
            true
        }
        binding.btnForward.setOnClickListener {
            tabManager.currentTab?.webView?.let { if (it.canGoForward()) it.goForward() }
        }
        binding.btnForward.setOnLongClickListener {
            showNavigationHistory(forward = true)
            true
        }
        binding.btnHome.setOnClickListener {
            tabManager.currentTab?.webView?.loadUrl(HOME_PAGE)
        }
    }

    private fun updateNavButtons(webView: WebView) {
        binding.btnBack.isEnabled = webView.canGoBack()
        binding.btnForward.isEnabled = webView.canGoForward()
        binding.btnReload.setImageResource(
            if (tabManager.currentTab?.isLoading == true) R.drawable.ic_stop else R.drawable.ic_refresh
        )
    }

    private fun showNavigationHistory(forward: Boolean) {
        val webView = tabManager.currentTab?.webView ?: return
        val list = webView.copyBackForwardList()
        val items = mutableListOf<Pair<String, String>>()
        val currentIndex = list.currentIndex
        if (forward) {
            for (i in currentIndex + 1 until list.size) {
                val item = list.getItemAtIndex(i)
                items.add(Pair(item.title ?: item.url, item.url))
            }
        } else {
            for (i in currentIndex - 1 downTo 0) {
                val item = list.getItemAtIndex(i)
                items.add(Pair(item.title ?: item.url, item.url))
            }
        }
        if (items.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(if (forward) "Forward" else "Back")
            .setItems(items.map { it.first }.toTypedArray()) { _, idx ->
                val steps = if (forward) idx + 1 else -(idx + 1)
                webView.goBackOrForward(steps)
            }
            .show()
    }

    // ─── Tabs Sheet ───────────────────────────────────────────────────────────

    private fun setupTabsButton() {
        binding.btnTabs.setOnClickListener { showTabsSheet() }
    }

    private fun showTabsSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_tabs, null)
        val rv = view.findViewById<RecyclerView>(R.id.rvTabs)
        val btnNewTab = view.findViewById<Button>(R.id.btnNewTab)
        val btnNewIncognito = view.findViewById<Button>(R.id.btnNewIncognito)

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = TabsAdapter(tabManager.tabs, tabManager.currentTabIndex) { action, tab ->
            when (action) {
                "switch" -> { tabManager.switchToTab(tab.id); sheet.dismiss() }
                "close" -> tabManager.closeTab(tab.id)
            }
        }

        btnNewTab.setOnClickListener {
            tabManager.openNewTab(HOME_PAGE, false)
            sheet.dismiss()
        }
        btnNewIncognito.setOnClickListener {
            tabManager.openNewTab(HOME_PAGE, true)
            sheet.dismiss()
        }
        sheet.setContentView(view)
        sheet.show()
    }

    // ─── Menu ─────────────────────────────────────────────────────────────────

    private fun setupMenuButton() {
        binding.btnMenu.setOnClickListener { showMenu() }
    }

    private fun showMenu() {
        val popup = PopupMenu(this, binding.btnMenu)
        popup.menu.apply {
            add(0, 1, 0, "Bookmarks")
            add(0, 2, 0, "History")
            add(0, 3, 0, "Downloads")
            add(0, 4, 0, "Extensions")
            add(0, 5, 0, "Settings")
            add(0, 6, 0, "Find in Page")
            add(0, 7, 0, "Reader Mode")
            add(0, 8, 0, if (isDarkMode) "Light Mode" else "Dark Mode")
            add(0, 9, 0, if (isDesktopMode) "Mobile Mode" else "Desktop Mode")
            add(0, 10, 0, "Add Bookmark")
            add(0, 11, 0, "Share Page")
            add(0, 12, 0, "Zoom")
            add(0, 13, 0, "Print / PDF")
            add(0, 14, 0, "Kiosk Mode")
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> startActivity(Intent(this, BookmarksActivity::class.java))
                2 -> startActivity(Intent(this, HistoryActivity::class.java))
                3 -> startActivity(Intent(this, DownloadsActivity::class.java))
                4 -> startActivity(Intent(this, ExtensionsActivity::class.java))
                5 -> startActivity(Intent(this, SettingsActivity::class.java))
                6 -> showFindInPage()
                7 -> toggleReaderMode()
                8 -> toggleDarkMode()
                9 -> toggleDesktopMode()
                10 -> addBookmark()
                11 -> sharePage()
                12 -> showZoomDialog()
                13 -> printPage()
                14 -> startActivity(Intent(this, KioskActivity::class.java))
            }
            true
        }
        popup.show()
    }

    // ─── Features ─────────────────────────────────────────────────────────────

    private fun showFindInPage() {
        binding.findInPageLayout.isVisible = true
        binding.etFindInPage.requestFocus()
        binding.btnFindNext.setOnClickListener {
            val q = binding.etFindInPage.text.toString()
            tabManager.currentTab?.webView?.findNext(true)
            tabManager.currentTab?.webView?.findAllAsync(q)
        }
        binding.btnFindPrev.setOnClickListener {
            tabManager.currentTab?.webView?.findNext(false)
        }
        binding.btnFindClose.setOnClickListener {
            tabManager.currentTab?.webView?.clearMatches()
            binding.findInPageLayout.isVisible = false
        }
        binding.etFindInPage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tabManager.currentTab?.webView?.findAllAsync(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun toggleReaderMode() {
        val js = """
            (function() {
                var body = document.body;
                var article = document.querySelector('article') || body;
                var text = article.innerText;
                document.body.innerHTML = '<div style="max-width:700px;margin:40px auto;font-family:Georgia,serif;font-size:18px;line-height:1.8;color:#222;padding:20px">' + text.replace(/\n/g,'<br>') + '</div>';
            })();
        """.trimIndent()
        tabManager.currentTab?.webView?.evaluateJavascript(js, null)
    }

    private fun toggleDarkMode() {
        isDarkMode = !isDarkMode
        val js = if (isDarkMode) {
            "document.documentElement.style.filter='invert(1) hue-rotate(180deg)';"
        } else {
            "document.documentElement.style.filter='';"
        }
        tabManager.currentTab?.webView?.evaluateJavascript(js, null)
    }

    private fun toggleDesktopMode() {
        isDesktopMode = !isDesktopMode
        val webView = tabManager.currentTab?.webView ?: return
        webView.settings.userAgentString = if (isDesktopMode) DESKTOP_UA else DEFAULT_UA
        webView.reload()
    }

    private fun addBookmark() {
        val tab = tabManager.currentTab ?: return
        lifecycleScope.launch {
            bookmarkManager.addBookmark(tab.title, tab.url)
            Toast.makeText(this@BrowserActivity, "Bookmark saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePage() {
        val tab = tabManager.currentTab ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, tab.url)
            putExtra(Intent.EXTRA_SUBJECT, tab.title)
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun showZoomDialog() {
        val levels = arrayOf("50%", "75%", "100%", "125%", "150%", "200%")
        val values = intArrayOf(50, 75, 100, 125, 150, 200)
        AlertDialog.Builder(this)
            .setTitle("Zoom Level")
            .setItems(levels) { _, idx ->
                currentZoom = values[idx]
                tabManager.currentTab?.webView?.setInitialScale(currentZoom)
                tabManager.currentTab?.webView?.settings?.textZoom = currentZoom
            }
            .show()
    }

    private fun printPage() {
        val webView = tabManager.currentTab?.webView ?: return
        val printManager = getSystemService(PRINT_SERVICE) as android.print.PrintManager
        val printAdapter = webView.createPrintDocumentAdapter(tabManager.currentTab?.title ?: "Page")
        printManager.print("BettingEngine_Print", printAdapter, android.print.PrintAttributes.Builder().build())
    }

    // ─── Downloads ────────────────────────────────────────────────────────────

    private fun startDownload(url: String, contentDisposition: String, mimeType: String) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mimeType)
            addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
            setDescription("Downloading…")
            setTitle(fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(this, "Downloading $fileName", Toast.LENGTH_SHORT).show()
    }

    // ─── Fullscreen ───────────────────────────────────────────────────────────

    private fun enterFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        fullscreenView = view
        fullscreenCallback = callback
        isFullscreen = true
        binding.fullscreenContainer.addView(view)
        binding.fullscreenContainer.isVisible = true
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun exitFullscreen() {
        fullscreenView?.let { binding.fullscreenContainer.removeView(it) }
        binding.fullscreenContainer.isVisible = false
        fullscreenCallback?.onCustomViewHidden()
        fullscreenView = null
        fullscreenCallback = null
        isFullscreen = false
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    // ─── Permissions ─────────────────────────────────────────────────────────

    private fun requestPermissionsIfNeeded() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
    }

    // ─── Back handling ────────────────────────────────────────────────────────

    override fun onBackPressed() {
        when {
            isFullscreen -> exitFullscreen()
            binding.findInPageLayout.isVisible -> {
                binding.findInPageLayout.isVisible = false
                tabManager.currentTab?.webView?.clearMatches()
            }
            tabManager.currentTab?.webView?.canGoBack() == true ->
                tabManager.currentTab?.webView?.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tabManager.destroy()
    }
}
