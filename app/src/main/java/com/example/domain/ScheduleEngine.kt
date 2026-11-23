package com.example.domain

import com.example.model.ActivityStatus
import com.example.model.DailyHabit
import com.example.model.HabitStats
import com.example.model.RoutineActivity
import com.example.util.DateTimeUtils
import kotlin.math.max
import kotlin.math.min

data class ReorganizeResult(
    val updatedActivities: List<RoutineActivity>,
    val movedCount: Int,
    val unscheduledOverflowCount: Int,
)

object ScheduleEngine {

    /**
     * Returns all activities on the same date whose interval overlaps [startMinute, endMinute).
     */
    fun findOverlaps(
        activities: List<RoutineActivity>,
        startMinute: Long,
        endMinute: Long,
        excludeActivityId: String? = null,
    ): List<RoutineActivity> {
        return activities.filter { act ->
            act.id != excludeActivityId &&
                startMinute < act.endMinute &&
                endMinute > act.startMinute
        }.sortedBy { it.startMinute }
    }

    /**
     * Finds the earliest non-overlapping slot of [durationMinutes] starting at or after [earliestStart].
     */
    fun findNextAvailableSlot(
        activities: List<RoutineActivity>,
        durationMinutes: Int,
        earliestStart: Int,
        dayEnd: Int = 1440,
        bufferMinutes: Int = 5,
        excludeActivityId: String? = null,
    ): Pair<Long, Long>? {
        val duration = durationMinutes.coerceIn(5, 720)
        val occupied = activities
            .filter { it.id != excludeActivityId }
            .sortedBy { it.startMinute }

        var candidate = earliestStart.coerceIn(0, 1435)
        for (block in occupied) {
            val blockStart = block.startMinute.toInt()
            val blockEnd = block.endMinute.toInt()
            if (candidate + duration <= blockStart) {
                return candidate.toLong() to (candidate + duration).toLong()
            }
            if (candidate < blockEnd) {
                candidate = blockEnd + bufferMinutes
            }
        }
        return if (candidate + duration <= dayEnd.coerceAtMost(1440)) {
            candidate.toLong() to (candidate + duration).toLong()
        } else {
            null
        }
    }

    /**
     * Delays [targetActivityId] by [delayMinutes] and cascades any colliding flexible activities forward,
     * hopping cleanly over fixed (!flexible) blocks.
     */
    fun delayAndCascade(
        dayActivities: List<RoutineActivity>,
        targetActivityId: String,
        delayMinutes: Int,
        dayEnd: Int = 1440,
        bufferMinutes: Int = 5,
    ): List<RoutineActivity> {
        val sorted = dayActivities.sortedWith(compareBy<RoutineActivity> { it.startMinute }.thenBy { it.sortOrder })
        val target = sorted.firstOrNull { it.id == targetActivityId } ?: return emptyList()

        val duration = target.durationMinutes
        val newTargetStart = (target.startMinute.toInt() + delayMinutes).coerceAtMost(1440 - duration)
        val newTargetEnd = (newTargetStart + duration).coerceAtMost(1440)

        val fixedBlocks = sorted.filter { !it.flexible && it.id != targetActivityId }
        val changed = mutableListOf<RoutineActivity>()

        val adjustedTargetStart = fitAfterFixed(newTargetStart, duration, fixedBlocks, dayEnd, bufferMinutes)
        val adjustedTarget = target.copy(
            startMinute = adjustedTargetStart.toLong(),
            endMinute = (adjustedTargetStart + duration).coerceAtMost(1440).toLong(),
            status = ActivityStatus.DELAYED.name
        )
        changed += adjustedTarget

        // Now cascade subsequent flexible uncompleted items that overlap
        var cursorEnd = adjustedTarget.endMinute.toInt()
        val subsequentFlexible = sorted.filter {
            it.id != targetActivityId &&
                it.flexible &&
                !it.completed &&
                it.startMinute >= target.startMinute
        }

        for (item in subsequentFlexible) {
            val itemDur = item.durationMinutes
            val desiredStart = item.startMinute.toInt()
            if (desiredStart < cursorEnd + bufferMinutes) {
                val nextStart = fitAfterFixed(
                    earliestStart = cursorEnd + bufferMinutes,
                    duration = itemDur,
                    fixedBlocks = fixedBlocks,
                    dayEnd = dayEnd,
                    bufferMinutes = bufferMinutes
                )
                val nextEnd = min(1440, nextStart + itemDur)
                if (nextStart < 1440 && nextEnd > nextStart) {
                    changed += item.copy(
                        startMinute = nextStart.toLong(),
                        endMinute = nextEnd.toLong(),
                        status = ActivityStatus.RESCHEDULED.name
                    )
                    cursorEnd = nextEnd
                }
            } else {
                cursorEnd = max(cursorEnd, item.endMinute.toInt())
            }
        }
        return changed
    }

    /**
     * One-click Reorganize Remaining Day:
     * Keeps completed and fixed (!flexible) activities in place, then packs all remaining
     * uncompleted flexible activities sequentially from [startFromMinute] around fixed anchors.
     */
    fun reorganizeRemainingDay(
        dayActivities: List<RoutineActivity>,
        startFromMinute: Int,
        dayEnd: Int = 1440,
        bufferMinutes: Int = 10,
    ): ReorganizeResult {
        val anchors = dayActivities.filter { it.completed || !it.flexible }
            .sortedBy { it.startMinute }
        val flexiblePending = dayActivities.filter { !it.completed && it.flexible }
            .sortedWith(compareBy<RoutineActivity> { it.sortOrder }.thenBy { it.startMinute })

        if (flexiblePending.isEmpty()) {
            return ReorganizeResult(emptyList(), movedCount = 0, unscheduledOverflowCount = 0)
        }

        val updated = mutableListOf<RoutineActivity>()
        var cursor = startFromMinute.coerceIn(0, 1435)
        var overflow = 0

        for ((index, item) in flexiblePending.withIndex()) {
            val duration = item.durationMinutes
            val fittedStart = fitAfterFixed(
                earliestStart = cursor,
                duration = duration,
                fixedBlocks = anchors,
                dayEnd = dayEnd,
                bufferMinutes = bufferMinutes
            )
            val fittedEnd = fittedStart + duration
            if (fittedEnd <= dayEnd.coerceAtMost(1440)) {
                val moved = item.startMinute != fittedStart.toLong() ||
                    item.endMinute != fittedEnd.toLong() ||
                    item.sortOrder != index.toLong()
                if (moved) {
                    updated += item.copy(
                        startMinute = fittedStart.toLong(),
                        endMinute = fittedEnd.toLong(),
                        sortOrder = index.toLong(),
                        status = if (item.startMinute != fittedStart.toLong()) {
                            ActivityStatus.RESCHEDULED.name
                        } else {
                            item.status
                        }
                    )
                }
                cursor = fittedEnd + bufferMinutes
            } else {
                overflow++
            }
        }

        return ReorganizeResult(
            updatedActivities = updated,
            movedCount = updated.size,
            unscheduledOverflowCount = overflow
        )
    }

    /**
     * Reflows flexible activities after drag-and-drop reordering so sortOrder and time slots stay consistent.
     */
    fun reorderAndReflowSlots(
        orderedActivities: List<RoutineActivity>,
        bufferMinutes: Int = 5,
    ): List<RoutineActivity> {
        if (orderedActivities.isEmpty()) return emptyList()
        val fixedBlocks = orderedActivities.filter { !it.flexible || it.completed }.sortedBy { it.startMinute }
        val earliestFlexibleStart = orderedActivities
            .filter { it.flexible && !it.completed }
            .minOfOrNull { it.startMinute.toInt() }
            ?: return orderedActivities.mapIndexed { idx, act -> act.copy(sortOrder = idx.toLong()) }

        var cursor = earliestFlexibleStart
        val result = mutableListOf<RoutineActivity>()

        orderedActivities.forEachIndexed { idx, act ->
            if (!act.flexible || act.completed) {
                if (act.sortOrder != idx.toLong()) {
                    result += act.copy(sortOrder = idx.toLong())
                }
            } else {
                val duration = act.durationMinutes
                val newStart = fitAfterFixed(cursor, duration, fixedBlocks, 1440, bufferMinutes)
                val newEnd = min(1440, newStart + duration)
                cursor = newEnd + bufferMinutes
                if (act.sortOrder != idx.toLong() ||
                    act.startMinute != newStart.toLong() ||
                    act.endMinute != newEnd.toLong()
                ) {
                    result += act.copy(
                        sortOrder = idx.toLong(),
                        startMinute = newStart.toLong(),
                        endMinute = newEnd.toLong(),
                        status = if (act.startMinute != newStart.toLong()) {
                            ActivityStatus.RESCHEDULED.name
                        } else {
                            act.status
                        }
                    )
                }
            }
        }
        return result
    }

    private fun fitAfterFixed(
        earliestStart: Int,
        duration: Int,
        fixedBlocks: List<RoutineActivity>,
        dayEnd: Int,
        bufferMinutes: Int,
    ): Int {
        var candidate = earliestStart.coerceIn(0, 1435)
        for (fixed in fixedBlocks.sortedBy { it.startMinute }) {
            val fStart = fixed.startMinute.toInt()
            val fEnd = fixed.endMinute.toInt()
            if (candidate + duration <= fStart) {
                break
            }
            if (candidate < fEnd && candidate + duration > fStart) {
                candidate = fEnd + bufferMinutes
            }
        }
        return candidate.coerceAtMost((dayEnd - duration).coerceAtLeast(0))
    }

    fun computeHabitStats(
        habit: DailyHabit,
        todayKey: String = DateTimeUtils.todayDateKey(),
    ): HabitStats {
        val completedSet = habit.completedDates.toSet()

        // Current streak: count backwards from today (or yesterday if today isn't done yet)
        var currentStreak = 0
        var checkDate = if (completedSet.contains(todayKey)) todayKey else DateTimeUtils.addDays(todayKey, -1)
        while (completedSet.contains(checkDate)) {
            currentStreak++
            checkDate = DateTimeUtils.addDays(checkDate, -1)
        }

        // Best streak across sorted completedDates
        var bestStreak = 0
        val sortedDates = completedSet.sorted()
        var run = 0
        var prevDate: String? = null
        for (date in sortedDates) {
            if (prevDate != null && DateTimeUtils.addDays(prevDate, 1) == date) {
                run++
            } else {
                run = 1
            }
            if (run > bestStreak) bestStreak = run
            prevDate = date
        }

        val weekKeys = DateTimeUtils.weekDateKeys(todayKey)
        val weeklyCompleted = weekKeys.count { completedSet.contains(it) }

        val (year, month) = DateTimeUtils.yearMonthOf(todayKey)
        val monthKeys = DateTimeUtils.monthDateKeys(year, month)
        val monthlyCompleted = monthKeys.count { completedSet.contains(it) }
        val daysElapsedInMonth = DateTimeUtils.dayOfMonth(todayKey).coerceAtLeast(1)

        return HabitStats(
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            weeklyCompleted = weeklyCompleted,
            weeklyTarget = habit.targetDaysPerWeek.toInt().coerceIn(1, 7),
            monthlyCompleted = monthlyCompleted,
            monthlyTarget = daysElapsedInMonth,
            completedToday = completedSet.contains(todayKey)
        )
    }
}
