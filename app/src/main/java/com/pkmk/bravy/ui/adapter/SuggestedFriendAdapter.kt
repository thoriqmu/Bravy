package com.pkmk.bravy.ui.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.databinding.ItemSuggestedFriendBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// PERUBAHAN 1: Tambahkan CoroutineScope di constructor
class SuggestedFriendAdapter(
    private var users: List<User>,
    private val scope: CoroutineScope, // <-- Tambahkan ini
    private val onAddFriend: (User) -> Unit,
    private val onCancelRequest: (User) -> Unit
) : RecyclerView.Adapter<SuggestedFriendAdapter.ViewHolder>() {

    private val sentRequests = mutableSetOf<String>()
    private val TAG = "SuggestedFriendAdapter"

    inner class ViewHolder(val binding: ItemSuggestedFriendBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.tvUserName.text = user.name
            if (user.bio.isNullOrEmpty()) {
                binding.tvUserDesc.text = "A new Bravy user!"
            } else {
                binding.tvUserDesc.text = user.bio
            }
            loadProfileImage(user.image)

            if (sentRequests.contains(user.uid)) {
                showCancelButton()
            } else {
                showAddButton()
            }

            binding.btnAddFriend.setOnClickListener {
                if (sentRequests.contains(user.uid)) {
                    onCancelRequest(user)
                    sentRequests.remove(user.uid)
                    showAddButton()
                } else {
                    onAddFriend(user)
                    sentRequests.add(user.uid)
                    showCancelButton()
                }
            }
        }

        private fun showAddButton() {
            binding.btnAddFriend.text = "Add Friend"
        }

        private fun showCancelButton() {
            binding.btnAddFriend.text = "Cancel Request"
        }

        private fun loadProfileImage(imageName: String?) {
            // PERUBAHAN 2: Gunakan scope yang dipinjam dari Fragment
            scope.launch {
                try {
                    if (imageName.isNullOrEmpty()) {
                        // Dapatkan URL unduhan sebagai String
                        val imageUrl = FirebaseStorage.getInstance()
                            .getReference("picture")
                            .child("default.jpg")
                            .downloadUrl
                            .await()
                            .toString()

                        // Berikan String URL tersebut ke Glide
                        Glide.with(itemView.context)
                            .load(imageUrl)
                            .circleCrop()
                            .placeholder(R.drawable.default_picture)
                            .error(R.drawable.default_picture)
                            .into(binding.ivUserPhoto)
                        return@launch
                    }

                    // Dapatkan URL unduhan sebagai String
                    val imageUrl = FirebaseStorage.getInstance()
                        .getReference("picture")
                        .child(imageName)
                        .downloadUrl
                        .await()
                        .toString()

                    // Berikan String URL tersebut ke Glide
                    Glide.with(itemView.context)
                        .load(imageUrl)
                        .circleCrop()
                        .placeholder(R.drawable.default_picture)
                        .error(R.drawable.default_picture)
                        .into(binding.ivUserPhoto)

                } catch (e: Exception) {
                    Log.e(TAG, "Error getting download URL for '$imageName': ${e.message}")
                    binding.ivUserPhoto.setImageResource(R.drawable.default_picture)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSuggestedFriendBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount(): Int = users.size

    fun updateUsers(newUsers: List<User>) {
        users = newUsers
        notifyDataSetChanged()
    }
}