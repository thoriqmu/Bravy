package com.pkmk.bravy.ui.view.chat

import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.ActivityCreateCommunityChatBinding
import com.pkmk.bravy.ui.viewmodel.CommunityChatViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class CreateCommunityChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateCommunityChatBinding
    private val viewModel: CommunityChatViewModel by viewModels()
    private var selectedImageUri: Uri? = null

    // Launcher untuk memilih gambar dari galeri
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            updateImagePreview() // Panggil fungsi baru untuk update UI
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateCommunityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupObservers()

        // Muat data pengguna saat ini
        viewModel.loadCurrentUser()
    }

    private fun setupObservers() {
        viewModel.currentUser.observe(this) { result ->
            result.onSuccess { user ->
                binding.tvUserName.text = user.name
                loadProfileImage(user.image)
            }.onFailure {
                Toast.makeText(this, "Failed to load user data", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.postCreationStatus.observe(this) { result ->
            // Sembunyikan loading saat proses selesai (baik sukses maupun gagal)
            binding.loadingLayout.root.visibility = View.GONE

            result.onSuccess {
                Toast.makeText(this, "Post created successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }

        // --- PERBARUI OBSERVER INI ---
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                binding.loadingLayout.root.visibility = View.VISIBLE
            }
            // Penanganan untuk menyembunyikan loading sudah dipindah ke observer `postCreationStatus`
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnAddImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // --- TAMBAHKAN LISTENER BARU UNTUK TOMBOL REMOVE ---
        binding.btnRemoveImage.setOnClickListener {
            selectedImageUri = null
            updateImagePreview() // Update UI setelah gambar dihapus
        }

        binding.btnCommunityChatPost.setOnClickListener {
            val title = binding.postTitle.text.toString().trim()
            val description = binding.postDescription.text.toString().trim()

            if (title.isBlank()) {
                Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (description.isBlank()) {
                Toast.makeText(this, "Post content cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (title.length > 50) {
                Toast.makeText(this, "Title cannot be more than 50 characters.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ViewModel akan mengubah isLoading menjadi true di sini
            viewModel.createPost(title, description, selectedImageUri)
            viewModel.onPostCreated()
        }
    }

    // --- BUAT FUNGSI BARU UNTUK MENGATUR UI GAMBAR ---
    private fun updateImagePreview() {
        if (selectedImageUri != null) {
            // Jika ada gambar yang dipilih
            binding.ivPreviewImage.visibility = View.VISIBLE
            binding.btnRemoveImage.visibility = View.VISIBLE
            binding.btnAddImage.text = "Change Image"
            Glide.with(this).load(selectedImageUri).into(binding.ivPreviewImage)
        } else {
            // Jika tidak ada gambar
            binding.ivPreviewImage.visibility = View.GONE
            binding.btnRemoveImage.visibility = View.GONE
            binding.btnAddImage.text = "Add Image"
            binding.ivPreviewImage.setImageDrawable(null) // Hapus gambar dari preview
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
                    Glide.with(this@CreateCommunityChatActivity)
                        .load(url)
                        .circleCrop()
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