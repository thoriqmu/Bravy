package com.pkmk.bravy.ui.view.practice

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.ActivityPracticeBinding
import com.pkmk.bravy.ui.viewmodel.PracticeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PracticeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPracticeBinding
    private val viewModel: PracticeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPracticeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.userProfile.observe(this) { result ->
            result.onSuccess { user ->
                binding.tvLastSpeakingResult.text = user.lastAnxietyLevel
            }.onFailure { exception ->
                binding.tvLastSpeakingResult.text = "None"
            }
        }

        binding.btnWatch1.setOnClickListener {
            val intent = Intent(this, LearningActivity::class.java).apply {
                putExtra("LEVEL_ID", "level_1")
            }
            startActivity(intent)
        }

        binding.btnWatch2.setOnClickListener {
            val intent = Intent(this, LearningActivity::class.java).apply {
                putExtra("LEVEL_ID", "level_2")
            }
            startActivity(intent)
        }

        binding.btnWatch3.setOnClickListener {
            val intent = Intent(this, LearningActivity::class.java).apply {
                putExtra("LEVEL_ID", "level_3")
            }
            startActivity(intent)
        }

        binding.btnWatch4.setOnClickListener {
            val intent = Intent(this, LearningActivity::class.java).apply {
                putExtra("LEVEL_ID", "level_4")
            }
            startActivity(intent)
        }

        binding.btnWatch5.setOnClickListener {
            val intent = Intent(this, LearningActivity::class.java).apply {
                putExtra("LEVEL_ID", "level_5")
            }
            startActivity(intent)
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        viewModel.loadUserProfile()
    }
}