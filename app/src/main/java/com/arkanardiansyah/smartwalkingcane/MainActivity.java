package com.arkanardiansyah.smartwalkingcane;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private FloatingActionButton fabHome;

    private static final String TAG = "MainActivity_MQTT";
    // ... (Konstanta MQTT lainnya tetap sama)
    private static final String MQTT_HOST = "05e5e16897dd4ecaae26a5dbb5c5e2f8.s1.eu.hivemq.cloud";
    private static final String MQTT_USERNAME = "TestKit";
    private static final String MQTT_PASSWORD = "TesKit123";
    private static final int MQTT_PORT = 8883;
    private static final int CONNECTION_TIMEOUT_MS = 20000;
    private static final int RECONNECT_DELAY_MS = 5000;
    private static final int KEEP_ALIVE_INTERVAL = 60;


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

    // ViewModel
    private HomeViewModel homeViewModel;

    // Handler utama
    private Handler mainHandler;

    // Device Offline Detection
    private static final long DEVICE_OFFLINE_TIMEOUT_MS_ACTIVITY = 70000;
    private Handler deviceOfflineHandlerActivity;
    private Runnable deviceOfflineRunnableActivity;
    private boolean isDeviceConsideredOnlineActivity = false;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "Lifecycle: onCreate");
        
        // Start the background service for SOS notifications
        startMqttBackgroundService();

        mainHandler = new Handler(Looper.getMainLooper());

        if (generalExecutorService == null || generalExecutorService.isShutdown()) {
            generalExecutorService = Executors.newSingleThreadScheduledExecutor();
        }
        if (mqttMonitorExecutor == null || mqttMonitorExecutor.isShutdown()) {
            mqttMonitorExecutor = Executors.newSingleThreadScheduledExecutor();
        }

        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        Log.d(TAG, "MainActivity onCreate: ViewModel Light state (initial): " + homeViewModel.getCurrentLightState());

        loadOrGenerateMqttClientId();

        reconnectHandler = new Handler(Looper.getMainLooper());
        reconnectRunnable = () -> {
            if (isDestroyed() || isFinishing()) { isAttemptingMqttConnection = false; return; }
            if(mqttClient != null && mqttClient.getState() == MqttClientState.CONNECTED) {
                isAttemptingMqttConnection = false; mqttConnectionEstablished.set(true); reconnectAttempts = 0;
                homeViewModel.setMqttConnectionStatus(true);
                subscribeToTopics(); return;
            }
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++;
                Log.i(TAG, "MQTT Reconnect attempt " + reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS);
                setupMqttClient();
            } else {
                Log.e(TAG, "Max MQTT reconnect attempts reached.");
                Toast.makeText(this,"Gagal total menghubungkan ke server.", Toast.LENGTH_LONG).show();
                isAttemptingMqttConnection = false;
                homeViewModel.setMqttConnectionStatus(false);
                homeViewModel.setDeviceOnlineStatus(false,"Gagal konek server");
            }
        };

        deviceOfflineHandlerActivity = new Handler(Looper.getMainLooper());
        deviceOfflineRunnableActivity = () -> {
            if (isDestroyed() || isFinishing()) return;
            Log.w(TAG, "DEVICE OFFLINE TIMEOUT in Activity!");
            isDeviceConsideredOnlineActivity = false;
            homeViewModel.setDeviceOnlineStatus(false, "Perangkat Offline (Timeout)");
        };

        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        fabHome = findViewById(R.id.fab_home);
        setupNavigation();
        if (savedInstanceState == null) {
            loadFragment(fragment_home.newInstance());
        }

        if (mqttClient == null || mqttClient.getState() != MqttClientState.CONNECTED) {
            Log.i(TAG, "onCreate: Initial MQTT setup call.");
            setupMqttClient();
        }
    }

    private void loadOrGenerateMqttClientId() {
        SharedPreferences prefs = getSharedPreferences("MqttAppClientPrefs_MainAct_v1", MODE_PRIVATE); // Tambah versi
        androidMqttClientId = prefs.getString("androidMqttClientId_MainAct_v1", null);
        if (androidMqttClientId == null) {
            androidMqttClientId = "AndroidActClient-" + UUID.randomUUID().toString().substring(0, 16); // Sedikit lebih panjang
            prefs.edit().putString("androidMqttClientId_MainAct_v1", androidMqttClientId).apply();
            Log.d(TAG, "New persistent ClientID (Activity): " + androidMqttClientId);
        } else {
            Log.d(TAG, "Persistent ClientID loaded (Activity): " + androidMqttClientId);
        }
    }

    private void setupNavigation() {
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();

            if (itemId == R.id.navigation_profile) {
                selectedFragment = new fragment_profile();
            } else if (itemId == R.id.navigation_settings) {
                selectedFragment = new fragment_settings();
            }
            // TIDAK ADA CASE UNTUK HOME DI SINI JIKA HOME DITANGANI FAB

            return loadFragment(selectedFragment);
        });

        // Setup FAB to return to the home fragment
        fabHome.setOnClickListener(v -> {
            Log.d(TAG, "FAB Home clicked, loading fragment_home.");
            loadFragment(fragment_home.newInstance()); // Selalu buat instance baru fragment_home
            // Kosongkan seleksi pada BottomNavigationView jika FAB ditekan
            // Ini agar tidak ada item di BottomNav yang terlihat aktif saat Home ditampilkan via FAB
            if (bottomNavigationView.getSelectedItemId() != 0) { // Cek jika ada yang terpilih
                bottomNavigationView.getMenu().findItem(bottomNavigationView.getSelectedItemId()).setChecked(false);
            }
        });
    }

    private boolean loadFragment(Fragment fragment) {
        if (fragment != null && !isFinishing() && !isDestroyed()) {
            Log.d(TAG, "Loading fragment: " + fragment.getClass().getSimpleName());
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.frame_layout, fragment)
                    .commitAllowingStateLoss();
            return true;
        }
        Log.w(TAG, "LoadFragment: Fragment was null or activity not in valid state.");
        return false;
    }

    /**
     * Metode publik yang dipanggil oleh Fragment untuk mengirim perintah lampu
     * melalui MQTT yang dikelola oleh Activity ini.
     * @param turnOn true jika lampu ingin dinyalakan, false jika ingin dimatikan.
     */
    public void sendLightCommandToDevice(boolean turnOn) {
        Log.d(TAG, "MainActivity: sendLightCommandToDevice called with turnOn = " + turnOn);
        if (!mqttConnectionEstablished.get()) {
            Toast.makeText(this, "Koneksi MQTT belum siap.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "sendLightCommandToDevice: MQTT not established. Command not sent.");
            if (!isAttemptingMqttConnection) { // Coba sambungkan lagi jika tidak sedang mencoba
                scheduleManualReconnect();
            }
            // Jangan langsung update ViewModel jika koneksi tidak ada, karena perintah tidak terkirim
            return;
        }

        // 1. Update ViewModel secara optimis SEKARANG.
        //    Fragment akan meng-observe perubahan ini dan memperbarui UI-nya.
        homeViewModel.setLightState(turnOn);
        Log.d(TAG, "sendLightCommandToDevice: ViewModel light state updated optimistically to " + turnOn);

        // 2. Siapkan dan kirim perintah MQTT
        String command = turnOn ? "on" : "off";
        String payload = "{\"state\": \"" + command + "\"}";
        publishMessage("android/light", payload, false); // Topic perintah ke ESP32 adalah "android/light"
        Log.i(TAG, "Light command ('" + command + "') published from MainActivity to android/light.");
    }


    private void startMqttConnectionMonitor() {
        if (isDestroyed() || isFinishing()) return;
        if (mqttMonitorExecutor == null || mqttMonitorExecutor.isShutdown() || mqttMonitorExecutor.isTerminated()) {
            Log.w(TAG, "MqttMonitorExecutor needs re-creation for monitor in Activity.");
            mqttMonitorExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        Log.d(TAG, "Starting MQTT Connection Monitor in MainActivity.");
        try {
            mqttMonitorExecutor.scheduleAtFixedRate(() -> {
                if (isDestroyed() || isFinishing()) return;
                if (mqttClient != null) {
                    MqttClientState currentState = mqttClient.getState();
                    if (currentState != MqttClientState.CONNECTED &&
                            currentState != MqttClientState.CONNECTING &&
                            currentState != MqttClientState.CONNECTING_RECONNECT &&
                            !isAttemptingMqttConnection) {
                        Log.w(TAG, "MQTT Monitor (Activity): Disconnected (State: "+ currentState +"). Scheduling manual reconnect.");
                        mqttConnectionEstablished.set(false);
                        homeViewModel.setMqttConnectionStatus(false);
                        scheduleManualReconnect();
                    }
                } else if (!isAttemptingMqttConnection) {
                    Log.w(TAG, "MQTT Monitor (Activity): mqttClient is null & not connecting. Scheduling setup.");
                    scheduleManualReconnect();
                }
            }, 10, 20, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.e(TAG, "MQTT Monitor (Activity) start failed.", e);
        }
    }

    private void stopMqttConnectionMonitor() {
        Log.d(TAG, "Stopping MQTT Connection Monitor in MainActivity.");
        if (mqttMonitorExecutor != null && !mqttMonitorExecutor.isShutdown()) {
            mqttMonitorExecutor.shutdownNow();
        }
    }

    private void scheduleManualReconnect() {
        if (isDestroyed() || isFinishing()) return;
        if (!isAttemptingMqttConnection) {
            isAttemptingMqttConnection = true;
            Log.i(TAG, "Scheduling MANUAL MQTT reconnect (Activity). Current attempts: " + reconnectAttempts);
            reconnectHandler.removeCallbacks(reconnectRunnable);
            reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
        } else {
            Log.d(TAG, "Manual reconnect ALREADY in progress/scheduled (Activity).");
        }
    }

    private void setupMqttClient() {
        if (isDestroyed() || isFinishing()) { isAttemptingMqttConnection = false; return; }
        Log.i(TAG, "setupMqttClient in MainActivity (isAttemptingConnection=true)");
        isAttemptingMqttConnection = true;

        try {
            if (mqttClient != null) {
                if (mqttClient.getState() != MqttClientState.DISCONNECTED) {
                    try { mqttClient.disconnect().get(1, TimeUnit.SECONDS); }
                    catch (Exception e) { Log.e(TAG, "Err disconnecting old client (Activity).", e); }
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

            Log.d(TAG, "MQTT (Activity): Attempting connect with cleanStart=" + connectOptions.isCleanStart() + ", sessionExpiry=" + connectOptions.getSessionExpiryInterval());
            CompletableFuture<Mqtt5ConnAck> connFuture = mqttClient.connect(connectOptions);

            connFuture.whenCompleteAsync((connAck, throwable) -> {
                if (isDestroyed() || isFinishing()) { isAttemptingMqttConnection = false; return; }
                mainHandler.post(() -> {
                    isAttemptingMqttConnection = false;
                    if (throwable != null) {
                        String errMsg; Throwable rc = getRootCause(throwable);
                        if (rc instanceof TimeoutException) errMsg = "Koneksi timeout";
                        else errMsg = rc.getMessage()!=null ? rc.getMessage() : "Error koneksi MQTT";
                        Log.e(TAG, "MQTT Connection FAILED (Activity): " + errMsg, rc);
                        mqttConnectionEstablished.set(false);
                        homeViewModel.setMqttConnectionStatus(false);
                        Toast.makeText(this, "MQTT Gagal: " + errMsg.substring(0, Math.min(errMsg.length(), 70)), Toast.LENGTH_LONG).show();
                    } else {
                        if (connAck.getReasonCode() == Mqtt5ConnAckReasonCode.SUCCESS) {
                            Log.i(TAG, "MQTT Connected SUCCESSFULLY (Activity)! ConnAck: " + connAck.getReasonCode() + ", Session Present: " + connAck.isSessionPresent());
                            mqttConnectionEstablished.set(true);
                            homeViewModel.setMqttConnectionStatus(true);
                            reconnectAttempts = 0;
                            Toast.makeText(this, "Terhubung ke Server MQTT", Toast.LENGTH_SHORT).show();
                            subscribeToTopics();
                        } else {
                            Log.e(TAG, "MQTT Connection REJECTED (Activity). Reason: " + connAck.getReasonCode() + (connAck.getReasonString().isPresent() ? ", Info: " + connAck.getReasonString().get() : ""));
                            mqttConnectionEstablished.set(false);
                            homeViewModel.setMqttConnectionStatus(false);
                            Toast.makeText(this, "MQTT Ditolak: " + connAck.getReasonCode(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }, generalExecutorService);
        } catch (Exception e) {
            Log.e(TAG, "General Exception during MQTT client setup (Activity)", e);
            mqttConnectionEstablished.set(false);
            homeViewModel.setMqttConnectionStatus(false);
            isAttemptingMqttConnection = false;
            if (!isDestroyed() && !isFinishing()) {
                mainHandler.post(() -> Toast.makeText(this, "MQTT Setup Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }

    private void subscribeToTopics() {
        if (mqttClient == null || mqttClient.getState() != MqttClientState.CONNECTED) {
            Log.e(TAG, "Cannot subscribe (Activity) - MQTT not connected. State: " + (mqttClient != null ? mqttClient.getState() : "null"));
            if (!isAttemptingMqttConnection && !isDestroyed() && !isFinishing()) scheduleManualReconnect();
            return;
        }
        Log.i(TAG, "Subscribing to topics from MainActivity: [esp32/gps, esp32/battery, esp32/light, esp32/sos_event]");
        String[] topics = {"esp32/gps", "esp32/battery", "esp32/light", "esp32/sos_event"};
        for (String topic : topics) {
            final String currentTopic = topic;
            try {
                mqttClient.subscribeWith().topicFilter(currentTopic).qos(MqttQos.AT_LEAST_ONCE)
                        .callback(publish -> {
                            if (isDestroyed() || isFinishing()) return;
                            mainHandler.post(() -> processMqttMessage(currentTopic, publish));
                        })
                        .send()
                        .whenCompleteAsync((subAck, throwable) -> {
                            if (isDestroyed() || isFinishing()) return;
                            if (throwable != null) {
                                Log.e(TAG, "Failed to subscribe to " + currentTopic +" (Activity)", throwable);
                                if (isConnectionLostError(throwable)) { mqttConnectionEstablished.set(false); homeViewModel.setMqttConnectionStatus(false); }
                            } else {
                                boolean errorsInSubAck = subAck.getReasonCodes().stream().anyMatch(Mqtt5SubAckReasonCode::isError);
                                if (errorsInSubAck) Log.e(TAG, "Subscribe to " + currentTopic + " FAILED (Activity). SubAck: " + subAck.getReasonCodes());
                                else Log.i(TAG, "Subscribed to " + currentTopic + " OK (Activity). SubAck: " + subAck.getReasonCodes());
                            }
                        }, generalExecutorService);
            } catch (Exception e) { Log.e(TAG, "Exception creating subscription (Activity) for " + currentTopic, e); }
        }
    }

    private void processMqttMessage(String topic, Mqtt5Publish publish) {
        if (isDestroyed() || isFinishing()) return;
        String payload = new String(publish.getPayloadAsBytes());
        Log.d(TAG, "MainActivity processMqttMessage on " + topic + ": " + payload.substring(0, Math.min(payload.length(),60)));
        resetEsp32OfflineTimerActivity();

        try {
            JSONObject jsonPayload = new JSONObject(payload);
            switch (topic) {
                case "esp32/light":
                    String stateFromEsp32 = jsonPayload.getString("state");
                    homeViewModel.setLightState(stateFromEsp32.equals("on"));
                    break;
                case "esp32/battery":
                    int batteryLevel = jsonPayload.getInt("battery");
                    homeViewModel.setBatteryLevel(batteryLevel);
                    break;
                case "esp32/gps":
                    double latitude = jsonPayload.getDouble("latitude");
                    double longitude = jsonPayload.getDouble("longitude");
                    homeViewModel.setGpsLocation(new LatLng(latitude, longitude));
                    // double hdop = jsonPayload.optDouble("hdop", 99.0);
                    // int satellites = jsonPayload.optInt("satellites", 0);
                    // homeViewModel.setGpsQuality(hdop, satellites); // Jika ada metode ini di ViewModel
                    break;
                case "esp32/sos_event":
                    String eventType = jsonPayload.getString("sos_event");
                    homeViewModel.triggerSosEvent(eventType.equals("pressed"));
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "MainActivity JSON error for " + topic + ": " + payload, e);
        }
    }

    private void resetEsp32OfflineTimerActivity() {
        mainHandler.post(() -> {
            if (!isDeviceConsideredOnlineActivity || !mqttConnectionEstablished.get()) {
                Log.i(TAG, "Data from ESP32. Device ONLINE (Activity). MQTT Est: " + mqttConnectionEstablished.get());
            }
            isDeviceConsideredOnlineActivity = true;
            if(mqttConnectionEstablished.get()){
                homeViewModel.setDeviceOnlineStatus(true, "Perangkat Terhubung");
            } else {
                homeViewModel.setDeviceOnlineStatus(false, "Menunggu koneksi server...");
            }

            deviceOfflineHandlerActivity.removeCallbacks(deviceOfflineRunnableActivity);
            deviceOfflineHandlerActivity.postDelayed(deviceOfflineRunnableActivity, DEVICE_OFFLINE_TIMEOUT_MS_ACTIVITY);
        });
    }

    public void publishMessage(String topic, String payload, boolean retain) {
        if (isDestroyed() || isFinishing()) return;
        if (mqttClient != null && mqttClient.getState() == MqttClientState.CONNECTED) {
            Log.d(TAG, "MainActivity Publishing to " + topic + ": " + payload.substring(0, Math.min(payload.length(),100)) + " (retain: " + retain + ")");
            mqttClient.publishWith()
                    .topic(topic).payload(payload.getBytes()).qos(MqttQos.AT_LEAST_ONCE).retain(retain)
                    .send()
                    .whenComplete((publishResult, throwable) -> {
                        if (isDestroyed() || isFinishing()) return;
                        if (throwable != null) {
                            Log.e(TAG, "Failed to publish to " + topic + " from MainActivity", throwable);
                            if (isConnectionLostError(throwable)) {
                                mqttConnectionEstablished.set(false); homeViewModel.setMqttConnectionStatus(false);
                                scheduleManualReconnect();
                            }
                        } else {
                            Log.d(TAG, "Message published to " + topic + " from MainActivity.");
                        }
                    });
        } else {
            Log.e(TAG, "Cannot publish from MainActivity - MQTT client state: " + (mqttClient != null ? mqttClient.getState() : "null"));
            Toast.makeText(this, "Gagal kirim: Server tidak terhubung.", Toast.LENGTH_SHORT).show();
            if (!isAttemptingMqttConnection) scheduleManualReconnect();
        }
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
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "Lifecycle: onResume (MainActivity). MQTT ConnEst: " + mqttConnectionEstablished.get() +
                ", DeviceOnlineByAct: " + isDeviceConsideredOnlineActivity);
        startMqttConnectionMonitor();

        if (mqttClient == null || mqttClient.getState() != MqttClientState.CONNECTED) {
            if (!isAttemptingMqttConnection) {
                Log.i(TAG, "MainActivity onResume: MQTT not connected. Scheduling reconnect.");
                homeViewModel.setMqttConnectionStatus(false);
                homeViewModel.setDeviceOnlineStatus(false, "Menyambungkan ke Server...");
                scheduleManualReconnect();
            }
        } else {
            mqttConnectionEstablished.set(true);
            homeViewModel.setMqttConnectionStatus(true);
            Log.i(TAG, "MainActivity onResume: MQTT already connected. Subscribing.");
            homeViewModel.setDeviceOnlineStatus(isDeviceConsideredOnlineActivity,
                    isDeviceConsideredOnlineActivity ? "Perangkat Terhubung" : "Sinkronisasi data...");
            subscribeToTopics();
        }
        if (isDeviceConsideredOnlineActivity && mqttConnectionEstablished.get()) {
            resetEsp32OfflineTimerActivity();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Lifecycle: onPause (MainActivity).");
        // Jika ingin sesi bertahan saat app di background sementara, jangan stop/disconnect di sini.
        // Jika ingin menghemat baterai secara agresif saat tidak di foreground:
        // stopMqttConnectionMonitor();
        // if (deviceOfflineHandlerActivity != null) deviceOfflineHandlerActivity.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "Lifecycle: onStop (MainActivity).");
        // Jika aplikasi benar-benar tidak terlihat (onStop), baru hentikan monitor dan timer.
        // Ini membantu menghemat baterai jika aplikasi lama di background.
        if (!isChangingConfigurations()) {
            Log.d(TAG, "onStop: Not changing config, stopping monitor and offline timer.");
            stopMqttConnectionMonitor();
            if (deviceOfflineHandlerActivity != null) deviceOfflineHandlerActivity.removeCallbacksAndMessages(null);
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "Lifecycle: onDestroy (MainActivity). Cleaning ALL resources.");
        super.onDestroy();

        stopMqttConnectionMonitor();
        if (deviceOfflineHandlerActivity != null) deviceOfflineHandlerActivity.removeCallbacksAndMessages(null);
        if (reconnectHandler != null) reconnectHandler.removeCallbacksAndMessages(null);
        if (mainHandler != null) mainHandler.removeCallbacksAndMessages(null);

        if (mqttClient != null) {
            Log.i(TAG, "Disconnecting MQTT client (MainActivity onDestroy). State: " + mqttClient.getState());
            try { mqttClient.disconnect(); }
            catch (Exception e) { Log.e(TAG, "Exception disconnecting MQTT (MainActivity onDestroy)", e); }
            finally { mqttClient = null; }
        }

        shutdownExecutor(generalExecutorService, "generalExecutorService_MA");
        generalExecutorService = null;
        shutdownExecutor(mqttMonitorExecutor, "mqttMonitorExecutor_MA");
        mqttMonitorExecutor = null;

        Log.i(TAG, "MainActivity onDestroy: Cleanup complete.");
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
    
    private void startMqttBackgroundService() {
        Intent serviceIntent = new Intent(this, MqttBackgroundService.class);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Log.d(TAG, "Started MqttBackgroundService for background SOS notifications");
    }
}