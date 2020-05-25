package com.neurotec.tutorials.biometrics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import com.neurotec.biometrics.NBiometricCaptureOption;
import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NFace;
import com.neurotec.biometrics.NLAttributes;
import com.neurotec.biometrics.NLivenessAction;
import com.neurotec.biometrics.NLivenessMode;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.NTemplateSize;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.images.NImage;
import com.neurotec.images.NImageReader;
import com.neurotec.io.NBuffer;
import com.neurotec.lang.NThrowable;
import com.neurotec.media.NMediaSource;
import com.neurotec.samples.licensing.LicensingManager;
import com.neurotec.samples.util.NImageUtils;
import com.neurotec.util.NCollectionChangedAction;
import com.neurotec.util.concurrent.AggregateExecutionException;
import com.neurotec.util.event.NCollectionChangeEvent;
import com.neurotec.util.event.NCollectionChangeListener;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class CredenceHandlerThreadCustom extends Thread {

    private NBiometricClient mBiometricClient;
    private NSubject mNSubject;
    private boolean mIsNeurotecInit= false;
    private static final String[] LICENSES = new String[]{"FaceClient"};
    private Context mAppCtx;
    //public Handler mHandler;
    public static Handler sImageHandler = null;
    private static List<NLAttributes> sMonitorredAtributes;
    private static String TAG = CredenceLivenessDetection.TAG;
    private static NFace sNFace = new NFace();
    private static final String IMAGES_FOLDER_PATH = "Neurotechnology/Data/biometrics-tutorials/output/";

    public CredenceHandlerThreadCustom(Context appContext) {
        super();
        mAppCtx = appContext;

        sMonitorredAtributes = new ArrayList<>();

        try {
            List<String> obtainedLicenses = LicensingManager.getInstance().obtainLicenses(mAppCtx, LICENSES);
            if (obtainedLicenses.size() == LICENSES.length) {
                Log.d(CredenceLivenessDetection.TAG, String.valueOf(R.string.msg_licenses_obtained));
                Log.d(CredenceLivenessDetection.TAG, String.valueOf(R.string.msg_initialising));
                this.init();
                Log.d(TAG, String.valueOf("Init completed"));
                //TODO Add start capturing thread here
                // startCapturing(mSource, mRTSPUrl);
            } else {
                Log.d(CredenceLivenessDetection.TAG, String.valueOf(R.string.msg_licenses_partially_obtained));
            }
        }catch (Exception e) {
            Log.e(CredenceLivenessDetection.TAG, e.getMessage());
        }

        if(mIsNeurotecInit) {

            mBiometricClient.setFacesLivenessMode(NLivenessMode.SIMPLE);
            mBiometricClient.setFacesLivenessThreshold((byte) 5);

            Log.d(TAG, "Neurotec Face Engine detectFace - Task is ready");

        } else {
            Log.e(TAG, "Neurotec is not initialized");
        }
    }

    @SuppressLint("HandlerLeak")
    public void run() {
        Looper.prepare();

        sImageHandler = new Handler() {
            @Override
            public void handleMessage(Message inputMessage) {
                switch (inputMessage.what) {
                    case 0:
                        if(mIsNeurotecInit) {
                            Log.d(CredenceLivenessDetection.TAG, "Image received");
                            Bitmap[] imageStream = (Bitmap[]) inputMessage.obj;

                            Log.d(CredenceLivenessDetection.TAG, "Stream lentgh = " + imageStream.length);
                            Enroll(null, imageStream);

                        }else{
                            Log.e(TAG, "Neurotec is not initialized");
                        }
                        break;
                }
            }
        };
        Looper.loop();
    }

    public int startEnrolling(Bitmap[] imgStream){
        if(mIsNeurotecInit) {
            Log.d(CredenceLivenessDetection.TAG, "startEnrolling");
            return(Enroll(null, imgStream));

        }else{
            Log.e(TAG, "Neurotec is not initialized");
            return -3;
        }
    }

    private int Enroll(NMediaSource source, Bitmap[] imgStream) {

        int livenessScore = 0;
        sNFace = new NFace();
        NSubject subject = new NSubject();

        sNFace.setHasMoreSamples(true);
        subject.getFaces().add(sNFace);


        sNFace.setCaptureOptions(EnumSet.of(NBiometricCaptureOption.STREAM));
        //mBiometricClient.setFacesLivenessMode(NLivenessMode.ACTIVE);
        mBiometricClient.setFacesLivenessThreshold((byte)10);
        mBiometricClient.setFacesQualityThreshold((byte)10);
        mBiometricClient.setFacesLivenessMode(NLivenessMode.SIMPLE);


        try {

            NBiometricStatus status = NBiometricStatus.NONE;

            int j = 0;
            while (status == NBiometricStatus.NONE) {
                NImage image = NImage.fromBitmap(imgStream[j]);
                sNFace.setImage(image);
                File outputFile = new File(BiometricsTutorialsApp.TUTORIALS_OUTPUT_DATA_DIR, "face-image-from-camera-" + j + ".jpg");
                if(j<10){
                    outputFile = new File(BiometricsTutorialsApp.TUTORIALS_OUTPUT_DATA_DIR, "face-image-from-camera-0" + j + ".jpg");
                }
                sNFace.getImage().save(outputFile.getAbsolutePath());
                Log.d(TAG, "Face saved to " + outputFile.getAbsolutePath());

                j++;
                if (j >= imgStream.length) break;

            }

            // Start extraction from stream
            NBiometricTask task = mBiometricClient.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), subject);

            int i = 0;
            while (status != NBiometricStatus.OK) {
                long start = SystemClock.currentThreadTimeMillis();
                NImage image = NImage.fromBitmap(imgStream[i]);
                Log.d(TAG, "Table image time = " + (SystemClock.currentThreadTimeMillis()-start));
                Log.d(CredenceLivenessDetection.TAG, "Image traited: " + i);
                sNFace.setImage(image);
                try{
                    start = SystemClock.currentThreadTimeMillis();
                    mBiometricClient.performTask(task);
                    Log.d(TAG, "Table extraction time = " + (SystemClock.currentThreadTimeMillis()-start));
                    Throwable th = task.getError();
                    if (th != null) {
                        throw th;
                    }
                } catch (Throwable th){
                    Log.e(TAG, "Neurotec Client TASK ERROR: " + th);
                    Log.e(TAG, "Neurotec Client TASK ERROR Message: " + th.getMessage());
                    Log.e(TAG, "Neurotec Client TASK ERROR Cause: " + th.getCause());
                    livenessScore = -2;
                }
                status = task.getStatus();
                Log.d(TAG, "Task status = " + status.toString());
                image.dispose();
                i++;
                if (i >= imgStream.length) break;
            }


                // If loading was finished because MeadiaReaded had no more images we have to
            // finalize extraction by performing task after setting HasMoreSamples to false.
//            NImage image = NImage.fromBitmap(imgStream[i]);
//            if (image == null) {
//                mBiometricClient.performTask(task);
//                if (task.getError() != null) {
//                    throw task.getError();
//                }
//                status = task.getStatus();
//            }

            // Print extraction results
            if (status == NBiometricStatus.OK) {
                Log.d(TAG, "Template extracted.");
                for(NFace faces : subject.getFaces()) {
                    for (NLAttributes attributes : faces.getObjects()) {
                        Log.d(TAG, "Liveness Score = " + attributes.getLivenessScore());
                        livenessScore = attributes.getLivenessScore();
                    }
                }
            } else {
                Log.d(TAG, "Extraction failed: " + status);
                Throwable th = task.getError();
                if (th != null) {
                    throw th;
                }
            }


            // Reset HasMoreSamples value since we finished loading images
            sNFace.setHasMoreSamples(false);
            Uri[] filesUri = null;

            File dir = new File(Environment.getExternalStorageDirectory(), IMAGES_FOLDER_PATH);
            Log.d(TAG,dir.toString());
            if (dir.isDirectory()) {
                File[] files = dir.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        return pathname.isFile();
                    }
                });
                filesUri = new Uri[files.length];

                for (int k = 0; k < files.length; k++) {
                    filesUri[k] = Uri.fromFile(files[k]);
                    Log.d(TAG, "File loaded: " + filesUri[k].getLastPathSegment());
                }
            }

            sNFace = new NFace();
            subject= new NSubject();

            sNFace.setHasMoreSamples(true);


            subject.getFaces().add(sNFace);

            //mBiometricClient.setFacesLivenessMode(NLivenessMode.ACTIVE);
            mBiometricClient.setFacesLivenessThreshold((byte)1);
            mBiometricClient.setFacesLivenessMode(NLivenessMode.SIMPLE);

                // Start extraction from stream
                status = NBiometricStatus.NONE;
                task = mBiometricClient.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), subject);

                NImage image = NImageUtils.fromUri(mAppCtx, filesUri[0]);

                i = 1;
                while ((image != null) && (status == NBiometricStatus.NONE)) {
                    sNFace.setImage(image);
                    long start = SystemClock.currentThreadTimeMillis();
                    mBiometricClient.performTask(task);
                    Log.d(TAG, "URI extraction time = " + (SystemClock.currentThreadTimeMillis()-start));
                    Throwable th = task.getError();
                    if (th != null) {
                        throw th;
                    }
                    status = task.getStatus();
                    Log.d(TAG, "Image " + i + " - Task status = " + status.toString());
                    image.dispose();

                    if (i >= filesUri.length) break;
                    start = SystemClock.currentThreadTimeMillis();
                    image = NImageUtils.fromUri(mAppCtx, filesUri[i++]);
                    Log.d(TAG, "URI image time = " + (SystemClock.currentThreadTimeMillis()-start));
                }

                // Reset HasMoreSamples value since we finished loading images
                sNFace.setHasMoreSamples(false);

                // If loading was finished because MeadiaReaded had no more images we have to
                // finalize extraction by performing task after setting HasMoreSamples to false.
                if (image == null) {
                    mBiometricClient.performTask(task);
                    if (task.getError() != null) {
                        throw task.getError();
                    }
                    status = task.getStatus();
                }

                // Print extraction results
                if (status == NBiometricStatus.OK) {
                    Log.d(TAG, "Template extracted.");
                    for(NFace faces : subject.getFaces()) {
                        for (NLAttributes attributes : faces.getObjects()) {
                            Log.d(TAG, "Liveness Score = " + attributes.getLivenessScore());
                        }
                    }
                }  else {
                    Log.d(TAG,"Extraction failed: " + status);
                    Throwable th = task.getError();
                    if (th != null) {
                        throw th;
                    }
                }


            } catch (Throwable th) {
            Log.e(TAG, "ERROR: " + th);
            Log.e(TAG, "Message: " + th.getMessage());
            Log.e(TAG, "Cause: " + th.getCause());
            handleError(th);
        } finally {
            if (sNFace != null) {
                sNFace.dispose();
            }
            if (subject != null) {
                subject.dispose();
            }
        }
        return livenessScore;
    }

    public static void handleError(Throwable th) {
        if (th == null)
            throw new NullPointerException("th");
        int errorCode = -1;
        if (th instanceof NThrowable) {
            errorCode = handleNThrowable((NThrowable) th);
        } else if (th.getCause() instanceof NThrowable) {
            errorCode = handleNThrowable((NThrowable) th.getCause());
        }
        th.printStackTrace();
        System.exit(errorCode);
    }

    private static int handleNThrowable(NThrowable th) {
        int errorCode = -1;
        if (th instanceof AggregateExecutionException) {
            List<Throwable> causes = ((AggregateExecutionException) th).getCauses();
            for (Throwable cause : causes) {
                Log.e(TAG, "Throwable message " + cause.getMessage());
                Log.e(TAG, "Throwable Cause " + cause.getCause());
                if (cause instanceof NThrowable) {
                    if (cause.getCause() instanceof NThrowable) {
                        errorCode = handleNThrowable((NThrowable) cause.getCause());
                    } else {
                        errorCode = ((NThrowable) cause).getCode();
                    }
                    break;
                }
            }
        } else {
            errorCode = ((NThrowable) th).getCode();
        }
        return errorCode;
    }

//    private void init() {
//        mBiometricClient = new NBiometricClient();
//        // Detect all face features
//        if (LICENSES[0].equals("FaceClient") || LICENSES[0].equals("FaceFastExtractor")) {
//            mBiometricClient.setFacesDetectAllFeaturePoints(true);
//        }
//        // Initialize NBiometricClient
//        mBiometricClient.initialize();
//        Log.d(TAG, "mBiometricClient initialized");
//    }

    private void init() {

        Log.i(CredenceLivenessDetection.TAG, "Initialisation started");
        mBiometricClient = new NBiometricClient();
        mBiometricClient.setFacesDetectAllFeaturePoints(true);
        // Initialize NBiometricClient
        mBiometricClient.initialize();
        mNSubject = new NSubject();
        mIsNeurotecInit = true;
    }

 }
