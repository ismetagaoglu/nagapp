package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.NagAlarmActivity
import com.example.data.AppDatabase
import com.example.data.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("REMINDER_ID", -1)
        if (reminderId == -1) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val reminder = db.reminderDao().getReminderById(reminderId)

                if (reminder != null && !reminder.isCompleted) {
                    // Update last nagged time and save
                    val updatedReminder = reminder.copy(lastNaggedAt = System.currentTimeMillis())
                    db.reminderDao().updateReminder(updatedReminder)

                    // Post/update notification
                    showNotification(context, updatedReminder)

                    // Schedule the next nag (insistent check)
                    val nextNagTime = System.currentTimeMillis() + (reminder.nagIntervalSeconds * 1000L)
                    val nextNagReminder = updatedReminder.copy(targetTime = nextNagTime)
                    AlarmScheduler.scheduleAlarm(context, nextNagReminder)
                    
                    Log.d("ReminderReceiver", "Nagged for reminder $reminderId, scheduled next in ${reminder.nagIntervalSeconds}s")
                } else {
                    Log.d("ReminderReceiver", "Reminder $reminderId is either null or already completed. Skipping.")
                }
            } catch (e: Exception) {
                Log.e("ReminderReceiver", "Error processing reminder alarm: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, reminder: Reminder) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "nag_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Nag Israrcı Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Zamanı dolan hatırlatıcılar için sesli ve ısrarcı bildirimler"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setBypassDnd(true)
                
                // Add default ringtone sound
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap notification to open app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NOTIFICATION_REMINDER_ID", reminder.id)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            reminder.id,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full screen intent for locked screens / Heads-up alerts
        val alarmActivityIntent = Intent(context, NagAlarmActivity::class.java).apply {
            putExtra("REMINDER_ID", reminder.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val alarmActivityPendingIntent = PendingIntent.getActivity(
            context,
            reminder.id * 10 + 3,
            alarmActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Tamamlandı" Action Button
        val doneIntent = Intent(context, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_DONE
            putExtra("REMINDER_ID", reminder.id)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id * 10 + 1,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "10 dk Ertele" Action Button
        val snoozeIntent = Intent(context, ActionReceiver::class.java).apply {
            action = ActionReceiver.ACTION_SNOOZE
            putExtra("REMINDER_ID", reminder.id)
            putExtra("SNOOZE_MINUTES", 10)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id * 10 + 2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Nag Hatırlatıcı!")
            .setContentText(reminder.title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(alarmActivityPendingIntent)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .addAction(android.R.drawable.ic_menu_view, "Tamamlandı", donePendingIntent)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "10 Dk Ertele", snoozePendingIntent)
            .setFullScreenIntent(alarmActivityPendingIntent, true) // Makes it show up as heads-up notification or lock screen popup

        notificationManager.notify(reminder.id, builder.build())
    }
}
