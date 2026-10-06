package com.moltrax.personalnoteapp.ui.github

import com.moltrax.personalnoteapp.domain.model.GithubCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-shot bus for the `dailyhub://github-callback?code=...&state=...`
 * deep link. MainActivity emits; the Projects GitHub UI (or Settings row)
 * consumes, calls `finish`, then refreshes the connection state.
 */
object GitHubCallbackBus {
    private val _pending = MutableStateFlow<GithubCallback?>(null)
    val pending: StateFlow<GithubCallback?> = _pending.asStateFlow()

    fun emit(callback: GithubCallback) {
        _pending.value = callback
    }

    fun consume() {
        _pending.value = null
    }
}
