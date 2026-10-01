package com.moltrax.personalnoteapp.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object Login
@Serializable object Home
@Serializable data class TaskDetail(val taskId: String = "new")
@Serializable object Profile
@Serializable object Settings
@Serializable data class FocusTimer(val taskId: String)
@Serializable object WorkoutList
@Serializable data class WorkoutDetail(val groupId: String)
@Serializable data class LiveWorkout(val workoutId: String, val groupId: String)
// Tamamlanan bir antrenman seansının özet/sonuç sayfası (set/tekrar/ağırlık detayları).
@Serializable data class WorkoutSummary(val sessionId: String)
