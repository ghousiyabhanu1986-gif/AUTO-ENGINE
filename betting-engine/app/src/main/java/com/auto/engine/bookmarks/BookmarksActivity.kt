package com.auto.engine.bookmarks

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.auto.engine.R
import com.auto.engine.browser.BrowserActivity
import com.auto.engine.databinding.ActivityBookmarksBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class BookmarksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookmarksBinding
    private lateinit var manager: BookmarkManager
    private val items = mutableListOf<Bookmark>()
    private lateinit var adapter: BookmarksAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Bookmarks"

        manager = BookmarkManager(this)
        adapter = BookmarksAdapter(items,
            onClick = { bm ->
                startActivity(Intent(this, BrowserActivity::class.java).apply {
                    putExtra(BrowserActivity.EXTRA_URL, bm.url)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
                finish()
            },
            onDelete = { bm ->
                lifecycleScope.launch { manager.delete(bm); load() }
            }
        )
        binding.rvBookmarks.layoutManager = LinearLayoutManager(this)
        binding.rvBookmarks.adapter = adapter

        binding.etSearch.addTextChangedListener { text ->
            lifecycleScope.launch { load(text.toString()) }
        }

        lifecycleScope.launch { load() }
    }

    private suspend fun load(q: String = "") {
        val result = if (q.isBlank()) manager.getAll() else manager.search(q)
        items.clear(); items.addAll(result)
        adapter.notifyDataSetChanged()
        binding.tvEmpty.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

class BookmarksAdapter(
    private val items: List<Bookmark>,
    private val onClick: (Bookmark) -> Unit,
    private val onDelete: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarksAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvUrl)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark, parent, false)
    )

    override fun onBindViewHolder(h: VH, pos: Int) {
        val bm = items[pos]
        h.tvTitle.text = bm.title.ifBlank { bm.url }
        h.tvUrl.text = bm.url
        h.itemView.setOnClickListener { onClick(bm) }
        h.btnDelete.setOnClickListener { onDelete(bm) }
    }

    override fun getItemCount() = items.size
}
