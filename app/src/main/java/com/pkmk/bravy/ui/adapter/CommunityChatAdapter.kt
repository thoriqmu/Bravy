package com.pkmk.bravy.ui.adapter

import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.CommunityPostDetails
import com.pkmk.bravy.databinding.ItemCommunityChatBinding
import com.pkmk.bravy.ui.view.chat.DetailCommunityChatActivity
import com.pkmk.bravy.ui.viewmodel.CommunityChatViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CommunityChatAdapter(
    private val viewModel: CommunityChatViewModel,
    private val onPostClick: (CommunityPostDetails) -> Unit
) : ListAdapter<CommunityPostDetails, CommunityChatAdapter.PostViewHolder>(PostDiffCallback) {

    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemCommunityChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class PostViewHolder(private val binding: ItemCommunityChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(details: CommunityPostDetails) {
            val post = details.post
            val author = details.author

            binding.tvUserName.text = author.name
            binding.tvPostTitle.text = post.title
            binding.tvPostDescription.text = post.description
            binding.tvPostTime.text = formatTimestamp(post.timestamp)
            binding.likesCount.text = (post.likes?.size ?: 0).toString()
            binding.commentsCount.text = (post.comments?.size ?: 0).toString()

            loadProfileImage(author.image)

            if (post.imageUrl != null) {
                binding.ivCommunityChatAttachment.visibility = View.VISIBLE
                Glide.with(itemView.context).load(post.imageUrl).into(binding.ivCommunityChatAttachment)
            } else {
                binding.ivCommunityChatAttachment.visibility = View.GONE
            }

            // --- TAMBAHKAN LOGIKA LIKE DI SINI ---
            updateLikeButton(post.likes?.containsKey(currentUserId) == true)

            binding.btnLike.setOnClickListener {
                viewModel.toggleLikeOnPost(post.postId)
            }

            binding.layoutCommunity.setOnClickListener {
                onPostClick(details)
            }

            val context = itemView.context

            val openDetailIntent = { focusComment: Boolean ->
                val intent = Intent(context, DetailCommunityChatActivity::class.java).apply {
                    // --- PERBAIKAN UTAMA: KIRIM ID, BUKAN OBJEK ---
                    putExtra(DetailCommunityChatActivity.EXTRA_POST_ID, details.post.postId)
                    putExtra(DetailCommunityChatActivity.EXTRA_AUTHOR_ID, details.author.uid) // Kirim UID author
                    putExtra(DetailCommunityChatActivity.EXTRA_FOCUS_COMMENT, focusComment)
                }
                context.startActivity(intent)
            }

            binding.layoutPostContent.setOnClickListener { openDetailIntent(false) }
            binding.btnComment.setOnClickListener { openDetailIntent(true) }
        }

        private fun updateLikeButton(isLiked: Boolean) {
            val context = itemView.context
            if (isLiked) {
                binding.btnLike.icon = ContextCompat.getDrawable(context, R.drawable.ic_thumb_up_fill)
                binding.btnLike.setTextColor(ContextCompat.getColor(context, R.color.primary))
                binding.btnLike.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary))
            } else {
                binding.btnLike.icon = ContextCompat.getDrawable(context, R.drawable.ic_thumb_up)
                binding.btnLike.setTextColor(ContextCompat.getColor(context, R.color.onBackground))
                binding.btnLike.iconTint = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.onBackground))
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
                            .placeholder(R.drawable.default_picture)
                            .into(binding.ivUserProfile)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.ivUserProfile.setImageResource(R.drawable.default_picture)
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