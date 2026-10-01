package com.moltrax.personalnoteapp.domain.model

sealed interface SyncStatus {
    data object Idle    : SyncStatus
    data object Syncing : SyncStatus
    data object Synced  : SyncStatus
    data class  Error(val message: String) : SyncStatus
}
