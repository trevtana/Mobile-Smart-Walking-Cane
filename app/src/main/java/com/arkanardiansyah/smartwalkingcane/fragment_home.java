package com.arkanardiansyah.smartwalkingcane; // Pastikan package name ini sesuai

import android.app.Activity;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Tidak ada import MQTT client di sini

public class fragment_home extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "HomeFragment_UI";

    // UI Views
    private TextView deviceStatusTextView, batteryTextView, lampStatusTextView;
    private MaterialButton lightButton;
    private LottieAnimationView sosAnimation;
    private View rootView;

    // Google Maps
    private GoogleMap mMap;
    private Marker currentLocationMarker;

    // ViewModel (di-scope ke Activity)
    private HomeViewModel homeViewModel;

    // Handler utama untuk UI updates dari Fragment (jika diperlukan)
    private Handler mainHandler;


    public fragment_home() { /* Required empty public constructor */ }

    /**
     * Factory method untuk membuat instance baru dari fragment ini.
     */
    public static fragment_home newInstance() {
        fragment_home fragment = new fragment_home();
        // Jika ada argumen yang perlu di-pass, lakukan di sini
        // Bundle args = new Bundle();
        // fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Lifecycle: onCreate");
        mainHandler = new Handler(Looper.getMainLooper());

        // Dapatkan ViewModel yang di-scope ke Activity
        if (getActivity() != null) {
            homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        } else {
            Log.e(TAG, "onCreate: getActivity() is null, cannot initialize ViewModel! This may lead to NullPointerExceptions.");
            // Pertimbangkan untuk tidak melanjutkan jika ViewModel tidak bisa didapatkan,
            // atau siapkan penanganan null yang lebih robus.
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_home, container, false);
        Log.d(TAG, "Lifecycle: onCreateView (Fragment Root View created: " + (rootView != null) + ")");
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d(TAG, "Lifecycle: onViewCreated (Fragment)");
        
        // Initialize views first
        initViews(view);
        
        // Setup observers before map to ensure UI updates happen quickly
        setupObservers();
        
        // Setup button listeners
        setupButtonListeners();
        
        // Load map asynchronously - this can be heavy
        loadMapAsync();
    }

    private void loadMapAsync() {
        // Use post to move map loading off the immediate UI thread cycle
        mainHandler.post(() -> {
            if (isAdded() && getChildFragmentManager() != null) {
                SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map_view);
                if (mapFragment != null) {
                    mapFragment.getMapAsync(this);
                } else {
                    Log.e(TAG, "loadMapAsync: MapFragment is NULL! Check ID R.id.map_view in XML.");
                }
            }
        });
    }

    private void initViews(View view) {
        deviceStatusTextView = view.findViewById(R.id.text_status);
        batteryTextView = view.findViewById(R.id.text_battery);
        lampStatusTextView = view.findViewById(R.id.lamp_status);
        lightButton = view.findViewById(R.id.light_button);
        sosAnimation = view.findViewById(R.id.sos_animation);

        // Pengecekan null untuk debugging
        if (deviceStatusTextView == null) Log.e(TAG, "initViews: deviceStatusTextView is NULL! Check R.id.text_status.");
        if (batteryTextView == null) Log.e(TAG, "initViews: batteryTextView is NULL! Check R.id.text_battery.");
        if (lampStatusTextView == null) Log.e(TAG, "initViews: lampStatusTextView is NULL! Check R.id.lamp_status.");
        if (lightButton == null) Log.e(TAG, "initViews: lightButton is NULL! Check R.id.light_button.");
        if (sosAnimation == null) Log.e(TAG, "initViews: sosAnimation is NULL! Check R.id.sos_animation.");

        Log.d(TAG, "Fragment Views initialized successfully.");
    }

    private void setupButtonListeners() {
        if (lightButton == null) {
            Log.e(TAG, "setupButtonListeners: lightButton is null, cannot attach listener.");
            return;
        }
        lightButton.setOnClickListener(v -> {
            Log.d(TAG, "Fragment LightButton CLICKED!");
            Activity activity = getActivity();
            // Panggil metode di MainActivity untuk mengirim perintah lampu
            if (activity instanceof MainActivity) {
                if (homeViewModel != null) {
                    // State yang diinginkan adalah kebalikan dari state saat ini di ViewModel
                    boolean desiredLightState = !homeViewModel.getCurrentLightState();
                    Log.d(TAG, "Fragment: Calling MainActivity.sendLightCommandToDevice(" + desiredLightState + ")");
                    ((MainActivity) activity).sendLightCommandToDevice(desiredLightState);
                    // MainActivity akan update ViewModel (optimis), yang akan trigger LiveData observer di sini untuk update UI
                } else {
                    Log.e(TAG, "LightButton click: homeViewModel is null.");
                    showToast("Error: Tidak dapat mengakses state perangkat.");
                }
            } else {
                Log.e(TAG, "Cannot send light command: Fragment not attached to MainActivity or Activity is null.");
                showToast("Error: Operasi tidak dapat dilakukan saat ini.");
            }
        });
    }

    private void setupObservers() {
        if (homeViewModel == null) {
            Log.e(TAG, "setupObservers: homeViewModel is NULL! Cannot set observers. UI will not update from ViewModel.");
            // Set UI to default/error state
            if (isAdded() && getContext() != null) {
                updateDeviceStatusText(false, "Error: Data tidak tersedia");
                updateLightButtonUIFromState(false);
                if (batteryTextView!=null) batteryTextView.setText("Baterai: -");
            }
            return;
        }

        // Use a single observer pattern to reduce observer count
        homeViewModel.getIsLightOnLiveData().observe(getViewLifecycleOwner(), isOn -> {
            if (!isAdded()) return; // Skip if fragment is not attached
            
            Log.d(TAG, "Fragment LIVEDATA Observer for Light: state = " + (isOn != null ? isOn : "null_livedata"));
            if (isOn != null) {
                updateLightButtonUIFromState(isOn);
            } else {
                updateLightButtonUIFromState(false);
            }
        });

        // Other observers with added safety checks
        // Observe status koneksi MQTT dari ViewModel
        homeViewModel.getMqttConnectionStatusLiveData().observe(getViewLifecycleOwner(), isConnected -> {
            if (isConnected != null && isAdded()) {
                Log.d(TAG, "Fragment LIVEDATA Observer for MQTT Connection: " + isConnected);
                // Perbarui status UI yang bergantung pada koneksi MQTT jika perlu
                // (Saat ini, status perangkat keseluruhan yang lebih relevan)
            }
        });

        // Observe status perangkat online/offline dari ViewModel
        homeViewModel.getDeviceOnlineStatusLiveData().observe(getViewLifecycleOwner(), statusUpdate -> {
            if (statusUpdate != null && isAdded()) {
                Log.d(TAG, "Fragment LIVEDATA Observer for Device Status: online=" + statusUpdate.isOnline() + ", msg=" + statusUpdate.getMessage());
                updateDeviceStatusText(statusUpdate.isOnline(), statusUpdate.getMessage());
                if (lightButton != null) {
                    lightButton.setEnabled(statusUpdate.isOnline()); // Tombol lampu aktif jika perangkat online
                    lightButton.setAlpha(statusUpdate.isOnline() ? 1.0f : 0.6f);
                }
            }
        });

        // Observe data GPS dari ViewModel
        homeViewModel.getGpsLocationLiveData().observe(getViewLifecycleOwner(), latLng -> {
            if (latLng != null && mMap != null && isAdded()) { // Pastikan map juga sudah siap
                updateMap(latLng.latitude, latLng.longitude);
            }
        });

        // Observe data Baterai dari ViewModel
        homeViewModel.getBatteryLevelLiveData().observe(getViewLifecycleOwner(), level -> {
            if (level != null && batteryTextView != null && isAdded()) {
                batteryTextView.setText("Baterai: " + level + "%");
            }
        });

        // Observe event SOS dari ViewModel
        homeViewModel.getSosTriggerEvent().observe(getViewLifecycleOwner(), isPressed -> {
            // isPressed bisa null setelah di-consume
            if(isPressed != null && isAdded()){
                triggerSosVisualFeedback(isPressed);
                if (isPressed) { // Jika ini adalah event "pressed" baru
                    homeViewModel.consumeSosEvent(); // Penting: Reset event di ViewModel agar tidak ter-trigger lagi
                }
            }
        });
        Log.d(TAG, "All LiveData Observers set up.");
    }

    // Dipanggil oleh observer LiveData (getIsLightOnLiveData) atau saat UI perlu disinkronkan
    private void updateLightButtonUIFromState(boolean newLightState) {
        if (!isAdded() || getContext() == null || lightButton == null || lampStatusTextView == null) return;
        
        // Avoid posting to main thread if we're already on it
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateLightButtonUIDirect(newLightState);
        } else {
            mainHandler.post(() -> updateLightButtonUIDirect(newLightState));
        }
    }

    private void updateLightButtonUIDirect(boolean newLightState) {
        if (newLightState) {
            lightButton.setText("Matikan Lampu");
            lightButton.setBackgroundTintList(ContextCompat.getColorStateList(getContext(), R.color.alert_red));
            lampStatusTextView.setText("Lampu: ON");
        } else {
            lightButton.setText("Nyalakan Lampu");
            lightButton.setBackgroundTintList(ContextCompat.getColorStateList(getContext(), R.color.primary));
            lampStatusTextView.setText("Lampu: OFF");
        }
    }

    // Helper untuk update UI lampu berdasarkan state dari ViewModel, dipanggil saat init UI atau resume
    private void updateLightButtonUI() {
        if (homeViewModel != null) {
            // Ambil state terbaru dari ViewModel
            updateLightButtonUIFromState(homeViewModel.getCurrentLightState());
        } else if (isAdded()){
            Log.w(TAG, "updateLightButtonUI: homeViewModel is null, defaulting light UI to OFF.");
            updateLightButtonUIFromState(false); // State default jika ViewModel belum ada
        }
    }

    // Metode untuk update teks status perangkat utama di UI Fragment
    public void updateDeviceStatusText(boolean isOnline, String statusMessage) {
        if (!isAdded() || getContext() == null || deviceStatusTextView == null) return;
        mainHandler.post(() -> {
            deviceStatusTextView.setText(statusMessage);
            if(isOnline) {
                deviceStatusTextView.setTextColor(ContextCompat.getColor(getContext(), R.color.status_safe));
            } else {
                deviceStatusTextView.setTextColor(ContextCompat.getColor(getContext(), R.color.alert_red));
                // Reset UI elemen lain jika perangkat offline
                if (batteryTextView != null) batteryTextView.setText("Baterai: -");
                // Status lampu akan diupdate oleh LiveData dari ViewModel jika ViewModel juga diset offline
                // if (lampStatusTextView != null) lampStatusTextView.setText("Lampu: -");
                // Penanganan lightButton enable/disable dilakukan di observer DeviceOnlineStatusLiveData
            }
        });
    }

    private void triggerSosVisualFeedback(boolean pressed) {
        if (!isAdded() || getContext() == null || sosAnimation == null) return;
        mainHandler.post(() -> {
            if (pressed) {
                showToast("PERINGATAN SOS DITERIMA!");
                sosAnimation.setVisibility(View.VISIBLE);
                sosAnimation.playAnimation();
                // Handler untuk menyembunyikan animasi setelah beberapa detik
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (sosAnimation != null) sosAnimation.setVisibility(View.GONE);
                }, 7000); // Durasi animasi SOS ditampilkan
            } else {
                // Jika ada event 'released' dan animasi sedang berjalan, hentikan
                if (sosAnimation != null && sosAnimation.isAnimating()) {
                    sosAnimation.cancelAnimation();
                    sosAnimation.setVisibility(View.GONE);
                }
                Log.d(TAG, "SOS event 'released' received, animation (if any) handled.");
            }
        });
    }

    private void showToast(String message) {
        // Selalu cek getActivity() dan isAdded()
        Activity activity = getActivity();
        if (activity != null && isAdded()) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } else {
            Log.w(TAG, "showToast: Cannot show toast, activity or fragment context not available.");
        }
    }

    private void updateMap(double latitude, double longitude) {
        if (mMap == null || !isAdded()) return;
        LatLng gpsLocation = new LatLng(latitude, longitude);
        if (currentLocationMarker == null) {
            // Hanya buat marker baru jika benar-benar belum ada
            currentLocationMarker = mMap.addMarker(new MarkerOptions().position(gpsLocation).title("Posisi Tongkat"));
            // Zoom ke lokasi saat marker pertama kali dibuat
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(gpsLocation, 17));
        } else {
            currentLocationMarker.setPosition(gpsLocation);
            // Animasikan kamera untuk mengikuti posisi, pertahankan zoom atau gunakan zoom detail
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(gpsLocation, Math.max(mMap.getCameraPosition().zoom, 16f)));
        }
    }

    // Add this method to fix the "Cannot resolve method 'updateMarkerOnMap'" error
    private void updateMarkerOnMap(LatLng location) {
        if (mMap == null || !isAdded()) return;
        if (currentLocationMarker == null) {
            currentLocationMarker = mMap.addMarker(new MarkerOptions().position(location).title("Posisi Tongkat"));
        } else {
            currentLocationMarker.setPosition(location);
        }
    }

    // Fix the onMapReady method to properly handle UiSettings
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        
        // Initialize ExecutorService if null
        if (generalExecutorService == null) {
            generalExecutorService = Executors.newSingleThreadExecutor();
        }
        
        generalExecutorService.execute(() -> {
            // Post UI changes back to main thread
            mainHandler.post(() -> {
                if (isAdded() && getContext() != null) {
                    // Fix UiSettings usage
                    mMap.getUiSettings().setZoomControlsEnabled(true);
                    mMap.getUiSettings().setCompassEnabled(true);
                    
                    // Set initial camera position if we have a location
                    if (homeViewModel != null) {
                        // Use the correct method to get last location from ViewModel
                        LatLng lastLocation = homeViewModel.getGpsLocationLiveData().getValue();
                        if (lastLocation != null) {
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastLocation, 15f));
                            updateMarkerOnMap(lastLocation);
                        }
                    }
                }
            });
        });
    }

    // Change the field type from ScheduledExecutorService to ExecutorService
    private ExecutorService generalExecutorService;

    // Clean up resources properly
    @Override
    public void onDestroyView() {
        Log.i(TAG, "Lifecycle: onDestroyView (Fragment). Cleaning up Fragment's UI resources.");
        
        // Shutdown executor service
        if (generalExecutorService != null) {
            generalExecutorService.shutdown();
            generalExecutorService = null;
        }
        
        // Remove callbacks
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        
        // Null views
        rootView = null;
        mMap = null; // GoogleMap juga bisa menyebabkan leak jika tidak di-null-kan
        currentLocationMarker = null;
        deviceStatusTextView = null;
        batteryTextView = null;
        lampStatusTextView = null;
        lightButton = null;
        sosAnimation = null;
        Log.i(TAG, "onDestroyView (Fragment): UI resources have been nulled.");
        
        // Add the missing super call
        super.onDestroyView();
    }
}