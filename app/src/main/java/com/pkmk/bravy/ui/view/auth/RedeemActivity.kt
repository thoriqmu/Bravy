package com.pkmk.bravy.ui.view.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.pkmk.bravy.databinding.ActivityRedeemBinding
import com.pkmk.bravy.ui.view.base.BaseActivity
import com.pkmk.bravy.ui.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RedeemActivity : BaseActivity() {

    private lateinit var binding: ActivityRedeemBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRedeemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRedeem.setOnClickListener {
            val code = binding.codeInput.text.toString().trim()
            if (code.length < 4) { // Pastikan kode diisi 6 digit
                Toast.makeText(this, "Please enter a 4-digit code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.validateRedeemCode(code)
        }

        binding.loginPage.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        binding.btnShopee.setOnClickListener {
            val shopeeUrl = "https://shopee.co.id/bravy_id"
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(shopeeUrl)
                    setPackage("com.shopee.id") // package Shopee Indonesia
                }
                startActivity(intent)
            } catch (e: Exception) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(shopeeUrl))
                startActivity(browserIntent)
            }
        }


        viewModel.redeemResult.observe(this) { result ->
            result.onSuccess { redeemCode ->
                Toast.makeText(this, "Code valid! Proceed to register.", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, SignupActivity::class.java).apply {
                    putExtra("REDEEM_CODE", redeemCode)
                }
                startActivity(intent)
            }.onFailure { exception ->
                // Tampilkan pesan error dengan Toast atau cara lain
                val errorMessage = when (exception.message) {
                    "Redeem code has been used" -> "Redeem code has been used"
                    "Invalid redeem code" -> "Invalid redeem code"
                    else -> "Error: ${exception.message}"
                }
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                binding.codeInput.setText("") // Kosongkan input jika salah
            }
        }
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        super.onBackPressed()
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }
}