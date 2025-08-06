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
import com.pkmk.bravy.data.model.Message
import com.pkmk.bravy.databinding.ItemDetailPrivateChatBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetailPrivateChatAdapter : ListAdapter<Message, DetailPrivateChatAdapter.MessageViewHolder>(DiffCallback) {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    companion object {
        private const val VIEW_TYPE_CURRENT_USER = 1
        private const val VIEW_TYPE_OTHER_USER = 2

        object DiffCallback : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
                return oldItem.messageId == newItem.messageId
            }

            override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (message.sender_uid == currentUserId) {
            VIEW_TYPE_CURRENT_USER
        } else {
            VIEW_TYPE_OTHER_USER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemDetailPrivateChatBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position), getItemViewType(position))
    }

    class MessageViewHolder(private val binding: ItemDetailPrivateChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message, viewType: Int) {
            if (viewType == VIEW_TYPE_CURRENT_USER) {
                setupCurrentUserMessage(message)
            } else {
                setupOtherUserMessage(message)
            }
        }

        private fun setupCurrentUserMessage(message: Message) {
            binding.clCurrentUserMessage.visibility = View.VISIBLE
            binding.clOtherUserMessage.visibility = View.GONE

            when (message.type) {
                "text" -> {
                    binding.tvMessageContentCurrent.visibility = View.VISIBLE
                    binding.ivMessageImageCurrent.visibility = View.GONE
                    binding.tvMessageContentCurrent.text = message.content
                }
                "image" -> {
                    binding.tvMessageContentCurrent.visibility = View.GONE
                    binding.ivMessageImageCurrent.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(message.content)
                        .placeholder(R.drawable.ic_image)
                        .into(binding.ivMessageImageCurrent)
                }
                // TODO: Handle audio and reply types
            }
            binding.tvTimestampCurrent.text = formatTimestamp(message.timestamp)
        }

        private fun setupOtherUserMessage(message: Message) {
            binding.clCurrentUserMessage.visibility = View.GONE
            binding.clOtherUserMessage.visibility = View.VISIBLE

            when (message.type) {
                "text" -> {
                    binding.tvMessageContentOther.visibility = View.VISIBLE
                    binding.ivMessageImageOther.visibility = View.GONE
                    binding.tvMessageContentOther.text = message.content
                }
                "image" -> {
                    binding.tvMessageContentOther.visibility = View.GONE
                    binding.ivMessageImageOther.visibility = View.VISIBLE
                    Glide.with(itemView.context)
                        .load(message.content)
                        .placeholder(R.drawable.ic_image)
                        .into(binding.ivMessageImageOther)
                }
                // TODO: Handle audio and reply types
            }
            binding.tvTimestampOther.text = formatTimestamp(message.timestamp)
        }

        private fun formatTimestamp(timestamp: Long): String {
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
    }
}