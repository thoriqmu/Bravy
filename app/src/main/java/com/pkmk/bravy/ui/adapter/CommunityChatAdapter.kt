package com.pkmk.bravy.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pkmk.bravy.data.model.CommunityPostDetails
import com.pkmk.bravy.databinding.ItemCommunityChatBinding

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
            // TODO: Isi data ke view seperti nama, judul, deskripsi, dll.
            // Contoh: binding.tvUserName.text = details.author.name
        }
    }
}

object PostDiffCallback : DiffUtil.ItemCallback<CommunityPostDetails>() {
    override fun areItemsTheSame(oldItem: CommunityPostDetails, newItem: CommunityPostDetails): Boolean {
        return oldItem.post.postId == newItem.post.postId
    }

    override fun areContentsTheSame(oldItem: CommunityPostDetails, newItem: CommunityPostDetails): Boolean {
        return oldItem == newItem
    }
}