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
import com.pkmk.bravy.databinding.ActivitySignupBinding
import com.pkmk.bravy.ui.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignupActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignupBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val redeemCode = intent.getStringExtra("REDEEM_CODE") ?: ""

        binding.btnSignup.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            when {
                name.isEmpty() -> binding.nameInputLayout.error = "Please enter your name"
                email.isEmpty() -> binding.emailInputLayout.error = "Please enter your email"
                password.isEmpty() -> binding.passwordInputLayout.error = "Please enter a password"
                password.length < 6 -> binding.passwordInputLayout.error = "Password must be at least 6 characters"
                else -> {
                    binding.nameInputLayout.error = null
                    binding.emailInputLayout.error = null
                    binding.passwordInputLayout.error = null
                    viewModel.registerUser(name, email, password, redeemCode)
                }
            }
        }

        viewModel.registerResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "Registration successful! Please login.", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }.onFailure { exception ->
                Toast.makeText(this, exception.message ?: "Registration failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}