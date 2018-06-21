package ca.mcgill.cim.soundmap;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.util.LinkedList;
import java.util.List;

public class Data {

    private class Sample {
        private int intensity;
        private LatLng location;

        private Sample(int intensity, LatLng location) {
            this.intensity = intensity;
            this.location = location;
        }

        private int getIntensity() {
            return this.intensity;
        }

        private LatLng getLocation() {
            return this.location;
        }
    }

    private static final String TAG = "Data";

    private static final double LAT_ACCEPT_THRESH = 0.1;
    private static final double LNG_ACCEPT_THRESH = 0.1;

    private List<Sample> set;
    private double avgLat;
    private double avgLng;

    public Data() {
        set = new LinkedList<>();
    }

    public void push(int intensity, LatLng location) {
        Sample sample = new Sample(intensity, location);
        this.set.add(sample);
    }

    public int size() {
        return set.size();
    }

    public boolean isValid() {
        // TODO
        return true;
    }

    private void updateAvgLocation() {
        // Get the average intensity of the data set.
        double sumLat = 0;
        double sumLng = 0;

        for (Sample sample : this.set) {
            sumLat += sample.getLocation().latitude;
            sumLng += sample.getLocation().longitude;
        }

        this.avgLat = sumLat / (double) this.set.size();
        this.avgLng = sumLng / (double) this.set.size();
    }

    public double getAverageIntensity() {
        int sum = 0;
        int nbRejected = 0;

        this.updateAvgLocation();

        for (Sample sample : this.set) {
            if ((Math.abs(this.avgLat - sample.getLocation().latitude) < LAT_ACCEPT_THRESH) &&
                (Math.abs(this.avgLng - sample.getLocation().longitude) < LNG_ACCEPT_THRESH)) {
                sum += sample.getIntensity();
            } else {
                nbRejected++;
            }
        }

        if (nbRejected > 0) {
            Log.w(TAG, "getAverageIntensity: Rejected samples - " +
                    Integer.toString(nbRejected));
        }

        return ( (double) sum / (double) this.set.size() );
    }

    public void clear() {
        this.set.clear();
        this.avgLat = 0.0;
        this.avgLng = 0.0;
    }

    public LatLng getAvgLocation() {
        return (new LatLng(this.avgLat, this.avgLng));
    }
}
