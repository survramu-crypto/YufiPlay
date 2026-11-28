package com.yufiplay.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yufiplay.app.R
import com.yufiplay.app.data.VideoItem
import com.yufiplay.app.databinding.ItemVideoBinding

class VideoAdapter(
    private val onPlayClick: (VideoItem) -> Unit,
    private val onDownloadClick: (VideoItem) -> Unit,
    private val onShareClick: (VideoItem) -> Unit
) : ListAdapter<VideoItem, VideoAdapter.VideoViewHolder>(DiffCallback) {

    inner class VideoViewHolder(private val binding: ItemVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VideoItem) {
            binding.tvTitle.text = item.title
            binding.tvDuration.text = item.formattedDuration()

            val meta = buildString {
                append(item.channel)
                if (item.viewCountText.isNotBlank()) {
                    append(" • ")
                    append(item.viewCountText)
                }
            }
            binding.tvChannel.text = meta

            Glide.with(binding.ivThumbnail.context)
                .load(item.thumbnailUrl)
                .placeholder(R.drawable.ic_yufiplay_logo)
                .centerCrop()
                .into(binding.ivThumbnail)

            binding.root.setOnClickListener { onPlayClick(item) }
            binding.btnDownload.setOnClickListener { onDownloadClick(item) }
            binding.btnShare.setOnClickListener { onShareClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<VideoItem>() {
            override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem): Boolean =
                oldItem == newItem
        }
    }
}
