package com.pkmk.bravy.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pkmk.bravy.ui.view.friend.FriendListFragment

class FriendPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> FriendListFragment.newInstance("friend")
            1 -> FriendListFragment.newInstance("received")
            2 -> FriendListFragment.newInstance("sent")
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}