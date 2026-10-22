package com.iyad.deen.screens

import android.Manifest
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.iyad.deen.core.CalculationMethod
import com.iyad.deen.core.Madhab
import com.iyad.deen.core.Prayer
import com.iyad.deen.core.ReaderTheme
import com.iyad.deen.core.defaultMethodFor
import com.iyad.deen.ui.DeenScreen
import com.iyad.deen.ui.MenuRow
import com.iyad.deen.ui.SectionLabel
import com.iyad.deen.ui.ToggleRow
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.device.LightAlert
import com.thelightphone.sdk.device.LightAlerts
import com.thelightphone.sdk.rememberPermissionRequestLauncher
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

class SettingsViewModel(ctx: SealedLightContext) : PlaceViewModel(ctx)

private val REMINDER_STEPS = listOf(0, 5, 10, 15, 20, 30)

class SettingsScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SettingsViewModel>(sealedActivity) {
    override val viewModelClass = SettingsViewModel::class.java
    override fun createViewModel() = SettingsViewModel(lightContext)

    private var resumes by mutableIntStateOf(0)
    override fun willShow() { resumes++ }

    @Composable
    override fun Content() {
        val s by viewModel.settings.collectAsState()
        val notifyPermission = rememberPermissionRequestLauncher(Manifest.permission.POST_NOTIFICATIONS)
        var notificationsOn by remember { mutableStateOf(true) }
        var exactOk by remember { mutableStateOf(true) }
        LaunchedEffect(resumes) {
            notificationsOn = LightAlerts.notificationsEnabled(lightContext)
            exactOk = LightAlerts.exactAlarmsAllowed(lightContext)
        }

        DeenScreen("Settings", onBack = { goBack() }) {
            val st = s ?: return@DeenScreen
            fun set(reschedule: Boolean = false, block: com.iyad.deen.core.SettingsStore.Editor.() -> Unit) =
                viewModel.update(block, reschedule)

            LightScrollView(Modifier.fillMaxWidth().weight(1f)) {
                SectionLabel("Prayer alerts")
                if (!notificationsOn) {
                    MenuRow(
                        "Allow notifications",
                        detail = "Needed for prayer alerts",
                        variant = LightTextVariant.Copy,
                    ) { notifyPermission?.launch(); resumes++ }
                }
                for (p in Prayer.entries) {
                    ToggleRow(
                        p.english,
                        detail = if (p == Prayer.Sunrise) "End of Fajr time" else null,
                        checked = st.alerts[p] == true,
                    ) { on -> set(true) { alert(p, on) } }
                }
                ToggleRow("Alert sound", detail = if (st.alertSound) "Sound and vibration" else "Vibrate only", checked = st.alertSound) { on ->
                    set(true) { alertSound(on) }
                }
                MenuRow(
                    "Reminder before",
                    detail = "An extra quiet alert before each prayer",
                    variant = LightTextVariant.Copy,
                    trailing = if (st.reminderMinutes == 0) "Off" else "${st.reminderMinutes} min",
                ) {
                    val i = REMINDER_STEPS.indexOf(st.reminderMinutes).coerceAtLeast(0)
                    set(true) { reminderMinutes(REMINDER_STEPS[(i + 1) % REMINDER_STEPS.size]) }
                }
                ToggleRow("Morning azkar reminder", detail = "20 minutes after Fajr", checked = st.morningAzkarReminder) { on ->
                    set(true) { morningAzkar(on) }
                }
                ToggleRow("Evening azkar reminder", detail = "20 minutes after Asr", checked = st.eveningAzkarReminder) { on ->
                    set(true) { eveningAzkar(on) }
                }
                MenuRow("Send a test alert", variant = LightTextVariant.Copy) {
                    LightAlerts.test(
                        lightContext,
                        LightAlert(System.currentTimeMillis(), "Test alert", "حان الآن موعد الصلاة", st.alertSound),
                    )
                }
                if (!exactOk) {
                    LightText(
                        "This phone may deliver alerts a few minutes late.",
                        LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                    )
                }

                SectionLabel("Calculation")
                MenuRow(
                    "Method",
                    detail = st.effectiveMethod.label + if (st.method == null) " (automatic)" else "",
                    variant = LightTextVariant.Copy,
                ) { navigateTo(::MethodScreen) }
                MenuRow(
                    "Asr time",
                    detail = st.madhab.label,
                    variant = LightTextVariant.Copy,
                ) {
                    set(true) { madhab(if (st.madhab == Madhab.Shafi) Madhab.Hanafi else Madhab.Shafi) }
                }
                MenuRow(
                    "Adjust minutes",
                    detail = "Match your local mosque",
                    variant = LightTextVariant.Copy,
                ) { navigateTo(::AdjustScreen) }

                SectionLabel("Display")
                ToggleRow("Dark theme", checked = st.darkApp) { on -> set { darkApp(on) } }
                ToggleRow("Azkar translation", detail = "Show English under each dhikr", checked = st.showTranslation) { on ->
                    set { showTranslation(on) }
                }
                MenuRow(
                    "Quran background",
                    variant = LightTextVariant.Copy,
                    trailing = st.readerTheme.label,
                ) {
                    val all = ReaderTheme.entries
                    set { readerTheme(all[(all.indexOf(st.readerTheme) + 1) % all.size]) }
                }

                SectionLabel("About")
                LightText(
                    "Prayer times: adhan astronomy (MIT). Quran text: King Fahd Complex Uthmani script via quranenc.com. " +
                        "Font: Amiri Quran (OFL). Azkar: Hisn al-Muslim via muslim-data (Apache 2.0). Everything works offline; " +
                        "your location never leaves the phone.",
                    LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
                )
            }
        }
    }
}

class MethodScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SettingsViewModel>(sealedActivity) {
    override val viewModelClass = SettingsViewModel::class.java
    override fun createViewModel() = SettingsViewModel(lightContext)

    @Composable
    override fun Content() {
        val s by viewModel.settings.collectAsState()
        DeenScreen("Method", onBack = { goBack() }) {
            val st = s ?: return@DeenScreen
            LightScrollView(Modifier.fillMaxWidth().weight(1f)) {
                MenuRow(
                    "Automatic",
                    detail = "Uses ${defaultMethodFor(st.place?.countryCode).label} for your country",
                    variant = LightTextVariant.Copy,
                    trailing = if (st.method == null) "✓" else null,
                ) { viewModel.update({ method(null) }, reschedule = true); goBack() }
                for (m in CalculationMethod.entries) {
                    MenuRow(
                        m.label,
                        detail = if (m.ishaInterval > 0) "Fajr ${m.fajrAngle}° · Isha ${m.ishaInterval} min after Maghrib"
                        else "Fajr ${m.fajrAngle}° · Isha ${m.ishaAngle}°",
                        variant = LightTextVariant.Copy,
                        trailing = if (st.method == m) "✓" else null,
                    ) { viewModel.update({ method(m) }, reschedule = true); goBack() }
                }
            }
        }
    }
}

class AdjustScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SettingsViewModel>(sealedActivity) {
    override val viewModelClass = SettingsViewModel::class.java
    override fun createViewModel() = SettingsViewModel(lightContext)

    @Composable
    override fun Content() {
        val s by viewModel.settings.collectAsState()
        DeenScreen("Adjust minutes", onBack = { goBack() }) {
            val st = s ?: return@DeenScreen
            LightScrollView(Modifier.fillMaxWidth().weight(1f)) {
                for (p in Prayer.entries) {
                    val v = when (p) {
                        Prayer.Fajr -> st.adjustments.fajr
                        Prayer.Sunrise -> st.adjustments.sunrise
                        Prayer.Dhuhr -> st.adjustments.dhuhr
                        Prayer.Asr -> st.adjustments.asr
                        Prayer.Maghrib -> st.adjustments.maghrib
                        Prayer.Isha -> st.adjustments.isha
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.45f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LightText(p.english, LightTextVariant.Copy, modifier = Modifier.weight(1f))
                        LightText(
                            "−",
                            LightTextVariant.Heading,
                            modifier = Modifier
                                .lightClickable { viewModel.update({ adjustment(p, (v - 1).coerceIn(-30, 30)) }, reschedule = true) }
                                .padding(horizontal = 1f.gridUnitsAsDp()),
                        )
                        LightText(if (v > 0) "+$v" else "$v", LightTextVariant.Copy)
                        LightText(
                            "+",
                            LightTextVariant.Heading,
                            modifier = Modifier
                                .lightClickable { viewModel.update({ adjustment(p, (v + 1).coerceIn(-30, 30)) }, reschedule = true) }
                                .padding(horizontal = 1f.gridUnitsAsDp()),
                        )
                    }
                }
                LightText(
                    "Minutes added to the calculated time. Official timetables often add 1–3 minutes as a precaution.",
                    LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
                )
            }
        }
    }
}
