package axis.app.home

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import axis.app.data.SettingsStore
import axis.app.drawer.AppRepository
import axis.kernel.model.AppEntry
import axis.kernel.search.FuzzySearch
import axis.ui.components.PriorityData
import axis.ui.components.SuggestionItem
import axis.ui.components.SuggestionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: AppRepository,
    val settings: SettingsStore
) : ViewModel() {

    val query = MutableStateFlow("")

    /** Live app results while typing (top 8); empty when idle. */
    val searchResults: StateFlow<List<AppEntry>> =
        combine(repo.visibleApps, query) { list, q ->
            if (q.isBlank()) emptyList()
            else FuzzySearch.filter(q, list) { it.label }.take(8)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val suggested: StateFlow<SuggestionState> =
        combine(repo.isReady, repo.suggested(5)) { ready, list ->
            if (!ready) SuggestionState.Loading
            else SuggestionState.Loaded(
                list.map { SuggestionItem(it.packageName, it.label, repo.iconFor(it.packageName)) }
            )
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SuggestionState.Loading)

    val greeting: StateFlow<String> =
        combine(settings.userName, minuteTicker) { name, _ ->
            greetingFor(currentHour(), name)
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            greetingFor(currentHour(), null)
        )

    /**
     * NEXT UP snapshot. Empty in P1 (calendar/priority/routines land in
     * P2–P4), so the card hides itself — never fake data.
     */
    val priority: StateFlow<PriorityData> =
        MutableStateFlow(PriorityData()).stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), PriorityData()
        )

    /** P1 is always Basic Mode (no providers); P3 drives this from the capability manifest. */
    val basicMode: StateFlow<Boolean> =
        MutableStateFlow(true).stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), true
        )

    val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setQuery(q: String) {
        query.value = q
    }

    fun iconFor(packageName: String): Drawable? = repo.iconFor(packageName)

    fun launch(packageName: String) {
        viewModelScope.launch { repo.launch(packageName) }
    }

    fun webSearch(q: String) = repo.webSearch(q)

    fun openWallpaperPicker() = repo.openWallpaperPicker()

    companion object {
        private val minuteTicker: Flow<Unit> = flow {
            while (true) {
                emit(Unit)
                delay(60_000)
            }
        }
    }
}
