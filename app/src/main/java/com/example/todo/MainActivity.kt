package com.example.todo // Your actual package name

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.CalendarView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.todo.databinding.ActivityMainBinding // Assuming ViewBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName // Keep this if GeminiApiResponse is used
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var db: AppDatabase
    private lateinit var adapter: TaskAdapter
    private var selectedDate: String = ""
    private val REQUEST_CODE_SUGGESTED_TASKS = 1

    // Gemini API related (ensure these are correctly set up from your original code)
    private val APIKEY = "YOUR_GEMINI_API_KEY" // Replace with your actual Gemini API key
    private val client = OkHttpClient()
    private val gson = Gson()


    private val requestInternetPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                proceedWithNetworkAndPrompt()
            } else {
                Toast.makeText(this, "Internet permission denied. AI features unavailable.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(this)
        selectedDate = getDateString(binding.calendarView.date)

        adapter = TaskAdapter(emptyList(), { task, isChecked ->
            task.isCompleted = isChecked
            db.taskDao().updateTask(task)
        }, { task ->
            showDeleteConfirmationDialog(task)
        })

        binding.tasksRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.tasksRecyclerView.adapter = adapter

        loadTasks(selectedDate)

        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            loadTasks(selectedDate)
        }

        binding.addTaskFab.setOnClickListener {
            showAddTaskDialog()
        }

        // Compass button (aibutton) click listener
        binding.AIbutton.setOnClickListener {
            handleCompassButtonClick()
        }
    }

    private fun handleCompassButtonClick() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.INTERNET
            ) == PackageManager.PERMISSION_GRANTED -> {
                proceedWithNetworkAndPrompt()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.INTERNET) -> {
                showPermissionRationaleDialog()
            }
            else -> {
                requestInternetPermissionLauncher.launch(Manifest.permission.INTERNET)
            }
        }
    }

    private fun proceedWithNetworkAndPrompt() {
        if (isNetworkAvailable()) {
            showPromptDialog() // Network is available, show the text input dialog
        } else {
            showEnableNetworkDialog() // Network not available, ask user to enable
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }

    private fun showEnableNetworkDialog() {
        AlertDialog.Builder(this)
            .setTitle("Network Unavailable")
            .setMessage("Internet connection is required for AI features. Please enable network access.")
            .setPositiveButton("Enable Network") { dialog, _ ->
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Network access is required for this feature.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Needed")
            .setMessage("This feature requires internet access to provide AI suggestions. Please grant the permission.")
            .setPositiveButton("Grant") { dialog, _ ->
                requestInternetPermissionLauncher.launch(Manifest.permission.INTERNET)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Permission denied. AI features unavailable.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // This is your existing dialog to get text input from the user
    private fun showPromptDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("What do you need to do?") // Or any title you prefer
        val input = EditText(this)
        input.hint = "Describe your tasks for the AI"
        builder.setView(input)
        builder.setPositiveButton("Submit") { dialog, _ ->
            val prompt = input.text.toString().trim()
            if (prompt.isNotEmpty()) {
                fetchTasksFromAI(prompt) // Proceed with the AI call
            } else {
                Toast.makeText(this, "Input cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    // --- Functions from your original MainActivity that remain the same ---

    private fun fetchTasksFromAI(prompt: String) {
        // Use the correct API URL
        val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$APIKEY"

        // Build the JSON request body
        val requestJson = """
    {
      "contents": [{
        "parts":[{
          "text": "$prompt"
        }]
      }],
      "generationConfig": {
        "temperature": 0.9,
        "topK": 1,
        "topP": 1,
        "maxOutputTokens": 250,
        "stopSequences": []
      },
      "safetySettings": [
        {
          "category": "HARM_CATEGORY_HARASSMENT",
          "threshold": "BLOCK_MEDIUM_AND_ABOVE"
        },
        // ... (include other safety settings as per your original code or API docs)
      ]
    }
    """.trimIndent() // Ensure this JSON matches Gemini API requirements

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
                    Toast.makeText(this@MainActivity, "Failed to fetch tasks from AI: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBodyString = response.body?.string() // Read body once

                if (!response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "API error: ${response.code} - ${response.message}\nBody: $responseBodyString", Toast.LENGTH_LONG).show()
                    }
                    response.body?.close() // Close body if not read or error
                    return
                }

                if (responseBodyString == null) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Empty response from API", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                try {
                    // Adjusted parsing for common Gemini response structure
                    val jsonElement = JsonParser.parseString(responseBodyString)
                    val candidates = jsonElement.asJsonObject.getAsJsonArray("candidates")
                    if (candidates != null && candidates.size() > 0) {
                        val content = candidates[0].asJsonObject.getAsJsonObject("content")
                        val parts = content.getAsJsonArray("parts")
                        if (parts != null && parts.size() > 0) {
                            val generatedText = parts[0].asJsonObject.get("text").asString

                            val suggestedTasks = generatedText.lines()
                                .map { it.trim().removePrefix("-").trim() } // Clean up list items
                                .filter { it.isNotBlank() }

                            if (suggestedTasks.isEmpty()) {
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "No tasks generated by AI.", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                runOnUiThread {
                                    showSuggestedTasksDialog(suggestedTasks)
                                }
                            }
                        } else { throw Exception("No parts in content") }
                    } else { throw Exception("No candidates in response") }

                } catch (ex: Exception) {
                    ex.printStackTrace()
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to parse AI response: ${ex.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    response.body?.close() // Ensure body is closed
                }
            }
        })
    }


    private fun showSuggestedTasksDialog(suggestedTasks: List<String>) {
        val intent = Intent(this, SuggestedTasksActivity::class.java)
        intent.putStringArrayListExtra("suggested_tasks", ArrayList(suggestedTasks))
        intent.putExtra("selected_date", selectedDate)
        startActivityForResult(intent, REQUEST_CODE_SUGGESTED_TASKS) // Use the new Activity Result API if possible for new activities
    }

    // This is deprecated, consider using the new Activity Result API for starting SuggestedTasksActivity as well
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SUGGESTED_TASKS && resultCode == RESULT_OK) {
            loadTasks(selectedDate) // Reload tasks for the current date
            Toast.makeText(this, "Suggested tasks processed.", Toast.LENGTH_SHORT).show()
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

    // Data class GeminiApiResponse - Keep if you still need it for specific parsing,
    // otherwise, direct parsing as shown in fetchTasksFromAI is also possible.
    // data class GeminiApiResponse(
    //    @SerializedName("candidates") val candidates: List<Candidate>?
    // )
    // data class Candidate(...) // Define according to actual API response structure
}