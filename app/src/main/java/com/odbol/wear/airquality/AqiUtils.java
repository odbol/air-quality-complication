package com.odbol.wear.airquality;

import android.util.Log;

import com.odbol.wear.airquality.purpleair.Sensor;
import com.thanglequoc.aqicalculator.AQICalculator;
import com.thanglequoc.aqicalculator.AQIResult;
import com.thanglequoc.aqicalculator.Pollutant;

import org.jetbrains.annotations.NotNull;

public class AqiUtils {

    private static final String TAG = "AqiUtils";

    public static AQIResult convertPm25ToAqi(double pm25) {
        AQICalculator calculator = AQICalculator.getAQICalculatorInstance();
        AQIResult result = calculator.getAQI(Pollutant.PM25, pm25);
        return result;
    }

    @NotNull
    public static Sensor throwIfInvalid(Sensor sensor) throws Exception {
        Log.v(TAG, "throwIfInvalid sensor: " + sensor);

        if (sensor.getPm25() == 0) {
            throw new Exception("Invalid sensor data");
        }
        return sensor;
    }
}
