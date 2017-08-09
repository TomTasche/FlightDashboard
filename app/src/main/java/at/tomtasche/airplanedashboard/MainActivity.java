package at.tomtasche.airplanedashboard;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.offline.OfflineManager;
import com.mapbox.mapboxsdk.offline.OfflineRegion;
import com.mapbox.mapboxsdk.offline.OfflineRegionError;
import com.mapbox.mapboxsdk.offline.OfflineRegionStatus;
import com.mapbox.mapboxsdk.offline.OfflineTilePyramidRegionDefinition;

public class MainActivity extends AppCompatActivity implements MapboxMap.OnMyLocationChangeListener {

    private static final String TAG = "AirplaneDashboard";

    private static final String MAPBOX_API_KEY = "pk.eyJ1IjoidG9tdGFzY2hlIiwiYSI6ImNqNjN3cWJneTFsaTkyeG8zNTZ3ZDhocGwifQ.NJbB_cCAqT_KDOublhLa2A";
    private static final String MAPBOX_STYLE_URL = "mapbox://styles/mapbox/outdoors-v10";
    private static final int MAPBOX_MIN_ZOOM = 1;
    private static final int MAPBOX_MAX_ZOOM = 6;

    private static final double MIN_FLIGHT_ALTITUDE = 10000.0;

    private ProgressBar progressBar;
    private Snackbar altitudeSnackbar;

    private MapView mapView;
    private MapboxMap mapboxMap;
    private OfflineManager offlineManager;

    private boolean isLocationFix = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Mapbox.getInstance(this, MAPBOX_API_KEY);

        setContentView(R.layout.activity_main);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        mapView = (MapView) findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.setStyleUrl(MAPBOX_STYLE_URL);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
        } else {
            initializeMap();
        }
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 123);
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
            final Snackbar snackbar = Snackbar.make(mapView, "Allow location permission to show your current location on the map", Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction("Okay", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    snackbar.dismiss();

                    requestLocationPermission();
                }
            });

            snackbar.show();
        }
    }

    private void initializeMap() {
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                MainActivity.this.mapboxMap = mapboxMap;

                mapboxMap.setMyLocationEnabled(true);

                mapboxMap.setMinZoomPreference(MAPBOX_MIN_ZOOM);
                mapboxMap.setMaxZoomPreference(MAPBOX_MAX_ZOOM);

                mapboxMap.setOnMyLocationChangeListener(MainActivity.this);

                pollLocationFix();
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

        if (location.getAltitude() < MIN_FLIGHT_ALTITUDE) {
            if (altitudeSnackbar != null) {
                altitudeSnackbar.show();

                return;
            }

            altitudeSnackbar = Snackbar.make(mapView, "You are not in a flight it seems!", Snackbar.LENGTH_INDEFINITE);
            altitudeSnackbar.setAction("Huh?", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAltitudeWarning();
                }
            });

            altitudeSnackbar.show();
        } else {
            altitudeSnackbar.dismiss();
        }
    }

    private void showAltitudeWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Why do you tell me I'm not in a flight, stupid?!");
        builder.setMessage("To make sure everybody is able to use this app to its strengths we inform users that are not sitting inside an airplane that is already at flight altitude.\n\n" +
                "This app is not useful at all while you are not inside an airplane at flight altitude! The only thing you can do right now is to download the world map for later offline use (inside an airplane at flight altitude!)");

        builder.setNeutralButton("I understand that this app is only useful at flight altitude", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // the snackbar should also be visible after reading this dialog. you never know...
                isLocationFix = false;
                pollLocationFix();

                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
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
