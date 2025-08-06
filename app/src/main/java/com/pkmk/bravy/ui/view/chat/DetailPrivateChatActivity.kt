package com.pkmk.bravy.ui.view.chat

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.databinding.ActivityDetailPrivateChatBinding
import com.pkmk.bravy.ui.adapter.DetailPrivateChatAdapter
import com.pkmk.bravy.ui.viewmodel.DetailChatViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class DetailPrivateChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailPrivateChatBinding
    private val viewModel: DetailChatViewModel by viewModels()
    private lateinit var messageAdapter: DetailPrivateChatAdapter

    companion object {
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_OTHER_USER = "extra_other_user"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailPrivateChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
        val otherUser = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_OTHER_USER, User::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_OTHER_USER)
        }

        if (chatId == null || otherUser == null) {
            Toast.makeText(this, "Chat data is missing.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupUI(otherUser)
        setupRecyclerView()
        setupObservers()
        setupListeners(chatId)

        viewModel.listenForMessages(chatId)
    }

    private fun setupUI(otherUser: User) {
        binding.tvUserName.text = otherUser.name

        // --- PERBAIKAN DI SINI ---
        val imageName = otherUser.image
        if (imageName.isNullOrEmpty()) {
            // Jika tidak ada nama gambar, gunakan placeholder
            binding.ivUserPhoto.setImageResource(R.drawable.ic_profile)
        } else {
            // Jika ada nama gambar, ambil URL dari Firebase Storage
            lifecycleScope.launch {
                try {
                    val storageRef = FirebaseStorage.getInstance().getReference("picture").child(imageName)
                    val downloadUrl = storageRef.downloadUrl.await()
                    Glide.with(this@DetailPrivateChatActivity)
                        .load(downloadUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .into(binding.ivUserPhoto)
                } catch (e: Exception) {
                    // Jika gagal mengambil URL (misal: file tidak ada di storage), gunakan placeholder
                    binding.ivUserPhoto.setImageResource(R.drawable.ic_profile)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = DetailPrivateChatAdapter()
        binding.recyclerView.apply {
            adapter = messageAdapter
            layoutManager = LinearLayoutManager(this@DetailPrivateChatActivity).apply {
                stackFromEnd = true // Pesan baru akan muncul dari bawah
            }
        }
    }

    private fun setupObservers() {
        viewModel.messages.observe(this) { messages ->
            messageAdapter.submitList(messages)
            // Scroll ke posisi paling bawah saat ada pesan baru
            if (messages.isNotEmpty()) {
                binding.recyclerView.post {
                    binding.recyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }
        viewModel.error.observe(this) { error ->
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupListeners(chatId: String) {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSend.setOnClickListener {
            val messageContent = binding.messageInput.text.toString().trim()
            if (messageContent.isNotEmpty()) {
                viewModel.sendMessage(chatId, messageContent, "text")
                binding.messageInput.text?.clear()
            }
        }

        binding.btnAttachment.setOnClickListener {
            binding.layoutAttachment.visibility = if (binding.layoutAttachment.visibility == View.VISIBLE) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
    }
}