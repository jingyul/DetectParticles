package com.opuses.detectparticles;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Fragment3_Result extends Fragment {
    private final String TAG = "Fragment3_Result";

    private TextView _textTime;
    private TextView _textLongitude, _textLatitude;
    private EditText _editText_reference_reading;
    private EditText _editText_comments;

    private Button _cancelButton;
    private Button _redoButton;
    private Button _saveButton;

    private String _referenceReading = "";
    private String _comments = "";
    private String _timestamp;
    private String _dngFilename = "";
    private TextBlinker _textBlinker;


    private OnTestListener _listener;
    public interface OnTestListener {
        void testCanceled();
        void testRedo();
        void testSaved();
        LatLng getLL();

        void playNegativeTone();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            _listener = (OnTestListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnTestListener");
        }
    }


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment3, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //display date time
        Date date = Fragment2_Camera.getTestTimeStamp();
        SimpleDateFormat simpleDateFormat1 = new SimpleDateFormat("yyyy/MM/dd  HH:mm:ss");
        SimpleDateFormat simpleDateFormat2 = new SimpleDateFormat("yyMMdd_HHmmss");
        String time = simpleDateFormat1.format(date);
        _timestamp = simpleDateFormat2.format(date);

        _textTime = (TextView)view.findViewById(R.id.textViewTime);
        _textTime.setText(time);


        // display location info here
        _textLatitude = (TextView)view.findViewById(R.id.text_latitude);
        _textLongitude = (TextView)view.findViewById(R.id.text_longitude);
        _textLatitude.setText(String.valueOf((_listener.getLL()).latitude));
        _textLongitude.setText(String.valueOf((_listener.getLL()).longitude));

        _editText_reference_reading = (EditText)view.findViewById(R.id.editText_other_reading);
        _editText_reference_reading.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    _referenceReading = _editText_reference_reading.getText().toString();
                    if (!_referenceReading.isEmpty()) {
                        _textBlinker.stopBlink();
                    }
                    Log.d(TAG, "DustTrak " + _referenceReading);
                }
                return false;
            }
        });

        _editText_comments = (EditText)view.findViewById(R.id.editText_comments);
        _editText_comments.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    _comments = _editText_comments.getText().toString();
                    Log.d(TAG, "Comments " + _comments);
                }
                return false;
            }
        });

        _cancelButton = (Button)view.findViewById(R.id.button_back);
        _cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // remove the image file
                File dngFile = new File(_dngFilename);
                dngFile.delete();

                // transfer to the initial screen
                _listener.testCanceled();
            }
        });


        _redoButton = (Button)view.findViewById(R.id.button_redo);
        _redoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // remove the image file
                File dngFile = new File(_dngFilename);
                dngFile.delete();

                // transfer to the test(camera) screen
                _listener.testRedo();
            }
        });


        _saveButton = (Button)view.findViewById(R.id.button_save);
        _saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((_editText_reference_reading.getText().toString()).isEmpty()) {
                    _listener.playNegativeTone();
                    _textBlinker.startBlink();
                } else {
                    addAResultRow(v);
                    _listener.testSaved();
                }
            }
        });

        _dngFilename = Fragment2_Camera.get_dngFilename();

        _textBlinker = new TextBlinker((TextView)view.findViewById(R.id.enter_other_reading));

        TextView camera = (TextView)view.findViewById(R.id.textView_camera);
        camera.setText(
                String.valueOf(MainActivity._cameraSettings.get_focus()) + "/" +
                String.valueOf(MainActivity._cameraSettings.get_exposure()) + "/" +
                String.valueOf(MainActivity._cameraSettings.get_iso()) + "/" +
                String.valueOf(MainActivity._cameraSettings.get_flash())
        );
    }


    public void addAResultRow(View view) {
        // write all the info to a file
        File infoFile = new File(Environment.
                getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                _timestamp + ".txt");
        writeInfoFile(infoFile);

        //store info to content provider
        ContentValues values = new ContentValues();
        values.put(ResultDB.KEY_TRANSFERRED, " ");
        values.put(ResultDB.KEY_TIMESTAMP, _textTime.getText().toString());
        values.put(ResultDB.KEY_LONGITUDE, (_listener.getLL()).longitude);
        values.put(ResultDB.KEY_LATITUDE, (_listener.getLL()).latitude);
        values.put(ResultDB.KEY_REFERENCE, _referenceReading);
        values.put(ResultDB.KEY_COMMENTS, _comments);
        values.put(ResultDB.KEY_INFO_FILENAME, infoFile.toString());
        values.put(ResultDB.KEY_DNG_FILENAME, _dngFilename);

        Context context = getActivity().getBaseContext();
        Uri uri = context.getContentResolver().insert(ResultContentProvider.CONTENT_URI, values);

        if (uri != null ) {
            Toast.makeText(context, uri.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    private void writeInfoFile(@NonNull File infoFile) {
        FileOutputStream fileOut = null;
        try {
            fileOut = new FileOutputStream(infoFile);

            String logStr = "";

            String newLine = "\n\r";
            String s = "IMAGE FILE: " + _dngFilename;
            fileOut.write(s.getBytes());
            fileOut.write(newLine.getBytes());

            logStr += s + " ";

            s = "LATITUDE, LONGTITUDE: " +
                    _textLatitude.getText() + ", " + _textLongitude.getText();
            fileOut.write(s.getBytes());
            fileOut.write(newLine.getBytes());

            logStr += s + " ";

            s = "\nREFERENCE: " + _editText_reference_reading.getText();
            fileOut.write(s.getBytes());
            fileOut.write(newLine.getBytes());

            logStr += s + " ";

            s = "\nCOMMENTS: " + _editText_comments.getText();
            fileOut.write(s.getBytes());
            fileOut.write(newLine.getBytes());

            s = "\nCAMERA SETTINGS: " +
                " focus="    + String.valueOf(MainActivity._cameraSettings.get_focus()) +
                " exposure=" + String.valueOf(MainActivity._cameraSettings.get_exposure()) +
                " iso="      + String.valueOf(MainActivity._cameraSettings.get_iso()) +
                " flash="    + String.valueOf(MainActivity._cameraSettings.get_flash());
            fileOut.write(s.getBytes());
            fileOut.write(newLine.getBytes());

            Log.d(TAG, logStr);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != fileOut) {
                try {
                    fileOut.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

