package at.tomtasche.flightdashboard;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.offline.OfflineManager;
import com.mapbox.mapboxsdk.offline.OfflineRegion;
import com.mapbox.mapboxsdk.offline.OfflineRegionError;
import com.mapbox.mapboxsdk.offline.OfflineRegionStatus;
import com.mapbox.mapboxsdk.offline.OfflineTilePyramidRegionDefinition;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements MapboxMap.OnMyLocationChangeListener {

    private static final String TAG = "FlightDashboard";

    private static final String MAPBOX_API_KEY = "pk.eyJ1IjoidG9tdGFzY2hlIiwiYSI6ImNqNjN3cWJneTFsaTkyeG8zNTZ3ZDhocGwifQ.NJbB_cCAqT_KDOublhLa2A";
    private static final String MAPBOX_STYLE_URL = "mapbox://styles/mapbox/outdoors-v10";
    private static final int MAPBOX_MIN_ZOOM = 1;
    private static final int MAPBOX_MAX_ZOOM = 6;

    // actual flight altitude is much higher, but devices seem to stop updating altitude at some point
    private static final double MIN_FLIGHT_ALTITUDE = 5000.0;

    private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("hh:mm:ss");

    private ProgressBar progressBar;
    private Snackbar altitudeSnackbar;

    private View bottomSheet;
    private BottomSheetBehavior<View> sheetBehavior;

    private TextView altitudeView;
    private TextView speedView;
    private TextView bearingView;
    private TextView accuracyView;
    private TextView timestampView;

    private MapView mapView;
    private MapboxMap mapboxMap;
    private OfflineManager offlineManager;

    private boolean isLocationFix = false;

    private boolean isAltitudeAcknowledged = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Mapbox.getInstance(this, MAPBOX_API_KEY);

        setContentView(R.layout.activity_main);

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

        mapView.setStyleUrl(MAPBOX_STYLE_URL);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            showLocationPermissionSnackbar();
        } else {
            initializeMap();
        }
    }

    private void setBottomSheetState(int state) {
        if (sheetBehavior.getState() != state) {
            sheetBehavior.setState(state);
        }
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
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeMap();
        } else {
            showLocationPermissionSnackbar();
        }
    }

    private void showLocationPermissionSnackbar() {
        final Snackbar snackbar = Snackbar.make(mapView, R.string.snackbar_location_permission_text, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.snackbar_location_permission_action, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snackbar.dismiss();

                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 123);
            }
        });

        snackbar.show();
    }

    private void initializeMap() {
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                MainActivity.this.mapboxMap = mapboxMap;

                mapboxMap.setMyLocationEnabled(true);

                mapboxMap.setOnMyLocationChangeListener(MainActivity.this);

                pollLocationFix();

                mapboxMap.setOnMapClickListener(new MapboxMap.OnMapClickListener() {
                    @Override
                    public void onMapClick(@NonNull LatLng point) {
                        setBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED);
                    }
                });
            }
        });

        offlineManager = OfflineManager.getInstance(this);

        offlineManager.listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback() {
            @Override
            public void onList(OfflineRegion[] offlineRegions) {
                if (offlineRegions.length == 0) {
                    downloadMap();

                    return;
                }

                offlineRegions[0].getStatus(new OfflineRegion.OfflineRegionStatusCallback() {
                    @Override
                    public void onStatus(OfflineRegionStatus status) {
                        if (!status.isComplete()) {
                            downloadMap();
                        } else {
                            progressBar.setProgress(100);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Log.d(TAG, "Error: " + error);

                        downloadMap();
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.d(TAG, "Error: " + error);

                downloadMap();
            }
        });
    }

    private void downloadMap() {
        LatLngBounds worldBounds = LatLngBounds.world();
        float screenDensity = MainActivity.this.getResources().getDisplayMetrics().density;

        OfflineTilePyramidRegionDefinition definition = new OfflineTilePyramidRegionDefinition(MAPBOX_STYLE_URL, worldBounds, MAPBOX_MIN_ZOOM, MAPBOX_MAX_ZOOM, screenDensity);

        offlineManager.createOfflineRegion(definition, null, new OfflineManager.CreateOfflineRegionCallback() {
            @Override
            public void onCreate(OfflineRegion offlineRegion) {
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE);

                offlineRegion.setObserver(new OfflineRegion.OfflineRegionObserver() {
                    @Override
                    public void onStatusChanged(OfflineRegionStatus status) {
                        double percentage = status.getRequiredResourceCount() >= 0 ? (100.0 * status.getCompletedResourceCount() / status.getRequiredResourceCount()) : 0.0;
                        progressBar.setProgress((int) percentage);

                        if (status.isComplete()) {
                            Log.d(TAG, "Region downloaded successfully.");
                        } else if (status.isRequiredResourceCountPrecise()) {
                            Log.d(TAG, percentage + "");
                        }
                    }

                    @Override
                    public void onError(OfflineRegionError error) {
                        Log.e(TAG, "onError reason: " + error.getReason());
                        Log.e(TAG, "onError message: " + error.getMessage());
                    }

                    @Override
                    public void mapboxTileCountLimitExceeded(long limit) {
                        Log.e(TAG, "Mapbox tile count limit exceeded: " + limit);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error: " + error);
            }
        });
    }

    private void pollLocationFix() {
        if (isLocationFix) {
            return;
        }

        Location location = mapboxMap.getMyLocation();
        if (location != null) {
            onMyLocationChange(location);

            return;
        }

        mapView.postDelayed(new Runnable() {
            @Override
            public void run() {
                pollLocationFix();
            }
        }, 500);
    }

    @Override
    public void onMyLocationChange(@Nullable Location location) {
        isLocationFix = true;

        if (!isAltitudeAcknowledged && location.getAltitude() <= MIN_FLIGHT_ALTITUDE) {
            if (altitudeSnackbar == null) {
                altitudeSnackbar = Snackbar.make(mapView, R.string.snackbar_altitude_text, Snackbar.LENGTH_INDEFINITE);
                altitudeSnackbar.setAction(R.string.snackbar_altitude_action, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showAltitudeWarning();
                    }
                });
            }

            bottomSheet.setVisibility(View.GONE);
            altitudeSnackbar.show();
        } else {
            if (altitudeSnackbar != null) {
                altitudeSnackbar.dismiss();
            }

            bottomSheet.setVisibility(View.VISIBLE);

            altitudeView.setText(formatNumber(location.getAltitude(), "m"));
            speedView.setText(formatNumber(location.getSpeed() * 3.6, "km/h"));

            double bearing = location.getBearing();
            String direction = convertBearingToDirection((int) bearing);
            bearingView.setText(formatNumber(bearing) + " (" + direction + ")");

            accuracyView.setText(formatNumber(location.getAccuracy(), "m"));

            String dateString = DATE_FORMAT.format(new Date());
            timestampView.setText(dateString);
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
                isLocationFix = false;
                pollLocationFix();

                dialog.dismiss();
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

}
