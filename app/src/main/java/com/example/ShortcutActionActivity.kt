package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.ReminderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShortcutActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (intent.action == "com.example.ACTION_ADD_REMINDER") {
            val title = intent.getStringExtra("EXTRA_TITLE") ?: "Hatırlatıcı"
            val mins = intent.getIntExtra("EXTRA_MINS", 10)
            
            lifecycleScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val repo = ReminderRepository(applicationContext, db.reminderDao())
                val targetTime = System.currentTimeMillis() + mins * 60 * 1000L
                repo.insertReminder(title, targetTime, 60, null)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "'$title' $mins dk sonra için kuruldu!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        } else {
            finish()
        }
    }
}
