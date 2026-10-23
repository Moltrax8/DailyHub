package com.moltrax.personalnoteapp.data.repository

import androidx.room.withTransaction
import com.moltrax.personalnoteapp.data.local.db.AppDatabase
import com.moltrax.personalnoteapp.data.local.db.dao.TaskCategoryDao
import com.moltrax.personalnoteapp.data.local.db.dao.TaskDao
import com.moltrax.personalnoteapp.data.local.db.entity.TaskCategoryCrossRef
import com.moltrax.personalnoteapp.data.local.db.entity.CategoryEntity
import com.moltrax.personalnoteapp.data.local.db.entity.TaskEntity
import com.moltrax.personalnoteapp.data.local.db.entity.toDomain
import com.moltrax.personalnoteapp.data.local.db.entity.toEntity
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.service.NotificationScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val xrefDao: TaskCategoryDao,
    private val db: AppDatabase,
    private val scheduler: NotificationScheduler,
) : TaskRepository {

    /** Attaches the live tag set + per-tag positions from xref (legacy fallback). */
    private suspend fun attach(entities: List<TaskEntity>): List<Task> {
        if (entities.isEmpty()) return emptyList()
        val byTask = xrefDao.getAllLinks().groupBy { it.taskId }
        return entities.map { e ->
            val links = byTask[e.id].orEmpty()
            val names = links.map { it.categoryName }.toSet()
                .ifEmpty { e.category?.takeIf { it.isNotBlank() }?.let(::setOf).orEmpty() }
            e.toDomain().copy(
                categoryNames = names,
                categoryOrders = links.associate { it.categoryName to it.sortOrder },
            )
        }
    }

    override fun observeAll(): Flow<List<Task>> = combine(dao.observeAll(), xrefDao.observeAllLinks()) { list, _ -> attach(list) }

    override fun observeTasksForCategory(name: String): Flow<List<Task>> =
        observeAll().map { list ->
            list.mapNotNull { task ->
                val order = task.categoryOrders.entries
                    .firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
                    ?: return@mapNotNull null
                task to order
            }.sortedBy { it.second }.map { it.first }
        }

    override fun observeTasksForCategories(names: Set<String>, matchAll: Boolean): Flow<List<Task>> =
        observeAll().map { list ->
            list.filter { task ->
                val hits = names.map { n -> task.categoryNames.any { it.equals(n, ignoreCase = true) } }
                if (matchAll) hits.all { it } else hits.any { it }
            }
        }

    override suspend fun getAll(): List<Task> = attach(dao.getAll())

    override suspend fun getUntagged(): List<Task> = attach(xrefDao.getUntagged())

    override suspend fun getById(id: String): Task? {
        val entity = dao.getById(id) ?: return null
        return attach(listOf(entity)).single()
    }

    override suspend fun upsert(task: Task) {
        db.withTransaction {
            val normalized = task.copy(categoryNames = canonicalTags(task.categoryNames))
            dao.upsert(normalized.toEntity())
            writeLinks(normalized)
        }
        // Single choke point: every mutation stages/cancels the alarm here,
        // so writers (UI, widget, focus timer) cannot forget it.
        scheduler.refresh(task)
    }

    override suspend fun delete(id: String) {
        db.withTransaction {
            xrefDao.deleteLinksForTask(id)
            dao.delete(id)
        }
        scheduler.cancel(id)
    }

    override suspend fun setTaskCategories(taskId: String, names: Set<String>) {
        db.withTransaction {
            val entity = dao.getById(taskId) ?: return@withTransaction
            val clean = canonicalTags(names)
            writeLinks(entity.toDomain().copy(categoryNames = clean, categoryOrders = emptyMap()))
            dao.upsert(entity.copy(category = clean.sorted().firstOrNull(), updatedAt = System.currentTimeMillis()))
        }
    }

    private suspend fun canonicalTags(names: Set<String>): Set<String> = names
        .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.map { name ->
            val categories = db.categoryDao()
            categories.getByName(name)?.name ?: name.also { categories.upsert(CategoryEntity(it)) }
        }.toSet()

    override suspend fun reorderInCategory(category: String, orderedIds: List<String>): Boolean {
        val current = xrefDao.getAllLinks()
            .filter { it.categoryName.equals(category, ignoreCase = true) }
            .sortedBy { it.sortOrder }
            .map { it.taskId }
        val requested = orderedIds.distinct().filter { it in current }
        val selected = requested.toSet()
        val iterator = requested.iterator()
        val merged = current.map { if (it in selected) iterator.next() else it }
        if (current == merged) return false
        val now = System.currentTimeMillis()
        db.withTransaction {
            xrefDao.reorderInCategory(category, merged)
            dao.touchUpdated(merged, now)
        }
        return true
    }

    override suspend fun reorderGlobal(orderedIds: List<String>): Boolean {
        val master = dao.getAll() // sorted by sortOrder ASC (DAO)
        val byId = master.associateBy { it.id }
        val displayedSet = orderedIds.toSet()
        val iter = orderedIds.iterator()
        // Rebuild the master order: put the new order into visible slots, keep their own id for hidden ones.
        // iter.hasNext() guard: if a task is concurrently deleted/added during the drag so master
        // and displayedIds no longer match (e.g. completion from the widget), stay on its own id
        // instead of throwing NoSuchElement.
        val newOrderIds = master.map { if (it.id in displayedSet && iter.hasNext()) iter.next() else it.id }

        val now = System.currentTimeMillis()
        val changed = newOrderIds.mapIndexedNotNull { index, id ->
            val task = byId[id] ?: return@mapIndexedNotNull null
            if (task.sortOrder != index.toLong()) task.copy(sortOrder = index.toLong(), updatedAt = now)
            else null
        }
        if (changed.isEmpty()) return false
        db.withTransaction { changed.forEach { dao.upsert(it) } }
        return true
    }

    /**
     * Rewrites a task's links from its tag set. Overlapping tags keep their
     * existing per-category position (edits must not reshuffle categories);
     * brand-new tags (and adopted orders) append at max+1.
     */
    private suspend fun writeLinks(task: Task) {
        val keep = xrefDao.getLinksForTask(task.id)
            .filter { link -> task.categoryNames.any { it.equals(link.categoryName, ignoreCase = true) } }
            .associate { it.categoryName to it.sortOrder }
        xrefDao.deleteLinksForTask(task.id)
        if (task.categoryNames.isEmpty()) return
        val nextBase = mutableMapOf<String, Long>()
        val now = System.currentTimeMillis()
        val links = task.categoryNames.sorted().map { name ->
            val order = keep.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
                ?: task.categoryOrders[name]
                ?: nextBase.getOrPut(name.lowercase()) { xrefDao.maxSortForCategory(name) + 1 }
                    .also { nextBase[name.lowercase()] = it + 1 }
            TaskCategoryCrossRef(task.id, name, order, now)
        }
        xrefDao.upsertLinks(links)
    }
}
