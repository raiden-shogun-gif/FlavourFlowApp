package com.example.todo

import android.os.Bundle
import android.widget.CalendarView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var tasksRecyclerView: RecyclerView
    private lateinit var addTaskFab: FloatingActionButton

    private lateinit var db: AppDatabase
    private lateinit var adapter: TaskAdapter

    private var selectedDate: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        calendarView = findViewById(R.id.calendarView)
        tasksRecyclerView = findViewById(R.id.tasksRecyclerView)
        addTaskFab = findViewById(R.id.addTaskFab)

        db = AppDatabase.getInstance(this)

        selectedDate = getDateString(calendarView.date)

        adapter = TaskAdapter(emptyList(), { task, isChecked ->
            task.isCompleted = isChecked
            db.taskDao().updateTask(task)
        }, { task ->
            showDeleteConfirmationDialog(task)
        })

        tasksRecyclerView.layoutManager = LinearLayoutManager(this)
        tasksRecyclerView.adapter = adapter

        loadTasks(selectedDate)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            loadTasks(selectedDate)
        }

        addTaskFab.setOnClickListener {
            showAddTaskDialog()
        }
    }

    private fun loadTasks(date: String) {
        val tasks = db.taskDao().getTasksForDate(date)
        adapter.updateList(tasks)
    }

    private fun showAddTaskDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Task for $selectedDate")

        val input = EditText(this)
        input.hint = "Task description"
        builder.setView(input)

        builder.setPositiveButton("Add") { dialog, _ ->
            val description = input.text.toString().trim()
            if (description.isNotEmpty()) {
                val newTask = Task(date = selectedDate, description = description, isCompleted = false)
                db.taskDao().insertTask(newTask)
                loadTasks(selectedDate)
            } else {
                Toast.makeText(this, "Task cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        builder.show()
    }

    private fun showDeleteConfirmationDialog(task: Task) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Delete Task")
        builder.setMessage("Are you sure you want to delete this task?")

        builder.setPositiveButton("Delete") { dialog, _ ->
            db.taskDao().deleteTask(task)
            loadTasks(selectedDate)
            Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        builder.show()
    }


    private fun getDateString(millis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(calendar.time)
    }
}