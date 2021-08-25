package com.odbol.wear.airquality.purpleair

import android.content.Context
import com.odbol.wear.airquality.R
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Caches calls to the giant list of sensors, but uses fresh data for individual sensor retrieval.
 */
class CachingClient(private val context: Context) {
    private val readKey = context.getString(R.string.purpleair_api_key_read)

    fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.MINUTES)
                .readTimeout(2, TimeUnit.MINUTES)
                .writeTimeout(2, TimeUnit.MINUTES)
                .cache(Cache(context.cacheDir, 10 * 1024 * 1024))
                .addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder().header("X-API-Key", readKey).build())
                }
                // Add an Interceptor to the OkHttpClient.
                .addInterceptor { chain ->

                    // Get the request from the chain.
                    var request = chain.request()

                    val pathSegments = request.url().encodedPathSegments()
                    var isIndividualSensorRequest = !(pathSegments.size > 1 && pathSegments[pathSegments.size - 1] == "sensors")


                    /*
                *  Leveraging the advantage of using Kotlin,
                *  we initialize the request and change its header depending on whether
                *  the device is connected to Internet or not.
                */
                    request = if (isIndividualSensorRequest)
                    /*
                *  If there is Internet, get the cache that was stored 5 seconds ago.
                *  If the cache is older than 60 seconds, then discard it,
                *  and indicate an error in fetching the response.
                *  The 'max-age' attribute is responsible for this behavior.
                */
                        request.newBuilder().header("Cache-Control", "public, max-age=" + 60).build()
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