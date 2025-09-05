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
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.MissionType
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.databinding.FragmentHomeBinding
import com.pkmk.bravy.ui.adapter.MissionAdapter
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
    private lateinit var missionAdapter: MissionAdapter

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

        setupRecyclerView()
        setupListeners()
        setupObservers()

        viewModel.loadUserProfile()
    }

    // Hapus onResume() atau kosongkan isinya
    override fun onResume() {
        super.onResume()
        viewModel.startDailyMissionCountdown()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopDailyMissionCountdown()
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
    }

    private fun setupRecyclerView() {
        missionAdapter = MissionAdapter { missionType ->
            // Handle klik pada item misi
            when (missionType) {
                MissionType.SPEAKING -> {
                    val intent = Intent(requireContext(), DailyMissionActivity::class.java)
                    // Kirim topik dari LiveData
                    val topic = viewModel.dailyMissions.value?.getOrNull()?.first()?.description ?: "What made you happy today?"
                    intent.putExtra("TOPIC", topic)
                    startActivity(intent)
                }
                MissionType.COMMUNITY -> startActivity(Intent(requireContext(), CommunityChatActivity::class.java))
                MissionType.CHAT -> startActivity(Intent(requireContext(), PrivateChatActivity::class.java))
            }
        }
        binding.rvDailyMissions.adapter = missionAdapter
        binding.rvDailyMissions.layoutManager = LinearLayoutManager(requireContext())
    }

    @SuppressLint("StringFormatInvalid")
    private fun setupObservers() {
        // Observer untuk isLoading
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.shimmerViewContainer.visibility = View.VISIBLE
                binding.scrollViewContent.visibility = View.GONE
                binding.shimmerViewContainer.startShimmer()
            } else {
                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
                binding.scrollViewContent.visibility = View.VISIBLE
            }
        }

        viewModel.userProfile.observe(viewLifecycleOwner) { result ->
            result.onSuccess { user ->
                binding.tvUserName.text = "Hi, ${user.name.split(" ").firstOrNull() ?: "User"}!"
                loadProfileImage(user.image)
                updateDailyCheckInUI(user)
            }.onFailure { exception ->
                Toast.makeText(requireContext(), "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                binding.tvUserName.text = getString(R.string.greeting_user_name, "User")
                loadProfileImage(null)
            }
        }

        // --- TAMBAHKAN OBSERVER BARU INI UNTUK PROGRESS ---
        viewModel.learningProgress.observe(viewLifecycleOwner) { result ->
            result.onSuccess { (percentage, completed, total) ->
                binding.tvProgressResult.text = "$percentage%"
                binding.tvTaskProgress.text = "$completed/$total Task Complete"
                binding.indicatorProgressResult.progress = percentage
            }.onFailure {
                // Set ke nilai default jika gagal memuat
                binding.tvProgressResult.text = "0%"
                binding.tvTaskProgress.text = "0/0 Task Complete"
                binding.indicatorProgressResult.progress = 0
            }
        }

        viewModel.dailyMissions.observe(viewLifecycleOwner) { result ->
            result.onSuccess { missions ->
                missionAdapter.submitList(missions)
                updateDailyCheckInUI(viewModel.userProfile.value?.getOrNull() ?: User())
            }.onFailure {
                Toast.makeText(requireContext(), "Failed to load daily missions.", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.missionCountdown.observe(viewLifecycleOwner) { timeLeft ->
            binding.countdownDailyMission.text = timeLeft
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateDailyCheckInUI(user: User?) {
        if (user == null) {
            binding.tvTitleFeel.visibility = View.GONE
            return
        }

        val today = Calendar.getInstance()
        val lastCheckInCal = Calendar.getInstance().apply { timeInMillis = user.lastSpeakingTimestamp }

        if (isSameDay(today, lastCheckInCal) && user.lastSpeakingResult != null) {
            binding.materialCardView.visibility = View.VISIBLE
            binding.tvTitleFeel.visibility = View.VISIBLE
            binding.tvTitleFeel.text = "Your Daily Check-in"

            val emotionDrawable = when (user.lastSpeakingResult.lowercase()) {
                "happy" -> R.drawable.vector_happy
                "neutral" -> R.drawable.vector_neutral
                "sad" -> R.drawable.vector_sad
                else -> R.drawable.vector_happy
            }

            val moodEmoji = when (user.lastSpeakingResult.lowercase()) {
                "happy" -> "😊"
                "neutral" -> "😐"
                else -> "😔"
            }

            binding.shapeableImageView8.setImageResource(emotionDrawable)
            binding.chipMood.text = "Mood: ${user.lastSpeakingResult} $moodEmoji"
            binding.chipConfidence.text = "Confidence: ${user.lastSpeakingConfidence}% 👌"
            binding.chipTempo.text = "30s · ${user.lastSpeakingWordCount} words 💬"
            binding.chipStreak.text = "${user.streak} Streak 🔥"

        } else {
            binding.tvTitleFeel.visibility = View.GONE
            binding.materialCardView.visibility = View.GONE
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
                    .load(imageUrl ?: R.drawable.default_picture)
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