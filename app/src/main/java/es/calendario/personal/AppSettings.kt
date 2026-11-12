package es.calendario.personal

import android.content.Context

/**
 * Preferences local to this installation. Calendar data itself stays in Android's
 * Calendar Provider; these values only control how this app displays and creates events.
 */
data class AppSettings(
    val theme: String = "auto",
    val accentColor: String = "cyan",
    val firstDayOfWeek: Int = 1,
    val visibleCalendarIds: Set<Long>? = null,
    val defaultDurationMinutes: Int = 60,
    val defaultReminderMinutes: Int = 0,
    val showBirthdays: Boolean = true,
    val widgetDays: Int = 30,
    val widgetEventCount: Int = 5
) {
    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("theme", theme)
            .putString("accent_color", accentColor)
            .putInt("first_day_of_week", firstDayOfWeek)
            .putBoolean("visible_calendars_configured", visibleCalendarIds != null)
            .putStringSet("visible_calendar_ids", visibleCalendarIds?.map { it.toString() }?.toMutableSet() ?: mutableSetOf())
            .putInt("default_duration", defaultDurationMinutes)
            .putInt("default_reminder", defaultReminderMinutes)
            .putBoolean("show_birthdays", showBirthdays)
            .putInt("widget_days", widgetDays)
            .putInt("widget_event_count", widgetEventCount)
            .apply()
    }

    companion object {
        const val PREFS = "calendar_app_settings"

        fun load(context: Context): AppSettings {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val configured = prefs.getBoolean("visible_calendars_configured", false)
            val ids = if (configured) prefs.getStringSet("visible_calendar_ids", mutableSetOf())
                .orEmpty().mapNotNull { it.toLongOrNull() }.toSet() else null
            return AppSettings(
                theme = prefs.getString("theme", "auto") ?: "auto",
                accentColor = prefs.getString("accent_color", "cyan") ?: "cyan",
                firstDayOfWeek = prefs.getInt("first_day_of_week", 1).let { if (it == 7) 7 else 1 },
                visibleCalendarIds = ids,
                defaultDurationMinutes = prefs.getInt("default_duration", 60).coerceIn(15, 90),
                defaultReminderMinutes = prefs.getInt("default_reminder", 0).coerceIn(0, 60),
                showBirthdays = prefs.getBoolean("show_birthdays", true),
                widgetDays = prefs.getInt("widget_days", 30).coerceIn(7, 60),
                widgetEventCount = prefs.getInt("widget_event_count", 5).coerceIn(3, 15)
            )
        }
    }
}
