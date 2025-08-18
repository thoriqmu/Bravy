package com.pkmk.bravy.ui.view.home

import android.annotation.SuppressLint
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
import androidx.transition.Fade
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.FragmentHomeBinding
import com.pkmk.bravy.ui.view.chat.CommunityChatActivity
import com.pkmk.bravy.ui.view.chat.PrivateChatActivity
import com.pkmk.bravy.ui.view.practice.PracticeActivity
import com.pkmk.bravy.ui.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()
    private val storageRef = FirebaseStorage.getInstance().getReference("picture")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadUserProfile()
        setupObservers()

        binding.btnSpeakingLearning.setOnClickListener {
            val intent = Intent(requireContext(), PracticeActivity::class.java)
            startActivity(intent)
        }

        binding.btnCommunityChat.setOnClickListener {
            val intent = Intent(requireContext(), CommunityChatActivity::class.java)
            startActivity(intent)
        }

        binding.btnPrivateChat.setOnClickListener {
            val intent = Intent(requireContext(), PrivateChatActivity::class.java)
            startActivity(intent)
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun setupObservers() {
        viewModel.userProfile.observe(viewLifecycleOwner) { result ->
            result.onSuccess { user ->
                // Gunakan string resource untuk format yang lebih baik
                binding.tvUserName.text = "Hi,${user.name.split(" ").firstOrNull() ?: "User"}!"
                binding.tvLastSpeakingResult.text = user.lastAnxietyLevel
                loadProfileImage(user.image)
            }.onFailure { exception ->
                Toast.makeText(requireContext(), "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                binding.tvUserName.text = getString(R.string.greeting_user_name, "User")
                binding.tvLastSpeakingResult.text = "None"
                // Panggil fungsi yang sama untuk memuat gambar default
                loadProfileImage(null)
            }
        }
    }

    // FUNGSI HELPER BARU UNTUK MENGHINDARI DUPLIKASI
    private fun loadProfileImage(imageName: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Coba muat gambar dari nama yang diberikan
            val imageUrl = getImageUrl(imageName)
            // Jika gagal, coba muat "default.jpg"
                ?: getImageUrl("default.jpg")

            if (isAdded) { // Pastikan fragment masih terpasang
                Glide.with(this@HomeFragment)
                    .load(imageUrl ?: R.drawable.ic_profile) // Jika semua URL gagal, muat drawable lokal
                    .circleCrop() // Opsional: Membuat gambar jadi bulat
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.ivProfilePhoto)
            }
        }
    }

    // Fungsi suspend kecil untuk mendapatkan URL, mengembalikan null jika gagal
    private suspend fun getImageUrl(imageName: String?): String? {
        if (imageName == null) return null
        return try {
            storageRef.child(imageName).downloadUrl.await().toString()
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}