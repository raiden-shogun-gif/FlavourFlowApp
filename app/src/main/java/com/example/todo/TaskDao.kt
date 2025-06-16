package com.example.todo

import androidx.room.*


@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE date = :date")
    fun getTasksForDate(date: String): List<Task>

    @Insert
    fun insertTask(task: Task)

    @Update
    fun updateTask(task: Task)

    @Delete
    fun deleteTask(task: Task)

}