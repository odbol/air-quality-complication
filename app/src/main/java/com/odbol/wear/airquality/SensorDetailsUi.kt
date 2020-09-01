package com.odbol.wear.airquality

import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import com.odbol.wear.airquality.purpleair.Sensor

class SensorDetailsUi(private val parent: ViewGroup) {

    private val title = parent.findViewById<TextView>(R.id.title)
    private val aqi = parent.findViewById<TextView>(R.id.aqi)
    private val time = parent.findViewById<TextView>(R.id.time)
    private val progress = parent.findViewById<ProgressBar>(R.id.progress)
    private val settingsButton = parent.findViewById<ImageButton>(R.id.settingsButton)
    private val aqiProgressBar = parent.findViewById<CircularProgressBar>(R.id.aqiProgressBar)

    fun bind(sensor: Sensor) {
        val aqiData = AqiUtils.convertPm25ToAqi(sensor.pm25)

        title.text = sensor.Label
        aqi.text = aqiData.aqi.toString()
        time.text = AqiUtils.getTimeAgo(sensor.lastModified)

        val color = AqiUtils.getColor(parent.context, aqiData)
        aqi.setTextColor(color)

        aqiProgressBar.progressBarColor = color
        aqiProgressBar.progress = AqiUtils.getPercentage(aqiData) * 100
    }

    fun onError(it: Throwable?) {
        title.text = parent.context.getString(R.string.sensor_error)
        aqi.text = "--"
        isLoading = false
    }

    var isLoading: Boolean
        set(value) { progress.visibility = if (value) View.VISIBLE else View.INVISIBLE }
        get() = progress.visibility == View.VISIBLE
}