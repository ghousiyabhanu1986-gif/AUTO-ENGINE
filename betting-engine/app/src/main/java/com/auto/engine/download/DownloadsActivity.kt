package com.auto.engine.download

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.auto.engine.R
import com.auto.engine.databinding.ActivityDownloadsBinding

data class DownloadItem(
    val id: Long,
    val title: String,
    val description: String,
    val status: Int,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val localUri: String?,
    val mediaType: String?
)

class DownloadsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadsBinding
    private val items = mutableListOf<DownloadItem>()
    private lateinit var adapter: DownloadsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Downloads"

        adapter = DownloadsAdapter(items) { item ->
            pauseOrResumeDownload(item)
        }
        binding.rvDownloads.layoutManager = LinearLayoutManager(this)
        binding.rvDownloads.adapter = adapter

        loadDownloads()
    }

    private fun loadDownloads() {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_RUNNING or
            DownloadManager.STATUS_PAUSED or
            DownloadManager.STATUS_PENDING or
            DownloadManager.STATUS_SUCCESSFUL or
            DownloadManager.STATUS_FAILED
        )
        val cursor: Cursor = dm.query(query)
        items.clear()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
            val title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Unknown"
            val desc = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION)) ?: ""
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            val mediaType = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE))
            items.add(DownloadItem(id, title, desc, status, downloaded, total, localUri, mediaType))
        }
        cursor.close()
        adapter.notifyDataSetChanged()
        binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun pauseOrResumeDownload(item: DownloadItem) {
        // DownloadManager doesn't natively support pause/resume for basic downloads
        // For full pause/resume, use a custom download manager
        Toast.makeText(this, "Download: ${item.title}", Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

class DownloadsAdapter(
    private val items: List<DownloadItem>,
    private val onAction: (DownloadItem) -> Unit
) : RecyclerView.Adapter<DownloadsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false)
    )

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.tvTitle.text = item.title
        val statusText = when (item.status) {
            DownloadManager.STATUS_RUNNING -> {
                val pct = if (item.bytesTotal > 0) (item.bytesDownloaded * 100 / item.bytesTotal).toInt() else 0
                h.progressBar.progress = pct
                h.progressBar.visibility = View.VISIBLE
                "Downloading… $pct%"
            }
            DownloadManager.STATUS_SUCCESSFUL -> { h.progressBar.visibility = View.GONE; "Completed" }
            DownloadManager.STATUS_FAILED -> { h.progressBar.visibility = View.GONE; "Failed" }
            DownloadManager.STATUS_PAUSED -> { h.progressBar.visibility = View.GONE; "Paused" }
            else -> { h.progressBar.visibility = View.GONE; "Pending" }
        }
        h.tvStatus.text = statusText
        h.itemView.setOnClickListener { onAction(item) }
    }

    override fun getItemCount() = items.size
}
