package com.arkanardiansyah.smartwalkingcane;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class editprofile extends AppCompatActivity {

    // Inisialisasi EditText, RadioGroup, dan Button
    EditText etNama, etEmail, etTelepon, etUsia, etTinggiBadan;
    RadioGroup genderRadioGroup;
    Button btnSimpan;

    DatabaseReference databaseReference;
    SharedPreferences sharedPreferences;
    String productId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_editprofile);

        // Menambahkan WindowInsetsListener untuk menyesuaikan padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Inisialisasi SharedPreferences untuk mengambil product_id
        sharedPreferences = getSharedPreferences("user_session", MODE_PRIVATE);
        productId = sharedPreferences.getString("product_id", null);

        // Pastikan productId ada
        if (productId == null) {
            Toast.makeText(this, "User ID tidak ditemukan", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Inisialisasi UI components
        etNama = findViewById(R.id.edit_name);  // Pastikan ID sesuai dengan layout
        etEmail = findViewById(R.id.edit_email);  // Pastikan ID sesuai dengan layout
        etTelepon = findViewById(R.id.edit_phone);  // Pastikan ID sesuai dengan layout
        etUsia = findViewById(R.id.edit_age);  // Pastikan ID sesuai dengan layout
        genderRadioGroup = findViewById(R.id.gender_radio_group);  // Mengakses RadioGroup untuk gender
        etTinggiBadan = findViewById(R.id.edit_height);  // Pastikan ID sesuai dengan layout
        btnSimpan = findViewById(R.id.save_button);  // Pastikan ID sesuai dengan layout

        // Referensi ke Firebase berdasarkan productId
        databaseReference = FirebaseDatabase.getInstance().getReference("users").child(productId);

        // Ambil data pengguna dari Firebase
        loadUserData();

        // Aksi ketika tombol Simpan ditekan
        btnSimpan.setOnClickListener(v -> saveUserData());

        // Logika tombol Kembali
        ImageButton backButton = findViewById(R.id.back_button);  // Pastikan ID sesuai dengan layout
        backButton.setOnClickListener(v -> onBackPressed());

    }

    private void loadUserData() {
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String nama = snapshot.child("nama").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    String telepon = snapshot.child("no_telp").getValue(String.class);

                    // Mengambil usia sebagai String dari Firebase
                    String usia = snapshot.child("usia").getValue(String.class);

                    String jenisKelamin = snapshot.child("jenis_kelamin").getValue(String.class);
                    String tinggiBadan = snapshot.child("tinggi_badan").getValue(String.class);

                    // Isi data ke dalam EditText
                    etNama.setText(nama);
                    etEmail.setText(email);
                    etTelepon.setText(telepon);
                    etUsia.setText(usia);  // Menampilkan usia dalam bentuk String
                    etTinggiBadan.setText(tinggiBadan);

                    // Set pilihan gender berdasarkan data dari Firebase
                    if (jenisKelamin != null) {
                        if (jenisKelamin.equals("Laki-laki")) {
                            genderRadioGroup.check(R.id.male_radio_button);  // ID RadioButton untuk Laki-laki
                        } else if (jenisKelamin.equals("Perempuan")) {
                            genderRadioGroup.check(R.id.female_radio_button);  // ID RadioButton untuk Perempuan
                        }
                    }
                } else {
                    Toast.makeText(editprofile.this, "Data tidak ditemukan", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(editprofile.this, "Gagal mengambil data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void saveUserData() {
        // Ambil data dari EditText
        String nama = etNama.getText().toString();
        String email = etEmail.getText().toString();
        String telepon = etTelepon.getText().toString();
        String usia = etUsia.getText().toString();
        String tinggiBadan = etTinggiBadan.getText().toString();

        // Mendapatkan pilihan gender dari RadioGroup
        int selectedGenderId = genderRadioGroup.getCheckedRadioButtonId();
        RadioButton selectedGenderButton = findViewById(selectedGenderId);
        String jenisKelamin = selectedGenderButton != null ? selectedGenderButton.getText().toString() : "";

        // Validasi jika ada field yang kosong
        if (nama.isEmpty() || email.isEmpty() || telepon.isEmpty() || usia.isEmpty() || jenisKelamin.isEmpty() || tinggiBadan.isEmpty()) {
            Toast.makeText(this, "Semua field harus diisi!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Update data pengguna di Firebase
        databaseReference.child("nama").setValue(nama);
        databaseReference.child("email").setValue(email);
        databaseReference.child("no_telp").setValue(telepon);
        databaseReference.child("usia").setValue(usia);
        databaseReference.child("jenis_kelamin").setValue(jenisKelamin);
        databaseReference.child("tinggi_badan").setValue(tinggiBadan);

        // Menampilkan pesan sukses
        Toast.makeText(this, "Data berhasil diperbarui", Toast.LENGTH_SHORT).show();

        // Redirect ke halaman profil setelah berhasil
        Intent intent = new Intent(editprofile.this, MainActivity.class);
        startActivity(intent);
        finish();  // Menutup activity editprofile
    }
}
