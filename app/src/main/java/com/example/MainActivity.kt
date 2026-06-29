package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Reminder
import com.example.ui.NagViewModel
import com.example.ui.ParsingState
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val viewModel: NagViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle permissions
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Nag: Bildirim izni verildi!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Israrcı hatırlatıcılar için bildirim izni gereklidir.", Toast.LENGTH_LONG).show()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        handleIntent(intent)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    NagAppScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val query = when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                } else null
            }
            Intent.ACTION_VIEW -> {
                val data = intent.data
                if (data != null && data.scheme == "nag" && data.host == "create") {
                    data.getQueryParameter("q")
                } else if (data != null && data.scheme == "https" && data.host == "nag.example.com" && data.path == "/create") {
                    data.getQueryParameter("q")
                } else null
            }
            "android.intent.action.CREATE_NOTE" -> {
                intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            }
            else -> null
        }

        if (!query.isNullOrBlank()) {
            viewModel.parseAndCreateReminder(query) { success, msg ->
                runOnUiThread {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

// Custom Colors for Nag aesthetic - Geometric Balance Theme
val SlateDark = Color(0xFF1C1B1F)
val SlateDarkLight = Color(0xFF1C1B1F)
val SlateCard = Color(0xFF313033)
val AccentOrange = Color(0xFFD0BCFF) // Primary Accent: Lavender
val AccentRed = Color(0xFFF2B8B5)    // Overdue / Nag Alert: Pinkish Red
val AccentGreen = Color(0xFFD0BCFF)  // Matches main accent for uniform theme
val AccentBlue = Color(0xFFD0BCFF)   // Secondary Accent: Lavender
val SlateTextMuted = Color(0xFFCAC4D0)
val DarkPurpleText = Color(0xFF381E72)
val GeoBorderColor = Color(0xFF49454F)
val GeoSecondaryText = Color(0xFF938F99)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NagAppScreen(
    viewModel: NagViewModel,
    modifier: Modifier = Modifier
) {
    val activeReminders by viewModel.activeReminders.collectAsStateWithLifecycle()
    val completedReminders by viewModel.completedReminders.collectAsStateWithLifecycle()
    val parsingState by viewModel.parsingState.collectAsStateWithLifecycle()

    var reminderTitle by remember { mutableStateOf("") }
    var assistantQuery by remember { mutableStateOf("") }
    var nagIntervalSeconds by remember { mutableStateOf(60) } // Default 1 min
    var showCompleted by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf(15) } // Default custom duration of 15 min
    var customTargetTime by remember { mutableStateOf<Long?>(null) }
    var customRepeatMode by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Background gradient
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SlateDark, SlateDarkLight)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AccentOrange)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Nag Logo",
                        tint = DarkPurpleText,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "DURT",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Israrcı & Hızlı Hatırlatıcılar",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SlateTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 2: Quick Due-style Manual Setup Grid
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GeoBorderColor, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Hızlı Hatırlatıcı Kur",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = reminderTitle,
                                onValueChange = { reminderTitle = it },
                                placeholder = {
                                    Text(
                                        "Yapılacak iş / Hatırlatıcı başlığı...",
                                        color = SlateTextMuted,
                                        fontSize = 13.sp
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reminder_title_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentOrange,
                                    unfocusedBorderColor = GeoBorderColor,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            // Section 2.1: Custom Duration Selector with adjustment buttons
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Özel Süre Seçimi:",
                                fontSize = 11.sp,
                                color = SlateTextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(GeoBorderColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .border(1.dp, GeoBorderColor, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (customTargetTime != null) {
                                        Text(
                                            text = "Seçilen Zaman",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        val formatted = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(customTargetTime!!))
                                        Text(
                                            text = "$formatted zamanında çalacak",
                                            color = AccentOrange,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            text = "$customMinutes Dakika",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, customMinutes) }
                                        val formatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
                                        Text(
                                            text = "$formatted zamanında çalacak",
                                            color = AccentOrange,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        val calendar = Calendar.getInstance()
                                        android.app.DatePickerDialog(
                                            context,
                                            { _, year, month, dayOfMonth ->
                                                android.app.TimePickerDialog(
                                                    context,
                                                    { _, hourOfDay, minute ->
                                                        val targetCal = Calendar.getInstance().apply {
                                                            set(Calendar.YEAR, year)
                                                            set(Calendar.MONTH, month)
                                                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                            set(Calendar.MINUTE, minute)
                                                            set(Calendar.SECOND, 0)
                                                        }
                                                        if (targetCal.timeInMillis > System.currentTimeMillis()) {
                                                            customTargetTime = targetCal.timeInMillis
                                                        } else {
                                                            Toast.makeText(context, "Geçmiş bir zaman seçemezsiniz!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    calendar.get(Calendar.HOUR_OF_DAY),
                                                    calendar.get(Calendar.MINUTE),
                                                    true
                                                ).show()
                                            },
                                            calendar.get(Calendar.YEAR),
                                            calendar.get(Calendar.MONTH),
                                            calendar.get(Calendar.DAY_OF_MONTH)
                                        ).apply {
                                            datePicker.minDate = System.currentTimeMillis()
                                        }.show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentOrange,
                                        contentColor = DarkPurpleText
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Saat Seç",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tarih/Saat Seç", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Adjustment buttons Row (only when using minutes)
                            if (customTargetTime == null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        Pair("-10 dk", -10),
                                        Pair("-5 dk", -5),
                                        Pair("-1 dk", -1),
                                        Pair("+1 dk", 1),
                                        Pair("+5 dk", 5),
                                        Pair("+10 dk", 10)
                                    ).forEach { (label, diff) ->
                                        Button(
                                            onClick = {
                                                val newVal = customMinutes + diff
                                                customMinutes = if (newVal < 1) 1 else newVal
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = GeoBorderColor,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { customTargetTime = null },
                                    modifier = Modifier.fillMaxWidth().height(34.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GeoBorderColor,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Seçilen Zamanı İptal Et (Dakikaya Dön)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Repeat Mode Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Tekrar:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                listOf(
                                    Pair("Yok", null),
                                    Pair("Her Gün", "DAILY"),
                                    Pair("Haftada", "WEEKLY"),
                                    Pair("Ayda", "MONTHLY")
                                ).forEach { (label, mode) ->
                                    Button(
                                        onClick = { customRepeatMode = mode },
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (customRepeatMode == mode) AccentBlue else GeoBorderColor,
                                            contentColor = if (customRepeatMode == mode) Color.White else SlateTextMuted
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Custom setup Action Button
                            Button(
                                onClick = {
                                    val title = reminderTitle.ifBlank { "Özel Hatırlatıcı" }
                                    if (customTargetTime != null) {
                                        viewModel.insertReminderExact(title, customTargetTime!!, nagIntervalSeconds, customRepeatMode)
                                        Toast.makeText(context, "'$title' seçilen zamana kuruldu!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.insertReminder(title, customMinutes, nagIntervalSeconds, customRepeatMode)
                                        Toast.makeText(context, "'$title' $customMinutes dakika sonraya kuruldu!", Toast.LENGTH_SHORT).show()
                                    }
                                    reminderTitle = ""
                                    customTargetTime = null
                                    keyboardController?.hide()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("custom_add_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentOrange,
                                    contentColor = DarkPurpleText
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Kur", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SEÇİLEN SÜRE İLE HATIRLATICI KUR", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }

                            // Grid of Quick-Add Buttons
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Dokun ve Anında Kur (Alternatif):",
                                fontSize = 11.sp,
                                color = SlateTextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            val timeOffsets = listOf(
                                Pair("+1 dk", 1),
                                Pair("+5 dk", 5),
                                Pair("+10 dk", 10),
                                Pair("+15 dk", 15),
                                Pair("+30 dk", 30),
                                Pair("+1 sa", 60),
                                Pair("+2 sa", 120),
                                Pair("+4 sa", 240),
                                Pair("+1 gün", 1440)
                            )

                            // Simple Grid Implementation
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (row in 0 until 3) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (col in 0 until 3) {
                                            val index = row * 3 + col
                                            if (index < timeOffsets.size) {
                                                val (label, mins) = timeOffsets[index]
                                                Button(
                                                    onClick = {
                                                        val title = reminderTitle.ifBlank { "Hatırlatıcı ($label)" }
                                                        viewModel.insertReminder(title, mins, nagIntervalSeconds)
                                                        Toast.makeText(context, "'$title' $label sonraya kuruldu!", Toast.LENGTH_SHORT).show()
                                                        reminderTitle = ""
                                                        keyboardController?.hide()
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(42.dp)
                                                        .testTag("quick_add_$mins"),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = GeoBorderColor,
                                                        contentColor = Color.White
                                                    )
                                                ) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Nagging Frequency Selector
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(
                                        text = "Israr Sıklığı",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Tamamlanana kadar tekrarlanır",
                                        color = SlateTextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        Pair("1 dk", 60),
                                        Pair("5 dk", 300),
                                        Pair("10 dk", 600)
                                    ).forEach { (label, secs) ->
                                        val isSelected = nagIntervalSeconds == secs
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isSelected) AccentOrange else GeoBorderColor,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) Color.Transparent else GeoBorderColor,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    nagIntervalSeconds = secs
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) DarkPurpleText else SlateTextMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: Active Reminders
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Aktif Hatırlatıcılar (${activeReminders.size})",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (activeReminders.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Active",
                                tint = AccentOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (activeReminders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SlateCard.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Boş",
                                    tint = SlateTextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Aktif hatırlatıcı yok.",
                                    color = SlateTextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Üstten hızlıca veya asistanla kurun!",
                                    color = SlateTextMuted.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(activeReminders) { reminder ->
                        ActiveReminderCard(
                            reminder = reminder,
                            onComplete = { viewModel.completeReminder(reminder.id) },
                            onDelete = { viewModel.deleteReminder(reminder.id) },
                            onSnooze = { mins -> viewModel.snoozeReminder(reminder.id, mins) }
                        )
                    }
                }

                // Section 3.5: Settings & Assistant Guide
                item {
                    var isOverlayGranted by remember { mutableStateOf(true) }

                    // Periodically check overlay permission status
                    LaunchedEffect(Unit) {
                        while (true) {
                            isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                android.provider.Settings.canDrawOverlays(context)
                            } else {
                                true
                            }
                            kotlinx.coroutines.delay(2000)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GeoBorderColor, RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = SlateCard),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSettings = !showSettings },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Ayarlar",
                                        tint = AccentOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Uygulama Ayarları & Asistan Rehberi",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Expand Settings",
                                    tint = SlateTextMuted,
                                    modifier = Modifier.rotate(if (showSettings) 180f else 0f)
                                )
                            }

                            if (showSettings) {
                                Spacer(modifier = Modifier.height(16.dp))

                                // Overlay popup explanation and grant button
                                Text(
                                    text = "1. EKRAN ÜZERİNDE POPUP İZNİ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = AccentOrange
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Hatırlatıcıların uygulama açık değilken ekranda pencereli popup (diyalog) şeklinde açılabilmesi için 'Diğer uygulamaların üzerinde görüntüleme' izni gereklidir.",
                                    color = SlateTextMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isOverlayGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = "Status",
                                            tint = if (isOverlayGranted) Color(0xFF81C784) else Color(0xFFE57373),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isOverlayGranted) "İzin Verildi (Aktif)" else "İzin Eksik",
                                            color = if (isOverlayGranted) Color(0xFF81C784) else Color(0xFFE57373),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (!isOverlayGranted) {
                                        Button(
                                            onClick = {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    val intent = Intent(
                                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        android.net.Uri.parse("package:${context.packageName}")
                                                    )
                                                    context.startActivity(intent)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentOrange,
                                                contentColor = DarkPurpleText
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("İzin Ver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 4: Completed History
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCompleted = !showCompleted }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tamamlanan Geçmiş (${completedReminders.size})",
                            color = SlateTextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Expand",
                            tint = SlateTextMuted,
                            modifier = Modifier.rotate(if (showCompleted) 180f else 0f)
                        )
                    }
                }

                if (showCompleted) {
                    if (completedReminders.isEmpty()) {
                        item {
                            Text(
                                text = "Geçmiş temiz.",
                                color = SlateTextMuted.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    } else {
                        items(completedReminders) { reminder ->
                            CompletedReminderCard(
                                reminder = reminder,
                                onDelete = { viewModel.deleteReminder(reminder.id) },
                                onRecreate = {
                                    reminderTitle = reminder.title
                                    Toast.makeText(context, "Başlık kopyalandı! Yeni süre seçin.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Processing Overlay for Assistant Parsing
        if (parsingState is ParsingState.Processing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = AccentBlue)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Asistan Komutu İşliyor...",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sesli veya yazılı komut çözümlenip hatırlatıcı anında kuruluyor.",
                            color = SlateTextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveReminderCard(
    reminder: Reminder,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onSnooze: (Int) -> Unit
) {
    val currentTime = remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Live update time-left
    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = System.currentTimeMillis()
            kotlinx.coroutines.delay(5000)
        }
    }

    val isOverdue = currentTime.value > reminder.targetTime
    val timeLeftMs = reminder.targetTime - currentTime.value
    val timeLeftMinutes = (timeLeftMs / 60000L).toInt()

    // Pulse transition if overdue (insistent warning!)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val cardBorderBrush = if (isOverdue) {
        BorderStroke(2.dp, AccentRed.copy(alpha = borderAlpha))
    } else {
        BorderStroke(1.dp, GeoBorderColor)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                border = cardBorderBrush,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue) SlateCard.copy(alpha = 0.9f) else SlateCard
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isOverdue) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alarm",
                                tint = AccentRed,
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = reminder.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isOverdue) AccentRed else Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Time status label
                    val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reminder.targetTime))
                    val statusText = when {
                        isOverdue -> "Gecikti! Israrla çalıyor (Hedef: $timeString)"
                        timeLeftMinutes <= 0 -> "Hemen şimdi! (Hedef: $timeString)"
                        timeLeftMinutes < 60 -> "$timeLeftMinutes dk kaldı (Hedef: $timeString)"
                        else -> {
                            val hours = timeLeftMinutes / 60
                            val mins = timeLeftMinutes % 60
                            "$hours sa $mins dk kaldı (Hedef: $timeString)"
                        }
                    }
                    
                    Text(
                        text = statusText,
                        color = if (isOverdue) AccentRed.copy(alpha = 0.8f) else SlateTextMuted,
                        fontSize = 12.sp,
                        fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Medium
                    )

                    // Nagging details
                    val repeatStr = when (reminder.repeatMode) {
                        "DAILY" -> " • Her Gün"
                        "WEEKLY" -> " • Haftada Bir"
                        "MONTHLY" -> " • Ayda Bir"
                        else -> ""
                    }
                    Text(
                        text = "Israr sıklığı: ${reminder.nagIntervalSeconds} sn$repeatStr",
                        color = SlateTextMuted.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp).testTag("delete_${reminder.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = SlateTextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Done Button
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(38.dp)
                        .testTag("complete_${reminder.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentOrange,
                        contentColor = DarkPurpleText
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Done",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tamamlandı", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Snooze 5 min Button
                Button(
                    onClick = { onSnooze(5) },
                    modifier = Modifier
                        .weight(0.9f)
                        .height(38.dp)
                        .testTag("snooze_5_${reminder.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeoBorderColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+5 dk", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Snooze 15 min Button
                Button(
                    onClick = { onSnooze(15) },
                    modifier = Modifier
                        .weight(0.9f)
                        .height(38.dp)
                        .testTag("snooze_15_${reminder.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GeoBorderColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+15 dk", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CompletedReminderCard(
    reminder: Reminder,
    onDelete: () -> Unit,
    onRecreate: () -> Unit
) {
    val completedTimeString = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(reminder.targetTime))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, GeoBorderColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateCard.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onRecreate() }
            ) {
                Text(
                    text = reminder.title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = SlateTextMuted,
                    textDecoration = TextDecoration.LineThrough
                )
                Text(
                    text = "Tamamlandı: $completedTimeString",
                    color = SlateTextMuted.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Restore button icon
                IconButton(
                    onClick = onRecreate,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Yeniden Kur",
                        tint = AccentOrange.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = SlateTextMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
