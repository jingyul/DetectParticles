package com.opuses.detectparticles;


import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.MediaActionSound;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;



public class MainActivity extends AppCompatActivity
        implements
        Fragment0_Initial.OnStartListener,
        Fragment1_Prepare.OnAttachmentClosedListener,
        Fragment2_Camera.OnAttachmentOpenedListener,
        Fragment3_Result.OnTestListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private final String TAG = "MainActivity";

    private final MediaActionSound _mediaAudioSound = new MediaActionSound();

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private LatLng _latlng;

    // Location updates
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;
    private static int UPDATE_INTERVAL = 10000; // 10 sec
    private static int FATEST_INTERVAL = 5000; // 5 sec
    private static int DISPLACEMENT = 10; // 10 meters

    private boolean _attachmentClosed = false;

    public static CameraSettings _cameraSettings;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Showing Fragment0
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.your_placeholder, new Fragment0_Initial());
        ft.commit();

        //set location service
        setLocationService();

        // camera
        _cameraSettings = new CameraSettings();
        _cameraSettings.set_focus(Fragment2_Camera.DEFAULT_FOCUS_MM);
        _cameraSettings.set_exposure(Fragment2_Camera.DEFAULT_EXPOSURE);
        _cameraSettings.set_iso(Fragment2_Camera.DEFAULT_ISO);
        _cameraSettings.set_flash(Fragment2_Camera.DEFAULT_FLASH);
    }

    // on attachment closed, transition to the next fragment:Fragment2
    @Override
    public void attachmentClosed()
    {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.your_placeholder, new Fragment2_Camera());
        ft.commit();
    }

    // Start the test and stop reading GPS
    @Override
    public void goToResultScreen()
    {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.your_placeholder, new Fragment3_Result());
        ft.commit();
    }

    // listeners from Fragment3
    // cancel the test and go to Fragment1
    @Override
    public void testCanceled() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.your_placeholder, new Fragment0_Initial());
        ft.commit();
    }

    // restart the test by going to Fragment2
    @Override
    public void testRedo() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.your_placeholder, new Fragment2_Camera());
        ft.commit();
    }

    // after saved test, go to Fragment0
    @Override
    public void testSaved() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.your_placeholder, new Fragment0_Initial());
        ft.commit();
    }

    // request saved LatLng
    @Override
    public LatLng getLL() {
        if (_latlng == null) {
            return new LatLng(0, 0);
        }
        return _latlng;
    }

    // listeners from Fragment 4
    // stat the next test by going to Fragment1
    @Override
    public void nextTest() {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.your_placeholder, new Fragment1_Prepare());
        ft.commit();
    }

    // list all the test results
    @Override
    public void transferResults() {
        // list all the test results
        Intent intent = new Intent(getBaseContext(), ListActivity.class);
        startActivity(intent);
    }

    @Override
    public void masterKey() {
        // create the dialog
        final LinearLayout layout = new LinearLayout(this);
        LayoutInflater.from(this).inflate(R.layout.dialog_master_key, layout);

        final EditText masterkey = (EditText)layout.findViewById(R.id.textEdit_masterkey);
        final TextView textFeedback = (TextView)layout.findViewById(R.id.textView_feedback);
        textFeedback.setText(R.string.master_key);

        final AlertDialog b = new AlertDialog.Builder(this)
                .setPositiveButton("Ok", null)
                .setNegativeButton("Cancel", null)
                .setView(layout)
                .create();
        b.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        b.show();

        b.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (masterkey.getText().toString().equals("1234")) {
                    setCamera();
                    b.dismiss();
                } else {
                    textFeedback.setText(R.string.incorrect);
                }
            }
        });
    }

    private int focus_val       = 0;
    private Long exposure_val   = 0L;
    private int iso_val     = 0;
    private int flash_val       = 0;
    private boolean enterError = false;

    private void setCamera() {
        // create the dialog to enter settings
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_camera_settings, null);
        dialogBuilder.setView(dialogView);

        final EditText focus_ui = (EditText) dialogView.findViewById(R.id.textEdit_focus);
        focus_ui.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    focus_val = Integer.valueOf(focus_ui.getText().toString());
                    // check the data range
                        if (focus_val >= 100) {
                            return false;
                        } else {
                            focus_ui.getText().clear();
                        }
                    }
                    return true;
            }
        });


        final EditText exposure_ui = (EditText) dialogView.findViewById(R.id.textEdit_Exposure);
            exposure_ui.setOnEditorActionListener(new TextView.OnEditorActionListener()

            {
                @Override
                public boolean onEditorAction (TextView v,int actionId, KeyEvent event){
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    // check the data range
                    exposure_val = Long.valueOf(exposure_ui.getText().toString());
                    if (exposure_val >= 32000L && exposure_val <= 5000000000L) {
                        return false;
                    } else {
                        exposure_ui.getText().clear();
                    }
                }
                return true;
            }
        });

        final EditText iso_ui = (EditText) dialogView.findViewById(R.id.textEdit_ISO);
        iso_ui.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    // check the data range
                    iso_val = Integer.valueOf(iso_ui.getText().toString());
                    if (iso_val >= 50 && iso_val <= 1600) {
                        return false;
                    } else {
                        iso_ui.getText().clear();
                    }
                }
                return true;
            }
        });

        final ToggleButton flash = (ToggleButton) dialogView.findViewById(R.id.toggleButton_flash);
        flash.setText(R.string.flash_off);
        flash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (flash.isChecked()) {
                    flash.setText(R.string.flash_on);
                    flash_val = 1;
                } else {
                    flash_val = 0;
                }
            }
        });

        dialogBuilder.setTitle(R.string.set_camera);
        dialogBuilder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // share finalized camera setting to Pref
                _cameraSettings.set_focus(focus_val);
                _cameraSettings.set_exposure(exposure_val);
                _cameraSettings.set_iso(iso_val);
                _cameraSettings.set_flash(flash_val);
            }
        });
        dialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                //pass
            }
        });
        AlertDialog b = dialogBuilder.create();
        b.show();
    }

    //attachment is open/close state
    @Override
    public void setAttachmentClosed(boolean b) {
        _attachmentClosed = b;
    }

    @Override
    public boolean getAttachmentClosed() {
        return _attachmentClosed;
    }

    //LOCATION LOCATION
    public void setLocationService() {
        //called by MainActivity onCreate
        if (checkPlayServices()) {
            // Building the GoogleApi client
            buildGoogleApiClient();
            createLocationRequest();
        }
    }

    @Override
    public void startGPS() {
        //start GPS updating, called at entering test screen
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void stopGPS() {
        //stop GPS updating, called after a picture is taken
        mGoogleApiClient.disconnect();
    }

    /**
     * Method to display the location on UI
     * */
    public void getLocation() {
        // get location when the compartment is opened

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED) {
            Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (location != null) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();

                _latlng = new LatLng(location.getLatitude(), location.getLongitude());

            } else {

                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle("Alert!");
                alertDialog.setMessage("Please turn on Location in Settings!");
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), 0);
                            }
                        });
                alertDialog.show();
            }
        }
    }

    /**
     * Method to verify google play services on the device
     * */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil
                .isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Toast.makeText(getApplicationContext(),
                        "This device is not supported.", Toast.LENGTH_LONG)
                        .show();
                finish();
            }
            return false;
        }
        return true;
    }

    /**
     * Creating google api client object
     * */
    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API).build();
    }

    /**
     * Creating location request object
     * */
    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FATEST_INTERVAL);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setSmallestDisplacement(DISPLACEMENT);
    }

    @Override
    public void onConnected(Bundle connectionHint) {
            getLocation();
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }



    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = "
                + connectionResult.getErrorCode());
    }


    // Play Sound
    @Override
    public void playSound() {
        _mediaAudioSound.play(MediaActionSound.SHUTTER_CLICK);
    }

    @Override
    public void  playNegativeTone() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
