package com.pkmk.bravy.ui.view.practice

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.databinding.ActivityPracticeBinding
import com.pkmk.bravy.ui.viewmodel.PracticeViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar

@AndroidEntryPoint
class PracticeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPracticeBinding
    private val viewModel: PracticeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPracticeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        // Muat ulang data setiap kali activity ini ditampilkan
        viewModel.loadUserProfile()
    }

    private fun setupObservers() {
        viewModel.userProfile.observe(this) { result ->
            result.onSuccess { user ->
                // Panggil fungsi helper untuk update UI
                updateActivityUI(user)
            }.onFailure { exception ->
                // Handle jika gagal memuat user
                binding.tvPoints.text = "0"
                binding.tvStreak.text = "0"
                binding.layoutWithEmotion.visibility = View.GONE
                binding.tvNoEmotion.visibility = View.VISIBLE
            }
        }
    }

    private fun setupListeners() {
        binding.btnWatch1.setOnClickListener { startLearningActivity("level_1") }
        binding.btnWatch2.setOnClickListener { startLearningActivity("level_2") }
        binding.btnWatch3.setOnClickListener { startLearningActivity("level_3") }
        binding.btnWatch4.setOnClickListener { startLearningActivity("level_4") }
        binding.btnWatch5.setOnClickListener { startLearningActivity("level_5") }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun startLearningActivity(levelId: String) {
        val intent = Intent(this, LearningActivity::class.java).apply {
            putExtra("LEVEL_ID", levelId)
        }
        startActivity(intent)
    }

    private fun updateActivityUI(user: User) {
        // Update Points dan Streak
        binding.tvPoints.text = user.points.toString()
        binding.tvStreak.text = user.streak.toString()

        // Logika untuk Daily Mood
        val today = Calendar.getInstance()
        val lastCheckIn = user.dailyMood?.timestamp?.let {
            Calendar.getInstance().apply { timeInMillis = it }
        }

        if (lastCheckIn != null && isSameDay(today, lastCheckIn)) {
            // Jika sudah check-in hari ini, tampilkan mood
            binding.layoutWithEmotion.visibility = View.VISIBLE
            binding.tvNoEmotion.visibility = View.GONE

            val emotion = user.dailyMood.emotion
            binding.tvLastSpeakingResult.text = emotion

            val emotionDrawable = when (emotion.lowercase()) {
                "happy" -> R.drawable.vector_happy
                "neutral" -> R.drawable.vector_neutral
                "sad" -> R.drawable.vector_sad
                "fear" -> R.drawable.vector_fear
                else -> R.drawable.vector_happy
            }
            binding.ivEmotion.setImageResource(emotionDrawable)
        } else {
            // Jika belum check-in, tampilkan pesan
            binding.layoutWithEmotion.visibility = View.GONE
            binding.tvNoEmotion.visibility = View.VISIBLE
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}