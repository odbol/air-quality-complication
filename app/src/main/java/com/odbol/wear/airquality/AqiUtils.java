package com.odbol.wear.airquality;

import android.content.Context;
import android.graphics.Color;
import android.text.format.DateUtils;
import android.util.Log;

import com.odbol.wear.airquality.purpleair.Sensor;
import com.thanglequoc.aqicalculator.AQICalculator;
import com.thanglequoc.aqicalculator.AQIResult;
import com.thanglequoc.aqicalculator.Pollutant;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import androidx.annotation.ColorInt;

public class AqiUtils {

    private static final String TAG = "AqiUtils";

    public static AQIResult convertPm25ToAqi(double pm25) {
        AQICalculator calculator = AQICalculator.getAQICalculatorInstance();
        AQIResult result = calculator.getAQI(Pollutant.PM25, pm25);
        return result;
    }

    public static CharSequence getTimeAgo(Long timeSeconds) {
        if (timeSeconds == null || timeSeconds == 0) return "--";
        return DateUtils.getRelativeTimeSpanString(timeSeconds * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
    }

    @ColorInt
    public static int getColor(Context context, AQIResult aqi) {
        switch(aqi.getColor()) {
            case "Yellow":
                return context.getColor(R.color.Yellow);
            case "Orange":
                return context.getColor(R.color.Orange);
            case "Red":
                return context.getColor(R.color.Red);
            case "Purple":
                return context.getColor(R.color.Purple);
            case "Maroon":
                return context.getColor(R.color.Maroon);
            case "Green":
                return context.getColor(R.color.Green);
            default:
                return context.getColor(R.color.white);
        }
    }

    @NotNull
    public static Sensor throwIfInvalid(Sensor sensor) throws Exception {
        Log.v(TAG, "throwIfInvalid sensor: " + sensor);

        if (sensor.getPm25() == 0) {
            throw new Exception("Invalid sensor data");
        }
        return sensor;
    }

    public static float getPercentage(AQIResult aqiData) {
        return Math.min(1f, (float)aqiData.getAQI() / (float)500);
    }
}