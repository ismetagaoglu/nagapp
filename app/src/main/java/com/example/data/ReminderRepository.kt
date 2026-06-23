package com.example.data

import android.content.Context
import com.example.receiver.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class ReminderRepository(
    private val context: Context,
    private val reminderDao: ReminderDao
) {
    val activeReminders: Flow<List<Reminder>> = reminderDao.getActiveReminders()
    val completedReminders: Flow<List<Reminder>> = reminderDao.getCompletedReminders()

    suspend fun getReminderById(id: Int): Reminder? = reminderDao.getReminderById(id)

    suspend fun insertReminder(title: String, targetTime: Long, nagIntervalSeconds: Int = 60, repeatMode: String? = null): Int {
        val reminder = Reminder(
            title = title,
            targetTime = targetTime,
            nagIntervalSeconds = nagIntervalSeconds,
            repeatMode = repeatMode
        )
        val id = reminderDao.insertReminder(reminder).toInt()
        val savedReminder = reminder.copy(id = id)
        AlarmScheduler.scheduleAlarm(context, savedReminder)
        return id
    }

    suspend fun updateReminder(reminder: Reminder) {
        reminderDao.updateReminder(reminder)
        if (!reminder.isCompleted) {
            AlarmScheduler.scheduleAlarm(context, reminder)
        } else {
            AlarmScheduler.cancelAlarm(context, reminder.id)
        }
    }

    suspend fun completeReminder(id: Int) {
        val reminder = reminderDao.getReminderById(id) ?: return
        
        if (reminder.repeatMode != null) {
            // Schedule the next occurrence instead of completing
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = reminder.targetTime
            when (reminder.repeatMode) {
                "DAILY" -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                "WEEKLY" -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                "MONTHLY" -> cal.add(java.util.Calendar.MONTH, 1)
            }
            
            // If the next time is still in the past (e.g. user delayed completing it),
            // advance it to the next valid future date based on current time
            val now = System.currentTimeMillis()
            while (cal.timeInMillis <= now) {
                when (reminder.repeatMode) {
                    "DAILY" -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    "WEEKLY" -> cal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                    "MONTHLY" -> cal.add(java.util.Calendar.MONTH, 1)
                }
            }
            
            val updated = reminder.copy(
                targetTime = cal.timeInMillis,
                lastNaggedAt = 0L
            )
            reminderDao.updateReminder(updated)
            AlarmScheduler.scheduleAlarm(context, updated)
        } else {
            val updated = reminder.copy(isCompleted = true)
            reminderDao.updateReminder(updated)
            AlarmScheduler.cancelAlarm(context, id)
        }
    }

    suspend fun snoozeReminder(id: Int, minutes: Int) {
        val reminder = reminderDao.getReminderById(id) ?: return
        val updated = reminder.copy(
            targetTime = System.currentTimeMillis() + minutes * 60 * 1000L,
            lastNaggedAt = 0L // reset nag timing
        )
        reminderDao.updateReminder(updated)
        AlarmScheduler.scheduleAlarm(context, updated)
    }

    suspend fun deleteReminder(id: Int) {
        reminderDao.deleteReminderById(id)
        AlarmScheduler.cancelAlarm(context, id)
    }
}
