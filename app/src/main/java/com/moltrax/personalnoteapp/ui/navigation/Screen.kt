package com.moltrax.personalnoteapp.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object Login
// Managed-account sign-in (Phase 3 Supabase Auth; Drive uses Login).
@Serializable object SupabaseAuth
@Serializable object Home
@Serializable data class TaskDetail(val taskId: String = "new")
@Serializable object Profile
@Serializable object Settings
@Serializable data class FocusTimer(val taskId: String)
@Serializable object WorkoutList
@Serializable data class WorkoutDetail(val groupId: String)
@Serializable data class LiveWorkout(val workoutId: String, val groupId: String)
// Summary/result page of a completed workout session (set/rep/weight details).
@Serializable data class WorkoutSummary(val sessionId: String)

// Social graph (Phase 4+): friends, requests, per-user profile.
@Serializable object SocialGraph
@Serializable object FriendRequests
@Serializable data class UserProfile(val username: String)

// Shared spaces (Phase 5+): Duo hubs (Projects reuse the same routes later).
@Serializable data class DuoHub(val spaceId: String)
