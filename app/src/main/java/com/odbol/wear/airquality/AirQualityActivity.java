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

package com.odbol.wear.airquality;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.icu.text.RelativeDateTimeFormatter;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.wearable.complications.ProviderUpdateRequester;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Pair;
import android.widget.TextView;

import com.google.android.gms.location.LocationRequest;
import com.odbol.wear.airquality.complication.AirQualityComplicationProviderService;
import com.odbol.wear.airquality.purpleair.PurpleAir;
import com.odbol.wear.airquality.purpleair.Sensor;
import com.patloew.rxlocation.RxLocation;
import com.tbruyelle.rxpermissions2.RxPermissions;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

public class AirQualityActivity extends FragmentActivity {

    private static final String TAG = "AirQualityActivity";

    private final CompositeDisposable subscriptions = new CompositeDisposable();

    private TextView textView;
    private RxLocation rxLocation;

    private final PurpleAir purpleAir = new PurpleAir();

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.where_am_i_activity);

        textView = (TextView) findViewById(R.id.text);

        textView.setKeepScreenOn(true);
        rxLocation = new RxLocation(this);
        subscriptions.add(
            checkPermissions()
                .subscribeOn(Schedulers.io())
                .flatMap((isGranted) -> rxLocation.location().updates(createLocationRequest()))
                .flatMapSingle(purpleAir::findSensorForLocation)
                    .map(AqiUtils::throwIfInvalid)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    // onNext
                    (sensor) -> {
                        textView.setText(getString(
                            R.string.sensor_result,
                                AqiUtils.convertPm25ToAqi(sensor.getStatistics().getAvg10Min()),
                                sensor.getStatistics().getAvg10Min(),
                            DateUtils.getRelativeTimeSpanString(this, sensor.getStatistics().getLastModified(), false)));

                        textView.setKeepScreenOn(false);
                    },
                    // onError
                    (error) -> {
                        Log.e(TAG, "Error:", error);
                        textView.setText(R.string.location_error);
                        textView.setKeepScreenOn(false);
                    }
                )
        );
    }

    @Override
    public void onDestroy() {
        subscriptions.dispose();
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        forceComplicationUpdate();
        super.onStop();
    }

    private void forceComplicationUpdate() {
        if (checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            new ProviderUpdateRequester(this, new ComponentName(this, AirQualityComplicationProviderService.class))
                    .requestUpdateAll();
        }
    }

    private Observable<Boolean> checkPermissions() {
        return new RxPermissions(this)
                .request(Manifest.permission.ACCESS_FINE_LOCATION)
                .map(isGranted -> {
                    if (isGranted) return true;
                    throw new SecurityException("No location permission");
                });
    }

    private CharSequence getTimeAgo(long time) {
        return DateUtils.getRelativeTimeSpanString(time);
    }


    private static LocationRequest createLocationRequest() {
        return LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(TimeUnit.SECONDS.toMillis(10))
                .setSmallestDisplacement(50);
    }
}
