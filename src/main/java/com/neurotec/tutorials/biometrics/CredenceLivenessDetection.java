package com.neurotec.tutorials.biometrics;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Looper;
import android.os.Message;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.credenceid.biometrics.Biometrics;
import com.neurotec.beans.NParameterBag;
import com.neurotec.beans.NParameterDescriptor;
import com.neurotec.biometrics.NBiometric;
import com.neurotec.biometrics.NBiometricCaptureOption;
import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NBiometricType;
import com.neurotec.biometrics.NFace;
import com.neurotec.biometrics.NLAttributes;
import com.neurotec.biometrics.NLFeaturePoint;
import com.neurotec.biometrics.NLProperty;
import com.neurotec.biometrics.NLivenessMode;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.biometrics.view.NFaceView;
import com.neurotec.devices.NCamera;
import com.neurotec.devices.NDevice;
import com.neurotec.devices.NDeviceManager;
import com.neurotec.devices.NDeviceType;
import com.neurotec.images.NImage;
import com.neurotec.io.NFile;
import com.neurotec.io.NStream;
import com.neurotec.lang.NCore;
import com.neurotec.media.NVideoFormat;
import com.neurotec.plugins.NPlugin;
import com.neurotec.plugins.NPluginState;
import com.neurotec.samples.app.BaseActivity;
import com.neurotec.samples.app.DirectoryViewer;
import com.neurotec.samples.licensing.LicensingManager;
import com.neurotec.samples.view.BaseDialogFragment;
import com.neurotec.tutorials.biometrics.camera.DrawingView;
import com.neurotec.tutorials.biometrics.camera.PreviewFrameLayout;
import com.neurotec.tutorials.biometrics.camera.Utils;
import com.neurotec.util.concurrent.CompletionHandler;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static android.view.View.VISIBLE;
import static com.credenceid.biometrics.Biometrics.ResultCode.OK;

public class CredenceLivenessDetection extends BaseActivity implements SurfaceHolder.Callback {



    //=========================================================================
    // CHOOSE LICENCES !!!
    //=========================================================================
    // ONE of the below listed licenses is required for unlocking this sample's functionality. Choose a license that you currently have on your device.
    // If you are using a TRIAL version - choose any of them.

    //private static final String[] LICENSES = new String[]{"FaceExtractor"};
    //private static final String[] LICENSES = new String[]{"FaceClient"};
    private static final String[] LICENSES = new String[]{"FaceFastExtractor"};

    //=========================================================================

    public static final String TAG = "CID_DEV";
    private static final String IMAGES_FOLDER_PATH = "/Neurotechnology/";
    private NFaceView mFaceView;
    private Button mButtonExtract;
    private Button mSelectSource;
    private TextView mStatus;
    private String mRTSPUrl;
    private boolean mIsNeurotecInit= false;

    private NBiometricClient mBiometricClient;
    private NSubject mNSubject;
    private CredenceHandlerThreadCustom mNeurotechThread;
    private static boolean sIsrecording = false;
    private Bitmap[] mImageStream;
    private int imageCounter = 0;
    CountDownTimer mRecordingTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NCore.setContext(this);
        setContentView(R.layout.tutorial_credence_face_liveness);

        mNeurotechThread = new CredenceHandlerThreadCustom(getApplicationContext());
        mNeurotechThread.start();

        mContext = this;
        mCamera = null;

        this.initializeLayoutComponents();
        this.configureLayoutComponents();
        this.reset();
        this.doPreview();

        /*mFaceView = (NFaceView) findViewById(R.id.camera_view);*/
        mStatus = (TextView) findViewById(R.id.text_view_status);
        mButtonExtract = (Button) findViewById(R.id.button_extract);
        mButtonExtract.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //new InitializationTask().execute();
                if(!sIsrecording){
                    sIsrecording = true;
                    mImageStream = new Bitmap[100];
                    mRecordingTimer = new CountDownTimer(15000, 500){
                        public void onTick(long millisUntilFinished){
                            //
                        }
                        public  void onFinish(){
                            Log.d(TAG, "Timer Finished");
                            sIsrecording = false;
                            detectFace();
                        }
                    };
                    Log.d(TAG, "Start recording");
                    mRecordingTimer.start();
                }else{
                    mRecordingTimer.cancel();
                    sIsrecording = false;
                    imageCounter = 0;
                    Log.d(TAG, "stop recording (User)");
                }
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideProgress();
        if (mBiometricClient != null) {
            mBiometricClient.cancel();
            mBiometricClient.dispose();
            mBiometricClient = null;
            mIsNeurotecInit = false;
        }
    }


    public void recordFace(Bitmap img){

        if(sIsrecording){
            if(imageCounter < 100) {
                mImageStream[imageCounter] = img;
                imageCounter++;
            } else {
                sIsrecording = false;
                mRecordingTimer.cancel();
                detectFace();
            }
        }
    }

    public void detectFace(){

        Log.d(TAG, "Image stream length = " + imageCounter);

        Message msg = new Message();
        Bitmap[] finalStream = new Bitmap[imageCounter];
        for(int i = 0; i < imageCounter; i++)
            finalStream[i] = mImageStream[i];
//        msg.obj = finalStream;
//        mNeurotechThread.sImageHandler.sendMessage(msg);
        int result = mNeurotechThread.startEnrolling(finalStream);
        imageCounter = 0;
        //detectFaceNeurotecTask(img);
        showMessage("Liveness result = " + result);
    }

    private void showMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatus.append(message + "\n");
            }
        });
    }

    //-----------------------------------------------------------
    // Credence Code
    //----------------------------------------------------------

    @Override
    protected void
    onResume() {

        super.onResume();

        new Thread(() -> {
            try {
                /* Add a slight delay to avoid "Application passed NULL surface" error. */
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            runOnUiThread(() -> {
                this.reset();
                this.doPreview();
            });
        }).start();
    }


    /* This is required to stop camera preview every time activity loses focus. */
    @Override
    protected void
    onPause() {

        super.onPause();
        this.stopReleaseCamera();
    }

    /* This is required to stop camera every time application is killed.  */
    @Override
    protected void
    onStop() {

        super.onStop();
        this.stopReleaseCamera();
    }

    // Credence Objects
    @SuppressLint("StaticFieldLeak")
    private static Context mContext;
    private Camera mCamera = null;

    private PreviewFrameLayout mPreviewFrameLayout;
    private DrawingView mDrawingView;
    private SurfaceView mScannedImageView;
    private SurfaceHolder mSurfaceHolder;

    private boolean mIsCameraConfigured = false;

    private final static int P_WIDTH = 320;
    private final static int P_HEIGHT = 240;
    private boolean mInPreview = false;

    /* Initializes all layout file component objects. */
    private void
    initializeLayoutComponents() {

        mPreviewFrameLayout = findViewById(R.id.preview_frame_layout);
        mDrawingView = findViewById(R.id.drawing_view);
        mScannedImageView = findViewById(R.id.scanned_imageview);

    }

    /* Configured all layout file component objects. Assigns listeners, configurations, etc. */
    @SuppressWarnings("deprecation")
    private void
    configureLayoutComponents() {

        mPreviewFrameLayout.setVisibility(VISIBLE);
        mDrawingView.setVisibility(VISIBLE);
        mScannedImageView.setVisibility(VISIBLE);

        mSurfaceHolder = mScannedImageView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

    }

    /* This callback is invoked on each camera preview frame. In this callback will run call face
     * detection API and pass it preview frame.
     */
    private Camera.PreviewCallback mCameraPreviewCallback
            = (byte[] data, Camera camera) -> detectFace(data);

    /* This callback is invoked each time camera finishes auto-focusing. */
    private Camera.AutoFocusCallback mAutoFocusCallback = new Camera.AutoFocusCallback() {
        public void
        onAutoFocus(boolean autoFocusSuccess,
                    Camera arg1) {

            /* Tell DrawingView to stop displaying auto-focus circle by giving it a region of 0. */
            mDrawingView.setHasTouch(false, new Rect(0, 0, 0, 0));
            mDrawingView.invalidate();

        }
    };

    /* Resets camera flash and UI back to camera preview state. */
    private void
    reset() {
        /* This method is called before we start a camera preview, so we update global variable. */
        mInPreview = true;

    }

    private void
    setCameraPreviewDisplayOrientation() {

         mCamera.setDisplayOrientation(90);
    }

    /* Stops camera preview, turns off torch, releases camera object, and sets it to null. */
    private void
    stopReleaseCamera() {

        if (null != mCamera) {
            /* Tell camera to no longer invoke callback on each preview frame. */
            mCamera.setPreviewCallback(null);

            /* Stop camera preview. */
            if (mInPreview)
                mCamera.stopPreview();

            /* Release camera and nullify object. */
            mCamera.release();
            mCamera = null;
            /* We are no longer in preview mode. */
            mInPreview = false;
        }

        /* Remove camera surfaces. */
        mSurfaceHolder.removeCallback(this);
        this.surfaceDestroyed(mSurfaceHolder);
    }

    private void
    detectFace(byte[] bitmapBytes) {

        /* If camera was closed, immediately after a preview callback exit out, this is to prevent
         * NULL pointer exceptions when using the camera object later on.
         */
        if (null == mCamera || null == bitmapBytes)
            return;

        /* We need to stop camera preview callbacks from continuously being invoked while processing
         * is going on. Otherwise we would have a backlog of frames needing to be processed. To fix
         * this we remove preview callback, then re-enable it post-processing.
         *
         * - Preview callback invoked.
         * -- Tell camera to sto preview callbacks.
         * **** Meanwhile camera is still receiving frames, but continues to draw them. ****
         * -- Process camera preview frame.
         * -- Draw detected face Rect.
         * -- Tell camera to invoke preview callback with next frame.
         *
         * Using this technique does not drop camera frame-rate, so camera does not look "laggy".
         * Instead now we use every 5-th frame for face detection.
         */
        mCamera.setPreviewCallback(null);

        /* Need to fix color format of raw camera preview frames. */
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        Rect rect = new Rect(0, 0, P_WIDTH, P_HEIGHT);
        YuvImage yuvimage = new YuvImage(bitmapBytes, ImageFormat.NV21, P_WIDTH, P_HEIGHT, null);
        yuvimage.compressToJpeg(rect, 100, outStream);

//        try {
//            NImage image = NImage.fromStream(NStream.fromOutputStream(outStream));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        /* Save fixed color image as final good Bitmap. */
        Bitmap bm = BitmapFactory.decodeByteArray(outStream.toByteArray(), 0, outStream.size());

        /* On CredenceTWO device's captured image is rotated by 270 degrees. To fix this rotate
         * image by another 90 degrees to have it right-side-up.
         */
        bm = Utils.rotateBitmap(bm, 90);

        /* Detect face on finalized Bitmap image. */
        long start_time = System.currentTimeMillis();

        //TODO Call the detectFace from Neurotrech Here
        //detectFace(bm);
        recordFace(bm);

        if (null == mCamera || !mInPreview)
            return;

            /* Tell camera to start preview callbacks again. */
            mCamera.setPreviewCallback(mCameraPreviewCallback);
//        App.BioManager.detectFace(bm, (Biometrics.ResultCode resultCode,
//                                       RectF rectF) -> {
//            /* If camera was closed or preview stopped, immediately exit out. This is done so that
//             * we do not continue to process invalid frames, or draw to NULL surfaces.
//             */
//            if (null == mCamera || !mInPreview)
//                return;
//
//            /* Tell camera to start preview callbacks again. */
//            mCamera.setPreviewCallback(mCameraPreviewCallback);
//
//            if (resultCode == OK) {
//                /* Tell view that it will need to draw a detected face's Rect. region. */
//                mDrawingView.setHasFace(true);
//
//                Log.d(TAG, "Sample App detectFace time = " + (System.currentTimeMillis()-start_time));
//                /* If a CredenceTWO device then bounding Rect needs to be scaled to properly fit. */
//                mDrawingView.setFaceRect(rectF.left + 40,
//                        rectF.top - 25,
//                        rectF.right + 40,
//                        rectF.bottom - 50);
//
//            } else {
//                /* Tell view to not draw face Rect. region on next "onDraw()" call. */
//                mDrawingView.setHasFace(false);
//            }
//
//            /* Tell view to invoke an "onDraw()". */
//            mDrawingView.invalidate();
//        });
    }

    @SuppressLint("SetTextI18n")
    public void
    performTapToFocus(final Rect touchRect) {

        if (!mInPreview)
            return;

        final int one = 2000, two = 1000;

        /* Here we properly bound our Rect for a better tap to focus region */
        final Rect targetFocusRect = new Rect(
                touchRect.left * one / mDrawingView.getWidth() - two,
                touchRect.top * one / mDrawingView.getHeight() - two,
                touchRect.right * one / mDrawingView.getWidth() - two,
                touchRect.bottom * one / mDrawingView.getHeight() - two);

        /* Since Camera parameters only accept a List of  areas to focus, create a list. */
        final List<Camera.Area> focusList = new ArrayList<>();
        /* Convert Graphics.Rect to Camera.Rect for camera parameters to understand.
         * Add custom focus Rect. region to focus list.
         */
        focusList.add(new Camera.Area(targetFocusRect, 1000));

        /* Call mCamera AutoFocus and pass callback to be called when auto focus finishes */
        mCamera.autoFocus(mAutoFocusCallback);
        /* Tell our drawing view we have a touch in the given Rect */
        mDrawingView.setHasTouch(true, touchRect);
        /* Tell our drawing view to Update */
        mDrawingView.invalidate();
    }


    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        if (null == mCamera) {
            Log.w(TAG, "Camera object is null, will not set up preview.");
            return;
        }

        this.initPreview();
        this.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

        if (null == mCamera)
            return;

        if (mInPreview)
            mCamera.stopPreview();

        mCamera.release();
        mCamera = null;
        mInPreview = false;
    }

    /* --------------------------------------------------------------------------------------------
     *
     * Camera initialization, reset, close, etc.
     *
     * --------------------------------------------------------------------------------------------
     */

    private void
    initPreview() {

        if (null == mCamera || null == mSurfaceHolder.getSurface()) {
            Log.d(TAG, "Either camera or SurfaceHolder was null, skip initPreview().");
            return;
        }
        if (mIsCameraConfigured) {
            Log.d(TAG, "camera is already configured, no need to iniPreview().");
            return;
        }

        try {
            /* Tell camera object where to display preview frames. */
            mCamera.setPreviewDisplay(mSurfaceHolder);
            /* Initialize camera preview in proper orientation. */
            this.setCameraPreviewDisplayOrientation();

            /* Get camera parameters. We will edit these, then write them back to camera. */
            Camera.Parameters parameters = mCamera.getParameters();

            /* Enable auto-focus if available. */
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO))
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);

            /* For FaceEngine we show a preview with 320x240, but the actual image is
             * captured with largest available picture size, this way we get a high
             * resolution in final image.
             */
            Camera.Size picSize = Utils.getLargestPictureSize(parameters);
            parameters.setPictureSize(picSize.width, picSize.height);

            /* Regardless of what size is returned we always use a 320x240 preview size for face
             * detection since it is extremely fast.
             *
             * This previewSize is used to set up dimensions of all camera views.
             */
            Camera.Size previewSize = parameters.getPreviewSize();
            previewSize.width = P_WIDTH;
            previewSize.height = P_HEIGHT;

             mPreviewFrameLayout.getLayoutParams().width = (int) (previewSize.height * 2);
             mPreviewFrameLayout.getLayoutParams().height = (int) (previewSize.width  * 2);
             mPreviewFrameLayout.setAspectRatio((previewSize.width) / (double) (previewSize.height));

            ViewGroup.LayoutParams drawingViewLayoutParams = mDrawingView.getLayoutParams();


            ViewGroup.LayoutParams prevParams = mPreviewFrameLayout.getLayoutParams();
            drawingViewLayoutParams.width = prevParams.width;
            drawingViewLayoutParams.height = prevParams.height;
            mDrawingView.setLayoutParams(drawingViewLayoutParams);

            /* Need to set FaceEngine specific bitmap size so DrawingView knows
             * where and how to draw face detection points. Otherwise it would
             * assume the bitmap size is 0.
             */
            mDrawingView.setBitmapDimensions(P_WIDTH, P_HEIGHT);

            mCamera.setParameters(parameters);
            mIsCameraConfigured = true;
        } catch (Throwable t) {
            Log.e("PreviewDemo-Callback", "Exception in setPreviewDisplay()", t);
        }
    }

    private void
    startPreview() {

        if (mIsCameraConfigured && null != mCamera) {
            mPreviewFrameLayout.setVisibility(VISIBLE);
            mDrawingView.setVisibility(VISIBLE);
            mScannedImageView.setVisibility(VISIBLE);

            mCamera.startPreview();

            mInPreview = true;
        } else Log.w(TAG, "Camera not configured, aborting start preview.");
    }

    private void
    doPreview() {

        try {
            /* If camera was not already opened, open it. */
            if (null == mCamera) {
                if(Camera.getNumberOfCameras()>1){
                    mCamera = Camera.open(1);
                } else {
                    mCamera = Camera.open();
                }

                /* Tells camera to give us preview frames in these dimensions. */
                this.setPreviewSize(P_WIDTH, P_HEIGHT, (double) P_WIDTH / P_HEIGHT);
            }

            if (null != mCamera) {

                /* Tell camera where to draw frames to. */
                mCamera.setPreviewDisplay(mSurfaceHolder);
                /* Tell camera to invoke this callback on each frame. */
                mCamera.setPreviewCallback(mCameraPreviewCallback);
                /* Rotate preview frames to proper orientation based on DeviceType. */
                this.setCameraPreviewDisplayOrientation();
                /* Now we can tell camera to start preview frames. */
                this.startPreview();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start camera preview: " + e.getLocalizedMessage());
            if (null != mCamera)
                mCamera.release();

            mCamera = null;
            mInPreview = false;
        }
    }

    /* Tells camera to return preview frames in a certain width/height and aspect ratio.
     *
     * @param width Width of preview frames to send back.
     * @param height Height of preview frames to send back.
     * @param ratio Aspect ration of preview frames to send back.
     */
    @SuppressWarnings("SameParameterValue")
    private void
    setPreviewSize(int width,
                   int height,
                   double ratio) {

        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewSize(width, height);
        mPreviewFrameLayout.setAspectRatio(ratio);
        mCamera.setParameters(parameters);
    }


}

