package es.calendario.personal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext\nimport kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Spanish = Locale("es", "ES")
private val DateFormat = DateTimeFormatter.ISO_LOCAL_DATE
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CalendarioTheme { CalendarApp() } }
    }
}

private enum class Destination(val label: String) {
    CALENDAR("Calendario"), AGENDA("Agenda"), SETTINGS("Ajustes")
}

@Composable
private fun CalendarioTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Color(0xFF5263A8),
        onPrimary = Color.White,
        secondary = Color(0xFF665A8E),
        background = Color(0xFFF7F7FC),
        surface = Color.White,
        surfaceVariant = Color(0xFFE9EAF5)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun hasCalendarPermissions(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

@Composable
private fun CalendarApp() {
    val context = LocalContext.current
    val repository = remember { CalendarRepository(context) }
    var selected by remember { mutableStateOf(Destination.CALENDAR) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var permissionGranted by remember { mutableStateOf(hasCalendarPermissions(context)) }
    var events by remember { mutableStateOf(emptyList<CalendarEvent>()) }
    var calendars by remember { mutableStateOf(emptyList<WritableCalendar>()) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = result[Manifest.permission.READ_CALENDAR] == true &&
            result[Manifest.permission.WRITE_CALENDAR] == true
    }

    LaunchedEffect(permissionGranted, month) {
        if (permissionGranted) {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    repository.eventsBetween(month.atDay(1), month.plusMonths(1).atDay(1))
                }
                events = loaded
                calendars = withContext(Dispatchers.IO) { repository.writableCalendars() }
            } catch (e: SecurityException) {
                permissionGranted = false
                message = "Faltan permisos para acceder a los calendarios."
            } catch (e: Exception) {
                message = e.message ?: "No se han podido cargar los eventos."
            }
        } else {
            events = emptyList()
            calendars = emptyList()
        }
    }

    fun refresh() {
        // Changing the selected month triggers the normal provider refresh.
        month = month
        permissionGranted = hasCalendarPermissions(context)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Calendario", fontWeight = FontWeight.Bold)
                        Text("Tu tiempo, organizado", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { month = YearMonth.now(); selectedDate = LocalDate.now() }) {
                        Icon(Icons.Default.Today, contentDescription = "Ir a hoy")
                    }
                    IconButton(onClick = { selected = Destination.AGENDA }) {
                        Icon(Icons.Default.ViewAgenda, contentDescription = "Ver agenda")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val icon = when (destination) {
                        Destination.CALENDAR -> Icons.Default.CalendarMonth
                        Destination.AGENDA -> Icons.Default.ViewAgenda
                        Destination.SETTINGS -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = selected == destination,
                        onClick = { selected = destination },
                        icon = { Icon(icon, contentDescription = destination.label) },
                        label = { Text(destination.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selected != Destination.SETTINGS && permissionGranted) {
                FloatingActionButton(onClick = {
                    editingEvent = null
                    showEditor = true
                }) { Icon(Icons.Default.Add, contentDescription = "Crear evento") }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !permissionGranted -> PermissionScreen {
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR
                    ))
                }
                selected == Destination.CALENDAR -> MonthScreen(
                    month, selectedDate, events, repository,
                    onPrevious = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) },
                    onSelect = { selectedDate = it },
                    onEventClick = { editingEvent = it; showEditor = true }
                )
                selected == Destination.AGENDA -> AgendaScreen(
                    selectedDate, events, repository,
                    onDateChange = { selectedDate = it; month = YearMonth.from(it) },
                    onEventClick = { editingEvent = it; showEditor = true }
                )
                else -> SettingsScreen(calendars.size)
            }
        }
    }

    if (showEditor) {
        EventEditorDialog(
            event = editingEvent,
            selectedDate = selectedDate,
            calendars = calendars,
            onDismiss = { showEditor = false },
            onSave = { calendarId, title, description, date, start, end, allDay ->
                try {
                    val zone = ZoneId.systemDefault()
                    val begin = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
                    val finishDate = if (allDay) date.plusDays(1) else date
                    val finishTime = if (allDay) LocalTime.MIDNIGHT else end
                    val finish = finishDate.atTime(finishTime).atZone(zone).toInstant().toEpochMilli()
                    if (!allDay && finish <= begin) {
                        message = "La hora de fin debe ser posterior a la de inicio."
                    } else {
                        withContext(Dispatchers.IO) {
                            repository.saveEvent(editingEvent?.id, calendarId, title, description, begin, finish, allDay)
                        }
                        showEditor = false
                        month = YearMonth.from(date)
                        // Reload after a provider write.
                        events = withContext(Dispatchers.IO) {
                            repository.eventsBetween(month.atDay(1), month.plusMonths(1).atDay(1))
                        }
                        calendars = withContext(Dispatchers.IO) { repository.writableCalendars() }
                    }
                } catch (e: Exception) {
                    message = e.message ?: "No se ha podido guardar el evento."
                }
            },
            onDelete = {
                try {
                    withContext(Dispatchers.IO) { repository.deleteEvent(it) }
                    showEditor = false
                    events = withContext(Dispatchers.IO) {
                        repository.eventsBetween(month.atDay(1), month.plusMonths(1).atDay(1))
                    }
                } catch (e: Exception) {
                    message = e.message ?: "No se ha podido eliminar el evento."
                }
            }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text("Aceptar") } },
            title = { Text("Calendario") },
            text = { Text(text) }
        )
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(18.dp))
        Text("Conecta tus calendarios", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Calendario necesita permiso para leer y modificar los calendarios que ya tienes configurados en Android, incluido Google Calendar. No se inicia sesión ni se envían tus eventos a ningún servidor.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text("Conceder permisos") }
    }
}

@Composable
private fun MonthScreen(
    month: YearMonth,
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    repository: CalendarRepository,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit
) {
    val dayEvents = events.filter { repository.toLocalDate(it.begin) == selectedDate }
        .sortedBy { it.begin }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, "Mes anterior") }
            Text(
                "${month.month.getDisplayName(TextStyle.FULL, Spanish).replaceFirstChar { it.uppercase() }} ${month.year}",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Mes siguiente") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
        for (week in 0 until cells / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (column in 0..6) {
                    val day = week * 7 + column - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(0.86f), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            val selected = date == selectedDate
                            val today = date == LocalDate.now()
                            val hasEvents = events.any { repository.toLocalDate(it.begin) == date }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { onSelect(date) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$day", color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal)
                                }
                                if (hasEvents) {
                                    Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary))
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            selectedDate.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Spanish))
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
        )
        if (dayEvents.isEmpty()) {
            Text("No hay eventos para este día", color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dayEvents, key = { it.id }) { event -> EventRow(event, repository) { onEventClick(event) } }
            }
        }
    }
}

@Composable
private fun AgendaScreen(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    repository: CalendarRepository,
    onDateChange: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit
) {
    val days = events.groupBy { repository.toLocalDate(it.begin) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDateChange(selectedDate.minusDays(1)) }) {
                    Icon(Icons.Default.ChevronLeft, "Día anterior")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Agenda", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Spanish))
                        .replaceFirstChar { it.uppercase() })
                }
                IconButton(onClick = { onDateChange(selectedDate.plusDays(1)) }) {
                    Icon(Icons.Default.ChevronRight, "Día siguiente")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        val selectedEvents = days[selectedDate].orEmpty().sortedBy { it.begin }
        if (selectedEvents.isEmpty()) {
            item { Text("No hay eventos para este día.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(selectedEvents, key = { it.id }) { event -> EventRow(event, repository) { onEventClick(event) } }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, repository: CalendarRepository, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val time = if (event.allDay) "Todo el día" else
        java.time.Instant.ofEpochMilli(event.begin).atZone(zone).format(TimeFormat)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(4.dp))
                .background(Color(event.color)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(event.title, fontWeight = FontWeight.SemiBold)
                Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Editar evento",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EventEditorDialog(
    event: CalendarEvent?,
    selectedDate: LocalDate,
    calendars: List<WritableCalendar>,
    onDismiss: () -> Unit,
    onSave: suspend (Long, String, String, LocalDate, LocalTime, LocalTime, Boolean) -> Unit,
    onDelete: suspend (Long) -> Unit
) {
    val zone = ZoneId.systemDefault()
    var title by remember(event?.id) { mutableStateOf(event?.title.orEmpty()) }
    var description by remember(event?.id) { mutableStateOf("") }
    var dateText by remember(event?.id) {
        mutableStateOf(event?.let { InstantDate(it.begin, zone).toString() } ?: selectedDate.toString())
    }
    var startText by remember(event?.id) {
        mutableStateOf(event?.let { InstantTime(it.begin, zone).format(TimeFormat) } ?: LocalTime.now().withMinute(0).format(TimeFormat))
    }
    var endText by remember(event?.id) {
        mutableStateOf(event?.let { InstantTime(it.end, zone).format(TimeFormat) } ?: LocalTime.now().withMinute(0).plusHours(1).format(TimeFormat))
    }
    var allDay by remember(event?.id) { mutableStateOf(event?.allDay ?: false) }
    var calendarId by remember(event?.id) {
        mutableLongStateOf(event?.calendarId ?: calendars.firstOrNull()?.id ?: -1L)
    }
    var calendarMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (event == null) "Nuevo evento" else "Editar evento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Descripción (opcional)") })
                OutlinedTextField(dateText, { dateText = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startText, { startText = it }, label = { Text("Desde (HH:mm)") },
                        modifier = Modifier.weight(1f), singleLine = true, enabled = !allDay)
                    OutlinedTextField(endText, { endText = it }, label = { Text("Hasta (HH:mm)") },
                        modifier = Modifier.weight(1f), singleLine = true, enabled = !allDay)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(allDay, { allDay = it })
                    Text("Todo el día")
                }
                if (calendars.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { calendarMenu = true }) {
                            Text(calendars.firstOrNull { it.id == calendarId }?.name ?: "Elegir calendario")
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = calendarMenu, onDismissRequest = { calendarMenu = false }) {
                            calendars.forEach { calendar ->
                                DropdownMenuItem(
                                    text = { Text(calendar.name) },
                                    onClick = { calendarId = calendar.id; calendarMenu = false }
                                )
                            }
                        }
                    }
                } else {
                    Text("No hay calendarios con permiso de escritura en este dispositivo.",
                        style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && calendarId >= 0, onClick = {
                try {
                    val date = LocalDate.parse(dateText, DateFormat)
                    val start = LocalTime.parse(startText, TimeFormat)
                    val end = LocalTime.parse(endText, TimeFormat)
                    error = null
                    androidx.compose.runtime.LaunchedEffect(Unit) { }
                    // The callback performs provider I/O and closes the dialog on success.
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        onSave(calendarId, title, description, date, start, end, allDay)
                    }
                } catch (_: Exception) {
                    error = "Revisa la fecha y las horas. Usa AAAA-MM-DD y HH:mm."
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (event != null) {
                    TextButton(onClick = {
                        scope.launch {
                            onDelete(event.id)
                        }
                    }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

private fun InstantDate(millis: Long, zone: ZoneId): LocalDate =
    java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

private fun InstantTime(millis: Long, zone: ZoneId): LocalTime =
    java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()

@Composable
private fun SettingsScreen(calendarCount: Int) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Configuración", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        SettingsRow(Icons.Default.Sync, "Google Calendar", "Sincronización mediante las cuentas de Android")
        SettingsRow(Icons.Default.CalendarMonth, "Calendarios disponibles", "$calendarCount calendarios con escritura")
        SettingsRow(Icons.Default.Notifications, "Notificaciones", "En una próxima versión")
        SettingsRow(Icons.Default.Widgets, "Widgets", "En una próxima versión")
        SettingsRow(Icons.Default.Info, "Acerca de", "Calendario · versión 0.2.0")
        Spacer(Modifier.height(12.dp))
        Text("Tus eventos permanecen en los calendarios del dispositivo. La sincronización con Google depende de la cuenta y de los ajustes de sincronización de Android.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
    )
}
