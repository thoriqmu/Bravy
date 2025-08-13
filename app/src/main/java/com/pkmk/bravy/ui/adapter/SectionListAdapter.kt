package com.pkmk.bravy.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.LearningSection
import com.pkmk.bravy.databinding.ItemSectionBinding

class SectionListAdapter(private val onSectionClick: (LearningSection) -> Unit) :
    ListAdapter<LearningSection, SectionListAdapter.SectionViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding = ItemSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SectionViewHolder(binding, onSectionClick)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SectionViewHolder(
        private val binding: ItemSectionBinding,
        private val onSectionClick: (LearningSection) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(section: LearningSection) {
            binding.tvSectionNumber.text = String.format("%02d", section.order)
            binding.tvSectionTitle.text = section.title

            if (section.isLocked) {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_lock) // Ganti dengan ikon gembok
                binding.root.alpha = 0.5f
                binding.root.isClickable = false
            } else {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_play)
                binding.root.alpha = 1.0f
                binding.root.isClickable = true
                binding.root.setOnClickListener { onSectionClick(section) }
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LearningSection>() {
            override fun areItemsTheSame(oldItem: LearningSection, newItem: LearningSection): Boolean =
                oldItem.sectionId == newItem.sectionId

            override fun areContentsTheSame(oldItem: LearningSection, newItem: LearningSection): Boolean =
                oldItem == newItem
        }
    }
}