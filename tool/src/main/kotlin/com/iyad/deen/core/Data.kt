package com.iyad.deen.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos

/** Bundled, offline content. Everything is parsed once and cached for the process. */

data class Surah(
    val number: Int,
    val arabicName: String,
    val transliteration: String,
    val english: String,
    val meccan: Boolean,
    val verses: List<String>,
)

data class Dhikr(
    val arabic: String,
    val english: String,
    val reference: String,
    val count: Int,
)

data class City(val name: String, val countryCode: String, val lat: Double, val lon: Double)

/** First verse of each juz, as (surah, ayah). Standard Hafs division. */
val JUZ_STARTS: List<Pair<Int, Int>> = listOf(
    1 to 1, 2 to 142, 2 to 253, 3 to 93, 4 to 24, 4 to 148, 5 to 82, 6 to 111, 7 to 88, 8 to 41,
    9 to 93, 11 to 6, 12 to 53, 15 to 1, 17 to 1, 18 to 75, 21 to 1, 23 to 1, 25 to 21, 27 to 56,
    29 to 46, 33 to 31, 36 to 28, 39 to 32, 41 to 47, 46 to 1, 51 to 31, 58 to 1, 67 to 1, 78 to 1,
)

fun juzOf(surah: Int, ayah: Int): Int {
    var j = 1
    for ((i, start) in JUZ_STARTS.withIndex()) {
        if (surah > start.first || (surah == start.first && ayah >= start.second)) j = i + 1
    }
    return j
}

object Content {
    @Volatile private var quran: List<Surah>? = null
    @Volatile private var azkar: Map<String, List<Dhikr>>? = null
    @Volatile private var cities: List<City>? = null
    @Volatile private var countries: Map<String, String>? = null

    fun quran(readAsset: (String) -> ByteArray): List<Surah> = quran ?: synchronized(this) {
        quran ?: run {
            val arr = JSONArray(String(readAsset("quran.json"), Charsets.UTF_8))
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val v = o.getJSONArray("v")
                Surah(
                    number = o.getInt("n"),
                    arabicName = o.getString("ar"),
                    transliteration = o.getString("tr"),
                    english = o.getString("en"),
                    meccan = o.getString("t") == "m",
                    verses = List(v.length()) { v.getString(it) },
                )
            }.also { quran = it }
        }
    }

    fun azkar(readAsset: (String) -> ByteArray): Map<String, List<Dhikr>> = azkar ?: synchronized(this) {
        azkar ?: run {
            val o = JSONObject(String(readAsset("azkar.json"), Charsets.UTF_8))
            o.keys().asSequence().associateWith { key ->
                val a = o.getJSONArray(key)
                List(a.length()) {
                    val d = a.getJSONObject(it)
                    Dhikr(d.getString("ar"), d.getString("en"), d.getString("ref"), d.getInt("count"))
                }
            }.also { azkar = it }
        }
    }

    private fun cities(readAsset: (String) -> ByteArray): List<City> = cities ?: synchronized(this) {
        cities ?: String(readAsset("cities.txt"), Charsets.UTF_8).lineSequence().mapNotNull { line ->
            val p = line.split('|')
            if (p.size == 4) City(p[0], p[1], p[2].toDouble(), p[3].toDouble()) else null
        }.toList().also { cities = it }
    }

    fun countryName(code: String, readAsset: (String) -> ByteArray): String {
        val map = countries ?: synchronized(this) {
            countries ?: JSONObject(String(readAsset("countries.json"), Charsets.UTF_8)).let { o ->
                o.keys().asSequence().associateWith { o.getString(it) }
            }.also { countries = it }
        }
        return map[code] ?: code
    }

    /** The bundled place closest to the coordinates (equirectangular distance is plenty here). */
    fun nearestCity(lat: Double, lon: Double, readAsset: (String) -> ByteArray): City? {
        val k = cos(Math.toRadians(lat))
        return cities(readAsset).minByOrNull { c ->
            val dLat = c.lat - lat
            var dLon = c.lon - lon
            if (dLon > 180) dLon -= 360
            if (dLon < -180) dLon += 360
            dLat * dLat + (dLon * k) * (dLon * k)
        }
    }
}

/** A sensible default method for the country the user is in. */
fun defaultMethodFor(countryCode: String?): CalculationMethod = when (countryCode) {
    "QA" -> CalculationMethod.Qatar
    "SA", "YE" -> CalculationMethod.UmmAlQura
    "AE", "OM" -> CalculationMethod.Dubai
    "KW" -> CalculationMethod.Kuwait
    "EG", "SY", "LB", "IQ", "LY", "SD", "JO", "PS" -> CalculationMethod.Egyptian
    "PK", "IN", "BD", "AF" -> CalculationMethod.Karachi
    "US", "CA" -> CalculationMethod.NorthAmerica
    "GB", "IE" -> CalculationMethod.MoonsightingCommittee
    "SG", "MY", "ID", "BN" -> CalculationMethod.Singapore
    "TR" -> CalculationMethod.Turkey
    "IR" -> CalculationMethod.Tehran
    else -> CalculationMethod.MuslimWorldLeague
}

private val ARABIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

fun Int.toArabicDigits(): String = toString().map { ARABIC_DIGITS[it - '0'] }.joinToString("")
