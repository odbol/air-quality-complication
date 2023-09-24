// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.odbol.wear.airquality.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.location.Address
import android.text.TextUtils
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.odbol.wear.airquality.AqiUtils
import com.odbol.wear.airquality.R
import com.odbol.wear.airquality.SensorDetailsActivity
import com.odbol.wear.airquality.SensorStore
import com.odbol.wear.airquality.purpleair.PurpleAir
import com.odbol.wear.airquality.purpleair.Sensor
import com.patloew.rxlocation.RxLocation
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable

class AirQualityComplicationProviderService : ComplicationDataSourceService() {
    private var rxLocation: RxLocation? = null
    private val subscriptions = CompositeDisposable()
    private var purpleAir: PurpleAir? = null
    private var sensorStore: SensorStore? = null
    override fun onCreate() {
        super.onCreate()
        sensorStore = SensorStore(this)
        purpleAir = PurpleAir(this)
        rxLocation = RxLocation(this)
    }

    override fun onDestroy() {
        subscriptions.dispose()
        super.onDestroy()
    }


    override fun onComplicationRequest(
        complicationRequest: ComplicationRequest,
        complicationRequestListener: ComplicationRequestListener
    ) {
        val complicationId = complicationRequest.complicationInstanceId
        val dataType = complicationRequest.complicationType
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onComplicationUpdate() id: $complicationId")
        }
        val task: Single<Sensor>
        val sensorId = sensorStore!!.selectedSensorId
        task = if (sensorId >= 0) {
////            FusedLocation locationProvider = rxLocation.location();
////            task = locationProvider
////                    .isLocationAvailable()
////                    .subscribeOn(Schedulers.io())
////                    .flatMapObservable((hasLocation) -> hasLocation ?
////                            locationProvider.lastLocation().toObservable() :
////                            locationProvider.updates(createLocationRequest()))
//                    .flatMapSingle(purpleAir::getSensor())
//                    .map(AqiUtils::throwIfInvalid);
            purpleAir!!.loadSensor(sensorId)
                .map { sensor: Sensor? -> AqiUtils.throwIfInvalid(sensor) }
        } else {
            Single.error(SecurityException("No sensor selected"))
        }
        subscriptions.add(
            task
                .subscribe( // onNext
                    { sensor: Sensor? ->
                        updateComplication(
                            complicationId,
                            dataType,
                            sensor,
                            complicationRequestListener
                        )
                    }
                )  // onError
                { error: Throwable? ->
                    Log.e(TAG, "Error retreiving location", error)
                    updateComplication(
                        complicationId,
                        dataType,
                        null,
                        complicationRequestListener
                    )
                }
        )
    }

    private fun updateComplication(
        complicationId: Int,
        dataType: ComplicationType,
        sensor: Sensor?,
        complicationRequestListener: ComplicationRequestListener
    ) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "sensor: $sensor")
        }
        val complicationData: ComplicationData? = generateComplicationData(dataType, sensor)
        if (complicationData != null) {
            complicationRequestListener.onComplicationData(complicationData)
        } else {
            // If no data is sent, we still need to inform the ComplicationManager, so
            // the update job can finish and the wake lock isn't held any longer.
            complicationRequestListener.onComplicationData(null)
        }
    }

    private fun generateComplicationData(
        dataType: ComplicationType,
        sensor: Sensor?
    ): ComplicationData? {
        var complicationData: ComplicationData? = null
        when (dataType) {

            ComplicationType.SHORT_TEXT -> complicationData = ShortTextComplicationData.Builder(
                getAqi(sensor),
                getFullDescription(sensor)
            )
                .setTitle(plainText("AQI"))
                .setMonochromaticImage(
                    MonochromaticImage.Builder(
                        Icon.createWithResource(this, R.drawable.ic_air_quality)
                    ).build()
                )
                .setTapAction(tapAction)
                .build()

            ComplicationType.LONG_TEXT -> complicationData = LongTextComplicationData.Builder(
                getAqi(sensor),
                getFullDescription(sensor)
            )
                .setTitle(plainText("AQI"))
                .setMonochromaticImage(
                    MonochromaticImage.Builder(
                        Icon.createWithResource(this, R.drawable.ic_air_quality)
                    ).build()
                )
                .setTapAction(tapAction)
                .build()

    //            ComplicationType.RANGED_VALUE -> complicationData = Builder(RANGED_VALUE)
    //                .setShortTitle(getTimeAgo(sensor))
    //                .setShortText(getAqi(sensor))
    //                .setMinValue(0)
    //                .setMaxValue(500)
    //                .setValue(min(500.0, getAqiValue(sensor).toDouble()))
    //                .setContentDescription(getFullDescription(sensor))
    //                .setIcon(Icon.createWithResource(this, R.drawable.ic_air_quality))
    //                .setTapAction(tapAction)
    //                .build()

            else -> //                if (Log.isLoggable(TAG, Log.WARN)) {
                Log.w(TAG, "Unexpected complication type $dataType")
        }
        return complicationData
    }

    private fun getAqiValue(sensor: Sensor?): Int {
        return if (sensor == null) 0 else AqiUtils.convertPm25ToAqi(sensor.pm25).aqi
    }

    private fun getAqi(sensor: Sensor?): ComplicationText {
        return if (sensor == null) plainText("--") else plainText(getAqiValue(sensor).toString())
    }

    private val tapAction: PendingIntent
        private get() {
            val intent = Intent(this, SensorDetailsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

    private fun getFullDescription(sensor: Sensor?): ComplicationText {
        return if (sensor == null) plainText(getString(R.string.no_location)) else plainText("AQI")
    }
//
//    private fun getTimeAgo(fromTime: Long?): TimeDifferenceBuilder {
//        return TimeDifferenceBuilder()
//            .setStyle(DIFFERENCE_STYLE_SHORT_SINGLE_UNIT)
//            .setMinimumUnit(TimeUnit.MINUTES)
//            .setReferencePeriodEnd(fromTime ?: 0)
//            .setShowNowText(true)
//    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return generateComplicationData(type, null)
    }


    companion object {
        private const val TAG = "AirQualityComplication"
        fun getAddressDescriptionText(context: Context, address: Address?): ComplicationText {
            return plainText(getAddressDescription(context, address))
        }

        fun getAddressDescription(context: Context, address: Address?): String {
            if (address == null) return context.getString(R.string.no_location)
            val subThoroughfare = address.subThoroughfare
            val thoroughfare = address.thoroughfare ?: return address.toString()
            return (if (TextUtils.isEmpty(subThoroughfare)) "" else "$subThoroughfare ") + thoroughfare
        }
    }
}

fun plainText(text: CharSequence) = PlainComplicationText.Builder(text).build()