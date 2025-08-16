package com.pkmk.bravy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.ChatItem
import com.pkmk.bravy.data.model.Message
import com.pkmk.bravy.databinding.ItemChatDateSeparatorBinding
import com.pkmk.bravy.databinding.ItemDetailPrivateChatBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- PERUBAHAN 1: Ganti tipe data dan ViewHolder ---
class DetailPrivateChatAdapter : ListAdapter<ChatItem, RecyclerView.ViewHolder>(DiffCallback) {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    // --- PERUBAHAN 2: Definisikan tipe view ---
    companion object {
        private const val VIEW_TYPE_MESSAGE_CURRENT = 1
        private const val VIEW_TYPE_MESSAGE_OTHER = 2
        private const val VIEW_TYPE_DATE_SEPARATOR = 3
    }

    object DiffCallback : DiffUtil.ItemCallback<ChatItem>() {
        override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return if (oldItem is ChatItem.MessageItem && newItem is ChatItem.MessageItem) {
                oldItem.message.messageId == newItem.message.messageId
            } else if (oldItem is ChatItem.DateSeparatorItem && newItem is ChatItem.DateSeparatorItem) {
                oldItem.date == newItem.date
            } else false
        }
        override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
            return oldItem == newItem
        }
    }

    // --- PERUBAHAN 3: Tentukan tipe view berdasarkan item ---
    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ChatItem.MessageItem -> {
                if (item.message.sender_uid == currentUserId) VIEW_TYPE_MESSAGE_CURRENT
                else VIEW_TYPE_MESSAGE_OTHER
            }
            is ChatItem.DateSeparatorItem -> VIEW_TYPE_DATE_SEPARATOR
        }
    }

    // --- PERUBAHAN 4: Buat ViewHolder yang sesuai ---
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_MESSAGE_CURRENT, VIEW_TYPE_MESSAGE_OTHER -> {
                val binding = ItemDetailPrivateChatBinding.inflate(inflater, parent, false)
                MessageViewHolder(binding)
            }
            VIEW_TYPE_DATE_SEPARATOR -> {
                val binding = ItemChatDateSeparatorBinding.inflate(inflater, parent, false)
                DateSeparatorViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    // --- PERUBAHAN 5: Bind data ke ViewHolder yang sesuai ---
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatItem.MessageItem -> (holder as MessageViewHolder).bind(item.message)
            is ChatItem.DateSeparatorItem -> (holder as DateSeparatorViewHolder).bind(item)
        }
    }

    // --- ViewHolder untuk Pesan (logika lama dipindahkan ke sini) ---
    class MessageViewHolder(private val binding: ItemDetailPrivateChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message) {
            val isCurrentUser = message.sender_uid == FirebaseAuth.getInstance().currentUser?.uid

            binding.clCurrentUserMessage.visibility = if (isCurrentUser) View.VISIBLE else View.GONE
            binding.clOtherUserMessage.visibility = if (isCurrentUser) View.GONE else View.VISIBLE

            if (isCurrentUser) {
                setupMessageContent(
                    message,
                    binding.tvMessageContentCurrent,
                    binding.ivMessageImageCurrent,
                    binding.tvTimestampCurrent
                )
            } else {
                setupMessageContent(
                    message,
                    binding.tvMessageContentOther,
                    binding.ivMessageImageOther,
                    binding.tvTimestampOther
                )
            }
        }

        private fun setupMessageContent(message: Message, tvContent: android.widget.TextView, ivContent: android.widget.ImageView, tvTimestamp: android.widget.TextView) {
            when (message.type) {
                "text" -> {
                    tvContent.visibility = View.VISIBLE
                    ivContent.visibility = View.GONE
                    tvContent.text = message.content
                }
                "image" -> {
                    tvContent.visibility = View.GONE
                    ivContent.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(message.content)
                        .placeholder(R.drawable.ic_image)
                        .into(ivContent)
                }
            }
            tvTimestamp.text = formatTimestamp(message.timestamp)
        }

        private fun formatTimestamp(timestamp: Long): String {
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }

    // --- ViewHolder BARU untuk Pembatas Tanggal ---
    class DateSeparatorViewHolder(private val binding: ItemChatDateSeparatorBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatItem.DateSeparatorItem) {
            binding.tvDateSeparator.text = item.date
        }
    }
}