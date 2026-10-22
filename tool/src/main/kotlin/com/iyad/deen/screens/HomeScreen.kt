package com.iyad.deen.screens

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.iyad.deen.core.Coordinates
import com.iyad.deen.core.DeenSettings
import com.iyad.deen.core.Hijri
import com.iyad.deen.core.PlaceFinder
import com.iyad.deen.core.Prayer
import com.iyad.deen.core.SettingsStore
import com.iyad.deen.core.ensureRefreshJob
import com.iyad.deen.core.formatClock
import com.iyad.deen.core.localDayTimes
import com.iyad.deen.core.refreshAlerts
import com.iyad.deen.core.upcomingPrayers
import com.iyad.deen.ui.CenteredMessage
import com.iyad.deen.ui.DeenScreen
import com.iyad.deen.ui.MenuRow
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberPermissionRequestLauncher
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Settings + location plumbing shared by every screen that needs a place. */
open class PlaceViewModel(val ctx: SealedLightContext) : LightViewModel<Unit>() {
    val settings: StateFlow<DeenSettings?> =
        SettingsStore.flow(ctx.dataStore).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val locating = MutableStateFlow(false)
    val locateFailed = MutableStateFlow(false)
    val now = MutableStateFlow(Instant.now())

    init {
        viewModelScope.launch {
            while (true) {
                now.value = Instant.now()
                delay(15_000)
            }
        }
        viewModelScope.launch {
            settings.collect { s -> if (s != null) applyTheme(s) }
        }
    }

    fun locate() {
        if (locating.value) return
        viewModelScope.launch {
            locating.value = true
            locateFailed.value = false
            val fix = PlaceFinder.locate()
            if (fix == null) {
                locateFailed.value = true
            } else {
                val place = PlaceFinder.describe(fix.first, fix.second, ctx)
                SettingsStore.update(ctx.dataStore) { place(place) }
                refreshAlerts(ctx)
            }
            locating.value = false
        }
    }

    fun update(block: SettingsStore.Editor.() -> Unit, reschedule: Boolean = false) {
        viewModelScope.launch {
            SettingsStore.update(ctx.dataStore, block)
            if (reschedule) refreshAlerts(ctx)
        }
    }
}

fun applyTheme(s: DeenSettings) {
    if (s.darkApp) LightThemeController.setDarkTheme() else LightThemeController.setLightTheme()
}

class HomeViewModel(ctx: SealedLightContext) : PlaceViewModel(ctx) {
    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        now.value = Instant.now()
        viewModelScope.launch {
            ensureRefreshJob(ctx)
            refreshAlerts(ctx)
        }
    }
}

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

fun untilText(seconds: Long): String {
    val m = (seconds + 59) / 60
    return when {
        m < 1 -> "now"
        m < 60 -> "in ${m}m"
        else -> "in ${m / 60}h ${m % 60}m"
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeViewModel>(sealedActivity) {
    override val viewModelClass = HomeViewModel::class.java
    override fun createViewModel() = HomeViewModel(lightContext)

    private var resumes by mutableIntStateOf(0)
    override fun willShow() { resumes++ }

    @Composable
    override fun Content() {
        val s by viewModel.settings.collectAsState()
        val now by viewModel.now.collectAsState()
        val locating by viewModel.locating.collectAsState()
        val failed by viewModel.locateFailed.collectAsState()
        val permission = rememberPermissionRequestLauncher(Manifest.permission.ACCESS_FINE_LOCATION)

        // first run: once permission is granted, find the place automatically
        LaunchedEffect(resumes, s?.place) {
            val settings = s ?: return@LaunchedEffect
            if (settings.place == null && PlaceFinder.hasPermission()) viewModel.locate()
        }

        DeenScreen(title = "Deen", onBack = null) {
            val settings = s ?: return@DeenScreen
            val place = settings.place
            val zone = ZoneId.systemDefault()

            LightScrollView(Modifier.fillMaxWidth().weight(1f)) {
                // header: dates and the next prayer
                Column(Modifier.padding(horizontal = 2f.gridUnitsAsDp()).padding(bottom = 1f.gridUnitsAsDp())) {
                    val today = LocalDate.now(zone)
                    var afterMaghrib = false
                    if (place != null) {
                        val coords = Coordinates(place.lat, place.lon)
                        val times = localDayTimes(coords, settings.params(), zone, today)
                        afterMaghrib = times.maghrib?.let { now.epochSecond >= it } ?: false
                        val next = upcomingPrayers(coords, settings.params(), zone, now, 2)
                            .firstOrNull { it.first.isSalah || it.first == Prayer.Sunrise }
                        if (next != null) {
                            LightText(next.first.english, LightTextVariant.Subtitle)
                            LightText(
                                "${untilText(next.second - now.epochSecond)} · ${formatClock(next.second, zone)}",
                                LightTextVariant.Copy,
                                lighten = true,
                            )
                        }
                    }
                    val h = Hijri.today(now, afterMaghrib)
                    LightText(
                        "${DAY_FMT.format(today)} · ${h.english}",
                        LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                    )
                }

                if (place == null) {
                    CenteredMessage(
                        text = when {
                            locating -> "Finding your location…"
                            failed -> "Couldn't get a location. Go outside for a GPS fix, or set a default location on the Light dashboard."
                            else -> "Prayer times and the Qibla need your location."
                        },
                        action = if (locating) null else "Find my location",
                        onAction = {
                            permission?.launch()
                            viewModel.locate()
                        },
                    )
                }

                MenuRow("Prayer times", detail = place?.name) { navigateTo(::PrayerTimesScreen) }
                MenuRow("Qibla") { navigateTo(::QiblaScreen) }
                MenuRow(
                    "Quran",
                    detail = if (settings.lastSurah > 0) "Continue · surah ${settings.lastSurah}, ayah ${settings.lastAyah}" else null,
                ) { navigateTo(::QuranIndexScreen) }
                MenuRow("Morning azkar") { navigateTo({ a -> AzkarScreen(a, morning = true) }) }
                MenuRow("Evening azkar") { navigateTo({ a -> AzkarScreen(a, morning = false) }) }
                MenuRow("Settings") { navigateTo(::SettingsScreen) }
            }
        }
    }
}
