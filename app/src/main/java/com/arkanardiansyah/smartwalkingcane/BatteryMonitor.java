package com.arkanardiansyah.smartwalkingcane;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

public class BatteryMonitor {
    private static final String TAG = "BatteryMonitor";

    private final Context context;
    private BatteryCallback callback;
    private BroadcastReceiver batteryReceiver;

    public interface BatteryCallback {
        void onBatteryLevelChanged(int level);
        void onBatteryLow(int level);
    }

    public BatteryMonitor(Context context, BatteryCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void startMonitoring() {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

                if (level != -1 && scale != -1) {
                    int batteryPct = (int) ((level / (float) scale) * 100);
                    callback.onBatteryLevelChanged(batteryPct);

                    if (batteryPct <= 20) {
                        callback.onBatteryLow(batteryPct);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(batteryReceiver, filter);
        Log.d(TAG, "Battery monitoring started");
    }

    public void stopMonitoring() {
        if (batteryReceiver != null) {
            context.unregisterReceiver(batteryReceiver);
            Log.d(TAG, "Battery monitoring stopped");
        }
    }

    public int getCurrentBatteryLevel() {
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, iFilter);

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            if (level != -1 && scale != -1) {
                return (int) ((level / (float) scale) * 100);
            }
        }

        return -1;
    }
}
