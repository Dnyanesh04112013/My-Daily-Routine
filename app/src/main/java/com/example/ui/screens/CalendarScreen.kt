package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.RoutineActivity
import com.example.ui.components.RoutineActivityCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.CalendarViewMode
import com.example.util.DateTimeUtils

@Composable
fun CalendarScreen(
    selectedDateKey: String,
    calendarMode: CalendarViewMode,
    calendarYearMonth: Pair<Int, Int>,
    allActivities: List<RoutineActivity>,
    onSelectDate: (String) -> Unit,
    onChangeMode: (CalendarViewMode) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onAddActivityForDate: (String) -> Unit,
    onEditActivity: (RoutineActivity) -> Unit,
    onToggleComplete: (RoutineActivity) -> Unit,
    onCopyYesterdayToSelected: () -> Unit,
) {
    val activitiesByDate = remember(allActivities) {
        allActivities.groupBy { it.dateKey }
    }
    val selectedDayList = remember(activitiesByDate, selectedDateKey) {
        (activitiesByDate[selectedDateKey] ?: emptyList())
            .sortedWith(compareBy<RoutineActivity> { it.startMinute }.thenBy { it.sortOrder })
    }
    val todayKey = DateTimeUtils.todayDateKey()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("calendar_screen")
    ) {
        // View Mode Segmented Control (Daily / Weekly / Monthly)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Routine Calendar",
                    style = MaterialTheme.typography.headlineMedium
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CalendarViewMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = CalendarViewMode.entries.size
                            ),
                            onClick = { onChangeMode(mode) },
                            selected = calendarMode == mode,
                            modifier = Modifier.testTag("calendar_mode_${mode.name.lowercase()}")
                        ) {
                            Text(mode.label)
                        }
                    }
                }
            }
        }

        when (calendarMode) {
            CalendarViewMode.DAY -> {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            IconButton(
                                onClick = { onSelectDate(DateTimeUtils.addDays(selectedDateKey, -1)) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous Day")
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = DateTimeUtils.formatDateHeader(selectedDateKey),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = if (selectedDateKey == todayKey) "Today" else DateTimeUtils.formatShortWeekday(selectedDateKey),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onSelectDate(DateTimeUtils.addDays(selectedDateKey, 1)) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next Day")
                            }
                        }
                    }
                }
            }

            CalendarViewMode.WEEK -> {
                item {
                    val weekDays = remember(selectedDateKey) { DateTimeUtils.weekDateKeys(selectedDateKey) }
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = { onSelectDate(DateTimeUtils.addDays(selectedDateKey, -7)) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous week")
                                }
                                Text(
                                    text = "${DateTimeUtils.formatShortDate(weekDays.first())} – ${DateTimeUtils.formatShortDate(weekDays.last())}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                IconButton(
                                    onClick = { onSelectDate(DateTimeUtils.addDays(selectedDateKey, 7)) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next week")
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                weekDays.forEach { dayKey ->
                                    val isSelected = dayKey == selectedDateKey
                                    val isToday = dayKey == todayKey
                                    val count = activitiesByDate[dayKey]?.size ?: 0
                                    val completed = activitiesByDate[dayKey]?.count { it.completed } ?: 0

                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                            else -> MaterialTheme.colorScheme.surfaceContainer
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onSelectDate(dayKey) }
                                            .testTag("week_day_$dayKey")
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp)
                                        ) {
                                            Text(
                                                text = DateTimeUtils.formatShortWeekday(dayKey),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = DateTimeUtils.dayOfMonth(dayKey).toString(),
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (count > 0) "$completed/$count" else "•",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                                else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            CalendarViewMode.MONTH -> {
                item {
                    val (year, month) = calendarYearMonth
                    val monthKeys = remember(year, month) { DateTimeUtils.monthDateKeys(year, month) }
                    val leadingEmpty = remember(year, month) { DateTimeUtils.leadingEmptyDaysInMonth(year, month) }
                    val totalCells = leadingEmpty + monthKeys.size
                    val rowCount = (totalCells + 6) / 7

                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(onClick = { onShiftMonth(-1) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
                                }
                                Text(
                                    text = DateTimeUtils.formatMonthYear(year, month),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                IconButton(onClick = { onShiftMonth(1) }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth()) {
                                listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                                    Text(
                                        text = d,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            for (row in 0 until rowCount) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    for (col in 0 until 7) {
                                        val cellIndex = row * 7 + col
                                        val dayIndex = cellIndex - leadingEmpty
                                        if (dayIndex in monthKeys.indices) {
                                            val dayKey = monthKeys[dayIndex]
                                            val isSelected = dayKey == selectedDateKey
                                            val isToday = dayKey == todayKey
                                            val dayTasks = activitiesByDate[dayKey] ?: emptyList()

                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                    else -> MaterialTheme.colorScheme.surface
                                                },
                                                border = if (isToday && !isSelected) {
                                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                                } else null,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clickable { onSelectDate(dayKey) }
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = (dayIndex + 1).toString(),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (dayTasks.isNotEmpty()) {
                                                        Spacer(Modifier.height(2.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .size(5.dp)
                                                                .clip(CircleShape)
                                                                .background(
                                                                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                                    else if (dayTasks.all { it.completed }) MaterialTheme.colorScheme.primary
                                                                    else MaterialTheme.colorScheme.secondary
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Actions for Selected Date (Add Activity + Copy Previous Day)
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onAddActivityForDate(selectedDateKey) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("calendar_add_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Plan ${DateTimeUtils.formatShortDate(selectedDateKey)}")
                }

                OutlinedButton(
                    onClick = onCopyYesterdayToSelected,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("calendar_copy_yesterday_button")
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Previous Day")
                }
            }
        }

        item {
            Text(
                text = "Schedule for ${DateTimeUtils.formatDateHeader(selectedDateKey)}",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (selectedDayList.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "No routine saved for this date yet",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Create custom blocks or copy from the previous day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(selectedDayList, key = { it.id }) { activity ->
                RoutineActivityCard(
                    activity = activity,
                    onToggleComplete = { onToggleComplete(activity) },
                    onEdit = { onEditActivity(activity) }
                )
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}
