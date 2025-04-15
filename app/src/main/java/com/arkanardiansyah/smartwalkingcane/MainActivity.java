package com.arkanardiansyah.smartwalkingcane;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private FloatingActionButton fabHome;
    private MqttClient mqttClient;
    private GoogleMap mMap;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        fabHome = findViewById(R.id.fab_home);

        // Set up navigation
        setupNavigation();

        // Load default fragment (Home)
        loadFragment(new fragment_home());

        // Set up MQTT client with SSL/TLS
        String brokerUrl = "ssl://c38289aa538e486fb241fd7f0df4da8d.s1.eu.hivemq.cloud:8883"; // HiveMQ broker with SSL
        try {
            mqttClient = new MqttClient(brokerUrl, MqttClient.generateClientId(), new MemoryPersistence());
            mqttClient.connect();

            // Subscribe to the topic where the ESP32 sends GPS data
            mqttClient.subscribe("esp32/gps", new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    // Parse the received message (GPS coordinates)
                    String payload = new String(message.getPayload());
                    String[] coordinates = payload.replace("{\"latitude\":", "").replace("}", "").split(",");
                    double latitude = Double.parseDouble(coordinates[0].trim());
                    double longitude = Double.parseDouble(coordinates[1].trim());

                    // Update the map with the received GPS coordinates
                    updateMap(latitude, longitude);
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    // Method to update the map with new GPS coordinates
    private void updateMap(double latitude, double longitude) {
        LatLng gpsLocation = new LatLng(latitude, longitude);

        // Add a marker on the map for the received GPS location
        mMap.addMarker(new MarkerOptions().position(gpsLocation).title("GPS Location"));

        // Move the camera to the new location
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(gpsLocation, 15));
    }

    // Setup bottom navigation to switch between fragments
    private void setupNavigation() {
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            Fragment fragment = null;

            int itemId = item.getItemId();
            if (itemId == R.id.navigation_profile) {
                fragment = new fragment_profile();
            } else if (itemId == R.id.navigation_settings) {
                fragment = new fragment_settings();
            }

            return loadFragment(fragment);
        });

        // Setup FAB to return to the home fragment
        fabHome.setOnClickListener(v -> {
            loadFragment(new fragment_home());
            bottomNavigationView.getMenu().setGroupCheckable(0, true, false);
            for (int i = 0; i < bottomNavigationView.getMenu().size(); i++) {
                bottomNavigationView.getMenu().getItem(i).setChecked(false);
            }
            bottomNavigationView.getMenu().setGroupCheckable(0, true, true);
        });
    }

    // Method to load a fragment dynamically
    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.frame_layout, fragment)
                    .commit();
            return true;
        }
        return false;
    }
}
