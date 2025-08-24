package com.pkmk.bravy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.CommentDetails
import com.pkmk.bravy.databinding.ItemCommunityCommentBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// --- PERBARUI TIPE DATA LIST ADAPTER ---
class CommentAdapter : ListAdapter<CommentDetails, CommentAdapter.CommentViewHolder>(CommentDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommunityCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(private val binding: ItemCommunityCommentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // --- IMPLEMENTASIKAN LOGIKA BIND DI SINI ---
        fun bind(details: CommentDetails) {
            val comment = details.comment
            val author = details.author

            binding.tvUserName.text = author.name
            binding.tvPostTime.text = formatTimestamp(comment.timestamp)
            binding.tvCommentText.text = comment.commentText

            loadProfileImage(author.image)

            // --- TAMBAHKAN LOGIKA UNTUK ATTACHMENT ---
            if (details.comment.mediaUrl != null && details.comment.mediaType == "image") {
                binding.ivCommunityChatAttachment.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(details.comment.mediaUrl)
                    .placeholder(R.drawable.ic_image)
                    .into(binding.ivCommunityChatAttachment)
            } else {
                binding.ivCommunityChatAttachment.visibility = View.GONE
            }
        }

        private fun loadProfileImage(imageName: String?) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = if (imageName.isNullOrEmpty()) {
                        FirebaseStorage.getInstance().getReference("picture/default.jpg").downloadUrl.await()
                    } else {
                        FirebaseStorage.getInstance().getReference("picture/$imageName").downloadUrl.await()
                    }
                    withContext(Dispatchers.Main) {
                        Glide.with(itemView.context)
                            .load(url)
                            .circleCrop()
                            .placeholder(R.drawable.ic_profile)
                            .into(binding.ivUserProfile)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.ivUserProfile.setImageResource(R.drawable.ic_profile)
                    }
                }
            }
        }
    }
}

// --- PERBARUI DIFF CALLBACK ---
object CommentDiffCallback : DiffUtil.ItemCallback<CommentDetails>() {
    override fun areItemsTheSame(oldItem: CommentDetails, newItem: CommentDetails): Boolean {
        return oldItem.comment.commentId == newItem.comment.commentId
    }

    override fun areContentsTheSame(oldItem: CommentDetails, newItem: CommentDetails): Boolean {
        return oldItem == newItem
    }
}

// Fungsi helper ini bisa dipindahkan ke file util terpisah jika diinginkan
private fun formatTimestamp(timestamp: Long?): String {
    if (timestamp == null) return ""
    val currentTime = System.currentTimeMillis()
    val elapsedTime = currentTime - timestamp
    val seconds = elapsedTime / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> "$days days ago"
        hours > 0 -> "$hours hours ago"
        minutes > 0 -> "$minutes minutes ago"
        else -> "Just now"
    }
}