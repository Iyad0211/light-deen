package com.iyad.deen.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.tan

/*
 * Prayer time astronomy.
 *
 * A Kotlin port of the core of "adhan" (https://github.com/batoulapps/adhan-js),
 * Copyright (c) Batoul Apps, MIT License. Equations from Jean Meeus,
 * "Astronomical Algorithms".
 */

data class Coordinates(val latitude: Double, val longitude: Double)

enum class Prayer(val english: String, val arabic: String) {
    Fajr("Fajr", "الفجر"),
    Sunrise("Sunrise", "الشروق"),
    Dhuhr("Dhuhr", "الظهر"),
    Asr("Asr", "العصر"),
    Maghrib("Maghrib", "المغرب"),
    Isha("Isha", "العشاء");

    /** Sunrise is shown but is not a prayer you are called to. */
    val isSalah: Boolean get() = this != Sunrise
}

enum class Madhab(val label: String, val shadowLength: Double) {
    Shafi("Standard (Shafi'i, Maliki, Hanbali)", 1.0),
    Hanafi("Hanafi (later Asr)", 2.0),
}

enum class HighLatitudeRule { MiddleOfTheNight, SeventhOfTheNight, TwilightAngle }

data class Adjustments(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
) {
    operator fun plus(o: Adjustments) = Adjustments(
        fajr + o.fajr, sunrise + o.sunrise, dhuhr + o.dhuhr,
        asr + o.asr, maghrib + o.maghrib, isha + o.isha,
    )
}

enum class CalculationMethod(
    val label: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaInterval: Int = 0,
    val maghribAngle: Double = 0.0,
    val methodAdjustments: Adjustments = Adjustments(),
    val roundUp: Boolean = false,
) {
    Qatar("Qatar", 18.0, 0.0, ishaInterval = 90),
    UmmAlQura("Umm al-Qura, Makkah", 18.5, 0.0, ishaInterval = 90),
    Dubai("Dubai", 18.2, 18.2, methodAdjustments = Adjustments(sunrise = -3, dhuhr = 3, asr = 3, maghrib = 3)),
    Kuwait("Kuwait", 18.0, 17.5),
    MuslimWorldLeague("Muslim World League", 18.0, 17.0, methodAdjustments = Adjustments(dhuhr = 1)),
    Egyptian("Egyptian General Authority", 19.5, 17.5, methodAdjustments = Adjustments(dhuhr = 1)),
    Karachi("Karachi", 18.0, 18.0, methodAdjustments = Adjustments(dhuhr = 1)),
    NorthAmerica("ISNA, North America", 15.0, 15.0, methodAdjustments = Adjustments(dhuhr = 1)),
    MoonsightingCommittee("Moonsighting Committee", 18.0, 18.0, methodAdjustments = Adjustments(dhuhr = 5, maghrib = 3)),
    Singapore("Singapore, Malaysia, Indonesia", 20.0, 18.0, methodAdjustments = Adjustments(dhuhr = 1), roundUp = true),
    Turkey("Diyanet, Turkey", 18.0, 17.0, methodAdjustments = Adjustments(sunrise = -7, dhuhr = 5, asr = 4, maghrib = 7)),
    Tehran("Tehran", 17.7, 14.0, maghribAngle = 4.5),
}

data class CalculationParameters(
    val method: CalculationMethod,
    val madhab: Madhab = Madhab.Shafi,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.MiddleOfTheNight,
    val adjustments: Adjustments = Adjustments(),
) {
    fun nightPortions(): Pair<Double, Double> = when (highLatitudeRule) {
        HighLatitudeRule.MiddleOfTheNight -> 0.5 to 0.5
        HighLatitudeRule.SeventhOfTheNight -> 1.0 / 7 to 1.0 / 7
        HighLatitudeRule.TwilightAngle -> method.fajrAngle / 60 to method.ishaAngle / 60
    }

    companion object {
        fun recommendedHighLatitudeRule(c: Coordinates) =
            if (c.latitude > 48) HighLatitudeRule.SeventhOfTheNight else HighLatitudeRule.MiddleOfTheNight
    }
}

internal object MathUtil {
    fun rad(d: Double) = d * Math.PI / 180.0
    fun deg(r: Double) = r * 180.0 / Math.PI
    fun normalize(num: Double, max: Double) = num - max * floor(num / max)
    fun unwind(a: Double) = normalize(a, 360.0)
    fun quadrantShift(a: Double): Double =
        if (a >= -180 && a <= 180) a else a - 360 * round(a / 360)
}

internal object Astro {
    fun meanSolarLongitude(t: Double) = MathUtil.unwind(280.4664567 + 36000.76983 * t + 0.0003032 * t.pow(2))
    fun meanLunarLongitude(t: Double) = MathUtil.unwind(218.3165 + 481267.8813 * t)
    fun ascendingLunarNodeLongitude(t: Double) =
        MathUtil.unwind(125.04452 - 1934.136261 * t + 0.0020708 * t.pow(2) + t.pow(3) / 450000)

    fun meanSolarAnomaly(t: Double) = MathUtil.unwind(357.52911 + 35999.05029 * t - 0.0001537 * t.pow(2))

    fun solarEquationOfTheCenter(t: Double, m: Double): Double {
        val mr = MathUtil.rad(m)
        return (1.914602 - 0.004817 * t - 0.000014 * t.pow(2)) * sin(mr) +
            (0.019993 - 0.000101 * t) * sin(2 * mr) +
            0.000289 * sin(3 * mr)
    }

    fun apparentSolarLongitude(t: Double, l0: Double): Double {
        val longitude = l0 + solarEquationOfTheCenter(t, meanSolarAnomaly(t))
        val omega = 125.04 - 1934.136 * t
        return MathUtil.unwind(longitude - 0.00569 - 0.00478 * sin(MathUtil.rad(omega)))
    }

    fun meanObliquityOfTheEcliptic(t: Double) =
        23.439291 - 0.013004167 * t - 0.0000001639 * t.pow(2) + 0.0000005036 * t.pow(3)

    fun apparentObliquityOfTheEcliptic(t: Double, e0: Double) =
        e0 + 0.00256 * cos(MathUtil.rad(125.04 - 1934.136 * t))

    fun meanSiderealTime(t: Double): Double {
        val jd = t * 36525 + 2451545.0
        return MathUtil.unwind(
            280.46061837 + 360.98564736629 * (jd - 2451545) + 0.000387933 * t.pow(2) - t.pow(3) / 38710000,
        )
    }

    fun nutationInLongitude(l0: Double, lp: Double, omega: Double) =
        -17.2 / 3600 * sin(MathUtil.rad(omega)) - 1.32 / 3600 * sin(2 * MathUtil.rad(l0)) -
            0.23 / 3600 * sin(2 * MathUtil.rad(lp)) + 0.21 / 3600 * sin(2 * MathUtil.rad(omega))

    fun nutationInObliquity(l0: Double, lp: Double, omega: Double) =
        9.2 / 3600 * cos(MathUtil.rad(omega)) + 0.57 / 3600 * cos(2 * MathUtil.rad(l0)) +
            0.1 / 3600 * cos(2 * MathUtil.rad(lp)) - 0.09 / 3600 * cos(2 * MathUtil.rad(omega))

    fun altitudeOfCelestialBody(phi: Double, delta: Double, h: Double): Double {
        val t1 = sin(MathUtil.rad(phi)) * sin(MathUtil.rad(delta))
        val t2 = cos(MathUtil.rad(phi)) * cos(MathUtil.rad(delta)) * cos(MathUtil.rad(h))
        return MathUtil.deg(asin(t1 + t2))
    }

    fun approximateTransit(l: Double, theta0: Double, a2: Double): Double {
        val lw = -l
        val m0 = MathUtil.normalize((a2 + lw - theta0) / 360, 1.0)
        val expected = MathUtil.normalize((12.0 - l / 15.0) / 24.0, 1.0)
        return when {
            m0 - expected > 0.5 -> m0 - 1.0
            expected - m0 > 0.5 -> m0 + 1.0
            else -> m0
        }
    }

    fun correctedTransit(m0: Double, l: Double, theta0: Double, a2: Double, a1: Double, a3: Double): Double {
        val lw = -l
        val theta = MathUtil.unwind(theta0 + 360.985647 * m0)
        val a = MathUtil.unwind(interpolateAngles(a2, a1, a3, m0))
        val h = MathUtil.quadrantShift(theta - lw - a)
        return (m0 + h / -360) * 24
    }

    fun correctedHourAngle(
        m0: Double, h0: Double, c: Coordinates, afterTransit: Boolean, theta0: Double,
        a2: Double, a1: Double, a3: Double, d2: Double, d1: Double, d3: Double,
    ): Double {
        val lw = -c.longitude
        val term1 = sin(MathUtil.rad(h0)) - sin(MathUtil.rad(c.latitude)) * sin(MathUtil.rad(d2))
        val term2 = cos(MathUtil.rad(c.latitude)) * cos(MathUtil.rad(d2))
        val bigH0 = MathUtil.deg(acos(term1 / term2))
        val m = if (afterTransit) m0 + bigH0 / 360 else m0 - bigH0 / 360
        val theta = MathUtil.unwind(theta0 + 360.985647 * m)
        val a = MathUtil.unwind(interpolateAngles(a2, a1, a3, m))
        val delta = interpolate(d2, d1, d3, m)
        val h = theta - lw - a
        val alt = altitudeOfCelestialBody(c.latitude, delta, h)
        val dm = (alt - h0) / (360 * cos(MathUtil.rad(delta)) * cos(MathUtil.rad(c.latitude)) * sin(MathUtil.rad(h)))
        return (m + dm) * 24
    }

    fun interpolate(y2: Double, y1: Double, y3: Double, n: Double): Double {
        val a = y2 - y1
        val b = y3 - y2
        return y2 + n / 2 * (a + b + n * (b - a))
    }

    fun interpolateAngles(y2: Double, y1: Double, y3: Double, n: Double): Double {
        val a = MathUtil.unwind(y2 - y1)
        val b = MathUtil.unwind(y3 - y2)
        return y2 + n / 2 * (a + b + n * (b - a))
    }

    fun julianDay(year: Int, month: Int, day: Int, hours: Double = 0.0): Double {
        val y = if (month > 2) year else year - 1
        val m = if (month > 2) month else month + 12
        val d = day + hours / 24
        val a = y / 100
        val b = 2 - a + a / 4
        val i0 = (365.25 * (y + 4716)).toInt()
        val i1 = (30.6001 * (m + 1)).toInt()
        return i0 + i1 + d + b - 1524.5
    }

    fun julianCentury(jd: Double) = (jd - 2451545.0) / 36525

    private fun isLeap(y: Int) = (y % 4 == 0) && !(y % 100 == 0 && y % 400 != 0)

    fun daysSinceSolstice(dayOfYear: Int, year: Int, latitude: Double): Int {
        val daysInYear = if (isLeap(year)) 366 else 365
        return if (latitude >= 0) {
            var d = dayOfYear + 10
            if (d >= daysInYear) d -= daysInYear
            d
        } else {
            var d = dayOfYear - (if (isLeap(year)) 173 else 172)
            if (d < 0) d += daysInYear
            d
        }
    }

    private fun seasonal(dyy: Int, a: Double, b: Double, c: Double, d: Double): Double = when {
        dyy < 91 -> a + (b - a) / 91.0 * dyy
        dyy < 137 -> b + (c - b) / 46.0 * (dyy - 91)
        dyy < 183 -> c + (d - c) / 46.0 * (dyy - 137)
        dyy < 229 -> d + (c - d) / 46.0 * (dyy - 183)
        dyy < 275 -> c + (b - c) / 46.0 * (dyy - 229)
        else -> b + (a - b) / 91.0 * (dyy - 275)
    }

    fun seasonAdjustedMorningTwilight(lat: Double, doy: Int, year: Int, sunrise: Double): Double {
        val l = abs(lat)
        val adj = seasonal(
            daysSinceSolstice(doy, year, lat),
            75 + 28.65 / 55.0 * l, 75 + 19.44 / 55.0 * l, 75 + 32.74 / 55.0 * l, 75 + 48.1 / 55.0 * l,
        )
        return sunrise - round(adj * 60.0)
    }

    /** General shafaq (the adhan default). */
    fun seasonAdjustedEveningTwilight(lat: Double, doy: Int, year: Int, sunset: Double): Double {
        val l = abs(lat)
        val adj = seasonal(
            daysSinceSolstice(doy, year, lat),
            75 + 25.6 / 55.0 * l, 75 + 2.05 / 55.0 * l, 75 - 9.21 / 55.0 * l, 75 + 6.14 / 55.0 * l,
        )
        return sunset + round(adj * 60.0)
    }
}

internal class SolarCoordinates(jd: Double) {
    val declination: Double
    val rightAscension: Double
    val apparentSiderealTime: Double

    init {
        val t = Astro.julianCentury(jd)
        val l0 = Astro.meanSolarLongitude(t)
        val lp = Astro.meanLunarLongitude(t)
        val omega = Astro.ascendingLunarNodeLongitude(t)
        val lambda = MathUtil.rad(Astro.apparentSolarLongitude(t, l0))
        val theta0 = Astro.meanSiderealTime(t)
        val dPsi = Astro.nutationInLongitude(l0, lp, omega)
        val dEpsilon = Astro.nutationInObliquity(l0, lp, omega)
        val e0 = Astro.meanObliquityOfTheEcliptic(t)
        val eApp = MathUtil.rad(Astro.apparentObliquityOfTheEcliptic(t, e0))
        declination = MathUtil.deg(asin(sin(eApp) * sin(lambda)))
        rightAscension = MathUtil.unwind(MathUtil.deg(atan2(cos(eApp) * sin(lambda), cos(lambda))))
        apparentSiderealTime = theta0 + dPsi * 3600 * cos(MathUtil.rad(e0 + dEpsilon)) / 3600
    }
}

internal class SolarTime(date: LocalDate, private val observer: Coordinates) {
    private val solar: SolarCoordinates
    private val prev: SolarCoordinates
    private val next: SolarCoordinates
    private val approxTransit: Double
    val transit: Double
    val sunrise: Double
    val sunset: Double

    init {
        val jd = Astro.julianDay(date.year, date.monthValue, date.dayOfMonth)
        solar = SolarCoordinates(jd)
        prev = SolarCoordinates(jd - 1)
        next = SolarCoordinates(jd + 1)
        approxTransit = Astro.approximateTransit(observer.longitude, solar.apparentSiderealTime, solar.rightAscension)
        transit = Astro.correctedTransit(
            approxTransit, observer.longitude, solar.apparentSiderealTime,
            solar.rightAscension, prev.rightAscension, next.rightAscension,
        )
        val solarAltitude = -50.0 / 60.0
        sunrise = hourAngle(solarAltitude, false)
        sunset = hourAngle(solarAltitude, true)
    }

    fun hourAngle(angle: Double, afterTransit: Boolean) = Astro.correctedHourAngle(
        approxTransit, angle, observer, afterTransit, solar.apparentSiderealTime,
        solar.rightAscension, prev.rightAscension, next.rightAscension,
        solar.declination, prev.declination, next.declination,
    )

    fun afternoon(shadowLength: Double): Double {
        val tangent = abs(observer.latitude - solar.declination)
        val inverse = shadowLength + tan(MathUtil.rad(tangent))
        val angle = MathUtil.deg(atan(1.0 / inverse))
        return hourAngle(angle, true)
    }
}

/**
 * The six times for one civil day at one place. All values are epoch seconds (UTC).
 * A value is null if it cannot be computed (polar day/night).
 */
data class PrayerTimes(
    val date: LocalDate,
    val fajr: Long?,
    val sunrise: Long?,
    val dhuhr: Long?,
    val asr: Long?,
    val maghrib: Long?,
    val isha: Long?,
) {
    fun timeOf(p: Prayer): Long? = when (p) {
        Prayer.Fajr -> fajr
        Prayer.Sunrise -> sunrise
        Prayer.Dhuhr -> dhuhr
        Prayer.Asr -> asr
        Prayer.Maghrib -> maghrib
        Prayer.Isha -> isha
    }

    fun asList(): List<Pair<Prayer, Long?>> = Prayer.entries.map { it to timeOf(it) }

    companion object {
        /** Seconds since 00:00 UTC of [date], expressed as decimal hours -> epoch seconds. */
        private fun utcSeconds(date: LocalDate, hours: Double): Double? {
            if (hours.isNaN() || hours.isInfinite()) return null
            // mirror adhan's TimeComponents truncation (whole seconds)
            val h = floor(hours)
            val m = floor((hours - h) * 60)
            val s = floor((hours - (h + m / 60)) * 3600)
            val midnight = date.atStartOfDay(ZoneId.of("UTC")).toEpochSecond()
            return midnight + h * 3600 + m * 60 + s
        }

        private fun rounded(t: Double?, adjMinutes: Int, up: Boolean): Long? {
            if (t == null || t.isNaN()) return null
            val v = t + adjMinutes * 60
            val sec = Math.floorMod(v.toLong(), 60L)
            val offset = if (up) 60 - sec else if (sec >= 30) 60 - sec else -sec
            return v.toLong() + offset
        }

        fun calculate(coordinates: Coordinates, date: LocalDate, params: CalculationParameters): PrayerTimes {
            val method = params.method
            val solar = SolarTime(date, coordinates)
            val tomorrow = date.plusDays(1)
            val tomorrowSolar = SolarTime(tomorrow, coordinates)

            val dhuhr = utcSeconds(date, solar.transit)
            val sunrise = utcSeconds(date, solar.sunrise)
            val sunset = utcSeconds(date, solar.sunset)
            val asr = utcSeconds(date, solar.afternoon(params.madhab.shadowLength))
            val tomorrowSunrise = utcSeconds(tomorrow, tomorrowSolar.sunrise)

            val night = if (sunset != null && tomorrowSunrise != null) tomorrowSunrise - sunset else null
            val (fajrPortion, ishaPortion) = params.nightPortions()
            val isMsc = method == CalculationMethod.MoonsightingCommittee

            var fajr = utcSeconds(date, solar.hourAngle(-method.fajrAngle, false))
            if (isMsc && coordinates.latitude >= 55 && sunrise != null && night != null) {
                fajr = sunrise - night / 7
            }
            val safeFajr: Double? = when {
                sunrise == null -> null
                isMsc -> Astro.seasonAdjustedMorningTwilight(coordinates.latitude, date.dayOfYear, date.year, sunrise)
                night != null -> sunrise - fajrPortion * night
                else -> null
            }
            if (safeFajr != null && (fajr == null || safeFajr > fajr)) fajr = safeFajr

            var isha: Double?
            if (method.ishaInterval > 0) {
                isha = sunset?.plus(method.ishaInterval * 60.0)
            } else {
                isha = utcSeconds(date, solar.hourAngle(-method.ishaAngle, true))
                if (isMsc && coordinates.latitude >= 55 && sunset != null && night != null) {
                    isha = sunset + night / 7
                }
                val safeIsha: Double? = when {
                    sunset == null -> null
                    isMsc -> Astro.seasonAdjustedEveningTwilight(coordinates.latitude, date.dayOfYear, date.year, sunset)
                    night != null -> sunset + ishaPortion * night
                    else -> null
                }
                if (safeIsha != null && (isha == null || safeIsha < isha)) isha = safeIsha
            }

            var maghrib = sunset
            if (method.maghribAngle > 0 && sunset != null && isha != null) {
                val angleBased = utcSeconds(date, solar.hourAngle(-method.maghribAngle, true))
                if (angleBased != null && sunset < angleBased && isha > angleBased) maghrib = angleBased
            }

            val adj = params.adjustments + method.methodAdjustments
            val up = method.roundUp
            return PrayerTimes(
                date = date,
                fajr = rounded(fajr, adj.fajr, up),
                sunrise = rounded(sunrise, adj.sunrise, up),
                dhuhr = rounded(dhuhr, adj.dhuhr, up),
                asr = rounded(asr, adj.asr, up),
                maghrib = rounded(maghrib, adj.maghrib, up),
                isha = rounded(isha, adj.isha, up),
            )
        }
    }
}

/** Upcoming prayer events from [from] onward, across day boundaries, in the given zone. */
fun upcomingPrayers(
    coordinates: Coordinates,
    params: CalculationParameters,
    zone: ZoneId,
    from: Instant,
    days: Int,
): List<Pair<Prayer, Long>> {
    val startDate = from.atZone(zone).toLocalDate().minusDays(1)
    val out = ArrayList<Pair<Prayer, Long>>()
    for (i in 0..days) {
        val day = localDayTimes(coordinates, params, zone, startDate.plusDays(i.toLong()))
        for ((p, t) in day.asList()) if (t != null && t > from.epochSecond) out += p to t
    }
    return out.sortedBy { it.second }
}

/**
 * Prayer times for a local calendar day. adhan computes times for the UTC date that
 * matches the local date; that is correct for any timezone within about ±12h of
 * the location's solar time, which covers real-world use.
 */
fun localDayTimes(coordinates: Coordinates, params: CalculationParameters, zone: ZoneId, localDate: LocalDate): PrayerTimes =
    PrayerTimes.calculate(coordinates, localDate, params)

object Qibla {
    val MAKKAH = Coordinates(21.4225241, 39.8261818)

    /** Initial great-circle bearing to the Kaaba, degrees clockwise from true north. */
    fun bearing(c: Coordinates): Double {
        val t1 = sin(MathUtil.rad(MAKKAH.longitude) - MathUtil.rad(c.longitude))
        val t2 = cos(MathUtil.rad(c.latitude)) * tan(MathUtil.rad(MAKKAH.latitude))
        val t3 = sin(MathUtil.rad(c.latitude)) * cos(MathUtil.rad(MAKKAH.longitude) - MathUtil.rad(c.longitude))
        return MathUtil.unwind(MathUtil.deg(atan2(t1, t2 - t3)))
    }

    /** Great-circle distance to the Kaaba in kilometres. */
    fun distanceKm(c: Coordinates): Double {
        val p1 = MathUtil.rad(c.latitude)
        val p2 = MathUtil.rad(MAKKAH.latitude)
        val dp = p2 - p1
        val dl = MathUtil.rad(MAKKAH.longitude - c.longitude)
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 6371.0 * 2 * atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}

/** Where the sun is right now, so the Qibla can be found without a compass. */
object SunPosition {
    data class Position(val azimuth: Double, val altitude: Double)

    fun at(instant: Instant, c: Coordinates): Position {
        val jd = instant.epochSecond / 86400.0 + 2440587.5
        val sc = SolarCoordinates(jd)
        // Greenwich apparent sidereal time for this instant
        val t = Astro.julianCentury(jd)
        val gmst = MathUtil.unwind(
            280.46061837 + 360.98564736629 * (jd - 2451545.0) + 0.000387933 * t * t - t * t * t / 38710000,
        )
        val lst = gmst + c.longitude
        val h = MathUtil.rad(MathUtil.unwind(lst - sc.rightAscension))
        val phi = MathUtil.rad(c.latitude)
        val dec = MathUtil.rad(sc.declination)
        val alt = asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h))
        val az = atan2(sin(h), cos(h) * sin(phi) - tan(dec) * cos(phi)) // from south, westward
        return Position(MathUtil.unwind(MathUtil.deg(az) + 180.0), MathUtil.deg(alt))
    }
}

/** Compass-point words for a bearing, e.g. 258 -> "west-southwest". */
fun compassWords(bearing: Double): String {
    val names = listOf(
        "north", "north-northeast", "northeast", "east-northeast",
        "east", "east-southeast", "southeast", "south-southeast",
        "south", "south-southwest", "southwest", "west-southwest",
        "west", "west-northwest", "northwest", "north-northwest",
    )
    return names[((MathUtil.unwind(bearing) + 11.25) / 22.5).toInt() % 16]
}
