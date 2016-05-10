package com.opuses.detectparticles;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Fragment2_Camera extends Fragment{
    private final String TAG = "Fragment2_Camera";



    private OnAttachmentOpenedListener _listener;
    public interface OnAttachmentOpenedListener {
        void goToResultScreen();
        void playSound();

        void setLocationService();
        void startGPS();
        void stopGPS();
        void getLocation();

        boolean  getAttachmentClosed();
        void     setAttachmentClosed(boolean b);

        void  playNegativeTone();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            _listener = (OnAttachmentOpenedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnAttachmentOpenedListener");
        }
    }

    /** default camera settings **/
    public static final int DEFAULT_FOCUS_MM = 100;          //default distance is 100mm
    public static final Long DEFAULT_EXPOSURE = 20000000L;     //default exposure is TODO
    public static final int DEFAULT_ISO = 200;          //default ISO is TODO
    public static final int DEFAULT_FLASH = 0;

    private static Date _date = null;
    public static Date getTestTimeStamp() {return _date;}

    private static String _dngFilename = "";
    public static String get_dngFilename() {return _dngFilename;}

    /** UI **/
    private TextBlinker _textBlinker;
    private Button _startTestButton;

    private ArrayList<String> mBurstSettings = null;
    private String mSingleSettingName = null;

    /** camera settings **/
    private int mFocus_mm = DEFAULT_FOCUS_MM;
    private long mExposure = DEFAULT_EXPOSURE;     //using int instead of long because of 16bit
    private int mISO = DEFAULT_ISO;
    private int mFlashOn;
    private float mDiopter = 0f;                    //calculated from mFocus_mm

    private float mFocusDiopter_max;
    private float mFocusDiopter_min;
    private Range<Long> mExposureRange;
    private Range<Integer> mISORange;
    private boolean mFlashAvailable;


    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    /**
     * Request code for camera permissions.
     */
    private static final int REQUEST_CAMERA_PERMISSIONS = 1;

    /**
     * Permissions required to take a picture.
     */
    private static final String[] CAMERA_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };


    /**
     * Tolerance when comparing aspect ratios.
     */
    private static final double ASPECT_RATIO_TOLERANCE = 0.005;

    // Camera state
    private static final int STATE_CLOSED = 0;
    private static final int STATE_OPENED = 1;
    private static final int STATE_PREVIEW = 2;
    private static final int STATE_CAPTURE = 3;
    private int mState = STATE_CLOSED;

    /**
     * An {@link OrientationEventListener} used to determine when device rotation has occurred.
     * This is mainly necessary for when the device is rotated by 180 degrees, in which case
     * onCreate or onConfigurationChanged is not called as the view dimensions remain the same,
     * but the orientation of the has changed, and thus the preview rotation must be updated.
     */
    private OrientationEventListener mOrientationListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment2_camera, container, false);
    }


    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {

        //connect to GPS when enter the screen
        _listener.startGPS();

        _startTestButton = (Button)view.findViewById(R.id.picture_button);
        _startTestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!_listener.getAttachmentClosed()) {
                    _textBlinker.stopBlink();

                    _listener.stopGPS();    //stops GPS before taking picture
                    _listener.playSound();

                    takePicture();
                } else {
                    _listener.playNegativeTone();
                    _textBlinker.startBlink();
                }
            }
        });

        _textBlinker = new TextBlinker((TextView)view.findViewById(R.id.textView_instruction));

        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);

        mOrientationListener = new OrientationEventListener(getActivity(),
                SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (mTextureView != null && mTextureView.isAvailable()) {
                    configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
                }
            }
        };

        //set the camera parameters
        mFocus_mm   = MainActivity._cameraSettings.get_focus();
        mExposure   = MainActivity._cameraSettings.get_exposure();
        mISO        = MainActivity._cameraSettings.get_iso();
        mFlashOn    = MainActivity._cameraSettings.get_flash();

         /*
        For test
         */
        ToggleButton _closeToggleButton = (ToggleButton)view.findViewById(R.id.test_open_toggleButton);
        _closeToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //get the location when the attachment is opened
                _listener.getLocation();

                boolean b = ((ToggleButton) v).isChecked();
                _listener.setAttachmentClosed(!b);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        startBackgroundThread();
        openCameraServiceStartsPreview();

        if (mTextureView.isAvailable()) {
            configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
        if (mOrientationListener != null && mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }
    }

    @Override
    public void onPause() {

        if (mOrientationListener != null) {
            mOrientationListener.disable();
        }
        closeCamera();
        stopBackgroundThread();



        super.onPause();

    }

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events of a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            synchronized (mCameraStateLock) {
                mPreviewSize = null;
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };


    private AutoFitTextureView mTextureView;
    private HandlerThread mBackgroundThread;
    private final AtomicInteger mRequestCounter = new AtomicInteger();
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private final Object mCameraStateLock = new Object();

    // *********************************************************************************************
    // State protected by mCameraStateLock.
    //
    // The following state is used across both the UI and background threads.  Methods with "Locked"
    // in the name expect mCameraStateLock to be held while calling.


    private String mCameraId;
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice = null;
    private Size mPreviewSize;
    private CameraCharacteristics mCharacteristics;
    private Handler mBackgroundHandler;
    private RefCountedAutoCloseable<ImageReader> mRawImageReader;
    private int mPendingUserCaptures = 0;
    private final TreeMap<Integer, ImageSaver.ImageSaverBuilder> mRawResultQueue = new TreeMap<>();
    private CaptureRequest.Builder mPreviewRequestBuilder;


    //**********************************************************************************************

    /**
     * {@link CameraDevice.StateCallback} is called when the currently active {@link CameraDevice}
     * changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here if
            // the TextureView displaying this has been set up.
            synchronized (mCameraStateLock) {
                mState = STATE_OPENED;
                mCameraOpenCloseLock.release();
                mCameraDevice = cameraDevice;
            }

            // Start the preview session if the TextureView has been set up already.
            if (mPreviewSize != null && mTextureView.isAvailable()) {
                createCameraPreviewSession();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            synchronized (mCameraStateLock) {
                mState = STATE_CLOSED;
                mCameraOpenCloseLock.release();
                cameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            Log.e(TAG, "Received camera device error: " + error);
            synchronized (mCameraStateLock) {
                mState = STATE_CLOSED;
                mCameraOpenCloseLock.release();
                cameraDevice.close();
                mCameraDevice = null;
            }
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * RAW image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnRawImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.e(TAG, "onImageAvailable dequeueAndSaveImage "+ reader.getMaxImages());
            dequeueAndSaveImage(mRawResultQueue, mRawImageReader);
        }

    };

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events for the preview and
     * pre-capture sequence.
     */
    private final CameraCaptureSession.CaptureCallback mPreCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        @SuppressWarnings("UnusedParameters")
        private void process(CaptureResult result) {
            synchronized (mCameraStateLock) {
                switch (mState) {
                    case STATE_PREVIEW: {
                        // We have nothing to do when the camera preview is running normally.
                        break;
                    }
                    case STATE_CAPTURE: {

                        if (mPendingUserCaptures > 0) {
                            // Capture once for each user tap of the "Picture" button.
                            while (mPendingUserCaptures > 0) {
                                    captureStillPictureSession();
                                mPendingUserCaptures--;
                            }
                            // After this, the camera will go back to the normal state of preview.
                            mState = STATE_PREVIEW;
                        }
                    }
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles the still JPEG and RAW capture
     * request.
     */
    private final CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                     @NonNull CaptureRequest request,
                                     long timestamp, long frameNumber) {
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {

            // turn off the flashlight if it is on
            // turn off the flash for the next preview, in case the last one was on
            if (mFlashOn != 0) {
                mFlashOn = 0;
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }


            // Look up the ImageSaverBuilder for this request and
            // update it with the CaptureResult
            ImageSaver.ImageSaverBuilder rawBuilder;

            int requestId = (int) request.getTag();

            synchronized (mCameraStateLock) {
                rawBuilder = mRawResultQueue.get(requestId);

                _date = new Date();
                String timestamp = (new SimpleDateFormat("yyMMdd_HHmmss")).format(_date);

                _dngFilename = Environment.
                        getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) +
                        "/" + timestamp + ".dng";

                File rawFile = new File(Environment.
                        getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        timestamp + ".dng");


                if (rawBuilder != null) {
                    rawBuilder.setFile(rawFile);
                    rawBuilder.setResult(result);
                }

                // If we have all the results necessary, save the image to a file in the background.
                handleCompletionLocked(requestId, rawBuilder, mRawResultQueue);
            }


//            // notify the user
//            StringBuilder sb = new StringBuilder();
//            synchronized (mCameraStateLock) {
//                if (rawBuilder != null) {
//                    sb.append("Saving RAW as: ");
//                    sb.append(rawBuilder.getSaveLocation());
//                }
//                showToast(sb.toString());
//            }

            //back to preview
//            try {
//                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(),
//                        mPreCaptureCallback,
//                        mBackgroundHandler);
//            } catch (CameraAccessException e) {
//                e.printStackTrace();
//            }

            // forward to the next screen
            _listener.goToResultScreen();
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                    @NonNull CaptureRequest request,
                                    @NonNull CaptureFailure failure) {
            //turn off the flash if it is on
            // turn off the flash for the next preview, in case the last one was on
            if (mFlashOn != 0) {
                mFlashOn = 0;
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }

            int requestId = (int) request.getTag();
            synchronized (mCameraStateLock) {
                mRawResultQueue.remove(requestId);
            }
            showToast("Test failed!");
        }

    };

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles the still JPEG and RAW capture
     * request.
     */
    private final CameraCaptureSession.CaptureCallback mBurstCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {


        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session,
                                     @NonNull CaptureRequest request,
                                     long timestamp, long frameNumber) {
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {

            Log.i(TAG, "onCaptureCompleted requestedFocus/resultFocus="+request.get(CaptureRequest.LENS_FOCUS_DISTANCE) +
                            "/"+result.get(CaptureResult.LENS_FOCUS_DISTANCE)+
                            " requestedExp/resultExp="+request.get(CaptureRequest.SENSOR_EXPOSURE_TIME) +
                            "/"+result.get(CaptureResult.SENSOR_EXPOSURE_TIME) +
                            " requestedISO/resultISO="+request.get(CaptureRequest.SENSOR_SENSITIVITY) +
                            "/"+result.get(CaptureResult.SENSOR_SENSITIVITY)
            );

            // in case the last one was on
            // turn off the flash for the next preview
            if (mFlashOn != 0) {
                mFlashOn = 0;
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }


            // Look up the ImageSaverBuilder for this request and
            // update it with the CaptureResult
            ImageSaver.ImageSaverBuilder rawBuilder;
            int requestId = (int) request.getTag();

            synchronized (mCameraStateLock) {
                rawBuilder = mRawResultQueue.get(requestId);

                if (rawBuilder != null) {
                    // also get the setting name from it, write to filename
                    String settingName = rawBuilder.getSettingName();
                    String currentDateTime = generateTimestamp();

                    String fname = extractSettingName(settingName) + "_" + currentDateTime;
                    File rawFile = new File(Environment.
                            getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                            fname + ".dng");

                    rawBuilder.setFile(rawFile);
                    rawBuilder.setResult(result);

                    Log.e(TAG, "**handleCompletionLocked " + requestId + " rawBuilder=" + rawBuilder.toString());

                    // save the image to a file in the background.
                    handleCompletionLocked(requestId, rawBuilder, mRawResultQueue);

                }
            }


            // go back to preview
            try {
                mCaptureSession.setRepeatingRequest(
                        mPreviewRequestBuilder.build(),
                        mPreCaptureCallback, mBackgroundHandler);
                mState = STATE_PREVIEW;
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                    @NonNull CaptureRequest request,
                                    @NonNull CaptureFailure cf) {
            // turn off the flash for the next preview, in case the last one was on
            if (mFlashOn != 0) {
                mFlashOn = 0;
                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }

            int requestId = (int) request.getTag();
            synchronized (mCameraStateLock) {
                mRawResultQueue.remove(requestId);
            }
            showToast("Capture failed!");
        }

    };

    /**
     * A {@link Handler} for showing {@link Toast}s on the UI thread.
     */
    @SuppressLint("HandlerLeak")
    private final Handler mMessageHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Activity activity = getActivity();
            if (activity != null) {
                Toast.makeText(activity, (String) msg.obj, Toast.LENGTH_SHORT).show();
            }
        }
    };


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSIONS) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    showMissingPermissionError();
                    return;
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }


    /**
     * Sets up state related to camera that is needed before opening a {@link CameraDevice}.
     */
    private boolean setUpCameraOutputs() {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
//            ErrorDialog.buildErrorDialog("This device doesn't support Camera2 API.").
//                    show(getFragmentManager(), "dialog");
            return false;
        }
        try {
            // Find a CameraDevice that supports RAW captures, and configure state.
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We only use a camera that supports RAW
                if (!contains(characteristics.get(
                                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                    continue;
                }


                //grab the camera parameter range
                getCameraParameterRange(characteristics);

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                // For still image captures, we use the largest available size.
                if (map == null) {
                    continue;
                }

                Size largestRaw = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.RAW_SENSOR)),
                        new CompareSizesByArea());


                // save the camera characteristics and cameraId
                mCharacteristics = characteristics;
                mCameraId = cameraId;

                synchronized (mCameraStateLock) {
                    // Set up ImageReaders for RAW outputs.  Place these in a reference
                    // counted wrapper to ensure they are only closed when all background tasks
                    // using them are finished.

                    if (mRawImageReader == null || mRawImageReader.getAndRetain() == null) {
                        mRawImageReader = new RefCountedAutoCloseable<>(
                                ImageReader.newInstance(largestRaw.getWidth(),
                                        largestRaw.getHeight(), ImageFormat.RAW_SENSOR, 5));
                    }
                    mRawImageReader.get().setOnImageAvailableListener(
                            mOnRawImageAvailableListener, mBackgroundHandler);
                }
                return true;
            }
        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
        }

//        // If we found no suitable cameras for capturing RAW, warn the user.
//        ErrorDialog.buildErrorDialog("This device doesn't support capturing RAW photos").
//                show(getFragmentManager(), "dialog");
        return false;
    }

    private void getCameraParameterRange(CameraCharacteristics character) {

        // check focus range
        //noinspection ConstantConditions
        mFocusDiopter_min = character.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        mFocusDiopter_max = 0f;

        // check iso and exposure
        mISORange = character.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        if (mISORange != null) {

            // only support exposure_time if iso_range is supported
            mExposureRange = character.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        }

        // check if this device has a flash
        //noinspection ConstantConditions
        mFlashAvailable = character.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);

    }

    /**
     * Opens the camera specified by {@link #mCameraId}.
     */
    private void openCameraServiceStartsPreview() {
        if (!setUpCameraOutputs()) {
            return;
        }
        if (!hasAllPermissionsGranted()) {
            requestCameraPermissions();
            return;
        }

        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            // Wait for any previously running session to finish.
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            String cameraId;
            Handler backgroundHandler;
            synchronized (mCameraStateLock) {
                cameraId = mCameraId;
                backgroundHandler = mBackgroundHandler;
            }
            //noinspection ResourceType
            manager.openCamera(cameraId, mStateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Requests permissions necessary to use camera and save pictures.
     */
    private void requestCameraPermissions() {
        if (shouldShowRationale()) {
            //PermissionConfirmationDialog.newInstance().show(getChildFragmentManager(), "dialog");
        } else {
            //FragmentCompat.requestPermissions(this, CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSIONS);
        }
    }

    /**
     * Tells whether all the necessary permissions are granted to this app.
     *
     * @return True if all the required permissions are granted.
     */
    private boolean hasAllPermissionsGranted() {
        for (String permission : CAMERA_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(getActivity(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets whether you should show UI with rationale for requesting the permissions.
     *
     * @return True if the UI should be shown.
     */
    private boolean shouldShowRationale() {
        for (String permission : CAMERA_PERMISSIONS) {
//            if (FragmentCompat.shouldShowRequestPermissionRationale(this, permission)) {
//                return true;
//            }
        }
        return false;
    }

    /**
     * Shows that this app really needs the permission and finishes the app.
     */
    private void showMissingPermissionError() {
        Activity activity = getActivity();
        if (activity != null) {
            Toast.makeText(activity, R.string.request_permission, Toast.LENGTH_SHORT).show();
            activity.finish();
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            synchronized (mCameraStateLock) {

                // Reset state and clean up resources used by the camera.
                // Note: After calling this, the ImageReaders will be closed after any background
                // tasks saving Images from these readers have been completed.
                mPendingUserCaptures = 0;
                mState = STATE_CLOSED;
                if (null != mCaptureSession) {
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
                if (null != mCameraDevice) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
                if (null != mRawImageReader) {
                    mRawImageReader.close();
                    mRawImageReader = null;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        synchronized (mCameraStateLock) {
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            synchronized (mCameraStateLock) {
                mBackgroundHandler = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void createCameraPreviewSession() {
        if (mPreviewSize != null && (mCameraDevice != null)) {
            try {
                SurfaceTexture texture = mTextureView.getSurfaceTexture();
                // We configure the size of default buffer to be the size of camera preview we want.
                texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

                // This is the output Surface we need to start preview.
                Surface surface = new Surface(texture);

                // We set up a CaptureRequest.Builder with the output Surface.
                mPreviewRequestBuilder
                        = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mPreviewRequestBuilder.addTarget(surface);

                setManualMode(mPreviewRequestBuilder);

                // Here, we create a CameraCaptureSession for camera preview.
                mCameraDevice.createCaptureSession(Arrays.asList(surface,
                                mRawImageReader.get().getSurface()), new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                synchronized (mCameraStateLock) {
                                    // The camera is already closed
                                    if (null == mCameraDevice) {
                                        return;
                                    }

                                    /** set camera parameters from the input value **/
                                    //- set Focus within range
                                    mDiopter = getValidDiopterFromDistance(mFocus_mm);

                                    //- set ISO within range
                                    mISO = getValidISO(mISO);

                                    //- set Exposure within range
                                    mExposure = getValidExposure(mExposure);

                                    //set the flash to several choices
                                    getActivity().runOnUiThread(new Runnable() {
                                        @SuppressLint("SetTextI18n")
                                        @Override
                                        public void run() {
                                        }
                                    });

                                    // update previews on the settings
                                    Log.i(TAG, "1.set focus= " + mDiopter);
                                    mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mDiopter);
                                    mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, mISO);
                                    mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mExposure);

                                    if (mFlashOn == 0) {
                                        mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                                    } else if (mFlashAvailable){
                                        mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                                    }

                                    try {
                                        // Finally, we start displaying the camera preview.
                                        cameraCaptureSession.setRepeatingRequest(
                                                mPreviewRequestBuilder.build(),
                                                mPreCaptureCallback, mBackgroundHandler);
                                        mState = STATE_PREVIEW;
                                    } catch (CameraAccessException | IllegalStateException e) {
                                        e.printStackTrace();
                                        return;
                                    }
                                    // When the session is ready, we start displaying the preview.
                                    mCaptureSession = cameraCaptureSession;
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                showToast("Failed to configure camera.");
                            }
                        }, mBackgroundHandler
                );
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Configure the necessary {@link android.graphics.Matrix} transformation to `mTextureView`,
     * and start/restart the preview capture session if necessary.
     * <p/>
     * This method should be called after the camera state has been initialized in
     * setUpCameraOutputs.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        synchronized (mCameraStateLock) {
            if (null == mTextureView || null == activity) {
                return;
            }

            try {
                StreamConfigurationMap map = mCharacteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                // For still image captures, we always use the largest available size.
                assert map != null;
                Size largestJpeg = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());

                // Find the rotation of the device relative to the native device orientation.
                int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();

                // Find the rotation of the device relative to the camera sensor's orientation.
                int totalRotation = sensorToDeviceRotation(mCharacteristics, deviceRotation);

                // Swap the view dimensions for calculation as needed if they are rotated relative to
                // the sensor.
                boolean swappedDimensions = totalRotation == 90 || totalRotation == 270;
                int rotatedViewWidth = viewWidth;
                int rotatedViewHeight = viewHeight;
                if (swappedDimensions) {
                    //noinspection SuspiciousNameCombination
                    rotatedViewWidth = viewHeight;
                    //noinspection SuspiciousNameCombination
                    rotatedViewHeight = viewWidth;
                }

                // Find the best preview size for these view dimensions and configured JPEG size.
                Size previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedViewWidth, rotatedViewHeight, largestJpeg);

                if (swappedDimensions) {
                    mTextureView.setAspectRatio(
                            previewSize.getHeight(), previewSize.getWidth());
                } else {
                    mTextureView.setAspectRatio(
                            previewSize.getWidth(), previewSize.getHeight());
                }


                assert mCharacteristics != null;

                Matrix matrix = new Matrix();
                mTextureView.setTransform(matrix);

                // Start or restart the active capture session if the preview was initialized or
                // if its aspect ratio changed significantly.
                if (mPreviewSize == null || !checkAspectsEqual(previewSize, mPreviewSize)) {
                    mPreviewSize = previewSize;
                    if (mState != STATE_CLOSED) {
                        createCameraPreviewSession();
                    }
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Initiate a still image capture.
     * <p/>
     * This function sends a capture request that initiates a pre-capture sequence in our state
     * machine that waits for auto-focus to finish, ending in a "locked" state where the lens is no
     * longer moving, waits for auto-exposure to choose a good exposure value, and waits for
     * auto-white-balance to converge.
     */
    private void takePicture() {
        synchronized (mCameraStateLock) {
            mPendingUserCaptures++;

            // If we already triggered a pre-capture sequence, or are in a state where we cannot
            // do this, return immediately.
            if (mState != STATE_PREVIEW) {
                return;
            }

            try {
                mState = STATE_CAPTURE;
                // Replace the existing repeating request with one with updated parameters
                mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback,
                        mBackgroundHandler);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Send a capture request to the camera device that initiates a capture targeting the
     * RAW outputs.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void captureStillPictureSession() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }

            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            captureBuilder.addTarget(mRawImageReader.get().getSurface());

            setManualMode(captureBuilder);

            Log.i(TAG, "2.set focus= " + mDiopter);
            captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mDiopter);
            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, mISO);
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mExposure);
            if (mFlashOn == 0) {
                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            } else {
                captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            }

            // Set orientation.
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    sensorToDeviceRotation(mCharacteristics, rotation));

            // Set request tag to easily track results in callbacks.
            captureBuilder.setTag(mRequestCounter.getAndIncrement());

            CaptureRequest request = captureBuilder.build();

            // Create an ImageSaverBuilder in which to collect results, and add it to the queue
            // of active requests.
            ImageSaver.ImageSaverBuilder jpegBuilder = new ImageSaver.ImageSaverBuilder(activity)
                    .setCharacteristics(mCharacteristics);
            ImageSaver.ImageSaverBuilder rawBuilder = new ImageSaver.ImageSaverBuilder(activity)
                    .setCharacteristics(mCharacteristics);

            rawBuilder.setSettingName(mSingleSettingName);
            mRawResultQueue.put((int) request.getTag(), rawBuilder);

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(request, mCaptureCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * Retrieve the next {@link Image} from a reference counted {@link ImageReader}, retaining
     * that {@link ImageReader} until that {@link Image} is no longer in use, and set this
     * {@link Image} as the result for the next request in the queue of pending requests.  If
     * all necessary information is available, begin saving the image to a file in a background
     * thread.
     *
     * @param pendingQueue the currently active requests.
     * @param reader       a reference counted wrapper containing an {@link ImageReader} from which
     *                     to acquire an image.
     */
    private void dequeueAndSaveImage(TreeMap<Integer, ImageSaver.ImageSaverBuilder> pendingQueue,
                                     RefCountedAutoCloseable<ImageReader> reader) {
        synchronized (mCameraStateLock) {
            Map.Entry<Integer, ImageSaver.ImageSaverBuilder> entry =
                    pendingQueue.firstEntry();

            ImageSaver.ImageSaverBuilder builder = entry.getValue();

            // Increment reference count to prevent ImageReader from being closed while we
            // are saving its Images in a background thread (otherwise their resources may
            // be freed while we are writing to a file).
            if (reader == null || reader.getAndRetain() == null) {
                Log.e(TAG, "Paused the activity before we could save the image," +
                        " ImageReader already closed.");
                pendingQueue.remove(entry.getKey());
                return;
            }

            Image image;
            try {
                image = reader.get().acquireLatestImage();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Too many images queued for saving, dropping image for request: " +
                        entry.getKey());
                pendingQueue.remove(entry.getKey());
                return;
            }

            if (builder != null && image != null)
                builder.setRefCountedReader(reader).setImage(image);
        }
    }

    /**
     * Runnable that saves an {@link Image} into the specified {@link File}, and updates
     * {@link android.provider.MediaStore} to include the resulting file.
     * <p/>
     * This can be constructed through an {@link ImageSaverBuilder} as the necessary image and
     * result information becomes available.
     */
    private static class ImageSaver implements Runnable {
        private final String TAG = "ImageSaver";

        private final Image mImage;
        private final File mFile;
        private final CaptureResult mCaptureResult;
        private final CameraCharacteristics mCharacteristics;
        private final Context mContext;
        private final RefCountedAutoCloseable<ImageReader> mReader;

        private ImageSaver(Image image, File file, CaptureResult result,
                           CameraCharacteristics characteristics, Context context,
                           RefCountedAutoCloseable<ImageReader> reader) {
            mImage = image;
            mFile = file;
            mCaptureResult = result;
            mCharacteristics = characteristics;
            mContext = context;
            mReader = reader;
        }

        @Override
        public void run() {
            boolean success = false;
            int format = mImage.getFormat();
            switch (format) {
                case ImageFormat.RAW_SENSOR: {
                    DngCreator dngCreator = new DngCreator(mCharacteristics, mCaptureResult);
                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(mFile);
                        dngCreator.writeImage(output, mImage);
                        success = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        mImage.close();
                        closeOutput(output);
                    }
                    break;
                }
                default: {
                    Log.e(TAG, "Cannot save image, unexpected image format:" + format);
                    break;
                }
            }

            // If saving the file succeeded,
            if (success) {
                // Decrement reference count to allow ImageReader to be closed to free up resources.
                mReader.close();



                MediaScannerConnection.scanFile(mContext, new String[]{mFile.getPath()},
                /*mimeTypes*/null, new MediaScannerConnection.MediaScannerConnectionClient() {
                    @Override
                    public void onMediaScannerConnected() {
                        // Do nothing
                    }

                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i(TAG, "Scanned " + path + ":");
                        Log.i(TAG, "-> uri=" + uri);
                    }
                });
            }
        }

        /**
         * Builder class for constructing {@link ImageSaver}s.
         * <p/>
         * This class is thread safe.
         */

        public static class ImageSaverBuilder {
            private Image mImage;
            private File mFile;
            private CaptureResult mCaptureResult;
            private CameraCharacteristics mCharacteristics;
            private final Context mImageSaveBuilderContext;
            private RefCountedAutoCloseable<ImageReader> mReader;
            private String mSettingName;

            /**
             * Construct a new ImageSaverBuilder using the given {@link Context}.
             *
             * @param context a {@link Context} to for accessing the
             *                {@link android.provider.MediaStore}.
             */
            public ImageSaverBuilder(final Context context) {
                mImageSaveBuilderContext = context;
                mImage = null;
                mFile = null;
                mCaptureResult = null;
                mCharacteristics = null;
            }

            public synchronized ImageSaverBuilder setRefCountedReader(
                    RefCountedAutoCloseable<ImageReader> reader) {
                if (reader == null) throw new NullPointerException();

                mReader = reader;
                return this;
            }

            public synchronized void setImage(final Image image) {
                if (image == null) throw new NullPointerException();
                mImage = image;
            }

            public synchronized void setFile(final File file) {
                if (file == null) throw new NullPointerException();
                mFile = file;
            }

            public synchronized void setSettingName(final String settingName) {
                mSettingName = settingName;
            }

            public synchronized void setResult(final CaptureResult result) {
                if (result == null) throw new NullPointerException();
                mCaptureResult = result;
            }

            public synchronized ImageSaverBuilder setCharacteristics(
                    final CameraCharacteristics characteristics) {
                if (characteristics == null) throw new NullPointerException();
                mCharacteristics = characteristics;
                return this;
            }

            public synchronized ImageSaver buildIfComplete() {
                if (!isComplete()) {
                    return null;
                }
                return new ImageSaver(mImage, mFile, mCaptureResult, mCharacteristics, mImageSaveBuilderContext,
                        mReader);
            }

            public synchronized String getSettingName() {
                return mSettingName;
            }

            public synchronized String getSaveLocation() {
                return (mFile == null) ? "Unknown" : mFile.toString();
            }

            private boolean isComplete() {
                return mImage != null && mFile != null && mCaptureResult != null
                        && mCharacteristics != null;
            }
        }
    }

    // Utility classes and methods:
    // *********************************************************************************************

    /**
     * Comparator based on area of the given {@link Size} objects.
     */
    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * A dialog fragment for displaying non-recoverable errors; this {@link Activity} will be
     * finished once the dialog has been acknowledged by the user.
     */
    public static class ErrorDialog extends DialogFragment {

        private String mErrorMessage;

        public ErrorDialog() {
            mErrorMessage = "Unknown error occurred!";
        }

        // Build a dialog with a custom message (Fragments require default constructor).
        public static ErrorDialog buildErrorDialog(String errorMessage) {
            ErrorDialog dialog = new ErrorDialog();
            dialog.mErrorMessage = errorMessage;
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(mErrorMessage)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }

    /**
     * A wrapper for an {@link AutoCloseable} object that implements reference counting to allow
     * for resource management.
     */
    public static class RefCountedAutoCloseable<T extends AutoCloseable> implements AutoCloseable {
        private T mObject;
        private long mRefCount = 0;

        /**
         * Wrap the given object.
         *
         * @param object an object to wrap.
         */
        public RefCountedAutoCloseable(T object) {
            if (object == null) throw new NullPointerException();
            mObject = object;
        }

        /**
         * Increment the reference count and return the wrapped object.
         *
         * @return the wrapped object, or null if the object has been released.
         */
        public synchronized T getAndRetain() {
            if (mRefCount < 0) {
                return null;
            }
            mRefCount++;
            return mObject;
        }

        /**
         * Return the wrapped object.
         *
         * @return the wrapped object, or null if the object has been released.
         */
        public synchronized T get() {
            return mObject;
        }

        /**
         * Decrement the reference count and release the wrapped object if there are no other
         * users retaining this object.
         */
        @Override
        public synchronized void close() {
            if (mRefCount >= 0) {
                mRefCount--;
                if (mRefCount < 0) {
                    try {
                        mObject.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        mObject = null;
                    }
                }
            }
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e("chooseOptimalSize", "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Generate a string containing a formatted timestamp with the current date and time.
     *
     * @return a {@link String} representing a time.
     */
    private static String generateTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US);
        return sdf.format(new Date());
    }

    /**
     * Cleanup the given {@link OutputStream}.
     *
     * @param outputStream the stream to close.
     */
    private static void closeOutput(OutputStream outputStream) {
        if (null != outputStream) {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Return true if the given array contains the given integer.
     *
     * @param modes array to check.
     * @param mode  integer to get for.
     * @return true if the array contains the given integer, otherwise false.
     */
    private static boolean contains(int[] modes, @SuppressWarnings("SameParameterValue") int mode) {
        if (modes == null) {
            return false;
        }
        for (int i : modes) {
            if (i == mode) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if the two given {@link Size}s have the same aspect ratio.
     *
     * @param a first {@link Size} to compare.
     * @param b second {@link Size} to compare.
     * @return true if the sizes have the same aspect ratio, otherwise false.
     */
    private static boolean checkAspectsEqual(Size a, Size b) {
        double aAspect = a.getWidth() / (double) a.getHeight();
        double bAspect = b.getWidth() / (double) b.getHeight();
        return Math.abs(aAspect - bAspect) <= ASPECT_RATIO_TOLERANCE;
    }

    /**
     * Rotation need to transform from the camera sensor orientation to the device's current
     * orientation.
     *
     * @param c                 the {@link CameraCharacteristics} to query for the camera sensor
     *                          orientation.
     * @param deviceOrientation the current device orientation relative to the native device
     *                          orientation.
     * @return the total rotation from the sensor orientation to the current device orientation.
     */
    private static int sensorToDeviceRotation(CameraCharacteristics c, int deviceOrientation) {
        //noinspection ConstantConditions
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Get device orientation in degrees
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);

        // Reverse device orientation for front-facing cameras
        //noinspection ConstantConditions
        if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            deviceOrientation = -deviceOrientation;
        }

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show.
     */
    private void showToast(String text) {
        // We show a Toast by sending request message to mMessageHandler. This makes sure that the
        // Toast is shown on the UI thread.
        Message message = Message.obtain();
        message.obj = text;
        mMessageHandler.sendMessage(message);
    }

    /**
     * If the given request has been completed, remove it from the queue of active requests and
     * send an {@link ImageSaver} with the results from this request to a background thread to
     * save a file.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @param requestId the ID of the {@link CaptureRequest} to handle.
     * @param builder   the {@link ImageSaver.ImageSaverBuilder} for this request.
     * @param queue     the queue to remove this request from, if completed.
     */
    private void handleCompletionLocked(int requestId, ImageSaver.ImageSaverBuilder builder,
                                        TreeMap<Integer, ImageSaver.ImageSaverBuilder> queue) {
        if (builder == null) return;
        ImageSaver saver = builder.buildIfComplete();
        if (saver != null) {
            Log.e(TAG, "handleCompletionLocked saver=" + saver.toString());
            queue.remove(requestId);
            AsyncTask.THREAD_POOL_EXECUTOR.execute(saver);
        }
    }

    /**
     * A dialog that explains about the necessary permissions.
     */
    public static class PermissionConfirmationDialog extends DialogFragment {

        public static PermissionConfirmationDialog newInstance() {
            return new PermissionConfirmationDialog();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
//            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
//                            FragmentCompat.requestPermissions(parent, CAMERA_PERMISSIONS,
//                                    REQUEST_CAMERA_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    getActivity().finish();
                                }
                            })
                    .create();
        }
    }

    private void setManualMode(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);

        builder.set(CaptureRequest.EDGE_MODE,
                CaptureRequest.EDGE_MODE_OFF);

        builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF);

        builder.set(CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.NOISE_REDUCTION_MODE_OFF);

        builder.set(CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.NOISE_REDUCTION_MODE_OFF);

        builder.set(CaptureRequest.CONTROL_EFFECT_MODE,
                CaptureRequest.CONTROL_EFFECT_MODE_OFF);

        builder.set(CaptureRequest.HOT_PIXEL_MODE,
                CaptureRequest.HOT_PIXEL_MODE_OFF);

        builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);

        builder.set(CaptureRequest.SENSOR_TEST_PATTERN_MODE,
                CaptureRequest.SENSOR_TEST_PATTERN_MODE_OFF);

        builder.set(CaptureRequest.SHADING_MODE,
                CaptureRequest.SHADING_MODE_OFF);

        builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF);

        builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_OFF);
    }

    private float getValidDiopterFromDistance(float distance_mm) {
        float diopter = 1000.f / distance_mm;
        if (diopter < mFocusDiopter_max) {
            diopter = mFocusDiopter_max;

            //recalculate the mFocus
            mFocus_mm = (int)(1000.f / diopter);

        } else if (diopter > mFocusDiopter_min) {
            diopter = mFocusDiopter_min;

            //recalculate the mFocus
            mFocus_mm = (int)(1000.f / diopter);
        }
        return diopter;
    }

    private int getValidISO(int iso) {
        int isoMax = mISORange.getUpper();
        int isoMin = mISORange.getLower();
        if (iso > isoMax) {
            iso = isoMax;
        } else if (iso < isoMin) {
            iso = isoMin;
        }
        return iso;
    }

    private long getValidExposure(long exposure) {
        long exposureMax = mExposureRange.getUpper();
        long exposureMin = mExposureRange.getLower();

        if (exposure > exposureMax) {
            exposure = exposureMax;
        } else if (exposure < exposureMin) {
            exposure = exposureMin;
        }
        return exposure;
    }


    private String extractSettingName(String settingName) {
        String ns = "_";
        if (settingName != null) {
            if (!settingName.isEmpty()) {
                // only get the first 8 letters
                final int MAX_NAME_LENGTH =8;
                int l = settingName.length();
                if (l > MAX_NAME_LENGTH) l = MAX_NAME_LENGTH;
                ns = settingName.substring(0, l);
            }
        }
        return ns;
    }
}

