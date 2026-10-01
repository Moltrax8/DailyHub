package com.moltrax.personalnoteapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.domain.model.SyncStatus
import com.moltrax.personalnoteapp.domain.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sync state kept at the app (Activity) level. Since SyncRepository is a @Singleton,
 * it shares the same state flow with all screens; so the sync banner can be shown
 * globally across all tabs.
 */
@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncRepo: SyncRepository,
) : ViewModel() {

    val syncStatus: StateFlow<SyncStatus> = syncRepo.syncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncStatus.Idle)

    /** User-triggered (pull-to-refresh / manual button) → show success in the UI. */
    fun sync() { viewModelScope.launch { syncRepo.sync(manual = true) } }

    /**
     * Lifecycle trigger (when the app comes to the foreground) → silent background
     * sync. No success message is shown; the repository only publishes success if the previous state was an ERROR
     * (recovery from error).
     */
    fun syncSilent() { viewModelScope.launch { syncRepo.sync(manual = false) } }

    /** Clear the shown "Synced" message. */
    fun acknowledge() = syncRepo.acknowledgeStatus()
}
