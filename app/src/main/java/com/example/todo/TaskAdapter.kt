package com.example.todo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView

class TaskAdapter(
    private var tasks: List<Task>,
    private val onCheckedChange: (Task, Boolean) -> Unit,
    private val onTaskDelete: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    fun getTasks(): List<Task> {
        return tasks
    }

    inner class TaskViewHolder(val view: View) : RecyclerView.ViewHolder(view){
        val deleteButton: ImageButton = itemView.findViewById(R.id.delete_button)
        val checkBox: CheckBox = itemView.findViewById(R.id.task_checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        holder.checkBox.text = task.description
        holder.checkBox.isChecked = task.isCompleted
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            onCheckedChange(task, isChecked)
        }
        holder.deleteButton.setOnClickListener {
            onTaskDelete(task)
        }
        fun onTaskDelete(task: Task) {
            val db = AppDatabase.getInstance(holder.itemView.context)
            db.taskDao().deleteTask(task)
            tasks = tasks.filterIndexed { index, _ -> index != position }
            notifyItemRemoved(position)
        }
    }

    override fun getItemCount(): Int = tasks.size

    fun updateList(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

}