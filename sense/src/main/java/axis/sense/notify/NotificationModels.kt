package axis.sense.notify

/** One captured notification (spec §F8). Text is truncated at capture. */
data class NotificationRecord(
    val key: String,
    val pkg: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val channelId: String?,
    val category: String?,
    val postedAt: Long,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val isGroupSummary: Boolean
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: appLabel
    val displayText: String get() = text?.takeIf { it.isNotBlank() } ?: ""
}

/** Quiet hours window (spec §F8). Minutes since midnight, wrap-around safe. */
data class QuietHours(
    val startMinute: Int,
    val endMinute: Int,
    val days: Set<Int> = (1..7).toSet(),
    val enabled: Boolean = false
) {
    fun isActive(nowMillis: Long): Boolean {
        if (!enabled) return false
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        val day = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1=Sunday
        val minutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val inDay = day in days
        return if (startMinute <= endMinute) {
            inDay && minutes in startMinute until endMinute
        } else {
            // Wraps midnight: either late evening or early morning.
            (inDay && minutes >= startMinute) || (inDay && minutes < endMinute)
        }
    }
}

/** User priorities that decide what breaks through (spec §F8). */
data class TriageRules(
    val priorityApps: Set<String> = emptySet(),
    val priorityContacts: Set<String> = emptySet(),
    val keywords: List<String> = emptyList(),
    val quietHours: QuietHours = QuietHours(22 * 60, 7 * 60, enabled = false),
    val silenceOngoing: Boolean = true
)

enum class Triage { BREAK_THROUGH, NORMAL, HOLD }

data class TriageResult(
    val verdict: Triage,
    val score: Int,
    val reason: String
)

/**
 * Scores a notification: is it important enough to surface now, or should it
 * wait in the Notification Center? Pure logic, unit-tested — the listener
 * service only feeds it records.
 */
object NotificationTriage {

    fun decide(record: NotificationRecord, rules: TriageRules, nowMillis: Long): TriageResult {
        val quiet = rules.quietHours.isActive(nowMillis)
        val text = ((record.title ?: "") + " " + (record.text ?: "")).lowercase()

        var score = 0
        val reasons = mutableListOf<String>()

        if (record.pkg in rules.priorityApps) {
            score += 40
            reasons += "priority app"
        }
        if (record.category == "msg" || record.category == "call" || record.category == "alarm") {
            score += 20
            reasons += "category ${record.category}"
        }
        val keyword = rules.keywords.firstOrNull { it.isNotBlank() && text.contains(it.lowercase()) }
        if (keyword != null) {
            score += 30
            reasons += "keyword \"$keyword\""
        }
        if (rules.priorityContacts.any { c -> text.contains(c.lowercase()) }) {
            score += 35
            reasons += "known contact"
        }
        if (record.isOngoing) {
            score -= if (rules.silenceOngoing) 30 else 0
            if (rules.silenceOngoing) reasons += "ongoing"
        }
        if (record.isGroupSummary) {
            score -= 10
            reasons += "group summary"
        }

        val verdict = when {
            score >= 40 -> Triage.BREAK_THROUGH
            quiet && score < 40 -> Triage.HOLD
            else -> Triage.NORMAL
        }
        val reason = if (reasons.isEmpty()) "no rules matched" else reasons.joinToString(", ")
        return TriageResult(verdict, score, if (quiet) "$reason · quiet hours" else reason)
    }
}
