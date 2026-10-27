package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.CleanTimer
import com.kodraliu.localrock.shared.vacuum.MopRoute
import com.kodraliu.localrock.shared.vacuum.VacuumFanPower
import com.kodraliu.localrock.shared.vacuum.WaterBoxMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: VacuumViewModel,
    onBack: () -> Unit,
) {
    val repository = viewModel.repository
    val timers by repository.timers.collectAsState()
    val status by repository.status.collectAsState()
    val mopMode by repository.mopMode.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    // Timer id -> the state a toggle in flight is asking for; the switch shows it and is locked.
    var pendingToggles by remember { mutableStateOf(mapOf<String, Boolean>()) }

    // Shown inline rather than as an error message, which would repeat every time the screen opens.
    LaunchedEffect(loadAttempt) { loadFailed = runCatching { repository.fetchTimers() }.isFailure }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add schedule")
            }
        },
    ) { padding ->
        if (timers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (loadFailed) "Couldn't load schedules" else "No schedules yet",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (loadFailed) "The robot didn't answer. Check that it's online." else "Tap + to schedule a recurring clean",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (loadFailed) TextButton(onClick = { loadAttempt++ }) { Text("Try again") }
                }
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(timers, key = { it.id }) { timer ->
                    TimerRow(
                        timer = timer,
                        pendingEnabled = pendingToggles[timer.id],
                        onToggle = {
                            pendingToggles = pendingToggles + (timer.id to !timer.enabled)
                            viewModel.run(refreshStatus = false, onDone = { pendingToggles = pendingToggles - timer.id }) {
                                it.toggleTimer(timer)
                            }
                        },
                        onDelete = { viewModel.run(refreshStatus = false) { it.deleteTimer(timer) } },
                    )
                    HorizontalDivider(Modifier.padding(start = 72.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        AddTimerDialog(
            // The robot's current mode, unless it is SmartPlan, per-room or another code the
            // dialog can't show; a schedule stores its own settings and keeps them.
            initialFanPower = status.fanPower.takeIf { fan -> FAN_OPTIONS.any { it.first == fan } } ?: VacuumFanPower.BALANCED,
            initialWaterBoxMode = WaterBoxMode.normalize(status.waterBoxCustomMode)
                .takeIf { water -> WATER_OPTIONS.any { it.first == water } } ?: WaterBoxMode.MEDIUM,
            initialMopMode = (mopMode ?: status.mopMode).takeIf { route -> ROUTE_OPTIONS.any { it.first == route } } ?: MopRoute.STANDARD,
            onAdd = { hour, minute, weekdays, fanPower, waterBoxMode, routeMode ->
                showAddDialog = false
                viewModel.run("Schedule saved", refreshStatus = false) {
                    it.createTimer(hour, minute, weekdays, fanPower, waterBoxMode, routeMode)
                }
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun TimerRow(timer: CleanTimer, pendingEnabled: Boolean?, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (timer.enabled) 1f else 0.4f),
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Schedule, null, Modifier.size(22.dp), MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatTime(timer.hour, timer.minute),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (timer.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                listOfNotNull(formatWeekdays(timer.weekdays), formatMode(timer)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = pendingEnabled ?: timer.enabled, onCheckedChange = { onToggle() }, enabled = pendingEnabled == null)
    }
}

@Composable
private fun AddTimerDialog(
    initialFanPower: Int,
    initialWaterBoxMode: Int,
    initialMopMode: Int,
    onAdd: (hour: Int, minute: Int, weekdays: Set<Int>, fanPower: Int, waterBoxMode: Int, mopMode: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }
    var weekdays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) } // Mon–Fri default
    var fanPower by remember { mutableIntStateOf(initialFanPower) }
    var waterBoxMode by remember { mutableIntStateOf(initialWaterBoxMode) }
    var mopMode by remember { mutableIntStateOf(initialMopMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New schedule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberStepper("Hour", hour, 0, 23) { hour = it }
                    Text(":", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    NumberStepper("Min", minute, 0, 59, step = 15) { minute = it }
                }
                Text("Days", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val dayLabels = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                    dayLabels.forEachIndexed { index, label ->
                        val selected = index in weekdays
                        Surface(
                            onClick = { weekdays = if (selected) weekdays - index else weekdays + index },
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                Text("Cleaning mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ModeOptionSelector("Suction", FAN_OPTIONS.map { it.second }, FAN_OPTIONS.indexOfFirst { it.first == fanPower }, true) {
                    fanPower = FAN_OPTIONS[it].first
                }
                ModeOptionSelector("Water", WATER_OPTIONS.map { it.second }, WATER_OPTIONS.indexOfFirst { it.first == waterBoxMode }, true) {
                    waterBoxMode = WATER_OPTIONS[it].first
                }
                // The route only applies while mopping.
                if (waterBoxMode != WaterBoxMode.OFF) {
                    ModeOptionSelector("Route", ROUTE_OPTIONS.map { it.second }, ROUTE_OPTIONS.indexOfFirst { it.first == mopMode }, true) {
                        mopMode = ROUTE_OPTIONS[it].first
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(hour, minute, weekdays, fanPower, waterBoxMode, mopMode) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun NumberStepper(label: String, value: Int, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { onChange(if (value + step > max) min else value + step) }, modifier = Modifier.size(36.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("+")
        }
        Text(value.toString().padStart(2, '0'), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { onChange(if (value - step < min) max else value - step) }, modifier = Modifier.size(36.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("−")
        }
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = if (hour < 12) "AM" else "PM"
    return "${h.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} $amPm"
}

/** "Strong · Water Med · Deep", or null when the robot didn't report the timer's settings. */
private fun formatMode(timer: CleanTimer): String? {
    val fan = FAN_OPTIONS.firstOrNull { it.first == timer.fanPower }?.second
    val waterMode = WaterBoxMode.normalize(timer.waterBoxMode)
    val water = when (waterMode) {
        null -> null
        WaterBoxMode.OFF -> "No mopping"
        else -> WATER_OPTIONS.firstOrNull { it.first == waterMode }?.second?.let { "Water $it" }
    }
    val route = ROUTE_OPTIONS.firstOrNull { it.first == timer.mopMode }?.second
        ?.takeIf { waterMode != null && waterMode != WaterBoxMode.OFF }
    return listOfNotNull(fan, water, route).joinToString(" · ").ifEmpty { null }
}

private fun formatWeekdays(days: Set<Int>): String {
    if (days.isEmpty()) return "Every day"
    val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    if (days == setOf(1, 2, 3, 4, 5)) return "Weekdays"
    if (days == setOf(0, 6)) return "Weekends"
    if (days.size == 7) return "Every day"
    return days.sorted().joinToString(", ") { names.getOrElse(it) { "$it" } }
}
