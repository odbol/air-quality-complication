package com.odbol.wear.airquality

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.odbol.wear.airquality.purpleair.PurpleAir
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

const val REQUEST_ID = 3214
const val EXTRA_SENSOR_ID = "EXTRA_SENSOR_ID"

class SensorDetailsActivity: Activity() {

    private lateinit var ui: SensorDetailsUi

    private lateinit var purpleAir: PurpleAir
    private lateinit var sensorStore: SensorStore

    private val subscriptions = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.sensor_details)


        purpleAir = PurpleAir(this)
        sensorStore = SensorStore(this)

        ui = SensorDetailsUi(findViewById(R.id.sensorDetails), this::startSensorPickerActivity)

        if (sensorStore.selectedSensorId < 0) {
            startSensorPickerActivity()
        }
    }

    private fun startSensorPickerActivity() {
        startActivityForResult(Intent(this, AirQualityActivity::class.java), REQUEST_ID)
    }

    override fun onResume() {
        super.onResume()

        if (sensorStore.selectedSensorId >= 0) {
            loadSensor(sensorStore.selectedSensorId)
        }
    }

    private fun loadSensor(sensorId: Int) {
        if (sensorId < 0) return

        subscriptions.clear()

        ui.isLoading = true
        subscriptions.add(
                purpleAir.loadSensorCached(sensorId)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            // onNext
                            {
                                Log.d(TAG, "Got sensor")
                                ui.bind(it)
                            },
                            // onError
                            {
                                Log.e(TAG, "Error retreiving sensor", it)
                                ui.onError(it)
                            },
                            // onComplete
                            {
                                Log.d(TAG, "Completed getting sensors")
                                ui.isLoading = false
                            })
        )
    }

    override fun onDestroy() {
        subscriptions.dispose()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ID) {
            if (resultCode == RESULT_OK) {
                loadSensor(data?.getIntExtra(EXTRA_SENSOR_ID, -1) ?: -1)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}