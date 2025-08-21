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
import com.pkmk.bravy.data.model.CommunityPostDetails
import com.pkmk.bravy.databinding.ItemCommunityChatBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CommunityChatAdapter(private val onClick: (CommunityPostDetails) -> Unit) :
    ListAdapter<CommunityPostDetails, CommunityChatAdapter.PostViewHolder>(PostDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemCommunityChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    inner class PostViewHolder(private val binding: ItemCommunityChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(details: CommunityPostDetails) {
            val post = details.post
            val author = details.author

            // Set data teks
            binding.tvUserName.text = author.name
            binding.tvPostTitle.text = post.title
            binding.tvPostDescription.text = post.description
            binding.tvPostTime.text = formatTimestamp(post.timestamp)
            binding.likesCount.text = post.likes.size.toString()
            binding.commentsCount.text = post.comments.size.toString()

            // Muat gambar profil author
            loadProfileImage(author.image)

            // Logika untuk menampilkan gambar attachment
            if (post.imageUrl != null) {
                binding.ivCommunityChatAttachment.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(post.imageUrl)
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

// Pisahkan DiffCallback agar lebih rapi
object PostDiffCallback : DiffUtil.ItemCallback<CommunityPostDetails>() {
    override fun areItemsTheSame(oldItem: CommunityPostDetails, newItem: CommunityPostDetails): Boolean {
        return oldItem.post.postId == newItem.post.postId
    }

    override fun areContentsTheSame(oldItem: CommunityPostDetails, newItem: CommunityPostDetails): Boolean {
        return oldItem == newItem
    }
}

// Fungsi helper untuk format waktu, letakkan di luar kelas
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