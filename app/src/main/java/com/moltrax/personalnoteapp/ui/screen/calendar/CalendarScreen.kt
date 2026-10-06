package com.moltrax.personalnoteapp.ui.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.ui.navigation.TaskDetail
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Date

/**
 * Calendar view — no longer a separate tab; embedded as a sub-view inside the "Tasks" tab
 * (it has no Scaffold/bottom bar of its own). [modifier] provides the wrapping screen layout.
 */
@Composable
fun CalendarContent(
    nav: NavController,
    modifier: Modifier = Modifier,
    vm: CalendarViewModel = hiltViewModel(),
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    // "Today" is recomputed on every foregrounding (ON_START); indicators update past midnight.
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                today = LocalDate.now()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Month/day selection survives process death: stored as ISO strings
    // (YearMonth/LocalDate cannot be written to a Bundle). Read/write helpers below.
    var currentMonthIso by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedDateIso by rememberSaveable { mutableStateOf(today.toString()) }
    val currentMonth = runCatching { YearMonth.parse(currentMonthIso) }
        .getOrDefault(YearMonth.from(today))
    val selectedDate = runCatching { LocalDate.parse(selectedDateIso) }.getOrDefault(today)

    // Day -> task mapping for the visible month (including recurrences). Recomputed when the month/tasks change.
    val occurrences: Map<LocalDate, List<Task>> = remember(tasks, currentMonthIso) {
        val first = currentMonth.atDay(1)
        val last = currentMonth.atEndOfMonth()
        val map = mutableMapOf<LocalDate, MutableList<Task>>()
        var d = first
        while (!d.isAfter(last)) {
            val day = d
            val onDay = tasks.filter { it.occursOn(day) }
            if (onDay.isNotEmpty()) map[day] = onDay.toMutableList()
            d = d.plusDays(1)
        }
        map
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.background)) {
        MonthHeader(
            month = currentMonth,
            onPrev = { currentMonthIso = currentMonth.minusMonths(1).toString() },
            onNext = { currentMonthIso = currentMonth.plusMonths(1).toString() },
        )
        WeekdayRow()
        MonthGrid(
            month = currentMonth,
            today = today,
            selected = selectedDate,
            occurrences = occurrences,
            onSelect = { selectedDateIso = it.toString() },
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        DayTaskList(
            date = selectedDate,
            tasks = occurrences[selectedDate].orEmpty(),
            onTap = { nav.navigate(TaskDetail(it.id)) },
        )
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val locale = LocalConfiguration.current.locales[0]
        IconButton(onClick = onPrev, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_prev_month),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val label = "${month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }} ${month.year}"
        Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next_month),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekdayRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        listOf(
            R.string.weekday_mon, R.string.weekday_tue, R.string.weekday_wed, R.string.weekday_thu,
            R.string.weekday_fri, R.string.weekday_sat, R.string.weekday_sun,
        ).forEach { dRes ->
            Text(stringResource(dRes), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    occurrences: Map<LocalDate, List<Task>>,
    onSelect: (LocalDate) -> Unit,
) {
    val first = month.atDay(1)
    // ISO: Monday=1 → number of empty cells in the first week.
    val leading = first.dayOfWeek.value - 1
    val daysInMonth = month.lengthOfMonth()
    val totalCells = ((leading + daysInMonth + 6) / 7) * 7

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        var cell = 0
        while (cell < totalCells) {
            Row(Modifier.fillMaxWidth()) {
                repeat(7) {
                    val dayNum = cell - leading + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = month.atDay(dayNum)
                        DayCell(
                            day = dayNum,
                            isToday = date == today,
                            isSelected = date == selected,
                            hasTasks = occurrences.containsKey(date),
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(date) },
                        )
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    }
                    cell++
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    hasTasks: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(shape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .then(
                if (isToday && !isSelected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onBackground,
            )
            // Small dot when there are tasks.
            Box(
                Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape)
                    .background(
                        if (hasTasks) MaterialTheme.colorScheme.primary
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
            )
        }
    }
}

@Composable
private fun DayTaskList(date: LocalDate, tasks: List<Task>, onTap: (Task) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val header = remember(date, locale) {
        val d = Date(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        SimpleDateFormat("d MMMM yyyy, EEEE", locale).format(d)
    }
    // Time label depends on the composition locale; recreated when the language changes.
    val dayTimeFmt = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    Text(header, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground)

    if (tasks.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.calendar_no_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tasks.forEach { task ->
            com.moltrax.personalnoteapp.ui.components.DhCard(onClick = { onTap(task) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title, style = MaterialTheme.typography.titleSmall,
                            color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        task.dueDate?.let {
                            Text(dayTimeFmt.format(Date(it)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (task.subtaskCount > 0) {
                            Text(stringResource(R.string.calendar_subtask_count, task.doneSubtaskCount, task.subtaskCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (task.isRecurring) {
                        Icon(Icons.Default.Repeat, contentDescription = stringResource(R.string.cd_recurring),
                            modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
