package com.auto.engine.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.auto.engine.R
import com.auto.engine.databinding.ItemTabBinding
import com.auto.engine.tab.Tab

class TabsAdapter(
    private val tabs: List<Tab>,
    private val currentIndex: Int,
    private val onAction: (String, Tab) -> Unit
) : RecyclerView.Adapter<TabsAdapter.TabViewHolder>() {

    inner class TabViewHolder(val binding: ItemTabBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        with(holder.binding) {
            tvTabTitle.text = if (tab.title.isBlank()) "New Tab" else tab.title
            tvTabUrl.text = tab.url
            if (tab.isIncognito) {
                ivTabIcon.setImageResource(R.drawable.ic_incognito)
            } else {
                ivTabIcon.setImageResource(R.drawable.ic_tab)
            }
            // Highlight current tab
            root.setBackgroundColor(
                if (position == currentIndex)
                    ContextCompat.getColor(root.context, R.color.tab_selected)
                else
                    ContextCompat.getColor(root.context, android.R.color.transparent)
            )
            root.setOnClickListener { onAction("switch", tab) }
            btnCloseTab.setOnClickListener { onAction("close", tab) }
        }
    }

    override fun getItemCount() = tabs.size
}
