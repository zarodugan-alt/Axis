package axis.app.chat

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tiny carrier for "the user typed something on the home terminal and hit
 * ask" — the prompt survives the navigation to the chat screen, which
 * consumes it exactly once.
 */
object ChatHandoff {
    val prompt = MutableStateFlow<String?>(null)

    fun take(): String? {
        val value = prompt.value
        prompt.value = null
        return value
    }
}
