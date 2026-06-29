package com.example

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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

class ShortcutConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (intent.action != Intent.ACTION_CREATE_SHORTCUT) {
            finish()
            return
        }

        setContent {
            var title by remember { mutableStateOf("") }
            var minsStr by remember { mutableStateOf("10") }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF141218))
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
                            text = "Rutinler İçin Hatırlatıcı Kur",
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
                                val t = title.ifBlank { "Rutin Hatırlatıcı" }
                                createShortcut(t, m)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF2B8B5),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Kaydet", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    private fun createShortcut(title: String, mins: Int) {
        val actionIntent = Intent(this, ShortcutActionActivity::class.java).apply {
            action = "com.example.ACTION_ADD_REMINDER"
            putExtra("EXTRA_TITLE", title)
            putExtra("EXTRA_MINS", mins)
        }
        
        val shortcutIntent = Intent().apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, actionIntent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, "$mins dk: $title")
            // Optional: icon
            putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this@ShortcutConfigureActivity, R.mipmap.ic_launcher))
        }
        
        setResult(Activity.RESULT_OK, shortcutIntent)
        finish()
    }
}
