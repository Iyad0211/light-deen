package com.iyad.deen

import com.iyad.deen.core.CalculationMethod
import com.iyad.deen.core.CalculationParameters
import com.iyad.deen.core.Coordinates
import com.iyad.deen.core.Madhab
import com.iyad.deen.core.PrayerTimes
import com.iyad.deen.core.Qibla
import com.iyad.deen.core.juzOf
import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Expected values come from the reference adhan-js 4.4.6 library. */
class PrayerCalcTest {
    private fun check(c: Coordinates, d: LocalDate, m: CalculationMethod, mad: Madhab, expected: List<Long>) {
        val p = CalculationParameters(m, mad, CalculationParameters.recommendedHighLatitudeRule(c))
        val t = PrayerTimes.calculate(c, d, p)
        val got = listOf(t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha)
        got.zip(expected).forEach { (g, e) -> assertTrue(abs(g!! - e) <= 1, "expected $e got $g ($m $d)") }
    }

    @Test fun doha() = check(
        Coordinates(25.2854, 51.5310), LocalDate.of(2026, 10, 22), CalculationMethod.Qatar, Madhab.Shafi,
        listOf(1792631940, 1792636560, 1792657080, 1792668840, 1792677660, 1792683060),
    )

    @Test fun londonMoonsightingHanafi() = check(
        Coordinates(51.5074, -0.1278), LocalDate.of(2026, 12, 21), CalculationMethod.MoonsightingCommittee, Madhab.Hanafi,
        listOf(1797834120, 1797840240, 1797854640, 1797862020, 1797868560, 1797874320),
    )

    @Test fun newYorkIsna() = check(
        Coordinates(40.7128, -74.0060), LocalDate.of(2026, 6, 21), CalculationMethod.NorthAmerica, Madhab.Shafi,
        listOf(1782027900, 1782033900, 1782061140, 1782075480, 1782088260, 1782094260),
    )

    @Test fun osloSummerHighLatitude() = check(
        Coordinates(59.9139, 10.7522), LocalDate.of(2026, 6, 21), CalculationMethod.MuslimWorldLeague, Madhab.Shafi,
        listOf(1782004140, 1782006840, 1782040800, 1782057600, 1782074640, 1782077281),
    )

    @Test fun qibla() {
        assertEquals(252.62, Qibla.bearing(Coordinates(25.2854, 51.5310)), 0.01)
        assertEquals(118.99, Qibla.bearing(Coordinates(51.5074, -0.1278)), 0.01)
        assertEquals(58.48, Qibla.bearing(Coordinates(40.7128, -74.0060)), 0.01)
    }

    @Test fun juz() {
        assertEquals(1, juzOf(1, 1))
        assertEquals(2, juzOf(2, 142))
        assertEquals(1, juzOf(2, 141))
        assertEquals(30, juzOf(114, 6))
    }
}
