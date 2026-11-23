package com.example.auth

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.UserEntity
import com.example.model.ThemeMode
import com.example.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

class LocalAuthManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dao = AppDatabase.getInstance(appContext).routineDao()

    private val _activeUserId = MutableStateFlow(prefs.getString(KEY_ACTIVE_USER_ID, null))
    val activeUserId: StateFlow<String?> = _activeUserId.asStateFlow()

    suspend fun createAccount(username: String, password: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUsername = username.trim()
                val usernameLower = cleanUsername.lowercase(Locale.US)
                require(cleanUsername.length in 2..40) {
                    "Username must be between 2 and 40 characters."
                }
                require(password.length >= 4) {
                    "Password must be at least 4 characters."
                }
                require(password.length <= 128) {
                    "Password is too long."
                }

                val existing = dao.getUserByUsername(usernameLower)
                if (existing != null) {
                    return@withContext Result.failure(
                        IllegalStateException("Username \"$cleanUsername\" already exists. Try signing in.")
                    )
                }

                val (salt, hash) = PasswordHasher.hashPassword(password)
                val userId = UUID.randomUUID().toString()
                val entity = UserEntity(
                    userId = userId,
                    username = cleanUsername,
                    usernameLower = usernameLower,
                    passwordSalt = salt,
                    passwordHash = hash,
                    themeMode = ThemeMode.SYSTEM.name,
                    morningStartMinute = 420L,
                    dayEndMinute = 1320L,
                    defaultBufferMinutes = 10L,
                    notificationsEnabled = true,
                    createdAtMillis = System.currentTimeMillis()
                )
                dao.insertUser(entity)
                setSession(userId)
                Result.success(entity.toDomain())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun signIn(username: String, password: String): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val cleanUsername = username.trim()
                val usernameLower = cleanUsername.lowercase(Locale.US)
                require(cleanUsername.isNotEmpty()) { "Please enter your username." }
                require(password.isNotEmpty()) { "Please enter your password." }

                val user = dao.getUserByUsername(usernameLower)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("No account found for \"$cleanUsername\". Create an account first.")
                    )

                if (!PasswordHasher.verifyPassword(password, user.passwordSalt, user.passwordHash)) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Incorrect password. Please try again.")
                    )
                }

                setSession(user.userId)
                Result.success(user.toDomain())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun signOut() {
        prefs.edit().remove(KEY_ACTIVE_USER_ID).apply()
        _activeUserId.value = null
    }

    private fun setSession(userId: String) {
        prefs.edit().putString(KEY_ACTIVE_USER_ID, userId).apply()
        _activeUserId.value = userId
    }

    companion object {
        private const val PREFS_NAME = "routine_auth_session"
        private const val KEY_ACTIVE_USER_ID = "active_user_id"

        @Volatile
        private var INSTANCE: LocalAuthManager? = null

        fun getInstance(context: Context): LocalAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalAuthManager(context).also { INSTANCE = it }
            }
        }
    }
}
