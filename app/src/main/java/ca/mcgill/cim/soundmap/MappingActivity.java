package ca.mcgill.cim.soundmap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.MediaRecorder;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MappingActivity extends FragmentActivity {

    // Log Tag
    private static final String TAG = "MappingActivity";

    // Status and Process Codes
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
    private boolean mIsDebugging = false;

    // User
    private String mUser;

    // Default View Settings
    private static final int DEFAULT_ZOOM = 20;
    private static final int DEFAULT_TILT = 70;
    private static final int DEFAULT_BEARING = 0;

    // Event Timers
    private Timer mLocationUpdateTimer;
    private static final int LOCATION_UPDATE_RATE = 1000; // ms
    private Timer mAudioSampleTimer;
    private static final int AUDIO_SAMPLE_RATE = 100;     // ms
    private Timer mPointOfInterestTimer;
    private static final int POI_UPDATE_RATE = 30000;     // ms

    // GPS Localization
    private GoogleMap mMap;
    private LocationClientService mLocationClientService;
    private final LatLng mDefaultLocation = new LatLng(45.504812985241564, -73.57715606689453);
    private float mLastKnownBearing = DEFAULT_BEARING;
    private LatLng mLastKnownCoords = mDefaultLocation;
    private long mLastFix;
    private static final int GPS_TIMEOUT= 60000;    // ms (1 min)

    // Mapping
    private boolean mIsViewInitted = false;
    private Marker mTarget;
    private static final double DEFAULT_MARKER_OPACITY = 0.9;
    private static final double TARGET_DISTANCE_THRESHOLD = 100; // m

    // Audio Sampling
    private MediaRecorder mAudioSampler;
    private ProgressBar mProgressBar;
    private String mSampleFile;
    private String mPathToFile;
    private int mCurrentVolume = -1;
    private static final String AUDIO_FILE_EXT = ".3gp";
    private static final int RECORDING_LENGTH = 30000;
    private static final int RECORDING_CHECK_RATE = 1000;
    private static final double PROGRESS_RATE =
            ((double)RECORDING_CHECK_RATE / (double)RECORDING_LENGTH) * 100;

    // Volume Indicator
    private static final int VOLUME_UPPER_BOUND = 1000;
    private static final int VOLUME_LOWER_BOUND = 100;
    private View mVolumeBar;
    private int mVolumeBarMaxHeight;
    private int mVolumeBarSetpoint;
    private TextView mVolumeText;
    private boolean mIsTextVisible = false;

    // Error Message
    private TextView mErrorMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapping);

        Log.d(TAG, "onCreate: Initializing the mapping activity");

        // Get user from landing page
        Bundle extras = getIntent().getExtras();
        if (extras.containsKey("email")) {
            mUser = extras.getString("email");
        } else {
            mUser = "anon";
        }
        Log.d(TAG, "onCreate: User identified as: " + mUser);

        try {
            mPathToFile = getExternalCacheDir().getAbsolutePath();
        } catch (NullPointerException e) {
            Log.e(TAG, "onCreate: Error - " + e.getMessage());
            return;
        }

        // Initialize Timers
        // $1 == Thread Name
        // $2 == Run as Daemon
        mLocationUpdateTimer = new Timer("GPS Update Event Timer", true);
        mAudioSampleTimer = new Timer("Audio Sampling Event Timer", true);
        mPointOfInterestTimer = new Timer("POI Update Event Timer", false);

        mLocationClientService = new LocationClientService();

        // Create Event Listener for the recording button
        ImageButton recordButton = (ImageButton) findViewById(R.id.rec_button);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordButtonClicked();
            }
        });

        // Create Event Listener for the recording button
        ImageButton recBadge = (ImageButton) findViewById(R.id.rec_badge);
        recBadge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsTextVisible) {
                    mVolumeText.setVisibility(View.GONE);
                    mIsTextVisible = false;
                    mIsDebugging = false;
                } else {
                    mVolumeText.setVisibility(View.VISIBLE);
                    mIsTextVisible = true;
                    mIsDebugging = true;
                }
            }
        });

        // Grab the volume bar object for later manipulation
        mVolumeBar = findViewById(R.id.volume_bar);
        mVolumeText = (TextView) findViewById(R.id.volume_text);
        mProgressBar = (ProgressBar) findViewById(R.id.rec_progress);
        mErrorMessage = (TextView) findViewById(R.id.error_text);

        Log.d(TAG, "onCreate: Members initialized; checking service compatibility and permissions");

        // Get user permissions for Location and Audio Recording
        getPermissions();
        if (mPermissionGranted && !mMapInitiated) {
            Log.d(TAG, "onCreate: Creating the map");
            initMap();
        } else {
            Log.d(TAG, "onCreate: Permission denied or map already initialized");
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
                    mPointOfInterestTimer.schedule(new UpdatePOITask(), 0, POI_UPDATE_RATE);
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

    private void updatePointsOfInterest() {
        if (mLocationClientService == null) {
            Log.w(TAG, "updatePointsOfInterest: Location Client Service is not initted");
            return;
        }

        // Clear previous data
        runOnUiThread(new Runnable(){
            public void run() {
                mMap.clear();
            }
        });

        Log.d(TAG, "updatePointsOfInterest: Attempting to add target marker");
        try {
            final Pair<String, LatLng> target =
                    mLocationClientService.getTargetLocation(mLastKnownCoords);

            if (target != null && target.first != null && target.second != null) {
                runOnUiThread(new Runnable(){
                    public void run() {
                        mErrorMessage.setVisibility(View.GONE);
                        addMarker(target.second, target.first);
                    }
                });
            } else {
                runOnUiThread(new Runnable(){
                    public void run() {
                        mErrorMessage.setVisibility(View.VISIBLE);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "updatePointsOfInterest: Error - " + e.toString());
            runOnUiThread(new Runnable(){
                public void run() {
                    mErrorMessage.setVisibility(View.VISIBLE);
                }
            });
        }

        Log.d(TAG, "updatePointsOfInterest: Attempting to add user markers");
        try {
            List<Pair<String, LatLng>> users =
                    mLocationClientService.getOtherUsers();

            if (!(users == null || users.isEmpty())) {
                for (final Pair<String, LatLng> user : users) {
                    runOnUiThread(new Runnable(){
                        public void run() {
                            addPerson(user.second, user.first);
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "updatePointsOfInterest: Error - " + e.toString());
        }
    }

    private void addPerson(LatLng latLng, String user) {
        mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title(user)
                .alpha((float)DEFAULT_MARKER_OPACITY)
                .draggable(false)
                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_person)));
    }

    private void addMarker(LatLng latLng, String desc) {
        mTarget = mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title(desc)
                .alpha((float)DEFAULT_MARKER_OPACITY)
                .draggable(false)
                .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_marker)));

        mMap.addCircle(new CircleOptions()
                .center(latLng)
                .radius(TARGET_DISTANCE_THRESHOLD)
                .clickable(false)
                .strokeWidth(10)
                .strokeColor(ContextCompat.getColor(this, R.color.colorAccent))
                .fillColor(ContextCompat.getColor(this, R.color.brightRedTrans)));
    }

    private void recordButtonClicked() {
        // Grab the recording status button to switch it on and off
        Log.d(TAG, "recordButtonClicked: clicked");

        if (mIsRecording) {
            Toast.makeText(this, "Please wait for the recording to finish",
                    Toast.LENGTH_SHORT).show();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        Log.d(TAG, "recordButtonClicked: Recording ON");
        ImageButton status = (ImageButton) findViewById(R.id.rec_badge);
        ImageButton button = (ImageButton) findViewById(R.id.rec_button);

        mAudioSampler = new MediaRecorder();
        mAudioSampler.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
        mAudioSampler.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mAudioSampler.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

        if (mPathToFile == null || mPathToFile.trim().equals("")) {
            Toast.makeText(this, "Cannot record.. Contact admin", Toast.LENGTH_LONG).show();
            return;
        } else {
            mSampleFile = mPathToFile + "/" + mUser + "_"
                    + Long.toString(System.currentTimeMillis()) + AUDIO_FILE_EXT;
            mAudioSampler.setOutputFile(mSampleFile);
        }

        try {
            mAudioSampler.prepare();
            mAudioSampler.start();
        } catch (IOException e) {
            Toast.makeText(this, "Could not access mic. \n Make sure it is not" +
                    "being used by another process.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "recordButtonClicked: Error - " + e.getMessage());
            return;
        }

        // Start the audio sampling event timer and make the status red
        mAudioSampleTimer = new Timer("Audio Sampling Event Timer",true);
        mAudioSampleTimer.schedule(new SampleAudioTask(), 0, AUDIO_SAMPLE_RATE);

        new CountDownTimer(RECORDING_LENGTH, RECORDING_CHECK_RATE) {
            private double progress = PROGRESS_RATE;

            public void onTick(long millisUntilFinished) {
                Location target = new Location("target");
                target.setLatitude(mTarget.getPosition().latitude);
                target.setLongitude(mTarget.getPosition().longitude);

                Location current = new Location("current");
                current.setLatitude(mLastKnownCoords.latitude);
                current.setLongitude(mLastKnownCoords.longitude);

                if ((current.distanceTo(target) > TARGET_DISTANCE_THRESHOLD) && !mIsDebugging) {
                    stopRecording();
                    Toast.makeText(MappingActivity.this, "You have gone out of range",
                            Toast.LENGTH_SHORT).show();
                    cancel();
                }

                progress += PROGRESS_RATE;
                mProgressBar.setProgress((int)progress);
            }

            public void onFinish() {
                stopRecording();
                uploadRecording();
            }
        }.start();

        button.setImageResource(R.mipmap.ic_button_grey);
        status.setImageResource(R.mipmap.ic_rec_badge_red);
        mProgressBar.setVisibility(View.VISIBLE);
        mIsRecording = true;
    }

    private void stopRecording() {
        Log.d(TAG, "recordButtonClicked: Recording OFF");
        ImageButton status = (ImageButton) findViewById(R.id.rec_badge);
        ImageButton button = (ImageButton) findViewById(R.id.rec_button);

        // Clear the audio sampling event timer and make the status grey
        if (mAudioSampleTimer != null) {
            mAudioSampleTimer.cancel();
            mAudioSampleTimer.purge();
        }

        if (mAudioSampler != null && mIsRecording) {
            try {
                mAudioSampler.stop();
            } catch (Exception e) {
                Log.e(TAG, "stopRecording: Error trying to stop media recorder.\n\tError - " +
                        e.toString());
            }
            mAudioSampler.release();
            mAudioSampler = null;
        }

        mCurrentVolume = 0;
        updateVolumeBar();
        updateVolumeText();
        button.setImageResource(R.mipmap.ic_button_red);
        status.setImageResource(R.mipmap.ic_rec_badge_grey);
        mProgressBar.setVisibility(View.INVISIBLE);
        mIsRecording = false;
    }

    private void sampleAudio() {
        // Take a new sample and average it with the last one
        if ((mAudioSampler != null) && (!mIsTimeout)) {
            int sample = mAudioSampler.getMaxAmplitude();
            Log.i(TAG, "sampleAudio: Sample - " + Integer.toString(sample));

            // Update the volume indicator
            mCurrentVolume = sample;
            updateVolumeBar();
            updateVolumeText();
        }
    }

    private void uploadRecording() {
        if (mSampleFile == null || mSampleFile.trim().equals("")) {
            Log.w(TAG, "uploadRecording: Could not find an appropriate source file path");
            return;
        }

        Toast.makeText(this, "Uploading the audio sample...", Toast.LENGTH_SHORT).show();

        FileTransferService fts = new FileTransferService(mSampleFile, mUser, mLastKnownCoords);
        fts.execute();
    }

    void updateVolumeBar() {
        // Map the input volume to the corresponding pixel height
        if (mCurrentVolume > VOLUME_UPPER_BOUND) {
            mVolumeBarSetpoint = mVolumeBarMaxHeight;
        } else if (mCurrentVolume < VOLUME_LOWER_BOUND) {
            mVolumeBarSetpoint = 0;
        } else {
            double ratio = (double) (mCurrentVolume - VOLUME_LOWER_BOUND) / (double) VOLUME_UPPER_BOUND;
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

    void updateVolumeText() {
        // Update the text to display the audio intensity as a number
        runOnUiThread(new Runnable() {
            public void run() {
                mVolumeText.setText(Integer.toString(mCurrentVolume));
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

    // A TimerTask to sample audio at a consistent event rate
    private class SampleAudioTask extends TimerTask {
        @Override
        public void run() {
            sampleAudio();
        }
    }

    // A TimerTask to sample audio at a consistent event rate
    private class UpdatePOITask extends TimerTask {

        @Override
        public void run() {
            updatePointsOfInterest();
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

    @Override
    public void onBackPressed() {
        if (mIsRecording) {
            stopRecording();
        }
        finish();
    }
}
