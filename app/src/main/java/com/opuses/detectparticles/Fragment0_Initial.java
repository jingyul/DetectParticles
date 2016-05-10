package com.opuses.detectparticles;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public class Fragment0_Initial extends Fragment {
    private final String TAG = "Fragment0_Initial";

    private OnStartListener _listener;
    public interface OnStartListener {
        void nextTest();
        void transferResults();
        void masterKey();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            _listener = (OnStartListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnStartListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment0, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button detectParticlesButton = (Button)view.findViewById(R.id.button_detectParticles);
        detectParticlesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _listener.nextTest();
            }
        });

        Button transferButton = (Button)view.findViewById(R.id.button_transferResults);
        transferButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _listener.transferResults();
            }
        });

        Button settingButton = (Button)view.findViewById(R.id.button_settings);
        settingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                _listener.masterKey();
            }
        });


    }
}
