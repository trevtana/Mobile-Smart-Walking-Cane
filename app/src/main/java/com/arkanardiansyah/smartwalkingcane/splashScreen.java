package com.arkanardiansyah.smartwalkingcane;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

public class splashScreen extends AppCompatActivity {

    private VideoView splashVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_splash_screen);

        splashVideo = findViewById(R.id.splashVideo);
        setupSplashVideo();
    }

    private void setupSplashVideo() {
        String videoPath = "android.resource://" + getPackageName() + "/" + R.raw.splash1;
        Uri videoUri = Uri.parse(videoPath);

        splashVideo.setVideoURI(videoUri);
        splashVideo.setOnPreparedListener(mp -> {
            splashVideo.start();
        });

        splashVideo.setOnCompletionListener(mp -> navigateToNextScreen());
        splashVideo.setOnErrorListener((mp, what, extra) -> {
            navigateToNextScreen();
            return true;
        });
    }

    private void navigateToNextScreen() {
        SharedPreferences preferences = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String productId = preferences.getString("product_id", null);

        Intent intent;
        if (productId == null || productId.isEmpty()) {
            intent = new Intent(splashScreen.this, product_id.class);
        } else {
            intent = new Intent(splashScreen.this, MainActivity.class);
        }
        startActivity(intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (splashVideo.isPlaying()) {
            splashVideo.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!splashVideo.isPlaying()) {
            splashVideo.start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (splashVideo != null) {
            splashVideo.stopPlayback();
        }
    }
}
