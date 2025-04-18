package com.arkanardiansyah.smartwalkingcane;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class fragment_profile extends Fragment {

    private Button editProfileButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        editProfileButton = view.findViewById(R.id.edit_profile_button);

        editProfileButton.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), editprofile.class);
            startActivity(intent);
        });

        return view;
    }
}