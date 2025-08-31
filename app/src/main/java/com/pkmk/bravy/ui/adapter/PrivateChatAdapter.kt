package com.pkmk.bravy.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.RecentChat
import com.pkmk.bravy.databinding.ItemPrivateChatBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Pastikan constructor menerima CoroutineScope
class PrivateChatAdapter(
    private val scope: CoroutineScope,
    private val onClickListener: (RecentChat) -> Unit
) : ListAdapter<RecentChat, PrivateChatAdapter.ChatViewHolder>(DiffCallback) {

    // Buat ViewHolder dengan memberikan scope
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemPrivateChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChatViewHolder(binding, scope) // <-- Berikan scope ke ViewHolder
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val recentChat = getItem(position)
        holder.bind(recentChat)
        holder.itemView.setOnClickListener {
            onClickListener(recentChat)
        }
    }

    // Ubah constructor ViewHolder untuk menerima scope
    class ChatViewHolder(
        private val binding: ItemPrivateChatBinding,
        private val scope: CoroutineScope
    ) : RecyclerView.ViewHolder(binding.root) {

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

            // HANYA PANGGIL FUNGSI INI, HAPUS PANGGILAN GLIDE DARI SINI
            loadProfileImage(user.image)
        }

        private fun formatTimestamp(timestamp: Long?): String {
            if (timestamp == null) return ""
            return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }

        private fun loadProfileImage(imageName: String?) {
            scope.launch {
                try {
                    val finalImageName = imageName ?: "default.jpg"

                    val imageUrl = FirebaseStorage.getInstance()
                        .getReference("picture")
                        .child(finalImageName)
                        .downloadUrl
                        .await()
                        .toString()

                    Glide.with(itemView.context)
                        .load(imageUrl)
                        .circleCrop()
                        .placeholder(R.drawable.default_picture)
                        .error(R.drawable.default_picture)
                        .into(binding.ivChat)

                } catch (e: Exception) {
                    binding.ivChat.setImageResource(R.drawable.default_picture)
                }
            }
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