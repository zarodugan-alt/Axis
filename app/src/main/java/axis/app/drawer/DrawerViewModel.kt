package axis.app.drawer

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import axis.app.data.SettingsStore
import axis.kernel.model.AppEntry
import axis.kernel.search.FuzzySearch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val repo: AppRepository,
    private val settings: SettingsStore
) : ViewModel() {

    val query = MutableStateFlow("")

    /** Visible apps filtered by the fuzzy query (≤100ms, in-memory). */
    val apps: StateFlow<List<AppEntry>> =
        combine(repo.visibleApps, query) { list, q ->
            FuzzySearch.filter(q, list) { it.label }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val iconSize: StateFlow<Int> = settings.drawerIconSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 48)

    val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setQuery(q: String) {
        query.value = q
    }

    fun iconFor(packageName: String): Drawable? = repo.iconFor(packageName)

    fun launch(packageName: String) {
        viewModelScope.launch { repo.launch(packageName) }
    }

    fun hide(packageName: String) {
        viewModelScope.launch { settings.setHidden(packageName, true) }
    }

    fun openAppInfo(packageName: String) = repo.openAppInfo(packageName)

    fun uninstall(packageName: String) = repo.uninstall(packageName)

    /** Notification counts arrive with the P2 listener; always 0 in P1. */
    @Suppress("UNUSED_PARAMETER") // P2 listener fills this in
    fun badgeCount(packageName: String): Int = 0
}
