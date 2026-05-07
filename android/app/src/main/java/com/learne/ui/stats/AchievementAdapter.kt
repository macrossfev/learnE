package com.learne.ui.stats

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.learne.R
import com.learne.data.model.Achievement
import com.learne.databinding.ItemAchievementBinding

class AchievementAdapter : RecyclerView.Adapter<AchievementAdapter.ViewHolder>() {

    private var items: List<Achievement> = emptyList()

    fun updateData(newItems: List<Achievement>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemAchievementBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemAchievementBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Achievement) {
            binding.tvTitle.text = item.title
            binding.tvDesc.text = item.description

            val progressPercent = (item.progress * 100 / item.target).coerceAtMost(100)
            binding.progressAchievement.max = 100
            binding.progressAchievement.progress = progressPercent

            if (item.unlocked) {
                binding.tvStatus.text = "已解锁"
                binding.tvStatus.setTextColor(Color.parseColor("#FFD700"))
                binding.tvIcon.text = when (item.type) {
                    "streak" -> "🔥"
                    "master" -> "🏅"
                    else -> "⭐"
                }
            } else {
                binding.tvStatus.text = "${item.progress}/${item.target}"
                binding.tvStatus.setTextColor(Color.parseColor("#808080"))
                binding.tvIcon.text = "🔒"
            }
        }
    }
}