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

    init { extensionsDir.mkdirs() }

    fun getAll(): List<Extension> {
        val dirs = extensionsDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir -> loadExtension(dir) }
    }

    private fun loadExtension(dir: File): Extension? {
        val manifestFile = File(dir, "manifest.json")
        if (!manifestFile.exists()) return null
        return try {
            val manifest = gson.fromJson(manifestFile.readText(), ExtensionManifest::class.java)
            val enabled = prefs.getBoolean("enabled_${dir.name}", true)
            Extension(id = dir.name, manifest = manifest, path = dir.absolutePath, enabled = enabled)
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
            // Extract all files
            zipFile.entries().asSequence().forEach { entry ->
                val file = File(extDir, entry.name)
                if (entry.isDirectory) file.mkdirs()
                else {
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
     * Inject all enabled extensions into the given WebView page.
     * Injects: Chrome API bridge bootstrap, then each extension's content scripts.
     */
    fun injectExtensions(webView: WebView) {
        // Always inject Chrome API bootstrap
        webView.evaluateJavascript(ChromeApisBridge.BOOTSTRAP_JS, null)

        val extensions = getAll().filter { it.enabled }
        for (ext in extensions) {
            val extDir = File(ext.path)
            for (cs in ext.manifest.contentScripts) {
                // Inject CSS
                cs.css.forEach { cssFile ->
                    val f = File(extDir, cssFile)
                    if (f.exists()) {
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
                    val f = File(extDir, jsFile)
                    if (f.exists()) {
                        val code = f.readText()
                        webView.evaluateJavascript(code, null)
                    }
                }
            }
        }
    }
}
