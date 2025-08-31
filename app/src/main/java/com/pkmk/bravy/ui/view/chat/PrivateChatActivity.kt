package com.pkmk.bravy.ui.view.chat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.databinding.ActivityPrivateChatBinding
import com.pkmk.bravy.ui.adapter.FriendChatAdapter // <-- Import adapter baru
import com.pkmk.bravy.ui.adapter.PrivateChatAdapter
import com.pkmk.bravy.ui.viewmodel.PrivateChatViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PrivateChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivateChatBinding
    private val viewModel: PrivateChatViewModel by viewModels()
    private lateinit var chatAdapter: PrivateChatAdapter
    private lateinit var friendAdapter: FriendChatAdapter // <-- Deklarasi adapter baru

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivateChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        setupObservers()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadInitialData()
    }

    private fun setupRecyclerViews() {
        // Setup adapter untuk recent chats
        chatAdapter = PrivateChatAdapter(lifecycleScope) { recentChat ->
            openDetailChat(recentChat.chatId, recentChat.user)
        }
        binding.rvPrivateChat.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@PrivateChatActivity)
        }

        friendAdapter = FriendChatAdapter(lifecycleScope) { friend ->
            viewModel.onFriendClicked(friend)
        }

        binding.rvFriendList.apply {
            adapter = friendAdapter
            layoutManager = LinearLayoutManager(this@PrivateChatActivity, LinearLayoutManager.HORIZONTAL, false)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading && !binding.swipeRefreshLayout.isRefreshing) {
                // Tampilkan shimmer hanya saat loading awal
                binding.shimmerViewContainer.visibility = View.VISIBLE
                binding.contentLayout.visibility = View.GONE
            } else if (!isLoading) {
                binding.shimmerViewContainer.visibility = View.GONE
                binding.contentLayout.visibility = View.VISIBLE
            }
        }

        viewModel.isRefreshing.observe(this) { isRefreshing ->
            binding.swipeRefreshLayout.isRefreshing = isRefreshing
        }

        viewModel.recentChats.observe(this) { result ->
            result.onSuccess { chats ->
                if (chats.isEmpty()) {
                    binding.rvPrivateChat.visibility = View.GONE
                    binding.tvNoPrivateChat.visibility = View.VISIBLE
                } else {
                    binding.rvPrivateChat.visibility = View.VISIBLE
                    binding.tvNoPrivateChat.visibility = View.GONE
                    chatAdapter.submitList(chats)
                }
            }.onFailure { Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show() }
        }

        // Observer BARU untuk friends list
        viewModel.friends.observe(this) { result ->
            result.onSuccess { friends ->
                if (friends.isEmpty()) {
                    binding.rvFriendList.visibility = View.GONE
                    binding.tvNoFriendList.visibility = View.VISIBLE
                } else {
                    binding.rvFriendList.visibility = View.VISIBLE
                    binding.tvNoFriendList.visibility = View.GONE
                    friendAdapter.submitList(friends)
                }
            }.onFailure { Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show() }
        }

        viewModel.navigateToChat.observe(this) { chatInfo ->
            chatInfo?.let { (chatId, otherUser) ->
                // Panggil fungsi openDetailChat yang sudah ada agar konsisten
                openDetailChat(chatId, otherUser)
                viewModel.onNavigationDone() // Reset event
            }
        }
    }

    // Fungsi helper untuk navigasi
    private fun openDetailChat(chatId: String, otherUser: User) {
        val intent = Intent(this, DetailPrivateChatActivity::class.java).apply {
            putExtra(DetailPrivateChatActivity.EXTRA_CHAT_ID, chatId)
            // SEKARANG MENGIRIM SELURUH OBJEK 'User' DENGAN BENAR
            putExtra(DetailPrivateChatActivity.EXTRA_OTHER_USER, otherUser)
        }
        startActivity(intent)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}