package com.example.todo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.todo.databinding.ActivitySuggestedTasksBinding

class SuggestedTasksActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySuggestedTasksBinding
    private lateinit var suggestedTaskAdapter: SuggestedTaskAdapter
    private var originalTasks: ArrayList<String> = arrayListOf()
    private lateinit var selectedDate: String
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySuggestedTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.suggestedTasksToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = AppDatabase.getInstance(this)

        originalTasks = intent.getStringArrayListExtra("suggested_tasks") ?: arrayListOf()
        selectedDate = intent.getStringExtra("selected_date") ?: ""

        if (originalTasks.isEmpty() || selectedDate.isEmpty()) {
            Toast.makeText(this, "No tasks or date provided.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupRecyclerView()

        binding.addSelectedTasksButton.setOnClickListener {
            addRemainingTasksToDatabase()
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun setupRecyclerView() {
        val mutableTasks = ArrayList(originalTasks)

        suggestedTaskAdapter = SuggestedTaskAdapter(mutableTasks) { taskDescription, position ->
            AlertDialog.Builder(this)
                .setTitle("Delete Suggestion")
                .setMessage("Are you sure you want to remove '${taskDescription}' from the suggestions?")
                .setPositiveButton("Delete") { dialog, _ ->
                    suggestedTaskAdapter.removeItem(position)
                    Toast.makeText(this, "'$taskDescription' removed", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
        binding.suggestedTasksRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.suggestedTasksRecyclerView.adapter = suggestedTaskAdapter
    }

    private fun addRemainingTasksToDatabase() {
        val remainingTasks = suggestedTaskAdapter.getTasks()
        if (remainingTasks.isNotEmpty()) {
            for (taskDesc in remainingTasks) {
                val newTask = Task(date = selectedDate, description = taskDesc, isCompleted = false)
                db.taskDao().insertTask(newTask)
            }
            Toast.makeText(this, "${remainingTasks.size} tasks added to your list.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No tasks selected to add.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {

        if (suggestedTaskAdapter.itemCount != originalTasks.size) {
            AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. Do you want to add the remaining tasks before leaving?")
                .setPositiveButton("Add & Leave") { _, _ ->
                    addRemainingTasksToDatabase()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                .setNegativeButton("Discard & Leave") { _, _ ->
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
                .setNeutralButton("Stay") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
            return true
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return true
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        onSupportNavigateUp()
    }
}