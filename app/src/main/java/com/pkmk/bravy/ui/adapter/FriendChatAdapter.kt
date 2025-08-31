package com.pkmk.bravy.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.databinding.ItemFriendChatBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FriendChatAdapter(
    private val scope: CoroutineScope,
    private val onClickListener: (User) -> Unit
) : ListAdapter<User, FriendChatAdapter.FriendViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val binding = ItemFriendChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FriendViewHolder(scope,binding)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user)
        holder.itemView.setOnClickListener {
            onClickListener(user)
        }
    }

    class FriendViewHolder(
        private val scope: CoroutineScope,
        private val binding: ItemFriendChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.tvFriendName.text = user.name
            loadProfileImage(user.image)
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
                        .into(binding.ivFriendPhoto)
                } catch (e: Exception) {
                    binding.ivFriendPhoto.setImageResource(R.drawable.default_picture)
                }
            }

        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.uid == newItem.uid
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}