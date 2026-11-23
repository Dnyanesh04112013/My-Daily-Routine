package com.example

import com.example.domain.ScheduleEngine
import com.example.model.ActivityStatus
import com.example.model.DailyHabit
import com.example.model.RoutineActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEngineTest {

    @Test
    fun `delayAndCascade shifts colliding flexible tasks around fixed block`() {
        val activities = listOf(
            RoutineActivity(id = "a1", title = "Morning Prep", startMinute = 480, endMinute = 540, flexible = true),
            RoutineActivity(id = "a2", title = "Flexible Focus", startMinute = 545, endMinute = 605, flexible = true),
            RoutineActivity(id = "a3", title = "Fixed Meeting", startMinute = 615, endMinute = 675, flexible = false),
        )

        val updates = ScheduleEngine.delayAndCascade(
            dayActivities = activities,
            targetActivityId = "a1",
            delayMinutes = 30,
            dayEnd = 1320,
            bufferMinutes = 10
        )

        val updatedA1 = updates.first { it.id == "a1" }
        val updatedA2 = updates.first { it.id == "a2" }

        assertEquals(510L, updatedA1.startMinute)
        assertEquals(570L, updatedA1.endMinute)
        assertEquals(ActivityStatus.DELAYED.name, updatedA1.status)

        // a2 would start at 580, which collides with Fixed Meeting (615..675) for 60m duration -> hops to 685..745
        assertEquals(685L, updatedA2.startMinute)
        assertEquals(745L, updatedA2.endMinute)
        assertEquals(ActivityStatus.RESCHEDULED.name, updatedA2.status)
    }

    @Test
    fun `reorganizeRemainingDay packs uncompleted flexible tasks without overlapping fixed anchors`() {
        val activities = listOf(
            RoutineActivity(id = "done", title = "Done", startMinute = 420, endMinute = 450, completed = true, flexible = true),
            RoutineActivity(id = "fixed", title = "Standup", startMinute = 600, endMinute = 630, completed = false, flexible = false),
            RoutineActivity(id = "f1", title = "Deep Work", startMinute = 500, endMinute = 560, sortOrder = 0, flexible = true),
            RoutineActivity(id = "f2", title = "Review", startMinute = 570, endMinute = 610, sortOrder = 1, flexible = true),
        )

        val result = ScheduleEngine.reorganizeRemainingDay(
            dayActivities = activities,
            startFromMinute = 550,
            dayEnd = 1320,
            bufferMinutes = 10
        )

        val movedF1 = result.updatedActivities.first { it.id == "f1" }
        val movedF2 = result.updatedActivities.first { it.id == "f2" }

        // f1 (60m) cannot fit 550..610 because fixed is 600..630 -> hops to 640..700
        assertEquals(640L, movedF1.startMinute)
        assertEquals(700L, movedF1.endMinute)
        assertEquals(710L, movedF2.startMinute)
        assertEquals(750L, movedF2.endMinute)
    }

    @Test
    fun `findOverlaps detects intersecting slots`() {
        val activities = listOf(
            RoutineActivity(id = "a1", title = "Task 1", startMinute = 540, endMinute = 600),
            RoutineActivity(id = "a2", title = "Task 2", startMinute = 630, endMinute = 690),
        )
        val overlaps = ScheduleEngine.findOverlaps(activities, startMinute = 580, endMinute = 640)
        assertEquals(2, overlaps.size)
        val slot = ScheduleEngine.findNextAvailableSlot(activities, durationMinutes = 30, earliestStart = 540, bufferMinutes = 5)
        assertNotNull(slot)
        assertTrue(slot!!.first >= 605L || slot.first >= 695L)
    }

    @Test
    fun `computeHabitStats calculates current and best streaks accurately`() {
        val habit = DailyHabit(
            id = "h1",
            title = "Hydrate",
            targetDaysPerWeek = 7L,
            completedDates = listOf("2026-11-18", "2026-11-19", "2026-11-21", "2026-11-22"),
            createdDateKey = "2026-11-18"
        )
        val stats = ScheduleEngine.computeHabitStats(habit, todayKey = "2026-11-22")
        assertEquals(2, stats.currentStreak)
        assertEquals(2, stats.bestStreak)
        assertTrue(stats.completedToday)
    }
}
