package com.odbol.wear.airquality.purpleair

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit


//open class PurpleAirCache(context: Context): PurpleAir() {
//
//    val prefs = context.getSharedPreferences("SENSORS_CACHE", Context.MODE_PRIVATE)
//
//    override fun getAllSensors(): Observable<Sensor> {
//        return super.getAllSensors()
//                .toList()
//                .flattenAsObservable { sensors ->
//                    sensors
//                }
//    }
//}

class CachingClient(private val context: Context) {
    fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.MINUTES)
                .readTimeout(2, TimeUnit.MINUTES)
                .writeTimeout(2, TimeUnit.MINUTES)
                .cache(Cache(context.cacheDir, 10 * 1024 * 1024))
                // Add an Interceptor to the OkHttpClient.
                .addInterceptor { chain ->

                    // Get the request from the chain.
                    var request = chain.request()

                    var isIndividualSensorRequest = request.url().queryParameterValues("show").isNotEmpty()


                    /*
                *  Leveraging the advantage of using Kotlin,
                *  we initialize the request and change its header depending on whether
                *  the device is connected to Internet or not.
                */
                    request = if (isIndividualSensorRequest)
                    /*
                *  If there is Internet, get the cache that was stored 5 seconds ago.
                *  If the cache is older than 5 seconds, then discard it,
                *  and indicate an error in fetching the response.
                *  The 'max-age' attribute is responsible for this behavior.
                */
                        request.newBuilder().header("Cache-Control", "public, max-age=" + 5).build()
                    else
                    /*
                *  If there is no Internet, get the cache that was stored 7 days ago.
                *  If the cache is older than 7 days, then discard it,
                *  and indicate an error in fetching the response.
                *  The 'max-stale' attribute is responsible for this behavior.
                *  The 'only-if-cached' attribute indicates to not retrieve new data; fetch the cache only instead.
                */
                        request.newBuilder().header("Cache-Control", "public, max-stale=" + 60 * 60 * 24 * 60).build()
                    // End of if-else statement

                    // Add the modified request to the chain.
                    chain.proceed(request)
                }
                .build()
    }
}