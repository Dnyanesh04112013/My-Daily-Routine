package com.example.data

import com.example.data.local.ActivityEntity
import com.example.data.local.HabitEntity
import com.example.data.local.RoutineDao
import com.example.model.ActivityCategory
import com.example.model.ActivityStatus
import com.example.model.DailyHabit
import com.example.model.RoutineActivity
import com.example.model.ThemeMode
import com.example.model.UserProfile
import com.example.util.DateTimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID

class RoutineRepository(
    private val dao: RoutineDao,
) {

    fun observeProfile(userId: String): Flow<UserProfile?> =
        dao.observeUser(userId).map { it?.toDomain() }

    suspend fun updateProfile(
        userId: String,
        displayName: String,
        themeMode: ThemeMode,
        morningStartMinute: Long,
        dayEndMinute: Long,
        defaultBufferMinutes: Long,
        notificationsEnabled: Boolean,
    ): Result<Unit> {
        return try {
            val cleanName = displayName.trim().take(40)
            require(cleanName.length >= 2) { "Username must be at least 2 characters." }
            require(morningStartMinute in 0L..1439L) { "Invalid morning start time." }
            require(dayEndMinute in (morningStartMinute + 1)..1440L) { "Day end must be after start time." }
            require(defaultBufferMinutes in 0L..60L) { "Buffer must be between 0 and 60 minutes." }

            val current = dao.getUserById(userId)
                ?: return Result.failure(IllegalStateException("Account not found."))
            val lower = cleanName.lowercase(Locale.US)
            if (lower != current.usernameLower) {
                val conflict = dao.getUserByUsername(lower)
                if (conflict != null && conflict.userId != userId) {
                    return Result.failure(IllegalStateException("Username \"$cleanName\" is already taken."))
                }
            }

            dao.updateUser(
                current.copy(
                    username = cleanName,
                    usernameLower = lower,
                    themeMode = themeMode.name,
                    morningStartMinute = morningStartMinute,
                    dayEndMinute = dayEndMinute,
                    defaultBufferMinutes = defaultBufferMinutes,
                    notificationsEnabled = notificationsEnabled
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeAllActivities(userId: String): Flow<List<RoutineActivity>> =
        dao.observeActivities(userId).map { entities -> entities.map { it.toDomain() } }

    suspend fun addActivity(
        userId: String,
        dateKey: String,
        title: String,
        description: String,
        category: ActivityCategory,
        startMinute: Long,
        endMinute: Long,
        sortOrder: Long,
        flexible: Boolean,
        reminderEnabled: Boolean,
        reminderMinutesBefore: Long,
    ): Result<RoutineActivity> {
        return try {
            val cleanTitle = title.trim().take(120)
            val cleanDesc = description.trim().take(500)
            require(cleanTitle.isNotEmpty()) { "Activity title is required." }
            require(startMinute in 0L..1439L) { "Invalid start time." }
            require(endMinute in (startMinute + 1)..1440L) { "End time must be after start time." }

            val now = System.currentTimeMillis()
            val entity = ActivityEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                dateKey = dateKey,
                title = cleanTitle,
                description = cleanDesc,
                category = category.name,
                startMinute = startMinute,
                endMinute = endMinute,
                sortOrder = sortOrder.coerceIn(0L, 10000L),
                completed = false,
                flexible = flexible,
                reminderEnabled = reminderEnabled,
                reminderMinutesBefore = reminderMinutesBefore.coerceIn(0L, 120L),
                status = ActivityStatus.SCHEDULED.name,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            dao.upsertActivity(entity)
            Result.success(entity.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateActivity(
        userId: String,
        activityId: String,
        dateKey: String,
        title: String,
        description: String,
        category: ActivityCategory,
        startMinute: Long,
        endMinute: Long,
        sortOrder: Long,
        completed: Boolean,
        flexible: Boolean,
        reminderEnabled: Boolean,
        reminderMinutesBefore: Long,
        status: ActivityStatus,
    ): Result<Unit> {
        return try {
            require(activityId.isNotBlank()) { "Activity ID must not be blank." }
            val cleanTitle = title.trim().take(120)
            val cleanDesc = description.trim().take(500)
            require(cleanTitle.isNotEmpty()) { "Activity title is required." }
            require(startMinute in 0L..1439L) { "Invalid start time." }
            require(endMinute in (startMinute + 1)..1440L) { "End time must be after start time." }

            val existing = dao.getActivity(userId, activityId)
            val now = System.currentTimeMillis()
            val updated = ActivityEntity(
                id = activityId,
                userId = userId,
                dateKey = dateKey,
                title = cleanTitle,
                description = cleanDesc,
                category = category.name,
                startMinute = startMinute,
                endMinute = endMinute,
                sortOrder = sortOrder.coerceIn(0L, 10000L),
                completed = completed,
                flexible = flexible,
                reminderEnabled = reminderEnabled,
                reminderMinutesBefore = reminderMinutesBefore.coerceIn(0L, 120L),
                status = status.name,
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now
            )
            dao.upsertActivity(updated)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleActivityCompleted(userId: String, activity: RoutineActivity): Result<Unit> {
        return try {
            val existing = dao.getActivity(userId, activity.id)
                ?: return Result.failure(IllegalStateException("Activity not found."))
            val nextCompleted = !existing.completed
            val nextStatus = if (nextCompleted) ActivityStatus.COMPLETED.name else ActivityStatus.SCHEDULED.name
            dao.upsertActivity(
                existing.copy(
                    completed = nextCompleted,
                    status = nextStatus,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun batchUpdateActivities(userId: String, updated: List<RoutineActivity>): Result<Unit> {
        if (updated.isEmpty()) return Result.success(Unit)
        return try {
            val now = System.currentTimeMillis()
            val entities = updated.mapNotNull { item ->
                val existing = dao.getActivity(userId, item.id) ?: return@mapNotNull null
                existing.copy(
                    dateKey = item.dateKey,
                    startMinute = item.startMinute,
                    endMinute = item.endMinute,
                    sortOrder = item.sortOrder.coerceIn(0L, 10000L),
                    status = item.status,
                    updatedAtMillis = now
                )
            }
            if (entities.isNotEmpty()) {
                dao.applyActivityUpdates(entities)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun copyRoutineToDate(
        userId: String,
        sourceActivities: List<RoutineActivity>,
        targetDateKey: String,
    ): Result<Int> {
        if (sourceActivities.isEmpty()) return Result.success(0)
        return try {
            val now = System.currentTimeMillis()
            val copies = sourceActivities.map { item ->
                ActivityEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    dateKey = targetDateKey,
                    title = item.title.take(120),
                    description = item.description.take(500),
                    category = item.category,
                    startMinute = item.startMinute,
                    endMinute = item.endMinute,
                    sortOrder = item.sortOrder.coerceIn(0L, 10000L),
                    completed = false,
                    flexible = item.flexible,
                    reminderEnabled = item.reminderEnabled,
                    reminderMinutesBefore = item.reminderMinutesBefore.coerceIn(0L, 120L),
                    status = ActivityStatus.SCHEDULED.name,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            }
            dao.upsertActivities(copies)
            Result.success(copies.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteActivity(userId: String, activityId: String): Result<Unit> {
        return try {
            dao.deleteActivity(userId, activityId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeHabits(userId: String): Flow<List<DailyHabit>> =
        dao.observeHabits(userId).map { entities -> entities.map { it.toDomain() } }

    suspend fun addHabit(
        userId: String,
        title: String,
        category: ActivityCategory,
        targetDaysPerWeek: Long,
        reminderTimeMinute: Long,
    ): Result<Unit> {
        return try {
            val cleanTitle = title.trim().take(80)
            require(cleanTitle.isNotEmpty()) { "Habit title is required." }
            val now = System.currentTimeMillis()
            val entity = HabitEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                title = cleanTitle,
                category = category.name,
                targetDaysPerWeek = targetDaysPerWeek.coerceIn(1L, 7L),
                reminderTimeMinute = reminderTimeMinute.coerceIn(0L, 1439L),
                completedDatesCsv = "",
                createdDateKey = DateTimeUtils.todayDateKey(),
                createdAtMillis = now,
                updatedAtMillis = now
            )
            dao.upsertHabit(entity)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateHabit(
        userId: String,
        habitId: String,
        title: String,
        category: ActivityCategory,
        targetDaysPerWeek: Long,
        reminderTimeMinute: Long,
    ): Result<Unit> {
        return try {
            val cleanTitle = title.trim().take(80)
            require(cleanTitle.isNotEmpty()) { "Habit title is required." }
            val existing = dao.getHabit(userId, habitId)
                ?: return Result.failure(IllegalStateException("Habit not found."))
            dao.upsertHabit(
                existing.copy(
                    title = cleanTitle,
                    category = category.name,
                    targetDaysPerWeek = targetDaysPerWeek.coerceIn(1L, 7L),
                    reminderTimeMinute = reminderTimeMinute.coerceIn(0L, 1439L),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleHabitDate(userId: String, habit: DailyHabit, dateKey: String): Result<Unit> {
        return try {
            val existing = dao.getHabit(userId, habit.id)
                ?: return Result.failure(IllegalStateException("Habit not found."))
            val updatedDates = existing.completedDatesCsv
                .split(",")
                .filter { it.isNotBlank() }
                .toMutableSet()
            if (updatedDates.contains(dateKey)) {
                updatedDates.remove(dateKey)
            } else {
                updatedDates.add(dateKey)
            }
            val csv = updatedDates.sorted().takeLast(500).joinToString(",")
            dao.upsertHabit(
                existing.copy(
                    completedDatesCsv = csv,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteHabit(userId: String, habitId: String): Result<Unit> {
        return try {
            dao.deleteHabit(userId, habitId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
