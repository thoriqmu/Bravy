package com.pkmk.bravy.ui.view.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.pkmk.bravy.R
import com.pkmk.bravy.databinding.ActivityVerificationBinding
import com.pkmk.bravy.ui.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Layar verifikasi email setelah pendaftaran. Pengguna memasukkan token yang
 * dikirim backend ke email, atau meminta kirim ulang.
 *
 * Tombol kirim ulang mengikuti aturan backend (jeda 2 menit antar permintaan):
 * setelah berhasil, tombol dinonaktifkan selama [RESEND_COOLDOWN_SECONDS] agar
 * pengguna tidak memicu error cooldown dari server.
 */
@AndroidEntryPoint
class VerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerificationBinding
    private val viewModel: AuthViewModel by viewModels()

    private var email: String = ""
    private var resendJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()

        binding.verificationSubtitle.text = if (email.isNotEmpty()) {
            getString(R.string.verification_subtitle_with_email, email)
        } else {
            getString(R.string.verification_subtitle)
        }

        binding.btnVerify.setOnClickListener { submitToken() }

        binding.btnResend.setOnClickListener {
            if (email.isBlank()) {
                Toast.makeText(this, R.string.verification_email_missing, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.resendVerification(email)
            }
        }

        binding.loginPage.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        setupObservers()
    }

    private fun submitToken() {
        val token = binding.tokenInput.text.toString().trim()
        if (token.isEmpty()) {
            binding.tokenInputLayout.error = getString(R.string.verification_token_required)
            return
        }
        binding.tokenInputLayout.error = null
        viewModel.verifyEmail(token)
    }

    private fun setupObservers() {
        viewModel.verifyEmailResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, R.string.verification_success, Toast.LENGTH_LONG).show()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }.onFailure { exception ->
                binding.tokenInputLayout.error =
                    exception.message ?: getString(R.string.verification_failed)
            }
        }

        viewModel.resendVerificationResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, R.string.verification_resent, Toast.LENGTH_SHORT).show()
                startResendCooldown()
            }.onFailure { exception ->
                Toast.makeText(
                    this,
                    exception.message ?: getString(R.string.verification_resend_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Menonaktifkan tombol kirim ulang dan menampilkan hitungan mundur selama
     * [RESEND_COOLDOWN_SECONDS] detik. Job sebelumnya dibatalkan lebih dulu agar
     * hitungan tidak berjalan ganda.
     */
    private fun startResendCooldown() {
        resendJob?.cancel()
        resendJob = lifecycleScope.launch {
            var remaining = RESEND_COOLDOWN_SECONDS
            binding.resendCooldownIndicator.isVisible = true
            binding.resendCooldownText.isVisible = true
            binding.resendCooldownIndicator.max = RESEND_COOLDOWN_SECONDS

            while (remaining > 0) {
                binding.resendCooldownIndicator.progress = remaining
                binding.resendCooldownText.text =
                    getString(R.string.verification_resend_cooldown, remaining)
                binding.btnResend.isEnabled = false
                delay(ONE_SECOND_MS)
                remaining--
            }

            binding.btnResend.isEnabled = true
            binding.resendCooldownIndicator.isVisible = false
            binding.resendCooldownText.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        resendJob?.cancel()
        super.onDestroy()
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }

    companion object {
        const val EXTRA_EMAIL = "EMAIL"

        private const val RESEND_COOLDOWN_SECONDS = 120
        private const val ONE_SECOND_MS = 1000L
    }
}
