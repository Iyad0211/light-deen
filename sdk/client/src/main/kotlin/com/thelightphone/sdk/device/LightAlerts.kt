package com.thelightphone.sdk.device

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.thelightphone.sdk.R
import com.thelightphone.sdk.SealedLightContext
import org.json.JSONArray
import org.json.JSONObject

/*
 * LOCAL SDK EXTENSION (light-deen fork).
 *
 * Light SDK 0.2.1 can only run background work through WorkManager (LightWork), which
 * Android may delay by many minutes - too late for an adhan alert. This schedules exact,
 * doze-proof alarms with AlarmManager and posts a notification when each one is due.
 *
 * The tool hands over a plain list of future alerts; the Context never leaves the SDK.
 * Only the next alert is armed at any time; each firing arms the one after it, and the
 * list is re-armed after a reboot, a clock change or an app update.
 */

/** One scheduled alert. */
data class LightAlert(
    val atEpochMs: Long,
    val title: String,
    val text: String,
    /** Play the notification sound (false = vibrate only). */
    val sound: Boolean = true,
)

object LightAlerts {
    private const val PREFS = "light_sdk_alerts"
    private const val KEY = "alerts"
    private const val ACTION_FIRE = "com.thelightphone.sdk.device.ALERT_FIRE"
    internal const val CHANNEL_SOUND = "light_alerts_sound"
    internal const val CHANNEL_QUIET = "light_alerts_vibrate"

    /** Replace every pending alert with [alerts] and arm the next one. */
    fun replaceAll(context: SealedLightContext, alerts: List<LightAlert>) =
        replaceAll(context.androidContext, alerts)

    fun clear(context: SealedLightContext) = replaceAll(context.androidContext, emptyList())

    /** Whether the user lets this tool post notifications at all. */
    fun notificationsEnabled(context: SealedLightContext): Boolean {
        val ctx = context.androidContext
        val granted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** Whether alarms will fire on the exact minute (otherwise Android may batch them). */
    fun exactAlarmsAllowed(context: SealedLightContext): Boolean {
        val am = context.androidContext.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    /** The next alert that is armed, if any (for display / debugging). */
    fun nextPending(context: SealedLightContext): LightAlert? =
        load(context.androidContext).firstOrNull { it.atEpochMs > System.currentTimeMillis() }

    /** Post a notification right now, so the user can hear what an alert sounds like. */
    fun test(context: SealedLightContext, alert: LightAlert) = post(context.androidContext, alert, 999)

    // ---- internals -------------------------------------------------------------------

    internal fun replaceAll(ctx: Context, alerts: List<LightAlert>) {
        val now = System.currentTimeMillis()
        val future = alerts.filter { it.atEpochMs > now }.sortedBy { it.atEpochMs }
        save(ctx, future)
        armNext(ctx)
    }

    internal fun armNext(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = firePendingIntent(ctx)
        am.cancel(pi)
        val now = System.currentTimeMillis()
        val pending = load(ctx).filter { it.atEpochMs > now - 60_000 }
        save(ctx, pending)
        val next = pending.firstOrNull() ?: return
        val at = maxOf(next.atEpochMs, now + 500)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Called when the armed alarm goes off: post everything that is due, then arm the next. */
    internal fun onFire(ctx: Context) {
        val now = System.currentTimeMillis()
        val all = load(ctx)
        // anything due now (or missed by up to 10 minutes while the phone was off) is shown
        val due = all.filter { it.atEpochMs <= now + 30_000 }
        due.filter { it.atEpochMs >= now - 10 * 60_000 }.forEachIndexed { i, a ->
            post(ctx, a, (a.atEpochMs / 60_000).toInt() + i)
        }
        save(ctx, all - due.toSet())
        armNext(ctx)
    }

    private fun post(ctx: Context, a: LightAlert, id: Int) {
        ensureChannels(ctx)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val content = launch?.let {
            PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = NotificationCompat.Builder(ctx, if (a.sound) CHANNEL_SOUND else CHANNEL_QUIET)
            .setSmallIcon(R.drawable.light_sdk_alert)
            .setContentTitle(a.title)
            .setContentText(a.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(a.text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setWhen(a.atEpochMs)
            .setShowWhen(true)
            .apply { content?.let { setContentIntent(it) } }
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    private fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_SOUND) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SOUND, "Alerts with sound", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_QUIET) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_QUIET, "Silent alerts (vibrate)", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                    setSound(null, null)
                },
            )
        }
    }

    private fun firePendingIntent(ctx: Context): PendingIntent {
        val i = Intent(ctx, LightAlertReceiver::class.java).setAction(ACTION_FIRE)
        return PendingIntent.getBroadcast(ctx, 7, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun save(ctx: Context, alerts: List<LightAlert>) {
        val arr = JSONArray()
        alerts.forEach {
            arr.put(JSONObject().put("t", it.atEpochMs).put("ti", it.title).put("tx", it.text).put("s", it.sound))
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(ctx: Context): List<LightAlert> {
        val raw = prefs(ctx).getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                LightAlert(o.getLong("t"), o.getString("ti"), o.getString("tx"), o.optBoolean("s", true))
            }.sortedBy { it.atEpochMs }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** Fires each alert and re-arms the schedule after reboot / clock change / update. */
class LightAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.thelightphone.sdk.device.ALERT_FIRE" -> LightAlerts.onFire(context)
            else -> LightAlerts.armNext(context)
        }
    }
}
