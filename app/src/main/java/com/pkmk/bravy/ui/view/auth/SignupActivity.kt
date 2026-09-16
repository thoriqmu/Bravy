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

    private val usernamePattern = Regex("^[a-zA-Z0-9_]+$")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        setupObservers()
    }

    private fun setupListeners() {
        binding.btnSignup.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            val username = binding.usernameInput.text.toString().trim()
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (validateInput(name, username, email, password)) {
                viewModel.registerBackend(name, username, email, password)
            }
        }

        binding.layoutAgreement.setOnClickListener {
            showTermsDialog()
        }
    }

    /**
     * Validasi sisi klien yang mencerminkan aturan backend (registerSchema),
     * sehingga kesalahan umum tertangkap sebelum request dikirim.
     */
    private fun validateInput(name: String, username: String, email: String, password: String): Boolean {
        binding.nameInputLayout.error = null
        binding.usernameInputLayout.error = null
        binding.emailInputLayout.error = null
        binding.passwordInputLayout.error = null

        return when {
            name.length < 2 -> {
                binding.nameInputLayout.error = "Name must be at least 2 characters"
                false
            }
            username.length < 3 -> {
                binding.usernameInputLayout.error = "Username must be at least 3 characters"
                false
            }
            username.length > 30 -> {
                binding.usernameInputLayout.error = "Username must be at most 30 characters"
                false
            }
            !usernamePattern.matches(username) -> {
                binding.usernameInputLayout.error = "Only letters, numbers, and underscores allowed"
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.emailInputLayout.error = "Please enter a valid email"
                false
            }
            password.length < 6 -> {
                binding.passwordInputLayout.error = "Password must be at least 6 characters"
                false
            }
            !binding.cbAgreement.isChecked -> {
                Toast.makeText(this, "You must agree to the terms and conditions", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    private fun setupObservers() {
        viewModel.registerBackendResult.observe(this) { result ->
            result.onSuccess { user ->
                // Akun dibuat tetapi belum aktif: pengguna harus memasukkan token
                // verifikasi yang dikirim ke emailnya.
                Toast.makeText(this, "Account created! Check your email for the code.", Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(this, VerificationActivity::class.java).apply {
                        putExtra(VerificationActivity.EXTRA_EMAIL, user.email)
                    }
                )
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