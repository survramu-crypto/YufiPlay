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

    inner class DownloadViewHolder(private val binding: ItemDownloadBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: DownloadTask) {
            binding.tvTitle.text = task.title
            binding.tvFormat.text = task.format.label
            binding.tvStatus.text = task.speedOrEta.ifBlank { task.status.name }

            Glide.with(binding.ivThumb.context)
                .load(task.thumbnailUrl)
                .placeholder(R.drawable.ic_yufiplay_logo)
                .centerCrop()
                .into(binding.ivThumb)

            when (task.status) {
                DownloadStatus.COMPLETED -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnPlay.visibility = View.VISIBLE
                    binding.btnShare.visibility = View.VISIBLE
                    binding.btnCancel.setImageResource(android.R.drawable.ic_menu_delete)
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                    binding.progressBar.visibility = View.GONE
                    binding.btnPlay.visibility = View.GONE
                    binding.btnShare.visibility = View.GONE
                    binding.btnCancel.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                }
                else -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.isIndeterminate = task.progress <= 0
                    binding.progressBar.progress = task.progress
                    binding.btnPlay.visibility = View.GONE
                    binding.btnShare.visibility = View.GONE
                    binding.btnCancel.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                }
            }

            binding.root.setOnClickListener {
                if (task.status == DownloadStatus.COMPLETED) {
                    onPlayClick(task)
                }
            }
            binding.btnPlay.setOnClickListener { onPlayClick(task) }
            binding.btnShare.setOnClickListener { onShareClick(task) }
            binding.btnCancel.setOnClickListener { onCancelOrDeleteClick(task) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val binding = ItemDownloadBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DownloadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<DownloadTask>() {
            override fun areItemsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean =
                oldItem.taskId == newItem.taskId

            override fun areContentsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean =
                oldItem == newItem
        }
    }
}
