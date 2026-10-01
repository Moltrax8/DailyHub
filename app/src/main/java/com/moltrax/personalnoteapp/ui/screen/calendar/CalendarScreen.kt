package com.moltrax.personalnoteapp.ui.screen.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.moltrax.personalnoteapp.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Date

/**
 * Takvim görünümü — artık ayrı bir sekme değil; "Görevler" sekmesi içindeki bir alt görünüm olarak
 * gömülü çalışır (kendi Scaffold/alt bar'ı yoktur). [modifier] ile saran ekran yerleşimi verir.
 */
@Composable
fun CalendarContent(
    nav: NavController,
    modifier: Modifier = Modifier,
    vm: CalendarViewModel = hiltViewModel(),
) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    // "Bugün" her ön plana gelişte (ON_START) yeniden hesaplanır; gece yarısı geçmişse göstergeler güncellenir.
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
    // Ay/gün seçimi process-death'e dayanıklı: ISO String olarak saklanır
    // (YearMonth/LocalDate Bundle'a yazılamaz). Okuma/yazma yardımcıları aşağıda.
    var currentMonthIso by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedDateIso by rememberSaveable { mutableStateOf(today.toString()) }
    val currentMonth = runCatching { YearMonth.parse(currentMonthIso) }
        .getOrDefault(YearMonth.from(today))
    val selectedDate = runCatching { LocalDate.parse(selectedDateIso) }.getOrDefault(today)

    // Görünür ay için gün -> görev eşlemesi (tekrarlar dahil). Ay/görev değişince yeniden hesaplanır.
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

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val locale = LocalConfiguration.current.locales[0]
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_prev_month),
                tint = MaterialTheme.colorScheme.onBackground)
        }
        val label = "${month.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase() }} ${month.year}"
        Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next_month),
                tint = MaterialTheme.colorScheme.onBackground)
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
    // ISO: Pazartesi=1 → ilk haftadaki boş hücre sayısı.
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
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) AppColors.AccentGlow else androidx.compose.ui.graphics.Color.Transparent)
            .then(if (isToday) Modifier.border(1.dp, AppColors.Accent, RoundedCornerShape(10.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.toString(),
                fontSize = 14.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) AppColors.Accent else MaterialTheme.colorScheme.onBackground,
            )
            // Görev varsa küçük bir nokta.
            Box(
                Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape)
                    .background(if (hasTasks) AppColors.Accent else androidx.compose.ui.graphics.Color.Transparent)
            )
        }
    }
}

@Composable
private fun ColumnScope.DayTaskList(date: LocalDate, tasks: List<Task>, onTap: (Task) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val header = remember(date, locale) {
        val d = Date(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        SimpleDateFormat("d MMMM yyyy, EEEE", locale).format(d)
    }
    // Saat etiketi composition locale'una bağlı; dil değişince yeniden kurulur.
    val dayTimeFmt = remember(locale) { SimpleDateFormat("HH:mm", locale) }
    Text(header, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground)

    if (tasks.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.calendar_no_tasks), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxWidth().weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tasks, key = { it.id }) { task ->
            Card(
                onClick = { onTap(task) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface)
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
                            modifier = Modifier.size(18.dp), tint = AppColors.Accent)
                    }
                }
            }
        }
    }
}
