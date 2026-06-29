package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            val mins = intent.getStringExtra("EXTRA_MINS")?.toIntOrNull() 
                ?: intent.getIntExtra("EXTRA_MINS", 10)
            
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
        } else if (intent.action == "com.example.ACTION_ADD_CUSTOM" || intent.action == Intent.ACTION_SEND) {
            setContent {
                val initialTitle = intent.getStringExtra(Intent.EXTRA_TEXT) 
                    ?: intent.getStringExtra("EXTRA_TITLE") 
                    ?: ""
                val initialMins = intent.getStringExtra("EXTRA_MINS")?.toIntOrNull()?.toString()
                    ?: (if (intent.hasExtra("EXTRA_MINS")) intent.getIntExtra("EXTRA_MINS", 10).toString() else "10")

                var title by remember { mutableStateOf(initialTitle) }
                var minsStr by remember { mutableStateOf(initialMins) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF211F26)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "Özel Hatırlatıcı Kur",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("Ne Hatırlatılacak?", color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFF2B8B5),
                                    unfocusedBorderColor = Color.Gray
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = minsStr,
                                onValueChange = { minsStr = it },
                                label = { Text("Kaç Dakika Sonra?", color = Color.Gray) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFF2B8B5),
                                    unfocusedBorderColor = Color.Gray
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    val m = minsStr.toIntOrNull() ?: 10
                                    val t = title.ifBlank { "Özel Hatırlatıcı" }
                                    
                                    lifecycleScope.launch {
                                        val db = AppDatabase.getInstance(applicationContext)
                                        val repo = ReminderRepository(applicationContext, db.reminderDao())
                                        val targetTime = System.currentTimeMillis() + m * 60 * 1000L
                                        repo.insertReminder(t, targetTime, 60, null)
                                        
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(applicationContext, "'$t' $m dk sonra için kuruldu!", Toast.LENGTH_SHORT).show()
                                            finish()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF2B8B5),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Kur", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            finish()
        }
    }
}
