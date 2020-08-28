package com.odbol.wear.airquality.purpleair

import android.content.Context
import android.location.Location
import android.util.Log
import io.reactivex.Emitter
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.SingleEmitter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import kotlin.math.ceil
import kotlin.math.floor


const val TAG = "PurpleAir"

interface PurpleAirService {
    @GET("json")
    fun allSensors(): Call<SensorResult>?

    @GET("json?show={id}")
    fun sensor(@Path("id") sensorId: Int): Call<SensorResult>?
}

data class SensorResult(val results: List<Sensor?>)

open class PurpleAir(context: Context) {

    val client = CachingClient(context)

    private val retrofit = Retrofit.Builder()
            .baseUrl("https://www.purpleair.com/")
            .client(client.createClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    private val service: PurpleAirService = retrofit.create(PurpleAirService::class.java)

    open fun findSensorForLocation(location: Location): Single<Sensor> {
        return findSensorsForLocation(location)
                .take(1)
                .singleOrError()
    }

    open fun findSensorsForLocation(location: Location): Observable<Sensor> {
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
    }

    open fun getAllSensors(): Observable<Sensor> {
        return Observable.create { emitter: Emitter<Sensor> ->
            service.allSensors()!!.enqueue(object : Callback<SensorResult?> {

                override fun onResponse(call: Call<SensorResult?>, response: Response<SensorResult?>) {
                    if (response.isSuccessful && response.body() != null) {
                        response.body()!!.results.forEach { d ->
                            Log.v(TAG, "Got sensor $d : ${d?.PM2_5Value} : ${d?.Stats}")
                            if (d != null && d.Stats != null && d.PM2_5Value != null && d.Lat != null && d.Lon != null) {
                                emitter.onNext(d)
                            }
                        }
                        emitter.onComplete()
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

