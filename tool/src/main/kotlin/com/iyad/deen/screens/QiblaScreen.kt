package com.iyad.deen.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.iyad.deen.core.Coordinates
import com.iyad.deen.core.Qibla
import com.iyad.deen.core.SunPosition
import com.iyad.deen.core.compassWords
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.device.LightCompass
import com.thelightphone.sdk.device.LightCompassReading
import com.iyad.deen.ui.CenteredMessage
import com.iyad.deen.ui.DeenScreen
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class QiblaViewModel(ctx: SealedLightContext) : PlaceViewModel(ctx)

/** Signed smallest angle from a to b, in (-180, 180]. */
private fun delta(a: Double, b: Double): Double {
    var d = (b - a) % 360.0
    if (d > 180) d -= 360
    if (d <= -180) d += 360
    return d
}

class QiblaScreen(private val sealedActivity: SealedLightActivity) :
    LightScreen<Unit, QiblaViewModel>(sealedActivity) {
    override val viewModelClass = QiblaViewModel::class.java
    override fun createViewModel() = QiblaViewModel(lightContext)

    private val compass by lazy { LightCompass(sealedActivity) }

    @Composable
    override fun Content() {
        val s by viewModel.settings.collectAsState()
        DeenScreen("Qibla", onBack = { goBack() }) {
            val settings = s ?: return@DeenScreen
            val place = settings.place
            if (place == null) {
                CenteredMessage("Set your location first.", "Find my location") { viewModel.locate() }
                return@DeenScreen
            }
            val coords = Coordinates(place.lat, place.lon)
            val qibla = Qibla.bearing(coords)
            if (compass.isAvailable) LiveCompass(coords, qibla) else SunGuide(coords, qibla)
        }
    }

    @Composable
    private fun LiveCompass(coords: Coordinates, qibla: Double) {
        var reading by remember { mutableStateOf<LightCompassReading?>(null) }
        var aligned by remember { mutableStateOf(false) }

        LaunchedEffect(coords) {
            compass.headings(coords.latitude, coords.longitude).collect { reading = it }
        }

        val heading = reading?.trueHeading?.toDouble()
        val diff = heading?.let { delta(it, qibla) }
        val nowAligned = diff != null && abs(diff) <= 3.0
        LaunchedEffect(nowAligned) {
            if (nowAligned && !aligned) sealedActivity.vibrator.click()
            aligned = nowAligned
        }

        val colors = LightThemeTokens.colors
        val measurer = rememberTextMeasurer()

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 2f.gridUnitsAsDp()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LightText(
                when {
                    heading == null -> "Hold the phone flat…"
                    nowAligned -> "Facing the Qibla"
                    diff!! > 0 -> "Turn right ${diff.roundToInt()}°"
                    else -> "Turn left ${(-diff!!).roundToInt()}°"
                },
                LightTextVariant.Subheading,
                align = TextAlign.Center,
            )

            Box(
                Modifier.fillMaxWidth(0.86f).aspectRatio(1f).padding(vertical = 1f.gridUnitsAsDp()),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    val c = Offset(size.width / 2, size.height / 2)
                    val r = size.minDimension / 2 * 0.92f
                    val fg = colors.content
                    val dim = colors.contentSecondary
                    val dialRotation = -(heading ?: 0.0).toFloat()

                    // fixed marker at the top: the direction the phone points
                    val tip = Path().apply {
                        moveTo(c.x, c.y - r - r * 0.02f)
                        lineTo(c.x - r * 0.05f, c.y - r - r * 0.11f)
                        lineTo(c.x + r * 0.05f, c.y - r - r * 0.11f)
                        close()
                    }
                    drawPath(tip, fg)

                    rotate(dialRotation, c) {
                        drawCircle(dim, r, c, style = Stroke(width = r * 0.008f))
                        for (deg in 0 until 360 step 10) {
                            val a = Math.toRadians(deg.toDouble())
                            val major = deg % 90 == 0
                            val len = if (major) r * 0.1f else r * 0.05f
                            val o = Offset(c.x + (r * sin(a)).toFloat(), c.y - (r * cos(a)).toFloat())
                            val i = Offset(c.x + ((r - len) * sin(a)).toFloat(), c.y - ((r - len) * cos(a)).toFloat())
                            drawLine(if (major) fg else dim, o, i, strokeWidth = if (major) r * 0.014f else r * 0.008f)
                        }
                        // cardinal letters
                        listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0).forEach { (t, deg) ->
                            val a = Math.toRadians(deg)
                            val layout = measurer.measure(t, TextStyle(color = if (t == "N") fg else dim, fontSize = (r / 9).toSp()))
                            val p = Offset(c.x + (r * 0.76f * sin(a)).toFloat(), c.y - (r * 0.76f * cos(a)).toFloat())
                            rotate(-dialRotation, p) {
                                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
                            }
                        }
                        // Kaaba: a small square on the rim at the qibla bearing, with a line to the centre
                        val qa = Math.toRadians(qibla)
                        val kp = Offset(c.x + (r * 0.86f * sin(qa)).toFloat(), c.y - (r * 0.86f * cos(qa)).toFloat())
                        drawLine(fg, c, kp, strokeWidth = r * 0.03f)
                        val k = r * 0.11f
                        rotate(qibla.toFloat(), kp) {
                            drawRect(fg, Offset(kp.x - k / 2, kp.y - k / 2), Size(k, k))
                            drawRect(colors.background, Offset(kp.x - k / 2, kp.y - k * 0.22f), Size(k, k * 0.1f))
                        }
                    }
                    drawCircle(fg, r * 0.035f, c)
                    if (nowAligned) drawCircle(fg, r * 1.0f, c, style = Stroke(width = r * 0.03f))
                }
            }

            LightText(
                "Qibla ${qibla.roundToInt()}° · ${compassWords(qibla)}",
                LightTextVariant.Copy,
                align = TextAlign.Center,
            )
            LightText(
                "Distance to Makkah ${"%,d".format(Qibla.distanceKm(coords).roundToInt())} km",
                LightTextVariant.Detail,
                lighten = true,
                align = TextAlign.Center,
            )
            if (reading?.needsCalibration == true) {
                LightText(
                    "Compass needs calibrating: move the phone in a figure-8, away from metal and magnets.",
                    LightTextVariant.Detail,
                    align = TextAlign.Center,
                    modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                )
            }
        }
    }

    /** Phones without a magnetometer: find the Qibla from the sun, or from a known landmark. */
    @Composable
    private fun SunGuide(coords: Coordinates, qibla: Double) {
        val now by viewModel.now.collectAsState()
        val sun = SunPosition.at(Instant.ofEpochSecond(now.epochSecond), coords)
        LightScrollView(Modifier.fillMaxWidth().padding(horizontal = 2f.gridUnitsAsDp())) {
            LightText("${qibla.roundToInt()}°", LightTextVariant.Title)
            LightText("from true north · ${compassWords(qibla)}", LightTextVariant.Copy, lighten = true)
            LightText(
                "This phone has no compass sensor, so here is how to find the Qibla by eye.",
                LightTextVariant.Detail,
                lighten = true,
                modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
            )
            if (sun.altitude > 2) {
                val d = delta(sun.azimuth, qibla)
                LightText("Using the sun", LightTextVariant.Subheading)
                LightText(
                    when {
                        abs(d) < 5 -> "Face the sun. The Qibla is straight ahead."
                        abs(d) > 175 -> "Stand with the sun directly behind you. The Qibla is straight ahead."
                        d > 0 -> "Face the sun, then turn ${d.roundToInt()}° to your right."
                        else -> "Face the sun, then turn ${(-d).roundToInt()}° to your left."
                    },
                    LightTextVariant.Copy,
                )
                LightText(
                    "90° is a quarter turn. The sun is at ${sun.azimuth.roundToInt()}° right now; this updates as it moves.",
                    LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                )
            } else {
                LightText("The sun is down", LightTextVariant.Subheading)
                LightText(
                    "Find north (the Pole Star in the northern hemisphere), then turn ${qibla.roundToInt()}° clockwise. " +
                        "Come back during daylight for sun directions.",
                    LightTextVariant.Copy,
                )
            }
        }
    }
}

