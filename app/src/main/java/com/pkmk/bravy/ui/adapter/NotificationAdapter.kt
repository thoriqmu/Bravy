package com.pkmk.bravy.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.AppNotification
import com.pkmk.bravy.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class NotificationAdapter : ListAdapter<AppNotification, NotificationAdapter.NotificationViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NotificationViewHolder(private val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(notification: AppNotification) {
            binding.tvNotificationTitle.text = notification.title
            if (notification.type == "CHAT_MESSAGE"){
                if (notification.message != null && notification.message.startsWith("https://", ignoreCase = true)) {
                    binding.tvNotificationDesc.text = "New message: Image media"
                } else {
                    binding.tvNotificationDesc.text = "New message: ${notification.message}"
                }
            } else {
                binding.tvNotificationDesc.text = notification.message
            }
            binding.tvNotificationTime.text = formatTimestamp(notification.timestamp)

            when (notification.type) {
                "LEARNING_REMINDER" -> {
                    binding.ivNotification.setImageResource(R.drawable.ic_notification_clock)
                }
                "NEW_POST" -> {
                    binding.ivNotification.setImageResource(R.drawable.ic_notification_new_post)
                }
                "NEW_COMMENT" -> {
                    binding.ivNotification.setImageResource(R.drawable.ic_notification_new_comment)
                }
                "NEW_LIKE" -> {
                    binding.ivNotification.setImageResource(R.drawable.ic_thumb_up_fill)
                }
                "CHAT_MESSAGE" -> {
                    binding.ivNotification.setImageResource(R.drawable.ic_notification_new_chat)
                }
                else -> {
                    binding.ivNotification.setImageResource(R.drawable.ic_notification_setting)
                }
            }

        }

        private fun formatTimestamp(timestamp: Long): String {
            if (timestamp == 0L) return ""
            val currentTime = System.currentTimeMillis()
            val diff = currentTime - timestamp

            val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            val days = TimeUnit.MILLISECONDS.toDays(diff)

            return when {
                seconds < 60 -> "just now"
                minutes < 60 -> "$minutes minutes ago"
                hours < 24 -> "$hours hours ago"
                days < 7 -> "$days days ago"
                else -> {
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AppNotification>() {
        override fun areItemsTheSame(oldItem: AppNotification, newItem: AppNotification): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: AppNotification, newItem: AppNotification): Boolean {
            return oldItem == newItem
        }
    }
}