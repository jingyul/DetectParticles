package com.opuses.detectparticles;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

//DropBox==>
import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.session.AppKeyPair;
//<==

import java.io.File;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback {
    private static final String EXTRA_MAP_ID = "com.opuses.particleresults.map_id";
    private static final String TAG = "MapActivity";

    // UI Widgets
    private Button mDeleteResultButton;
    private Button mTransferResultButton;
    private TextView mTextView_Transferred;

    // Map
    private GoogleMap mMap;
    private LatLng mLatLng;

    //DropBox ==>
    private static final String APP_KEY = "vaw0bqgjyvcuugo";
    private static final String APP_SECRET = "vaw0bqgjyvcuugo";
    private static final String ACCOUNT_PREFS_NAME = "prefs";

    private static final String ACCESS_KEY_NAME = "ACCESS_KEY";
    private static final String ACCESS_SECRET_NAME = "ACCESS_SECRET";

    private final String DROPBOX_DIR = "/TestResults/";

    DropboxAPI<AndroidAuthSession> mApi;
    private boolean mIsAuthenticated = false;
    //<==

    // Database
    private String mRowId;
    private String mInfoFilename;
    private String mDngFilename;

    public static Intent newIntent(Context packageContext) {
        return new Intent(packageContext, MapActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //DropBox ==> create a new AuthSession so that we can use Dropbox API.
        AndroidAuthSession session = buildSession();
        mApi = new DropboxAPI<>(session);

        // receive the passing extras
        Bundle b = getIntent().getExtras();
        mRowId = b.getString("RowId");

        // retrieve this record
        String[] projection = {
                ResultDB.KEY_ROWID,
                ResultDB.KEY_REFERENCE,
                ResultDB.KEY_COMMENTS,
                ResultDB.KEY_LATITUDE,
                ResultDB.KEY_LONGITUDE,
                ResultDB.KEY_TRANSFERRED,
                ResultDB.KEY_INFO_FILENAME,
                ResultDB.KEY_DNG_FILENAME};

        Uri uri = Uri.parse(ResultContentProvider.CONTENT_URI + "/" + mRowId);
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);

        if (cursor != null) {
            cursor.moveToFirst();

            // LatLng for the Map display
            String latStr = cursor.getString(cursor.getColumnIndexOrThrow(ResultDB.KEY_LATITUDE));
            String lngStr = cursor.getString(cursor.getColumnIndexOrThrow(ResultDB.KEY_LONGITUDE));
            mLatLng = new LatLng(Double.parseDouble(latStr), Double.parseDouble(lngStr));

            // get the name of the info file to be transfered
            mInfoFilename = cursor.getString(cursor.getColumnIndexOrThrow(ResultDB.KEY_INFO_FILENAME));

            // get the name of the dng file to be transfered
            mDngFilename = cursor.getString(cursor.getColumnIndexOrThrow(ResultDB.KEY_DNG_FILENAME));

            // reference to disp/ay
            TextView textView_reference = (TextView) findViewById(R.id.textView_reference);
            textView_reference.setText(cursor.getString(cursor.getColumnIndexOrThrow(ResultDB.KEY_REFERENCE)));

            // Comments to display
            TextView textView_comments = (TextView) findViewById(R.id.textView_comments);
            textView_comments.setText(cursor.getString(cursor.getColumnIndexOrThrow(ResultDB.KEY_COMMENTS)));

            // transferred status
            String transferred = cursor.getString(cursor.getColumnIndexOrThrow(ResultDB.KEY_TRANSFERRED));
            mTextView_Transferred = (TextView) findViewById(R.id.textView_transfered);
            if (transferred.equals("*")) {
                mTextView_Transferred.setText(R.string.transfered);
            } else {
                mTextView_Transferred.setText(R.string.transfered_not);
            }


            // Delete this record from the DataBase, close the map view
            mDeleteResultButton = (Button) findViewById(R.id.button_delete_result);
            mDeleteResultButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    // create the dialog listener
                    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int choice) {
                            switch (choice) {
                                case DialogInterface.BUTTON_POSITIVE:
                                    Uri uri = Uri.parse(ResultContentProvider.CONTENT_URI + "/" + mRowId);
                                    getContentResolver().delete(uri, null, null);

                                    // delete the txt file
                                    File infoFile = new File(mInfoFilename);
                                    infoFile.delete();

                                    // delete the dng file
                                    File dngFile = new File(mDngFilename);
                                    dngFile.delete();

                                    finish();
                                    break;
                                case DialogInterface.BUTTON_NEGATIVE:
                                    break;
                            }
                        }
                    };

                    // create the dialog
                    AlertDialog.Builder builder = new AlertDialog.Builder(MapActivity.this);
                    builder.setMessage(R.string.delete_confirmation)
                            .setPositiveButton("Yes", dialogClickListener)
                            .setNegativeButton("No", dialogClickListener).show();

                }

            });


            // transfer this test result to DropBox
            mTransferResultButton = (Button) findViewById(R.id.button_transfer);
            mTransferResultButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    if (!mApi.getSession().authenticationSuccessful()) {

                        // create the dialog listener
                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int choice) {
                                switch (choice) {
                                    case DialogInterface.BUTTON_POSITIVE:
                                        mApi.getSession().startOAuth2Authentication(MapActivity.this);
                                        break;
                                    case DialogInterface.BUTTON_NEGATIVE:
                                        break;
                                }
                            }
                        };

                        // create the dialog to confirm the transfer
                        AlertDialog.Builder builder = new AlertDialog.Builder(MapActivity.this);
                        builder.setMessage(R.string.authentication_first)
                                .setPositiveButton("Ok", dialogClickListener)
                                .setNegativeButton("cancel", dialogClickListener).show();

                    } else {

                        // create the dialog listener for data transfer
                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int choice) {
                                switch (choice) {
                                    case DialogInterface.BUTTON_POSITIVE:
                                        // transfer the info file
                                        File infoFile = new File(mInfoFilename);
                                        UploadOneFile uploadInfo =
                                                new UploadOneFile(MapActivity.this,
                                                        mApi, DROPBOX_DIR,
                                                        infoFile,
                                                        mRowId,
                                                        mTextView_Transferred);
                                        uploadInfo.execute();

                                        // transfer the dng file
                                        File dngFile = new File(mDngFilename);
                                        UploadOneFile uploadDng =
                                                new UploadOneFile(MapActivity.this,
                                                        mApi, DROPBOX_DIR,
                                                        dngFile,
                                                        mRowId,
                                                        mTextView_Transferred);
                                        uploadDng.execute();

                                        break;
                                    case DialogInterface.BUTTON_NEGATIVE:
                                        break;
                                }
                            }
                        };

                        // create the dialog to confirm the transfer
                        AlertDialog.Builder builder = new AlertDialog.Builder(MapActivity.this);
                        builder.setMessage(R.string.transfer_confirmation)
                                .setPositiveButton("Yes", dialogClickListener)
                                .setNegativeButton("No", dialogClickListener).show();
                    }
                }
            });

            // go back to the list view
            Button cancelButton = (Button)findViewById(R.id.button_back);
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });

        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        //DropBox ==>
        AndroidAuthSession session = mApi.getSession();

        if (session.authenticationSuccessful()) {
            try {
                // Mandatory call to complete the auth
                session.finishAuthentication();

                // Store it locally in our app for later use
                storeAuth(session);

            } catch (IllegalStateException e) {
                showToast("Couldn't authenticate with Dropbox:"
                        + e.getLocalizedMessage());
                Log.i(TAG, "Error authenticating", e);
            }
        }
        //<==
    }

    //DropBox functions ==>

    private void logOut() {
        //Remove credentials from the session
        mApi.getSession().unlink();

        // Clear our stored keys
        clearKeys();
    }

    /**
     * shows keeping the access keys returned from Trusted Authenticator in a
     * local store, rather than storing user name & password, and
     * re-authenticating each time (which is not to be done, ever).
     */
    private void loadAuth(AndroidAuthSession session) {
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        String key = prefs.getString(ACCESS_KEY_NAME, null);
        String secret = prefs.getString(ACCESS_SECRET_NAME, null);
        if (key == null || secret == null || key.length() == 0
                || secret.length() == 0)
            return;

        if (key.equals("oauth2:")) {
            // If the key is set to "oauth2:", then we can assume the token is
            // for OAuth 2.
            session.setOAuth2AccessToken(secret);
        }
    }

    /**
     * Shows keeping the access keys returned from Trusted Authenticator in a
     * local store, rather than storing user name & password, and
     * re-authenticating each time (which is not to be done, ever).
     */
    private void storeAuth(AndroidAuthSession session) {
        // Store the OAuth 2 access token, if there is one.
        String oauth2AccessToken = session.getOAuth2AccessToken();
        if (oauth2AccessToken != null) {
            SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME,
                    0);
            SharedPreferences.Editor edit = prefs.edit();
            edit.putString(ACCESS_KEY_NAME, "oauth2:");
            edit.putString(ACCESS_SECRET_NAME, oauth2AccessToken);
            edit.commit();
        }
    }

    private void clearKeys() {
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        SharedPreferences.Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }

    public AndroidAuthSession buildSession() {
        AppKeyPair appKeyPair = new AppKeyPair(APP_KEY, APP_SECRET);

        AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
        loadAuth(session);
        return session;
    }


    //Map function ==>
    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.addMarker(new MarkerOptions().position(mLatLng).title("Test Site"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(mLatLng));
    }

    //Utility functions ==>
    private void showToast(String msg) {
        Toast error = Toast.makeText(this, msg, Toast.LENGTH_LONG);
        error.show();
    }
}