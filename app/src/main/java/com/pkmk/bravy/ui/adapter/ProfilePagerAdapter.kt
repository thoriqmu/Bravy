package com.pkmk.bravy.ui.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pkmk.bravy.ui.view.profile.UserActivityFragment
import com.pkmk.bravy.ui.view.profile.UserSettingFragment

class ProfilePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> UserActivityFragment()
            1 -> UserSettingFragment()
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}