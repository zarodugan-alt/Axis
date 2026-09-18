package axis.app.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import axis.app.data.SettingsStore
import axis.app.data.TransitionStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsStore
) : ViewModel() {
    val transitionStyle: StateFlow<TransitionStyle> = settings.transitionStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransitionStyle.CUBE)

    val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
}
