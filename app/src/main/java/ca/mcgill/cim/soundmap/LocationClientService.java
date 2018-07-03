package ca.mcgill.cim.soundmap;

import android.util.Log;
import android.util.Pair;

import com.google.android.gms.maps.model.LatLng;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LocationClientService {

    private static final String TAG = "LocationClientService";

    private static final String PING_URL =
            "http://sandeepmanjanna.dlinkddns.com:5000/";

    private static final String LOCATION_HOST_URL =
            "http://sandeepmanjanna.dlinkddns.com:5000/location";

    private static final String USERS_HOST_URL =
            "http://sandeepmanjanna.dlinkddns.com:5000/users";

    OkHttpClient mClient;
    private final LatLng mDefaultLocation = new LatLng(45.504812985241564, -73.57715606689453);

    public LocationClientService() {
        mClient = new OkHttpClient();
    }

    public boolean checkConnection() {
        Runtime runtime = Runtime.getRuntime();
        try {
            Process  ping = runtime.exec("/system/bin/ping -c " + PING_URL);
            int res = ping.waitFor();
            if (res == 0) {
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "checkConnection: Error - " + e.toString());
        }

        return false;
    }

    public Pair<String, LatLng> getTargetLocation(LatLng userLocation) {
        Request request = new Request.Builder()
                .url(LOCATION_HOST_URL)
                .header("lat", Double.toString(userLocation.latitude))
                .header("lng", Double.toString(userLocation.longitude))
                .build();

        // Post the Request using the OkHttp Client
        try {
            Log.d(TAG, "getTargetLocation: attempting get request to server");
            Response response = mClient.newCall(request).execute();

            /**
             * Example Response:
             *      McGill:45.504812985241564,-73.57715606689453
             */

            String res = response.body().string();
            if (res == null || !res.contains(":") || !res.contains(",")) {
                return new Pair<>("DEFAULT", mDefaultLocation);
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
                    return new Pair<>(tag, location);
                } catch (Exception e) {
                    Log.e(TAG, "getTargetLocation: Could not parse coordinates");
                    return new Pair<>("DEFAULT", mDefaultLocation);
                }
            } else {
                Log.e(TAG, "getTargetLocation: Could not parse coordinates");
                return new Pair<>("DEFAULT", mDefaultLocation);
            }
        } catch (IOException e) {
            Log.e(TAG, "getTargetLocation: DID NOT USE SERVER LOCATION");
            Log.e(TAG, "uploadFile: Error - " + e.getMessage());
            return new Pair<>("DEFAULT", mDefaultLocation);
        }
    }

    public List<Pair<String, LatLng>> getOtherUsers() {
        // TODO : This will be a heavy computation for lots of users, but should be fine for
        // the assumed scale at this time.

        Request request = new Request.Builder()
                .url(USERS_HOST_URL)
                .build();

        // To store the users in parsed form
        List<Pair<String, LatLng>> result = new ArrayList<>();

        // Post the Request using the OkHttp Client
        try {
            Log.d(TAG, "getOtherUsers: attempting get request to server");
            Response response = mClient.newCall(request).execute();

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
                        result.add(new Pair<String, LatLng>(name, location));
                    } catch (Exception e) {
                        Log.e(TAG, "getOtherUsers: Could not parse user coordintates. Skipping...");
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "getTargetLocation: DID NOT USE SERVER LOCATION");
            Log.e(TAG, "uploadFile: Error - " + e.getMessage());
        }
        
        return result;
    }
}
