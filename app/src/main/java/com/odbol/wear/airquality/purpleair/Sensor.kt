package com.odbol.wear.airquality.purpleair

import com.google.gson.GsonBuilder
import com.thanglequoc.aqicalculator.AQICalculator
import com.thanglequoc.aqicalculator.Pollutant
import java.lang.NullPointerException


// Sample data:
/*
{
    "ID": 37149,
    "Label": "East Bay German International School",
    "DEVICE_LOCATIONTYPE": "outside",
    "THINGSPEAK_PRIMARY_ID": "843786",
    "THINGSPEAK_PRIMARY_ID_READ_KEY": "FUE5OYNZ5DQ6V4E5",
    "THINGSPEAK_SECONDARY_ID": "843787",
    "THINGSPEAK_SECONDARY_ID_READ_KEY": "UYM8UPN1SXYEC2FT",
    "Lat": 37.832604,
    "Lon": -122.278556,
    "PM2_5Value": "7.32",
    "LastSeen": 1598546748,
    "Type": "PMS5003+PMS5003+BME280",
    "Hidden": "false",
    "isOwner": 0,
    "humidity": "57",
    "temp_f": "65",
    "pressure": "1010.93",
    "AGE": 0,
    "Stats": "{\"v\":7.32,\"v1\":5.91,\"v2\":5.45,\"v3\":5.62,\"v4\":11.73,\"v5\":24.96,\"v6\":20.88,\"pm\":7.32,\"lastModified\":1598546748230,\"timeSinceModified\":118327}"
}
*/

data class Statistics(val v: Double, val v1: Double, val lastModified: Long) {
    val current: Double
        get() = this.v

    val avg10Min: Double
        get() = this.v1
}

data class Sensor(val ID: Int,
                  val Label: String?,
                  val DEVICE_LOCATIONTYPE: String?,
                  val THINGSPEAK_PRIMARY_ID: String?,
                  val THINGSPEAK_PRIMARY_ID_READ_KEY: String?,
                  val THINGSPEAK_SECONDARY_ID: String?,
                  val THINGSPEAK_SECONDARY_ID_READ_KEY: String?,
                  val Lat: Double?,
                  val Lon: Double?,
                  val PM2_5Value: String?,
                  val LastSeen: Long?,
                  val Stats: String?) {
    val statistics: Statistics?
    val lastModified: Long
    val lastSeenMs = if (LastSeen != null) LastSeen * 1000 else 0

    var isSelected = false

    init {
        statistics = parseStats()
        lastModified = statistics?.lastModified ?: lastSeenMs
    }

    private fun parseStats(): Statistics? {
        try {
            return GsonBuilder().create().fromJson(Stats!!, Statistics::class.java)
        } catch (e: NullPointerException) {}
        try {
            return Statistics(PM2_5Value!!.toDouble(), PM2_5Value!!.toDouble(), lastSeenMs)
        } catch (e: NullPointerException) {}

        return null
    }

    val pm25: Double
    get() = statistics?.avg10Min ?: PM2_5Value?.toDouble() ?: 0.0
}