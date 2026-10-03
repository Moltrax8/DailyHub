-- ============================================================================
-- DailyHub — SQLite schema (Room AppDatabase v20, `personal_note_app.db`)
-- ============================================================================
-- Source: app/src/main/java/com/moltrax/personalnoteapp/data/local/db/
--   AppDatabase.kt, Converters.kt, entity/*.kt, dao/*.kt
--   Wired in di/DatabaseModule.kt via Room.databaseBuilder(...).addMigrations(...)
--
-- Conventions (Room -> SQLite):
--   String / JSON-in-TEXT  -> TEXT      (JSON lists default to '[]')
--   Long / Int / epoch-ms  -> INTEGER
--   Boolean                -> INTEGER 0/1 (NOT NULL where the column is required)
--   Double (inside JSON)   -> REAL in JSON payload, never a column
--   Nullable Kotlin field  -> nullable column, except JSON lists (NOT NULL '[]')
--
-- History (19 migrations 1->20, see AppDatabase.kt):
--   1->2  tasks.linkedWorkoutId added
--   2->3  food_entries created            (later dropped in 15->16)
--   3->4  vault_entries.salt added        (table later dropped in 4->5)
--   4->5  vault_entries dropped (vault feature removed)
--   5->6  tasks.sortOrder added, backfilled to -createdAt
--   6->7  categories created + seeded from tasks.category
--   7->8  food_entries.imagePath added    (table later dropped)
--   8->9  food_entries.isDeleted added    (table later dropped)
--   9->10 workout_exercises.type added, default 'WEIGHTLIFTING'
--   10->11 tasks.linkedProgramId + programStartIndex added
--   11->12 body_weight_entries created    (later dropped in 16->17)
--   12->13 tasks.isPenalty added (gamification removed; always 0, kept for compat)
--   13->14 tasks.recurrenceType / recurrenceDaysOfWeek / subtasks added
--   14->15 workout_groups.updatedAt + isDeleted added (LWW + tombstone)
--   15->16 food_entries dropped (nutrition feature removed)
--   16->17 workout_sessions.taskId added + body_weight_entries dropped
--   17->18 isDeleted tombstones added to tasks / categories / workout_sessions
--   18->19 indices on tasks.category, tasks.linkedWorkoutId, tasks.linkedProgramId,
--           workout_sessions.workoutId, workout_sessions.taskId
--   19->20 task_category_cross_ref created + backfilled from tasks.category
--           (legacy tasks.category column kept one release as read-fallback)
--
-- Dropped tables (intentionally absent below): vault_entries, food_entries,
-- body_weight_entries. Do NOT reintroduce without a new migration.
-- ============================================================================

PRAGMA foreign_keys = ON;

-- ----------------------------------------------------------------------------
-- tasks: TaskEntity (dao: TaskDao)
-- Default list order: ORDER BY sortOrder ASC, createdAt DESC.
-- subtasks / recurrenceDaysOfWeek are JSON TEXT via Converters (kotlinx).
-- isPenalty is deprecated: always 0, retained only for backward compat.
-- linkedWorkoutId and linkedProgramId are mutually exclusive (app-enforced).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tasks (
    id                    TEXT    NOT NULL PRIMARY KEY,
    title                 TEXT    NOT NULL,
    notes                 TEXT,
    dueDate               INTEGER,
    priority              TEXT    NOT NULL,             -- Priority.name: LOW|MEDIUM|HIGH
    isDone                INTEGER NOT NULL,             -- 0/1
    isRecurring           INTEGER NOT NULL,             -- 0/1
    intervalDays          INTEGER,                      -- legacy cadence, null = none
    recurrenceType        TEXT,                         -- RecurrenceType.name or NULL (legacy interval)
    recurrenceDaysOfWeek  TEXT    NOT NULL DEFAULT '[]',-- JSON [1..7], [] = task's own weekday
    focusDurationSeconds  INTEGER NOT NULL,             -- default 1500
    category              TEXT,                         -- natural key into categories.name
    subtasks              TEXT    NOT NULL DEFAULT '[]',-- JSON [{id,title,isDone}]
    createdAt             INTEGER NOT NULL,             -- epoch-ms
    updatedAt             INTEGER NOT NULL,             -- epoch-ms, LWW clock for Drive sync
    completedAt           INTEGER,                      -- epoch-ms
    linkedWorkoutId       TEXT,                         -- single workout link
    linkedProgramId       TEXT,                         -- program (WorkoutGroup) link
    programStartIndex     INTEGER NOT NULL DEFAULT 0,
    sortOrder             INTEGER NOT NULL DEFAULT 0,   -- ASC; new tasks get MIN(sortOrder)-1
    isPenalty             INTEGER NOT NULL DEFAULT 0,   -- deprecated, always 0
    isDeleted             INTEGER NOT NULL DEFAULT 0    -- 0/1 tombstone (v18): hidden locally, synced
);

CREATE INDEX IF NOT EXISTS index_tasks_category ON tasks (category);
CREATE INDEX IF NOT EXISTS index_tasks_linkedWorkoutId ON tasks (linkedWorkoutId);
CREATE INDEX IF NOT EXISTS index_tasks_linkedProgramId ON tasks (linkedProgramId);

-- ----------------------------------------------------------------------------
-- task_category_cross_ref: per-category positions (dao: TaskCategoryDao, v20)
-- Same task may be #1 in one category and #4 in another. categoryName keeps
-- display casing; all lookups compare NOCASE. Backfilled from tasks.category
-- in 19->20; the legacy column is dual-written but no longer consulted.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS task_category_cross_ref (
    taskId      TEXT    NOT NULL,
    categoryName TEXT   NOT NULL,
    sortOrder   INTEGER NOT NULL DEFAULT 0,
    addedAt     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (taskId, categoryName)
);

CREATE INDEX IF NOT EXISTS index_task_category_cross_ref_taskId ON task_category_cross_ref (taskId);
CREATE INDEX IF NOT EXISTS index_task_category_cross_ref_categoryName ON task_category_cross_ref (categoryName);

-- ----------------------------------------------------------------------------
-- categories: CategoryEntity (dao: CategoryDao)
-- name is the primary + natural key; tasks.category references it by value
-- (no FK constraint — renames go through TaskDao.reassignCategory()).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    name        TEXT    NOT NULL PRIMARY KEY,
    isPermanent INTEGER NOT NULL DEFAULT 0,  -- 0 = auto-created temporary, 1 = user-pinned
    isDeleted   INTEGER NOT NULL DEFAULT 0   -- 0/1 tombstone (v18): hidden locally, synced
);

-- ----------------------------------------------------------------------------
-- workout_groups: WorkoutGroupEntity (dao: WorkoutDao)
-- updatedAt = last-write-wins clock (backfilled from createdAt in 14->15).
-- isDeleted = tombstone: hidden from visible queries, INCLUDED in sync payload
-- so deletes propagate instead of resurrecting from Drive.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workout_groups (
    id          TEXT    NOT NULL PRIMARY KEY,
    name        TEXT    NOT NULL,
    currentIndex INTEGER NOT NULL,
    createdAt   INTEGER NOT NULL,             -- epoch-ms
    updatedAt   INTEGER NOT NULL DEFAULT 0,   -- epoch-ms LWW clock
    isDeleted   INTEGER NOT NULL DEFAULT 0    -- 0/1 tombstone
);

-- ----------------------------------------------------------------------------
-- workouts: WorkoutEntity (FK groupId -> workout_groups.id CASCADE)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workouts (
    id         TEXT    NOT NULL PRIMARY KEY,
    groupId    TEXT    NOT NULL REFERENCES workout_groups (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    orderIndex INTEGER NOT NULL,
    createdAt  INTEGER NOT NULL,
    updatedAt  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS index_workouts_groupId ON workouts (groupId);

-- ----------------------------------------------------------------------------
-- workout_exercises: WorkoutExerciseEntity (FK workoutId -> workouts.id CASCADE)
-- plannedSetsJson: JSON [{reps,weightKg,durationSeconds,steps,distanceMeters}]
-- type: ExerciseType.name, default WEIGHTLIFTING for pre-9->10 rows.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workout_exercises (
    id              TEXT NOT NULL PRIMARY KEY,
    workoutId       TEXT NOT NULL REFERENCES workouts (id) ON DELETE CASCADE,
    exerciseId      TEXT NOT NULL,
    exerciseName    TEXT NOT NULL,
    plannedSetsJson TEXT NOT NULL,
    orderIndex      INTEGER NOT NULL,
    type            TEXT NOT NULL DEFAULT 'WEIGHTLIFTING'
);

CREATE INDEX IF NOT EXISTS index_workout_exercises_workoutId ON workout_exercises (workoutId);

-- ----------------------------------------------------------------------------
-- exercises: ExerciseEntity (library cache, dao: ExerciseDao)
-- mediaUrl = remote demo (ExerciseDB gifUrl); localMediaPath = cached file
-- under filesDir/exercise_media/<exerciseId>.<ext> (see ExerciseVideoStorage).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS exercises (
    id             TEXT    NOT NULL PRIMARY KEY,
    name           TEXT    NOT NULL,
    bodyPart       TEXT    NOT NULL,
    equipment      TEXT,
    isUnilateral   INTEGER NOT NULL,  -- 0/1
    description    TEXT,
    mediaUrl       TEXT,
    localMediaPath TEXT
);

-- ----------------------------------------------------------------------------
-- workout_sessions: WorkoutSessionEntity (append-only log, dao: WorkoutDao)
-- loggedExercisesJson: JSON [{exerciseId,exerciseName,type,sets:[{reps,weightKg,
--   durationSeconds,steps,distanceMeters,completedAt}]}]
-- taskId links a session to the sport task that produced it (null for free runs).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workout_sessions (
    id                 TEXT    NOT NULL PRIMARY KEY,
    workoutId          TEXT    NOT NULL,
    workoutName        TEXT    NOT NULL,
    startedAt          INTEGER NOT NULL,  -- epoch-ms
    completedAt        INTEGER,           -- epoch-ms, null = in progress
    loggedExercisesJson TEXT   NOT NULL,
    taskId             TEXT,              -- nullable FK-by-value into tasks.id
    isDeleted          INTEGER NOT NULL DEFAULT 0  -- 0/1 tombstone (v18): hidden locally, synced
);

CREATE INDEX IF NOT EXISTS index_workout_sessions_workoutId ON workout_sessions (workoutId);
CREATE INDEX IF NOT EXISTS index_workout_sessions_taskId ON workout_sessions (taskId);
