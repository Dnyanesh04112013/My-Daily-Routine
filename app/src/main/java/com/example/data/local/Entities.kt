package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.DailyHabit
import com.example.model.RoutineActivity
import com.example.model.UserProfile

@Entity(
    tableName = "users",
    indices = [Index(value = ["usernameLower"], unique = true)]
)
data class UserEntity(
    @PrimaryKey val userId: String,
    val username: String,
    val usernameLower: String,
    val passwordSalt: String,
    val passwordHash: String,
    val themeMode: String,
    val morningStartMinute: Long,
    val dayEndMinute: Long,
    val defaultBufferMinutes: Long,
    val notificationsEnabled: Boolean,
    val createdAtMillis: Long,
) {
    fun toDomain(): UserProfile = UserProfile(
        id = userId,
        userId = userId,
        displayName = username,
        themeMode = themeMode,
        morningStartMinute = morningStartMinute,
        dayEndMinute = dayEndMinute,
        defaultBufferMinutes = defaultBufferMinutes,
        notificationsEnabled = notificationsEnabled
    )
}

@Entity(
    tableName = "activities",
    indices = [Index(value = ["userId"]), Index(value = ["dateKey"])]
)
data class ActivityEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val dateKey: String,
    val title: String,
    val description: String,
    val category: String,
    val startMinute: Long,
    val endMinute: Long,
    val sortOrder: Long,
    val completed: Boolean,
    val flexible: Boolean,
    val reminderEnabled: Boolean,
    val reminderMinutesBefore: Long,
    val status: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toDomain(): RoutineActivity = RoutineActivity(
        id = id,
        userId = userId,
        dateKey = dateKey,
        title = title,
        description = description,
        category = category,
        startMinute = startMinute,
        endMinute = endMinute,
        sortOrder = sortOrder,
        completed = completed,
        flexible = flexible,
        reminderEnabled = reminderEnabled,
        reminderMinutesBefore = reminderMinutesBefore,
        status = status
    )
}

@Entity(
    tableName = "habits",
    indices = [Index(value = ["userId"])]
)
data class HabitEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val category: String,
    val targetDaysPerWeek: Long,
    val reminderTimeMinute: Long,
    val completedDatesCsv: String,
    val createdDateKey: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    fun toDomain(): DailyHabit = DailyHabit(
        id = id,
        userId = userId,
        title = title,
        category = category,
        targetDaysPerWeek = targetDaysPerWeek,
        reminderTimeMinute = reminderTimeMinute,
        completedDates = completedDatesCsv.split(",").filter { it.isNotBlank() },
        createdDateKey = createdDateKey
    )
}
