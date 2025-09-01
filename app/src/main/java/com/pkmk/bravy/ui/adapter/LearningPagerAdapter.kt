package com.pkmk.bravy.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pkmk.bravy.data.model.LearningSection
import com.pkmk.bravy.ui.view.practice.level1.MaterialLevel1Fragment
import com.pkmk.bravy.ui.view.practice.level1.PracticeLevel1Fragment

class LearningPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private var sections: List<LearningSection> = emptyList()

    fun setSections(sections: List<LearningSection>) {
        this.sections = sections
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = sections.size

    override fun createFragment(position: Int): Fragment {
        val section = sections[position]

        // Logika untuk memilih Fragment berdasarkan ID atau judul
        return when (section.sectionId) {
            "section_1" -> MaterialLevel1Fragment.newInstance(section)
            "section_2" -> PracticeLevel1Fragment.newInstance(section)
            // Tambahkan case untuk level dan section lain di sini
            else -> {
                // Fragment default atau error jika tidak ada yang cocok
                // Untuk sekarang, kita bisa asumsikan selalu ada yang cocok
                // atau buat fragment placeholder
                Fragment()
            }
        }
    }
}