package com.arkanardiansyah.smartwalkingcane; // GANTI DENGAN PACKAGE NAME ANDA

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.maps.model.LatLng;

// Helper class untuk status perangkat
class DeviceStatusUpdate { // Pastikan kelas ini ada
    private final boolean isOnline;
    private final String message;

    public DeviceStatusUpdate(boolean isOnline, String message) {
        this.isOnline = isOnline;
        this.message = message;
    }
    public boolean isOnline() { return isOnline; }
    public String getMessage() { return message; }
}


public class HomeViewModel extends AndroidViewModel {
    private static final String TAG = "HomeViewModel";
    private static final String PREFS_NAME = "SmartCaneAppPreferences_ViewModel_v1";
    private static final String KEY_IS_LIGHT_ON = "isLightOnState_v1";

    private final MutableLiveData<Boolean> _isLightOnLiveData;
    public LiveData<Boolean> getIsLightOnLiveData() { return _isLightOnLiveData; }

    private final MutableLiveData<LatLng> _gpsLocationLiveData = new MutableLiveData<>();
    public LiveData<LatLng> getGpsLocationLiveData() { return _gpsLocationLiveData; }

    private final MutableLiveData<Integer> _batteryLevelLiveData = new MutableLiveData<>();
    public LiveData<Integer> getBatteryLevelLiveData() { return _batteryLevelLiveData; }

    private final MutableLiveData<Boolean> _sosTriggerEvent = new MutableLiveData<>();
    public LiveData<Boolean> getSosTriggerEvent() { return _sosTriggerEvent; }

    private final MutableLiveData<Boolean> _mqttConnectionStatusLiveData = new MutableLiveData<>(false);
    public LiveData<Boolean> getMqttConnectionStatusLiveData() { return _mqttConnectionStatusLiveData; }

    private final MutableLiveData<DeviceStatusUpdate> _deviceOnlineStatusLiveData = new MutableLiveData<>();
    public LiveData<DeviceStatusUpdate> getDeviceOnlineStatusLiveData() { return _deviceOnlineStatusLiveData; }

    private final SharedPreferences sharedPreferences;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        _isLightOnLiveData = new MutableLiveData<>();
        sharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean initialLightState = sharedPreferences.getBoolean(KEY_IS_LIGHT_ON, false);
        _isLightOnLiveData.setValue(initialLightState);
        Log.d(TAG, "ViewModel Initialized. Light state from Prefs: " + initialLightState);
        _deviceOnlineStatusLiveData.setValue(new DeviceStatusUpdate(false, "Menghubungkan..."));
    }

    public void setLightState(boolean isOn) {
        Boolean currentVal = _isLightOnLiveData.getValue();
        if (currentVal == null || currentVal != isOn) {
            _isLightOnLiveData.postValue(isOn);
            sharedPreferences.edit().putBoolean(KEY_IS_LIGHT_ON, isOn).apply();
            Log.d(TAG, "VM Light state set to: " + isOn + " (saved)");
        }
    }
    public boolean getCurrentLightState() {
        Boolean currentVal = _isLightOnLiveData.getValue();
        return currentVal != null ? currentVal : false;
    }

    public void setGpsLocation(LatLng location) { _gpsLocationLiveData.postValue(location); }
    public void setBatteryLevel(int level) {
        Integer current = _batteryLevelLiveData.getValue();
        if(current == null || current != level) _batteryLevelLiveData.postValue(level);
    }
    public void triggerSosEvent(boolean isPressed) { _sosTriggerEvent.postValue(isPressed); }
    public void consumeSosEvent() { _sosTriggerEvent.postValue(null); }

    public void setMqttConnectionStatus(boolean isConnected) {
        Boolean current = _mqttConnectionStatusLiveData.getValue();
        if(current == null || current != isConnected) _mqttConnectionStatusLiveData.postValue(isConnected);
    }
    public void setDeviceOnlineStatus(boolean isOnline, String message) {
        DeviceStatusUpdate current = _deviceOnlineStatusLiveData.getValue();
        if(current == null || current.isOnline() != isOnline || !current.getMessage().equals(message)){
            _deviceOnlineStatusLiveData.postValue(new DeviceStatusUpdate(isOnline, message));
        }
    }

    /**
     * Gets the last known location from the GPS LiveData
     * @return The last known LatLng or null if not available
     */
    public LatLng getLastKnownLocation() {
        return _gpsLocationLiveData.getValue();
    }
    
    // Remove the duplicate field declaration:
    // private final MutableLiveData<LatLng> gpsLocationLiveData = new MutableLiveData<>();
}