package com.arkanardiansyah.smartwalkingcane;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private FloatingActionButton fabHome;
    private static final String TAG = "MainActivity";

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
