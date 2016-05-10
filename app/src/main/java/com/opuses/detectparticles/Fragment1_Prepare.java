package com.opuses.detectparticles;


import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

/**
 * Created by jingyuli on 1/4/16.
 */
public class Fragment1_Prepare extends Fragment {
    private final String TAG = "Fragment1_Prepare";

    private Button _doneCloseButton;
    private TextBlinker _textBlinker;

    private OnAttachmentClosedListener _listener;
    public interface OnAttachmentClosedListener {
        public void attachmentClosed();

        public boolean  getAttachmentClosed();
        public void     setAttachmentClosed(boolean b);

        public void  playNegativeTone();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            _listener = (OnAttachmentClosedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnAttachmentClosedListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment1, container, false);
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        _doneCloseButton = (Button)view.findViewById(R.id.button_done_close);
        _doneCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (_listener.getAttachmentClosed()) {
                    Log.d(TAG, "attachmentIsClosed");

                    _textBlinker.stopBlink();
                    _listener.attachmentClosed();

                } else {
                    Log.d(TAG, "attachmentIsOpened");

                    _listener.playNegativeTone();
                    _textBlinker.startBlink();
                }
            }
        });

        _textBlinker = new TextBlinker((TextView)view.findViewById(R.id.textView_instruction));

        /*
        For test
         */
        _closeToggleButton = (ToggleButton)view.findViewById(R.id.test_close_toggleButton);
        _closeToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean b = ((ToggleButton) v).isChecked() ? true : false;
                _listener.setAttachmentClosed(b);
            }
        });
    }

    /*
    For test use
     */
    private ToggleButton _closeToggleButton;

}

