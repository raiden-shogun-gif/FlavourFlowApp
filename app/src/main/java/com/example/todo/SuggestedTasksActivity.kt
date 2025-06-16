package com.example.todo

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class SuggestedTasksActivity : AppCompatActivity() {

    private lateinit var tasksRecyclerView: RecyclerView
    private lateinit var confirmButton: FloatingActionButton
    private lateinit var db: AppDatabase
    private lateinit var adapter: TaskAdapter
    private var selectedDate: String = ""
    private var suggestedTasks: List<String> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_suggested_tasks)

        tasksRecyclerView = findViewById(R.id.suggestedTasksRecyclerView)
        confirmButton = findViewById(R.id.confirmButton)

        db = AppDatabase.getInstance(this)

        suggestedTasks = intent.getStringArrayListExtra("suggested_tasks") ?: listOf()
        selectedDate = intent.getStringExtra("selected_date") ?: ""

        adapter = TaskAdapter(suggestedTasks.map { Task(date = selectedDate, description = it, isCompleted = false) }, { _, _ -> }, { _ -> })
        tasksRecyclerView.layoutManager = LinearLayoutManager(this)
        tasksRecyclerView.adapter = adapter

        confirmButton.setOnClickListener {
            addTasksToDatabase()
        }
    }

    private fun addTasksToDatabase() {
        val tasksToInsert = adapter.getTasks()
        tasksToInsert.forEach { task ->
            db.taskDao().insertTask(task)
        }
        setResult(RESULT_OK)
        finish()
    }
}