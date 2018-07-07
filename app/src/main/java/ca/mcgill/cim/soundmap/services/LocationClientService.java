package ca.mcgill.cim.soundmap.services;

import android.location.Address;
import android.location.Geocoder;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;

import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ca.mcgill.cim.soundmap.activities.MappingActivity;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LocationClientService extends AsyncTask<Void, Void, Void> {

    private static final String TAG = "LocationClientService";

    private static final String LOCATION_HOST_URL =
            "http://sandeepmanjanna.dlinkddns.com:5000/location";

    private static final String USERS_HOST_URL =
            "http://sandeepmanjanna.dlinkddns.com:5000/users";

    private static final String SERVER_WAIT_STRING = "Wait";

    private MappingActivity mCalledFrom;
    private String mUser;
    private LatLng mUserLocation;

    public LocationClientService(MappingActivity calledFrom, String user, LatLng userLocation) {
        //Log.d(TAG, "LocationClientService: Starting Location Client Service");

        mCalledFrom = calledFrom;
        mUser = user;
        mUserLocation = userLocation;
    }

    private Pair<String, LatLng> mTarget;
    private List<Pair<String, LatLng>> mUsers;

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected Void doInBackground(Void... params) {
        mTarget = getTargetLocation();
        mUsers = getOtherUsers();
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        mCalledFrom.onRequestMarkerUpdateComplete(mTarget, mUsers);
    }

    private Pair<String, LatLng> getTargetLocation() {
        OkHttpClient client = new OkHttpClient()
                .newBuilder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(LOCATION_HOST_URL)
                .header("username", mUser)
                .header("lat", Double.toString(mUserLocation.latitude))
                .header("lng", Double.toString(mUserLocation.longitude))
                .build();

        // Post the Request using the OkHttp Client
        try {
            //Log.d(TAG, "getTargetLocation: attempting get request to server");
            Response response = client.newCall(request).execute();

            /**
             * Example Response:
             *      McGill:45.504812985241564,-73.57715606689453
             */

            String res = response.body().string();

            // If the res is empty, then could not communicate with the server
            if (res == null) {
                return null;
            // If the res indicates "Wait" then sleep the thread and try making the request again
            } else if (res.equals(SERVER_WAIT_STRING)) {
                SystemClock.sleep(5000);
                return getTargetLocation();
            // If the final res does not contain a ':' or ',' then it is malformed --> server error
            } else if (!res.contains(":") || !res.contains(",")) {
                return null;
            }

            // The name tag of a location should appear before a ":"
            String[] data = res.split(":");
            String tag = data[0];

            // The coordinates should follow, separated by a ","
            if (data[1] != null) {
                String[] coords = data[1].split(",");
                try {
                    Double lat = Double.parseDouble(coords[0]);
                    Double lng = Double.parseDouble(coords[1]);
                    LatLng location = new LatLng(lat, lng);

// THIS INTRODUCES SOME UNRELIABILITY TO THE CODE, SO IT IS CURRENTLY NOT IN USE
//
//                    // Checks for an address at the given location
//                    Geocoder gc = new Geocoder(mCalledFrom.getApplicationContext());
//                    List<Address> address = gc.getFromLocation(lat, lng, 1);
//
//                    // If there is no address at that location, so use the raw target
//                    if (address.isEmpty()) {
//                        location = new LatLng(lat, lng);
//                    // If there is an address, the raw target may be unreachable,
//                    // user the coords of the street address instead of the raw target
//                    } else {
//                        location = new LatLng (address.get(0).getLatitude(),
//                                               address.get(0).getLongitude());
//                    }

                    return new Pair<>(tag, location);
                } catch (Exception e) {
                    //Log.e(TAG, "getTargetLocation: Could not parse coordinates");
                    return null;
                }
            } else {
                //Log.e(TAG, "getTargetLocation: Could not parse coordinates");
                return null;
            }
        } catch (IOException e) {
            //Log.e(TAG, "getTargetLocation: DID NOT USE SERVER LOCATION");
            //Log.e(TAG, "uploadFile: Error - " + e.getMessage());
            return null;
        }
    }

    private List<Pair<String, LatLng>> getOtherUsers() {
        // TODO : This will be a heavy computation for lots of users, but should be fine for
        // the assumed scale at this time.

        OkHttpClient client = new OkHttpClient()
                .newBuilder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(USERS_HOST_URL)
                .build();

        // To store the users in parsed form
        List<Pair<String, LatLng>> result = new ArrayList<>();

        // Post the Request using the OkHttp Client
        try {
            //Log.d(TAG, "getOtherUsers: attempting get request to server");
            Response response = client.newCall(request).execute();

            /**
             * Example Response:
             *      Foo:45.50,-73.57;Bar:45.55,-73.23
             */

            String res = response.body().string();
            if (res == null || !res.contains(":") || !res.contains(",")) {
                return null;
            }

            String[] users = res.split(";");

            for (String user : users) {
                // The name tag of a location should appear before a ":"
                String[] data = user.split(":");
                String name = data[0];

                if (data[1] != null) {
                    // The coordinates should follow, separated by a ","
                    String[] coords = data[1].split(",");
                    try {
                        Double lat = Double.parseDouble(coords[0]);
                        Double lng = Double.parseDouble(coords[1]);
                        LatLng location = new LatLng(lat, lng);
                        result.add(new Pair<>(name, location));
                    } catch (Exception e) {
                        //Log.e(TAG, "getOtherUsers: Could not parse user coordintates. Skipping...");
                    }
                }
            }
        } catch (IOException e) {
            //Log.e(TAG, "getTargetLocation: DID NOT USE SERVER LOCATION");
            //Log.e(TAG, "uploadFile: Error - " + e.getMessage());
        }
        
        return result;
    }
}
