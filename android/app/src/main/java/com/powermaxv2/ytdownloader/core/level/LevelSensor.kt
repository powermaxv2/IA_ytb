package com.powermaxv2.ytdownloader.core.level

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * The phone's tilt sensor for the Niveau à bulle: the gravity sensor when there is one (already filtered by Android
 * with the gyroscope, steadier), else the plain accelerometer, smoothed by [LowPass]. Some phones (cheap tablets,
 * TV boxes, some emulators) have neither: [available] is then false and the screen says so.
 */
class LevelSensor(context: Context) {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = manager?.let { it.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    private val smooth = LowPass()
    private var listener: SensorEventListener? = null

    val available: Boolean get() = sensor != null

    /** "gravité" or "accéléromètre", for the small line under the vial. */
    val kind: String get() = if (sensor?.type == Sensor.TYPE_GRAVITY) "capteur de gravité" else "accéléromètre"

    /** Starts sending [onGravity] (main thread, about 30 times a second); false when there is no sensor. */
    fun start(onGravity: (Gravity) -> Unit): Boolean {
        val s = sensor ?: return false
        val m = manager ?: return false
        stop()
        smooth.reset()
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 3) return
                onGravity(smooth.push(Gravity(event.values[0], event.values[1], event.values[2])))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = l
        return m.registerListener(l, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        listener?.let { manager?.unregisterListener(it) }
        listener = null
    }
}
