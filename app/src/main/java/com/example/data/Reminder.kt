package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val targetTime: Long,
    val isCompleted: Boolean = false,
    val nagIntervalSeconds: Int = 60, // Default to nagging every 60 seconds (1 minute)
    val lastNaggedAt: Long = 0L,
    val repeatMode: String? = null // null, "DAILY", "WEEKLY", "MONTHLY"
)
