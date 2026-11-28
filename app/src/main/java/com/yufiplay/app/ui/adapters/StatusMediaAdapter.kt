package com.yufiplay.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yufiplay.app.R
import com.yufiplay.app.data.StatusMediaItem
import com.yufiplay.app.databinding.ItemStatusMediaBinding

class StatusMediaAdapter(
    private val onItemClick: (StatusMediaItem) -> Unit,
    private val onSaveClick: (StatusMediaItem) -> Unit,
    private val onShareClick: (StatusMediaItem) -> Unit
) : ListAdapter<StatusMediaItem, StatusMediaAdapter.StatusViewHolder>(DiffCallback) {

    inner class StatusViewHolder(private val binding: ItemStatusMediaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StatusMediaItem) {
            binding.ivVideoBadge.visibility = if (item.isVideo) View.VISIBLE else View.GONE

            Glide.with(binding.ivStatusThumb.context)
                .load(item.uri)
                .placeholder(R.drawable.ic_yufiplay_logo)
                .centerCrop()
                .into(binding.ivStatusThumb)

            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnSaveStatus.setOnClickListener { onSaveClick(item) }
            binding.btnShareStatus.setOnClickListener { onShareClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusViewHolder {
        val binding = ItemStatusMediaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return StatusViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StatusViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<StatusMediaItem>() {
            override fun areItemsTheSame(
                oldItem: StatusMediaItem,
                newItem: StatusMediaItem
            ): Boolean = oldItem.uri == newItem.uri

            override fun areContentsTheSame(
                oldItem: StatusMediaItem,
                newItem: StatusMediaItem
            ): Boolean = oldItem == newItem
        }
    }
}
