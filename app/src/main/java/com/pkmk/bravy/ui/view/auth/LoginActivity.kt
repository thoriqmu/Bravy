package com.pkmk.bravy.ui.view.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pkmk.bravy.MainActivity
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.ActivityLoginBinding
import com.pkmk.bravy.ui.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            when {
                email.isEmpty() -> binding.emailInputLayout.error = "Please enter your email"
                password.isEmpty() -> binding.passwordInputLayout.error = "Please enter your password"
                else -> {
                    binding.emailInputLayout.error = null
                    binding.passwordInputLayout.error = null
                    viewModel.loginUser(email, password)
                }
            }
        }

        binding.forgetPassword.setOnClickListener {
            Toast.makeText(this, "Forgot password feature not implemented", Toast.LENGTH_SHORT).show()
        }

        binding.redeemPage.setOnClickListener {
            val intent = Intent(this, RedeemActivity::class.java)
            startActivity(intent)
        }

        viewModel.loginResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }.onFailure { exception ->
                Toast.makeText(this, exception.message ?: "Login failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }
}