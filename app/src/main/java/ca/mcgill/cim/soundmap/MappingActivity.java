package ca.mcgill.cim.soundmap;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Timer;
import java.util.TimerTask;

public class MappingActivity extends FragmentActivity {

    // Log Tag
    private static final String TAG = "MappingActivity";

    // Status and Process Codes
    private static final int ERROR_DIALOG_REQUEST = 9001;
    private static final int PERMISSIONS_REQ_CODE = 5029;

    // List of Necessary Permissions
    private static String[] PERMISSIONS = {Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO};

    // Process Flags
    private boolean mPermissionGranted = false;
    private boolean mMapInitiated = false;
    private boolean mIsTimeout = false;
    private boolean mIsRecording = false;

    // Default View Settings
    private static final int DEFAULT_ZOOM = 20;
    private static final int DEFAULT_TILT = 70;
    private static final int DEFAULT_BEARING = 0;

    // Event Timers
    private Timer mLocationUpdateTimer;
    private static final int LOCATION_UPDATE_RATE = 1000; // ms
    private Timer mAudioSampleTimer;
    private static final int AUDIO_SAMPLE_RATE = 100;     // ms

    // GPS Localization
    private GoogleMap mMap;
    private final LatLng mDefaultLocation = new LatLng(45.504812985241564, -73.57715606689453);
    private float mLastKnownBearing = DEFAULT_BEARING;
    private LatLng mLastKnownCoords = mDefaultLocation;
    private long mLastFix;
    private static final int GPS_TIMEOUT= 60000;    // ms (1 min)

    // Mapping
    private boolean mIsViewInitted = false;
    private Marker mTarget;
    private static final double DEFAULT_MARKER_OPACITY = 0.9;

    // Audio Sampling
    private MediaRecorder mAudioSampler;
    private String mSampleFile;
    private Data mSamples;
    private double mAverageIntensity = 0;
    private static final int POOL_SIZE = 110;

    // Volume Indicator
    private static final int VOLUME_UPPER_BOUND = 1000;
    private static final int VOLUME_LOWER_BOUND = 100;
    private View mVolumeBar;
    private int mVolumeBarMaxHeight;
    private int mVolumeBarSetpoint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapping);

        Log.d(TAG, "onCreate: Initializing the mapping activity");

        // Initialize the Samples ADT
        mSamples = new Data();

        try {
            mSampleFile = getExternalCacheDir().getAbsolutePath();
            mSampleFile += "/samples.3gp";
        } catch (NullPointerException e) {
            Log.e(TAG, "onCreate: Error - " + e.getMessage());
            return;
        }

        // Initialize Timers
        // $1 == Thread Name
        // $2 == Run as Daemon
        mLocationUpdateTimer = new Timer("GPS Update Event Timer", true);
        mAudioSampleTimer = new Timer("Audio Sampling Event Timer", true);

        // Create Event Listener for the recording button
        Button recordButton = (Button) findViewById(R.id.rec_button);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordButtonClicked();
            }
        });

        // Grab the volume bar object for later manipulation
        mVolumeBar = findViewById(R.id.volume_bar);

        Log.d(TAG, "onCreate: Members initialized; checking service compatibility and permissions");

        // Check to ensure Google Play Services is active and up-to-date
        if (isServicesAvailable()) {

            // Get user permissions for Location and Audio Recording
            getPermissions();
            if (mPermissionGranted && !mMapInitiated) {
                Log.d(TAG, "onCreate: Creating the map");
                initMap();
            } else {
                Log.d(TAG, "onCreate: Permission denied or map already initialized");
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        mVolumeBarMaxHeight = findViewById(R.id.map).getHeight();
        Log.d(TAG, "onWindowFocusChanged: Height: " + Integer.toString(mVolumeBarMaxHeight));
    }

    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        // Callback for when the Google Maps API becomes available
        Log.d(TAG, "initMap: Created fragment; waiting for response from API");
        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                if (mPermissionGranted) {
                    Log.d(TAG, "onMapReady: API is available and permission is granted");

                    mMap = googleMap;

                    // Map type normal, uses drawn polygonal maps
                    mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);

                    // Sets the map style; Two options:
                    // 1) Fancy map that eliminates more useless markers and is pretty
                    // 2) Simpler map that ~may~ be faster (not sure); and is just less cluttered
                    mMap.setMapStyle(new MapStyleOptions(getResources()
                            .getString(R.string.fancy_map)));
//                    mMap.setMapStyle(new MapStyleOptions(getResources()
//                            .getString(R.string.no_poi_map)));

                    // Set some GUI flags for what the user can see / do
                    mMap.getUiSettings().setCompassEnabled(false);
                    mMap.getUiSettings().setMapToolbarEnabled(false);
                    mMap.getUiSettings().setTiltGesturesEnabled(false);
                    mMap.getUiSettings().setScrollGesturesEnabled(false);
                    mMap.getUiSettings().setIndoorLevelPickerEnabled(false);

                    // Keep the zoom boundaries close
                    // 15 == Street
                    // 20 == Building
                    mMap.setMinZoomPreference(18);
                    mMap.setMaxZoomPreference(25);

                    // Remove Costly and unnecessary features
                    mMap.setBuildingsEnabled(false);
                    mMap.setIndoorEnabled(false);

                    Log.d(TAG, "onMapReady: Map created and flags set; Enabling location services");

                    // Recheck Permissions because android is ... thorough.
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION) !=
                            PackageManager.PERMISSION_GRANTED &&
                            ActivityCompat.checkSelfPermission(getApplicationContext(),
                                    Manifest.permission.ACCESS_COARSE_LOCATION) !=
                                    PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    // Enable Location Services within the Google Maps API
                    mMap.setMyLocationEnabled(true);
                    mLastFix = System.currentTimeMillis();
                    getCurrentLocation();

                    Log.d(TAG, "onMapReady: Location services enabled; Starting refresh timer");

                    // Start a timer to continuously update the location
                    mLocationUpdateTimer.schedule(new GetLocationTask(), 0, LOCATION_UPDATE_RATE);
                }
            }
        });
    }

    private void getCurrentLocation() {
        //Log.d(TAG, "getCurrentLocation: Getting current location");
        FusedLocationProviderClient fusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(this);

        try {
            if (mPermissionGranted) {
                // Retrieve the last known location as a Task
                // Note: the fusedLocationProviderClient seems to be the most recent API...
                // I think it also uses other networks, like wifi?
                // If necessary, there are alternative APIs
                Task track = fusedLocationProviderClient.getLastLocation();
                fusedLocationProviderClient.flushLocations();

                track.addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        long now = System.currentTimeMillis();

                        if (task.isSuccessful()) {
                            Location location = (Location) task.getResult();

                            // Get the bearing and coordinates of the device location
                            mLastKnownBearing = location.getBearing();
                            mLastKnownCoords = new LatLng(location.getLatitude(),
                                                          location.getLongitude());

                            if (mIsViewInitted) {
                                updateCameraPose();
                            } else {
                                initCameraPose();
                            }

                            mIsTimeout = false;
                            mLastFix = now;

                        } else {
                            // Could not get a proper GPS fix
                            Log.d(TAG, "onComplete: GPS signal unavailable");
                            Toast.makeText(MappingActivity.this, "GPS signal unavailable",
                                    Toast.LENGTH_SHORT).show();

                            if (Math.abs(now - mLastFix) > GPS_TIMEOUT) {
                                Log.d(TAG, "onComplete: GPS timed out");
                                mIsTimeout = true;
                            }
                        }
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e(TAG, "getLocation: " + e.getMessage());
        }
    }

    private void initCameraPose() {
        // Initialize the Camera position using the new coordinates and bearing
        // But always keep the default zoom and tilt to somewhat lock the view
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(mLastKnownCoords)
                .tilt(DEFAULT_TILT)
                .zoom(DEFAULT_ZOOM)
                .bearing(mLastKnownBearing)
                .build();
        mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

        mIsViewInitted = true;
    }

    private void updateCameraPose() {
        // Update the Camera position using the new coordinates and bearing
        // But always keep the default zoom and tilt to somewhat lock the view
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(mLastKnownCoords)
                .tilt(DEFAULT_TILT)
                .zoom(mMap.getCameraPosition().zoom) // Don't override zoom if user changed it
                .bearing(mMap.getCameraPosition().bearing) // Don't override bearing either
                .build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    private void addMarker(LatLng latLng, String desc) {
        mTarget = mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title(desc)
                .alpha((float)DEFAULT_MARKER_OPACITY)
                .draggable(false)
                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_launcher)));
    }

    private void recordButtonClicked() {
        // Grab the recording status button to switch it on and off
        Log.d(TAG, "recordButtonClicked: clicked");
        ImageButton status = (ImageButton) findViewById(R.id.rec_badge);

        if (mIsRecording) {
            Log.d(TAG, "recordButtonClicked: Recording OFF");

            // Clear the audio sampling event timer and make the status grey
            if (mAudioSampleTimer != null) {
                mAudioSampleTimer.cancel();
                mAudioSampleTimer.purge();
            }

            if (mAudioSampler != null) {
                mAudioSampler.stop();
                mAudioSampler.release();
                mAudioSampler = null;
            }

            updateVolumeBar(0);
            status.setImageResource(R.mipmap.ic_action_rec_grey);
            mIsRecording = false;
        } else {
            Log.d(TAG, "recordButtonClicked: Recording ON");

            mAudioSampler = new MediaRecorder();
            mAudioSampler.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
            mAudioSampler.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            mAudioSampler.setOutputFile(mSampleFile);
            mAudioSampler.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            try {
                mAudioSampler.prepare();
            } catch (IOException e) {
                Log.e(TAG, "recordButtonClicked: Error - " + e.getMessage());
            }

            mAudioSampler.start();

            // Start the audio sampling event timer and make the status red
            mAudioSampleTimer = new Timer("Audio Sampling Event Timer",true);
            mAudioSampleTimer.schedule(new SampleAudioTask(), 0, AUDIO_SAMPLE_RATE);

            status.setImageResource(R.mipmap.ic_action_rec_red);
            mIsRecording = true;
        }
    }

    private void sampleAudio() {
        // Take a new sample and average it with the last one
        if ((mAudioSampler != null) && (!mIsTimeout)) {
            int sample = mAudioSampler.getMaxAmplitude();
            Log.i(TAG, "sampleAudio: Sample - " + Integer.toString(sample));
            mSamples.push(sample, mLastKnownCoords);

            // Update the volume indicator
            updateVolumeBar(sample);
        }

        // If the sample set has reached the desired pool size,
        // pack the data into an average to transfer to the server
        if (mSamples.size() >= POOL_SIZE) {
            packSamples();
        }
    }

    // Calculate the mean of the data set and then clear it
    private void packSamples() {
        Log.d(TAG, "packSamples: Packing samples for transfer");

        if (mSamples.isValid()) {
            mAverageIntensity = mSamples.getAverageIntensity();
        } else {
            Log.w(TAG, "packSamples: Data set not valid");
            mAverageIntensity = 0.0;
        }
        mSamples.clear();
    }

    void updateVolumeBar(int level) {
        // Map the input volume to the corresponding pixel height
        if (level > VOLUME_UPPER_BOUND) {
            mVolumeBarSetpoint = mVolumeBarMaxHeight;
        } else if (level < VOLUME_LOWER_BOUND) {
            mVolumeBarSetpoint = 0;
        } else {
            double ratio = (double) (level - VOLUME_LOWER_BOUND) / (double) VOLUME_UPPER_BOUND;
            mVolumeBarSetpoint = (int)(ratio * mVolumeBarMaxHeight);
        }

        // Update the volume bar setpoint on the UI thread
        runOnUiThread(new Runnable() {
            public void run() {
                mVolumeBar.requestLayout();
                mVolumeBar.getLayoutParams().height = mVolumeBarSetpoint;
            }
        });
    }

    // A TimerTask to persistently update the user's location
    private class GetLocationTask extends TimerTask {
        @Override
        public void run() {
            getCurrentLocation();
        }
    }

    // A TimerTask that acts as a timeout for unresponsive GPS
    private class GPSTimeoutTask extends TimerTask {
        @Override
        public void run() {
            mIsTimeout = true;
        }
    }

    // A TimerTask to sample audio at a consistent event rate
    private class SampleAudioTask extends TimerTask {
        @Override
        public void run() {
            sampleAudio();
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
                Log.w(TAG, "getPermissions: Permissions Not Yet Granted");
                break;
            }
        }

        // Could not find already granted permissions, so ask explicitly
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

        // If this response is from this class's corresponding request,
        // process it and make sure all permissions were granted
        if (requestCode == PERMISSIONS_REQ_CODE) {
            boolean denied = false;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    denied = true;
                    break;
                }
            }

            // If any of the permissions were not granted nothing will proceed without the
            // permission granted flag
            if (denied) {
                Log.w(TAG, "getPermissions: Permissions Denied");
                Toast.makeText(this, "Permissions were not granted. The app will not proceed",
                        Toast.LENGTH_LONG).show();
            } else {
                // If permission was granted, proceed where the initial onCreate left off
                mPermissionGranted = true;
                if (!mMapInitiated) {
                    initMap();
                }
            }
        }
    }

    // Check that the Google Play Services is available and compatible
    private boolean isServicesAvailable() {
        Log.d(TAG, "isServicesAvailable: Verifying version and connectivity");
        int available = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(MappingActivity.this);

        if (available == ConnectionResult.SUCCESS) {
            Log.d(TAG, "isServicesAvailable: Google Play Services successfully connected.");
            return true;
        } else if (GoogleApiAvailability.getInstance().isUserResolvableError(available)) {
            Log.d(TAG, "isServicesAvailable: An error occurred, but can be resolved by the user");
            Dialog dialog = GoogleApiAvailability.getInstance()
                    .getErrorDialog(MappingActivity.this, available, ERROR_DIALOG_REQUEST);
            dialog.show();
            return false;
        } else {
            Log.d(TAG, "isServicesAvailable: An unresolvable error occurred");
            Toast.makeText(this, "An unresolvable error occurred with Google Play Services",
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }
}
