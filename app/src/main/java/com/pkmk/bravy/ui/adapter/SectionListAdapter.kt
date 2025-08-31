package com.pkmk.bravy.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.LearningSection
import com.pkmk.bravy.databinding.ItemSectionBinding

class SectionListAdapter(
    private val onSectionClick: (LearningSection, Int) -> Unit
) : RecyclerView.Adapter<SectionListAdapter.SectionViewHolder>() {

    private var sections = listOf<LearningSection>()
    private var currentSectionIndex = 0

    fun submitList(newSections: List<LearningSection>) {
        sections = newSections
        notifyDataSetChanged()
    }

    fun setCurrentSection(index: Int) {
        if (index != currentSectionIndex) {
            val previousIndex = currentSectionIndex
            currentSectionIndex = index
            notifyItemChanged(previousIndex)
            notifyItemChanged(currentSectionIndex)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding = ItemSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        holder.bind(sections[position], position)
    }

    override fun getItemCount(): Int = sections.size

    inner class SectionViewHolder(private val binding: ItemSectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(section: LearningSection, position: Int) {
            val context = itemView.context

            binding.tvSectionNumber.text = String.format("%02d", position + 1)
            binding.tvSectionTitle.text = section.title

            if (section.isLocked) {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_lock)
                val disabledColor = ContextCompat.getColor(context, R.color.surface)
                binding.ivStatusIcon.setColorFilter(disabledColor)
                binding.tvSectionNumber.setTextColor(disabledColor)
                binding.tvSectionTitle.setTextColor(disabledColor)
            } else {
                binding.ivStatusIcon.setImageResource(R.drawable.ic_play)
                val primaryColor = ContextCompat.getColor(context, R.color.primary)
                binding.ivStatusIcon.setColorFilter(primaryColor)
                binding.tvSectionNumber.setTextColor(primaryColor)
                binding.tvSectionTitle.setTextColor(ContextCompat.getColor(context, R.color.onBackground))
            }

            // Highlight the current section
            if (position == currentSectionIndex) {
                binding.root.setBackgroundColor(ContextCompat.getColor(context, R.color.primaryContainer))
            } else {
                binding.root.setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
            }

            itemView.setOnClickListener {
                if (!section.isLocked) {
                    onSectionClick(section, position)
                }
            }
        }
    }
}