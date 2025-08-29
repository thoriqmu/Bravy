package com.pkmk.bravy.ui.view.friend

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.pkmk.bravy.databinding.ActivityFriendBinding
import com.pkmk.bravy.ui.adapter.FriendPagerAdapter
import com.pkmk.bravy.ui.viewmodel.FriendViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FriendActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendBinding
    private val viewModel: FriendViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPagerAndTabs()
        setupObservers()

        binding.btnBack.setOnClickListener { finish() }
    }

    // Pindahkan pemanggilan load data ke onResume
    override fun onResume() {
        super.onResume()
        viewModel.loadFriends()
    }

    private fun setupViewPagerAndTabs() {
        binding.friendViewPager.adapter = FriendPagerAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.friendViewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Friends"
                1 -> "Requests"
                2 -> "Sent"
                else -> null
            }
        }.attach()
    }

    private fun setupObservers() {
        viewModel.friendList.observe(this) { binding.tvCountFriend.text = it.size.toString() }
        viewModel.receivedList.observe(this) { binding.tvCountReceived.text = it.size.toString() }

        viewModel.actionStatus.observe(this) { result ->
            result.onSuccess { message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, "Action failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}