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

// Observe recent chat users
        viewModel.recentChatUsers.observe(viewLifecycleOwner) { result ->
            result.onSuccess { usersWithChatIds ->
                if (usersWithChatIds.isNotEmpty()) {
                    val (user, chatId) = usersWithChatIds.first() // Ambil user dan chatId
                    binding.recentChatName.text = user.name

                    // AMBIL DAN TAMPILKAN PESAN TERAKHIR
                    viewModel.getLastMessage(chatId).observe(viewLifecycleOwner) { lastMessage ->
                        binding.recentChatText.text = when (lastMessage?.type) {
                            "text" -> lastMessage.content
                            "image" -> "Image Media"
                            "audio" -> "Audio Media"
                            else -> "No messages"
                        }
                    }

                    viewModel.getLastTimestamp(chatId).observe(viewLifecycleOwner) { lastTimestamp ->
                        binding.recentChatTime.text = lastTimestamp?.let {
                            val currentTime = System.currentTimeMillis()
                            val timeDifference = currentTime - it
                            val seconds = timeDifference / 1000
                            val minutes = seconds / 60
                            val hours = minutes / 60
                            val days = hours / 24
                            when {
                                days > 0 -> "$days days ago"
                                hours > 0 -> "$hours hours ago"
                                minutes > 0 -> "$minutes minutes ago"
                                else -> "$seconds seconds ago"
                            }
                        }.toString()
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val imageName = user.image ?: "default.jpg"
                            val imageRef = storageRef.child(imageName)
                            val downloadUrl = imageRef.downloadUrl.await()
                            Glide.with(this@ChatFragment)
                                .load(downloadUrl)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(binding.ivRecentChat)
                        } catch (e: Exception) {
                            Glide.with(this@ChatFragment)
                                .load(R.drawable.ic_profile)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(binding.ivRecentChat)
                        }
                    }
                    binding.layoutRecentChat.setOnClickListener {
                        val intent = Intent(requireContext(), ChatFragment::class.java).apply {
                            putExtra("CHAT_ID", chatId) // Gunakan chatId yang benar
                            putExtra("OTHER_USER_ID", user.uid)
                            putExtra("OTHER_USER_NAME", user.name)
                            putExtra("OTHER_USER_IMAGE", user.image)
                        }
                        startActivity(intent)
                    }
                } else {
                    binding.recentChatName.text = "No Recent Chat"
                    binding.recentChatText.text = "Start a new conversation"
                    Glide.with(this@ChatFragment)
                        .load(R.drawable.ic_profile)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(binding.ivRecentChat)
                }
            }.onFailure { exception ->
                binding.recentChatName.text = "User Name"
                binding.recentChatText.text = "Failed to load chat"
                Glide.with(this@ChatFragment)
                    .load(R.drawable.ic_profile)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.ivRecentChat)
                Toast.makeText(requireContext(), "Error loading recent chat: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle private chat click
        binding.btnPrivateChat.setOnClickListener {
            val intent = Intent(requireContext(), ChatFragment::class.java)
            startActivity(intent)
        }

        // Handle community click
        binding.btnCommunityChat.setOnClickListener {
            val intent = Intent(requireContext(), ChatFragment::class.java)
            startActivity(intent)
        }

        viewModel.loadRecentChatUsers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}