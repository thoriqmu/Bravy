package com.pkmk.bravy.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pkmk.bravy.data.model.LearningSection
import com.pkmk.bravy.ui.view.practice.level1.MaterialLevel1Fragment
import com.pkmk.bravy.ui.view.practice.level1.PracticeLevel1Fragment
import com.pkmk.bravy.ui.view.practice.level2.MaterialLevel2Fragment

class LearningPagerAdapter(activity: FragmentActivity, private val levelId: String) : FragmentStateAdapter(activity) {

    private var sections: List<LearningSection> = emptyList()

    fun setSections(sections: List<LearningSection>) {
        this.sections = sections
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = sections.size

    override fun createFragment(position: Int): Fragment {
        val section = sections[position]

        // Logika untuk memilih Fragment berdasarkan ID level dan section
        return when (levelId) {
            "level_1" -> {
                when (section.sectionId) {
                    "section_1" -> MaterialLevel1Fragment.newInstance(section)
                    "section_2" -> PracticeLevel1Fragment.newInstance(section)
                    else -> Fragment() // Placeholder
                }
            }
            "level_2" -> {
                when (section.sectionId) {
                    "section_1" -> MaterialLevel2Fragment.newInstance(section) // Gunakan Fragment baru
                    // "section_2" -> PracticeLevel2Fragment.newInstance(section) // (Jika Anda sudah membuatnya)
                    else -> Fragment() // Fallback
                }
            }
            // "level_2" -> { ... } // Tambahkan logika untuk level 2 di sini
            else -> Fragment() // Fragment default
        }
    }
}