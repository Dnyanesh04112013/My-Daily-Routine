package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.ReorganizeResult
import com.example.domain.ScheduleEngine
import com.example.model.ActivityCategory
import com.example.model.ActivityStatus
import com.example.model.DailyHabit
import com.example.model.RoutineActivity
import com.example.model.UserProfile
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.DateTimeUtils

@Composable
fun RoutineActivityCard(
    activity: RoutineActivity,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelayClick: (() -> Unit)? = null,
    isDragging: Boolean = false,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val category = activity.resolvedCategory
    val accent = category.color
    val containerColor = if (isDragging) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else if (activity.completed) {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            width = if (isDragging) 1.5.dp else 1.dp,
            color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("activity_card_${activity.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(if (activity.completed) accent.copy(alpha = 0.35f) else accent)
            )

            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("toggle_complete_${activity.id}")
            ) {
                Icon(
                    imageVector = if (activity.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (activity.completed) "Mark incomplete" else "Mark completed",
                    tint = if (activity.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = DateTimeUtils.formatTimeRange(activity.startMinute, activity.endMinute),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (activity.completed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    if (!activity.flexible) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Fixed time slot",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (activity.reminderEnabled) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = "Reminder active",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = activity.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (activity.completed) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (activity.completed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (activity.description.isNotBlank()) {
                    Text(
                        text = activity.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    CategoryChipBadge(category = category)
                    StatusBadge(
                        text = DateTimeUtils.formatDuration(activity.durationMinutes),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (activity.resolvedStatus == ActivityStatus.DELAYED ||
                        activity.resolvedStatus == ActivityStatus.RESCHEDULED
                    ) {
                        StatusBadge(
                            text = activity.resolvedStatus.label,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (onDelayClick != null && !activity.completed) {
                IconButton(
                    onClick = onDelayClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delay_button_${activity.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = "Delay activity",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (showDragHandle) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = dragHandleModifier
                        .size(48.dp)
                        .testTag("drag_handle_${activity.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapVert,
                        contentDescription = "Long press and drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChipBadge(category: ActivityCategory) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = category.color.copy(alpha = 0.14f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = category.color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = category.label,
                style = MaterialTheme.typography.labelSmall,
                color = category.color
            )
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActivityEditorSheet(
    editingActivity: RoutineActivity?,
    defaultDateKey: String,
    dayActivities: List<RoutineActivity>,
    profile: UserProfile?,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialStart = editingActivity?.startMinute?.toInt() ?: run {
        val earliest = if (defaultDateKey == DateTimeUtils.todayDateKey()) {
            maxOf(((DateTimeUtils.currentMinuteOfDay() + 14) / 15) * 15, (profile?.morningStartMinute ?: 420L).toInt())
        } else {
            (profile?.morningStartMinute ?: 540L).toInt()
        }
        ScheduleEngine.findNextAvailableSlot(
            activities = dayActivities,
            durationMinutes = 45,
            earliestStart = earliest,
            dayEnd = (profile?.dayEndMinute ?: 1320L).toInt(),
            bufferMinutes = (profile?.defaultBufferMinutes ?: 10L).toInt()
        )?.first?.toInt() ?: 540
    }
    val initialEnd = editingActivity?.endMinute?.toInt() ?: (initialStart + 45).coerceAtMost(1440)

    var title by remember { mutableStateOf(editingActivity?.title.orEmpty()) }
    var description by remember { mutableStateOf(editingActivity?.description.orEmpty()) }
    var category by remember { mutableStateOf(editingActivity?.resolvedCategory ?: ActivityCategory.FOCUS) }
    var startMinute by remember { mutableIntStateOf(initialStart) }
    var endMinute by remember { mutableIntStateOf(initialEnd) }
    var flexible by remember { mutableStateOf(editingActivity?.flexible ?: true) }
    var reminderEnabled by remember { mutableStateOf(editingActivity?.reminderEnabled ?: false) }
    var reminderLead by remember { mutableLongStateOf(editingActivity?.reminderMinutesBefore ?: 10L) }
    var autoShiftOverlaps by remember { mutableStateOf(false) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val overlaps = remember(startMinute, endMinute, dayActivities, editingActivity?.id) {
        if (endMinute > startMinute) {
            ScheduleEngine.findOverlaps(
                activities = dayActivities,
                startMinute = startMinute.toLong(),
                endMinute = endMinute.toLong(),
                excludeActivityId = editingActivity?.id
            )
        } else emptyList()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            reminderEnabled = true
        } else {
            reminderEnabled = false
            validationError = "Notification permission denied. Enable notifications in settings to receive alerts."
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = if (editingActivity == null) "New Activity" else "Edit Activity",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = DateTimeUtils.formatDateHeader(defaultDateKey),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_activity_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete activity",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = {
                    if (it.length <= 120) title = it
                    validationError = null
                },
                label = { Text("Activity Name") },
                placeholder = { Text("e.g., Morning Deep Work") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("activity_title_input")
            )

            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 500) description = it },
                label = { Text("Description (optional)") },
                placeholder = { Text("Notes, goals, or prep items") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("activity_desc_input")
            )

            Text("Category", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActivityCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = cat.icon,
                                contentDescription = null,
                                tint = cat.color,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("start_time_button")
                ) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Start", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = DateTimeUtils.formatMinute(startMinute),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily)
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showEndPicker = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("end_time_button")
                ) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("End", style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = DateTimeUtils.formatMinute(endMinute),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily)
                        )
                    }
                }
            }

            // Quick duration pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                listOf(15, 30, 45, 60, 90).forEach { mins ->
                    FilterChip(
                        selected = (endMinute - startMinute) == mins,
                        onClick = {
                            endMinute = (startMinute + mins).coerceAtMost(1440)
                            validationError = null
                        },
                        label = { Text(DateTimeUtils.formatDuration(mins)) }
                    )
                }
            }

            AnimatedVisibility(visible = overlaps.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Time Overlap Detected (${overlaps.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        overlaps.take(2).forEach { conflict ->
                            Text(
                                text = "• ${conflict.title} (${DateTimeUtils.formatTimeRange(conflict.startMinute, conflict.endMinute)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val dur = (endMinute - startMinute).coerceAtLeast(15)
                                    val slot = ScheduleEngine.findNextAvailableSlot(
                                        activities = dayActivities,
                                        durationMinutes = dur,
                                        earliestStart = startMinute,
                                        dayEnd = (profile?.dayEndMinute ?: 1320L).toInt(),
                                        bufferMinutes = (profile?.defaultBufferMinutes ?: 10L).toInt(),
                                        excludeActivityId = editingActivity?.id
                                    )
                                    if (slot != null) {
                                        startMinute = slot.first.toInt()
                                        endMinute = slot.second.toInt()
                                    } else {
                                        validationError = "No open slot remaining before day end."
                                    }
                                },
                                modifier = Modifier.testTag("auto_fit_slot_button")
                            ) {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Auto-Fit Open Slot")
                            }
                            FilterChip(
                                selected = autoShiftOverlaps,
                                onClick = { autoShiftOverlaps = !autoShiftOverlaps },
                                label = { Text("Shift Conflicting") }
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Flexible Time Slot", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (flexible) "Automatically adjusts during delays & reorganization"
                                else "Fixed anchor — stays locked at this exact time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = flexible,
                            onCheckedChange = { flexible = it },
                            modifier = Modifier.testTag("flexible_switch")
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reminder Notification", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (reminderEnabled) "Notify $reminderLead min before start"
                                else "No notification scheduled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        reminderEnabled = true
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    reminderEnabled = enabled
                                }
                            },
                            modifier = Modifier.testTag("reminder_switch")
                        )
                    }

                    AnimatedVisibility(visible = reminderEnabled) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf(0L to "At start", 5L to "5m before", 10L to "10m before", 15L to "15m before", 30L to "30m before")
                                .forEach { (mins, label) ->
                                    FilterChip(
                                        selected = reminderLead == mins,
                                        onClick = { reminderLead = mins },
                                        label = { Text(label) }
                                    )
                                }
                        }
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val trimmed = title.trim()
                    when {
                        trimmed.isEmpty() -> validationError = "Please enter an activity name."
                        endMinute <= startMinute -> validationError = "End time must be after start time."
                        else -> {
                            onSave(
                                defaultDateKey,
                                trimmed,
                                description.trim(),
                                category,
                                startMinute.toLong(),
                                endMinute.toLong(),
                                flexible,
                                reminderEnabled,
                                reminderLead,
                                autoShiftOverlaps
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_activity_button")
            ) {
                Text(
                    text = if (editingActivity == null) "Add to Routine" else "Save Changes",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    if (showStartPicker) {
        MaterialTimePickerDialog(
            title = "Select Start Time",
            initialMinuteOfDay = startMinute,
            onDismiss = { showStartPicker = false },
            onConfirm = { picked ->
                val prevDuration = (endMinute - startMinute).coerceAtLeast(15)
                startMinute = picked.coerceIn(0, 1435)
                if (endMinute <= startMinute) {
                    endMinute = (startMinute + prevDuration).coerceAtMost(1440)
                }
                showStartPicker = false
            }
        )
    }

    if (showEndPicker) {
        MaterialTimePickerDialog(
            title = "Select End Time",
            initialMinuteOfDay = endMinute.coerceAtMost(1439),
            onDismiss = { showEndPicker = false },
            onConfirm = { picked ->
                endMinute = if (picked == 0) 1440 else picked
                validationError = if (endMinute <= startMinute) "End time must be after start time." else null
                showEndPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialTimePickerDialog(
    title: String,
    initialMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val clamped = initialMinuteOfDay.coerceIn(0, 1439)
    val state = rememberTimePickerState(
        initialHour = clamped / 60,
        initialMinute = clamped % 60,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DelayActivityDialog(
    activity: RoutineActivity,
    onDismiss: () -> Unit,
    onDelay: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
        title = { Text("Running Late?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Delay \"${activity.title}\" and automatically shift overlapping flexible activities forward.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        Button(
                            onClick = { onDelay(mins) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("delay_option_$mins")
                        ) {
                            Text("+${mins}m", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ReorganizePreviewDialog(
    result: ReorganizeResult,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Reorganize Remaining Day") },
        text = {
            if (result.updatedActivities.isEmpty()) {
                Text(
                    text = "Your remaining flexible schedule is already clean and non-overlapping.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "${result.movedCount} flexible block(s) will be packed neatly around your completed and fixed anchors:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    result.updatedActivities.take(6).forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = DateTimeUtils.formatTimeRange(item.startMinute, item.endMinute),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    if (result.unscheduledOverflowCount > 0) {
                        Text(
                            text = "Note: ${result.unscheduledOverflowCount} task(s) extend past your configured day end.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (result.updatedActivities.isNotEmpty()) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.testTag("confirm_reorganize_button")
                ) {
                    Text("Apply Schedule")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (result.updatedActivities.isEmpty()) "Close" else "Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditorSheet(
    editingHabit: DailyHabit?,
    onDismiss: () -> Unit,
    onSave: (title: String, category: ActivityCategory, targetDaysPerWeek: Long, reminderTimeMinute: Long) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(editingHabit?.title.orEmpty()) }
    var category by remember { mutableStateOf(editingHabit?.resolvedCategory ?: ActivityCategory.WELLNESS) }
    var targetDays by remember { mutableLongStateOf(editingHabit?.targetDaysPerWeek ?: 7L) }
    var reminderMinute by remember { mutableIntStateOf(editingHabit?.reminderTimeMinute?.toInt() ?: 480) }
    var showTimePicker by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (editingHabit == null) "New Daily Habit" else "Edit Habit",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete habit",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = {
                    if (it.length <= 80) title = it
                    errorText = null
                },
                label = { Text("Habit Name") },
                placeholder = { Text("e.g., Read 20 pages, Hydrate, Meditate") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("habit_title_input")
            )

            Text("Category", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ActivityCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.label) }
                    )
                }
            }

            Text("Weekly Goal (${targetDays} days / week)", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                (1L..7L).forEach { days ->
                    FilterChip(
                        selected = targetDays == days,
                        onClick = { targetDays = days },
                        label = { Text("${days}d") }
                    )
                }
            }

            OutlinedButton(
                onClick = { showTimePicker = true },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Preferred Cue Time: ${DateTimeUtils.formatMinute(reminderMinute)}")
            }

            if (errorText != null) {
                Text(errorText.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isEmpty()) {
                        errorText = "Please enter a habit name."
                    } else {
                        onSave(trimmed, category, targetDays, reminderMinute.toLong())
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_habit_button")
            ) {
                Text(if (editingHabit == null) "Create Habit" else "Save Habit")
            }
        }
    }

    if (showTimePicker) {
        MaterialTimePickerDialog(
            title = "Preferred Cue Time",
            initialMinuteOfDay = reminderMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { picked ->
                reminderMinute = picked
                showTimePicker = false
            }
        )
    }
}
