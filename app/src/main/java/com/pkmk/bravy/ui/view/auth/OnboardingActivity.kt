package com.pkmk.bravy.ui.view.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.pkmk.bravy.databinding.ActivityOnboardingBinding
import com.pkmk.bravy.ui.adapter.OnboardingAdapter

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup ViewPager Adapter
        val adapter = OnboardingAdapter(this)
        binding.viewPager.adapter = adapter

        // Hubungkan TabLayout dengan ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ -> }.attach()

        // Atur listener untuk perubahan halaman
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // Tampilkan tombol hanya di halaman terakhir
                if (position == adapter.itemCount - 1) {
                    binding.btnRedeem.visibility = View.VISIBLE
                    binding.btnLogin.visibility = View.VISIBLE
                } else {
                    binding.btnRedeem.visibility = View.GONE
                    binding.btnLogin.visibility = View.GONE
                }
            }
        })

        // Atur OnClickListener untuk tombol
        binding.btnRedeem.setOnClickListener {
            val intent = Intent(this, RedeemActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}