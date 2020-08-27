package com.odbol.wear.airquality;

import com.odbol.wear.airquality.purpleair.Sensor;
import com.thanglequoc.aqicalculator.AQICalculator;
import com.thanglequoc.aqicalculator.AQIResult;
import com.thanglequoc.aqicalculator.Pollutant;

import org.jetbrains.annotations.NotNull;

public class AqiUtils {
    public static AQIResult convertPm25ToAqi(double pm25) {
        AQICalculator calculator = AQICalculator.getAQICalculatorInstance();
        AQIResult result = calculator.getAQI(Pollutant.PM25, pm25);
        return result;
    }

    @NotNull
    public static Sensor throwIfInvalid(Sensor sensor) throws Exception {
        if (sensor.getStatistics() == null) throw new Exception("Invalid sensor data");
        return sensor;
    }
}
