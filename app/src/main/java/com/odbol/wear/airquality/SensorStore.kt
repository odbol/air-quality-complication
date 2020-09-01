package com.odbol.wear.airquality

import android.content.Context
import com.odbol.wear.airquality.purpleair.Sensor
import io.reactivex.Observable
import io.reactivex.Single

const val PREFS_KEY_SELECTED_SENSOR = "PREFS_KEY_SELECTED_SENSOR"
const val PREFS_KEY_SAVED_SENSOR = "PREFS_KEY_SAVED_SENSOR_"

class SensorStore(context: Context) {

    val prefs = context.getSharedPreferences("SENSORS_CACHE", Context.MODE_PRIVATE)

    var selectedSensorId: Int
    get() = prefs.getInt(PREFS_KEY_SELECTED_SENSOR, -1)
    set(value) = prefs.edit().putInt(PREFS_KEY_SELECTED_SENSOR, value).apply()

//    fun loadCachedData(): Single<Sensor> {
//        val json = prefs.getString(PREFS_KEY_SAVED_SENSOR + selectedSensorId, null)
//
//        return Single.just(Sensor.deserialize(json))
//    }
}