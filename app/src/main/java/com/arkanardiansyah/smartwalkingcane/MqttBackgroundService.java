package com.arkanardiansyah.smartwalkingcane;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.maps.model.LatLng;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MqttBackgroundService extends Service {

    private static final String TAG = "MqttBackgroundService";
    
    // MQTT Configuration
    private static final String MQTT_HOST = "05e5e16897dd4ecaae26a5dbb5c5e2f8.s1.eu.hivemq.cloud";
    private static final String MQTT_USERNAME = "TestKit";
    private static final String MQTT_PASSWORD = "TesKit123";
    private static final int MQTT_PORT = 8883;
    private static final int CONNECTION_TIMEOUT_MS = 20000;
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int KEEP_ALIVE_INTERVAL = 60;

    // Notification Configuration
    private static final String NOTIFICATION_CHANNEL_ID = "smart_cane_service_channel";
    private static final String SOS_NOTIFICATION_CHANNEL_ID = "smart_cane_sos_channel";
    private static final int FOREGROUND_SERVICE_NOTIFICATION_ID = 1;
    private static final int SOS_NOTIFICATION_ID = 2;

    // Vibration Configuration
    private static final long[] SOS_VIBRATION_PATTERN = {0, 500, 200, 500, 200, 500};
    private static final int SOS_VIBRATION_REPEAT = 3; // Will vibrate 3 times then stop
    private Vibrator vibrator;
    private Handler vibrationHandler;
    private Runnable stopVibrationRunnable;

    // MQTT Client and State
    private Mqtt5AsyncClient mqttClient;
    private String androidMqttClientId;
    private boolean isAttemptingMqttConnection = false;
    private Handler reconnectHandler;
    private Runnable reconnectRunnable;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private ScheduledExecutorService generalExecutorService;
    private AtomicBoolean mqttConnectionEstablished = new AtomicBoolean(false);
    private ScheduledExecutorService mqttMonitorExecutor;

    // Handler for main thread operations
    private Handler mainHandler;

    // Device Offline Detection
    private static final long DEVICE_OFFLINE_TIMEOUT_MS = 70000;
    private Handler deviceOfflineHandler;
    private Runnable deviceOfflineRunnable;
    private boolean isDeviceConsideredOnline = false;

    // Location tracking
    private LatLng lastKnownLocation;

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
    
        mainHandler = new Handler(Looper.getMainLooper());
        vibrationHandler = new Handler(Looper.getMainLooper());
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        stopVibrationRunnable = () -> {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.cancel();
            }
        };
    
        if (generalExecutorService == null || generalExecutorService.isShutdown()) {
            generalExecutorService = Executors.newSingleThreadScheduledExecutor();
        }
        if (mqttMonitorExecutor == null || mqttMonitorExecutor.isShutdown()) {
            mqttMonitorExecutor = Executors.newSingleThreadScheduledExecutor();
        }
    
        loadOrGenerateMqttClientId();
        createNotificationChannels();
    
        // Start as foreground service with notification
        startForeground(FOREGROUND_SERVICE_NOTIFICATION_ID, createServiceNotification());
    
        reconnectHandler = new Handler(Looper.getMainLooper());
        reconnectRunnable = () -> {
            if (mqttClient != null && mqttClient.getState() == MqttClientState.CONNECTED) {
                isAttemptingMqttConnection = false;
                mqttConnectionEstablished.set(true);
                reconnectAttempts = 0;
                subscribeToTopics();
                return;
            }
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++;
                Log.i(TAG, "MQTT Reconnect attempt " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS);
                setupMqttClient();
            } else {
                Log.e(TAG, "Max MQTT reconnect attempts reached.");
                isAttemptingMqttConnection = false;
            }
        };
    
        deviceOfflineHandler = new Handler(Looper.getMainLooper());
        deviceOfflineRunnable = () -> {
            Log.w(TAG, "DEVICE OFFLINE TIMEOUT in Service!");
            isDeviceConsideredOnline = false;
        };
    
        // Start as foreground service with notification
        startForeground(FOREGROUND_SERVICE_NOTIFICATION_ID, createServiceNotification());
    
        // Setup MQTT connection
        setupMqttClient();
        startMqttConnectionMonitor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        
        if (intent != null && "com.arkanardiansyah.smartwalkingcane.STOP_SOS_VIBRATION".equals(intent.getAction())) {
            // Stop vibration when notification is dismissed
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.cancel();
            }
            vibrationHandler.removeCallbacks(stopVibrationRunnable);
        }
        
        // If service gets killed, restart it
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // We don't provide binding
    }

    private void loadOrGenerateMqttClientId() {
        SharedPreferences prefs = getSharedPreferences("MqttAppClientPrefs_Service_v1", MODE_PRIVATE);
        androidMqttClientId = prefs.getString("androidMqttClientId_Service_v1", null);
        if (androidMqttClientId == null) {
            androidMqttClientId = "AndroidServiceClient-" + UUID.randomUUID().toString().substring(0, 16);
            prefs.edit().putString("androidMqttClientId_Service_v1", androidMqttClientId).apply();
            Log.d(TAG, "New persistent ClientID (Service): " + androidMqttClientId);
        } else {
            Log.d(TAG, "Persistent ClientID loaded (Service): " + androidMqttClientId);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = 
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            // Service notification channel (low importance)
            NotificationChannel serviceChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Smart Walking Cane Service",
                    NotificationManager.IMPORTANCE_LOW);
            serviceChannel.setDescription("Keeps the Smart Walking Cane app connected in background");
            notificationManager.createNotificationChannel(serviceChannel);

            // SOS notification channel (high importance)
            NotificationChannel sosChannel = new NotificationChannel(
                    SOS_NOTIFICATION_CHANNEL_ID,
                    "SOS Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            sosChannel.setDescription("Critical SOS alerts from the Smart Walking Cane");
            sosChannel.enableLights(true);
            sosChannel.setLightColor(Color.RED);
            sosChannel.enableVibration(true);
            sosChannel.setVibrationPattern(new long[]{0, 500, 200, 500});
            sosChannel.setBypassDnd(true);
            sosChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(sosChannel);
        }
    }

    private Notification createServiceNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Smart Walking Cane")
                .setContentText("Monitoring for SOS events")
                .setSmallIcon(R.drawable.alert)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void showSosNotification(LatLng location) {
        // Create an intent to open the app when notification is tapped
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        // Create a custom layout for the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, SOS_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.alert)
                .setContentTitle(getString(R.string.status_sos))
                .setContentText(getString(R.string.sos_active))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setDeleteIntent(createDeleteIntent()) // Add delete intent to handle notification dismissal
                .setContentIntent(pendingIntent)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(getString(R.string.sos_active) + "\n" + 
                                (location != null ? 
                                "Lokasi: " + location.latitude + ", " + location.longitude : 
                                getString(R.string.location_info))))
                .setColor(Color.RED);

        // Add respond action
        Intent respondIntent = new Intent(this, MainActivity.class);
        respondIntent.setAction("com.arkanardiansyah.smartwalkingcane.RESPOND_TO_SOS");
        respondIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent respondPendingIntent = PendingIntent.getActivity(
                this, 1, respondIntent, PendingIntent.FLAG_IMMUTABLE);
        builder.addAction(R.drawable.alert, getString(R.string.respond), respondPendingIntent);

        // Get notification manager and show notification
        NotificationManager notificationManager = 
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(SOS_NOTIFICATION_ID, builder.build());

        // Start vibration with limited repeats
        startSosVibration();
    }

    private PendingIntent createDeleteIntent() {
        Intent deleteIntent = new Intent(this, MqttBackgroundService.class);
        deleteIntent.setAction("com.arkanardiansyah.smartwalkingcane.STOP_SOS_VIBRATION");
        return PendingIntent.getService(this, 2, deleteIntent, PendingIntent.FLAG_IMMUTABLE);
    }

    private void startSosVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            // Cancel any existing vibration
            vibrator.cancel();
            
            // Schedule vibration to stop after pattern completes
            vibrationHandler.removeCallbacks(stopVibrationRunnable);
            vibrationHandler.postDelayed(stopVibrationRunnable, 
                    SOS_VIBRATION_PATTERN.length * SOS_VIBRATION_REPEAT * 2); // Multiply by 2 because pattern alternates

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        SOS_VIBRATION_PATTERN, SOS_VIBRATION_REPEAT));
            } else {
                vibrator.vibrate(SOS_VIBRATION_PATTERN, SOS_VIBRATION_REPEAT);
            }
        }
    }

    private void startMqttConnectionMonitor() {
        if (mqttMonitorExecutor == null || mqttMonitorExecutor.isShutdown() || mqttMonitorExecutor.isTerminated()) {
            Log.w(TAG, "MqttMonitorExecutor needs re-creation for monitor in Service.");
            mqttMonitorExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        Log.d(TAG, "Starting MQTT Connection Monitor in Service.");
        try {
            mqttMonitorExecutor.scheduleAtFixedRate(() -> {
                if (mqttClient != null) {
                    MqttClientState currentState = mqttClient.getState();
                    if (currentState != MqttClientState.CONNECTED &&
                            currentState != MqttClientState.CONNECTING &&
                            currentState != MqttClientState.CONNECTING_RECONNECT &&
                            !isAttemptingMqttConnection) {
                        Log.w(TAG, "MQTT Monitor (Service): Disconnected (State: "+ currentState +"). Scheduling manual reconnect.");
                        mqttConnectionEstablished.set(false);
                        scheduleManualReconnect();
                    }
                } else if (!isAttemptingMqttConnection) {
                    Log.w(TAG, "MQTT Monitor (Service): mqttClient is null & not connecting. Scheduling setup.");
                    scheduleManualReconnect();
                }
            }, 10, 20, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.e(TAG, "MQTT Monitor (Service) start failed.", e);
        }
    }

    private void stopMqttConnectionMonitor() {
        Log.d(TAG, "Stopping MQTT Connection Monitor in Service.");
        if (mqttMonitorExecutor != null && !mqttMonitorExecutor.isShutdown()) {
            mqttMonitorExecutor.shutdownNow();
        }
    }

    private void scheduleManualReconnect() {
        if (!isAttemptingMqttConnection) {
            isAttemptingMqttConnection = true;
            Log.i(TAG, "Scheduling MANUAL MQTT reconnect (Service). Current attempts: " + reconnectAttempts);
            reconnectHandler.removeCallbacks(reconnectRunnable);
            reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
        } else {
            Log.d(TAG, "Manual reconnect ALREADY in progress/scheduled (Service).");
        }
    }

    private void setupMqttClient() {
        Log.i(TAG, "setupMqttClient in Service (isAttemptingConnection=true)");
        isAttemptingMqttConnection = true;

        try {
            if (mqttClient != null) {
                if (mqttClient.getState() != MqttClientState.DISCONNECTED) {
                    try { mqttClient.disconnect().get(1, TimeUnit.SECONDS); }
                    catch (Exception e) { Log.e(TAG, "Err disconnecting old client (Service).", e); }
                }
                mqttClient = null;
            }

            MqttClientSslConfig sslConfig = MqttClientSslConfig.builder().hostnameVerifier((h,s)->true).handshakeTimeout(20, TimeUnit.SECONDS).build();
            if (generalExecutorService == null || generalExecutorService.isShutdown()) {
                generalExecutorService = Executors.newSingleThreadScheduledExecutor();
            }

            mqttClient = MqttClient.builder().useMqttVersion5().identifier(androidMqttClientId)
                    .serverHost(MQTT_HOST).serverPort(MQTT_PORT).sslConfig(sslConfig)
                    .transportConfig().mqttConnectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS).applyTransportConfig()
                    .buildAsync();

            Mqtt5Connect connectOptions = Mqtt5Connect.builder()
                    .simpleAuth().username(MQTT_USERNAME).password(MQTT_PASSWORD.getBytes()).applySimpleAuth()
                    .keepAlive(KEEP_ALIVE_INTERVAL).cleanStart(false).sessionExpiryInterval(300).build();

            Log.d(TAG, "MQTT (Service): Attempting connect with cleanStart=" + connectOptions.isCleanStart() + ", sessionExpiry=" + connectOptions.getSessionExpiryInterval());
            CompletableFuture<Mqtt5ConnAck> connFuture = mqttClient.connect(connectOptions);

            connFuture.whenCompleteAsync((connAck, throwable) -> {
                mainHandler.post(() -> {
                    isAttemptingMqttConnection = false;
                    if (throwable != null) {
                        String errMsg; Throwable rc = getRootCause(throwable);
                        if (rc instanceof TimeoutException) errMsg = "Koneksi timeout";
                        else errMsg = rc.getMessage()!=null ? rc.getMessage() : "Error koneksi MQTT";
                        Log.e(TAG, "MQTT Connection FAILED (Service): " + errMsg, rc);
                        mqttConnectionEstablished.set(false);
                    } else {
                        if (connAck.getReasonCode() == Mqtt5ConnAckReasonCode.SUCCESS) {
                            Log.i(TAG, "MQTT Connected SUCCESSFULLY (Service)! ConnAck: " + connAck.getReasonCode() + ", Session Present: " + connAck.isSessionPresent());
                            mqttConnectionEstablished.set(true);
                            reconnectAttempts = 0;
                            subscribeToTopics();
                        } else {
                            Log.e(TAG, "MQTT Connection REJECTED (Service). Reason: " + connAck.getReasonCode() + (connAck.getReasonString().isPresent() ? ", Info: " + connAck.getReasonString().get() : ""));
                            mqttConnectionEstablished.set(false);
                        }
                    }
                });
            }, generalExecutorService);
        } catch (Exception e) {
            Log.e(TAG, "General Exception during MQTT client setup (Service)", e);
            mqttConnectionEstablished.set(false);
            isAttemptingMqttConnection = false;
        }
    }

    private void subscribeToTopics() {
        if (mqttClient == null || mqttClient.getState() != MqttClientState.CONNECTED) {
            Log.e(TAG, "Cannot subscribe (Service) - MQTT not connected. State: " + (mqttClient != null ? mqttClient.getState() : "null"));
            if (!isAttemptingMqttConnection) scheduleManualReconnect();
            return;
        }
        Log.i(TAG, "Subscribing to topics from Service: [esp32/gps, esp32/sos_event]");
        String[] topics = {"esp32/gps", "esp32/sos_event"};
        for (String topic : topics) {
            final String currentTopic = topic;
            try {
                mqttClient.subscribeWith().topicFilter(currentTopic).qos(MqttQos.AT_LEAST_ONCE)
                        .callback(publish -> {
                            mainHandler.post(() -> processMqttMessage(currentTopic, publish));
                        })
                        .send()
                        .whenCompleteAsync((subAck, throwable) -> {
                            if (throwable != null) {
                                Log.e(TAG, "Failed to subscribe to " + currentTopic +" (Service)", throwable);
                                if (isConnectionLostError(throwable)) { mqttConnectionEstablished.set(false); }
                            } else {
                                boolean errorsInSubAck = subAck.getReasonCodes().stream().anyMatch(Mqtt5SubAckReasonCode::isError);
                                if (errorsInSubAck) Log.e(TAG, "Subscribe to " + currentTopic + " FAILED (Service). SubAck: " + subAck.getReasonCodes());
                                else Log.i(TAG, "Subscribed to " + currentTopic + " OK (Service). SubAck: " + subAck.getReasonCodes());
                            }
                        }, generalExecutorService);
            } catch (Exception e) { Log.e(TAG, "Exception creating subscription (Service) for " + currentTopic, e); }
        }
    }

    private void processMqttMessage(String topic, Mqtt5Publish publish) {
        // Move JSON parsing to a background thread to avoid main thread work
        generalExecutorService.execute(() -> {
            String payload = new String(publish.getPayloadAsBytes());
            Log.d(TAG, "Service processMqttMessage on " + topic + ": " + payload.substring(0, Math.min(payload.length(),60)));
            resetEsp32OfflineTimer();
    
            try {
                JSONObject jsonPayload = new JSONObject(payload);
                
                // Post UI updates back to main thread
                mainHandler.post(() -> {
                    try {
                        switch (topic) {
                            case "esp32/gps":
                                double latitude = jsonPayload.getDouble("latitude");
                                double longitude = jsonPayload.getDouble("longitude");
                                lastKnownLocation = new LatLng(latitude, longitude);
                                break;
                            case "esp32/sos_event":
                                String eventType = jsonPayload.getString("sos_event");
                                if (eventType.equals("pressed")) {
                                    // Show notification with vibration for SOS event
                                    showSosNotification(lastKnownLocation);
                                }
                                break;
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "JSON parsing error in main thread", e);
                    }
                });
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing MQTT message JSON", e);
            }
        });
    }

    private void resetEsp32OfflineTimer() {
        mainHandler.post(() -> {
            if (!isDeviceConsideredOnline || !mqttConnectionEstablished.get()) {
                Log.i(TAG, "Data from ESP32. Device ONLINE (Service). MQTT Est: " + mqttConnectionEstablished.get());
            }
            isDeviceConsideredOnline = true;

            deviceOfflineHandler.removeCallbacks(deviceOfflineRunnable);
            deviceOfflineHandler.postDelayed(deviceOfflineRunnable, DEVICE_OFFLINE_TIMEOUT_MS);
        });
    }

    private Throwable getRootCause(Throwable throwable) {
        if (throwable == null) return new Throwable("Unknown error (throwable was null)");
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause;
    }

    private boolean isConnectionLostError(Throwable throwable) {
        if (throwable == null) return false;
        Throwable rootCause = getRootCause(throwable);
        String message = rootCause.getMessage() != null ? rootCause.getMessage().toLowerCase() : "";
        return message.contains("not connected") || message.contains("connection closed") ||
                message.contains("connection lost") || message.contains("socket closed") ||
                message.contains("connection reset") || (rootCause instanceof TimeoutException) ||
                message.contains("sslhandshakeexception");
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Service onDestroy: Cleaning ALL resources.");
        super.onDestroy();

        // Stop vibration if still running
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.cancel();
        }
        if (vibrationHandler != null) {
            vibrationHandler.removeCallbacksAndMessages(null);
        }

        stopMqttConnectionMonitor();
        if (deviceOfflineHandler != null) deviceOfflineHandler.removeCallbacksAndMessages(null);
        if (reconnectHandler != null) reconnectHandler.removeCallbacksAndMessages(null);
        if (mainHandler != null) mainHandler.removeCallbacksAndMessages(null);

        if (mqttClient != null) {
            Log.i(TAG, "Disconnecting MQTT client (Service onDestroy). State: " + mqttClient.getState());
            try { mqttClient.disconnect(); }
            catch (Exception e) { Log.e(TAG, "Exception disconnecting MQTT (Service onDestroy)", e); }
            finally { mqttClient = null; }
        }

        shutdownExecutor(generalExecutorService, "generalExecutorService_Service");
        generalExecutorService = null;
        shutdownExecutor(mqttMonitorExecutor, "mqttMonitorExecutor_Service");
        mqttMonitorExecutor = null;

        Log.i(TAG, "Service onDestroy: Cleanup complete.");
    }

    private void shutdownExecutor(ScheduledExecutorService executor, String name) {
        if (executor != null && !executor.isShutdown()) {
            Log.d(TAG, "Shutting down executor: " + name);
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(1500, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, name + " did not terminate promptly.");
                } else { Log.d(TAG, name + " terminated successfully."); }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}