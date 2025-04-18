package com.arkanardiansyah.smartwalkingcane;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class product_id extends AppCompatActivity {

    private Button btnConnect;
    private EditText etProductId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_id);

        // Inisialisasi views
        btnConnect = findViewById(R.id.btnConnect);
        etProductId = findViewById(R.id.etProductId);

        // Set listener untuk button
        btnConnect.setOnClickListener(v -> {
            String productId = etProductId.getText().toString().trim();

            // Validasi jika input kosong
            if (productId.isEmpty()) {
                etProductId.setError("Product ID tidak boleh kosong");
                return;
            }

            // Referensi ke node "users" di Firebase Realtime Database
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users");

            // Mengecek apakah data dengan product_id tersebut ada
            ref.child(productId).get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    DataSnapshot snapshot = task.getResult();
                    if (snapshot.exists()) {
                        // Simpan product_id ke SharedPreferences
                        SharedPreferences sharedPreferences = getSharedPreferences("user_session", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("product_id", productId);
                        editor.apply();

                        // Ambil nama untuk feedback (opsional)
                        String name = snapshot.child("nama").getValue(String.class);
                        Toast.makeText(product_id.this, "Login berhasil sebagai: " + name, Toast.LENGTH_SHORT).show();

                        // Lanjutkan ke MainActivity
                        Intent intent = new Intent(product_id.this, MainActivity.class);
                        startActivity(intent);
                        finish();  // Menutup activity login
                    } else {
                        // Jika product_id tidak ditemukan
                        Toast.makeText(product_id.this, "Product ID tidak ditemukan", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Jika gagal mengakses database
                    Toast.makeText(product_id.this, "Gagal mengakses database", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}
