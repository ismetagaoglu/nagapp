package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.CommandParser
import com.example.data.AppDatabase
import com.example.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.ACTION_TASKER_REMINDER") {
            val textToParse = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra("EXTRA_TITLE")
                ?: ""

            val defaultMinsStr = intent.getStringExtra("EXTRA_MINS")
            val defaultMins = defaultMinsStr?.toIntOrNull() ?: intent.getIntExtra("EXTRA_MINS", 10)

            val parsed = if (textToParse.isNotBlank()) CommandParser.parse(textToParse) else CommandParser.ParsedCommand("Tasker Hatırlatıcısı", defaultMins, null)
            val title = parsed.title
            val mins = if (textToParse.isNotBlank()) parsed.delayMins else defaultMins
            val repeatMode = parsed.repeatMode

            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val repo = ReminderRepository(context, db.reminderDao())
                    
                    val targetTime = System.currentTimeMillis() + mins * 60 * 1000L
                    repo.insertReminder(title, targetTime, 60, repeatMode)
                    
                    val repeatStr = when (repeatMode) {
                        "DAILY" -> " (Her Gün)"
                        "WEEKLY" -> " (Haftada Bir)"
                        "MONTHLY" -> " (Ayda Bir)"
                        else -> ""
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "'$title' ($mins dk sonra)$repeatStr kuruldu!", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
