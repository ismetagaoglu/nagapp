package com.example.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("REMINDER_ID", -1)
        if (reminderId == -1) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Immediately dismiss notification
        notificationManager.cancel(reminderId)

        val action = intent.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val repository = ReminderRepository(context, db.reminderDao())

                when (action) {
                    ACTION_DONE -> {
                        Log.d("ActionReceiver", "Completing reminder $reminderId from notification")
                        repository.completeReminder(reminderId)
                    }
                    ACTION_SNOOZE -> {
                        val minutes = intent.getIntExtra("SNOOZE_MINUTES", 10)
                        Log.d("ActionReceiver", "Snoozing reminder $reminderId for $minutes minutes")
                        repository.snoozeReminder(reminderId, minutes)
                    }
                }
            } catch (e: Exception) {
                Log.e("ActionReceiver", "Failed to handle notification action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.example.nag.ACTION_DONE"
        const val ACTION_SNOOZE = "com.example.nag.ACTION_SNOOZE"
    }
}
