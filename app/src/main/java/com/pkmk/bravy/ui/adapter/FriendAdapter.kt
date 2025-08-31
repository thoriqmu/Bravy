package com.pkmk.bravy.ui.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.FriendInfo
import com.pkmk.bravy.databinding.ItemFriendBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FriendAdapter(
    private val scope: CoroutineScope,
    private val onActionClick: (friendInfo: FriendInfo, action: String) -> Unit
) : ListAdapter<FriendInfo, FriendAdapter.FriendViewHolder>(DIFF_CALLBACK) {

    // Buat ViewHolder dengan memberikan scope
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val binding = ItemFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FriendViewHolder(binding, scope) // Berikan scope ke ViewHolder
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // PERUBAHAN 2: Ubah constructor ViewHolder untuk menerima scope
    inner class FriendViewHolder(
        private val binding: ItemFriendBinding,
        private val scope: CoroutineScope
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(friendInfo: FriendInfo) {
            val user = friendInfo.user
            binding.tvFriendName.text = user.name
            loadProfileImage(user.image)

            binding.btnRemoveFriend.isVisible = friendInfo.status == "friend"
            binding.layoutRequestActions.isVisible = friendInfo.status == "received"
            binding.btnCancelRequest.isVisible = friendInfo.status == "sent"

            binding.btnRemoveFriend.setOnClickListener { onActionClick(friendInfo, "remove") }
            binding.btnCancelRequest.setOnClickListener { onActionClick(friendInfo, "cancel") }
            binding.btnAcceptRequest.setOnClickListener { onActionClick(friendInfo, "accept") }
            binding.btnRejectRequest.setOnClickListener { onActionClick(friendInfo, "reject") }
        }

        private fun loadProfileImage(imageName: String?) {
            // PERUBAHAN 3: Gunakan scope yang dipinjamkan untuk menjalankan coroutine
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
                        .into(binding.ivFriendPhoto)

                } catch (e: Exception) {
                    Log.e("FriendAdapter", "Error loading image for $imageName: ${e.message}")
                    binding.ivFriendPhoto.setImageResource(R.drawable.default_picture)
                }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FriendInfo>() {
            override fun areItemsTheSame(oldItem: FriendInfo, newItem: FriendInfo): Boolean =
                oldItem.user.uid == newItem.user.uid
            override fun areContentsTheSame(oldItem: FriendInfo, newItem: FriendInfo): Boolean =
                oldItem == newItem
        }
    }
}