package at.tomtasche.flightdashboard;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.location.LocationEngineRequest;
import com.mapbox.android.core.location.LocationEngineResult;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.offline.OfflineManager;
import com.mapbox.mapboxsdk.offline.OfflineRegion;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PermissionsListener, LocationEngineCallback<LocationEngineResult> {

    private static final String TAG = "FlightDashboard";

    private static final String MAPBOX_API_KEY = "pk.eyJ1IjoidG9tdGFzY2hlIiwiYSI6ImNqNjN3cWJneTFsaTkyeG8zNTZ3ZDhocGwifQ.NJbB_cCAqT_KDOublhLa2A";
    private static final String MAPBOX_STYLE = Style.OUTDOORS;

    // actual flight altitude is much higher, but devices seem to stop updating altitude at some point
    private static final double MIN_FLIGHT_ALTITUDE = 5000.0;

    private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("hh:mm:ss");

    private LocationManager locationManager;

    private ProgressBar progressBar;
    private Snackbar altitudeSnackbar;
    private Snackbar altitudeLockSnackbar;

    private View bottomSheet;
    private BottomSheetBehavior<View> sheetBehavior;

    private TextView altitudeView;
    private TextView speedView;
    private TextView bearingView;
    private TextView accuracyView;
    private TextView timestampView;

    private Style style;
    private MapView mapView;
    private MapboxMap mapboxMap;
    private PermissionsManager permissionsManager;

    private boolean isMapInitialized = false;

    private boolean isAltitudeAcknowledged = false;
    private boolean isAltitudeLockAcknowledged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Mapbox.getInstance(this, MAPBOX_API_KEY);
        Mapbox.setConnected(false);

        setContentView(R.layout.activity_main);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        bottomSheet = findViewById(R.id.flight_stats_sheet);
        altitudeView = (TextView) findViewById(R.id.flight_stats_altitude_value);
        speedView = (TextView) findViewById(R.id.flight_stats_speed_value);
        bearingView = (TextView) findViewById(R.id.flight_stats_bearing_value);
        accuracyView = (TextView) findViewById(R.id.flight_stats_accuracy_value);
        timestampView = (TextView) findViewById(R.id.flight_stats_timestamp_value);

        sheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setBottomSheetState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        mapView = (MapView) findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
    }

    private void setBottomSheetState(int state) {
        if (sheetBehavior.getState() != state) {
            sheetBehavior.setState(state);
        }
    }

    private boolean checkLocationEnabled() {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();

        if (checkLocationEnabled()) {
            if (!isMapInitialized) {
                initializeMap();
            }
        } else {
            showLocationSnackbar();
        }
    }

    private void showLocationSnackbar() {
        final Snackbar snackbar = Snackbar.make(mapView, R.string.snackbar_location_text, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.snackbar_location_action, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snackbar.dismiss();

                if (!checkLocationEnabled()) {
                    Intent locationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivityForResult(locationIntent, 1234);
                }
            }
        });

        snackbar.show();
    }

    private void initializeMap() {
        isMapInitialized = true;

        copyOfflineMap();

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                MainActivity.this.mapboxMap = mapboxMap;

                mapboxMap.setStyle(new Style.Builder().fromUri(MAPBOX_STYLE),
                        new Style.OnStyleLoaded() {

                            @Override
                            public void onStyleLoaded(@NonNull Style style) {
                                MainActivity.this.style = style;

                                enableLocationComponent();
                            }
                        });

                mapboxMap.getUiSettings().setRotateGesturesEnabled(false);

                mapboxMap.addOnMapClickListener(new MapboxMap.OnMapClickListener() {
                    @Override
                    public boolean onMapClick(@NonNull LatLng point) {
                        setBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED);

                        return true;
                    }
                });
            }
        });
    }

    private void copyOfflineMap() {
        try {
            File file = new File(getFilesDir(), "world_v1.db");
            if (file.exists()) {
                progressBar.setProgress(100);

                return;
            }

            InputStream inputStream = getResources().openRawResource(R.raw.world);
            file.createNewFile();
            copy(inputStream, file);

            OfflineManager offlineManager = OfflineManager.getInstance(this);
            offlineManager.mergeOfflineRegions(file.getPath(), new OfflineManager.MergeOfflineRegionsCallback() {

                @Override
                public void onMerge(OfflineRegion[] offlineRegions) {
                    progressBar.setProgress(100);
                }

                @Override
                public void onError(String error) {
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void copy(InputStream src, File dst) throws IOException {
        try (OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = src.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } finally {
            src.close();
        }
    }

    private void enableLocationComponent() {
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            LocationComponent locationComponent = mapboxMap.getLocationComponent();

            locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this, style).build());

            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setCameraMode(CameraMode.NONE);
            locationComponent.setRenderMode(RenderMode.COMPASS);

            LocationEngine locationEngine = LocationEngineProvider.getBestLocationEngine(this);

            LocationEngineRequest request = new LocationEngineRequest.Builder(5000)
                    .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY).build();

            locationEngine.requestLocationUpdates(request, this, getMainLooper());
            locationEngine.getLastLocation(this);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    private String formatNumber(Number number) {
        return formatNumber(number, "");
    }

    private String formatNumber(Number number, String unit) {
        Double doubleNumber = number.doubleValue();

        return String.format("%.02f " + unit, doubleNumber);
    }

    private String convertBearingToDirection(int bearing) {
        String result = null;
        switch (bearing) {
            case 0:
                result = getString(R.string.direction_not_available);
                break;

            case 90:
                result = getString(R.string.direction_east);
                break;

            case 180:
                result = getString(R.string.direction_south);
                break;

            case 270:
                result = getString(R.string.direction_west);
                break;

            case 360:
                result = getString(R.string.direction_north);
                break;
        }

        if (bearing > 0 && bearing < 90) {
            result = getString(R.string.direction_north_east);
        } else if (bearing > 90 && bearing < 180) {
            result = getString(R.string.direction_south_east);
        } else if (bearing > 180 && bearing < 270) {
            result = getString(R.string.direction_south_west);
        } else if (bearing > 270 && bearing < 360) {
            result = getString(R.string.direction_north_west);
        }

        return result;
    }

    private void showAltitudeWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_altitude_title);
        builder.setMessage(R.string.dialog_altitude_message);

        builder.setNeutralButton(R.string.dialog_altitude_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                isAltitudeAcknowledged = true;

                altitudeSnackbar.dismiss();
                altitudeSnackbar = null;

                dialog.dismiss();

                bottomSheet.setVisibility(View.VISIBLE);
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onBackPressed() {
        if (progressBar.getProgress() < 100) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.dialog_download_title);
            builder.setMessage(R.string.dialog_download_message);

            builder.setNeutralButton(R.string.dialog_download_button, null);

            AlertDialog dialog = builder.create();
            dialog.show();

            return;
        }

        super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
    }

    @Override
    public void onPermissionResult(boolean granted) {
        enableLocationComponent();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onSuccess(LocationEngineResult result) {
        Location location = result.getLastLocation();

        final double altitude = location.getAltitude();
        if (!isAltitudeAcknowledged && altitude <= MIN_FLIGHT_ALTITUDE) {
            if (altitudeSnackbar != null) {
                return;
            }

            altitudeSnackbar = Snackbar.make(mapView, R.string.snackbar_altitude_text, Snackbar.LENGTH_INDEFINITE);
            altitudeSnackbar.setAction(R.string.snackbar_altitude_action, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    altitudeSnackbar.dismiss();

                    showAltitudeWarning();
                }
            });

            bottomSheet.setVisibility(View.GONE);
            altitudeSnackbar.show();
        } else if (!isAltitudeLockAcknowledged && (altitude == Math.floor(altitude)) && !Double.isInfinite(altitude)) {
            // taken from: https://stackoverflow.com/a/9898528/198996

            if (altitudeLockSnackbar != null) {
                return;
            }

            altitudeLockSnackbar = Snackbar.make(mapView, "Your device seems to lock the reported altitude at a specific value for security reasons. Sorry for any inconvenience caused!", Snackbar.LENGTH_INDEFINITE);
            altitudeLockSnackbar.setAction(android.R.string.ok, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    isAltitudeLockAcknowledged = true;

                    altitudeLockSnackbar.dismiss();
                    altitudeLockSnackbar = null;

                    bottomSheet.setVisibility(View.VISIBLE);
                }
            });

            bottomSheet.setVisibility(View.GONE);
            altitudeLockSnackbar.show();
        }

        altitudeView.setText(formatNumber(altitude, "m"));
        speedView.setText(formatNumber(location.getSpeed() * 3.6, "km/h"));

        double bearing = location.getBearing();
        String direction = convertBearingToDirection((int) bearing);
        bearingView.setText(formatNumber(bearing) + " (" + direction + ")");

        accuracyView.setText(formatNumber(location.getAccuracy(), "m"));

        String dateString = DATE_FORMAT.format(new Date());
        timestampView.setText(dateString);
    }

    @Override
    public void onFailure(@NonNull Exception exception) {
        showLocationSnackbar();
    }
}
