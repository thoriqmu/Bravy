package com.pkmk.bravy.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pkmk.bravy.data.model.LearningSection
import com.pkmk.bravy.ui.view.practice.LearningSectionFragment

class LearningPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private var sections: List<LearningSection> = emptyList()

    fun setSections(sections: List<LearningSection>) {
        this.sections = sections
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = sections.size

    override fun createFragment(position: Int): Fragment {
        return LearningSectionFragment.newInstance(sections[position])
    }
}