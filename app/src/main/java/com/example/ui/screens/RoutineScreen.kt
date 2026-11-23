package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.domain.ScheduleEngine
import com.example.model.RoutineActivity
import com.example.ui.components.RoutineActivityCard
import com.example.util.DateTimeUtils

@Composable
fun RoutineScreen(
    selectedDateKey: String,
    allActivities: List<RoutineActivity>,
    onSelectDate: (String) -> Unit,
    onAddActivity: () -> Unit,
    onEditActivity: (RoutineActivity) -> Unit,
    onToggleComplete: (RoutineActivity) -> Unit,
    onDelayActivity: (RoutineActivity) -> Unit,
    onReorganizeDay: () -> Unit,
    onCopyYesterday: () -> Unit,
    onCommitReorder: (List<RoutineActivity>, reflowTimes: Boolean) -> Unit,
) {
    val serverDayList = remember(allActivities, selectedDateKey) {
        allActivities
            .filter { it.dateKey == selectedDateKey }
            .sortedWith(compareBy<RoutineActivity> { it.sortOrder }.thenBy { it.startMinute })
    }

    var localList by remember { mutableStateOf(serverDayList) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var autoReflowOnDrag by remember { mutableStateOf(true) }

    LaunchedEffect(serverDayList) {
        if (draggedId == null) {
            localList = serverDayList
        }
    }

    // Detect any overlapping pairs on this day
    val overlappingIds = remember(localList) {
        val ids = mutableSetOf<String>()
        for (act in localList) {
            val overlaps = ScheduleEngine.findOverlaps(
                activities = localList,
                startMinute = act.startMinute,
                endMinute = act.endMinute,
                excludeActivityId = act.id
            )
            if (overlaps.isNotEmpty()) {
                ids += act.id
                overlaps.forEach { ids += it.id }
            }
        }
        ids
    }

    val itemHeightPx = with(LocalDensity.current) { 104.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("routine_screen")
    ) {
        // Date Navigation Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { onSelectDate(DateTimeUtils.addDays(selectedDateKey, -1)) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Previous day"
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = DateTimeUtils.formatDateHeader(selectedDateKey),
                            style = MaterialTheme.typography.titleLarge
                        )
                        val label = when (selectedDateKey) {
                            DateTimeUtils.todayDateKey() -> "Today"
                            DateTimeUtils.tomorrowDateKey() -> "Tomorrow"
                            DateTimeUtils.yesterdayDateKey() -> "Yesterday"
                            else -> DateTimeUtils.formatShortWeekday(selectedDateKey)
                        }
                        Text(
                            text = "$label • ${localList.size} activities",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { onSelectDate(DateTimeUtils.addDays(selectedDateKey, 1)) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Next day"
                        )
                    }
                }

                // Flexible Adjustment Action Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onReorganizeDay,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("routine_reorganize_button")
                    ) {
                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reorganize Day", style = MaterialTheme.typography.labelMedium)
                    }

                    FilterChip(
                        selected = autoReflowOnDrag,
                        onClick = { autoReflowOnDrag = !autoReflowOnDrag },
                        label = { Text("Reflow on Drag", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Rounded.SwapVert, contentDescription = null, modifier = Modifier.size(15.dp))
                        }
                    )
                }

                AnimatedVisibility(visible = overlappingIds.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${overlappingIds.size} overlapping tasks detected",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            TextButton(onClick = onReorganizeDay) {
                                Text("Fix Now", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
        }

        if (localList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "No activities scheduled",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Tap + to build your routine for ${DateTimeUtils.formatShortDate(selectedDateKey)}, or copy yesterday's plan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onAddActivity,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Activity")
                        }
                        OutlinedButton(
                            onClick = onCopyYesterday,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Previous Day")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Long-press ⇅ handle to drag & reorder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onCopyYesterday) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Yesterday", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                itemsIndexed(localList, key = { _, item -> item.id }) { index, activity ->
                    val isDragging = draggedId == activity.id
                    val handleModifier = Modifier.pointerInput(activity.id, localList.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedId = activity.id
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                                val currentIdx = localList.indexOfFirst { it.id == activity.id }
                                if (currentIdx != -1) {
                                    val steps = (dragOffsetY / itemHeightPx).toInt()
                                    val targetIdx = (currentIdx + steps).coerceIn(0, localList.lastIndex)
                                    if (targetIdx != currentIdx) {
                                        val mutable = localList.toMutableList()
                                        val movedItem = mutable.removeAt(currentIdx)
                                        mutable.add(targetIdx, movedItem)
                                        localList = mutable
                                        dragOffsetY -= (targetIdx - currentIdx) * itemHeightPx
                                    }
                                }
                            },
                            onDragEnd = {
                                draggedId = null
                                dragOffsetY = 0f
                                onCommitReorder(localList, autoReflowOnDrag)
                            },
                            onDragCancel = {
                                draggedId = null
                                dragOffsetY = 0f
                                localList = serverDayList
                            }
                        )
                    }

                    RoutineActivityCard(
                        activity = activity,
                        onToggleComplete = { onToggleComplete(activity) },
                        onEdit = { onEditActivity(activity) },
                        onDelayClick = { onDelayActivity(activity) },
                        isDragging = isDragging,
                        showDragHandle = true,
                        dragHandleModifier = handleModifier,
                        modifier = Modifier
                            .zIndex(if (isDragging) 10f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else 0f
                                scaleX = if (isDragging) 1.02f else 1f
                                scaleY = if (isDragging) 1.02f else 1f
                            }
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
