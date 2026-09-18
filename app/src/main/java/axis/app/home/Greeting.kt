package axis.app.home

import java.util.Calendar

/** Time-aware greeting for the home header (S2). Name arrives via chat in P3. */
fun greetingFor(hourOfDay: Int, name: String?): String {
    val part = when (hourOfDay) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    return if (name.isNullOrBlank()) part else "$part, $name"
}

fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
