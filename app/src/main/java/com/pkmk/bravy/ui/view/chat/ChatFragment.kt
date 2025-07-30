package com.pkmk.bravy.ui.view.chat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.FragmentChatBinding
import com.pkmk.bravy.ui.viewmodel.ChatViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by viewModels()
    private val storageRef = FirebaseStorage.getInstance().getReference("picture")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.userProfile.observe(viewLifecycleOwner) { result ->
            result.onSuccess { user ->
                loadUserImage(user.image)
            }.onFailure { exception ->
                Toast.makeText(requireContext(), "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                loadUserImage(null)
            }
        }

        viewModel.recentChats.observe(viewLifecycleOwner) { result ->
            result.onSuccess { recentChats ->
                if (recentChats.isNotEmpty()) {
                    // Ambil chat pertama dari daftar yang sudah diurutkan
                    val firstChat = recentChats.first()
                    val user = firstChat.user
                    val chatId = firstChat.chatId
                    val lastMessage = firstChat.lastMessage

                    binding.recentChatName.text = user.name

                    // Gunakan data pesan terakhir yang sudah ada, JANGAN observe lagi
                    binding.recentChatText.text = when (lastMessage?.type) {
                        "text" -> lastMessage.content
                        "image" -> "Image"
                        "audio" -> "Audio"
                        else -> "No messages yet"
                    }
                    binding.recentChatTime.text = formatTimestamp(lastMessage?.timestamp)

                    user.image?.let { loadProfileImage(it) }

                    binding.layoutRecentChat.setOnClickListener {
                        // PERBAIKI NAVIGASI DI SINI: Gunakan Navigation Component
                        // Contoh: findNavController().navigate(R.id.action_to_chatDetail, bundle)
                        Toast.makeText(requireContext(), "TODO: Fix navigation", Toast.LENGTH_SHORT).show()
                    }

                } else {
                    // Handle jika tidak ada recent chat
                    binding.recentChatName.text = "No Recent Chat"
                    binding.recentChatText.text = "Start a new conversation"
                }
            }.onFailure { exception ->
                Toast.makeText(requireContext(), "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle private chat click
        binding.btnPrivateChat.setOnClickListener {
            val intent = Intent(requireContext(), PrivateChatActivity::class.java)
            startActivity(intent)
        }

        // Handle community click
        binding.btnCommunityChat.setOnClickListener {
            val intent = Intent(requireContext(), ChatFragment::class.java)
            startActivity(intent)
        }

        viewModel.loadUserProfile()
        viewModel.loadRecentChatUsers()
    }

    private fun loadUserImage(imageName: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Coba muat gambar dari nama yang diberikan, jika null, gunakan default.jpg
                val finalImageName = imageName ?: "default.jpg"
                val imageUrl = storageRef.child(finalImageName).downloadUrl.await()

                Glide.with(this@ChatFragment)
                    .load(imageUrl)
                    .circleCrop() // Membuat gambar jadi lingkaran
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.ivProfilePhoto)

            } catch (e: Exception) {
                // Jika semua gagal, muat gambar placeholder dari drawable
                Glide.with(this@ChatFragment)
                    .load(R.drawable.ic_profile)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.ivProfilePhoto)
            }
        }
    }

    private fun loadProfileImage(imageUrl: String?) {
        if (imageUrl.isNullOrEmpty()) {
            binding.ivRecentChat.setImageResource(R.drawable.ic_profile)
            return
        }

        val imageLoader = Glide.with(this)

        if (imageUrl.startsWith("https://") || imageUrl.startsWith("http://")) {
            imageLoader.load(imageUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(binding.ivRecentChat)
        } else {
            // Jika hanya nama file, ambil URL dari Firebase Storage
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val storageRef = FirebaseStorage.getInstance().getReference("picture").child(imageUrl)
                    val downloadUrl = storageRef.downloadUrl.await()
                    imageLoader.load(downloadUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(binding.ivRecentChat)
                } catch (e: Exception) {
                    // Jika gagal mengambil dari storage, muat gambar default
                    binding.ivRecentChat.setImageResource(R.drawable.ic_profile)
                }
            }
        }
    }

    private fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null) return "Unknown"
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
            else -> "$seconds seconds ago"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}