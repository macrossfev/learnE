package com.learne.ui.stats

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
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
                binding.tvStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.mecha_gold))
                binding.tvIcon.text = when (item.type) {
                    "streak" -> "🔥"
                    "master" -> "🏅"
                    else -> "⭐"
                }
            } else {
                binding.tvStatus.text = "${item.progress}/${item.target}"
                binding.tvStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.text_hint))
                binding.tvIcon.text = "🔒"
            }
        }
    }
}