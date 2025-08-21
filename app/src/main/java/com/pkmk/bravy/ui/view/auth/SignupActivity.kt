package com.pkmk.bravy.ui.view.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.ActivitySignupBinding
import com.pkmk.bravy.databinding.DialogTermsAndConditionsBinding
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

        setupListeners(redeemCode) // Pindahkan logika listener ke fungsi terpisah
        setupObservers()
    }

    private fun setupListeners(redeemCode: String) {
        binding.btnSignup.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            when {
                name.isEmpty() -> binding.nameInputLayout.error = "Please enter your name"
                email.isEmpty() -> binding.emailInputLayout.error = "Please enter your email"
                password.isEmpty() -> binding.passwordInputLayout.error = "Please enter a password"
                password.length < 6 -> binding.passwordInputLayout.error = "Password must be at least 6 characters"
                !binding.cbAgreement.isChecked -> { // Validasi checkbox
                    Toast.makeText(this, "You must agree to the terms and conditions", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // Hapus error jika valid
                    binding.nameInputLayout.error = null
                    binding.emailInputLayout.error = null
                    binding.passwordInputLayout.error = null
                    viewModel.registerUser(name, email, password, redeemCode)
                }
            }
        }

        binding.layoutAgreement.setOnClickListener {
            showTermsDialog()
        }
    }

    private fun setupObservers() {
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

    private fun showTermsDialog() {
        val dialogBinding = DialogTermsAndConditionsBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        var hasAgreed = false

        dialogBinding.btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.scrollViewTerms.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val scrollView = dialogBinding.scrollViewTerms
            val child = scrollView.getChildAt(0)
            // Cek jika sudah scroll sampai bawah (dengan sedikit toleransi)
            if (child.bottom <= (scrollView.height + scrollY + 5)) {
                dialogBinding.btnAgree.isEnabled = true
            }
        }

        dialogBinding.btnAgree.setOnClickListener {
            hasAgreed = true
            binding.cbAgreement.isChecked = true
            dialog.dismiss()
        }

        // Handler saat dialog ditutup (misal dengan tombol back)
        dialog.setOnDismissListener {
            if (!hasAgreed) {
                binding.cbAgreement.isChecked = false
            }
        }

        dialog.show()
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }
}