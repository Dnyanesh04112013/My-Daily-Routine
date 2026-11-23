package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ListAlt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.LocalAuthManager
import com.example.data.RoutineRepository
import com.example.data.local.AppDatabase
import com.example.domain.ReorganizeResult
import com.example.model.DailyHabit
import com.example.model.RoutineActivity
import com.example.model.ThemeMode
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.notifications.RoutineReminderReceiver
import com.example.ui.auth.AuthScreen
import com.example.ui.components.ActivityEditorSheet
import com.example.ui.components.DelayActivityDialog
import com.example.ui.components.HabitEditorSheet
import com.example.ui.components.ReorganizePreviewDialog
import com.example.ui.screens.CalendarScreen
import com.example.ui.screens.HabitsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RoutineScreen
import com.example.ui.theme.MyDailyRoutineTheme
import com.example.ui.viewmodel.RoutineViewModel
import com.example.util.DateTimeUtils

const val ROUTE_HOME = "home"
const val ROUTE_ROUTINE = "my_routine"
const val ROUTE_CALENDAR = "calendar"
const val ROUTE_HABITS = "habits"
const val ROUTE_PROFILE = "profile"

enum class BottomNavTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    HOME(ROUTE_HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home"),
    ROUTINE(ROUTE_ROUTINE, R.string.nav_routine, Icons.Rounded.ListAlt, Icons.Outlined.ListAlt, "nav_routine"),
    CALENDAR(ROUTE_CALENDAR, R.string.nav_calendar, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_calendar"),
    HABITS(ROUTE_HABITS, R.string.nav_habits, Icons.Rounded.CheckCircle, Icons.Outlined.CheckCircle, "nav_habits"),
    PROFILE(ROUTE_PROFILE, R.string.nav_profile, Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RoutineReminderReceiver.ensureChannel(this)
        setContent {
            AppRoot()
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val authManager = remember(context) { LocalAuthManager.getInstance(context) }
    val activeUserId by authManager.activeUserId.collectAsStateWithLifecycle()
    var themeMode by remember { mutableStateOf(ThemeMode.SYSTEM) }

    LaunchedEffect(activeUserId) {
        if (activeUserId == null) {
            themeMode = ThemeMode.SYSTEM
        }
    }

    MyDailyRoutineTheme(themeMode = themeMode) {
        val uid = activeUserId
        if (uid == null) {
            AuthScreen(authManager = authManager)
        } else {
            key(uid) {
                MainRoutineApp(
                    currentUserId = uid,
                    onSignOut = { authManager.signOut() },
                    onThemeModeChanged = { themeMode = it }
                )
            }
        }
    }
}

@Composable
fun MainRoutineApp(
    currentUserId: String,
    onSignOut: () -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
) {
    val context = LocalContext.current

    val viewModel: RoutineViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = AppDatabase.getInstance(app)
                RoutineViewModel(
                    repository = RoutineRepository(db.routineDao()),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val activitiesState by viewModel.activitiesState.collectAsStateWithLifecycle()
    val habitsState by viewModel.habitsState.collectAsStateWithLifecycle()
    val selectedDateKey by viewModel.selectedDateKey.collectAsStateWithLifecycle()
    val calendarMode by viewModel.calendarMode.collectAsStateWithLifecycle()
    val calendarYearMonth by viewModel.calendarYearMonth.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()

    val profile: UserProfile? = (profileState as? UiState.Success)?.data
    val allActivities: List<RoutineActivity> = (activitiesState as? UiState.Success)?.data ?: emptyList()
    val habits: List<DailyHabit> = (habitsState as? UiState.Success)?.data ?: emptyList()

    LaunchedEffect(profile?.themeMode) {
        if (profile != null) {
            onThemeModeChanged(profile.resolvedThemeMode)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_HOME

    var showActivitySheet by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<RoutineActivity?>(null) }
    var activitySheetDateKey by remember { mutableStateOf(DateTimeUtils.todayDateKey()) }

    var delayTargetActivity by remember { mutableStateOf<RoutineActivity?>(null) }
    var reorganizePreview by remember { mutableStateOf<ReorganizeResult?>(null) }

    var showHabitSheet by remember { mutableStateOf(false) }
    var editingHabit by remember { mutableStateOf<DailyHabit?>(null) }

    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        BottomNavTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentRoute == ROUTE_ROUTINE || currentRoute == ROUTE_HOME) {
                    FloatingActionButton(
                        onClick = {
                            editingActivity = null
                            activitySheetDateKey = if (currentRoute == ROUTE_HOME) {
                                DateTimeUtils.todayDateKey()
                            } else {
                                selectedDateKey
                            }
                            showActivitySheet = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_activity")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add activity")
                    }
                } else if (currentRoute == ROUTE_HABITS) {
                    FloatingActionButton(
                        onClick = {
                            editingHabit = null
                            showHabitSheet = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_habit")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add habit")
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail {
                        BottomNavTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        AnimatedVisibility(visible = isMutating) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        if (activitiesState is UiState.Loading && habitsState is UiState.Loading) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (activitiesState is UiState.Error) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp)
                            ) {
                                Text(
                                    text = (activitiesState as UiState.Error).message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            NavHost(
                                navController = navController,
                                startDestination = ROUTE_HOME,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable(ROUTE_HOME) {
                                    val todayKey = DateTimeUtils.todayDateKey()
                                    val todayList = allActivities.filter { it.dateKey == todayKey }
                                    HomeScreen(
                                        profile = profile,
                                        allActivities = allActivities,
                                        onAddActivity = {
                                            editingActivity = null
                                            activitySheetDateKey = todayKey
                                            showActivitySheet = true
                                        },
                                        onEditActivity = { act ->
                                            editingActivity = act
                                            activitySheetDateKey = act.dateKey
                                            showActivitySheet = true
                                        },
                                        onToggleComplete = { act ->
                                            viewModel.toggleActivityCompleted(context, act, profile)
                                        },
                                        onDelayActivity = { act ->
                                            delayTargetActivity = act
                                        },
                                        onReorganizeDay = {
                                            reorganizePreview = viewModel.previewReorganizeRemainingDay(
                                                dayActivities = todayList,
                                                dateKey = todayKey,
                                                profile = profile
                                            )
                                        },
                                        onRescheduleUnfinishedToday = { task ->
                                            viewModel.rescheduleUnfinishedTask(
                                                context = context,
                                                task = task,
                                                targetDateKey = todayKey,
                                                targetDateActivities = todayList,
                                                profile = profile
                                            )
                                        },
                                        onRescheduleUnfinishedTomorrow = { task ->
                                            val tomorrowKey = DateTimeUtils.tomorrowDateKey()
                                            viewModel.rescheduleUnfinishedTask(
                                                context = context,
                                                task = task,
                                                targetDateKey = tomorrowKey,
                                                targetDateActivities = allActivities.filter { it.dateKey == tomorrowKey },
                                                profile = profile
                                            )
                                        },
                                        onCopyYesterdayToToday = {
                                            viewModel.copyRoutineFromDate(
                                                sourceDateKey = DateTimeUtils.yesterdayDateKey(),
                                                targetDateKey = todayKey,
                                                allActivities = allActivities
                                            )
                                        },
                                        onLoadStarterTemplate = {
                                            viewModel.addStarterRoutine(todayKey)
                                        },
                                        onOpenRoutineTab = {
                                            viewModel.selectDate(todayKey)
                                            navigateToTab(ROUTE_ROUTINE)
                                        }
                                    )
                                }

                                composable(ROUTE_ROUTINE) {
                                    val dayList = allActivities.filter { it.dateKey == selectedDateKey }
                                    RoutineScreen(
                                        selectedDateKey = selectedDateKey,
                                        allActivities = allActivities,
                                        onSelectDate = { viewModel.selectDate(it) },
                                        onAddActivity = {
                                            editingActivity = null
                                            activitySheetDateKey = selectedDateKey
                                            showActivitySheet = true
                                        },
                                        onEditActivity = { act ->
                                            editingActivity = act
                                            activitySheetDateKey = act.dateKey
                                            showActivitySheet = true
                                        },
                                        onToggleComplete = { act ->
                                            viewModel.toggleActivityCompleted(context, act, profile)
                                        },
                                        onDelayActivity = { act ->
                                            delayTargetActivity = act
                                        },
                                        onReorganizeDay = {
                                            reorganizePreview = viewModel.previewReorganizeRemainingDay(
                                                dayActivities = dayList,
                                                dateKey = selectedDateKey,
                                                profile = profile
                                            )
                                        },
                                        onCopyYesterday = {
                                            viewModel.copyRoutineFromDate(
                                                sourceDateKey = DateTimeUtils.addDays(selectedDateKey, -1),
                                                targetDateKey = selectedDateKey,
                                                allActivities = allActivities
                                            )
                                        },
                                        onCommitReorder = { reordered, reflow ->
                                            viewModel.reorderActivities(context, reordered, reflow, profile)
                                        }
                                    )
                                }

                                composable(ROUTE_CALENDAR) {
                                    CalendarScreen(
                                        selectedDateKey = selectedDateKey,
                                        calendarMode = calendarMode,
                                        calendarYearMonth = calendarYearMonth,
                                        allActivities = allActivities,
                                        onSelectDate = { viewModel.selectDate(it) },
                                        onChangeMode = { viewModel.setCalendarMode(it) },
                                        onShiftMonth = { viewModel.shiftCalendarMonth(it) },
                                        onAddActivityForDate = { dateKey ->
                                            editingActivity = null
                                            activitySheetDateKey = dateKey
                                            showActivitySheet = true
                                        },
                                        onEditActivity = { act ->
                                            editingActivity = act
                                            activitySheetDateKey = act.dateKey
                                            showActivitySheet = true
                                        },
                                        onToggleComplete = { act ->
                                            viewModel.toggleActivityCompleted(context, act, profile)
                                        },
                                        onCopyYesterdayToSelected = {
                                            viewModel.copyRoutineFromDate(
                                                sourceDateKey = DateTimeUtils.addDays(selectedDateKey, -1),
                                                targetDateKey = selectedDateKey,
                                                allActivities = allActivities
                                            )
                                        }
                                    )
                                }

                                composable(ROUTE_HABITS) {
                                    HabitsScreen(
                                        habits = habits,
                                        onAddHabit = {
                                            editingHabit = null
                                            showHabitSheet = true
                                        },
                                        onEditHabit = { habit ->
                                            editingHabit = habit
                                            showHabitSheet = true
                                        },
                                        onToggleHabitToday = { habit ->
                                            viewModel.toggleHabitForDate(habit, DateTimeUtils.todayDateKey())
                                        },
                                        onToggleHabitDate = { habit, dateKey ->
                                            viewModel.toggleHabitForDate(habit, dateKey)
                                        }
                                    )
                                }

                                composable(ROUTE_PROFILE) {
                                    ProfileScreen(
                                        profile = profile,
                                        allActivities = allActivities,
                                        habits = habits,
                                        onSavePreferences = { name, mode, startMin, endMin, buffer, notif, showToast ->
                                            onThemeModeChanged(mode)
                                            viewModel.updateProfilePreferences(
                                                displayName = name,
                                                themeMode = mode,
                                                morningStartMinute = startMin,
                                                dayEndMinute = endMin,
                                                defaultBufferMinutes = buffer,
                                                notificationsEnabled = notif,
                                                showToast = showToast
                                            )
                                        },
                                        onShowMessage = { msg ->
                                            uiScope.launch { snackbarHostState.showSnackbar(msg) }
                                        },
                                        onSignOut = onSignOut
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showActivitySheet) {
        val dayActivities = remember(allActivities, activitySheetDateKey) {
            allActivities.filter { it.dateKey == activitySheetDateKey }
        }
        ActivityEditorSheet(
            editingActivity = editingActivity,
            defaultDateKey = activitySheetDateKey,
            dayActivities = dayActivities,
            profile = profile,
            onDismiss = { showActivitySheet = false },
            onSave = { dateKey, title, desc, cat, start, end, flex, remEnabled, remLead, autoShift ->
                viewModel.saveActivity(
                    context = context,
                    editingActivity = editingActivity,
                    dateKey = dateKey,
                    title = title,
                    description = desc,
                    category = cat,
                    startMinute = start,
                    endMinute = end,
                    flexible = flex,
                    reminderEnabled = remEnabled,
                    reminderMinutesBefore = remLead,
                    autoShiftOverlaps = autoShift,
                    dayActivities = dayActivities,
                    profile = profile
                )
                showActivitySheet = false
            },
            onDelete = editingActivity?.let { act ->
                {
                    viewModel.deleteActivity(context, act)
                    showActivitySheet = false
                }
            }
        )
    }

    delayTargetActivity?.let { target ->
        val dayList = allActivities.filter { it.dateKey == target.dateKey }
        DelayActivityDialog(
            activity = target,
            onDismiss = { delayTargetActivity = null },
            onDelay = { mins ->
                viewModel.delayActivityAndCascade(
                    context = context,
                    dayActivities = dayList,
                    targetActivityId = target.id,
                    delayMinutes = mins,
                    profile = profile
                )
                delayTargetActivity = null
            }
        )
    }

    reorganizePreview?.let { preview ->
        ReorganizePreviewDialog(
            result = preview,
            onDismiss = { reorganizePreview = null },
            onConfirm = {
                viewModel.applyReorganizeResult(context, preview, profile)
                reorganizePreview = null
            }
        )
    }

    if (showHabitSheet) {
        HabitEditorSheet(
            editingHabit = editingHabit,
            onDismiss = { showHabitSheet = false },
            onSave = { title, cat, targetDays, remTime ->
                viewModel.saveHabit(
                    editingHabit = editingHabit,
                    title = title,
                    category = cat,
                    targetDaysPerWeek = targetDays,
                    reminderTimeMinute = remTime
                )
                showHabitSheet = false
            },
            onDelete = editingHabit?.let { hab ->
                {
                    viewModel.deleteHabit(hab)
                    showHabitSheet = false
                }
            }
        )
    }
}
