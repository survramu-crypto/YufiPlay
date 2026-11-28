package com.yufiplay.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yufiplay.app.R
import com.yufiplay.app.data.SearchHistoryItem
import com.yufiplay.app.databinding.ItemSearchHistoryBinding

class SearchHistoryAdapter(
    private val onItemClick: (SearchHistoryItem) -> Unit,
    private val onDeleteClick: (SearchHistoryItem) -> Unit
) : ListAdapter<SearchHistoryItem, SearchHistoryAdapter.SearchHistoryViewHolder>(DiffCallback) {

    inner class SearchHistoryViewHolder(private val binding: ItemSearchHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SearchHistoryItem) {
            binding.tvQueryOrUrl.text = item.queryOrUrl
            binding.ivTypeIcon.setImageResource(
                if (item.isUrl) R.drawable.ic_link else R.drawable.ic_search
            )

            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchHistoryViewHolder {
        val binding = ItemSearchHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SearchHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<SearchHistoryItem>() {
            override fun areItemsTheSame(
                oldItem: SearchHistoryItem,
                newItem: SearchHistoryItem
            ): Boolean = oldItem.queryOrUrl == newItem.queryOrUrl

            override fun areContentsTheSame(
                oldItem: SearchHistoryItem,
                newItem: SearchHistoryItem
            ): Boolean = oldItem == newItem
        }
    }
}
