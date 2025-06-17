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
import android.util.Log // For debugging API calls
import android.widget.CalendarView
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.todo.databinding.ActivityMainBinding
import com.google.gson.Gson
import com.google.gson.JsonParser
// Remove if not using @SerializedName directly in data classes here
// import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private lateinit var adapter: TaskAdapter
    private var selectedDate: String = ""

    // Replace with your actual Gemini API key from Google Cloud Console
    // Ensure this key is enabled for the "Generative Language API"
    private val APIKEY = "AIzaSyDg95sLvqJ2y80NRt-JnhD6ixPcxI7X7zU"

    private val client = OkHttpClient()
    private val gson = Gson()

    private val REQUEST_CODE_SUGGESTED_TASKS = 1 // Used with onActivityResult

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
        // Initialize selectedDate with current date from CalendarView
        selectedDate = getDateString(binding.calendarView.date)

        adapter = TaskAdapter(emptyList(), { task, isChecked ->
            task.isCompleted = isChecked
            db.taskDao().updateTask(task)
            // No need to reload all tasks here unless the UI needs a full refresh for other reasons
        }, { task ->
            showDeleteConfirmationDialog(task)
        })

        binding.tasksRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.tasksRecyclerView.adapter = adapter

        loadTasks(selectedDate)

        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            // Month is 0-indexed, so add 1
            selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            loadTasks(selectedDate)
        }

        binding.addTaskFab.setOnClickListener {
            showAddTaskDialog()
        }

        binding.AIbutton.setOnClickListener { // Assuming aibutton is your "compass" button
            handleAIFeatureClick()
        }
    }

    private fun handleAIFeatureClick() {
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
        if (APIKEY == "YOUR_GEMINI_API_KEY" || APIKEY.isBlank()) {
            Toast.makeText(this, "API Key is not configured.", Toast.LENGTH_LONG).show()
            Log.e("GeminiAPI", "API Key is missing. Please set it in MainActivity.")
            return
        }

        if (isNetworkAvailable()) {
            showPromptDialog()
        } else {
            showEnableNetworkDialog()
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

    private fun showPromptDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("What tasks do you need help with?")
        val input = EditText(this)
        input.hint = "e.g., 'chicken curry, fried rice'"
        builder.setView(input)
        builder.setPositiveButton("Get Suggestions") { dialog, _ ->
            val prompt = "give just the ingredients as individual list items for "+input.text.toString().trim()
            if (prompt.isNotEmpty()) {
                fetchTasksFromAI(prompt)
            } else {
                Toast.makeText(this, "Prompt cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun fetchTasksFromAI(prompt: String) {
        // IMPORTANT: Choose the correct model name and ensure your API key has access to it.
        // Common models: "gemini-pro", "gemini-1.5-flash-latest"
        // Verify the exact endpoint for the model you are using.
        val modelName = "gemini-1.5-flash-latest" // Or "gemini-pro"
        val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$APIKEY"

        val partsArray = listOf(mapOf("text" to prompt))
        val contentsArray = listOf(mapOf("parts" to partsArray))

        // Adjust generationConfig as needed
        val generationConfig = mapOf(
            "temperature" to 0.7,
            "topK" to 1,
            "topP" to 1.0,
            "maxOutputTokens" to 256, // Max tokens the model should generate
            "stopSequences" to emptyList<String>()
        )

        // Safety settings - adjust thresholds as needed (BLOCK_NONE, BLOCK_LOW_AND_ABOVE, BLOCK_MEDIUM_AND_ABOVE, BLOCK_ONLY_HIGH)
        val safetySettingsArray = listOf(
            mapOf("category" to "HARM_CATEGORY_HARASSMENT", "threshold" to "BLOCK_MEDIUM_AND_ABOVE"),
            mapOf("category" to "HARM_CATEGORY_HATE_SPEECH", "threshold" to "BLOCK_MEDIUM_AND_ABOVE"),
            mapOf("category" to "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold" to "BLOCK_MEDIUM_AND_ABOVE"),
            mapOf("category" to "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold" to "BLOCK_MEDIUM_AND_ABOVE")
        )

        val requestData = mapOf(
            "contents" to contentsArray,
            "generationConfig" to generationConfig,
            "safetySettings" to safetySettingsArray
        )

        val requestJson = gson.toJson(requestData)
        Log.d("GeminiAPI", "Request JSON: $requestJson") // For debugging

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(apiUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                Log.e("GeminiAPI", "API Call Failed: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "AI request failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBodyString = response.body?.string() // Read body once
                Log.d("GeminiAPI", "Response Code: ${response.code}")
                Log.d("GeminiAPI", "Response Body: $responseBodyString")

                if (!response.isSuccessful) {
                    var errorMessage = "API error: ${response.code} - ${response.message}"
                    if (!responseBodyString.isNullOrEmpty()) {
                        errorMessage += "\nDetails: $responseBodyString"
                        // Try to parse detailed error message if API provides it in JSON
                        try {
                            val errorJson = JsonParser.parseString(responseBodyString).asJsonObject
                            if (errorJson.has("error") && errorJson["error"].isJsonObject) {
                                val errorObj = errorJson.getAsJsonObject("error")
                                if (errorObj.has("message")) {
                                    errorMessage += "\nAPI Message: ${errorObj.get("message").asString}"
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("GeminiAPI", "Could not parse error response body as JSON.", e)
                        }
                    }
                    Log.e("GeminiAPI", errorMessage)
                    runOnUiThread { Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show() }
                    response.body?.close()
                    return
                }

                if (responseBodyString.isNullOrEmpty()) {
                    Log.w("GeminiAPI", "Empty response body from API.")
                    runOnUiThread { Toast.makeText(this@MainActivity, "Received empty response from AI.", Toast.LENGTH_LONG).show() }
                    return
                }

                try {
                    val jsonElement = JsonParser.parseString(responseBodyString)
                    if (!jsonElement.isJsonObject) {
                        throw Exception("Response is not a JSON object.")
                    }
                    val jsonObject = jsonElement.asJsonObject

                    if (!jsonObject.has("candidates") || !jsonObject.getAsJsonArray("candidates").isJsonArray) {
                        // Check for promptFeedback if no candidates
                        if (jsonObject.has("promptFeedback")) {
                            val promptFeedback = jsonObject.getAsJsonObject("promptFeedback")
                            if (promptFeedback.has("blockReason")) {
                                val blockReason = promptFeedback.get("blockReason").asString
                                throw Exception("Prompt was blocked. Reason: $blockReason")
                            }
                            // You can also check promptFeedback.safetyRatings here
                        }
                        throw Exception("No 'candidates' array found in AI response.")
                    }

                    val candidates = jsonObject.getAsJsonArray("candidates")
                    if (candidates.size() == 0) {
                        throw Exception("'candidates' array is empty.")
                    }

                    val firstCandidate = candidates[0].asJsonObject

                    // Check finishReason
                    if (firstCandidate.has("finishReason")) {
                        val finishReason = firstCandidate.get("finishReason").asString
                        if (finishReason != "STOP" && finishReason != "MAX_TOKENS") {
                            // Log safetyRatings if available
                            if (firstCandidate.has("safetyRatings")) {
                                Log.w("GeminiAPI", "Safety Ratings: ${firstCandidate.getAsJsonArray("safetyRatings")}")
                            }
                            throw Exception("AI generation stopped. Reason: $finishReason")
                        }
                    }


                    if (!firstCandidate.has("content") || !firstCandidate.getAsJsonObject("content").isJsonObject) {
                        throw Exception("No 'content' object in candidate.")
                    }
                    val content = firstCandidate.getAsJsonObject("content")

                    if (!content.has("parts") || !content.getAsJsonArray("parts").isJsonArray || content.getAsJsonArray("parts").size() == 0) {
                        throw Exception("No 'parts' array in content or it's empty.")
                    }
                    val parts = content.getAsJsonArray("parts")
                    val generatedText = parts[0].asJsonObject.get("text").asString

                    val suggestedTasks = generatedText.lines()
                        .mapNotNull { line ->
                            val trimmedLine = line.trim()
                            // Remove common list prefixes like "-", "*", "1.", etc.
                            trimmedLine.removePrefix("-").removePrefix("*").replaceFirst(Regex("^\\d+\\.\\s*"), "").trim()
                        }
                        .filter { it.isNotBlank() }

                    if (suggestedTasks.isEmpty()) {
                        runOnUiThread { Toast.makeText(this@MainActivity, "AI did not suggest any tasks.", Toast.LENGTH_LONG).show() }
                    } else {
                        runOnUiThread { showSuggestedTasksDialog(suggestedTasks) }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("GeminiAPI", "Failed to parse AI response: ${e.message}", e)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error processing AI response: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    response.body?.close()
                }
            }
        })
    }

    private fun showSuggestedTasksDialog(suggestedTasks: List<String>) {
        val intent = Intent(this, SuggestedTasksActivity::class.java)
        intent.putStringArrayListExtra("suggested_tasks", ArrayList(suggestedTasks))
        intent.putExtra("selected_date", selectedDate)
        // Consider using the Activity Result API if this is a new Activity interaction
        startActivityForResult(intent, REQUEST_CODE_SUGGESTED_TASKS)
    }

    // This is the older way to handle activity results.
    // For new interactions, prefer registerForActivityResult.
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SUGGESTED_TASKS && resultCode == RESULT_OK) {
            // Tasks were added or modified in SuggestedTasksActivity, reload for the current date.
            loadTasks(selectedDate)
            Toast.makeText(this, "Tasks updated from suggestions.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadTasks(date: String) {
        val tasks = db.taskDao().getTasksForDate(date)
        adapter.updateList(tasks) // Make sure TaskAdapter has an updateList method
    }

    private fun showAddTaskDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Grocery for $selectedDate")
        val input = EditText(this)
        input.hint = "Grocery Item"
        builder.setView(input)
        builder.setPositiveButton("Add") { dialog, _ ->
            val description = input.text.toString().trim()
            if (description.isNotEmpty()) {
                val newTask = Task(date = selectedDate, description = description) // isCompleted defaults to false
                db.taskDao().insertTask(newTask)
                loadTasks(selectedDate) // Refresh list
            } else {
                Toast.makeText(this, "Field cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun showDeleteConfirmationDialog(task: Task) {
        AlertDialog.Builder(this)
            .setTitle("Delete Item")
            .setMessage("Are you sure you want to delete '${task.description}'?")
            .setPositiveButton("Delete") { dialog, _ ->
                db.taskDao().deleteTask(task)
                loadTasks(selectedDate) // Refresh list
                Toast.makeText(this, "Item deleted", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun getDateString(millis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(calendar.time)
    }
}