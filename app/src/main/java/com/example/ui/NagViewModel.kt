package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Reminder
import com.example.data.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ParsingState {
    object Idle : ParsingState
    object Processing : ParsingState
    data class Success(val title: String) : ParsingState
    data class Error(val message: String) : ParsingState
}

class NagViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repository = ReminderRepository(application, db.reminderDao())

    val activeReminders: StateFlow<List<Reminder>> = repository.activeReminders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val completedReminders: StateFlow<List<Reminder>> = repository.completedReminders
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _parsingState = MutableStateFlow<ParsingState>(ParsingState.Idle)
    val parsingState: StateFlow<ParsingState> = _parsingState.asStateFlow()

    fun insertReminder(title: String, delayMinutes: Int, nagIntervalSeconds: Int = 60, repeatMode: String? = null) {
        viewModelScope.launch {
            val targetTime = System.currentTimeMillis() + delayMinutes * 60 * 1000L
            repository.insertReminder(title, targetTime, nagIntervalSeconds, repeatMode)
        }
    }

    fun insertReminderExact(title: String, targetTime: Long, nagIntervalSeconds: Int = 60, repeatMode: String? = null) {
        viewModelScope.launch {
            repository.insertReminder(title, targetTime, nagIntervalSeconds, repeatMode)
        }
    }

    fun completeReminder(id: Int) {
        viewModelScope.launch {
            repository.completeReminder(id)
        }
    }

    fun snoozeReminder(id: Int, minutes: Int) {
        viewModelScope.launch {
            repository.snoozeReminder(id, minutes)
        }
    }

    fun deleteReminder(id: Int) {
        viewModelScope.launch {
            repository.deleteReminder(id)
        }
    }

    fun resetParsingState() {
        _parsingState.value = ParsingState.Idle
    }

    data class ParsedCommand(val title: String, val delayMins: Int?, val repeatMode: String?)

    fun parseAndCreateReminder(query: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (query.isBlank()) return
        _parsingState.value = ParsingState.Processing
        viewModelScope.launch {
            val parsed = parseLocal(query)
            if (parsed != null) {
                _parsingState.value = ParsingState.Success(parsed.title)
                insertReminder(parsed.title, parsed.delayMins ?: 10, repeatMode = parsed.repeatMode)
                
                val repeatStr = when (parsed.repeatMode) {
                    "DAILY" -> " (Her Gün)"
                    "WEEKLY" -> " (Haftada Bir)"
                    "MONTHLY" -> " (Ayda Bir)"
                    else -> ""
                }
                
                onResult(true, "'${parsed.title}' (${parsed.delayMins} dk sonra)$repeatStr kuruldu!")
            } else {
                _parsingState.value = ParsingState.Error("Anlaşılmadı.")
                onResult(false, "Süre anlaşılamadı. Lütfen '10 dakika sonra çayı kapat' şeklinde belirtin.")
            }
        }
    }

    private fun parseLocal(query: String): ParsedCommand? {
        val lower = query.lowercase()
        // Simple regex to extract numbers followed by "dk", "dakika", "saat"
        val dkRegex = Regex("(\\d+)\\s*(dk|dakika)")
        val saatRegex = Regex("(\\d+)\\s*(saat)")
        
        var delayMins = -1
        
        val dkMatch = dkRegex.find(lower)
        if (dkMatch != null) {
            delayMins = dkMatch.groupValues[1].toInt()
        } else {
            val saatMatch = saatRegex.find(lower)
            if (saatMatch != null) {
                delayMins = saatMatch.groupValues[1].toInt() * 60
            }
        }
        
        if (delayMins <= 0) {
            if (lower.contains("yarım saat")) delayMins = 30
            else if (lower.contains("çeyrek saat") || lower.contains("15 dk")) delayMins = 15
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
        
        // Clean up the query to get a title
        var title = query
        // Remove the time portion from title roughly
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
        
        return ParsedCommand(title, delayMins, repeatMode)
    }
}
