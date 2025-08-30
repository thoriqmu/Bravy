package com.pkmk.bravy.ui.view.practice

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.ActivityPracticeBinding
import com.pkmk.bravy.ui.adapter.PracticeLevelAdapter
import com.pkmk.bravy.ui.viewmodel.PracticeViewModel
import dagger.hilt.android.AndroidEntryPoint

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
                // Handle mood display if needed
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
}