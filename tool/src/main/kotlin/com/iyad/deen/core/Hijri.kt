package com.iyad.deen.core

import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import java.time.Instant

object Hijri {
    private val EN = listOf(
        "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qa'dah", "Dhu al-Hijjah",
    )
    private val AR = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    data class HDate(val day: Int, val month: Int, val year: Int) {
        val english get() = "$day ${EN[month]} $year AH"
        val arabic get() = "${day.toArabicDigits()} ${AR[month]} ${year.toArabicDigits()} هـ"
    }

    /**
     * Umm al-Qura date. The Islamic day begins at Maghrib, so pass [afterMaghrib] = true
     * in the evening to show the date that has already begun.
     */
    fun today(now: Instant = Instant.now(), afterMaghrib: Boolean = false): HDate {
        val cal = IslamicCalendar(TimeZone.getDefault())
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.timeInMillis = now.toEpochMilli()
        if (afterMaghrib) cal.add(IslamicCalendar.DAY_OF_MONTH, 1)
        return HDate(
            cal.get(IslamicCalendar.DAY_OF_MONTH),
            cal.get(IslamicCalendar.MONTH),
            cal.get(IslamicCalendar.YEAR),
        )
    }
}
