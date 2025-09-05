package com.pkmk.bravy.ui.view.practice

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.databinding.ActivityPracticeBinding
import com.pkmk.bravy.ui.adapter.PracticeLevelAdapter
import com.pkmk.bravy.ui.viewmodel.PracticeViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar

@AndroidEntryPoint
class PracticeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPracticeBinding
    private val viewModel: PracticeViewModel by viewModels()
    private lateinit var practiceLevelAdapter: PracticeLevelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPracticeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        observeViewModel()
        viewModel.loadData()

        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        practiceLevelAdapter = PracticeLevelAdapter { level ->
            // Handle level click
            val intent = Intent(this, LearningActivity::class.java)
            intent.putExtra("LEVEL_ID", level.levelId)
            startActivity(intent)
        }
        binding.rvPracticeLevels.apply {
            adapter = practiceLevelAdapter
            layoutManager = LinearLayoutManager(this@PracticeActivity)
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                binding.shimmerViewContainer.visibility = View.VISIBLE
                binding.contentLayout.visibility = View.GONE
                binding.shimmerViewContainer.startShimmer()
            } else {
                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
                binding.contentLayout.visibility = View.VISIBLE
            }
        }

        viewModel.userProfile.observe(this) { result ->
            result.onSuccess { user ->
                binding.tvPoints.text = (user.points ?: 0).toString()
                binding.tvStreak.text = (user.streak ?: 0).toString()
                updateDailyMoodUI(viewModel.userProfile.value?.getOrNull() ?: User())
            }.onFailure {
                // Handle error
            }
        }

        viewModel.learningLevels.observe(this) { result ->
            result.onSuccess { levels ->
                practiceLevelAdapter.submitList(levels)
            }.onFailure {
                Toast.makeText(this, "Gagal memuat level", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateDailyMoodUI(user: User?) {
        if (user == null) {
            binding.tvNoEmotion.visibility = View.VISIBLE
            return
        }

        val today = Calendar.getInstance()
        val lastCheckInCal = Calendar.getInstance().apply { timeInMillis = user.lastSpeakingTimestamp }

        if (isSameDay(today, lastCheckInCal) && user.lastSpeakingResult != null){
            binding.tvNoEmotion.visibility = View.GONE
            binding.layoutWithEmotion.visibility = View.VISIBLE
            binding.tvLastSpeakingResult.text = user.lastSpeakingResult
            when (user.lastSpeakingResult.lowercase()) {
                "happy" -> binding.ivEmotion.setImageResource(R.drawable.vector_happy)
                "neutral" -> binding.ivEmotion.setImageResource(R.drawable.vector_neutral)
                "sad" -> binding.ivEmotion.setImageResource(R.drawable.vector_sad)
                else -> binding.ivEmotion.setImageResource(R.drawable.vector_happy)
            }
        } else {
            binding.tvNoEmotion.visibility = View.VISIBLE
            binding.layoutWithEmotion.visibility = View.GONE
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}