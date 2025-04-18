package com.arkanardiansyah.smartwalkingcane;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class product_id extends AppCompatActivity {
    private Button btnConnect;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_product_id);

        btnConnect = findViewById(R.id.btnConnect);
        btnConnect.setOnClickListener(v -> {
            Intent intent = new Intent(product_id.this, MainActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
