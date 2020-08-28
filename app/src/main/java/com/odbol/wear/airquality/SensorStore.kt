package com.odbol.wear.airquality

import android.content.Context

const val PREFS_KEY_SELECTED_SENSOR = "PREFS_KEY_SELECTED_SENSOR"

class SensorStore(context: Context) {

    val prefs = context.getSharedPreferences("SENSORS_CACHE", Context.MODE_PRIVATE)

    var selectedSensorId: Int
    get() = prefs.getInt(PREFS_KEY_SELECTED_SENSOR, -1)
    set(value) = prefs.edit().putInt(PREFS_KEY_SELECTED_SENSOR, value).apply()
}