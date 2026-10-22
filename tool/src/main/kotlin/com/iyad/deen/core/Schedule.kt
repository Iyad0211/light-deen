package com.iyad.deen.core

import com.thelightphone.sdk.LightJob
import com.thelightphone.sdk.LightJobHandler
import com.thelightphone.sdk.LightJobResult
import com.thelightphone.sdk.LightWork
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.device.LightAlert
import com.thelightphone.sdk.device.LightAlerts
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.hours

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun formatClock(epochSec: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    CLOCK.format(Instant.ofEpochSecond(epochSec).atZone(zone))

/** How many days of alerts are handed to the alarm scheduler at once. */
private const val ALERT_DAYS = 14

/** Builds the next [ALERT_DAYS] days of prayer alerts from the current settings. */
fun buildAlerts(s: DeenSettings, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<LightAlert> {
    val place = s.place ?: return emptyList()
    if (!s.anyAlertOn) return emptyList()
    val coords = Coordinates(place.lat, place.lon)
    val events = upcomingPrayers(coords, s.params(), zone, now, ALERT_DAYS)
    val out = ArrayList<LightAlert>()
    for ((prayer, t) in events) {
        val at = t * 1000
        if (s.alerts[prayer] == true) {
            out += if (prayer == Prayer.Sunrise) {
                LightAlert(at, "Sunrise · ${formatClock(t, zone)}", "The time for Fajr has ended.", s.alertSound)
            } else {
                LightAlert(at, "${prayer.english} · ${formatClock(t, zone)}", "حان الآن موعد صلاة ${prayer.arabic.removePrefix("ال")}", s.alertSound)
            }
            if (s.reminderMinutes > 0 && prayer.isSalah) {
                out += LightAlert(
                    at - s.reminderMinutes * 60_000L,
                    "${prayer.english} in ${s.reminderMinutes} min",
                    "${prayer.english} at ${formatClock(t, zone)}",
                    false,
                )
            }
        }
        if (prayer == Prayer.Fajr && s.morningAzkarReminder) {
            out += LightAlert(at + 20 * 60_000L, "Morning azkar", "أذكار الصباح", false)
        }
        if (prayer == Prayer.Asr && s.eveningAzkarReminder) {
            out += LightAlert(at + 20 * 60_000L, "Evening azkar", "أذكار المساء", false)
        }
    }
    return out.sortedBy { it.atEpochMs }
}

/** Re-arm alerts from the saved settings. Safe to call often. */
suspend fun refreshAlerts(ctx: SealedLightContext) {
    val s = SettingsStore.get(ctx.dataStore)
    LightAlerts.replaceAll(ctx, buildAlerts(s))
}

const val REFRESH_JOB = "refresh-prayer-alerts"

/** Keeps the alert list topped up even if the tool isn't opened for weeks. */
fun ensureRefreshJob(ctx: SealedLightContext) {
    LightWork.enqueuePeriodic(ctx, REFRESH_JOB, repeatInterval = 12.hours)
}

@LightJob("refresh-prayer-alerts")
val refreshPrayerAlerts: LightJobHandler = { ctx, _ ->
    refreshAlerts(ctx)
    LightJobResult.Success(outputFilePath = null, message = null)
}
