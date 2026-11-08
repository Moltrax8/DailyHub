package com.moltrax.personalnoteapp.ui.screen.task

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.local.db.AppDatabase
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.repository.CategoryRepositoryImpl
import com.moltrax.personalnoteapp.data.repository.TaskRepositoryImpl
import com.moltrax.personalnoteapp.data.repository.UpdateRepository
import com.moltrax.personalnoteapp.service.NotificationScheduler
import com.moltrax.personalnoteapp.service.NotificationService
import com.moltrax.personalnoteapp.ui.i18n.localizedConfiguration
import com.moltrax.personalnoteapp.ui.i18n.localizedFor
import com.moltrax.personalnoteapp.ui.screen.settings.SettingsViewModel
import com.moltrax.personalnoteapp.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TaskDetailAddFieldsTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val local = context.localizedFor("en")
    private lateinit var db: AppDatabase
    private lateinit var vm: TaskDetailViewModel
    private lateinit var settings: SettingsViewModel

    @Before fun showActualDetailScreen() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val prefs = AppPreferences(context)
        val service = NotificationService(context)
        val tasks = TaskRepositoryImpl(db.taskDao(), db.taskCategoryDao(), db, NotificationScheduler(service, prefs))
        val categories = CategoryRepositoryImpl(db.categoryDao(), db.taskDao(), db.taskCategoryDao(), db)
        runBlocking(Dispatchers.Main) {
            vm = TaskDetailViewModel(context, tasks, categories, SavedStateHandle())
            settings = SettingsViewModel(prefs, context, tasks, service, UpdateRepository(OkHttpClient(), prefs))
        }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides local,
                LocalConfiguration provides localizedConfiguration(context.resources.configuration, "en")) {
                AppTheme { TaskDetailScreen(rememberNavController(), "new", vm, settings) }
            }
        }
    }

    @After fun close(): Unit = runBlocking {
        withContext(Dispatchers.Main) {
            vm.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
            settings.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
        }
        db.close()
    }

    @Test fun categorySubmissionTrimsSelectsAndClosesOnlyTheCategoryForm() {
        val name = "Tag-${UUID.randomUUID().toString().take(8)}"
        val add = local.getString(R.string.task_add_category)
        val label = local.getString(R.string.task_new_category)
        compose.onNodeWithText(add).performScrollTo().performClick()
        compose.onNodeWithText(label).performScrollTo().performTextInput("   ")
        compose.onNodeWithContentDescription(add).assertIsNotEnabled()
        compose.onNodeWithText(label).performTextReplacement("  $name  ")
        compose.onNodeWithContentDescription(add).performClick()
        compose.waitUntil(5_000) { vm.state.value.categories == setOf(name) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText(name).assertIsSelected()
        compose.onNodeWithText(label).assertDoesNotExist()
        compose.onNodeWithText(add).performScrollTo().performClick()
        compose.onNodeWithText(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.runOnIdle { assertTrue(vm.state.value.subtasks.isEmpty()) }
    }

    @Test fun subtaskSubmissionTrimsAppendsAndClearsOnlyItsOwnText() {
        val label = local.getString(R.string.task_new_subtask)
        val add = local.getString(R.string.task_add_subtask)
        compose.onNodeWithText(label).performScrollTo().performTextInput("   ")
        compose.onNodeWithContentDescription(add).assertIsNotEnabled()
        compose.onNodeWithText(label).performTextReplacement("  First  ")
        compose.onNodeWithText(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("  First  ")))
        compose.onNodeWithContentDescription(add).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("First"), vm.state.value.subtasks.map { it.title }) }
        compose.onNodeWithText(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText(label).performScrollTo().performTextInput("Second")
        compose.onNodeWithContentDescription(add).performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf("First", "Second"), vm.state.value.subtasks.map { it.title })
            assertEquals(2, vm.state.value.subtasks.map { it.id }.toSet().size)
            assertTrue(vm.state.value.categories.isEmpty())
        }
    }
}
