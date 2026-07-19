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
            val lower = command.lowercase()
            val dkRegex = Regex("(\\d+)\\s*(dk|dakika)")
            val saatRegex = Regex("(\\d+)\\s*saat")

            var delayMins: Int? = null

            val dkMatch = dkRegex.find(lower)
            if (dkMatch != null) {
                delayMins = dkMatch.groupValues[1].toInt()
            } else {
                val saatMatch = saatRegex.find(lower)
                if (saatMatch != null) {
                    delayMins = saatMatch.groupValues[1].toInt() * 60
                }
            }

            if (delayMins == null) {
                if (lower.contains("yarım saat")) delayMins = 30
                else if (lower.contains("çeyrek saat")) delayMins = 15
                else if (lower.contains("bir saat")) delayMins = 60
                else delayMins = 10 // default 10 dk
            }
            
            var repeatMode: String? = null
            if (lower.contains("her gün") || lower.contains("hergün") || lower.contains("günlük")) {
                repeatMode = "DAILY"
            } else if (lower.contains("haftada bir") || lower.contains("haftalık")) {
                repeatMode = "WEEKLY"
            } else if (lower.contains("ayda bir") || lower.contains("aylık")) {
                repeatMode = "MONTHLY"
            }
            
            var title = command
            val toRemove = listOf(
                Regex("(\\d+)\\s*(dk|dakika|saat)\\s*(sonra)?", RegexOption.IGNORE_CASE),
                Regex("yarım saat\\s*(sonra)?", RegexOption.IGNORE_CASE),
                Regex("çeyrek saat\\s*(sonra)?", RegexOption.IGNORE_CASE),
                Regex("bir saat\\s*(sonra)?", RegexOption.IGNORE_CASE),
                Regex("her g[üu]n", RegexOption.IGNORE_CASE),
                Regex("herg[üu]n", RegexOption.IGNORE_CASE),
                Regex("g[üu]nl[üu]k", RegexOption.IGNORE_CASE),
                Regex("haftada bir", RegexOption.IGNORE_CASE),
                Regex("haftal[ıi]k", RegexOption.IGNORE_CASE),
                Regex("ayda bir", RegexOption.IGNORE_CASE),
                Regex("ayl[ıi]k", RegexOption.IGNORE_CASE)
            )
            for (regex in toRemove) {
                title = title.replace(regex, "")
            }
            
            title = title.replace("hatırlat", "", ignoreCase = true)
            title = title.replace("bana", "", ignoreCase = true)
            title = title.replace("için", "", ignoreCase = true)
            title = title.trim(' ', '\t', '\n', '\r', '"', '\'', ',', '.')
            
            if (title.isBlank()) {
                title = "Hızlı Hatırlatıcı"
            }
            
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
