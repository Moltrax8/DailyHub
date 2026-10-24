package com.moltrax.personalnoteapp.ui.screen.home

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.data.local.db.AppDatabase
import com.moltrax.personalnoteapp.data.local.db.entity.toEntity
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.repository.CategoryRepositoryImpl
import com.moltrax.personalnoteapp.data.repository.TaskRepositoryImpl
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.service.NotificationScheduler
import com.moltrax.personalnoteapp.service.NotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeViewModelOrderingTest {
    @Test fun missingCategoryOrdersUseLongMaxValueInTheActualHomeState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        var vm: HomeViewModel? = null
        try {
            val repo = TaskRepositoryImpl(db.taskDao(), db.taskCategoryDao(), db,
                NotificationScheduler(NotificationService(context), AppPreferences(context)))
            val categories = CategoryRepositoryImpl(db.categoryDao(), db.taskDao(), db.taskCategoryDao(), db)

            // This legacy-tagged task is visible to Home but has no category order.
            // Its low global order makes a global/zero fallback fail the assertion below.
            db.taskDao().upsert(Task(id = "missing", title = "Missing", sortOrder = -100,
                categoryNames = setOf("Work")).toEntity())
            repo.upsert(Task(id = "near-max", title = "Near max", sortOrder = 0,
                categoryNames = setOf("Work"), categoryOrders = mapOf("Work" to Long.MAX_VALUE - 1)))
            repo.upsert(Task(id = "at-max", title = "At max", sortOrder = 1,
                categoryNames = setOf("Work"), categoryOrders = mapOf("Work" to Long.MAX_VALUE)))
            assertTrue(repo.getById("missing")!!.categoryOrders.isEmpty())

            val home = withContext(Dispatchers.Main) {
                HomeViewModel(context, repo, categories).also { it.toggleCategory("wOrK") }
            }
            vm = home
            val state = withTimeout(5_000) {
                home.state.first { !it.isLoading && it.selectedCategories == setOf("wOrK") }
            }

            // The missing order ties with MAX, after MAX-1. Stable order keeps "missing"
            // before "at-max". A smaller fallback, exclusion, or no sorting fails this.
            assertEquals(listOf("near-max", "missing", "at-max"), state.tasks.map { it.id })
        } finally {
            withContext(Dispatchers.Main) { vm?.viewModelScope?.cancel() }
            db.close()
        }
    }
}
