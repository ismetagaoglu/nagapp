package com.example

object CommandParser {
    data class ParsedCommand(
        val title: String,
        val delayMins: Int,
        val repeatMode: String?
    )

    fun parse(command: String): ParsedCommand {
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

        return ParsedCommand(title, delayMins, repeatMode)
    }
}
