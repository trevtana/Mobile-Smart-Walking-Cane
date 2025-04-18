package com.arkanardiansyah.smartwalkingcane;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import com.google.firebase.database.ValueEventListener;

public class fragment_settings extends Fragment {

    private TextView deviceNameTextView;
    private DatabaseReference databaseReference;
    private String productId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        deviceNameTextView = view.findViewById(R.id.device_name_value);

        // Ambil product_id dari SharedPreferences
        SharedPreferences sharedPreferences = requireActivity().getSharedPreferences("user_session", Context.MODE_PRIVATE);
        productId = sharedPreferences.getString("product_id", null);

        if (productId != null) {
            // Referensi ke Firebase
            databaseReference = FirebaseDatabase.getInstance().getReference("users").child(productId);
            loadProductName();
        } else {
            Toast.makeText(getActivity(), "Product ID tidak ditemukan", Toast.LENGTH_SHORT).show();
        }

        return view;
    }

    private void loadProductName() {
        databaseReference.child("product_name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String productName = snapshot.getValue(String.class);
                if (productName != null) {
                    deviceNameTextView.setText(productName);
                } else {
                    deviceNameTextView.setText("Tidak tersedia");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getActivity(), "Gagal memuat nama perangkat: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
