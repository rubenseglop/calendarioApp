package es.calendario.personal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val Spanish = Locale("es", "ES")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CalendarioTheme { CalendarApp() } }
    }
}

private enum class Destination(val label: String) {
    CALENDAR("Calendario"), AGENDA("Agenda"), TASKS("Tareas"), SETTINGS("Ajustes")
}

@Composable
private fun CalendarioTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Color(0xFF5263A8),
        onPrimary = Color.White,
        secondary = Color(0xFF665A8E),
        background = Color(0xFFF7F7FC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE9EAF5)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun CalendarApp() {
    var selected by remember { mutableStateOf(Destination.CALENDAR) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

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
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
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
                        Destination.TASKS -> Icons.Default.CheckCircle
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
            if (selected != Destination.SETTINGS) {
                FloatingActionButton(onClick = { }) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir")
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                Destination.CALENDAR -> MonthScreen(month, selectedDate,
                    onPrevious = { month = month.minusMonths(1) },
                    onNext = { month = month.plusMonths(1) },
                    onSelect = { selectedDate = it })
                Destination.AGENDA -> AgendaScreen()
                Destination.TASKS -> TasksScreen()
                Destination.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopAppBar(
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    androidx.compose.material3.TopAppBar(title = title, actions = actions)
}

@Composable
private fun MonthScreen(
    month: YearMonth,
    selectedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
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
                        fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }
        val firstOffset = month.atDay(1).dayOfWeek.value - 1
        val days = month.lengthOfMonth()
        val cells = ((firstOffset + days + 6) / 7) * 7
        for (week in 0 until cells / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (dayColumn in 0..6) {
                    val index = week * 7 + dayColumn
                    val day = index - firstOffset + 1
                    Box(Modifier.weight(1f).aspectRatio(0.86f), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            val date = month.atDay(day)
                            val isSelected = date == selectedDate
                            val isToday = date == LocalDate.now()
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                Box(
                                    Modifier.size(38.dp).clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { onSelect(date) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$day", color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal)
                                }
                                if (date.dayOfMonth % 4 == 0) {
                                    Box(Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary))
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Spanish))
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)
        )
        Text("No hay eventos para este día", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Tus eventos y recordatorios aparecerán aquí.")
            }
        }
    }
}

private data class DemoEvent(val time: String, val title: String, val category: String, val color: Color)

@Composable
private fun AgendaScreen() {
    val events = listOf(
        DemoEvent("09:00", "Planificar la semana", "Personal", Color(0xFF5263A8)),
        DemoEvent("12:30", "Reunión", "Trabajo", Color(0xFF23856D)),
        DemoEvent("18:00", "Tiempo personal", "Personal", Color(0xFFB06B35))
    )
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Próximamente", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Una vista clara de lo que viene.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
        items(events) { event ->
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(48.dp).clip(RoundedCornerShape(4.dp)).background(event.color))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(event.title, fontWeight = FontWeight.SemiBold)
                        Text(event.category, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text(event.time, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun TasksScreen() {
    var tasks by remember { mutableStateOf(listOf(
        "Preparar la semana", "Revisar próximas citas", "Organizar cumpleaños"
    )) }
    val completed = remember { mutableStateListOf<String>() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Mis tareas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${completed.size} de ${tasks.size} completadas",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        tasks.forEach { task ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth().clickable {
                    if (completed.contains(task)) completed.remove(task) else completed.add(task)
                }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = completed.contains(task), onCheckedChange = {
                        if (it) completed.add(task) else completed.remove(task)
                    })
                    Text(task, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Configuración", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        SettingsRow(Icons.Default.Palette, "Apariencia", "Tema claro, oscuro y colores")
        SettingsRow(Icons.Default.Sync, "Sincronización", "Google Calendar (próximamente)")
        SettingsRow(Icons.Default.Notifications, "Notificaciones", "Avisos y recordatorios")
        SettingsRow(Icons.Default.Widgets, "Widgets", "Personalizar pantalla de inicio")
        SettingsRow(Icons.Default.Info, "Acerca de", "Calendario · versión 0.1.0")
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
