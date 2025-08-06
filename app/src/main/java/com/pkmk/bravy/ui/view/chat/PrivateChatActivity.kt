package com.pkmk.bravy.ui.view.chat

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkmk.bravy.data.model.User // Pastikan model User di-import
import com.pkmk.bravy.databinding.ActivityPrivateChatBinding
import com.pkmk.bravy.ui.adapter.PrivateChatAdapter
import com.pkmk.bravy.ui.viewmodel.PrivateChatViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PrivateChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivateChatBinding
    private val viewModel: PrivateChatViewModel by viewModels()
    private lateinit var chatAdapter: PrivateChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPrivateChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupListeners()

        // Memulai pengambilan data
        viewModel.loadRecentChats()
    }

    private fun setupRecyclerView() {
        chatAdapter = PrivateChatAdapter { recentChat ->
            // Menangani klik pada item: Navigasi ke DetailPrivateChatActivity
            val intent = Intent(this, DetailPrivateChatActivity::class.java).apply {
                // Mengirim data yang diperlukan ke activity berikutnya
                putExtra(DetailPrivateChatActivity.EXTRA_CHAT_ID, recentChat.chatId)
                putExtra(DetailPrivateChatActivity.EXTRA_OTHER_USER, recentChat.user as User)
            }
            startActivity(intent)
        }

        binding.rvPrivateChat.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@PrivateChatActivity)
        }
    }

    private fun setupObservers() {
        // Mengamati perubahan pada daftar chat
        viewModel.recentChats.observe(this) { result ->
            result.onSuccess { chats ->
                chatAdapter.submitList(chats)
            }.onFailure { exception ->
                Toast.makeText(this, "Error loading chats: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        }

        // Anda bisa menambahkan observer untuk `isLoading` di sini untuk menampilkan/menyembunyikan progress bar
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish() // Menutup activity saat tombol kembali ditekan
        }
        // Tambahkan listener untuk search dan FloatingActionButton di sini
    }
}