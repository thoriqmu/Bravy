package com.pkmk.bravy.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pkmk.bravy.data.model.LearningLevel
import com.pkmk.bravy.databinding.ItemPracticeLevelBinding

class PracticeLevelAdapter(
    private val onLevelClick: (LearningLevel) -> Unit
) : RecyclerView.Adapter<PracticeLevelAdapter.LevelViewHolder>() {

    private val levels = mutableListOf<LearningLevel>()

    fun submitList(newLevels: List<LearningLevel>) {
        levels.clear()
        levels.addAll(newLevels)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LevelViewHolder {
        val binding = ItemPracticeLevelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LevelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LevelViewHolder, position: Int) {
        holder.bind(levels[position])
    }

    override fun getItemCount(): Int = levels.size

    inner class LevelViewHolder(private val binding: ItemPracticeLevelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(level: LearningLevel) {
            binding.tvLevelTitle.text = level.title
            binding.tvMinPoints.text = "Collect ${level.minPoints} points to unlock the level"

            // Load thumbnail
            level.thumbnailUrl?.let { url ->
                Glide.with(itemView.context)
                    .load(url)
                    .into(binding.ivLevelThumbnail)
            }

            if (level.isLocked) {
                binding.overlayLocked.visibility = View.VISIBLE
                binding.layoutLocked.visibility = View.VISIBLE
                binding.btnStartLearning.isEnabled = false
            } else {
                binding.overlayLocked.visibility = View.GONE
                binding.layoutLocked.visibility = View.GONE
                binding.btnStartLearning.isEnabled = true
            }

            itemView.setOnClickListener {
                if (!level.isLocked) {
                    onLevelClick(level)
                }
            }
        }
    }
}