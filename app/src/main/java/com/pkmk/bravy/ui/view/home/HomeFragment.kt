package com.pkmk.bravy.ui.view.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.databinding.FragmentHomeBinding
import com.pkmk.bravy.ui.view.chat.CommunityChatActivity
import com.pkmk.bravy.ui.view.chat.PrivateChatActivity
import com.pkmk.bravy.ui.view.practice.PracticeActivity
import com.pkmk.bravy.ui.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

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

        setupListeners()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadUserProfile()
    }

    private fun setupListeners() {
        binding.btnSpeakingLearning.setOnClickListener {
            startActivity(Intent(requireContext(), PracticeActivity::class.java))
        }

        binding.btnCommunityChat.setOnClickListener {
            startActivity(Intent(requireContext(), CommunityChatActivity::class.java))
        }

        binding.btnPrivateChat.setOnClickListener {
            startActivity(Intent(requireContext(), PrivateChatActivity::class.java))
        }

        // --- PERBAIKI LISTENER EMOSI ---
        binding.layoutHappy.setOnClickListener { handleEmotionClick("Happy", R.drawable.vector_happy) }
        binding.layoutNeutral.setOnClickListener { handleEmotionClick("Neutral", R.drawable.vector_neutral) }
        binding.layoutSad.setOnClickListener { handleEmotionClick("Sad", R.drawable.vector_sad) }
        binding.layoutFear.setOnClickListener { handleEmotionClick("Fear", R.drawable.vector_fear) }
    }

    @SuppressLint("StringFormatInvalid")
    private fun setupObservers() {
        viewModel.userProfile.observe(viewLifecycleOwner) { result ->
            result.onSuccess { user ->
                binding.tvUserName.text = "Hi, ${user.name.split(" ").firstOrNull() ?: "User"}!"
                loadProfileImage(user.image)
                updateMoodUI(user)
            }.onFailure { exception ->
                Toast.makeText(requireContext(), "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                binding.tvUserName.text = getString(R.string.greeting_user_name, "User")
                loadProfileImage(null)
            }
        }

        viewModel.moodUpdateStatus.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                Log.d("HomeFragment", "Mood checked in successfully")
            }.onFailure {
                Toast.makeText(requireContext(), "Failed to check in mood.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- BUAT FUNGSI BARU UNTUK UMPAN BALIK INSTAN ---
    private fun handleEmotionClick(emotion: String, drawableRes: Int) {
        // 1. Update UI secara instan
        binding.layoutPickEmotion.visibility = View.GONE
        binding.tvFeelResult.visibility = View.VISIBLE
        binding.tvFeelResult.text = emotion
        binding.shapeableImageView8.setImageResource(drawableRes)
        binding.shapeableImageView8.visibility = View.VISIBLE

        // 2. Panggil ViewModel untuk menyimpan data di latar belakang
        viewModel.checkInDailyMood(emotion)
    }

    private fun updateMoodUI(user: User) {
        val today = Calendar.getInstance()
        val lastCheckIn = user.dailyMood?.timestamp?.let {
            Calendar.getInstance().apply { timeInMillis = it }
        }

        if (lastCheckIn != null && isSameDay(today, lastCheckIn)) {
            // Sudah check-in: Tampilkan hasil
            binding.layoutPickEmotion.visibility = View.GONE
            binding.tvFeelResult.visibility = View.VISIBLE
            binding.shapeableImageView8.visibility = View.VISIBLE // Pastikan terlihat
            binding.tvFeelResult.text = user.dailyMood.emotion

            val emotionDrawable = when (user.dailyMood.emotion.lowercase()) {
                "happy" -> R.drawable.vector_happy
                "neutral" -> R.drawable.vector_neutral
                "sad" -> R.drawable.vector_sad
                "fear" -> R.drawable.vector_fear
                else -> R.drawable.vector_happy
            }
            binding.shapeableImageView8.setImageResource(emotionDrawable)

        } else {
            // Belum check-in: Tampilkan pilihan, sembunyikan gambar
            binding.layoutPickEmotion.visibility = View.VISIBLE
            binding.tvFeelResult.visibility = View.GONE
            binding.shapeableImageView8.visibility = View.GONE // <-- SEMBUNYIKAN GAMBAR DI SINI
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun loadProfileImage(imageName: String?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val imageUrl = getImageUrl(imageName) ?: getImageUrl("default.jpg")
            if (isAdded) {
                Glide.with(this@HomeFragment)
                    .load(imageUrl ?: R.drawable.ic_profile)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(binding.ivProfilePhoto)
            }
        }
    }

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