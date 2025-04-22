package com.arkanardiansyah.smartwalkingcane;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class fragment_home extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "HomeFragment";
    // Corrected host - make sure this is exactly the HiveMQ cloud instance address
    private static final String MQTT_HOST = "05e5e16897dd4ecaae26a5dbb5c5e2f8.s1.eu.hivemq.cloud";
    private static final String MQTT_USERNAME = "TestKit";  // Original username
    private static final String MQTT_PASSWORD = "TesKit123";
    private static final int MQTT_PORT = 8883;
    private static final int CONNECTION_TIMEOUT_MS = 30000;  // Increased to 30 seconds
    private static final int RECONNECT_DELAY_MS = 3000;      // Reduced to 3 seconds for faster reconnection
    private static final int KEEP_ALIVE_INTERVAL = 60;       // 60 seconds keep alive

    // Views
    private TextView statusTextView;
    private TextView batteryTextView;
    private TextView sosStatusTextView;
    private MaterialButton lightButton;
    private MaterialButton sosButton;
    private LottieAnimationView locationAnimation;
    private LottieAnimationView sosAnimation;
    private View rootView;

    // Maps
    private GoogleMap mMap;
    private Marker currentLocationMarker;
    
    // MQTT
    private Mqtt5AsyncClient mqttClient;
    private Handler mainHandler;
    private boolean isLightOn = false;
    private boolean isSosActive = false;
    private boolean reconnecting = false;
    private Handler reconnectHandler = new Handler(Looper.getMainLooper());
    private Runnable reconnectRunnable;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = Integer.MAX_VALUE; // Unlimited reconnection attempts
    private ScheduledExecutorService executorService;
    private AtomicBoolean connectionActive = new AtomicBoolean(false);
    private ScheduledExecutorService keepAliveExecutor;

    public fragment_home() {
        // Required empty public constructor
    }

    public static fragment_home newInstance() {
        return new fragment_home();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());
        executorService = Executors.newSingleThreadScheduledExecutor();
        keepAliveExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        rootView = inflater.inflate(R.layout.fragment_home, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize views
        initViews(view);
        
        // Setup map
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map_view);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
        
        // Initialize reconnect runnable
        reconnectRunnable = () -> {
            if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts++;
                Log.d(TAG, "Reconnect attempt " + reconnectAttempts);
                setupMqttClient();
            } else {
                Log.d(TAG, "Max reconnect attempts reached. Please restart app.");
                showToast("Gagal menghubungkan ke server MQTT setelah beberapa percobaan");
                reconnecting = false;
            }
        };
        
        // Setup MQTT client
        setupMqttClient();
        
        // Setup button listeners
        setupButtonListeners();
        
        // Start periodic connection check
        startConnectionMonitor();
    }

    private void startConnectionMonitor() {
        // Schedule a periodic task to check connection status and reconnect if needed
        keepAliveExecutor.scheduleAtFixedRate(() -> {
            if (mqttClient != null) {
                if (!mqttClient.getState().isConnected() && !reconnecting) {
                    Log.d(TAG, "Connection monitor detected disconnection, reconnecting...");
                    connectionActive.set(false);
                    scheduleReconnect();
                } else if (mqttClient.getState().isConnected() && !connectionActive.get()) {
                    connectionActive.set(true);
                    Log.d(TAG, "Connection monitor confirmed active connection");
                    // Resubscribe to topics when connection is restored
                    subscribeToTopics();
                }
            }
        }, 5, 10, TimeUnit.SECONDS); // Check every 10 seconds
    }

    private void initViews(View view) {
        statusTextView = view.findViewById(R.id.status_text);
        batteryTextView = view.findViewById(R.id.text_battery);
        sosStatusTextView = view.findViewById(R.id.sos_status);
        lightButton = view.findViewById(R.id.light_button);
        sosButton = view.findViewById(R.id.sos_button);
//        try {
//            locationAnimation = view.findViewById(R.id.location_animation);
//        } catch (Exception e) {
//            Log.d(TAG, "Location animation view not found");
//        }
        sosAnimation = view.findViewById(R.id.sos_animation);
    }

    private void setupButtonListeners() {
        // Light Button Toggle Setup
        lightButton.setOnClickListener(v -> {
            isLightOn = !isLightOn;
            String payload = "{\"state\": \"" + (isLightOn ? "on" : "off") + "\"}";
            
            publishMessage("esp32/light", payload);
            
            // Update button text and appearance
            updateLightButtonUI();
        });
        
        // SOS Button Toggle Setup
        sosButton.setOnClickListener(v -> {
            isSosActive = !isSosActive;
            String payload = "{\"sos\": \"" + (isSosActive ? "active" : "inactive") + "\"}";
            publishMessage("esp32/sos", payload);
            
            // Update UI
            updateSosUI();
        });
    }

    private void updateLightButtonUI() {
        if (isLightOn) {
            lightButton.setText("Matikan Lampu");
            lightButton.setBackgroundTintList(getResources().getColorStateList(R.color.alert_red));
        } else {
            lightButton.setText("Nyalakan Lampu");
            lightButton.setBackgroundTintList(getResources().getColorStateList(R.color.primary));
        }
    }

    private void updateSosUI() {
        if (isSosActive) {
            sosButton.setText("Nonaktifkan SOS");
            sosButton.setBackgroundTintList(getResources().getColorStateList(R.color.alert_red));
            sosAnimation.setVisibility(View.VISIBLE);
            sosStatusTextView.setText("SOS: Aktif");
        } else {
            sosButton.setText("SOS");
            sosButton.setBackgroundTintList(getResources().getColorStateList(R.color.accent));
            sosAnimation.setVisibility(View.GONE);
            sosStatusTextView.setText("SOS: Tidak Aktif");
        }
    }

    private void showSnackbar(String message) {
        if (getView() != null) {
            Snackbar.make(getView(), message, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void showToast(String message) {
        if (getActivity() != null) {
            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void logDebug(String text) {
        Log.d(TAG, text);
    }

    // Add timeout to CompletableFuture for API level < 31
    private <T> CompletableFuture<T> timeoutAfter(CompletableFuture<T> future, long timeout, TimeUnit unit) {
        CompletableFuture<T> result = new CompletableFuture<>();
        
        // Schedule a task to complete the future with a TimeoutException
        executorService.schedule(() -> {
            if (!future.isDone()) {
                result.completeExceptionally(new TimeoutException("Operation timed out after " + timeout + " " + unit.name().toLowerCase()));
            }
        }, timeout, unit);
        
        // When the original future completes, complete the result future
        future.whenComplete((value, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            } else {
                result.complete(value);
            }
        });
        
        return result;
    }

    private void publishMessage(String topic, String payload) {
        if (mqttClient != null && mqttClient.getState().isConnected()) {
            Log.d(TAG, "Publishing to " + topic + ": " + payload);
            
            mqttClient.publishWith()
                    .topic(topic)
                    .payload(payload.getBytes())
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .retain(true) // Enable retain flag to make message persistent
                    .send()
                    .whenComplete((publishResult, throwable) -> {
                        if (throwable != null) {
                            Log.e(TAG, "Failed to publish message", throwable);
                            String errorMsg = throwable.toString();
                            
                            // Handle potential disconnect
                            if (isConnectionLostError(throwable)) {
                                connectionActive.set(false);
                                scheduleReconnect();
                            }
                            
                            mainHandler.post(() -> {
                                showToast("Gagal mengirim perintah: " + errorMsg);
                            });
                        } else {
                            Log.d(TAG, "Message published successfully to " + topic);
                        }
                    });
        } else {
            Log.e(TAG, "Cannot publish - MQTT client not connected");
            showToast("Tidak dapat mengirim perintah: MQTT tidak terhubung");
            
            // Try to reconnect if not already reconnecting
            if (!reconnecting) {
                scheduleReconnect();
            }
        }
    }

    private boolean isConnectionLostError(Throwable throwable) {
        String message = throwable.toString();
        return message.contains("Connection closed") || 
               message.contains("Socket closed") ||
               message.contains("Connection reset") ||
               message.contains("timeout") ||
               message.contains("Timeout");
    }

    private void scheduleReconnect() {
        if (!reconnecting) {
            reconnecting = true;
            Log.d(TAG, "Scheduling reconnect in " + (RECONNECT_DELAY_MS/1000) + " seconds");
            
            // Clear any existing pending reconnects
            reconnectHandler.removeCallbacks(reconnectRunnable);
            reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS);
        }
    }

    // Set up MQTT client
    private void setupMqttClient() {
        try {
            // First, clean up any existing client
            if (mqttClient != null) {
                try {
                    if (mqttClient.getState().isConnected()) {
                        mqttClient.disconnect();
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Error disconnecting existing client", ex);
                }
            }
            
            // Generate a unique client ID based on timestamp
            String clientId = "AndroidClient-" + UUID.randomUUID().toString().substring(0, 8);
            Log.d(TAG, "Setting up MQTT with clientId: " + clientId);
            
            // Configure SSL properly for HiveMQ cloud with extended timeouts
            MqttClientSslConfig sslConfig = MqttClientSslConfig.builder()
                    .hostnameVerifier((hostname, session) -> true) // Accept all hostnames
                    .handshakeTimeout(15, TimeUnit.SECONDS) // Increased timeout for handshake to 15 seconds
                    .build();
            
            // Build the MQTT client with SSL/TLS and aggressive reconnect settings
            mqttClient = MqttClient.builder()
                    .useMqttVersion5()
                    .identifier(clientId)
                    .serverHost(MQTT_HOST)
                    .serverPort(MQTT_PORT)
                    .sslConfig(sslConfig)
                    .automaticReconnect()
                        .initialDelay(1, TimeUnit.SECONDS)
                        .maxDelay(5, TimeUnit.SECONDS) // Reduced max delay for faster reconnects
                        .applyAutomaticReconnect()
                    .buildAsync();

            Log.d(TAG, "Connecting to MQTT broker...");

            // Set up MQTT connection options with increased keep alive interval
            Mqtt5Connect connectOptions = Mqtt5Connect.builder()
                    .simpleAuth()
                    .username(MQTT_USERNAME)
                    .password(MQTT_PASSWORD.getBytes())
                    .applySimpleAuth()
                    .keepAlive(KEEP_ALIVE_INTERVAL) // 60 seconds keep alive
                    .cleanStart(false) // Use persistent session for better reliability
                    .sessionExpiryInterval(7200) // 2 hour session expiry
                    .build();

            // Connect to the broker with custom timeout that works on API level 24+
            CompletableFuture<Mqtt5ConnAck> connectionFuture = mqttClient.connect(connectOptions);
            
            // Add extended timeout to the connection future
            timeoutAfter(connectionFuture, CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .whenComplete((connAck, throwable) -> {
                        if (throwable != null) {
                            String errorMsg;
                            if (throwable instanceof TimeoutException) {
                                errorMsg = "Connection timed out after " + CONNECTION_TIMEOUT_MS + "ms";
                            } else {
                                errorMsg = throwable.toString();
                            }
                            
                            Log.e(TAG, "MQTT Connection failed: " + errorMsg, throwable);
                            connectionActive.set(false);
                            
                            mainHandler.post(() -> {
                                showToast("Koneksi MQTT gagal: " + errorMsg);
                            });

                            // Always schedule a reconnect, no matter what the error
                            scheduleReconnect();
                        } else {
                            Log.d(TAG, "MQTT Connected successfully!");
                            connectionActive.set(true);
                            reconnecting = false;
                            reconnectAttempts = 0;

                            mainHandler.post(() -> {
                                showToast("Terhubung ke MQTT broker");
                            });

                            subscribeToTopics();
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "MQTT Connection setup failed", e);
            connectionActive.set(false);
            
            // Schedule reconnect attempt
            scheduleReconnect();
        }
    }

    // Subscribe to topics from the MQTT broker
    private void subscribeToTopics() {
        if (mqttClient == null || !mqttClient.getState().isConnected()) {
            Log.e(TAG, "Cannot subscribe - MQTT client not connected");
            scheduleReconnect();
            return;
        }
        
        // List of topics to subscribe to
        String[] topics = {"esp32/gps", "esp32/battery", "esp32/status", "esp32/light", "esp32/sos"};
        
        for (String topic : topics) {
            Log.d(TAG, "Subscribing to topic: " + topic);
            
            try {
                mqttClient.subscribeWith()
                        .topicFilter(topic)
                        .qos(MqttQos.AT_LEAST_ONCE)
                        .noLocal(true) // Don't receive back messages we publish
                        .callback(publish -> processMessage(topic, publish))
                        .send()
                        .whenComplete((subAck, throwable) -> {
                            if (throwable != null) {
                                Log.e(TAG, "Failed to subscribe to " + topic + ": " + throwable.toString(), throwable);
                                
                                // Handle potential disconnect during subscription
                                if (isConnectionLostError(throwable)) {
                                    connectionActive.set(false);
                                    scheduleReconnect();
                                }
                            } else {
                                Log.d(TAG, "Subscribed to " + topic + " successfully");
                            }
                        });
            } catch (Exception e) {
                Log.e(TAG, "Error subscribing to topic: " + topic, e);
            }
        }
    }
    
    private void processMessage(String topic, Mqtt5Publish message) {
        String payload = new String(message.getPayloadAsBytes());
        Log.d(TAG, "Message received on " + topic + ": " + payload);
        
        // Process based on topic
        switch (topic) {
            case "esp32/gps":
                processGpsData(payload);
                break;
            case "esp32/battery":
                processBatteryData(payload);
                break;
            case "esp32/status":
                processStatusData(payload);
                break;
            case "esp32/light":
                processLightData(payload);
                break;
            case "esp32/sos":
                processSosData(payload);
                break;
        }
    }
    
    private void processGpsData(String payload) {
        try {
            // Parse latitude and longitude
            String latStr = payload.substring(payload.indexOf("latitude") + 10, payload.indexOf(","));
            String lonStr = payload.substring(payload.indexOf("longitude") + 11, payload.indexOf("}"));
            
            double latitude = Double.parseDouble(latStr.trim());
            double longitude = Double.parseDouble(lonStr.trim());
            
            Log.d(TAG, "GPS coordinates parsed: " + latitude + ", " + longitude);

            // Update the map with the received GPS coordinates on the main thread
            mainHandler.post(() -> {
                if (mMap != null) {
                    updateMap(latitude, longitude);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error parsing GPS data", e);
        }
    }
    
    private void processBatteryData(String payload) {
        try {
            // Parse battery level
            String batteryStr = payload.substring(payload.indexOf("battery") + 9, payload.indexOf("}"));
            int batteryLevel = Integer.parseInt(batteryStr.trim());
            Log.d(TAG, "Battery level parsed: " + batteryLevel + "%");
            
            // Update UI on main thread
            mainHandler.post(() -> {
                if (batteryTextView != null) {
                    batteryTextView.setText(batteryLevel + "%");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error parsing battery data", e);
        }
    }
    
    private void processStatusData(String payload) {
        try {
            // Parse status
            String status = payload.substring(payload.indexOf("status") + 9, payload.lastIndexOf("\""));
            Log.d(TAG, "Status parsed: " + status);
            
            // Update UI on main thread
            mainHandler.post(() -> {
                if (statusTextView != null) {
                    statusTextView.setText("Status " + (status.equals("Connected") ? "Aman" : "Peringatan"));
                    if (status.equals("Connected")) {
                        statusTextView.setTextColor(getResources().getColor(R.color.status_safe));
                    } else {
                        statusTextView.setTextColor(getResources().getColor(R.color.alert_red));
                    }
                }
                
                if (status.equals("Connected")) {
                    lightButton.setEnabled(true);
                    sosButton.setEnabled(true);
                } else {
                    lightButton.setEnabled(false);
                    sosButton.setEnabled(false);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error parsing status data", e);
        }
    }
    
    private void processLightData(String payload) {
        try {
            // Parse light state
            String state = payload.substring(payload.indexOf("state") + 8, payload.lastIndexOf("\""));
            Log.d(TAG, "Light state parsed: " + state);
            
            // Update UI on main thread
            mainHandler.post(() -> {
                isLightOn = state.equals("on");
                updateLightButtonUI();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error parsing light data", e);
        }
    }
    
    private void processSosData(String payload) {
        try {
            // Parse SOS state
            String sosState = payload.substring(payload.indexOf("sos") + 6, payload.lastIndexOf("\""));
            Log.d(TAG, "SOS state parsed: " + sosState);
            
            // Update UI on main thread
            mainHandler.post(() -> {
                isSosActive = sosState.equals("active");
                updateSosUI();
            });
        } catch (Exception e) {
            Log.e(TAG, "Error parsing SOS data", e);
        }
    }

    // Method to update the map with new GPS coordinates
    private void updateMap(double latitude, double longitude) {
        LatLng gpsLocation = new LatLng(latitude, longitude);

        // If we already have a marker, update its position
        if (currentLocationMarker != null) {
            currentLocationMarker.setPosition(gpsLocation);
        } else {
            // Otherwise create a new marker
            currentLocationMarker = mMap.addMarker(new MarkerOptions()
                    .position(gpsLocation)
                    .title("Smart Walking Cane"));
        }

        // Move the camera to the new location
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(gpsLocation, 17));
        
        // Hide location animation when we get real coordinates
        if (locationAnimation != null && locationAnimation.getVisibility() == View.VISIBLE) {
            locationAnimation.setVisibility(View.GONE);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Default location (Indonesia)
        LatLng defaultLocation = new LatLng(-6.9, 107.6);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15));
        
        // Add initial marker
        currentLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(defaultLocation)
                .title("Smart Walking Cane"));
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Check if we need to reconnect
        if (mqttClient == null || !mqttClient.getState().isConnected()) {
            Log.d(TAG, "Reconnecting on resume...");
            setupMqttClient();
        }
    }
    
    @Override
    public void onDestroyView() {
        if (reconnectHandler != null && reconnectRunnable != null) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
        }

        if (mqttClient != null && mqttClient.getState().isConnected()) {
            try {
                Log.d(TAG, "Disconnecting MQTT...");
                mqttClient.disconnect();
                Log.d(TAG, "MQTT disconnected");
            } catch (Exception e) {
                Log.e(TAG, "Error disconnecting MQTT", e);
            }
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        
        if (keepAliveExecutor != null && !keepAliveExecutor.isShutdown()) {
            keepAliveExecutor.shutdown();
        }
        
        super.onDestroyView();
    }
}
