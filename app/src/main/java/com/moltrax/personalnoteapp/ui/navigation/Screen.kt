package com.moltrax.personalnoteapp.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object Home
@Serializable data class TaskDetail(val taskId: String = "new")
@Serializable object Notes
@Serializable data class NoteDetail(val noteId: String = "new")
@Serializable object Calendar
@Serializable object FocusHub
@Serializable data class FocusTimer(val taskId: String)
@Serializable object Settings
