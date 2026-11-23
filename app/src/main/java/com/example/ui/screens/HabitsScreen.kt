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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.ScheduleEngine
import com.example.model.DailyHabit
import com.example.ui.components.CategoryChipBadge
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.DateTimeUtils

@Composable
fun HabitsScreen(
    habits: List<DailyHabit>,
    onAddHabit: () -> Unit,
    onEditHabit: (DailyHabit) -> Unit,
    onToggleHabitToday: (DailyHabit) -> Unit,
    onToggleHabitDate: (DailyHabit, String) -> Unit,
) {
    val todayKey = DateTimeUtils.todayDateKey()
    val weekKeys = remember(todayKey) { DateTimeUtils.weekDateKeys(todayKey) }

    val habitStatsMap = remember(habits, todayKey) {
        habits.associate { it.id to ScheduleEngine.computeHabitStats(it, todayKey) }
    }

    val completedHabits = habits.filter { habitStatsMap[it.id]?.completedToday == true }
    val missedOrPendingHabits = habits.filter { habitStatsMap[it.id]?.completedToday != true }

    val weeklyCompletionPct = remember(habits, habitStatsMap) {
        if (habits.isEmpty()) 0 else {
            val sumDone = habits.sumOf { habitStatsMap[it.id]?.weeklyCompleted ?: 0 }
            val sumTarget = habits.sumOf { habitStatsMap[it.id]?.weeklyTarget ?: 7 }.coerceAtLeast(1)
            ((sumDone.toFloat() / sumTarget.toFloat()) * 100f).toInt().coerceIn(0, 100)
        }
    }

    val monthlyCompletionPct = remember(habits, habitStatsMap) {
        if (habits.isEmpty()) 0 else {
            val sumDone = habits.sumOf { habitStatsMap[it.id]?.monthlyCompleted ?: 0 }
            val sumTarget = habits.sumOf { habitStatsMap[it.id]?.monthlyTarget ?: 1 }.coerceAtLeast(1)
            ((sumDone.toFloat() / sumTarget.toFloat()) * 100f).toInt().coerceIn(0, 100)
        }
    }

    val topStreak = remember(habitStatsMap) {
        habitStatsMap.values.maxOfOrNull { it.currentStreak } ?: 0
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("habits_screen")
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Habit Tracker",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "${completedHabits.size} completed • ${missedOrPendingHabits.size} remaining today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onAddHabit,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("add_habit_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Habit")
                }
            }
        }

        // Weekly, Monthly, and Top Streak Summary
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HabitProgressMetricCard(
                    title = "Weekly",
                    percent = weeklyCompletionPct,
                    subtitle = "vs weekly goal",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                HabitProgressMetricCard(
                    title = "Monthly",
                    percent = monthlyCompletionPct,
                    subtitle = "this month",
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Top Streak", style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            text = "${topStreak}d",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "active days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        if (habits.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TrackChanges,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text("Build lasting daily habits", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Track hydration, reading, workouts, or mindfulness and watch your streaks grow.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onAddHabit,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("empty_add_habit_button")
                        ) {
                            Text("Create First Habit")
                        }
                    }
                }
            }
        } else {
            if (missedOrPendingHabits.isNotEmpty()) {
                item {
                    Text(
                        text = "Due Today / Missed (${missedOrPendingHabits.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                items(missedOrPendingHabits, key = { "pending_${it.id}" }) { habit ->
                    HabitCard(
                        habit = habit,
                        stats = habitStatsMap[habit.id]!!,
                        weekKeys = weekKeys,
                        todayKey = todayKey,
                        onToggleToday = { onToggleHabitToday(habit) },
                        onToggleDate = { dateKey -> onToggleHabitDate(habit, dateKey) },
                        onEdit = { onEditHabit(habit) }
                    )
                }
            }

            if (completedHabits.isNotEmpty()) {
                item {
                    Text(
                        text = "Completed Today (${completedHabits.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(completedHabits, key = { "done_${it.id}" }) { habit ->
                    HabitCard(
                        habit = habit,
                        stats = habitStatsMap[habit.id]!!,
                        weekKeys = weekKeys,
                        todayKey = todayKey,
                        onToggleToday = { onToggleHabitToday(habit) },
                        onToggleDate = { dateKey -> onToggleHabitDate(habit, dateKey) },
                        onEdit = { onEditHabit(habit) }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun HabitProgressMetricCard(
    title: String,
    percent: Int,
    subtitle: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
            LinearProgressIndicator(
                progress = { percent / 100f },
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HabitCard(
    habit: DailyHabit,
    stats: com.example.model.HabitStats,
    weekKeys: List<String>,
    todayKey: String,
    onToggleToday: () -> Unit,
    onToggleDate: (String) -> Unit,
    onEdit: () -> Unit,
) {
    val category = habit.resolvedCategory
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("habit_card_${habit.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onToggleToday,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("habit_toggle_${habit.id}")
                ) {
                    Icon(
                        imageVector = if (stats.completedToday) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (stats.completedToday) "Completed today" else "Mark completed today",
                        tint = if (stats.completedToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryChipBadge(category = category)
                        Text(
                            text = "🔥 ${stats.currentStreak}d streak (Best ${stats.bestStreak}d)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit habit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 7-day interactive week dots
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                weekKeys.forEach { dayKey ->
                    val done = habit.isCompletedOn(dayKey)
                    val isFuture = dayKey > todayKey
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !isFuture) { onToggleDate(dayKey) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = DateTimeUtils.formatShortWeekday(dayKey).take(1),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dayKey == todayKey) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        done -> MaterialTheme.colorScheme.primary
                                        isFuture -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
                                        dayKey < todayKey -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                )
                        ) {
                            if (done) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Done on $dayKey",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else if (dayKey < todayKey) {
                                Text(
                                    text = "–",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Week: ${stats.weeklyCompleted}/${stats.weeklyTarget} days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Month: ${stats.monthlyCompleted}/${stats.monthlyTarget} days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
