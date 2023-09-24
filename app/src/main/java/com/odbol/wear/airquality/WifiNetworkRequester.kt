package com.odbol.wear.airquality

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.SingleSource
import io.reactivex.functions.Consumer
import io.reactivex.functions.Function
import io.reactivex.subjects.AsyncSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.SingleSubject
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

const val TAG = "WifiNetworkRequester"

class WifiNetworkRequester(context: Context) {
//
//    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//
//    inner class NetworkCallbackSubject : ConnectivityManager.NetworkCallback() {
//
//        val onConnected : SingleSubject<Boolean> = SingleSubject.create();
//
//        override fun onAvailable(network: Network) {
//            if (connectivityManager.bindProcessToNetwork(network)) {
//                // socket connections will now use this network
//                Log.d(TAG, "Wifi is available")
//                onConnected.onSuccess(true)
//            } else {
//                // app doesn't have android.permission.INTERNET permission
//                Log.e(TAG, "Couldn't get wifi")
//                onConnected.onSuccess(false)
//            }
//        }
//    }
//
//    fun requestWifi(): Single<Boolean> {
//        val isWifi: Boolean = connectivityManager.activeNetwork?.let { activeNetwork ->
//            connectivityManager.getNetworkCapabilities(activeNetwork).hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
//        } ?: false
//
//        if (isWifi) {
//            // You already are on a high-bandwidth network, so start your network request
//            Log.d(TAG, "Already connected to wifi")
//            return Single.just(true)
//        }
//
//        return Single.using({
//            NetworkCallbackSubject()
//        }, { networkCallback ->
//
//            val request: NetworkRequest = NetworkRequest.Builder().run {
//                addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
//                addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
//                addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
//                addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
//                build()
//            }
//
//            Log.d(TAG, "Requesting Wifi")
//
//            connectivityManager.requestNetwork(request, networkCallback)
//
//            networkCallback.onConnected
//        },
//        { networkCallback ->
//            Log.d(TAG, "Disconnecting Wifi")
//            connectivityManager.bindProcessToNetwork(null)
//            connectivityManager.unregisterNetworkCallback(networkCallback)
//        })
//
//        .timeout(10, TimeUnit.SECONDS)
//        .onErrorReturn { t ->
//            Log.w(TAG, "Timed out waiting for Wifi ", t)
//            false
//        }
//
//    }
//
}