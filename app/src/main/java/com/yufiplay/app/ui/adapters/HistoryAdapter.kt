package com.yufiplay.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yufiplay.app.R
import com.yufiplay.app.data.HistoryItem
import com.yufiplay.app.data.HistoryType
import com.yufiplay.app.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onItemClick: (HistoryItem) -> Unit
) : ListAdapter<HistoryItem, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryItem) {
            binding.tvHistoryTitle.text = item.title
            val badge = if (item.type == HistoryType.DOWNLOADED) {
                "Downloaded (${item.qualityLabel})"
            } else {
                "Watched"
            }
            binding.tvHistoryMeta.text = "$badge • ${item.channel} • ${dateFormat.format(Date(item.timestamp))}"

            Glide.with(binding.ivHistoryThumb)
                .load(item.thumbnailUrl)
                .placeholder(R.drawable.ic_yufiplay_logo)
                .centerCrop()
                .into(binding.ivHistoryThumb)

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<HistoryItem>() {
        override fun areItemsTheSame(oldItem: HistoryItem, newItem: HistoryItem) =
            oldItem.id == newItem.id && oldItem.type == newItem.type

        override fun areContentsTheSame(oldItem: HistoryItem, newItem: HistoryItem) =
            oldItem == newItem
    }
}
