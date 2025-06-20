package com.example.todo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.todo.databinding.ItemSuggestedTaskBinding

class SuggestedTaskAdapter(
    private var suggestedTasks: MutableList<String>,
    private val onDeleteClicked: (taskDescription: String, position: Int) -> Unit
) : RecyclerView.Adapter<SuggestedTaskAdapter.SuggestedTaskViewHolder>() {

    inner class SuggestedTaskViewHolder(val binding: ItemSuggestedTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(taskDescription: String) {
            binding.suggestedTaskTextView.text = taskDescription
            binding.deleteSuggestedTaskButton.setOnClickListener {
                val currentPosition = adapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    onDeleteClicked(suggestedTasks[currentPosition], currentPosition)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestedTaskViewHolder {
        val binding = ItemSuggestedTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SuggestedTaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestedTaskViewHolder, position: Int) {
        holder.bind(suggestedTasks[position])
    }

    override fun getItemCount(): Int = suggestedTasks.size

    fun removeItem(position: Int) {
        if (position >= 0 && position < suggestedTasks.size) {
            suggestedTasks.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, suggestedTasks.size)
        }
    }

    fun getTasks(): List<String> {
        return suggestedTasks.toList()
    }
}