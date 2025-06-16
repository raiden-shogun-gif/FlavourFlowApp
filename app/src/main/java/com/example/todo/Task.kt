package com.example.todo

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,       // Format YYYY-MM-DD, e.g. "2024-06-22"
    val description: String,
    var isCompleted: Boolean = false
)