package com.odbol.wear.airquality.purpleair

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.util.Log
import com.google.gson.GsonBuilder
import com.odbol.wear.airquality.R
import io.reactivex.Single
import io.reactivex.SingleEmitter
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.SingleSubject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import java.io.FileReader
import kotlin.math.ceil
import kotlin.math.floor


const val TAG = "PurpleAir"

const val PURPLE_AIR_BASE_URL = "https://www.purpleair.com/"

fun sortByClosest(location: Location): Comparator<Sensor> {
    return Comparator<Sensor> { a, b ->
        // Multiply so small differences are counted after rounding.
        val dist = (calculateDistanceTo(location, a) - calculateDistanceTo(location, b)) * 10000
        if (dist > 0) {
            ceil(dist).toInt()
        } else {
            floor(dist).toInt()
        }
    }
}

private val tempLocation = Location("PurpleAir")
private fun calculateDistanceTo(location: Location, sensor: Sensor?): Float {
    return try {
        tempLocation.latitude = sensor!!.Lat!!
        tempLocation.longitude = sensor!!.Lon!!
        tempLocation.time = System.currentTimeMillis()
        location.distanceTo(tempLocation)
    } catch (e: NullPointerException) {
        Log.e(TAG, "Got invalid sensor coordinates for $sensor")
        Float.MAX_VALUE
    }
}

interface PurpleAirService {
    @GET("json")
    fun allSensors(): Call<SensorResult>?

    @GET("json")
    fun sensor(@Query("show") sensorId: Int): Call<SensorResult>?
}

data class SensorResult(val results: List<Sensor?>)

open class PurpleAir(context: Context) {

    val client = CachingClient(context)

    private val retrofit = Retrofit.Builder()
            .baseUrl(PURPLE_AIR_BASE_URL)
            .client(client.createClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    private val service: PurpleAirService = retrofit.create(PurpleAirService::class.java)

    val allSensorsDownloader = AllSensorsDownloader(context)

    open fun getAllSensors(): Single<List<Sensor>> {
          // Using retrofit is much too slow.
//        return Single.create { emitter: SingleEmitter<List<Sensor?>> ->
////            service.allSensors()!!.enqueue(object : Callback<SensorResult?> {
////
////                override fun onResponse(call: Call<SensorResult?>, response: Response<SensorResult?>) {
////                    if (response.isSuccessful && response.body() != null) {
////                        emitter.onSuccess(response.body()!!.results)
////                    } else {
////                        emitter.onError(Exception("Error ${response.code()}: ${response.message()}. ${response.errorBody()?.string()}"))
////                    }
////                }
////
////                override fun onFailure(call: Call<SensorResult?>, t: Throwable) {
////                    emitter.onError(t)
////                }
////            })
//
//
//
//        }
        return allSensorsDownloader.getAllSensors()
            .observeOn(Schedulers.computation())
            .flatMap { results ->
                Single.create { emitter: SingleEmitter<List<Sensor>> ->
                    val valids = ArrayList<Sensor>(results.size)
                    var invalidCount = 0
                    results.forEach { d ->
                        //Log.v(TAG, "Got sensor $d : ${d?.PM2_5Value} : ${d?.Stats}")
                        if (d != null && d.Stats != null && d.PM2_5Value != null && d.Lat != null && d.Lon != null) {
                            valids.add(d)
                        } else {
                            invalidCount++
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "Got invalid sensor $d")
                            }
                        }
                    }
                    Log.d(TAG, "Found ${valids.size} valid sensors and $invalidCount invalid ones")
                    emitter.onSuccess(valids)
                }
                .subscribeOn(Schedulers.computation())
            }
    }


    fun loadSensor(sensorId: Int): Single<Sensor> {
        return Single.create { emitter: SingleEmitter<Sensor> ->
            service.sensor(sensorId)!!.enqueue(object : Callback<SensorResult?> {

                override fun onResponse(call: Call<SensorResult?>, response: Response<SensorResult?>) {
                    if (response.isSuccessful && response.body() != null) {
                        response.body()!!.results.forEach { d ->
                            Log.v(TAG, "Got sensor $d : ${d?.PM2_5Value} : ${d?.Stats}")
                            if (d != null && d.Stats != null && d.PM2_5Value != null && d.Lat != null && d.Lon != null) {
                                emitter.onSuccess(d)
                                return@forEach
                            }
                        }
                    } else {
                        emitter.onError(Exception("Error ${response.code()}: ${response.message()}. ${response.errorBody()?.string()}"))
                    }
                }

                override fun onFailure(call: Call<SensorResult?>, t: Throwable) {
                    emitter.onError(t)
                }
            })
        }
    }
}


class DownloadReceiver(private val downloadId: Long): BroadcastReceiver() {

    val onDownloaded : SingleSubject<Boolean> = SingleSubject.create();

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == downloadId) {
            onDownloaded.onSuccess(true)
        }
    }
}

class AllSensorsDownloader(private val context: Context) {
    val dm = DownloadManagerRx(context)

    val file = File(context.getExternalFilesDir(null), "all_sensors.json")

    fun getAllSensors() : Single<List<Sensor?>> {
        // If it's already going, don't start it again.
        var progress = dm.getProgress()
        if (progress >= 1) {
            if (file.exists()) {
                return loadFile()
            } else {
                // it was downloaded at once point, but somehow it got deleted. re-download!
                dm.clearDownloadId()
                progress = -1.0
            }
        }

        if (progress < 0) {
            Log.d(TAG, "Starting download")
            dm.startDownload(PURPLE_AIR_BASE_URL + "json", file, context.getString(R.string.app_name))
        }

        return Single.using({
            DownloadReceiver(dm.downloadId)
        }, { receiver ->
            Log.d(TAG, "Registering download receiver")

            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

            receiver.onDownloaded
                    .flatMap{loadFile()}
        },
        { receiver ->
            Log.d(TAG, "Unregister download receiver")
            context.unregisterReceiver(receiver)
        })
    }

    private fun loadFile(): Single<List<Sensor?>> {
        Log.d(TAG, "loadFile()")
        return Single.create { emitter: SingleEmitter<List<Sensor?>> ->
                emitter.onSuccess(FileReader(file).use {
                    GsonBuilder().create().fromJson(it, SensorResult::class.java) }.results)
            }
            .subscribeOn(Schedulers.io())
    }

    fun getProgress() = dm.getProgress()

}

