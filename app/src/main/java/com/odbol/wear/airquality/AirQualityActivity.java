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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Vibrator;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.ambient.AmbientModeSupport;
import android.support.wearable.complications.ProviderUpdateRequester;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationRequest;
import com.odbol.wear.airquality.complication.AirQualityComplicationProviderService;
import com.odbol.wear.airquality.purpleair.PurpleAir;
import com.odbol.wear.airquality.purpleair.Sensor;
import com.patloew.rxlocation.RxLocation;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import static com.odbol.wear.airquality.SensorDetailsActivityKt.EXTRA_SENSOR_ID;
import static com.odbol.wear.airquality.purpleair.PurpleAirServiceKt.sortByClosest;

public class AirQualityActivity extends FragmentActivity implements AmbientModeSupport.AmbientCallbackProvider {

    private static final String TAG = "AirQualityActivity";

    private static final long PROGRESS_UPDATE_INTERVAL_SECONDS = 3;
    private static final long PROGRESS_UPDATE_TOTAL_SECONDS = TimeUnit.MINUTES.toSeconds(5);
    private static final float PROGRESS_DOWNLOAD_TOTAL_PERCENTAGE = 0.5f;

    int MAX_SENSORS_IN_LIST = 100;

    private final CompositeDisposable subscriptions = new CompositeDisposable();
    private final CompositeDisposable loadingSubscription = new CompositeDisposable();

    private View loadingView;
    private TextView textView;
    private TextView hintView;
    private RecyclerView listView;
    private ProgressBar progressBar;

    private RxLocation rxLocation;

    private PurpleAir purpleAir;

    private final SensorsAdapter adapter = new SensorsAdapter(this::onSensorSelected);

    private SensorStore sensorStore;

    /*
     * Declare an ambient mode controller, which will be used by
     * the activity to determine if the current mode is ambient.
     */
    private AmbientModeSupport.AmbientController ambientController;

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.where_am_i_activity);

        ambientController = AmbientModeSupport.attach(this);

        purpleAir = new PurpleAir(this);
        sensorStore = new SensorStore(this);

        textView = (TextView) findViewById(R.id.text);
        hintView = (TextView) findViewById(R.id.hint);
        progressBar = (ProgressBar) findViewById(R.id.progress);
        loadingView = (View) findViewById(R.id.loading);
        listView = (RecyclerView) findViewById(R.id.list);

        listView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter);

        int selectedSensorId = sensorStore.getSelectedSensorId();

        startLoading();

        rxLocation = new RxLocation(this);
        subscriptions.add(
            checkPermissions()
                    .subscribeOn(Schedulers.io())
                    .flatMap((isGranted) -> rxLocation.location().updates(createLocationRequest()))
                    .debounce(3, TimeUnit.SECONDS)
                    .take(1)
                    .singleOrError()
                    .timeout(60, TimeUnit.SECONDS, Schedulers.computation(), Single.error(new LocationException("Timed out waiting for location")))
                    //.zipWith(new WifiNetworkRequester(this).requestWifi(), (location, isWifiConnected) -> location)
                    //.zipWith(purpleAir.getAllSensors(), this::findSensorsForLocation)
                    .flatMap(location ->
                        purpleAir.getAllSensors(location)
                                .map(sensors -> findSensorsForLocation(location, sensors))
                    )
                    .subscribeOn(Schedulers.computation())
                    .observeOn(Schedulers.computation())
                    .map(sensors -> sensors.size() > MAX_SENSORS_IN_LIST ? sensors.subList(0, MAX_SENSORS_IN_LIST) : sensors)
                    .map(sensors -> {
                        for (Sensor sensor : sensors) {
                            if (sensor.getID() == selectedSensorId) {
                                sensor.setSelected(true);
                            }
                        }
                        return sensors;
                    })
                    .map(sensors -> {
                        sensors.sort((a, b) -> {
                            if (a.isSelected()) return -1;
                            if (b.isSelected()) return 1;
                            return 0;
                        });
                        return sensors;
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        // onNext
                        (sensors) -> {
                            loadingView.setVisibility(View.GONE);
                            listView.setVisibility(View.VISIBLE);

                            adapter.setSensors(sensors);

                            doneLoading();
                        },
                        // onError
                        (error) -> {
                            Log.e(TAG, "Error:", error);
                            textView.setText(error instanceof LocationException ? R.string.location_error : R.string.sensor_error);
                            hintView.setVisibility(View.INVISIBLE);
                            progressBar.setVisibility(View.INVISIBLE);
                            doneLoading();
                        }
                    )
        );
    }

    private List<Sensor> findSensorsForLocation(Location location, List<Sensor> sensors) {
        progressBar.post(() -> {
            //Log.d(TAG, "Done loading sensors " + progressBar.getProgress());
            if (getProgressPercentage() < 0.8) {
                progressBar.setProgress((int) (0.8 * (float)progressBar.getMax()), true);
            }
        });
        sensors.sort(sortByClosest(location));
        return sensors;
    }

    private void onSensorSelected(Sensor sensor) {
        sensorStore.setSelectedSensorId(sensor.getID());
        forceComplicationUpdate();

        setResult(RESULT_OK, new Intent().putExtra(EXTRA_SENSOR_ID, sensor.getID()));

        Toast.makeText(this, R.string.complication_set, Toast.LENGTH_SHORT).show();

        progressBar.postDelayed(this::finish, 1000);
    }

    @Override
    public void onDestroy() {
        subscriptions.dispose();
        stopLoadingAnimation();
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


    private static LocationRequest createLocationRequest() {
        return LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_LOW_POWER)
                .setInterval(TimeUnit.SECONDS.toMillis(10));
    }











    @Override
    public AmbientModeSupport.AmbientCallback getAmbientCallback() {
        return new AmbientModeSupport.AmbientCallback() {
            @Override
            public void onEnterAmbient(Bundle ambientDetails) {
                super.onEnterAmbient(ambientDetails);
                stopLoadingAnimation();
            }

            @Override
            public void onUpdateAmbient() {
                super.onUpdateAmbient();
                incrementProgressBar();
            }

            @Override
            public void onExitAmbient() {
                super.onExitAmbient();
                resumeLoadingAnimation();
            }
        };
    }

    private void doneLoading() {
        //loadingView.setKeepScreenOn(false);

        stopLoadingAnimation();

        getSystemService(Vibrator.class).vibrate(300);
    }

    private void startLoading() {
        //loadingView.setKeepScreenOn(true);
        progressBar.setProgress(0);

        resumeLoadingAnimation();
    }

    private void resumeLoadingAnimation() {
        stopLoadingAnimation();

        loadingSubscription.add(Observable.interval(PROGRESS_UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS, Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(time -> incrementProgressBar())
        );
    }

    private void stopLoadingAnimation() {
        loadingSubscription.clear();
    }

    private void incrementProgressBar() {
        double progress = purpleAir.getAllSensorsDownloader().getProgress();
        Log.d(TAG, "Download progress: " + progress);
        if (progress > 0 && progress < 1) {
            progressBar.setProgress((int) Math.max((float)progressBar.getMax() * 0.1, (progress * (float)progressBar.getMax() * PROGRESS_DOWNLOAD_TOTAL_PERCENTAGE)));
        } else {
            progressBar.incrementProgressBy((int) Math.round(getProgressRate() * (float) progressBar.getMax() * ((float) PROGRESS_UPDATE_INTERVAL_SECONDS / (float) PROGRESS_UPDATE_TOTAL_SECONDS)));
        }
    }

    private float getProgressRate() {
        if (getProgressPercentage() > PROGRESS_DOWNLOAD_TOTAL_PERCENTAGE + 0.2) {
            return 0.1f;
        }
        if (getProgressPercentage() > 0.9) {
            return 0.01f;
        }
        if (getProgressPercentage() < 0.2) {
            return 5f;
        }
        return 1f;
    }

    private float getProgressPercentage() {
        return (float)progressBar.getProgress() / (float)progressBar.getMax();
    }
}
