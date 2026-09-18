package axis.app.drawer

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import axis.app.data.SettingsStore
import axis.kernel.events.AxisEvent
import axis.kernel.events.EventBus
import axis.kernel.model.AppEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.statsDataStore by preferencesDataStore(name = "axis_stats")

/**
 * Installed-app inventory (spec §S3): launcher activity query, in-memory
 * index, icon LRU cache, launch counts (drives P1 suggestions; the
 * UsageStats-based predictor of §F17 lands in P4).
 *
 * Refresh triggers: init, [PackageChangeReceiver], and every launch (for
 * counts). TargetSdk 28 keeps `queryIntentActivities` unfiltered — no
 * `<queries>` block needed.
 */
@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val bus: EventBus
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pm: PackageManager get() = context.packageManager

    private val all = MutableStateFlow<List<AppEntry>>(emptyList())
    private val ready = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = ready.asStateFlow()

    /** Alphabetical, minus hidden apps. The drawer's single source of truth. */
    val visibleApps: StateFlow<List<AppEntry>> =
        combine(all, settings.hiddenApps) { list, hidden ->
            list.filter { it.packageName !in hidden }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Hidden apps, for the Appearance → Hidden apps manager. */
    val hiddenAppList: StateFlow<List<AppEntry>> =
        combine(all, settings.hiddenApps) { list, hidden ->
            list.filter { it.packageName in hidden }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val resolveMap = ConcurrentHashMap<String, ResolveInfo>()
    private val iconCache = LruCache<String, Drawable>(48)

    init {
        scope.launch { refresh() }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION") // Int-flags overload is correct for targetSdk 28.
        val resolved = pm.queryIntentActivities(intent, 0)
        val stats = context.statsDataStore.data.first()
        val entries = resolved
            .distinctBy { it.activityInfo?.packageName }
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                val label = ri.loadLabel(pm)?.toString()?.trim()
                    .takeUnless { it.isNullOrEmpty() } ?: pkg
                val flags = ri.activityInfo.applicationInfo?.flags ?: 0
                AppEntry(
                    packageName = pkg,
                    label = label,
                    launchCount = stats[longPreferencesKey("lc_$pkg")] ?: 0L,
                    lastLaunchedAt = stats[longPreferencesKey("ll_$pkg")] ?: 0L,
                    isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.label.lowercase() }
        resolveMap.clear()
        resolved.forEach { ri ->
            ri.activityInfo?.packageName?.let { resolveMap[it] = ri }
        }
        val pkgs = entries.map { it.packageName }.toSet()
        iconCache.snapshot().keys.filter { it !in pkgs }.forEach { iconCache.remove(it) }
        all.value = entries
        ready.value = true
    }

    /** Cached launcher icon, or null when the package has none / vanished. */
    fun iconFor(packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        val ri = resolveMap[packageName] ?: return null
        return try {
            ri.loadIcon(pm)?.also { iconCache.put(packageName, it) }
        } catch (_: Exception) {
            null
        }
    }

    /** Most-launched first, backfilled alphabetically (cold start). */
    fun suggested(count: Int): Flow<List<AppEntry>> = visibleApps.map { list ->
        val ranked = list.filter { it.launchCount > 0 }.sortedWith(
            compareByDescending<AppEntry> { it.launchCount }.thenByDescending { it.lastLaunchedAt }
        )
        (ranked + list.filter { it.launchCount == 0L }).take(count)
    }

    suspend fun launch(packageName: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
        return try {
            context.startActivity(intent)
            recordLaunch(packageName)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun uninstall(packageName: String) {
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:$packageName")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun webSearch(query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openWallpaperPicker() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private suspend fun recordLaunch(packageName: String) {
        val now = System.currentTimeMillis()
        context.statsDataStore.edit { p ->
            val countKey = longPreferencesKey("lc_$packageName")
            p[countKey] = (p[countKey] ?: 0L) + 1
            p[longPreferencesKey("ll_$packageName")] = now
        }
        all.update { list ->
            list.map {
                if (it.packageName == packageName) {
                    it.copy(launchCount = it.launchCount + 1, lastLaunchedAt = now)
                } else it
            }
        }
        bus.tryEmit(AxisEvent.AppInventoryChanged("launch:$packageName"))
    }
}
