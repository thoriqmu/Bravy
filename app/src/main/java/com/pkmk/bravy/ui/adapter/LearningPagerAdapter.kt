package com.pkmk.bravy.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pkmk.bravy.data.model.LearningSection
import com.pkmk.bravy.ui.view.practice.LearningSectionFragment

class LearningPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val sections: List<LearningSection>
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = sections.size

    override fun createFragment(position: Int): Fragment {
        val section = sections[position]
        // Selalu buat instance dari LearningSectionFragment yang baru
        return LearningSectionFragment.newInstance(section)
    }
}