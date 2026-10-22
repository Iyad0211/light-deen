package com.iyad.deen.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ReaderTheme(val label: String) { Black("Black"), Light("Light"), Cream("Cream") }

data class SavedPlace(val lat: Double, val lon: Double, val name: String, val countryCode: String)

data class DeenSettings(
    val place: SavedPlace? = null,
    /** null = pick automatically from the country */
    val method: CalculationMethod? = null,
    val madhab: Madhab = Madhab.Shafi,
    val adjustments: Adjustments = Adjustments(),
    val alerts: Map<Prayer, Boolean> = Prayer.entries.associateWith { it.isSalah },
    val alertSound: Boolean = true,
    val reminderMinutes: Int = 0,
    val morningAzkarReminder: Boolean = false,
    val eveningAzkarReminder: Boolean = false,
    val darkApp: Boolean = true,
    val readerTheme: ReaderTheme = ReaderTheme.Cream,
    val readerFontSize: Int = 30,
    val showTranslation: Boolean = false,
    val lastSurah: Int = 0,
    val lastAyah: Int = 0,
) {
    val effectiveMethod: CalculationMethod get() = method ?: defaultMethodFor(place?.countryCode)

    fun params(): CalculationParameters {
        val coords = place?.let { Coordinates(it.lat, it.lon) }
        return CalculationParameters(
            method = effectiveMethod,
            madhab = madhab,
            highLatitudeRule = coords?.let { CalculationParameters.recommendedHighLatitudeRule(it) }
                ?: HighLatitudeRule.MiddleOfTheNight,
            adjustments = adjustments,
        )
    }

    val anyAlertOn: Boolean get() = alerts.values.any { it } || morningAzkarReminder || eveningAzkarReminder
}

object SettingsStore {
    private val LAT = doublePreferencesKey("lat")
    private val LON = doublePreferencesKey("lon")
    private val PLACE = stringPreferencesKey("place")
    private val CC = stringPreferencesKey("cc")
    private val METHOD = stringPreferencesKey("method")
    private val MADHAB = stringPreferencesKey("madhab")
    private fun adjKey(p: Prayer) = intPreferencesKey("adj_${p.name}")
    private fun alertKey(p: Prayer) = booleanPreferencesKey("alert_${p.name}")
    private val SOUND = booleanPreferencesKey("alert_sound")
    private val REMIND = intPreferencesKey("remind_min")
    private val AZ_M = booleanPreferencesKey("azkar_morning_alert")
    private val AZ_E = booleanPreferencesKey("azkar_evening_alert")
    private val DARK = booleanPreferencesKey("dark_app")
    private val RTHEME = stringPreferencesKey("reader_theme")
    private val RSIZE = intPreferencesKey("reader_size")
    private val TRANS = booleanPreferencesKey("show_translation")
    private val LAST_S = intPreferencesKey("last_surah")
    private val LAST_A = intPreferencesKey("last_ayah")

    fun read(p: Preferences): DeenSettings {
        val d = DeenSettings()
        val lat = p[LAT]
        val lon = p[LON]
        return DeenSettings(
            place = if (lat != null && lon != null) SavedPlace(lat, lon, p[PLACE] ?: "", p[CC] ?: "") else null,
            method = p[METHOD]?.let { n -> CalculationMethod.entries.firstOrNull { it.name == n } },
            madhab = p[MADHAB]?.let { n -> Madhab.entries.firstOrNull { it.name == n } } ?: d.madhab,
            adjustments = Adjustments(
                fajr = p[adjKey(Prayer.Fajr)] ?: 0,
                sunrise = p[adjKey(Prayer.Sunrise)] ?: 0,
                dhuhr = p[adjKey(Prayer.Dhuhr)] ?: 0,
                asr = p[adjKey(Prayer.Asr)] ?: 0,
                maghrib = p[adjKey(Prayer.Maghrib)] ?: 0,
                isha = p[adjKey(Prayer.Isha)] ?: 0,
            ),
            alerts = Prayer.entries.associateWith { p[alertKey(it)] ?: d.alerts.getValue(it) },
            alertSound = p[SOUND] ?: d.alertSound,
            reminderMinutes = p[REMIND] ?: d.reminderMinutes,
            morningAzkarReminder = p[AZ_M] ?: d.morningAzkarReminder,
            eveningAzkarReminder = p[AZ_E] ?: d.eveningAzkarReminder,
            darkApp = p[DARK] ?: d.darkApp,
            readerTheme = p[RTHEME]?.let { n -> ReaderTheme.entries.firstOrNull { it.name == n } } ?: d.readerTheme,
            readerFontSize = p[RSIZE] ?: d.readerFontSize,
            showTranslation = p[TRANS] ?: d.showTranslation,
            lastSurah = p[LAST_S] ?: 0,
            lastAyah = p[LAST_A] ?: 0,
        )
    }

    fun flow(store: DataStore<Preferences>): Flow<DeenSettings> = store.data.map { read(it) }

    suspend fun get(store: DataStore<Preferences>): DeenSettings = flow(store).first()

    suspend fun update(store: DataStore<Preferences>, block: Editor.() -> Unit) {
        store.edit { Editor(it).block() }
    }

    class Editor(private val p: MutablePreferences) {
        fun place(v: SavedPlace) {
            p[LAT] = v.lat; p[LON] = v.lon; p[PLACE] = v.name; p[CC] = v.countryCode
        }
        fun method(v: CalculationMethod?) { if (v == null) p.remove(METHOD) else p[METHOD] = v.name }
        fun madhab(v: Madhab) { p[MADHAB] = v.name }
        fun adjustment(prayer: Prayer, minutes: Int) { p[adjKey(prayer)] = minutes }
        fun alert(prayer: Prayer, on: Boolean) { p[alertKey(prayer)] = on }
        fun alertSound(v: Boolean) { p[SOUND] = v }
        fun reminderMinutes(v: Int) { p[REMIND] = v }
        fun morningAzkar(v: Boolean) { p[AZ_M] = v }
        fun eveningAzkar(v: Boolean) { p[AZ_E] = v }
        fun darkApp(v: Boolean) { p[DARK] = v }
        fun readerTheme(v: ReaderTheme) { p[RTHEME] = v.name }
        fun readerFontSize(v: Int) { p[RSIZE] = v }
        fun showTranslation(v: Boolean) { p[TRANS] = v }
        fun lastRead(surah: Int, ayah: Int) { p[LAST_S] = surah; p[LAST_A] = ayah }
    }
}
