package com.pkmk.bravy.ui.view.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.ActivityRedeemBinding
import com.pkmk.bravy.ui.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RedeemActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRedeemBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRedeemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRedeem.setOnClickListener {
            val code = binding.codeInput.text.toString().trim()
            if (code.isEmpty()) {
                binding.codeInputLayout.error = "Please enter a code"
                return@setOnClickListener
            }
            binding.codeInputLayout.error = null
            viewModel.validateRedeemCode(code)
        }

        binding.loginPage.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        viewModel.redeemResult.observe(this) { result ->
            result.onSuccess { redeemCode ->
                Toast.makeText(this, "Code valid! Proceed to register.", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, SignupActivity::class.java).apply {
                    putExtra("REDEEM_CODE", redeemCode)
                }
                startActivity(intent)
            }.onFailure { exception ->
                binding.codeInputLayout.error = when (exception.message) {
                    "Redeem code has been used" -> "Redeem code has been used"
                    "Invalid redeem code" -> "Invalid redeem code"
                    else -> "Error: ${exception.message}"
                }
            }
        }
    }
}