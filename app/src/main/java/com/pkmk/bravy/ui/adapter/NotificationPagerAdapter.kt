package com.pkmk.bravy.ui.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pkmk.bravy.ui.view.notifications.NotificationListFragment

class NotificationPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3 // All, Progress, Chat

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> NotificationListFragment.newInstance("ALL")
            1 -> NotificationListFragment.newInstance("PROGRESS")
            2 -> NotificationListFragment.newInstance("CHAT")
            else -> throw IllegalStateException("Invalid position")
        }
    }
}