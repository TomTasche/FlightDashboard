package at.tomtasche.airplanedashboard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.Toast;

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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AirplaneDashboard";

    private static final String MAPBOX_STYLE_URL = "mapbox://styles/mapbox/outdoors-v10";
    private static final int MAPBOX_MIN_ZOOM = 1;
    private static final int MAPBOX_MAX_ZOOM = 6;

    private ProgressBar progressBar;

    private MapView mapView;
    private OfflineManager offlineManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Mapbox.getInstance(this, "pk.eyJ1IjoidG9tdGFzY2hlIiwiYSI6ImNqNjN3cWJneTFsaTkyeG8zNTZ3ZDhocGwifQ.NJbB_cCAqT_KDOublhLa2A");

        setContentView(R.layout.activity_main);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);

        mapView = (MapView) findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.setStyleUrl(MAPBOX_STYLE_URL);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 123);
        } else {
            initializeMap();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializeMap();
        } else {
            Toast.makeText(this, "nope.", Toast.LENGTH_LONG).show();

            finish();
        }
    }

    private void initializeMap() {
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                mapboxMap.setMyLocationEnabled(true);

                mapboxMap.setMinZoomPreference(MAPBOX_MIN_ZOOM);
                mapboxMap.setMaxZoomPreference(MAPBOX_MAX_ZOOM);
            }
        });

        offlineManager = OfflineManager.getInstance(this);

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
                        double percentage = status.getRequiredResourceCount() >= 0
                                ? (100.0 * status.getCompletedResourceCount() / status.getRequiredResourceCount()) :
                                0.0;

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
