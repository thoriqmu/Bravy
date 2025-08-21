package com.pkmk.bravy.ui.view.profile

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.ActivityProfileSettingBinding
import com.pkmk.bravy.ui.viewmodel.ProfileSettingViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class ProfileSettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSettingBinding
    private val viewModel: ProfileSettingViewModel by viewModels()

    private var selectedImageUri: Uri? = null
    private var isImageRemoved = false

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            isImageRemoved = false // Jika memilih gambar baru, batalkan status hapus
            // Tampilkan preview gambar yang baru dipilih
            Glide.with(this)
                .load(it)
                .circleCrop()
                .into(binding.ivProfilePhoto)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupObservers()

        viewModel.loadUserProfile()
    }

    private fun setupObservers() {
        viewModel.userProfile.observe(this) { result ->
            result.onSuccess { user ->
                // Isi UI dengan data awal
                binding.inputEditName.setText(user.name)
                binding.inputEditBio.setText(user.bio)
                loadProfileImage(user.image)
            }.onFailure {
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.updateStatus.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                finish() // Kembali ke halaman sebelumnya
            }.onFailure {
                Toast.makeText(this, "Update failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            // (Opsional) Tampilkan progress bar saat loading
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }

        binding.btnChangeProfilePicture.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnRemoveProfilePicture.setOnClickListener {
            showRemovePictureConfirmationDialog()
        }

        binding.btnSave.setOnClickListener {
            val newName = binding.inputEditName.text.toString().trim()
            val newBio = binding.inputEditBio.text.toString().trim()

            if (newName.isEmpty()) {
                binding.layoutEditName.error = "Name cannot be empty"
                return@setOnClickListener
            }
            binding.layoutEditName.error = null

            viewModel.saveProfileChanges(newName, newBio, selectedImageUri, isImageRemoved)
        }
    }

    private fun showRemovePictureConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Remove Profile Picture")
            .setMessage("Are you sure you want to remove your profile picture?")
            .setPositiveButton("Yes, Remove") { _, _ ->
                isImageRemoved = true
                selectedImageUri = null // Hapus referensi URI yang dipilih jika ada
                loadProfileImage(null) // Tampilkan gambar default
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadProfileImage(imageName: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Jika imageName null (karena dihapus atau memang tidak ada), gunakan default.jpg
                val finalImageName = imageName ?: "default.jpg"
                val url = FirebaseStorage.getInstance()
                    .getReference("picture/$finalImageName")
                    .downloadUrl.await()

                withContext(Dispatchers.Main) {
                    Glide.with(this@ProfileSettingActivity)
                        .load(url)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .into(binding.ivProfilePhoto)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.ivProfilePhoto.setImageResource(R.drawable.ic_profile)
                }
            }
        }
    }
}