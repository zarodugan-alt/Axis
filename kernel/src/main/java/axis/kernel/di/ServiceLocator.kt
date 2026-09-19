package axis.kernel.di

import java.util.concurrent.ConcurrentHashMap

/**
 * Tiny service locator for the components Android instantiates itself —
 * `Service`, `NotificationListenerService`, `AccessibilityService`,
 * `BroadcastReceiver`. Those classes cannot take constructor injection
 * without wiring Hilt/KSP into every library module, and they must be
 * constructible by the framework.
 *
 * `:app` registers the singletons once in `Application.onCreate`; the
 * services read them back here. Everything is optional ([get] returns null)
 * so a service that starts before the app registers simply does nothing
 * instead of crashing the launcher process.
 */
object ServiceLocator {

    private val entries = ConcurrentHashMap<String, Any>()

    fun <T : Any> register(type: Class<T>, instance: T) {
        entries[key(type)] = instance
    }

    fun <T : Any> get(type: Class<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return entries[key(type)] as? T
    }

    fun clear() = entries.clear()

    private fun key(type: Class<*>): String = type.name
}

/** Convenience accessors used inside framework-instantiated components. */
inline fun <reified T : Any> locate(): T? = ServiceLocator.get(T::class.java)
