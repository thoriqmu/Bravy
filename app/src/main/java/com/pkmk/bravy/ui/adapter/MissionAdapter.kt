package com.pkmk.bravy.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pkmk.bravy.R
import com.pkmk.bravy.data.model.DailyMission
import com.pkmk.bravy.data.model.MissionType
import com.pkmk.bravy.databinding.ItemMissionBinding

class MissionAdapter(private val onMissionClick: (MissionType) -> Unit) :
    ListAdapter<DailyMission, MissionAdapter.MissionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MissionViewHolder {
        val binding = ItemMissionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MissionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MissionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MissionViewHolder(private val binding: ItemMissionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(mission: DailyMission) {
            binding.tvMissionTitle.text = mission.title
            binding.tvMissionDesc.text = mission.description

            val iconRes = when (mission.type) {
                MissionType.SPEAKING -> R.drawable.ic_voice
                MissionType.COMMUNITY -> R.drawable.ic_friends
                MissionType.CHAT -> R.drawable.ic_chat
            }
            binding.ivMissionIcon.setImageResource(iconRes)

            binding.ivMissionStatus.setImageResource(
                if (mission.isCompleted) R.drawable.ic_circle_check else R.drawable.ic_right
            )

            itemView.setOnClickListener { onMissionClick(mission.type) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DailyMission>() {
        override fun areItemsTheSame(old: DailyMission, new: DailyMission) = old.id == new.id
        override fun areContentsTheSame(old: DailyMission, new: DailyMission) = old == new
    }
}