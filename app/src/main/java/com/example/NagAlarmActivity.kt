package com.example

import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.Reminder
import com.example.data.ReminderRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NagAlarmActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var reminderId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force activity to show on top of lock screen and turn on screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        reminderId = intent.getIntExtra("REMINDER_ID", -1)

        startAlarm(this)

        setContent {
            MyApplicationTheme {
                NagAlarmScreen(
                    reminderId = reminderId,
                    onDismiss = {
                        stopAlarm()
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    private fun startAlarm(context: Context) {
        try {
            val prefs = context.getSharedPreferences("NagPrefs", Context.MODE_PRIVATE)
            val customUriStr = prefs.getString("custom_ringtone_uri", null)
            val isLooping = prefs.getBoolean("is_looping_sound", true)

            val soundUri = if (customUriStr != null) {
                android.net.Uri.parse(customUriStr)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            ringtone = RingtoneManager.getRingtone(context, soundUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = isLooping
            }
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun NagAlarmScreen(
    reminderId: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var reminder by remember { mutableStateOf<Reminder?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reminderId) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getInstance(context)
            reminder = db.reminderDao().getReminderById(reminderId)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE61C1B1F)), // Transparent slate dark
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .border(2.dp, Color(0xFFD0BCFF), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF313033)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Animated Pulsing Bell Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD0BCFF))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Alarm",
                        tint = Color(0xFF381E72),
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "ISRARCI NAG HATIRLATMA!",
                    color = Color(0xFFF2B8B5),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = reminder?.title ?: "Yükleniyor...",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tamamlanana kadar ısrarla çalmaya devam edecek.",
                    color = Color(0xFFCAC4D0),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Done/Tamamlandı Button
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabase.getInstance(context)
                                val repo = ReminderRepository(context, db.reminderDao())
                                repo.completeReminder(reminderId)
                            }
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD0BCFF),
                        contentColor = Color(0xFF381E72)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Done", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TAMAMLANDI", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Snooze Buttons inside the popup
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 5, 10).forEach { mins ->
                        Button(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val db = AppDatabase.getInstance(context)
                                        val repo = ReminderRepository(context, db.reminderDao())
                                        repo.snoozeReminder(reminderId, mins)
                                    }
                                    onDismiss()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF49454F),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Snooze", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("$mins Dk Ertele", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Custom Time Snooze Button
                Button(
                    onClick = {
                        val calendar = java.util.Calendar.getInstance()
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                android.app.TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val targetCal = java.util.Calendar.getInstance().apply {
                                            set(java.util.Calendar.YEAR, year)
                                            set(java.util.Calendar.MONTH, month)
                                            set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                                            set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                                            set(java.util.Calendar.MINUTE, minute)
                                            set(java.util.Calendar.SECOND, 0)
                                        }
                                        if (targetCal.timeInMillis > System.currentTimeMillis()) {
                                            val diffMins = ((targetCal.timeInMillis - System.currentTimeMillis()) / (60 * 1000)).toInt()
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val db = AppDatabase.getInstance(context)
                                                    val repo = ReminderRepository(context, db.reminderDao())
                                                    repo.snoozeReminder(reminderId, if (diffMins > 0) diffMins else 1)
                                                }
                                                onDismiss()
                                            }
                                        } else {
                                            android.widget.Toast.makeText(context, "Geçmiş bir zaman seçemezsiniz!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    calendar.get(java.util.Calendar.HOUR_OF_DAY),
                                    calendar.get(java.util.Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            calendar.get(java.util.Calendar.YEAR),
                            calendar.get(java.util.Calendar.MONTH),
                            calendar.get(java.util.Calendar.DAY_OF_MONTH)
                        ).apply {
                            datePicker.minDate = System.currentTimeMillis()
                        }.show()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF49454F),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = "Saat Seç", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Özel Tarih/Saate Ertele", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}
