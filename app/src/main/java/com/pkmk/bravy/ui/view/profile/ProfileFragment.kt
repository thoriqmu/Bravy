package com.pkmk.bravy.ui.view.profile

import android.content.Intent
import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.FragmentProfileBinding
import com.pkmk.bravy.ui.view.auth.LoginActivity
import com.pkmk.bravy.ui.viewmodel.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels()
    private val storageRef = FirebaseStorage.getInstance().getReference("picture")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // PERBAIKAN 1: Panggil fungsi untuk memuat data user
        viewModel.loadUserProfile()

        setupObservers()
        setupClickListeners()
    }

    private fun setupObservers() {
        viewModel.userProfile.observe(viewLifecycleOwner) { result ->
            result.onSuccess { user ->
                binding.tvUserName.text = user.name
                binding.tvUserEmail.text = user.email
                // PERBAIKAN 3: Panggil fungsi helper
                loadProfileImage(user.image)
            }.onFailure { exception ->
                Toast.makeText(requireContext(), "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                binding.tvUserName.text = "User Name"
                binding.tvUserEmail.text = "email@gmail.com"
                // PERBAIKAN 3: Panggil fungsi helper
                loadProfileImage(null)
            }
        }

        viewModel.logoutResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                // Navigasi ke LoginActivity setelah logout berhasil
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "Logout failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnLogout.setOnClickListener {
            viewModel.logout()
        }
    }

    // PERBAIKAN 3: Ekstrak logika pemuatan gambar ke fungsi terpisah
    private fun loadProfileImage(imageName: String?) {
        // PERBAIKAN 2: Gunakan viewLifecycleOwner.lifecycleScope
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Coba muat gambar dari nama yang diberikan, jika null, gunakan default.jpg
                val finalImageName = imageName ?: "default.jpg"
                val imageUrl = storageRef.child(finalImageName).downloadUrl.await()

                Glide.with(this@ProfileFragment)
                    .load(imageUrl)
                    .circleCrop() // Membuat gambar jadi lingkaran
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.ivProfilePhoto)

            } catch (e: Exception) {
                // Jika semua gagal, muat gambar placeholder dari drawable
                Glide.with(this@ProfileFragment)
                    .load(R.drawable.ic_profile)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.ivProfilePhoto)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}