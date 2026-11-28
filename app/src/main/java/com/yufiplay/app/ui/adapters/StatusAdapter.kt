package com.yufiplay.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yufiplay.app.data.StatusMediaItem
import com.yufiplay.app.databinding.ItemStatusBinding

class StatusAdapter(
    private val onPreviewClick: (StatusMediaItem) -> Unit,
    private val onSaveClick: (StatusMediaItem) -> Unit
) : ListAdapter<StatusMediaItem, StatusAdapter.StatusViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val binding = ItemStatusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StatusViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StatusViewHolder(private val binding: ItemStatusBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: StatusMediaItem) {
            Glide.with(binding.ivStatusThumb)
                .load(item.uri)
                .centerCrop()
                .into(binding.ivStatusThumb)

            binding.ivVideoBadge.visibility = if (item.isVideo) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onPreviewClick(item) }
            binding.btnSaveStatus.setOnClickListener { onSaveClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<StatusMediaItem>() {
        override fun areItemsTheSame(oldItem: StatusMediaItem, newItem: StatusMediaItem) =
            oldItem.uri == newItem.uri

        override fun areContentsTheSame(oldItem: StatusMediaItem, newItem: StatusMediaItem) =
            oldItem == newItem
    }
}
