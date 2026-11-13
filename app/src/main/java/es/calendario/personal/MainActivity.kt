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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val Spanish = Locale("es", "ES")
private val DateFormat = DateTimeFormatter.ISO_LOCAL_DATE
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }

    override fun onResume() {
        super.onResume()
        WidgetUpdater.refresh(this)
    }
}

private enum class Destination(val label: String) {
    CALENDAR("Calendario"), AGENDA("Agenda"), SETTINGS("Ajustes")
}

private val NightBackground = Color(0xFF101216)
private val NightSurface = Color(0xFF191D23)
private val NightMuted = Color(0xFF858D98)
private val TimepageCyan = Color(0xFF62D6D0)
private val BirthdayPink = Color(0xFFFF829D)

private fun accentColor(settings: AppSettings): Color = when (settings.accentColor) {
    "blue" -> Color(0xFF82A9FF)
    "green" -> Color(0xFF79D6A5)
    "purple" -> Color(0xFFB69AFF)
    "pink" -> Color(0xFFFF829D)
    else -> TimepageCyan
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(AppSettings.load(context)) }
    CalendarioTheme(settings) {
        CalendarApp(settings) { updated ->
            updated.save(context)
            settings = updated
            WidgetUpdater.refresh(context)
        }
    }
}

@Composable
private fun CalendarioTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        "light" -> false
        "dark" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val accent = accentColor(settings)
    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = Color(0xFF101216),
        secondary = Color(0xFF91A9FF), onSecondary = Color(0xFF101216),
        tertiary = BirthdayPink, background = NightBackground,
        onBackground = Color(0xFFF3F5F7), surface = NightSurface,
        onSurface = Color(0xFFF3F5F7), surfaceVariant = Color(0xFF252B33),
        onSurfaceVariant = NightMuted, outline = Color(0xFF3B444F),
        outlineVariant = Color(0xFF303740)
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White, secondary = Color(0xFF5263A8),
        tertiary = BirthdayPink, background = Color(0xFFF7F8FA),
        onBackground = Color(0xFF191D23), surface = Color.White,
        onSurface = Color(0xFF191D23), surfaceVariant = Color(0xFFE9EDF2),
        onSurfaceVariant = Color(0xFF626B76), outline = Color(0xFFB8C0CA),
        outlineVariant = Color(0xFFD8DDE4)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
private fun hasContactsPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

private fun hasCalendarPermissions(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarApp(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val repository = remember { CalendarRepository(context) }
    val contactsRepository = remember { ContactsRepository(context) }
    var selected by remember { mutableStateOf(Destination.CALENDAR) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var permissionGranted by remember { mutableStateOf(hasCalendarPermissions(context)) }
    var events by remember { mutableStateOf(emptyList<CalendarEvent>()) }
    var calendars by remember { mutableStateOf(emptyList<WritableCalendar>()) }
    var availableCalendars by remember { mutableStateOf(emptyList<WritableCalendar>()) }
    var contactsPermission by remember { mutableStateOf(hasContactsPermission(context)) }
    var birthdays by remember { mutableStateOf(emptyList<ContactBirthday>()) }
    var editingEvent by remember { mutableStateOf<CalendarEvent?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> contactsPermission = granted }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionGranted = result[Manifest.permission.READ_CALENDAR] == true &&
            result[Manifest.permission.WRITE_CALENDAR] == true
    }

    LaunchedEffect(contactsPermission, month) {
        birthdays = if (contactsPermission) {
            try { withContext(Dispatchers.IO) { contactsRepository.birthdays() } }
            catch (_: SecurityException) { contactsPermission = false; emptyList() }
        } else emptyList()
    }

    LaunchedEffect(permissionGranted, month) {
        if (permissionGranted) {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    repository.eventsBetween(month.atDay(1), month.plusMonths(1).atDay(1))
                }
                events = loaded
                calendars = withContext(Dispatchers.IO) { repository.writableCalendars() }
                availableCalendars = withContext(Dispatchers.IO) { repository.visibleCalendars() }
            } catch (e: SecurityException) {
                permissionGranted = false
                message = "Faltan permisos para acceder a los calendarios."
            } catch (e: Exception) {
                message = e.message ?: "No se han podido cargar los eventos."
            }
        } else {
            events = emptyList()
            calendars = emptyList()
            availableCalendars = emptyList()
        }
    }

    val visibleEvents = events.filter { settings.visibleCalendarIds == null || it.calendarId in settings.visibleCalendarIds }
    val visibleBirthdays = if (settings.showBirthdays) birthdays else emptyList()

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
                    month, selectedDate, visibleEvents, repository, visibleBirthdays, settings.firstDayOfWeek,
                    onPrevious = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) },
                    onSelect = { selectedDate = it; month = YearMonth.from(it) },
                    onEventClick = { editingEvent = it; showEditor = true }
                )
                selected == Destination.AGENDA -> AgendaScreen(
                    selectedDate, visibleEvents, repository, visibleBirthdays,
                    onDateChange = { selectedDate = it; month = YearMonth.from(it) },
                    onEventClick = { editingEvent = it; showEditor = true }
                )
                else -> SettingsScreen(
                    calendars = availableCalendars,
                    contactsEnabled = contactsPermission,
                    birthdayCount = birthdays.size,
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onEnableContacts = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
                )
            }
        }
    }

    if (showEditor) {
        EventEditorDialog(
            event = editingEvent,
            repository = repository,
            selectedDate = selectedDate,
            calendars = calendars,
            defaultDurationMinutes = settings.defaultDurationMinutes,
            onDismiss = { showEditor = false },
            onSave = { calendarId, title, description, date, start, end, allDay ->
                try {
                    val zone = ZoneId.systemDefault()
                    val begin = if (allDay) date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        else date.atTime(start).atZone(zone).toInstant().toEpochMilli()
                    val finish = if (allDay) date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                        else date.atTime(end).atZone(zone).toInstant().toEpochMilli()
                    if (!allDay && finish <= begin) {
                        message = "La hora de fin debe ser posterior a la de inicio."
                    } else {
                        withContext(Dispatchers.IO) {
                            repository.saveEvent(editingEvent?.id, calendarId, title, description, begin, finish, allDay, settings.defaultReminderMinutes)
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
    birthdays: List<ContactBirthday>,
    firstDayOfWeek: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    MonthCalendarPanel(
                        month, selectedDate, events, repository, birthdays, firstDayOfWeek,
                        onPrevious, onNext, onSelect
                    )
                }
                VerticalDivider(
                    Modifier.fillMaxHeight(),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    DayEventsPanel(
                        selectedDate, events, repository, birthdays, onEventClick
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                MonthCalendarPanel(
                    month, selectedDate, events, repository, birthdays, firstDayOfWeek,
                    onPrevious, onNext, onSelect
                )
                DayEventsPanel(
                    selectedDate, events, repository, birthdays, onEventClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MonthCalendarPanel(
    month: YearMonth,
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    repository: CalendarRepository,
    birthdays: List<ContactBirthday>,
    firstDayOfWeek: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.ChevronLeft, "Mes anterior", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    month.month.getDisplayName(TextStyle.FULL, Spanish).uppercase(Spanish),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(month.year.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.ChevronRight, "Mes siguiente", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 5.dp)) {
            val weekdayLabels = listOf("L", "M", "X", "J", "V", "S", "D")
            (0..6).map { weekdayLabels[(firstDayOfWeek - 1 + it) % 7] }.forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
        val offset = (month.atDay(1).dayOfWeek.value - firstDayOfWeek + 7) % 7
        val firstVisibleDate = month.atDay(1).minusDays(offset.toLong())
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val date = firstVisibleDate.plusDays((week * 7 + column).toLong())
                    val inMonth = YearMonth.from(date) == month
                    val selected = date == selectedDate
                    val today = date == LocalDate.now()
                    val hasEvents = events.any { repository.toLocalDate(it.begin) == date }
                    val hasBirthday = birthdays.any { it.occursOn(date) }
                    Box(
                        Modifier.weight(1f).aspectRatio(0.95f).clickable { onSelect(date) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(38.dp)
                                    .then(
                                        if (selected) Modifier.background(Color.White, CircleShape)
                                        else if (today) Modifier.clip(CircleShape)
                                            .background(Color.Transparent)
                                            .then(Modifier)
                                        else Modifier
                                    )
                                    .then(if (today && !selected) Modifier.padding(1.dp) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                if (today && !selected) {
                                    Box(Modifier.size(36.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)))
                                }
                                Text(
                                    date.dayOfMonth.toString(),
                                    color = when {
                                        selected -> NightBackground
                                        !inMonth -> Color(0xFF4C535D)
                                        today -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onBackground
                                    },
                                    fontWeight = if (selected || today) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(top = 2.dp).height(5.dp)
                            ) {
                                if (hasEvents && inMonth) Box(Modifier.size(4.dp).clip(CircleShape).background(TimepageCyan))
                                if (hasBirthday && inMonth) Box(Modifier.size(4.dp).clip(CircleShape).background(BirthdayPink))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayEventsPanel(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    repository: CalendarRepository,
    birthdays: List<ContactBirthday>,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayEvents = events.filter { repository.toLocalDate(it.begin) == selectedDate }
        .sortedBy { it.begin }
    val dayBirthdays = birthdays.filter { it.occursOn(selectedDate) }

    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = Color(0xFF303740), modifier = Modifier.padding(top = 8.dp))
        Text(
            selectedDate.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Spanish))
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(dayBirthdays, key = { "birthday-${it.contactId}" }) { birthday ->
                BirthdayRow(birthday, selectedDate)
            }
            items(dayEvents, key = { "event-${it.id}" }) { event ->
                EventRow(event, repository) { onEventClick(event) }
            }
            if (dayEvents.isEmpty() && dayBirthdays.isEmpty()) {
                item {
                    Text(
                        "Un día despejado. No hay nada en la agenda.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AgendaScreen(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    repository: CalendarRepository,
    birthdays: List<ContactBirthday>,
    onDateChange: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit
) {
    val days = events.groupBy { repository.toLocalDate(it.begin) }
    val dayBirthdays = birthdays.filter { it.occursOn(selectedDate) }
    val upcomingBirthdays = birthdays.mapNotNull { birthday ->
        nextBirthdayDate(birthday, selectedDate)?.let { birthday to it }
    }.sortedBy { it.second }.take(20)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 14.dp)) {
                IconButton(onClick = { onDateChange(selectedDate.minusDays(1)) }) {
                    Icon(Icons.Default.ChevronLeft, "Día anterior", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("AGENDA", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Spanish))
                        .replaceFirstChar { it.uppercase() }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDateChange(selectedDate.plusDays(1)) }) {
                    Icon(Icons.Default.ChevronRight, "Día siguiente", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = Color(0xFF303740))
        }
        val selectedEvents = days[selectedDate].orEmpty().sortedBy { it.begin }
        items(dayBirthdays, key = { "birthday-${it.contactId}" }) { birthday -> BirthdayRow(birthday, selectedDate) }
        if (selectedEvents.isEmpty() && dayBirthdays.isEmpty()) {
            item { Text("Un día despejado. No hay nada en la agenda.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 18.dp)) }
        }
        items(selectedEvents, key = { it.id }) { event -> EventRow(event, repository) { onEventClick(event) } }
        if (upcomingBirthdays.isNotEmpty()) {
            item {
                Text("PRÓXIMOS CUMPLEAÑOS", style = MaterialTheme.typography.labelLarge,
                    color = BirthdayPink, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
            }
            items(upcomingBirthdays, key = { "upcoming-${it.first.contactId}" }) { (birthday, date) ->
                BirthdayRow(birthday, date)
            }
        }
    }
}

private fun nextBirthdayDate(birthday: ContactBirthday, from: LocalDate): LocalDate? {
    for (year in from.year..(from.year + 8)) {
        try {
            val date = LocalDate.of(year, birthday.month, birthday.day)
            if (!date.isBefore(from)) return date
        } catch (_: java.time.DateTimeException) {
            // A February 29 birthday only occurs in leap years.
        }
    }
    return null
}

@Composable
private fun BirthdayRow(birthday: ContactBirthday, date: LocalDate) {
    val age = birthday.ageOn(date)
    Row(
        Modifier.fillMaxWidth().clickable { }.padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(38.dp).clip(RoundedCornerShape(3.dp)).background(BirthdayPink))
        Spacer(Modifier.width(14.dp))
        Icon(Icons.Default.Cake, contentDescription = "Cumpleaños", tint = BirthdayPink, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(birthday.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            val dateLabel = date.format(DateTimeFormatter.ofPattern("d 'de' MMMM", Spanish))
            Text(if (age != null) "Cumple $age años · $dateLabel" else dateLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider(color = Color(0xFF252B33))
}

@Composable
private fun EventRow(event: CalendarEvent, repository: CalendarRepository, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val time = if (event.allDay) "Todo el día" else
        java.time.Instant.ofEpochMilli(event.begin).atZone(zone).format(TimeFormat)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(38.dp).clip(RoundedCornerShape(3.dp)).background(Color(event.color)))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(time, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Editar evento", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = Color(0xFF252B33))
}

@Composable
private fun EventEditorDialog(
    event: CalendarEvent?,
    repository: CalendarRepository,
    selectedDate: LocalDate,
    calendars: List<WritableCalendar>,
    defaultDurationMinutes: Int,
    onDismiss: () -> Unit,
    onSave: suspend (Long, String, String, LocalDate, LocalTime, LocalTime, Boolean) -> Unit,
    onDelete: suspend (Long) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val scope = rememberCoroutineScope()
    var title by remember(event?.id) { mutableStateOf(event?.title.orEmpty()) }
    var description by remember(event?.id) { mutableStateOf("") }
    LaunchedEffect(event?.id) {
        if (event != null) {
            description = withContext(Dispatchers.IO) { repository.eventDescription(event.id) }
        }
    }
    var dateText by remember(event?.id) {
        mutableStateOf(event?.let { InstantDate(it.begin, if (it.allDay) ZoneOffset.UTC else zone).toString() } ?: selectedDate.toString())
    }
    var startText by remember(event?.id) {
        mutableStateOf(event?.let { InstantTime(it.begin, zone).format(TimeFormat) } ?: LocalTime.now().withMinute(0).format(TimeFormat))
    }
    var endText by remember(event?.id) {
        mutableStateOf(event?.let { InstantTime(it.end, zone).format(TimeFormat) } ?: LocalTime.now().withMinute(0).plusMinutes(defaultDurationMinutes.toLong()).format(TimeFormat))
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
                    // The callback performs provider I/O and closes the dialog on success.
                    scope.launch {
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
private fun SettingsScreen(
    calendars: List<WritableCalendar>,
    contactsEnabled: Boolean,
    birthdayCount: Int,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onEnableContacts: () -> Unit
) {
    val context = LocalContext.current
    var resetDialog by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Text("CONFIGURACIÓN", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp, modifier = Modifier.padding(top = 12.dp, bottom = 18.dp))
            SettingsSection("APARIENCIA", Icons.Default.Palette)
            SettingsChoice("Tema", settings.theme,
                listOf("auto" to "Automático", "light" to "Claro", "dark" to "Oscuro")) {
                onSettingsChange(settings.copy(theme = it))
            }
            SettingsChoice("Color de acento", settings.accentColor,
                listOf("cyan" to "Turquesa", "blue" to "Azul", "green" to "Verde",
                    "purple" to "Morado", "pink" to "Rosa")) {
                onSettingsChange(settings.copy(accentColor = it))
            }
            SettingsSection("CALENDARIO", Icons.Default.CalendarMonth)
            SettingsChoice("Primer día de la semana", settings.firstDayOfWeek.toString(),
                listOf("1" to "Lunes", "7" to "Domingo")) {
                onSettingsChange(settings.copy(firstDayOfWeek = it.toInt()))
            }
            Text("Calendarios visibles", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            Text("Desmarca los calendarios que no quieras mostrar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (calendars.isEmpty()) {
                Text("No se han encontrado calendarios visibles.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
            } else {
                calendars.forEach { calendar ->
                    val isSelected = settings.visibleCalendarIds == null || calendar.id in settings.visibleCalendarIds
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isSelected, onCheckedChange = { checked ->
                            val current = settings.visibleCalendarIds ?: calendars.map { it.id }.toSet()
                            val updated = if (checked) current + calendar.id else current - calendar.id
                            onSettingsChange(settings.copy(visibleCalendarIds = updated))
                        })
                        Column(Modifier.weight(1f)) {
                            Text(calendar.name, style = MaterialTheme.typography.bodyMedium)
                            if (calendar.accountName.isNotBlank()) Text(calendar.accountName,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                TextButton(onClick = { onSettingsChange(settings.copy(visibleCalendarIds = null)) }) {
                    Text("Mostrar todos los calendarios")
                }
            }
            SettingsSection("NUEVOS EVENTOS", Icons.Default.Event)
            SettingsChoice("Duración predeterminada", settings.defaultDurationMinutes.toString(),
                listOf(15, 30, 45, 60, 90).map { it.toString() to "$it minutos" }) {
                onSettingsChange(settings.copy(defaultDurationMinutes = it.toInt()))
            }
            SettingsChoice("Recordatorio predeterminado", settings.defaultReminderMinutes.toString(),
                listOf(0, 5, 10, 15, 30, 60).map {
                    it.toString() to if (it == 0) "Ninguno" else "$it minutos antes"
                }) {
                onSettingsChange(settings.copy(defaultReminderMinutes = it.toInt()))
            }
            SettingsSection("CUMPLEAÑOS", Icons.Default.Cake)
            SettingsSwitch("Mostrar cumpleaños", settings.showBirthdays,
                if (contactsEnabled) "$birthdayCount cumpleaños detectados"
                else "Activa el permiso para leer los contactos") {
                onSettingsChange(settings.copy(showBirthdays = it))
            }
            if (!contactsEnabled) {
                OutlinedButton(onClick = onEnableContacts, modifier = Modifier.fillMaxWidth()) {
                    Text("Activar acceso a contactos")
                }
            }
            SettingsSection("WIDGETS", Icons.Default.Widgets)
            SettingsChoice("Días de agenda", settings.widgetDays.toString(),
                listOf(7, 14, 30, 60).map { it.toString() to "$it días" }) {
                onSettingsChange(settings.copy(widgetDays = it.toInt()))
            }
            SettingsChoice("Máximo de eventos", settings.widgetEventCount.toString(),
                listOf(3, 5, 8, 10, 15).map { it.toString() to "$it eventos" }) {
                onSettingsChange(settings.copy(widgetEventCount = it.toInt()))
            }
            SettingsSection("MANTENIMIENTO", Icons.Default.SettingsBackupRestore)
            OutlinedButton(onClick = { resetDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Restablecer configuración")
            }
            Spacer(Modifier.height(18.dp))
            Text("Calendario · versión 0.9.1", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall)
            Text("Tus eventos permanecen en los calendarios del dispositivo. La sincronización depende de Android.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp))
        }
    }
    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text("Restablecer ajustes") },
            text = { Text("Se recuperarán los valores predeterminados de apariencia, calendario, eventos y widgets. No se borrarán tus eventos ni tus contactos.") },
            confirmButton = {
                TextButton(onClick = {
                    val defaults = AppSettings()
                    defaults.save(context)
                    context.getSharedPreferences(WidgetConfigActivity.PREFS, android.content.Context.MODE_PRIVATE)
                        .edit().clear().apply()
                    onSettingsChange(defaults)
                    resetDialog = false
                }) { Text("Restablecer") }
            },
            dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(9.dp))
        Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge, letterSpacing = 1.2.sp)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SettingsChoice(title: String, value: String, options: List<Pair<String, String>>,
                           onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == value }?.second ?: value
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(label)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, option) ->
                    DropdownMenuItem(text = { Text(option) }, onClick = {
                        onSelected(key)
                        expanded = false
                    })
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitch(title: String, checked: Boolean, subtitle: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
