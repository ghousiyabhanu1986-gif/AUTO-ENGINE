package com.auto.engine.kiosk

import android.os.Bundle
import android.view.*
import android.webkit.*
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.auto.engine.R
import com.auto.engine.browser.BrowserWebViewClient
import com.auto.engine.databinding.ActivityKioskBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Kiosk Mode – single-URL, no address bar, no tabs, auto-reload on crash.
 * Full-screen, blocks navigation outside the locked domain.
 */
class KioskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKioskBinding
    private var kioskUrl: String = "https://www.google.com"
    private var lockedDomain: String = ""

    companion object {
        const val EXTRA_KIOSK_URL = "kiosk_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full-screen immersive
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        hideSystemUI()

        binding = ActivityKioskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        kioskUrl = intent.getStringExtra(EXTRA_KIOSK_URL) ?: showUrlDialog().let { kioskUrl }
        lockedDomain = extractDomain(kioskUrl)

        setupWebView()
        binding.webViewKiosk.loadUrl(kioskUrl)

        // Exit kiosk via long-press menu button (admin)
        binding.btnExitKiosk.setOnLongClickListener {
            showExitDialog()
            true
        }
    }

    private fun setupWebView() {
        val wv = binding.webViewKiosk
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val domain = extractDomain(url)
                // Block external sites
                if (lockedDomain.isNotEmpty() && !domain.contains(lockedDomain) && !lockedDomain.contains(domain)) {
                    return true // Block
                }
                return false
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    // Auto-reload after 3 seconds
                    view.postDelayed({ view.loadUrl(kioskUrl) }, 3000)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                binding.loadingIndicator.visibility = View.GONE
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                binding.loadingIndicator.visibility = View.VISIBLE
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.kioskProgress.progress = newProgress
                binding.kioskProgress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showUrlDialog() {
        val editText = EditText(this).apply { hint = "Enter kiosk URL"; setText(kioskUrl) }
        MaterialAlertDialogBuilder(this)
            .setTitle("Set Kiosk URL")
            .setView(editText)
            .setPositiveButton("Start") { _, _ ->
                val url = editText.text.toString().trim()
                kioskUrl = if (url.startsWith("http")) url else "https://$url"
                lockedDomain = extractDomain(kioskUrl)
                binding.webViewKiosk.loadUrl(kioskUrl)
            }
            .setCancelable(false)
            .show()
    }

    private fun showExitDialog() {
        val editText = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD; hint = "Admin password" }
        MaterialAlertDialogBuilder(this)
            .setTitle("Exit Kiosk Mode")
            .setView(editText)
            .setPositiveButton("Exit") { _, _ ->
                // In a real app, verify password
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun extractDomain(url: String): String {
        return try {
            val u = java.net.URL(url)
            u.host.removePrefix("www.")
        } catch (e: Exception) { url }
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    override fun onBackPressed() {
        // Block back navigation in kiosk mode
        if (binding.webViewKiosk.canGoBack()) binding.webViewKiosk.goBack()
        // else: silently block
    }

    override fun onDestroy() {
        binding.webViewKiosk.destroy()
        super.onDestroy()
    }
}
