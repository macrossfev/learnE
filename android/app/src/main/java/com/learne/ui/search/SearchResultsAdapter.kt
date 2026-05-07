package com.learne.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.learne.data.model.Word
import com.learne.databinding.ItemSearchResultBinding

class SearchResultsAdapter(
    private val onItemClick: (Word) -> Unit
) : RecyclerView.Adapter<SearchResultsAdapter.ViewHolder>() {

    private var items: List<Word> = emptyList()

    fun updateData(newItems: List<Word>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemSearchResultBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onItemClick)
    }

    override fun getItemCount() = items.size

    class ViewHolder(private val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(word: Word, onClick: (Word) -> Unit) {
            binding.tvWord.text = word.word
            binding.tvPhonetic.text = word.phonetic
            binding.tvMeaning.text = word.meaning
            binding.root.setOnClickListener { onClick(word) }
        }
    }
}