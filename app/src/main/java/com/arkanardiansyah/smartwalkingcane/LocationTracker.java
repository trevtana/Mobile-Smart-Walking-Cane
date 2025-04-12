package com.arkanardiansyah.smartwalkingcane;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class LocationTracker {
    private static final String TAG = "LocationTracker";
    private static final long MIN_TIME_BW_UPDATES = 1000; // 1 second
    private static final float MIN_DISTANCE_CHANGE_FOR_UPDATES = 5; // 5 meters

    private final Context context;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Location lastKnownLocation;
    private LocationCallback callback;

    public interface LocationCallback {
        void onLocationChanged(Location location);
        void onLocationError(String message);
    }

    public LocationTracker(Context context, LocationCallback callback) {
        this.context = context;
        this.callback = callback;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public void startTracking() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            callback.onLocationError("Location permission not granted");
            return;
        }

        try {
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    lastKnownLocation = location;
                    if (callback != null) {
                        callback.onLocationChanged(location);
                    }
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}

                @Override
                public void onProviderEnabled(String provider) {}

                @Override
                public void onProviderDisabled(String provider) {
                    callback.onLocationError("Location provider disabled");
                }
            };

            // Request location updates
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_BW_UPDATES,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES,
                    locationListener);

            Log.d(TAG, "Location tracking started");
        } catch (Exception e) {
            Log.e(TAG, "Error starting location tracking: " + e.getMessage());
            callback.onLocationError("Error starting location tracking");
        }
    }

    public void stopTracking() {
        if (locationManager != null && locationListener != null) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(locationListener);
            }
            Log.d(TAG, "Location tracking stopped");
        }
    }

    public Location getLastKnownLocation() {
        return lastKnownLocation;
    }
}
