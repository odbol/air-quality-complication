package com.odbol.wear.airquality.purpleair

import android.location.Location
import android.util.Log
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.Single
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

const val TAG = "PurpleAir"

interface PurpleAirService {
    @GET("json")
    fun allSensors(): Call<List<Sensor?>?>?

    @GET("json?show={id}")
    fun sensor(@Path("id") sensorId: Int): Call<Sensor?>?
}

class PurpleAir {
    private val retrofit = Retrofit.Builder()
            .baseUrl("https://www.purpleair.com/")
            .build()

    private val service: PurpleAirService = retrofit.create(PurpleAirService::class.java)

    fun findSensorForLocation(location: Location): Single<Sensor> {
        return getAllSensors()
                .sorted { a, b ->
                    // Multiply so small differences are counted after rounding.
                    val dist = (calculateDistanceTo(location, a) - calculateDistanceTo(location, b)) * 10000
                    return@sorted if (dist > 0) {
                        ceil(dist).toInt()
                    } else {
                        floor(dist).toInt()
                    }
                }
                .take(1)
                .singleOrError()
    }

    fun getAllSensors(): Observable<Sensor> {
        return Observable.create{ emitter: Emitter<Sensor> ->
            service.allSensors()!!.enqueue(object : Callback<List<Sensor?>?> {

                override fun onResponse(call: Call<List<Sensor?>?>, response: Response<List<Sensor?>?>) {
                    if (response.isSuccessful && response.body() != null) {
                        response.body()!!.forEach{ d ->
                            if (d != null) {
                                emitter.onNext(d)
                            }
                        }
                        emitter.onComplete()
                    } else {
                        emitter.onError(Exception("Error ${response.code()}: ${response.errorBody()}"))
                    }
                }

                override fun onFailure(call: Call<List<Sensor?>?>, t: Throwable) {
                    emitter.onError(t)
                }
            })
            }
        }
    }

    private val tempLocation = Location("PurpleAir")
    private fun calculateDistanceTo(location: Location, sensor: Sensor): Float {
        return try {
            tempLocation.latitude = sensor.Lat!!
            tempLocation.longitude = sensor.Lon!!
            tempLocation.time = System.currentTimeMillis()
            location.distanceTo(tempLocation)
        } catch (e: NullPointerException) {
            Log.e(TAG, "Got invalid sensor coordinates for $sensor")
            Float.MAX_VALUE
        }
    }
}