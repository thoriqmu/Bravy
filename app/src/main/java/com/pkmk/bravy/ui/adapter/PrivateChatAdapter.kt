package com.pkmk.bravy.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.RecentChat
import com.pkmk.bravy.databinding.ItemPrivateChatBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrivateChatAdapter(
    private val onClickListener: (RecentChat) -> Unit
) : ListAdapter<RecentChat, PrivateChatAdapter.ChatViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemPrivateChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val recentChat = getItem(position)
        holder.bind(recentChat)
        holder.itemView.setOnClickListener {
            onClickListener(recentChat)
        }
    }

    class ChatViewHolder(private val binding: ItemPrivateChatBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(recentChat: RecentChat) {
            val user = recentChat.user
            val lastMessage = recentChat.lastMessage

            binding.chatName.text = user.name
            binding.chatText.text = when (lastMessage?.type) {
                "text" -> lastMessage.content
                "image" -> "Image"
                "audio" -> "Voice Message"
                else -> "No messages yet"
            }
            binding.chatTime.text = formatTimestamp(lastMessage?.timestamp)

            Glide.with(itemView.context)
                .load(user.image)
                .circleCrop()
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .into(binding.ivChat)
        }

        private fun formatTimestamp(timestamp: Long?): String {
            if (timestamp == null) return ""
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<RecentChat>() {
        override fun areItemsTheSame(oldItem: RecentChat, newItem: RecentChat): Boolean {
            return oldItem.chatId == newItem.chatId
        }

        override fun areContentsTheSame(oldItem: RecentChat, newItem: RecentChat): Boolean {
            return oldItem == newItem
        }
    }
}