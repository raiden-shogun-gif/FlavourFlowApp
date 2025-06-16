package com.example.todo

import android.content.Intent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import android.os.Bundle
import android.widget.CalendarView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import okhttp3.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var calendarView: CalendarView
    private lateinit var tasksRecyclerView: RecyclerView
    private lateinit var addTaskFab: FloatingActionButton
    private lateinit var AIbutton: FloatingActionButton

    private lateinit var db: AppDatabase
    private lateinit var adapter: TaskAdapter

    private var selectedDate: String = ""
    private val REQUEST_CODE_SUGGESTED_TASKS = 1 // Request code for SuggestedTasksActivity

    // Replace this with your actual Gemini API endpoint URL
    private val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"

    // Replace with your actual Gemini API key
    private val APIKEY = "AIzaSyB5ftQsXyJitXcAujPvzl9XRujxB1zVXq4"

    private val client = OkHttpClient()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AIbutton = findViewById(R.id.AIbutton)
        AIbutton.setOnClickListener {
            showPromptDialog()
        }

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

    private fun showPromptDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("What do you need to do?")
        val input = EditText(this)
        input.hint = "Describe your tasks"
        builder.setView(input)
        builder.setPositiveButton("Submit") { dialog, _ ->
            val prompt = input.text.toString().trim()
            if (prompt.isNotEmpty()) {
                fetchTasksFromAI(prompt)
            } else {
                Toast.makeText(this, "Input cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun fetchTasksFromAI(prompt: String) {
        // Use the correct API URL
        val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$APIKEY"

        // Build the JSON request body
        val requestJson = """
    {
      "prompt": {
        "textPrompt": {
          "content": "$prompt",
          "exampleCount": 0
        }
      },
      "candidateCount": 1,
      "maxOutputTokens": 250
    }
    """.trimIndent()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to fetch tasks from AI", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "API error: ${response.code} - ${response.message}", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                val responseBody = response.body?.string()
                if (responseBody == null) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Empty response from API", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                // Parse response JSON to extract generated text content
                try {
                    val generatedText = extractGeneratedTextFromResponse(responseBody)

                    // Convert generated text (e.g. line-separated tasks) into a list
                    val suggestedTasks = generatedText.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    if (suggestedTasks.isEmpty()) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "No tasks generated by AI", Toast.LENGTH_LONG).show()
                        }
                        return
                    }

                    runOnUiThread {
                        showSuggestedTasksDialog(suggestedTasks)
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to parse API response", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun extractGeneratedTextFromResponse(responseBody: String): String {
        val jsonElement = JsonParser.parseString(responseBody)
        val candidates = jsonElement.asJsonObject.getAsJsonArray("candidates")
        if (candidates.size() == 0) return ""

        val content = candidates[0].asJsonObject.get("content").asString
        return content
    }


    private fun showSuggestedTasksDialog(suggestedTasks: List<String>) {
        val intent = Intent(this, SuggestedTasksActivity::class.java)
        intent.putStringArrayListExtra("suggested_tasks", ArrayList(suggestedTasks))
        intent.putExtra("selected_date", selectedDate)  // pass date for inserting
        startActivityForResult(intent, REQUEST_CODE_SUGGESTED_TASKS)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SUGGESTED_TASKS && resultCode == RESULT_OK) {
            loadTasks(selectedDate)
            Toast.makeText(this, "Suggested tasks added", Toast.LENGTH_SHORT).show()
        }
    }

    // Data class to match Gemini API response JSON
    data class GeminiApiResponse(
        @SerializedName("tasks") val tasks: List<String>?
    )
}
