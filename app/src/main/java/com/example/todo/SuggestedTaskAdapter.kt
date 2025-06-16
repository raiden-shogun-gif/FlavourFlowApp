package com.example.todo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SuggestedTaskAdapter(private val tasks: List<String>) :
    RecyclerView.Adapter<SuggestedTaskAdapter.TaskViewHolder>() {

    private val checkedStates = BooleanArray(tasks.size) { false }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suggested_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun getItemCount(): Int = tasks.size

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val taskDesc = tasks[position]
        holder.taskDescTextView.text = taskDesc
        holder.checkBox.isChecked = checkedStates[position]

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            checkedStates[position] = isChecked
        }

        holder.itemView.setOnClickListener {
            val newChecked = !checkedStates[position]
            checkedStates[position] = newChecked
            holder.checkBox.isChecked = newChecked
        }
    }

    fun getSelectedTasks(): List<String> {
        val selected = mutableListOf<String>()
        for (i in tasks.indices) {
            if (checkedStates[i]) {
                selected.add(tasks[i])
            }
        }
        return selected
    }

    class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.taskCheckBox)
        val taskDescTextView: TextView = itemView.findViewById(R.id.taskDescriptionTextView)
    }
}
