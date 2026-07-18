package com.auto.engine.settings

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.auto.engine.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs by lazy { getSharedPreferences("browser_settings", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        binding.apply {
            swJavaScript.isChecked = prefs.getBoolean("js_enabled", true)
            swCookies.isChecked = prefs.getBoolean("cookies_enabled", true)
            swImages.isChecked = prefs.getBoolean("images_enabled", true)
            swPopups.isChecked = prefs.getBoolean("popups_enabled", false)
            swDoNotTrack.isChecked = prefs.getBoolean("do_not_track", false)
            swSafeBrowsing.isChecked = prefs.getBoolean("safe_browsing", true)
            swSavePasswords.isChecked = prefs.getBoolean("save_passwords", false)
            swAutofill.isChecked = prefs.getBoolean("autofill", true)
            swHardwareAcceleration.isChecked = prefs.getBoolean("hw_accel", true)
            swServiceWorkers.isChecked = prefs.getBoolean("service_workers", true)
            swWebRtc.isChecked = prefs.getBoolean("webrtc", true)
            swLocation.isChecked = prefs.getBoolean("location", false)
            swCamera.isChecked = prefs.getBoolean("camera", false)
            swMicrophone.isChecked = prefs.getBoolean("microphone", false)
            swNotifications.isChecked = prefs.getBoolean("notifications", true)
            swDesktopMode.isChecked = prefs.getBoolean("desktop_mode", false)
            swDarkMode.isChecked = prefs.getBoolean("dark_mode", false)
            swOpenLinksInNewTab.isChecked = prefs.getBoolean("links_new_tab", false)
            tvHomePage.text = prefs.getString("home_page", "https://www.google.com")
            tvSearchEngine.text = prefs.getString("search_engine", "Google")
            tvDownloadLocation.text = prefs.getString("download_location", "Downloads")
        }
    }

    private fun setupListeners() {
        binding.apply {
            swJavaScript.setOnCheckedChangeListener { _, v -> save("js_enabled", v) }
            swCookies.setOnCheckedChangeListener { _, v -> save("cookies_enabled", v); CookieManager.getInstance().setAcceptCookie(v) }
            swImages.setOnCheckedChangeListener { _, v -> save("images_enabled", v) }
            swPopups.setOnCheckedChangeListener { _, v -> save("popups_enabled", v) }
            swDoNotTrack.setOnCheckedChangeListener { _, v -> save("do_not_track", v) }
            swSafeBrowsing.setOnCheckedChangeListener { _, v -> save("safe_browsing", v) }
            swSavePasswords.setOnCheckedChangeListener { _, v -> save("save_passwords", v) }
            swAutofill.setOnCheckedChangeListener { _, v -> save("autofill", v) }
            swHardwareAcceleration.setOnCheckedChangeListener { _, v -> save("hw_accel", v) }
            swServiceWorkers.setOnCheckedChangeListener { _, v -> save("service_workers", v) }
            swWebRtc.setOnCheckedChangeListener { _, v -> save("webrtc", v) }
            swLocation.setOnCheckedChangeListener { _, v -> save("location", v) }
            swCamera.setOnCheckedChangeListener { _, v -> save("camera", v) }
            swMicrophone.setOnCheckedChangeListener { _, v -> save("microphone", v) }
            swNotifications.setOnCheckedChangeListener { _, v -> save("notifications", v) }
            swDesktopMode.setOnCheckedChangeListener { _, v -> save("desktop_mode", v) }
            swDarkMode.setOnCheckedChangeListener { _, v -> save("dark_mode", v) }
            swOpenLinksInNewTab.setOnCheckedChangeListener { _, v -> save("links_new_tab", v) }

            rowHomePage.setOnClickListener { showHomePageDialog() }
            rowSearchEngine.setOnClickListener { showSearchEngineDialog() }
            rowClearBrowsingData.setOnClickListener { showClearDataDialog() }
            rowClearCache.setOnClickListener { clearCache() }
            rowClearCookies.setOnClickListener { clearCookies() }
        }
    }

    private fun save(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    private fun showHomePageDialog() {
        val et = android.widget.EditText(this).apply { setText(prefs.getString("home_page", "https://www.google.com")) }
        MaterialAlertDialogBuilder(this)
            .setTitle("Set Home Page")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                val url = et.text.toString().trim()
                prefs.edit().putString("home_page", url).apply()
                binding.tvHomePage.text = url
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSearchEngineDialog() {
        val engines = arrayOf("Google", "Bing", "DuckDuckGo", "Yahoo", "Brave", "Ecosia")
        MaterialAlertDialogBuilder(this)
            .setTitle("Search Engine")
            .setItems(engines) { _, idx ->
                prefs.edit().putString("search_engine", engines[idx]).apply()
                binding.tvSearchEngine.text = engines[idx]
            }
            .show()
    }

    private fun showClearDataDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear Browsing Data")
            .setMessage("This will clear history, cookies, and cached data.")
            .setPositiveButton("Clear All") { _, _ ->
                clearCache()
                clearCookies()
                Toast.makeText(this, "Browsing data cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearCache() {
        WebView(this).clearCache(true)
        WebStorage.getInstance().deleteAllData()
        Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
    }

    private fun clearCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        Toast.makeText(this, "Cookies cleared", Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
