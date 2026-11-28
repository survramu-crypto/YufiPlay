package com.yufiplay.app.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VideoViewHolder(private val binding: ItemVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VideoItem) {
            binding.tvVideoTitle.text = item.title
            binding.tvVideoChannel.text = item.channel
            binding.tvDuration.text = item.formattedDuration()

            Glide.with(binding.ivThumbnail)
                .load(item.thumbnailUrl)
                .placeholder(R.drawable.ic_yufiplay_logo)
                .centerCrop()
                .into(binding.ivThumbnail)

            binding.root.setOnClickListener { onPlayClick(item) }
            binding.btnQuickDownload.setOnClickListener { onDownloadClick(item) }
            binding.btnMoreOptions.setOnClickListener { view ->
                PopupMenu(view.context, view).apply {
                    menu.add(0, 1, 0, R.string.play)
                    menu.add(0, 2, 1, R.string.download)
                    menu.add(0, 3, 2, R.string.share)
                    setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            1 -> onPlayClick(item)
                            2 -> onDownloadClick(item)
                            3 -> onShareClick(item)
                        }
                        true
                    }
                    show()
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<VideoItem>() {
        override fun areItemsTheSame(oldItem: VideoItem, newItem: VideoItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: VideoItem, newItem: VideoItem) = oldItem == newItem
    }
}
