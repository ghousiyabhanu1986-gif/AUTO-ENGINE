package com.auto.engine.extensions

import com.google.gson.annotations.SerializedName

data class ExtensionManifest(
    val name: String = "",
    val version: String = "1.0",
    val description: String = "",
    @SerializedName("manifest_version") val manifestVersion: Int = 3,
    val permissions: List<String> = emptyList(),
    @SerializedName("host_permissions") val hostPermissions: List<String> = emptyList(),
    @SerializedName("content_scripts") val contentScripts: List<ContentScript> = emptyList(),
    val background: BackgroundService? = null,
    val action: ActionDefinition? = null,
    val icons: Map<String, String> = emptyMap()
)

data class ContentScript(
    val matches: List<String> = listOf("<all_urls>"),
    val js: List<String> = emptyList(),
    val css: List<String> = emptyList(),
    @SerializedName("run_at") val runAt: String = "document_end",
    @SerializedName("all_frames") val allFrames: Boolean = false
)

data class BackgroundService(
    @SerializedName("service_worker") val serviceWorker: String? = null,
    val scripts: List<String>? = null,
    val persistent: Boolean = false
)

data class ActionDefinition(
    @SerializedName("default_title") val defaultTitle: String = "",
    @SerializedName("default_popup") val defaultPopup: String = "",
    @SerializedName("default_icon") val defaultIcon: Map<String, String> = emptyMap()
)

data class Extension(
    val id: String,
    val manifest: ExtensionManifest,
    val path: String,          // directory on device
    val enabled: Boolean = true,
    val installedAt: Long = System.currentTimeMillis()
)
