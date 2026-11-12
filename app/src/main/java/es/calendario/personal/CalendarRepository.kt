package es.calendario.personal

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Instances
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CalendarEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val calendarId: Long,
    val color: Int
)

data class WritableCalendar(val id: Long, val name: String, val accountName: String)

class CalendarRepository(private val context: Context) {
    fun eventsBetween(start: LocalDate, endExclusive: LocalDate): List<CalendarEvent> {
        val zone = ZoneId.systemDefault()
        val beginMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon()
            .appendPath(beginMillis.toString())
            .appendPath(endMillis.toString())
            .build()
        val projection = arrayOf(
            Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
            Instances.ALL_DAY, Instances.CALENDAR_ID, Instances.DISPLAY_COLOR
        )
        val result = mutableListOf<CalendarEvent>()
        context.contentResolver.query(
            uri, projection, null, null, "${Instances.BEGIN} ASC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(Instances.EVENT_ID)
            val title = cursor.getColumnIndexOrThrow(Instances.TITLE)
            val begin = cursor.getColumnIndexOrThrow(Instances.BEGIN)
            val end = cursor.getColumnIndexOrThrow(Instances.END)
            val allDay = cursor.getColumnIndexOrThrow(Instances.ALL_DAY)
            val calendar = cursor.getColumnIndexOrThrow(Instances.CALENDAR_ID)
            val color = cursor.getColumnIndexOrThrow(Instances.DISPLAY_COLOR)
            while (cursor.moveToNext()) {
                result += CalendarEvent(
                    id = cursor.getLong(id),
                    title = cursor.getString(title) ?: "(Sin título)",
                    begin = cursor.getLong(begin),
                    end = cursor.getLong(end),
                    allDay = cursor.getInt(allDay) == 1,
                    calendarId = cursor.getLong(calendar),
                    color = if (cursor.isNull(color)) 0xFF5263A8.toInt() else cursor.getInt(color)
                )
            }
        }
        return result
    }

    fun writableCalendars(): List<WritableCalendar> {
        val projection = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME)
        val selection = "${Calendars.VISIBLE}=1 AND ${Calendars.CALENDAR_ACCESS_LEVEL}>=?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val result = mutableListOf<WritableCalendar>()
        context.contentResolver.query(
            Calendars.CONTENT_URI, projection, selection, args,
            "${Calendars.CALENDAR_DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(Calendars._ID)
            val name = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME)
            val account = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME)
            while (cursor.moveToNext()) {
                result += WritableCalendar(
                    cursor.getLong(id),
                    cursor.getString(name) ?: "Calendario",
                    cursor.getString(account) ?: ""
                )
            }
        }
        return result
    }

    fun saveEvent(
        eventId: Long?,
        calendarId: Long,
        title: String,
        description: String,
        begin: Long,
        end: Long,
        allDay: Boolean
    ): Long {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title.trim())
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, begin)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else ZoneId.systemDefault().id)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
        }
        return if (eventId == null) {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: error("Android no ha permitido crear el evento")
            android.content.ContentUris.parseId(uri)
        } else {
            val uri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val updated = context.contentResolver.update(uri, values, null, null)
            if (updated == 0) error("No se ha podido actualizar el evento")
            eventId
        }
    }


    fun eventDescription(eventId: Long): String {
        val uri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Events.DESCRIPTION),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0).orEmpty()
        }
        return ""
    }

    fun deleteEvent(eventId: Long) {
        val uri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        if (context.contentResolver.delete(uri, null, null) == 0) {
            error("No se ha podido eliminar el evento")
        }
    }

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}
