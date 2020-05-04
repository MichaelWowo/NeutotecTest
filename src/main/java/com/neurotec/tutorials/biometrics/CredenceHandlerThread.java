package com.neurotec.tutorials.biometrics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
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
import com.neurotec.devices.NCamera;
import com.neurotec.images.NImage;
import com.neurotec.samples.licensing.LicensingManager;
import com.neurotec.util.NCollectionChangedAction;
import com.neurotec.util.event.NCollectionChangeEvent;
import com.neurotec.util.event.NCollectionChangeListener;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import javax.net.ssl.SSLContext;

public class CredenceHandlerThread extends Thread {

    Handler handler;
    private NBiometricClient mBiometricClient;
    private NSubject mNSubject;
    private boolean mIsNeurotecInit= false;
    private static final String[] LICENSES = new String[]{"FaceClient"};
    private Context mAppCtx;
    //public Handler mHandler;
    private static NFace sFace = new NFace();
    private static NSubject sSubject = new NSubject();
    private static NBiometricTask sTask = null;
    private static NBiometricStatus sNStatus = NBiometricStatus.NONE;
    public static Handler sImageHandler = null;
    private static List<NLAttributes> sMonitorredAtributes;
    private static String TAG = CredenceLivenessDetection.TAG;

    public CredenceHandlerThread(Context appContext) {
        super();
        mAppCtx = appContext;

        try {
            List<String> obtainedLicenses = LicensingManager.getInstance().obtainLicenses(mAppCtx, LICENSES);
            if (obtainedLicenses.size() == LICENSES.length) {
                Log.d(CredenceLivenessDetection.TAG, String.valueOf(R.string.msg_licenses_obtained));
                Log.d(CredenceLivenessDetection.TAG, String.valueOf(R.string.msg_initialising));
                this.init();
                //TODO Add start capturing thread here
                // startCapturing(mSource, mRTSPUrl);
            } else {
                Log.d(CredenceLivenessDetection.TAG, String.valueOf(R.string.msg_licenses_partially_obtained));
            }
        }catch (Exception e) {
            Log.e(CredenceLivenessDetection.TAG, e.getMessage());
        }

        sFace.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent propertyChangeEvent) {

                Log.d(CredenceLivenessDetection.TAG, "propertyChangeEvent");

                repaint();
            }
        });

        sFace.getObjects().addCollectionChangeListener(new NCollectionChangeListener() {
            @Override
            public void collectionChanged(NCollectionChangeEvent nCollectionChangeEvent) {

                Log.d(CredenceLivenessDetection.TAG, "nCollectionChangeEvent");

                /*if(nCollectionChangeEvent.getAction() == NCollectionChangedAction.RESET){
                    sMonitorredAtributes.clear();
                } else {
                    if(nCollectionChangeEvent.getAction() == NCollectionChangedAction.ADD)
                    {
                        for ( Object item:nCollectionChangeEvent.getNewItems()
                             ) {
                            NLAttributes attributes = (NLAttributes) item;
                            sMonitorredAtributes.add(attributes);
                            attributes.addPropertyChangeListener(new PropertyChangeListener() {
                                @Override
                                public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
                                    repaint();
                                }
                            });
                        }
                    }
                    else
                    {
                        if(nCollectionChangeEvent.getAction() == NCollectionChangedAction.REMOVE)
                        {

                            for ( Object item:nCollectionChangeEvent.getNewItems()
                            ) {
                                NLAttributes attributes = (NLAttributes) item;
                                sMonitorredAtributes.remove(attributes);

                            }
                        }
                    }
                }*/
                repaint();
            }
        });



        //Set face template size (recommended, for enroll to database, is large) (optional)
        mBiometricClient.setFacesTemplateSize(NTemplateSize.LARGE);
        mBiometricClient.setFacesLivenessMode(NLivenessMode.ACTIVE);
        mBiometricClient.setFacesLivenessThreshold((byte)50);

        sNStatus = NBiometricStatus.NONE;
        sSubject.getFaces().add(sFace);
        NImage image = null;
        sTask = mBiometricClient.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), sSubject);
    }

    @SuppressLint("HandlerLeak")
    public void run() {
        Looper.prepare();

        sImageHandler = new Handler() {
            @Override
            public void handleMessage(Message inputMessage) {
                switch (inputMessage.what) {
                    case 0:
                        Log.d(CredenceLivenessDetection.TAG, "Image received");
                        Bitmap image = (Bitmap)inputMessage.obj;
                        NImage nFaceImage = NImage.fromBitmap(image);
                        sFace.setImage(nFaceImage);
                        mBiometricClient.performTask(sTask);
                        Throwable taskError = sTask.getError();
                        if (taskError != null) {
                            Log.e(CredenceLivenessDetection.TAG, "mBiometricClient ERROR : " + taskError.getMessage());
                        }
                        Log.d(CredenceLivenessDetection.TAG, "sImageHandler sTask.getStatus() = " + sTask.getStatus());
                        Log.d(TAG, "sImageHandler sTask subject Size =" + sTask.getSubjects().size());
                        Log.d(TAG, "sImageHandler sSubject getFace Size =" + sSubject.getFaces().size());
                        break;
                }
            }
        };
        Looper.loop();
    }


    static void repaint()
    {
        Log.d(CredenceLivenessDetection.TAG, "repaint, sFace Size = " + sFace.getObjects().size());
        Log.d(CredenceLivenessDetection.TAG, "repaint, sSubject.getFaces() Size = " + sSubject.getFaces().size());
        NLAttributes[] attributesArray = (NLAttributes[])sFace.getObjects().toArray();
        Log.d(CredenceLivenessDetection.TAG, "repaint, attributesArray Size = " + attributesArray.length);
        for (int i = 0; i < attributesArray.length; i++)
        {
            NLAttributes attributes = attributesArray[i];
            EnumSet<NLivenessAction> action = attributes.getLivenessAction();
            Log.d(CredenceLivenessDetection.TAG, "repaint, attributes action size = " + attributes.getLivenessAction().size());
            if(attributes.getLivenessAction().size()>1){
                Log.d(CredenceLivenessDetection.TAG, "repaint, attributes action = " + attributes.getLivenessAction().toArray()[0].toString());
            }
            byte score = attributes.getLivenessScore();
            boolean rotation = action.equals(NLivenessAction.ROTATE_YAW);
            boolean blink = action.equals(NLivenessAction.BLINK);
            boolean keepStill = action.equals(NLivenessAction.KEEP_STILL);

            if (rotation)
            {
                float yaw = attributes.getYaw();
                float targetYaw = attributes.getLivenessTargetYaw();
                if (targetYaw > yaw)
                {
                    Log.i(CredenceLivenessDetection.TAG, "rotate right");
                }
                if (yaw > targetYaw)
                {
                    Log.i(CredenceLivenessDetection.TAG, "rotate left");
                }
            }

            if (blink)
                Log.i(CredenceLivenessDetection.TAG, "Blink");

            if (keepStill)
            {
                Log.i(CredenceLivenessDetection.TAG, "Keep still");
            }
        }
    }


    private void init() {

        Log.i(CredenceLivenessDetection.TAG, "Initialisation started");
        mBiometricClient = new NBiometricClient();
        mBiometricClient.setFacesDetectAllFeaturePoints(true);
        mBiometricClient.setFacesDetectBaseFeaturePoints(true);
        mBiometricClient.setFacesRecognizeExpression(true);
        mBiometricClient.setFacesDetectProperties(true);
        mBiometricClient.setFacesDetermineGender(true);
        mBiometricClient.setFacesDetermineAge(true);
        // Initialize NBiometricClient
        mBiometricClient.initialize();
        mNSubject = new NSubject();
        mIsNeurotecInit = true;
    }
}
