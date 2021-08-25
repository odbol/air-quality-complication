package com.odbol.wear.airquality.purpleair

import com.google.gson.annotations.SerializedName


data class Statistics(
    @SerializedName("pm2.5_10minute")
    var PM2_5_10_Minute: Double?) {

    val avg10Min: Double
        get() = this.PM2_5_10_Minute ?: 0.0
}
// Sample data: https://api.purpleair.com/#api-sensors-get-sensors-data

val REQUIRED_FIELDS = listOf("name", "location_type", "pm2.5_10minute", "latitude", "longitude", "last_seen").joinToString(",")

data class Sensor(
                  @SerializedName("sensor_index")
                  val ID: Int,
                  val name: String?,
                  val location_type: String?,
                  val latitude: Double?,
                  val longitude: Double?,
                  val stats: Statistics?,
                  @SerializedName("last_seen")
                  val lastSeenSeconds: Long?,
                  val pm25Override: Double?) {

    var isSelected = false

    // WARNING: you can't use init here. for some reason gson can get around calling it. WTF Kotlin
    // see https://stackoverflow.com/a/54769068/473201
    init {

    }

    val pm25: Double
        get() = stats?.avg10Min ?: pm25Override ?: 0.0
}