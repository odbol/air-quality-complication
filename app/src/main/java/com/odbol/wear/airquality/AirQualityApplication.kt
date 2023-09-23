package com.odbol.wear.airquality

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import io.reactivex.plugins.RxJavaPlugins

class AirQualityApplication: Application() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        RxJavaPlugins.setErrorHandler { e: Throwable? ->
            Log.e(TAG, "UNHANDLED RxJava EXCEPTION", e)
            handler.post {
                Toast.makeText(
                    this,
                    R.string.general_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        private const val TAG = "AirQualityApp"
    }
}