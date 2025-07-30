package com.pkmk.bravy.ui.view.practice

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

        binding.btnBack.setOnClickListener {
            finish()
        }

        viewModel.loadUserProfile()
    }
}