package es.calendario.personal

import android.content.Context
import android.provider.ContactsContract
import java.time.LocalDate

data class ContactBirthday(
    val contactId: Long,
    val name: String,
    val month: Int,
    val day: Int,
    val birthYear: Int?
) {
    fun occursOn(date: LocalDate): Boolean = month == date.monthValue && day == date.dayOfMonth

    fun ageOn(date: LocalDate): Int? =
        birthYear?.takeIf { it > 0 && it <= date.year }?.let { date.year - it }
}

class ContactsRepository(private val context: Context) {
    fun birthdays(): List<ContactBirthday> {
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Event.START_DATE,
            ContactsContract.CommonDataKinds.Event.TYPE
        )
        val selection = "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Event.TYPE}=?"
        val args = arrayOf(
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
        )
        val result = mutableListOf<ContactBirthday>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            args,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(ContactsContract.Data.DISPLAY_NAME_PRIMARY)
            val dateColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.START_DATE)
            while (cursor.moveToNext()) {
                val raw = cursor.getString(dateColumn)?.trim().orEmpty()
                val parsed = parseBirthday(raw) ?: continue
                val name = cursor.getString(nameColumn)?.takeIf { it.isNotBlank() } ?: "Contacto"
                result += ContactBirthday(
                    contactId = cursor.getLong(idColumn),
                    name = name,
                    month = parsed.second,
                    day = parsed.third,
                    birthYear = parsed.first
                )
            }
        }
        return result.distinctBy { "${it.contactId}-${it.month}-${it.day}" }
            .sortedWith(compareBy<ContactBirthday> { it.month }.thenBy { it.day }.thenBy { it.name })
    }

    private fun parseBirthday(value: String): Triple<Int?, Int, Int>? {
        val normalized = value.removePrefix("--")
        val match = Regex("^(?:(\\d{4})-)?(\\d{2})-(\\d{2})$").matchEntire(normalized)
            ?: Regex("^(\\d{4})(\\d{2})(\\d{2})$").matchEntire(normalized)
                ?.let { return validBirthday(it.groupValues[2].toInt(), it.groupValues[3].toInt(), it.groupValues[1].toIntOrNull()) }
            ?: return null
        val year = match.groupValues[1].takeIf { it.isNotBlank() }?.toIntOrNull()
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        return validBirthday(month, day, year)
    }

    private fun validBirthday(month: Int, day: Int, year: Int?): Triple<Int?, Int, Int>? {
        return try {
            java.time.LocalDate.of(2000, month, day)
            Triple(year, month, day)
        } catch (_: java.time.DateTimeException) {
            null
        }
    }
}
