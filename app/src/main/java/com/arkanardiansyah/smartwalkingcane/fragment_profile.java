package com.arkanardiansyah.smartwalkingcane;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class fragment_profile extends Fragment {

    private ShapeableImageView profileImage;
    private TextView profileName, profileEmail, profilePhone, userAgeValue, userGenderValue, userHeightValue;
    private Button editProfileButton;

    private DatabaseReference userRef;
    private String productId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        profileImage = view.findViewById(R.id.profile_image);
        profileName = view.findViewById(R.id.profile_name);
        profileEmail = view.findViewById(R.id.profile_email);
        profilePhone = view.findViewById(R.id.profile_phone);
        userAgeValue = view.findViewById(R.id.user_age_value);
        userGenderValue = view.findViewById(R.id.user_gender_value);
        userHeightValue = view.findViewById(R.id.user_height_value);
        editProfileButton = view.findViewById(R.id.edit_profile_button);

        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("user_session", Context.MODE_PRIVATE);
        productId = sharedPreferences.getString("product_id", "");

        userRef = FirebaseDatabase.getInstance().getReference("users").child(productId);

        loadUserData();

        editProfileButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), editprofile.class);
            startActivity(intent);
        });

        return view;
    }

    private void loadUserData() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String nama = snapshot.child("nama").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    String noTelp = snapshot.child("no_telp").getValue(String.class);
                    String gender = snapshot.child("gender").getValue(String.class);
                    String tinggiBadan = snapshot.child("tinggi_badan").getValue(String.class);
                    // Mengambil usia sebagai String, bukan Long
                    String usia = snapshot.child("usia").getValue(String.class);
                    String fotoProfil = snapshot.child("foto_profil").getValue(String.class);

                    profileName.setText(nama != null ? nama : "-");
                    profileEmail.setText(email != null ? email : "-");
                    profilePhone.setText(formatPhoneNumber(noTelp));
                    userGenderValue.setText(gender != null ? gender : "-");
                    userHeightValue.setText((tinggiBadan != null ? tinggiBadan : "-") + " cm");
                    // Menampilkan usia sebagai String
                    userAgeValue.setText((usia != null ? usia : "-") + " tahun");

                    if (fotoProfil != null && !fotoProfil.isEmpty()) {
                        Glide.with(requireContext()).load(fotoProfil).into(profileImage);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Gagal memuat data. Kesalahan: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }


    // Method untuk format nomor telepon
    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return "-";
        }

        // Ubah 0 di depan jadi +62
        if (phoneNumber.startsWith("0")) {
            phoneNumber = "+62" + phoneNumber.substring(1);
        }

        // Hapus semua spasi/tanda khusus
        phoneNumber = phoneNumber.replaceAll("[^\\d+]", "");

        // Format: +62 XXX XXXX XXXX
        if (phoneNumber.startsWith("+62") && phoneNumber.length() > 8) {
            String formatted = phoneNumber.substring(0, 3); // +62
            String remaining = phoneNumber.substring(3);

            if (remaining.length() >= 9) {
                // Misal: 81234567890 -> 812 3456 7890
                formatted += " " + remaining.substring(0, 3) + " " + remaining.substring(3, 7) + " " + remaining.substring(7);
            } else if (remaining.length() >= 7) {
                formatted += " " + remaining.substring(0, 3) + " " + remaining.substring(3, 7) + " " + remaining.substring(7);
            } else {
                formatted += " " + remaining;
            }

            return formatted;
        }

        return phoneNumber; // fallback kalau format tidak sesuai
    }

}
