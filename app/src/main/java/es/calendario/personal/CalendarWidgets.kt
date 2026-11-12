package es.calendario.personal

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val widgetLocale = Locale("es", "ES")
private val widgetZone: ZoneId get() = ZoneId.systemDefault()

private data class WidgetEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

private object WidgetData {
    fun events(context: Context, from: LocalDate, until: LocalDate): List<WidgetEvent> {
        val start = from.atStartOfDay(widgetZone).toInstant().toEpochMilli()
        val end = until.atStartOfDay(widgetZone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString()).appendPath(end.toString()).build()
        val result = mutableListOf<WidgetEvent>()
        try {
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
                null, null, CalendarContract.Instances.BEGIN + " ASC"
            )?.use { cursor ->
                val title = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val begin = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val finish = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDay = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                while (cursor.moveToNext()) {
                    result += WidgetEvent(
                        cursor.getString(title)?.takeIf { it.isNotBlank() } ?: "Sin título",
                        cursor.getLong(begin), cursor.getLong(finish), cursor.getInt(allDay) != 0
                    )
                }
            }
        } catch (_: SecurityException) {
            // The user may not have granted calendar access yet.
        } catch (_: Exception) {
            // Widgets should remain usable even if a calendar provider is unavailable.
        }
        return result
    }

    fun birthdays(context: Context, from: LocalDate): List<Pair<ContactBirthday, LocalDate>> {
        return try {
            ContactsRepository(context).birthdays().mapNotNull { birthday ->
                (from.year..(from.year + 8)).firstNotNullOfOrNull { year ->
                    try {
                        LocalDate.of(year, birthday.month, birthday.day).takeIf { !it.isBefore(from) }
                    } catch (_: java.time.DateTimeException) { null }
                }?.let { birthday to it }
            }.sortedBy { it.second }.take(5)
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

object WidgetUpdater {
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(MonthCalendarWidgetProvider(), AgendaWidgetProvider(),
            BirthdayWidgetProvider()).forEach { provider ->
            provider.refresh(context, manager)
        }
    }
}

abstract class BaseCalendarWidgetProvider : AppWidgetProvider() {
    protected abstract fun createViews(context: Context): RemoteViews

    fun refresh(context: Context, manager: AppWidgetManager = AppWidgetManager.getInstance(context)) {
        val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
        if (ids.isNotEmpty()) onUpdate(context, manager, ids)
    }

    protected open fun configureActions(context: Context, views: RemoteViews, appWidgetId: Int) {}

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            val views = createViews(context)
            val opacity = context.getSharedPreferences(WidgetConfigActivity.PREFS, Context.MODE_PRIVATE)
                .getInt(WidgetConfigActivity.opacityKey(appWidgetId), 88)
            val alpha = opacity.coerceIn(0, 100) * 255 / 100
            views.setInt(R.id.widget_root, "setBackgroundColor",
                android.graphics.Color.argb(alpha, 28, 35, 54))
            val openApp = PendingIntent.getActivity(
                context, appWidgetId, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            configureActions(context, views, appWidgetId)
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}

class MonthCalendarWidgetProvider : BaseCalendarWidgetProvider() {
    override fun createViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_month)
        val month = YearMonth.now()
        val events = WidgetData.events(context, month.atDay(1), month.plusMonths(1).atDay(1))
        val eventDays = events.map { java.time.Instant.ofEpochMilli(it.begin).atZone(widgetZone).toLocalDate().dayOfMonth }.toSet()
        views.setTextViewText(R.id.widget_month_title,
            month.month.getDisplayName(TextStyle.FULL, widgetLocale).replaceFirstChar { it.uppercase() } + " " + month.year)
        views.removeAllViews(R.id.widget_month_days)

        val offset = month.atDay(1).dayOfWeek.value - 1
        val totalCells = 42
        repeat(6) { week ->
            val row = RemoteViews(context.packageName, R.layout.widget_month_week)
            repeat(7) { dayOfWeek ->
                val index = week * 7 + dayOfWeek
                val day = index - offset + 1
                val cell = RemoteViews(context.packageName, R.layout.widget_month_day)
                if (day in 1..month.lengthOfMonth()) {
                    val marker = if (day in eventDays) " ·" else ""
                    cell.setTextViewText(R.id.widget_month_day_text, "$day$marker")
                    if (LocalDate.now() == month.atDay(day)) {
                        cell.setInt(R.id.widget_month_day_text, "setBackgroundResource", R.drawable.widget_today_background)
                        cell.setTextColor(R.id.widget_month_day_text, android.graphics.Color.WHITE)
                    } else {
                        cell.setTextColor(R.id.widget_month_day_text, android.graphics.Color.rgb(45, 48, 62))
                    }
                } else {
                    cell.setTextViewText(R.id.widget_month_day_text, "")
                }
                row.addView(R.id.widget_month_week_row, cell)
            }
            views.addView(R.id.widget_month_days, row)
        }
        views.setTextViewText(R.id.widget_month_hint,
            if (events.isEmpty()) "Pulsa para abrir tu calendario" else "• Días con eventos")
        return views
    }
}

class AgendaWidgetProvider : BaseCalendarWidgetProvider() {
    override fun configureActions(context: Context, views: RemoteViews, appWidgetId: Int) {
        val now = System.currentTimeMillis()
        val start = java.time.Instant.ofEpochMilli(now).atZone(widgetZone)
            .withSecond(0).withNano(0).plusMinutes(5).toInstant().toEpochMilli()
        val eventIntent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 60 * 60 * 1000L)
        val dayStart = LocalDate.now().atStartOfDay(widgetZone).toInstant().toEpochMilli()
        val dayEnd = LocalDate.now().plusDays(1).atStartOfDay(widgetZone).toInstant().toEpochMilli()
        val dayIntent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dayStart)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, dayEnd)
            .putExtra(CalendarContract.Events.ALL_DAY, true)
        val reminderIntent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 15 * 60 * 1000L)
            .putExtra(CalendarContract.Events.HAS_ALARM, true)
        fun pending(code: Int, intent: Intent) = PendingIntent.getActivity(
            context, appWidgetId * 10 + code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_add_event, pending(1, eventIntent))
        views.setOnClickPendingIntent(R.id.widget_add_day, pending(2, dayIntent))
        views.setOnClickPendingIntent(R.id.widget_add_reminder, pending(3, reminderIntent))
    }

    override fun createViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_list)
        views.setTextViewText(R.id.widget_list_title, "Próximos eventos")
        views.setTextViewText(R.id.widget_list_subtitle, "Tu agenda")
        views.removeAllViews(R.id.widget_list_items)
        val today = LocalDate.now()
        val events = WidgetData.events(context, today, today.plusDays(31)).take(5)
        if (events.isEmpty()) {
            views.setTextViewText(R.id.widget_list_empty, "No hay próximos eventos")
            views.setViewVisibility(R.id.widget_list_empty, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_list_empty, android.view.View.GONE)
            events.forEach { event ->
                val item = RemoteViews(context.packageName, R.layout.widget_list_item)
                val date = java.time.Instant.ofEpochMilli(event.begin).atZone(widgetZone)
                val dateLabel = date.format(DateTimeFormatter.ofPattern("EEE d MMM", widgetLocale))
                val timeLabel = if (event.allDay) "Todo el día" else date.format(DateTimeFormatter.ofPattern("HH:mm"))
                item.setTextViewText(R.id.widget_item_title, event.title)
                item.setTextViewText(R.id.widget_item_detail, "$dateLabel · $timeLabel")
                views.addView(R.id.widget_list_items, item)
            }
        }
        return views
    }
}

class BirthdayWidgetProvider : BaseCalendarWidgetProvider() {
    override fun createViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_list)
        views.setViewVisibility(R.id.widget_action_buttons, android.view.View.GONE)
        views.setTextViewText(R.id.widget_list_title, "Cumpleaños")
        views.setTextViewText(R.id.widget_list_subtitle, "De tus contactos")
        views.removeAllViews(R.id.widget_list_items)
        val birthdays = WidgetData.birthdays(context, LocalDate.now())
        if (birthdays.isEmpty()) {
            views.setTextViewText(R.id.widget_list_empty, "Activa el permiso de Contactos en la app")
            views.setViewVisibility(R.id.widget_list_empty, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_list_empty, android.view.View.GONE)
            birthdays.forEach { (birthday, date) ->
                val item = RemoteViews(context.packageName, R.layout.widget_list_item)
                val age = birthday.ageOn(date)?.let { " · $it años" }.orEmpty()
                item.setTextViewText(R.id.widget_item_title, birthday.name)
                item.setTextViewText(R.id.widget_item_detail,
                    date.format(DateTimeFormatter.ofPattern("EEE d MMM", widgetLocale)) + age)
                views.addView(R.id.widget_list_items, item)
            }
        }
        return views
    }
}
