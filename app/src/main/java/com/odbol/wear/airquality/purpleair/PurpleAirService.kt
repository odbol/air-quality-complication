package com.odbol.wear.airquality.purpleair

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.util.Log
import com.google.gson.*
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
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.File
import java.io.FileReader
import java.lang.reflect.Type
import kotlin.math.ceil
import kotlin.math.floor


const val TAG = "PurpleAir"

const val PURPLE_AIR_BASE_URL = "https://api.purpleair.com/v1/"


fun sortByClosest(location: Location): Comparator<Sensor> {
    val calculator = DistanceCalculator(location)
    return Comparator<Sensor> { a, b ->
        // Multiply so small differences are counted after rounding.
        val dist = (calculator.calculateDistanceTo(a) - calculator.calculateDistanceTo(b)) * 10000
        if (dist > 0) {
            ceil(dist).toInt()
        } else {
            floor(dist).toInt()
        }
    }
}

private class DistanceCalculator(private val location: Location) {
    private val tempLocation = Location("PurpleAir")

    private val distanceCache: MutableMap<Sensor, Float> = mutableMapOf()

    fun calculateDistanceTo(sensor: Sensor?): Float {
        try {
            val dist = distanceCache[sensor]
            if (dist != null) return dist

            tempLocation.latitude = sensor!!.latitude!!
            tempLocation.longitude = sensor!!.longitude!!
            tempLocation.time = System.currentTimeMillis()
            val distanceTo = location.distanceTo(tempLocation)

            distanceCache[sensor] = distanceTo

            return distanceTo
        } catch (e: NullPointerException) {
            Log.e(TAG, "Got invalid sensor coordinates for $sensor")
            return Float.MAX_VALUE
        }
    }
}

interface PurpleAirService {
    @GET("sensors")
    fun allSensors(@Query("fields") fields: String,
                   @Query("nwlng") northwestLon: Double,
                   @Query("nwlat") northwestLat: Double,
                   @Query("selng") southeastLon: Double,
                   @Query("selat") southeastLat: Double,
    ): Call<SensorsResult>?

    @GET("sensors/{sensorId}")
    fun sensor(@Path("sensorId") sensor_index: Int, @Query("fields") fields: String): Call<SingleSensorResult>?
}

data class SingleSensorResult(val sensor: Sensor)
data class SensorsResult(val sensors: List<Sensor>)

/* example:
200 success
{
"api_version" : "V1.0.9-0.0.11",
"time_stamp" : 1629848809,
"data_time_stamp" : 1629848789,
"max_age" : 604800,
"firmware_default_version" : "6.01",
"fields" : [
"sensor_index",
"name",
"location_type",
"latitude",
"longitude",
"position_rating"
],
"location_types" : ["outside", "inside"],
"data" : [
[20,"Oakdale",0,40.6031,-111.8361,5],
[47,"OZONE TEST",0,40.4762,-111.8826,5],
[53,"Lakeshore",0,40.2467,-111.7048,5],
[74,"Wasatch Commons",0,40.7383,-111.9362,5],
[77,"Sunnyside",0,40.7508,-111.8253,5],
[81,"Sherwood Hills 2",0,40.2876,-111.6424,5],
[179,"Ross Way, Gabriola Island, British Columbia",0,49.1745,-123.8478,5],
[182,"Jolly Brothers Road, Gabriola Island BC P1",0,49.1601,-123.7423,0]
...
 */
class SensorResultDeserializer: JsonDeserializer<SensorsResult> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): SensorsResult? {
        if (json == null) return null

        try {
            val obj = json.asJsonObject
            val fields = obj.getAsJsonArray("fields").map { it.asString }
            val location_types = obj.getAsJsonArray("location_types").map { it.asString }
            val data = obj.getAsJsonArray("data").map { it.asJsonArray }

            // Yes, we could use reflection here, but we want speed.
            var sensor_indexIndex: Int = -1
            var nameIndex: Int = -1
            var location_typeIndex: Int = -1
            var latitudeIndex: Int = -1
            var longitudeIndex: Int = -1
            var pm25Index: Int = -1
            fields.forEachIndexed { index: Int, fieldName: String ->
                when (fieldName) {
                    "sensor_index" -> sensor_indexIndex = index
                    "name" -> nameIndex = index
                    "location_type" -> location_typeIndex = index
                    "latitude" -> latitudeIndex = index
                    "longitude" -> longitudeIndex = index
                    "pm2.5_10minute" -> pm25Index = index
                }
            }

            return SensorsResult(data.mapNotNull {
                try {
                    Sensor(
                        ID = it[sensor_indexIndex].asInt,
                        name = it[nameIndex].asString,
                        location_type = location_types[it[location_typeIndex].asInt],
                        latitude = it[latitudeIndex].asDouble,
                        longitude = it[longitudeIndex].asDouble,
                        stats = null,
                        lastSeenSeconds = null,
                        pm25Override = it[pm25Index].asDouble
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse sensor $it", e)
                    null
                }
            })
        } catch (e: Exception) {
            throw JsonParseException(e)
        }
    }
}


open class PurpleAir(context: Context) {
    val client = CachingClient(context)

    private val gson = GsonBuilder()
        .registerTypeAdapter(SensorsResult::class.java, SensorResultDeserializer())
        .create()

    private val retrofit = Retrofit.Builder()
            .baseUrl(PURPLE_AIR_BASE_URL)
            .client(client.createClient())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    private val service: PurpleAirService = retrofit.create(PurpleAirService::class.java)

    val allSensorsDownloader = AllSensorsDownloader(context)

    open fun getAllSensors(location: Location): Single<List<Sensor>> {
        return Single.create { emitter: SingleEmitter<List<Sensor?>> ->
            Log.d(TAG, "getAllSensors $location");

            service.allSensors(
                fields = REQUIRED_FIELDS,
                northwestLat = location.latitude + 1,
                northwestLon = location.longitude - 1,
                southeastLat = location.latitude - 1,
                southeastLon = location.longitude + 1,
                )!!.enqueue(object : Callback<SensorsResult?> {
                override fun onResponse(call: Call<SensorsResult?>, response: Response<SensorsResult?>) {
                    Log.d(TAG, "getAllSensors ${call.request().url()}");
                    if (response.isSuccessful && response.body() != null) {
                        emitter.onSuccess(response.body()!!.sensors)
                    } else {
                        Log.e(TAG, "getAllSensors error $response")
                        emitter.onError(Exception("Error ${response.code()}: ${response.message()}. ${response.errorBody()?.string()}"))
                    }
                }

                override fun onFailure(call: Call<SensorsResult?>, t: Throwable) {
                    Log.e(TAG, "getAllSensors onFailure", t)
                    emitter.onError(t)
                }
            })
        }
        // Don't need the allSensorsDownloader, with the new API. Leaving it here so you know the pain I went through.
//        return allSensorsDownloader.getAllSensors()
            .observeOn(Schedulers.computation())
            .flatMap { results ->
                Single.create { emitter: SingleEmitter<List<Sensor>> ->
                    val valids = ArrayList<Sensor>(results.size)
                    var invalidCount = 0
                    results.forEach { d ->
                        //Log.v(TAG, "Got sensor $d : ${d?.PM2_5Value} : ${d?.Stats}")
                        if (d != null && d.name != null && d.latitude != null && d.longitude != null) {
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
            service.sensor(sensorId, REQUIRED_FIELDS)!!.enqueue(object : Callback<SingleSensorResult?> {

                override fun onResponse(call: Call<SingleSensorResult?>, response: Response<SingleSensorResult?>) {
                    if (response.isSuccessful && response.body() != null) {
                        val d = response.body()!!.sensor
                        Log.v(TAG, "Got sensor $d : ${d?.stats} : ${d?.name}")
                        if (d != null &&  d.stats != null && d.latitude != null && d.longitude != null) {
                            emitter.onSuccess(d)
                        } else {
                            emitter.onError(Exception("Error: failed parsing sensor"))
                        }
                    } else {
                        emitter.onError(Exception("Error ${response.code()}: ${response.message()}. ${response.errorBody()?.string()}"))
                    }
                }

                override fun onFailure(call: Call<SingleSensorResult?>, t: Throwable) {
                    Log.e(TAG, "loadSensor onFailure ${call.request().url()}", t)
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
                    GsonBuilder().create().fromJson(it, SensorsResult::class.java) }.sensors)
            }
            .subscribeOn(Schedulers.io())
    }

    fun getProgress() = dm.getProgress()

}

