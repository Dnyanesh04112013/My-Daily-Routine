package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RoutineRepository
import com.example.domain.ReorganizeResult
import com.example.domain.ScheduleEngine
import com.example.model.ActivityCategory
import com.example.model.ActivityStatus
import com.example.model.DailyHabit
import com.example.model.RoutineActivity
import com.example.model.ThemeMode
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.notifications.RoutineReminderScheduler
import com.example.util.DateTimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CalendarViewMode(val label: String) {
    DAY("Daily"),
    WEEK("Weekly"),
    MONTH("Monthly"),
}

class RoutineViewModel(
    private val repository: RoutineRepository,
    private val currentUserId: String,
) : ViewModel() {

    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing profile", error)
            emit(UiState.Error(error.localizedMessage ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val activitiesState: StateFlow<UiState<List<RoutineActivity>>> = repository.observeAllActivities(currentUserId)
        .map<List<RoutineActivity>, UiState<List<RoutineActivity>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing activities", error)
            emit(UiState.Error(error.localizedMessage ?: "Failed to load routines"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val habitsState: StateFlow<UiState<List<DailyHabit>>> = repository.observeHabits(currentUserId)
        .map<List<DailyHabit>, UiState<List<DailyHabit>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing habits", error)
            emit(UiState.Error(error.localizedMessage ?: "Failed to load habits"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _selectedDateKey = MutableStateFlow(DateTimeUtils.todayDateKey())
    val selectedDateKey: StateFlow<String> = _selectedDateKey.asStateFlow()

    private val _calendarMode = MutableStateFlow(CalendarViewMode.DAY)
    val calendarMode: StateFlow<CalendarViewMode> = _calendarMode.asStateFlow()

    private val _calendarYearMonth = MutableStateFlow(DateTimeUtils.yearMonthOf(DateTimeUtils.todayDateKey()))
    val calendarYearMonth: StateFlow<Pair<Int, Int>> = _calendarYearMonth.asStateFlow()

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    fun selectDate(dateKey: String) {
        _selectedDateKey.value = dateKey
        _calendarYearMonth.value = DateTimeUtils.yearMonthOf(dateKey)
    }

    fun setCalendarMode(mode: CalendarViewMode) {
        _calendarMode.value = mode
    }

    fun shiftCalendarMonth(deltaMonths: Int) {
        val (year, month) = _calendarYearMonth.value
        var newMonth = month + deltaMonths
        var newYear = year
        while (newMonth > 12) {
            newMonth -= 12
            newYear++
        }
        while (newMonth < 1) {
            newMonth += 12
            newYear--
        }
        _calendarYearMonth.value = newYear to newMonth
    }

    fun saveActivity(
        context: Context,
        editingActivity: RoutineActivity?,
        dateKey: String,
        title: String,
        description: String,
        category: ActivityCategory,
        startMinute: Long,
        endMinute: Long,
        flexible: Boolean,
        reminderEnabled: Boolean,
        reminderMinutesBefore: Long,
        autoShiftOverlaps: Boolean,
        dayActivities: List<RoutineActivity>,
        profile: UserProfile?,
    ) {
        viewModelScope.launch {
            _isMutating.value = true
            try {
                val notificationsAllowed = profile?.notificationsEnabled ?: true
                if (editingActivity == null) {
                    val sortOrder = (dayActivities.maxOfOrNull { it.sortOrder } ?: -1L) + 1L
                    val addRes = repository.addActivity(
                        userId = currentUserId,
                        dateKey = dateKey,
                        title = title,
                        description = description,
                        category = category,
                        startMinute = startMinute,
                        endMinute = endMinute,
                        sortOrder = sortOrder,
                        flexible = flexible,
                        reminderEnabled = reminderEnabled,
                        reminderMinutesBefore = reminderMinutesBefore
                    )
                    addRes.onSuccess { created ->
                        RoutineReminderScheduler.syncReminder(context, created, notificationsAllowed)
                        if (autoShiftOverlaps) {
                            shiftOverlappingFlexible(dayActivities, created.id, endMinute, profile)
                        }
                        _snackbarMessages.tryEmit("Added \"${created.title}\"")
                    }.onFailure { err ->
                        _snackbarMessages.tryEmit(err.localizedMessage ?: "Could not add activity")
                    }
                } else {
                    val nextStatus = if (editingActivity.completed) {
                        ActivityStatus.COMPLETED
                    } else if (editingActivity.startMinute != startMinute || editingActivity.endMinute != endMinute) {
                        ActivityStatus.RESCHEDULED
                    } else {
                        editingActivity.resolvedStatus
                    }
                    val updRes = repository.updateActivity(
                        userId = currentUserId,
                        activityId = editingActivity.id,
                        dateKey = dateKey,
                        title = title,
                        description = description,
                        category = category,
                        startMinute = startMinute,
                        endMinute = endMinute,
                        sortOrder = editingActivity.sortOrder,
                        completed = editingActivity.completed,
                        flexible = flexible,
                        reminderEnabled = reminderEnabled,
                        reminderMinutesBefore = reminderMinutesBefore,
                        status = nextStatus
                    )
                    updRes.onSuccess {
                        val updatedAct = editingActivity.copy(
                            dateKey = dateKey,
                            title = title,
                            description = description,
                            category = category.name,
                            startMinute = startMinute,
                            endMinute = endMinute,
                            flexible = flexible,
                            reminderEnabled = reminderEnabled,
                            reminderMinutesBefore = reminderMinutesBefore,
                            status = nextStatus.name
                        )
                        RoutineReminderScheduler.syncReminder(context, updatedAct, notificationsAllowed)
                        if (autoShiftOverlaps) {
                            shiftOverlappingFlexible(dayActivities, editingActivity.id, endMinute, profile)
                        }
                        _snackbarMessages.tryEmit("Updated \"${title.trim()}\"")
                    }.onFailure { err ->
                        _snackbarMessages.tryEmit(err.localizedMessage ?: "Could not update activity")
                    }
                }
            } finally {
                _isMutating.value = false
            }
        }
    }

    private suspend fun shiftOverlappingFlexible(
        dayActivities: List<RoutineActivity>,
        anchorId: String,
        anchorEndMinute: Long,
        profile: UserProfile?,
    ) {
        val buffer = (profile?.defaultBufferMinutes ?: 10L).toInt()
        val dayEnd = (profile?.dayEndMinute ?: 1320L).toInt()
        val fixedBlocks = dayActivities.filter { !it.flexible && it.id != anchorId }
        val toShift = dayActivities.filter {
            it.id != anchorId && it.flexible && !it.completed && it.endMinute > anchorEndMinute - 1 && it.startMinute < anchorEndMinute
        }.sortedBy { it.startMinute }

        if (toShift.isEmpty()) return
        var cursor = (anchorEndMinute.toInt() + buffer).coerceAtMost(1435)
        val batchUpdates = mutableListOf<RoutineActivity>()
        for (item in toShift) {
            val dur = item.durationMinutes
            val slot = ScheduleEngine.findNextAvailableSlot(
                activities = fixedBlocks + batchUpdates,
                durationMinutes = dur,
                earliestStart = cursor,
                dayEnd = dayEnd,
                bufferMinutes = buffer,
                excludeActivityId = item.id
            )
            if (slot != null) {
                batchUpdates += item.copy(
                    startMinute = slot.first,
                    endMinute = slot.second,
                    status = ActivityStatus.RESCHEDULED.name
                )
                cursor = slot.second.toInt() + buffer
            }
        }
        if (batchUpdates.isNotEmpty()) {
            repository.batchUpdateActivities(currentUserId, batchUpdates)
        }
    }

    fun toggleActivityCompleted(context: Context, activity: RoutineActivity, profile: UserProfile?) {
        viewModelScope.launch {
            val res = repository.toggleActivityCompleted(currentUserId, activity)
            res.onSuccess {
                val toggled = activity.copy(completed = !activity.completed)
                RoutineReminderScheduler.syncReminder(context, toggled, profile?.notificationsEnabled ?: true)
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Could not update status")
            }
        }
    }

    fun deleteActivity(context: Context, activity: RoutineActivity) {
        viewModelScope.launch {
            _isMutating.value = true
            try {
                val res = repository.deleteActivity(currentUserId, activity.id)
                res.onSuccess {
                    RoutineReminderScheduler.cancelReminder(context, activity.id)
                    _snackbarMessages.tryEmit("Removed \"${activity.title}\"")
                }.onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Could not delete activity")
                }
            } finally {
                _isMutating.value = false
            }
        }
    }

    fun delayActivityAndCascade(
        context: Context,
        dayActivities: List<RoutineActivity>,
        targetActivityId: String,
        delayMinutes: Int,
        profile: UserProfile?,
    ) {
        viewModelScope.launch {
            _isMutating.value = true
            try {
                val buffer = (profile?.defaultBufferMinutes ?: 10L).toInt()
                val dayEnd = (profile?.dayEndMinute ?: 1320L).toInt()
                val updates = ScheduleEngine.delayAndCascade(
                    dayActivities = dayActivities,
                    targetActivityId = targetActivityId,
                    delayMinutes = delayMinutes,
                    dayEnd = dayEnd,
                    bufferMinutes = buffer
                )
                if (updates.isNotEmpty()) {
                    repository.batchUpdateActivities(currentUserId, updates).onSuccess {
                        val notifOn = profile?.notificationsEnabled ?: true
                        updates.forEach { RoutineReminderScheduler.syncReminder(context, it, notifOn) }
                        _snackbarMessages.tryEmit(
                            "Delayed by ${delayMinutes}m • adjusted ${updates.size} slot(s)"
                        )
                    }.onFailure { err ->
                        _snackbarMessages.tryEmit(err.localizedMessage ?: "Could not delay activity")
                    }
                }
            } finally {
                _isMutating.value = false
            }
        }
    }

    fun previewReorganizeRemainingDay(
        dayActivities: List<RoutineActivity>,
        dateKey: String,
        profile: UserProfile?,
    ): ReorganizeResult {
        val todayKey = DateTimeUtils.todayDateKey()
        val startMin = if (dateKey == todayKey) {
            val nowRounded = ((DateTimeUtils.currentMinuteOfDay() + 4) / 5) * 5
            maxOf(nowRounded, (profile?.morningStartMinute ?: 420L).toInt())
        } else {
            (profile?.morningStartMinute ?: 420L).toInt()
        }
        return ScheduleEngine.reorganizeRemainingDay(
            dayActivities = dayActivities,
            startFromMinute = startMin,
            dayEnd = (profile?.dayEndMinute ?: 1320L).toInt(),
            bufferMinutes = (profile?.defaultBufferMinutes ?: 10L).toInt()
        )
    }

    fun applyReorganizeResult(
        context: Context,
        result: ReorganizeResult,
        profile: UserProfile?,
    ) {
        if (result.updatedActivities.isEmpty()) {
            _snackbarMessages.tryEmit("Your remaining schedule is already organized.")
            return
        }
        viewModelScope.launch {
            _isMutating.value = true
            try {
                repository.batchUpdateActivities(currentUserId, result.updatedActivities).onSuccess {
                    val notifOn = profile?.notificationsEnabled ?: true
                    result.updatedActivities.forEach { RoutineReminderScheduler.syncReminder(context, it, notifOn) }
                    val overflowNote = if (result.unscheduledOverflowCount > 0) {
                        " (${result.unscheduledOverflowCount} didn't fit before day end)"
                    } else ""
                    _snackbarMessages.tryEmit("Reorganized ${result.movedCount} flexible block(s)$overflowNote")
                }.onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to reorganize schedule")
                }
            } finally {
                _isMutating.value = false
            }
        }
    }

    fun reorderActivities(
        context: Context,
        reorderedDayList: List<RoutineActivity>,
        reflowTimes: Boolean,
        profile: UserProfile?,
    ) {
        viewModelScope.launch {
            val updates = if (reflowTimes) {
                ScheduleEngine.reorderAndReflowSlots(
                    orderedActivities = reorderedDayList,
                    bufferMinutes = (profile?.defaultBufferMinutes ?: 10L).toInt()
                )
            } else {
                reorderedDayList.mapIndexedNotNull { idx, item ->
                    if (item.sortOrder != idx.toLong()) item.copy(sortOrder = idx.toLong()) else null
                }
            }
            if (updates.isNotEmpty()) {
                repository.batchUpdateActivities(currentUserId, updates).onSuccess {
                    val notifOn = profile?.notificationsEnabled ?: true
                    updates.forEach { RoutineReminderScheduler.syncReminder(context, it, notifOn) }
                }.onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to save new order")
                }
            }
        }
    }

    fun rescheduleUnfinishedTask(
        context: Context,
        task: RoutineActivity,
        targetDateKey: String,
        targetDateActivities: List<RoutineActivity>,
        profile: UserProfile?,
    ) {
        viewModelScope.launch {
            _isMutating.value = true
            try {
                val earliest = if (targetDateKey == DateTimeUtils.todayDateKey()) {
                    maxOf(
                        ((DateTimeUtils.currentMinuteOfDay() + 4) / 5) * 5,
                        (profile?.morningStartMinute ?: 420L).toInt()
                    )
                } else {
                    (profile?.morningStartMinute ?: 420L).toInt()
                }
                val slot = ScheduleEngine.findNextAvailableSlot(
                    activities = targetDateActivities,
                    durationMinutes = task.durationMinutes,
                    earliestStart = earliest,
                    dayEnd = (profile?.dayEndMinute ?: 1320L).toInt(),
                    bufferMinutes = (profile?.defaultBufferMinutes ?: 10L).toInt(),
                    excludeActivityId = task.id
                )
                if (slot == null) {
                    _snackbarMessages.tryEmit("No open slot available on ${DateTimeUtils.formatShortDate(targetDateKey)}")
                    return@launch
                }
                val updated = task.copy(
                    dateKey = targetDateKey,
                    startMinute = slot.first,
                    endMinute = slot.second,
                    status = ActivityStatus.RESCHEDULED.name
                )
                repository.batchUpdateActivities(currentUserId, listOf(updated)).onSuccess {
                    RoutineReminderScheduler.syncReminder(context, updated, profile?.notificationsEnabled ?: true)
                    _snackbarMessages.tryEmit(
                        "Rescheduled \"${task.title}\" to ${DateTimeUtils.formatShortDate(targetDateKey)} ${DateTimeUtils.formatMinute(slot.first.toInt())}"
                    )
                }.onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Could not reschedule task")
                }
            } finally {
                _isMutating.value = false
            }
        }
    }

    fun copyRoutineFromDate(
        sourceDateKey: String,
        targetDateKey: String,
        allActivities: List<RoutineActivity>,
    ) {
        val sourceList = allActivities.filter { it.dateKey == sourceDateKey }
        if (sourceList.isEmpty()) {
            _snackbarMessages.tryEmit("No routine found on ${DateTimeUtils.formatShortDate(sourceDateKey)} to copy.")
            return
        }
        viewModelScope.launch {
            _isMutating.value = true
            try {
                repository.copyRoutineToDate(currentUserId, sourceList, targetDateKey).onSuccess { count ->
                    _snackbarMessages.tryEmit(
                        "Copied $count activities to ${DateTimeUtils.formatShortDate(targetDateKey)}"
                    )
                }.onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Could not copy routine")
                }
            } finally {
                _isMutating.value = false
            }
        }
    }

    fun addStarterRoutine(dateKey: String) {
        viewModelScope.launch {
            _isMutating.value = true
            try {
                val templates = listOf(
                    Triple("Morning Hydration & Stretch", ActivityCategory.HEALTH, 420L to 450L),
                    Triple("Daily Planning & Priorities", ActivityCategory.WELLNESS, 460L to 485L),
                    Triple("Deep Focus Work Block", ActivityCategory.FOCUS, 540L to 660L),
                    Triple("Lunch & Mindful Walk", ActivityCategory.PERSONAL, 750L to 810L),
                    Triple("Skill Reading & Notes", ActivityCategory.LEARNING, 1080L to 1125L),
                    Triple("Evening Wind-Down", ActivityCategory.REST, 1260L to 1305L),
                )
                templates.forEachIndexed { idx, (title, cat, range) ->
                    repository.addActivity(
                        userId = currentUserId,
                        dateKey = dateKey,
                        title = title,
                        description = "Starter routine template block",
                        category = cat,
                        startMinute = range.first,
                        endMinute = range.second,
                        sortOrder = idx.toLong(),
                        flexible = idx != 2,
                        reminderEnabled = false,
                        reminderMinutesBefore = 10L
                    )
                }
                _snackbarMessages.tryEmit("Loaded starter daily routine for ${DateTimeUtils.formatShortDate(dateKey)}")
            } finally {
                _isMutating.value = false
            }
        }
    }

    fun saveHabit(
        editingHabit: DailyHabit?,
        title: String,
        category: ActivityCategory,
        targetDaysPerWeek: Long,
        reminderTimeMinute: Long,
    ) {
        viewModelScope.launch {
            _isMutating.value = true
            try {
                val result = if (editingHabit == null) {
                    repository.addHabit(
                        userId = currentUserId,
                        title = title,
                        category = category,
                        targetDaysPerWeek = targetDaysPerWeek,
                        reminderTimeMinute = reminderTimeMinute
                    )
                } else {
                    repository.updateHabit(
                        userId = currentUserId,
                        habitId = editingHabit.id,
                        title = title,
                        category = category,
                        targetDaysPerWeek = targetDaysPerWeek,
                        reminderTimeMinute = reminderTimeMinute
                    )
                }
                result.onSuccess {
                    _snackbarMessages.tryEmit(
                        if (editingHabit == null) "Habit \"${title.trim()}\" created"
                        else "Habit \"${title.trim()}\" updated"
                    )
                }.onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to save habit")
                }
            } finally {
                _isMutating.value = false
            }
        }
    }

    fun toggleHabitForDate(habit: DailyHabit, dateKey: String = DateTimeUtils.todayDateKey()) {
        viewModelScope.launch {
            repository.toggleHabitDate(currentUserId, habit, dateKey).onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to update habit")
            }
        }
    }

    fun deleteHabit(habit: DailyHabit) {
        viewModelScope.launch {
            repository.deleteHabit(currentUserId, habit.id).onSuccess {
                _snackbarMessages.tryEmit("Deleted habit \"${habit.title}\"")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to delete habit")
            }
        }
    }

    fun updateProfilePreferences(
        displayName: String,
        themeMode: ThemeMode,
        morningStartMinute: Long,
        dayEndMinute: Long,
        defaultBufferMinutes: Long,
        notificationsEnabled: Boolean,
        showToast: Boolean = true,
    ) {
        viewModelScope.launch {
            repository.updateProfile(
                userId = currentUserId,
                displayName = displayName,
                themeMode = themeMode,
                morningStartMinute = morningStartMinute,
                dayEndMinute = dayEndMinute,
                defaultBufferMinutes = defaultBufferMinutes,
                notificationsEnabled = notificationsEnabled
            ).onSuccess {
                if (showToast) {
                    _snackbarMessages.tryEmit("Preferences saved")
                }
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.localizedMessage ?: "Failed to save preferences")
            }
        }
    }

    private companion object {
        const val TAG = "RoutineVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
