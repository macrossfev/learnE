package com.learne.ui.wrong

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.learne.data.model.WrongWord
import com.learne.databinding.ItemWrongWordBinding

class WrongWordAdapter(
    private val onMarkCorrected: (WrongWord) -> Unit
) : RecyclerView.Adapter<WrongWordAdapter.ViewHolder>() {

    private var items: List<WrongWord> = emptyList()
    private var wordDetails: Map<String, com.learne.data.model.Word> = emptyMap()

    fun updateData(newItems: List<WrongWord>, details: Map<String, com.learne.data.model.Word>) {
        items = newItems
        wordDetails = details
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemWrongWordBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val wrongWord = items[position]
        val detail = wordDetails[wrongWord.word]
        holder.bind(wrongWord, detail, onMarkCorrected)
    }

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemWrongWordBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(wrong: WrongWord, detail: com.learne.data.model.Word?, onClick: (WrongWord) -> Unit) {
            binding.tvWord.text = wrong.word
            binding.tvWrongType.text = wrong.testType
            binding.tvWrongTimes.text = "错误${wrong.wrongCount}次"

            if (detail != null) {
                binding.tvMeaning.text = detail.meaning
            } else {
                binding.tvMeaning.text = ""
            }

            binding.btnMarkCorrected.setOnClickListener { onClick(wrong) }
        }
    }
}