package ca.mcgill.cim.soundmap;

//import android.*;
import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MappingActivity extends FragmentActivity {

    private static final String TAG = "MappingActivity";

    private static final int ERROR_DIALOG_REQUEST = 9001;
    private static final int PERMISSIONS_REQ_CODE = 5029;

    private static String[] PERMISSIONS = {Manifest.permission.ACCESS_FINE_LOCATION,
                                           Manifest.permission.RECORD_AUDIO};

    private boolean mPermissionGranted = false;
    private boolean mMapInitiated = false;
    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapping);

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
                mMap = googleMap;
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                mMap.getUiSettings().setCompassEnabled(false);
                mMap.getUiSettings().setMapToolbarEnabled(false);
                mMap.getUiSettings().setTiltGesturesEnabled(false);
                mMap.setMinZoomPreference(17);
                mMap.setMaxZoomPreference(25);
                mMap.setBuildingsEnabled(false);
                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(new LatLng(43.5017,-75.5673))
                        .zoom(18)
                        .tilt(68)
                        .bearing(314)
                        .build();
                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            }
        });
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
