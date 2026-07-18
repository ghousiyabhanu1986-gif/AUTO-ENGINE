package com.auto.engine.history

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.Intent
import android.view.*
import android.widget.*
import com.auto.engine.R
import com.auto.engine.browser.BrowserActivity
import com.auto.engine.databinding.ActivityHistoryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var manager: HistoryManager
    private val entries = mutableListOf<HistoryEntry>()
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "History"

        manager = HistoryManager(this)

        adapter = HistoryAdapter(entries) { entry ->
            val i = Intent(this, BrowserActivity::class.java).apply {
                putExtra(BrowserActivity.EXTRA_URL, entry.url)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(i)
            finish()
        }

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter

        binding.etSearch.addTextChangedListener { text ->
            lifecycleScope.launch { loadEntries(text.toString()) }
        }

        binding.btnClearAll.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear History")
                .setMessage("Delete all browsing history?")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        manager.clearAll()
                        loadEntries()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        lifecycleScope.launch { loadEntries() }
    }

    private suspend fun loadEntries(query: String = "") {
        val result = if (query.isBlank()) manager.getAll() else manager.search(query)
        entries.clear()
        entries.addAll(result)
        adapter.notifyDataSetChanged()
        binding.tvEmpty.visibility = if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

class HistoryAdapter(
    private val items: List<HistoryEntry>,
    private val onClick: (HistoryEntry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvUrl)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title.ifBlank { item.url }
        holder.tvUrl.text = item.url
        holder.tvTime.text = android.text.format.DateUtils.getRelativeTimeSpanString(item.visitedAt)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
