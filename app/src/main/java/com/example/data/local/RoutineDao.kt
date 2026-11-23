package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    fun observeUser(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE usernameLower = :usernameLower LIMIT 1")
    suspend fun getUserByUsername(usernameLower: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT * FROM activities WHERE userId = :userId ORDER BY dateKey ASC, startMinute ASC, sortOrder ASC")
    fun observeActivities(userId: String): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE userId = :userId AND id = :activityId LIMIT 1")
    suspend fun getActivity(userId: String, activityId: String): ActivityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivity(activity: ActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivities(activities: List<ActivityEntity>)

    @Transaction
    suspend fun applyActivityUpdates(activities: List<ActivityEntity>) {
        upsertActivities(activities)
    }

    @Query("DELETE FROM activities WHERE userId = :userId AND id = :activityId")
    suspend fun deleteActivity(userId: String, activityId: String)

    @Query("SELECT * FROM habits WHERE userId = :userId ORDER BY reminderTimeMinute ASC")
    fun observeHabits(userId: String): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE userId = :userId AND id = :habitId LIMIT 1")
    suspend fun getHabit(userId: String, habitId: String): HabitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE userId = :userId AND id = :habitId")
    suspend fun deleteHabit(userId: String, habitId: String)
}
