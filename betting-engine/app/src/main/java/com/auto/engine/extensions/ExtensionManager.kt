package com.auto.engine.extensions

import android.content.Context
import android.webkit.WebView
import com.google.gson.Gson
import java.io.File
import java.util.zip.ZipFile

class ExtensionManager(private val context: Context) {

    private val gson = Gson()
    private val extensionsDir = File(context.filesDir, "extensions")
    private val prefs = context.getSharedPreferences("extensions_prefs", Context.MODE_PRIVATE)

    // Track which WebViews have been bootstrapped and which extensions are already injected.
    // Using identity hash set since WebView doesn't have a proper equals/hashcode.
    private val bootstrappedWebViews = mutableSetOf<Int>()
    // Per-WebView: set of extension IDs already injected
    private val injectedExtensions = mutableMapOf<Int, MutableSet<String>>()

    init { extensionsDir.mkdirs() }

    fun getAll(): List<Extension> {
        val dirs = extensionsDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir -> loadExtension(dir) }
    }

    private fun loadExtension(dir: File): Extension? {
        // Search for manifest.json in root or one-level subdirectories (for zips with wrapper dirs)
        var manifestFile = File(dir, "manifest.json")
        if (!manifestFile.exists()) {
            val subDirs = dir.listFiles { f -> f.isDirectory } ?: return null
            for (subDir in subDirs) {
                val candidate = File(subDir, "manifest.json")
                if (candidate.exists()) {
                    manifestFile = candidate
                    break
                }
            }
        }
        if (!manifestFile.exists()) return null
        return try {
            val manifest = gson.fromJson(manifestFile.readText(), ExtensionManifest::class.java)
            val enabled = prefs.getBoolean("enabled_${dir.name}", true)
            // Use the directory containing manifest.json as the effective path
            val effectivePath = manifestFile.parentFile?.absolutePath ?: dir.absolutePath
            Extension(id = dir.name, manifest = manifest, path = effectivePath, enabled = enabled)
        } catch (e: Exception) { null }
    }

    fun installFromZip(zipPath: String): Extension? {
        return try {
            val zipFile = ZipFile(zipPath)
            val manifestEntry = zipFile.getEntry("manifest.json")
                ?: zipFile.entries().asSequence().firstOrNull { it.name.endsWith("manifest.json") }
                ?: return null
            val manifest = gson.fromJson(
                zipFile.getInputStream(manifestEntry).bufferedReader().readText(),
                ExtensionManifest::class.java
            )
            val extId = "${manifest.name.replace(" ", "_")}_${System.currentTimeMillis()}"
            val extDir = File(extensionsDir, extId)
            extDir.mkdirs()

            // Determine if there's a wrapper directory (like "extension/") in the zip
            // If the manifest is nested (e.g. "extension/manifest.json"), strip the prefix
            val stripPrefix = if (manifestEntry.name != "manifest.json") {
                manifestEntry.name.substringBeforeLast("/") + "/"
            } else null

            // Extract all files, stripping the wrapper directory prefix if present
            zipFile.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) {
                    val cleanName = if (stripPrefix != null) {
                        entry.name.removePrefix(stripPrefix)
                    } else entry.name
                    if (cleanName.isNotEmpty()) File(extDir, cleanName).mkdirs()
                } else {
                    val cleanName = if (stripPrefix != null) {
                        entry.name.removePrefix(stripPrefix)
                    } else entry.name
                    val file = File(extDir, cleanName)
                    file.parentFile?.mkdirs()
                    file.outputStream().use { out -> zipFile.getInputStream(entry).copyTo(out) }
                }
            }
            zipFile.close()
            loadExtension(extDir)
        } catch (e: Exception) { null }
    }

    fun installUnpacked(dirPath: String): Extension? {
        val sourceDir = File(dirPath)
        if (!sourceDir.exists() || !sourceDir.isDirectory) return null
        val extId = "unpacked_${System.currentTimeMillis()}"
        val destDir = File(extensionsDir, extId)
        sourceDir.copyRecursively(destDir, overwrite = true)
        return loadExtension(destDir)
    }

    fun enableExtension(id: String) = prefs.edit().putBoolean("enabled_$id", true).apply()

    fun disableExtension(id: String) = prefs.edit().putBoolean("enabled_$id", false).apply()

    fun removeExtension(id: String) {
        File(extensionsDir, id).deleteRecursively()
        prefs.edit().remove("enabled_$id").apply()
    }

    /**
     * Find a file by path within an extension directory, searching
     * both the root and one level of subdirectories.
     */
    private fun findExtensionFile(extDir: File, fileName: String): File? {
        val direct = File(extDir, fileName)
        if (direct.exists()) return direct
        // Search one level of subdirectories
        extDir.listFiles { f -> f.isDirectory }?.forEach { subDir ->
            val candidate = File(subDir, fileName)
            if (candidate.exists()) return candidate
        }
        return null
    }

    /**
     * Inject all enabled extensions into the given WebView page.
     * Uses guards to avoid re-injecting the same extension or bootstrap
     * on repeated onPageFinished callbacks (common on SPA / auto-refresh sites).
     *
     * - BOOTSTRAP_JS is injected only once per WebView lifetime
     * - Each extension's content scripts are injected only once per WebView lifetime
     */
    fun injectExtensions(webView: WebView) {
        val webViewId = System.identityHashCode(webView)

        // Inject Chrome API bootstrap only once per WebView
        if (bootstrappedWebViews.add(webViewId)) {
            webView.evaluateJavascript(ChromeApisBridge.BOOTSTRAP_JS, null)
        }

        val extensions = getAll().filter { it.enabled }
        // Get or create the injected set for this WebView
        val injected = injectedExtensions.getOrPut(webViewId) { mutableSetOf() }

        for (ext in extensions) {
            // Skip extensions already injected into this WebView
            if (ext.id in injected) continue
            injected.add(ext.id)

            val extDir = File(ext.path)
            for (cs in ext.manifest.contentScripts) {
                // Inject CSS
                cs.css.forEach { cssFile ->
                    val f = findExtensionFile(extDir, cssFile)
                    if (f != null && f.exists()) {
                        val css = f.readText().replace("\\", "\\\\").replace("`", "\\`")
                        val js = """
                            (function(){
                                var s=document.createElement('style');
                                s.textContent=`$css`;
                                document.head.appendChild(s);
                            })();
                        """.trimIndent()
                        webView.evaluateJavascript(js, null)
                    }
                }
                // Inject JS
                cs.js.forEach { jsFile ->
                    val f = findExtensionFile(extDir, jsFile)
                    if (f != null && f.exists()) {
                        val code = f.readText()
                        webView.evaluateJavascript(code, null)
                    }
                }
            }
        }
    }

    /**
     * Reset injection state for a WebView. Call when a tab's WebView is being
     * destroyed or when navigating to a completely new domain where extensions
     * should be re-applied fresh.
     */
    fun resetInjectionState(webView: WebView) {
        val webViewId = System.identityHashCode(webView)
        bootstrappedWebViews.remove(webViewId)
        injectedExtensions.remove(webViewId)
    }

    /**
     * Force re-inject all extensions on next injectExtensions call.
     * Useful when extensions are enabled/disabled at runtime.
     */
    fun invalidateAllInjectionState() {
        bootstrappedWebViews.clear()
        injectedExtensions.clear()
    }
}
