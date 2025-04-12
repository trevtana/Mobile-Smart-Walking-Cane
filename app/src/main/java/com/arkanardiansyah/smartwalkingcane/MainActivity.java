package com.arkanardiansyah.smartwalkingcane;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.arkanardiansyah.smartwalkingcane.fragment_home;
import com.arkanardiansyah.smartwalkingcane.fragment_profile;
import com.arkanardiansyah.smartwalkingcane.fragment_settings;


import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;
    private FloatingActionButton fabHome;

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

    private void setupNavigation() {
        // Set up bottom navigation listener
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

        // Set up FAB click listener
        fabHome.setOnClickListener(v -> {
            // Load home fragment
            loadFragment(new fragment_home());
            // Deselect all bottom navigation items
            bottomNavigationView.getMenu().setGroupCheckable(0, true, false);
            for (int i = 0; i < bottomNavigationView.getMenu().size(); i++) {
                bottomNavigationView.getMenu().getItem(i).setChecked(false);
            }
            bottomNavigationView.getMenu().setGroupCheckable(0, true, true);
        });
    }

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