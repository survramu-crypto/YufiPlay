package com.yufiplay.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.yufiplay.app.R
import com.yufiplay.app.data.DownloadStatus
import com.yufiplay.app.data.DownloadTask
import com.yufiplay.app.databinding.ItemDownloadBinding

class DownloadAdapter(
    private val onPlayClick: (DownloadTask) -> Unit,
    private val onShareClick: (DownloadTask) -> Unit,
    private val onCancelOrDeleteClick: (DownloadTask) -> Unit
) : ListAdapter<DownloadTask, DownloadAdapter.DownloadViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = ItemDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DownloadViewHolder(private val binding: ItemDownloadBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: DownloadTask) {
            binding.tvDownloadTitle.text = task.title
            binding.tvDownloadMeta.text = "${task.format.label} • ${task.speedOrEta}"

            Glide.with(binding.ivDownloadThumb)
                .load(task.thumbnailUrl)
                .placeholder(R.drawable.ic_yufiplay_logo)
                .centerCrop()
                .into(binding.ivDownloadThumb)

            val isCompleted = task.status == DownloadStatus.COMPLETED
            binding.progressDownload.visibility = if (isCompleted) View.GONE else View.VISIBLE
            binding.progressDownload.progress = task.progress
            binding.btnShareFile.visibility = if (isCompleted) View.VISIBLE else View.GONE

            binding.btnActionFile.setImageResource(
                if (isCompleted) R.drawable.ic_delete else R.drawable.ic_clear
            )

            binding.root.setOnClickListener {
                if (isCompleted) onPlayClick(task)
            }
            binding.btnShareFile.setOnClickListener { onShareClick(task) }
            binding.btnActionFile.setOnClickListener { onCancelOrDeleteClick(task) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DownloadTask>() {
        override fun areItemsTheSame(oldItem: DownloadTask, newItem: DownloadTask) =
            oldItem.taskId == newItem.taskId

        override fun areContentsTheSame(oldItem: DownloadTask, newItem: DownloadTask) =
            oldItem == newItem
    }
}
