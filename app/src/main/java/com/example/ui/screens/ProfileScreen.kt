package com.example.ui.screens

import android.app.DownloadManager
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.ScheduleEngine
import com.example.model.DailyHabit
import com.example.model.RoutineActivity
import com.example.model.ThemeMode
import com.example.model.UserProfile
import com.example.ui.components.MaterialTimePickerDialog
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.ApkExporter
import com.example.util.DateTimeUtils
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    profile: UserProfile?,
    allActivities: List<RoutineActivity>,
    habits: List<DailyHabit>,
    onSavePreferences: (
        displayName: String,
        themeMode: ThemeMode,
        morningStartMinute: Long,
        dayEndMinute: Long,
        defaultBufferMinutes: Long,
        notificationsEnabled: Boolean,
        showToast: Boolean,
    ) -> Unit,
    onShowMessage: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apkMeta = remember(context) { ApkExporter.getApkMetadata(context) }
    var isExportingApk by remember { mutableStateOf(false) }
    var savedApkStatus by remember { mutableStateOf<String?>(null) }

    val saveApkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ApkExporter.APK_MIME_TYPE)
    ) { destinationUri ->
        if (destinationUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isExportingApk = true
            val result = ApkExporter.exportApkToUri(context, destinationUri)
            isExportingApk = false
            result.onSuccess {
                val msg = "Saved ${apkMeta.suggestedFileName}"
                savedApkStatus = msg
                onShowMessage(msg)
            }.onFailure { err ->
                onShowMessage(err.message ?: "Failed to save APK file.")
            }
        }
    }
    var displayName by remember(profile?.displayName) {
        mutableStateOf(profile?.displayName ?: "Planner User")
    }
    var themeMode by remember(profile?.themeMode) {
        mutableStateOf(profile?.resolvedThemeMode ?: ThemeMode.SYSTEM)
    }
    var morningStart by remember(profile?.morningStartMinute) {
        mutableIntStateOf((profile?.morningStartMinute ?: 420L).toInt())
    }
    var dayEnd by remember(profile?.dayEndMinute) {
        mutableIntStateOf((profile?.dayEndMinute ?: 1320L).toInt())
    }
    var bufferMins by remember(profile?.defaultBufferMinutes) {
        mutableLongStateOf(profile?.defaultBufferMinutes ?: 10L)
    }
    var notificationsEnabled by remember(profile?.notificationsEnabled) {
        mutableStateOf(profile?.notificationsEnabled ?: true)
    }

    LaunchedEffect(profile) {
        if (profile != null) {
            displayName = profile.displayName
            themeMode = profile.resolvedThemeMode
            morningStart = profile.morningStartMinute.toInt()
            dayEnd = profile.dayEndMinute.toInt()
            bufferMins = profile.defaultBufferMinutes
            notificationsEnabled = profile.notificationsEnabled
        }
    }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val totalCompletedTasks = remember(allActivities) { allActivities.count { it.completed } }
    val bestStreak = remember(habits) {
        habits.maxOfOrNull { ScheduleEngine.computeHabitStats(it).bestStreak } ?: 0
    }

    val currentName = profile?.displayName ?: "Planner User"
    val initials = remember(currentName) {
        currentName.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "U" }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_screen")
    ) {
        item {
            Text("Profile & Settings", style = MaterialTheme.typography.headlineMedium)
        }

        // User Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentName,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Saved on Device",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // App Package (APK) Download & Share — placed prominently above the fold
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apk_download_card")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.InstallMobile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.apk_section_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (apkMeta.isAvailable) {
                                    "Version ${apkMeta.versionName} • ${apkMeta.formattedSize}"
                                } else {
                                    "Version ${apkMeta.versionName}"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.apk_section_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    savedApkStatus?.let { status ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
                                        }.onFailure {
                                            onShowMessage("Saved to Downloads: ${apkMeta.suggestedFileName}")
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Open")
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (!apkMeta.isAvailable) {
                                    onShowMessage("APK package is not accessible on this device.")
                                    return@Button
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    scope.launch {
                                        isExportingApk = true
                                        val result = ApkExporter.saveApkToDownloads(
                                            context = context,
                                            fileName = apkMeta.suggestedFileName
                                        )
                                        isExportingApk = false
                                        result.onSuccess { msg ->
                                            savedApkStatus = msg
                                            onShowMessage(msg)
                                        }.onFailure {
                                            saveApkPickerLauncher.launch(apkMeta.suggestedFileName)
                                        }
                                    }
                                } else {
                                    saveApkPickerLauncher.launch(apkMeta.suggestedFileName)
                                }
                            },
                            enabled = !isExportingApk,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("download_apk_button")
                        ) {
                            if (isExportingApk) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.apk_download_button),
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (!apkMeta.isAvailable) {
                                    onShowMessage("APK package is not accessible on this device.")
                                    return@OutlinedButton
                                }
                                scope.launch {
                                    isExportingApk = true
                                    val result = ApkExporter.prepareShareApkIntent(
                                        context = context,
                                        fileName = apkMeta.suggestedFileName
                                    )
                                    isExportingApk = false
                                    result.onSuccess { shareIntent ->
                                        runCatching {
                                            context.startActivity(shareIntent)
                                        }.onFailure {
                                            onShowMessage("No app available to share the APK file.")
                                        }
                                    }.onFailure { err ->
                                        onShowMessage(err.message ?: "Unable to prepare APK for sharing.")
                                    }
                                }
                            },
                            enabled = !isExportingApk,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_apk_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.apk_share_button),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Stats Summary
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileStatBox(
                    icon = Icons.Rounded.CheckCircle,
                    label = "Tasks Done",
                    value = totalCompletedTasks.toString(),
                    modifier = Modifier.weight(1f)
                )
                ProfileStatBox(
                    icon = Icons.Rounded.LocalFireDepartment,
                    label = "Best Streak",
                    value = "${bestStreak}d",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Theme Mode Switcher
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { idx, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = {
                                    themeMode = mode
                                    onSavePreferences(
                                        displayName.ifBlank { currentName },
                                        mode,
                                        morningStart.toLong(),
                                        dayEnd.toLong(),
                                        bufferMins,
                                        notificationsEnabled,
                                        false
                                    )
                                },
                                shape = SegmentedButtonDefaults.itemShape(idx, ThemeMode.entries.size),
                                modifier = Modifier.testTag("theme_${mode.name.lowercase()}")
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }
        }

        // Routine & Flexibility Settings
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Schedule Preferences", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { if (it.length <= 40) displayName = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { showStartPicker = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text("Day Starts", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = DateTimeUtils.formatMinute(morningStart),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { showEndPicker = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(52.dp)
                        ) {
                            Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text("Day Ends", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = DateTimeUtils.formatMinute(dayEnd),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                                )
                            }
                        }
                    }

                    Text("Reorganize Buffer Between Tasks", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0L, 5L, 10L, 15L, 20L).forEach { mins ->
                            FilterChip(
                                selected = bufferMins == mins,
                                onClick = { bufferMins = mins },
                                label = { Text("${mins}m") }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Routine Reminders", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Receive local alerts before scheduled blocks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it }
                        )
                    }

                    Button(
                        onClick = {
                            onSavePreferences(
                                displayName.ifBlank { currentName },
                                themeMode,
                                morningStart.toLong(),
                                dayEnd.toLong().coerceAtLeast(morningStart.toLong() + 60L),
                                bufferMins,
                                notificationsEnabled,
                                true
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_profile_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Preferences")
                    }
                }
            }
        }

        // Sign Out
        item {
            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign Out")
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }

    if (showStartPicker) {
        MaterialTimePickerDialog(
            title = "Day Start Time",
            initialMinuteOfDay = morningStart,
            onDismiss = { showStartPicker = false },
            onConfirm = { picked ->
                morningStart = picked.coerceAtMost(1380)
                if (dayEnd <= morningStart) {
                    dayEnd = (morningStart + 60).coerceAtMost(1440)
                }
                showStartPicker = false
            }
        )
    }

    if (showEndPicker) {
        MaterialTimePickerDialog(
            title = "Day End Time",
            initialMinuteOfDay = dayEnd.coerceAtMost(1439),
            onDismiss = { showEndPicker = false },
            onConfirm = { picked ->
                val adjusted = if (picked == 0) 1440 else picked
                if (adjusted > morningStart) {
                    dayEnd = adjusted
                }
                showEndPicker = false
            }
        )
    }
}

@Composable
private fun ProfileStatBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
