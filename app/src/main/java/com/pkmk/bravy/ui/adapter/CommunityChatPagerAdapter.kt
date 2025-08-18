package com.pkmk.bravy.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pkmk.bravy.ui.view.chat.CommunityChatListFragment

class CommunityChatPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 2 // Dua tab: All dan Friend

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CommunityChatListFragment.newInstance("all")
            1 -> CommunityChatListFragment.newInstance("friend")
            else -> throw IllegalStateException("Invalid position")
        }
    }
}