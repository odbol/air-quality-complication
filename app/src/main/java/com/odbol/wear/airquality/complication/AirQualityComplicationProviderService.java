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

package com.odbol.wear.airquality.complication;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.location.Address;
import android.location.Location;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationManager;
import android.support.wearable.complications.ComplicationProviderService;
import android.support.wearable.complications.ComplicationText;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.location.LocationRequest;
import com.odbol.wear.airquality.AqiUtils;
import com.odbol.wear.airquality.R;
import com.odbol.wear.airquality.AirQualityActivity;
import com.odbol.wear.airquality.purpleair.PurpleAir;
import com.odbol.wear.airquality.purpleair.Sensor;
import com.patloew.rxlocation.FusedLocation;
import com.patloew.rxlocation.RxLocation;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class AirQualityComplicationProviderService extends ComplicationProviderService {

    private static final String TAG = "AirQualityComplication";

    private RxLocation rxLocation;
    private final CompositeDisposable subscriptions = new CompositeDisposable();

    private final PurpleAir purpleAir = new PurpleAir();

    @Override
    public void onCreate() {
        super.onCreate();

        rxLocation = new RxLocation(this);
    }

    @Override
    public void onDestroy() {
        subscriptions.dispose();
        super.onDestroy();
    }

    @Override
    public void onComplicationUpdate(int complicationId, int dataType, ComplicationManager manager) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onComplicationUpdate() id: " + complicationId);
        }

        final Observable<Sensor> task;
        if (checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            FusedLocation locationProvider = rxLocation.location();
            task = locationProvider
                    .isLocationAvailable()
                    .subscribeOn(Schedulers.io())
                    .flatMapObservable((hasLocation) -> hasLocation ?
                            locationProvider.lastLocation().toObservable() :
                            locationProvider.updates(createLocationRequest()))
                    .flatMapSingle(purpleAir::findSensorForLocation)
                    .map(AqiUtils::throwIfInvalid);
        } else {
            task = Observable.error(new SecurityException("No location permission!"));
        }

        subscriptions.add(
            task
                .subscribe(
                        // onNext
                        (sensor -> updateComplication(complicationId, dataType, manager, sensor)),
                        // onError
                        (error) -> {
                            Log.e(TAG, "Error retreiving location", error);
                            updateComplication(complicationId, dataType, manager, null);
                        }
                )
        );
    }

    private void updateComplication(int complicationId, int dataType, ComplicationManager manager, Sensor sensor) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "sensor: " + sensor);
        }

        ComplicationData complicationData = null;
        switch (dataType) {
            case ComplicationData.TYPE_SHORT_TEXT:
                complicationData =
                        new ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                                .setShortText(getAqi(sensor))
                                .setContentDescription(getFullDescription(sensor))
                                .setIcon(Icon.createWithResource(this, R.drawable.ic_my_location))
                                .setTapAction(getTapAction())
                                .build();
                break;
            case ComplicationData.TYPE_LONG_TEXT:
                complicationData =
                        new ComplicationData.Builder(ComplicationData.TYPE_LONG_TEXT)
                                .setLongTitle(getTimeAgo(sensor.getStatistics().getLastModified()).build())
                                .setLongText(getAqi(sensor))
                                .setContentDescription(getFullDescription(sensor))
                                .setIcon(Icon.createWithResource(this, R.drawable.ic_my_location))
                                .setTapAction(getTapAction())
                                .build();
                break;
            default:
                if (Log.isLoggable(TAG, Log.WARN)) {
                    Log.w(TAG, "Unexpected complication type " + dataType);
                }
        }

        if (complicationData != null) {
            manager.updateComplicationData(complicationId, complicationData);
        } else {
            // If no data is sent, we still need to inform the ComplicationManager, so
            // the update job can finish and the wake lock isn't held any longer.
            manager.noUpdateRequired(complicationId);
        }
    }

    private ComplicationText getAqi(Sensor sensor) {
        return ComplicationText.plainText(String.valueOf(sensor.getStatistics().getAvg10Min()));
    }

    private PendingIntent getTapAction() {
        Intent intent = new Intent(this, AirQualityActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private ComplicationText getFullDescription(Sensor sensor) {
        if (sensor == null) return ComplicationText.plainText(getString(R.string.no_location));

        return getTimeAgo(sensor.getStatistics().getLastModified())
                .setSurroundingText(getString(R.string.aqi_as_of_time_ago, sensor.getStatistics().getAvg10Min(), "^1"))
                .build();
    }

    public static ComplicationText getAddressDescriptionText(Context context, Address address) {
        return ComplicationText.plainText(getAddressDescription(context, address));
    }

    public static String getAddressDescription(Context context, Address address) {
        if (address == null) return context.getString(R.string.no_location);
        String subThoroughfare = address.getSubThoroughfare();
        String thoroughfare = address.getThoroughfare();
        if (thoroughfare == null) return address.toString();
        return (TextUtils.isEmpty(subThoroughfare) ? "" : subThoroughfare +  " ") + thoroughfare;
    }


    private ComplicationText getTimeAgo(Location location) {
        if (location == null) return ComplicationText.plainText("--");
        return getTimeAgo(location.getTime()).build();
    }

    private ComplicationText.TimeDifferenceBuilder getTimeAgo(long fromTime) {
        return new ComplicationText.TimeDifferenceBuilder()
                .setStyle(ComplicationText.DIFFERENCE_STYLE_SHORT_WORDS_SINGLE_UNIT)
                .setMinimumUnit(TimeUnit.MINUTES)
                .setReferencePeriodEnd(fromTime)
                .setShowNowText(true);
    }

    public static LocationRequest createLocationRequest() {
        return LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setNumUpdates(1)
                .setExpirationDuration(TimeUnit.SECONDS.toMillis(30));
    }
}
