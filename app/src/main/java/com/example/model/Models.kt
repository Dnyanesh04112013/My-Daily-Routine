package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.CategoryFocusColor
import com.example.ui.theme.CategoryHealthColor
import com.example.ui.theme.CategoryLearningColor
import com.example.ui.theme.CategoryPersonalColor
import com.example.ui.theme.CategoryRestColor
import com.example.ui.theme.CategoryWellnessColor

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class ActivityCategory(
    val label: String,
    val color: Color,
    val icon: ImageVector,
) {
    FOCUS("Deep Focus", CategoryFocusColor, Icons.Rounded.Bolt),
    HEALTH("Health & Fitness", CategoryHealthColor, Icons.Rounded.FitnessCenter),
    WELLNESS("Mindfulness", CategoryWellnessColor, Icons.Rounded.SelfImprovement),
    LEARNING("Learning", CategoryLearningColor, Icons.Rounded.AutoStories),
    PERSONAL("Personal", CategoryPersonalColor, Icons.Rounded.Home),
    REST("Rest & Recharge", CategoryRestColor, Icons.Rounded.Bedtime);

    companion object {
        fun fromRaw(raw: String?): ActivityCategory =
            entries.firstOrNull { it.name == raw } ?: FOCUS
    }
}

enum class ActivityStatus(val label: String) {
    SCHEDULED("Scheduled"),
    DELAYED("Delayed"),
    RESCHEDULED("Rescheduled"),
    COMPLETED("Completed");

    companion object {
        fun fromRaw(raw: String?): ActivityStatus =
            entries.firstOrNull { it.name == raw } ?: SCHEDULED
    }
}

data class UserProfile(
    val id: String = "",
    val userId: String = "",
    val displayName: String = "Planner User",
    val themeMode: String = ThemeMode.SYSTEM.name,
    val morningStartMinute: Long = 420L,
    val dayEndMinute: Long = 1320L,
    val defaultBufferMinutes: Long = 10L,
    val notificationsEnabled: Boolean = true,
) {
    val resolvedThemeMode: ThemeMode
        get() = ThemeMode.fromRaw(themeMode)
}

data class RoutineActivity(
    val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = ActivityCategory.FOCUS.name,
    val startMinute: Long = 540L,
    val endMinute: Long = 600L,
    val sortOrder: Long = 0L,
    val completed: Boolean = false,
    val flexible: Boolean = true,
    val reminderEnabled: Boolean = false,
    val reminderMinutesBefore: Long = 10L,
    val status: String = ActivityStatus.SCHEDULED.name,
) {
    val resolvedCategory: ActivityCategory
        get() = ActivityCategory.fromRaw(category)

    val resolvedStatus: ActivityStatus
        get() = ActivityStatus.fromRaw(status)

    val durationMinutes: Int
        get() = (endMinute - startMinute).toInt().coerceAtLeast(5)
}

data class DailyHabit(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = ActivityCategory.WELLNESS.name,
    val targetDaysPerWeek: Long = 7L,
    val reminderTimeMinute: Long = 480L,
    val completedDates: List<String> = emptyList(),
    val createdDateKey: String = "",
) {
    val resolvedCategory: ActivityCategory
        get() = ActivityCategory.fromRaw(category)

    fun isCompletedOn(dateKey: String): Boolean = completedDates.contains(dateKey)
}

data class HabitStats(
    val currentStreak: Int,
    val bestStreak: Int,
    val weeklyCompleted: Int,
    val weeklyTarget: Int,
    val monthlyCompleted: Int,
    val monthlyTarget: Int,
    val completedToday: Boolean,
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
