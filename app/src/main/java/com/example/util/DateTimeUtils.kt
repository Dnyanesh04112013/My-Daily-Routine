package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateTimeUtils {

    private val isoDateFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val fullDateFormat: SimpleDateFormat
        get() = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())

    private val shortDateFormat: SimpleDateFormat
        get() = SimpleDateFormat("MMM d", Locale.getDefault())

    private val monthYearFormat: SimpleDateFormat
        get() = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    private val shortWeekdayFormat: SimpleDateFormat
        get() = SimpleDateFormat("EEE", Locale.getDefault())

    fun todayDateKey(): String = calendarToDateKey(Calendar.getInstance())

    fun yesterdayDateKey(): String = addDays(todayDateKey(), -1)

    fun tomorrowDateKey(): String = addDays(todayDateKey(), 1)

    fun calendarToDateKey(cal: Calendar): String = isoDateFormat.format(cal.time)

    fun parseDateKey(dateKey: String): Calendar {
        val cal = Calendar.getInstance()
        val parts = dateKey.split("-")
        if (parts.size == 3) {
            val y = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            val d = parts[2].toIntOrNull()
            if (y != null && m != null && d != null) {
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, (m - 1).coerceIn(0, 11))
                cal.set(Calendar.DAY_OF_MONTH, d.coerceIn(1, 31))
            }
        }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    fun addDays(dateKey: String, deltaDays: Int): String {
        val cal = parseDateKey(dateKey)
        cal.add(Calendar.DAY_OF_MONTH, deltaDays)
        return calendarToDateKey(cal)
    }

    fun formatDateHeader(dateKey: String): String {
        val cal = parseDateKey(dateKey)
        return fullDateFormat.format(cal.time)
    }

    fun formatShortDate(dateKey: String): String {
        val cal = parseDateKey(dateKey)
        return shortDateFormat.format(cal.time)
    }

    fun formatShortWeekday(dateKey: String): String {
        val cal = parseDateKey(dateKey)
        return shortWeekdayFormat.format(cal.time)
    }

    fun dayOfMonth(dateKey: String): Int =
        parseDateKey(dateKey).get(Calendar.DAY_OF_MONTH)

    fun yearMonthOf(dateKey: String): Pair<Int, Int> {
        val cal = parseDateKey(dateKey)
        return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
    }

    fun formatMonthYear(year: Int, month1Based: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, (month1Based - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return monthYearFormat.format(cal.time)
    }

    /** Returns 7 dateKeys for the week (Mon..Sun) containing [anchorDateKey]. */
    fun weekDateKeys(anchorDateKey: String): List<String> {
        val cal = parseDateKey(anchorDateKey)
        cal.firstDayOfWeek = Calendar.MONDAY
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val offsetFromMonday = when (dayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 6 // Sunday
        }
        cal.add(Calendar.DAY_OF_MONTH, -offsetFromMonday)
        return List(7) { idx ->
            val key = calendarToDateKey(cal)
            if (idx < 6) cal.add(Calendar.DAY_OF_MONTH, 1)
            key
        }
    }

    /** Returns all dateKeys in the given 1-based month. */
    fun monthDateKeys(year: Int, month1Based: Int): List<String> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, (month1Based - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return List(maxDay) { dayIdx ->
            cal.set(Calendar.DAY_OF_MONTH, dayIdx + 1)
            calendarToDateKey(cal)
        }
    }

    /** Returns leading empty slots (Mon=0..Sun=6) for a calendar month grid. */
    fun leadingEmptyDaysInMonth(year: Int, month1Based: Int): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, (month1Based - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 6
        }
    }

    fun formatMinute(minuteOfDay: Int): String {
        val clamped = minuteOfDay.coerceIn(0, 1440)
        val hour24 = (clamped / 60) % 24
        val min = clamped % 60
        val amPm = if (clamped == 1440 || hour24 < 12) "AM" else "PM"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, min, amPm)
    }

    fun formatMinuteCompact(minuteOfDay: Int): String {
        val clamped = minuteOfDay.coerceIn(0, 1440)
        val hour24 = clamped / 60
        val min = clamped % 60
        return String.format(Locale.US, "%02d:%02d", hour24, min)
    }

    fun formatTimeRange(startMinute: Long, endMinute: Long): String {
        return "${formatMinute(startMinute.toInt())} – ${formatMinute(endMinute.toInt())}"
    }

    fun formatDuration(minutes: Int): String {
        val hrs = minutes / 60
        val mins = minutes % 60
        return when {
            hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
            hrs > 0 -> "${hrs}h"
            else -> "${mins}m"
        }
    }

    fun currentMinuteOfDay(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun currentHourOfDay(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    fun personalizedGreeting(displayName: String, hour24: Int = currentHourOfDay()): String {
        val firstName = displayName.trim().split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: "there"
        return when (hour24) {
            in 4..11 -> "Good morning, $firstName"
            in 12..16 -> "Good afternoon, $firstName"
            else -> "Good evening, $firstName"
        }
    }

    fun triggerTimeMillis(dateKey: String, minuteOfDay: Int): Long {
        val cal = parseDateKey(dateKey)
        cal.set(Calendar.HOUR_OF_DAY, (minuteOfDay / 60).coerceIn(0, 23))
        cal.set(Calendar.MINUTE, (minuteOfDay % 60).coerceIn(0, 59))
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
