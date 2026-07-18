package com.auto.engine.extensions

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.auto.engine.R
import com.auto.engine.databinding.ActivityExtensionsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ExtensionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExtensionsBinding
    private lateinit var manager: ExtensionManager
    private val extensions = mutableListOf<Extension>()
    private lateinit var adapter: ExtensionsAdapter

    private val zipPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        val tmpFile = java.io.File(cacheDir, "ext_import.zip")
        contentResolver.openInputStream(uri)?.use { input ->
            tmpFile.outputStream().use { output -> input.copyTo(output) }
        }
        val ext = manager.installFromZip(tmpFile.absolutePath)
        if (ext != null) {
            Toast.makeText(this, "Extension '${ext.manifest.name}' installed", Toast.LENGTH_SHORT).show()
            reload()
        } else {
            Toast.makeText(this, "Failed to install extension", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExtensionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Extensions"

        manager = ExtensionManager(this)

        adapter = ExtensionsAdapter(
            extensions,
            onToggle = { ext, enabled ->
                if (enabled) manager.enableExtension(ext.id) else manager.disableExtension(ext.id)
            },
            onRemove = { ext ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Remove Extension")
                    .setMessage("Remove '${ext.manifest.name}'?")
                    .setPositiveButton("Remove") { _, _ ->
                        manager.removeExtension(ext.id)
                        reload()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.rvExtensions.layoutManager = LinearLayoutManager(this)
        binding.rvExtensions.adapter = adapter

        binding.btnImportZip.setOnClickListener {
            zipPicker.launch("application/zip")
        }

        binding.btnImportCrx.setOnClickListener {
            Toast.makeText(this, "Select a .crx or .zip extension file", Toast.LENGTH_SHORT).show()
            zipPicker.launch("*/*")
        }

        reload()
    }

    private fun reload() {
        extensions.clear()
        extensions.addAll(manager.getAll())
        adapter.notifyDataSetChanged()
        binding.tvEmpty.visibility = if (extensions.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

class ExtensionsAdapter(
    private val items: List<Extension>,
    private val onToggle: (Extension, Boolean) -> Unit,
    private val onRemove: (Extension) -> Unit
) : RecyclerView.Adapter<ExtensionsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvDesc: TextView = view.findViewById(R.id.tvDesc)
        val tvVersion: TextView = view.findViewById(R.id.tvVersion)
        val swEnabled: Switch = view.findViewById(R.id.swEnabled)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_extension, parent, false)
    )

    override fun onBindViewHolder(h: VH, pos: Int) {
        val ext = items[pos]
        h.tvName.text = ext.manifest.name.ifBlank { "Unknown Extension" }
        h.tvDesc.text = ext.manifest.description.ifBlank { "No description" }
        h.tvVersion.text = "v${ext.manifest.version} · MV${ext.manifest.manifestVersion}"
        h.swEnabled.setOnCheckedChangeListener(null)
        h.swEnabled.isChecked = ext.enabled
        h.swEnabled.setOnCheckedChangeListener { _, checked -> onToggle(ext, checked) }
        h.btnRemove.setOnClickListener { onRemove(ext) }
    }

    override fun getItemCount() = items.size
}
