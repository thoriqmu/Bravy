package com.pkmk.bravy.ui.view.chat

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.pkmk.bravy.databinding.ActivityCommunityChatBinding
import com.pkmk.bravy.ui.adapter.CommunityChatPagerAdapter
import com.pkmk.bravy.ui.viewmodel.CommunityChatViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CommunityChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommunityChatBinding
    private val viewModel: CommunityChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPagerAndTabs()
        setupListeners()
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadCommunityPosts()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnAddCommunityChat.setOnClickListener {
            val intent = Intent(this, CreateCommunityChatActivity::class.java)
            startActivity(intent)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshCommunityPosts()
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            if (!isLoading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun setupViewPagerAndTabs() {
        binding.communityChatViewPager.adapter = CommunityChatPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.communityChatViewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "All"
                1 -> "Friend"
                else -> null
            }
        }.attach()
    }
}