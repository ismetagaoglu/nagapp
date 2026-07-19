package com.example

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.ReminderRepository
import com.example.ui.NagViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class VoiceActivity : ComponentActivity() {

    companion object {
        private const val SPEECH_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, null)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ne hatırlatmamı istersiniz? (Örn: 5 dakika sonra ilaç içilecek)")
        }

        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Cihazınızda ses tanıma desteklenmiyor.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spokenText = results?.get(0) ?: ""

                if (spokenText.isNotBlank()) {
                    processVoiceCommand(spokenText)
                } else {
                    finish()
                }
            } else {
                finish() // Cancelled or failed
            }
        }
    }

    private fun processVoiceCommand(command: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val repository = ReminderRepository(applicationContext, db.reminderDao())
            
            // Re-using the parsing logic from ViewModel
            val parsed = CommandParser.parse(command)
            val delayMins = parsed.delayMins
            val title = parsed.title
            val repeatMode = parsed.repeatMode
            
            val targetTime = System.currentTimeMillis() + delayMins * 60 * 1000L
            repository.insertReminder(title, targetTime, 60, repeatMode)

            val repeatStr = when (repeatMode) {
                "DAILY" -> " (Her Gün)"
                "WEEKLY" -> " (Haftada Bir)"
                "MONTHLY" -> " (Ayda Bir)"
                else -> ""
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "'$title' ($delayMins dk sonra)$repeatStr kuruldu!", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
