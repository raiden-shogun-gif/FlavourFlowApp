package com.example.todo // Your package name

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.todo.databinding.ActivitySuggestedTasksBinding // Make sure this matches your XML file name

class SuggestedTasksActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySuggestedTasksBinding
    private lateinit var suggestedTaskAdapter: SuggestedTaskAdapter
    private var originalTasks: ArrayList<String> = arrayListOf()
    private lateinit var selectedDate: String
    private lateinit var db: AppDatabase // For adding tasks

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySuggestedTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.suggestedTasksToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Optional: for back navigation

        db = AppDatabase.getInstance(this)

        originalTasks = intent.getStringArrayListExtra("suggested_tasks") ?: arrayListOf()
        selectedDate = intent.getStringExtra("selected_date") ?: ""

        if (originalTasks.isEmpty() || selectedDate.isEmpty()) {
            Toast.makeText(this, "No tasks or date provided.", Toast.LENGTH_LONG).show()
            finish() // Close if no data
            return
        }

        setupRecyclerView()

        binding.addSelectedTasksButton.setOnClickListener {
            addRemainingTasksToDatabase()
            // Indicate that tasks were processed and MainActivity should reload
            setResult(Activity.RESULT_OK)
            finish() // Close this activity
        }
    }

    private fun setupRecyclerView() {
        // Use a mutable copy for the adapter so we can remove items
        val mutableTasks = ArrayList(originalTasks)

        suggestedTaskAdapter = SuggestedTaskAdapter(mutableTasks) { taskDescription, position ->
            // Show confirmation dialog before deleting
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
        // Handle the Toolbar's back button
        // Ask if user wants to discard changes or save them
        if (suggestedTaskAdapter.itemCount != originalTasks.size) { // Check if any tasks were deleted
            AlertDialog.Builder(this)
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved changes. Do you want to add the remaining tasks before leaving?")
                .setPositiveButton("Add & Leave") { _, _ ->
                    addRemainingTasksToDatabase()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
                .setNegativeButton("Discard & Leave") { _, _ ->
                    setResult(Activity.RESULT_CANCELED) // Or RESULT_OK if MainActivity should still refresh
                    finish()
                }
                .setNeutralButton("Stay") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
            return true // We've handled the event
        } else {
            setResult(Activity.RESULT_CANCELED) // No changes, or user chose to go back without saving
            finish()
            return true
        }
    }

    override fun onBackPressed() {
        // Similar logic to onSupportNavigateUp for the system back button
        onSupportNavigateUp()
    }
}