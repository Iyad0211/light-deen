package com.iyad.deen.screens

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.iyad.deen.core.Coordinates
import com.iyad.deen.core.Prayer
import com.iyad.deen.core.formatClock
import com.iyad.deen.core.localDayTimes
import com.iyad.deen.ui.CenteredMessage
import com.iyad.deen.ui.DeenScreen
import com.iyad.deen.ui.MenuRow
import com.iyad.deen.ui.SectionLabel
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberPermissionRequestLauncher
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PrayerTimesViewModel(ctx: com.thelightphone.sdk.SealedLightContext) : PlaceViewModel(ctx)

private val DATE_FMT = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

class PrayerTimesScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, PrayerTimesViewModel>(sealedActivity) {
    override val viewModelClass = PrayerTimesViewModel::class.java
    override fun createViewModel() = PrayerTimesViewModel(lightContext)

    @Composable
    override fun Content() {
        val s by viewModel.settings.collectAsState()
        val now by viewModel.now.collectAsState()
        val locating by viewModel.locating.collectAsState()
        val failed by viewModel.locateFailed.collectAsState()
        val permission = rememberPermissionRequestLauncher(Manifest.permission.ACCESS_FINE_LOCATION)
        val zone = ZoneId.systemDefault()
        var dayOffset by remember { mutableLongStateOf(0L) }

        DeenScreen("Prayer times", onBack = { goBack() }) {
            val settings = s ?: return@DeenScreen
            val place = settings.place
            if (place == null) {
                CenteredMessage(
                    if (locating) "Finding your location…" else "Location needed.",
                    if (locating) null else "Find my location",
                ) { permission?.launch(); viewModel.locate() }
                return@DeenScreen
            }
            val date = LocalDate.now(zone).plusDays(dayOffset)
            val times = localDayTimes(Coordinates(place.lat, place.lon), settings.params(), zone, date)
            val nextPrayer = if (dayOffset == 0L) {
                times.asList().firstOrNull { (_, t) -> t != null && t > now.epochSecond }?.first
            } else null

            LightScrollView(Modifier.fillMaxWidth().weight(1f)) {
                // day switcher
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.25f.gridUnitsAsDp()),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LightText("‹", LightTextVariant.Heading, modifier = Modifier.lightClickable { dayOffset-- }.padding(end = 1f.gridUnitsAsDp()))
                    LightText(
                        if (dayOffset == 0L) "Today" else DATE_FMT.format(date),
                        LightTextVariant.Copy,
                        modifier = Modifier.lightClickable { dayOffset = 0 },
                    )
                    LightText("›", LightTextVariant.Heading, modifier = Modifier.lightClickable { dayOffset++ }.padding(start = 1f.gridUnitsAsDp()))
                }

                for ((prayer, t) in times.asList()) {
                    val on = settings.alerts[prayer] == true
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .lightClickable {
                                viewModel.update({ alert(prayer, !on) }, reschedule = true)
                            }
                            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.45f.gridUnitsAsDp()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val isNext = prayer == nextPrayer
                        LightText(
                            (if (isNext) "› " else "") + prayer.english,
                            LightTextVariant.Heading,
                            lighten = !prayer.isSalah && !isNext,
                            modifier = Modifier.weight(1f),
                        )
                        LightText(
                            t?.let { formatClock(it, zone) } ?: "—",
                            LightTextVariant.Heading,
                            lighten = !prayer.isSalah && !isNext,
                        )
                        LightIcon(
                            icon = if (on) LightIcons.ALARM else LightIcons.MUTE,
                            modifier = Modifier.padding(start = 1f.gridUnitsAsDp()),
                        )
                    }
                }
                LightText(
                    "Tap a prayer to turn its alert on or off.",
                    LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
                )

                SectionLabel("Location")
                MenuRow(
                    if (locating) "Updating…" else place.name.ifBlank { "Current location" },
                    detail = if (failed) "Couldn't get a new fix. Tap to retry." else "Tap to update from GPS",
                    variant = LightTextVariant.Copy,
                ) { permission?.launch(); viewModel.locate() }

                SectionLabel("Calculation")
                MenuRow(
                    settings.effectiveMethod.label + if (settings.method == null) " (auto)" else "",
                    detail = "Asr: " + settings.madhab.label,
                    variant = LightTextVariant.Copy,
                ) { navigateTo(::SettingsScreen) }
            }
        }
    }
}
