package ca.mcgill.cim.soundmap;

//import android.*;
import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
//import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
//import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.Timer;
import java.util.TimerTask;

public class MappingActivity extends FragmentActivity {

    private static final String TAG = "MappingActivity";

    private static final int ERROR_DIALOG_REQUEST = 9001;
    private static final int PERMISSIONS_REQ_CODE = 5029;

    private static String[] PERMISSIONS = {Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO};

    private boolean mPermissionGranted = false;
    private boolean mMapInitiated = false;

    private static final int DEFAULT_ZOOM = 20;
    private static final int DEFAULT_TILT = 70;
    private static final int DEFAULT_BEARING = 0;

    private GoogleMap mMap;
    private Location mLastKnownLocation;
//    private LocationCallback mLocationCallback;
    private final LatLng mDefaultLocation = new LatLng(45.504812985241564, -73.57715606689453);

    private Timer mForceUpdateTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapping);

        mForceUpdateTimer = new Timer("Force Local Update Timer", true);

        // TODO : Pull to an earlier screen whenever it is created
        if (isServicesAvailable()) {
            getPermissions();
            if (mPermissionGranted && !mMapInitiated) {
                initMap();
            }
        }
    }

    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                if (mPermissionGranted) {
                    mMap = googleMap;
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    mMap.setMapStyle(new MapStyleOptions(getResources()
                            .getString(R.string.fancy_map)));
//                    mMap.setMapStyle(new MapStyleOptions(getResources()
//                            .getString(R.string.no_poi_map)));

                    mMap.getUiSettings().setCompassEnabled(false);
                    mMap.getUiSettings().setMapToolbarEnabled(false);
                    mMap.getUiSettings().setTiltGesturesEnabled(false);
                    mMap.getUiSettings().setScrollGesturesEnabled(false);
                    mMap.getUiSettings().setIndoorLevelPickerEnabled(false);

                    mMap.setMinZoomPreference(18);
                    mMap.setMaxZoomPreference(25);

                    mMap.setBuildingsEnabled(false);
                    mMap.setIndoorEnabled(false);

                    if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION) !=
                            PackageManager.PERMISSION_GRANTED &&
                            ActivityCompat.checkSelfPermission(getApplicationContext(),
                                    Manifest.permission.ACCESS_COARSE_LOCATION) !=
                                    PackageManager.PERMISSION_GRANTED) {
                        updateCameraPose(mDefaultLocation, DEFAULT_BEARING);
                        return;
                    }
                    mMap.setMyLocationEnabled(true);
                    getLocation();

                    mForceUpdateTimer.schedule(new GetLocationTask(), 0, 15000);
                }
            }
        });
    }

    private void getLocation() {
        FusedLocationProviderClient fusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(this);

        try {
            if (mPermissionGranted) {
                Task track = fusedLocationProviderClient.getLastLocation();
                fusedLocationProviderClient.flushLocations();

                track.addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "onComplete: Localized");
                            mLastKnownLocation = (Location) task.getResult();

                            float bearing = mLastKnownLocation.getBearing();
                            LatLng latLng = new LatLng(mLastKnownLocation.getLatitude(),
                                                       mLastKnownLocation.getLongitude());

                            updateCameraPose(latLng, bearing);
//                            createLocationCallback();
                        } else {
                            // TODO : Convert to persistent error message
                            Toast.makeText(MappingActivity.this, "GPS signal unavailable",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e(TAG, "getLocation: " + e.getMessage());
        }
    }

//    private void createLocationCallback() {
//        mLocationCallback = new LocationCallback() {
//            @Override
//            public void onLocationResult(LocationResult locationResult) {
//                super.onLocationResult(locationResult);
//
//                Toast.makeText(MappingActivity.this, "New GPS Signal Received",
//                      Toast.LENGTH_SHORT).show();
//
//                mLastKnownLocation = locationResult.getLastLocation();
//                float bearing = mLastKnownLocation.getBearing();
//                LatLng latLng = new LatLng(mLastKnownLocation.getLatitude(),
//                        mLastKnownLocation.getLongitude());
//
//                updateCameraPose(latLng, bearing);
//            }
//        };
//    }

    private void updateCameraPose(LatLng latLng, float bearing) {
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(latLng)
                .zoom(DEFAULT_ZOOM)
                .tilt(DEFAULT_TILT)
                .bearing(bearing)
                .build();
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    private class GetLocationTask extends TimerTask {
        @Override
        public void run() {
            getLocation();
        }
    }

    private void getPermissions() {
        boolean denied = false;

        // Iterate through permissions,
        // If any are not granted, set denied flag to true
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                    permission) != PackageManager.PERMISSION_GRANTED) {
                denied = true;
                break;
            }
        }

        if (denied) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSIONS_REQ_CODE);
        } else {
            mPermissionGranted = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQ_CODE) {
            boolean denied = false;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    denied = true;
                    break;
                }
            }

            if (denied) {
                Toast.makeText(this, "Permissions were not granted. The app will not proceed",
                        Toast.LENGTH_LONG).show();
            } else {
                mPermissionGranted = true;
                if (!mMapInitiated) {
                    initMap();
                }
            }
        }
    }

    private boolean isServicesAvailable() {
        Log.d(TAG, "Verifying Google Play Services version and connectivity...");
        int available = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(MappingActivity.this);

        if (available == ConnectionResult.SUCCESS) {
            Log.d(TAG, "Google Play Services successfully connected.");
            return true;
        } else if (GoogleApiAvailability.getInstance().isUserResolvableError(available)) {
            Log.d(TAG, "An error occurred, but can be resolved by the user");
            Dialog dialog = GoogleApiAvailability.getInstance()
                    .getErrorDialog(MappingActivity.this, available, ERROR_DIALOG_REQUEST);
            dialog.show();
            return false;
        } else {
            Log.d(TAG, "An unresolvable error occurred with Google Play Services");
            Toast.makeText(this, "An unresolvable error occurred with Google Play Services",
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
