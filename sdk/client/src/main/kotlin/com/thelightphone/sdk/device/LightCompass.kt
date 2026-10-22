package com.thelightphone.sdk.device

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.thelightphone.sdk.SealedLightActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/*
 * LOCAL SDK EXTENSION (light-deen fork).
 *
 * Light SDK 0.2.1 has no API for the orientation sensors, and tools may not call
 * getSystemService() themselves. This wrapper keeps the Android Context inside the
 * SDK, the same way LightVibrator and LightAudio do, and hands the tool only
 * headings. If Light adds a compass API, switch to it and delete this file.
 */

/** One compass reading. [trueHeading] is degrees clockwise from true north. */
data class LightCompassReading(
    val trueHeading: Float,
    val magneticHeading: Float,
    /** [SensorManager.SENSOR_STATUS_ACCURACY_HIGH] .. [SensorManager.SENSOR_STATUS_UNRELIABLE] */
    val accuracy: Int,
) {
    val needsCalibration: Boolean
        get() = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
}

class LightCompass(sealedActivity: SealedLightActivity) {
    private val context: Context = sealedActivity.activity.applicationContext
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotation: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensors.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val accel: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnet: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    /** False when the phone has no magnetometer; use a sun- or bearing-based fallback. */
    val isAvailable: Boolean = rotation != null || (accel != null && magnet != null)

    /**
     * Headings while collected. [latitude]/[longitude] correct magnetic north to
     * true north using the World Magnetic Model.
     */
    fun headings(latitude: Double, longitude: Double): Flow<LightCompassReading> = callbackFlow {
        val declination = GeomagneticField(
            latitude.toFloat(), longitude.toFloat(), 0f, System.currentTimeMillis(),
        ).declination

        val rot = FloatArray(9)
        val orient = FloatArray(3)
        var gravity: FloatArray? = null
        var geomag: FloatArray? = null
        var magAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
        // low-pass on the unit vector so the needle doesn't jitter or wrap at 0/360
        var sx = Double.NaN
        var sy = Double.NaN

        fun emit(azimuthRad: Float) {
            val a = azimuthRad.toDouble()
            if (sx.isNaN()) { sx = cos(a); sy = sin(a) } else {
                sx += 0.15 * (cos(a) - sx); sy += 0.15 * (sin(a) - sy)
            }
            val magnetic = ((Math.toDegrees(atan2(sy, sx)) + 360) % 360).toFloat()
            val trueH = (magnetic + declination + 360f) % 360f
            trySend(LightCompassReading(trueH, magnetic, magAccuracy))
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rot, e.values)
                        SensorManager.getOrientation(rot, orient)
                        emit(orient[0])
                    }
                    Sensor.TYPE_ACCELEROMETER -> gravity = e.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        geomag = e.values.clone()
                        val g = gravity ?: return
                        if (SensorManager.getRotationMatrix(rot, null, g, geomag)) {
                            SensorManager.getOrientation(rot, orient)
                            emit(orient[0])
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD ||
                    sensor.type == Sensor.TYPE_ROTATION_VECTOR ||
                    sensor.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
                ) magAccuracy = accuracy
            }
        }

        val rate = SensorManager.SENSOR_DELAY_UI
        if (rotation != null) {
            sensors.registerListener(listener, rotation, rate)
            // rotation-vector accuracy is unreliable on some devices; watch the raw magnetometer too
            magnet?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        } else if (accel != null && magnet != null) {
            sensors.registerListener(listener, accel, rate)
            sensors.registerListener(listener, magnet, rate)
        } else {
            close()
        }
        awaitClose { sensors.unregisterListener(listener) }
    }
}
