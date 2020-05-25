package com.neurotec.tutorials.biometrics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NFace;
import com.neurotec.biometrics.NLAttributes;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.biometrics.view.NFaceView;
import com.neurotec.images.NImage;
import com.neurotec.io.NFile;
import com.neurotec.lang.NCore;
import com.neurotec.samples.app.BaseActivity;
import com.neurotec.samples.app.DirectoryViewer;
import com.neurotec.samples.licensing.LicensingManager;
import com.neurotec.samples.util.NImageUtils;
import com.neurotec.tutorials.biometrics.camera.DrawingView;
import com.neurotec.tutorials.biometrics.camera.PreviewFrameLayout;
import com.neurotec.tutorials.biometrics.camera.Utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static android.view.View.VISIBLE;

public class CredencePerformancesTest extends BaseActivity {


    private static final String TAG = EnrollFaceFromImage.class.getSimpleName();
    private static final int REQUEST_CODE_GET_IMAGE = 1;

    //=========================================================================
    // CHOOSE LICENCES !!!
    //=========================================================================
    // ONE of the below listed licenses is required for unlocking this sample's functionality. Choose a license that you currently have on your device.
    // If you are using a TRIAL version - choose any of them.

    //private static final String[] LICENSES = new String[]{"FaceExtractor"};
    private static final String[] LICENSES = new String[]{"FaceClient"};
    //private static final String[] LICENSES = new String[]{"FaceFastExtractor"};

    //=========================================================================

    private Button mButton;
    private TextView mResult;
    private NBiometricClient mBiometricClient;
    private static Uri sUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tutorial_enroll_face_from_image);
        mButton = (Button) findViewById(R.id.tutorials_button_1);
        mButton.setText(R.string.msg_select_image);
        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getImage();
            }
        });
        mResult = (TextView) findViewById(R.id.tutorials_results);

        enableControls(false);
        new CredencePerformancesTest.InitializationTask().execute();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_GET_IMAGE) {
            if (resultCode == RESULT_OK) {
                try {
                    sUri = data.getData();
                    new templateCreationTask().execute();
                } catch (Exception e) {
                    showMessage(e.getMessage());
                    Log.e(TAG, "Exception", e);
                }
            }
        }
    }

    final class templateCreationTask extends AsyncTask<Object, Boolean, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgress("Creating templates");
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            try {
                enrollFromUri(sUri);
                enrollFromUri(sUri);
                enrollFromUri(sUri);
                enrollFromUri(sUri);
                enrollFromBitmap(sUri);
                enrollFromBitmap(sUri);
                enrollFromBitmap(sUri);
                enrollFromBitmap(sUri);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            hideProgress();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideProgress();
        if (mBiometricClient != null) {
            mBiometricClient.cancel();
            mBiometricClient.dispose();
            mBiometricClient = null;
        }
    }

    private void init() {
        mBiometricClient = new NBiometricClient();
        // Detect all face features
        if (LICENSES[0].equals("FaceClient") || LICENSES[0].equals("FaceFastExtractor")) {
            mBiometricClient.setFacesDetectAllFeaturePoints(true);
        }
        // Initialize NBiometricClient
        mBiometricClient.initialize();
    }

    private void enableControls(final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mButton.setEnabled(enable);
            }
        });
    }

    private void getImage() {
        Intent intent = new Intent(this, DirectoryViewer.class);
        intent.putExtra(DirectoryViewer.ASSET_DIRECTORY_LOCATION, BiometricsTutorialsApp.TUTORIALS_ASSETS_DIR);
        startActivityForResult(intent, REQUEST_CODE_GET_IMAGE);
    }

    private void showMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mResult.append(message + "\n");
            }
        });
    }

    private void enrollFromUri(Uri imageUri) throws IOException {
        NSubject subject = null;
        NFace face = null;
        NBiometricTask task = null;
        NBiometricStatus status = null;

        try {
            subject = new NSubject();
            face = new NFace();

            // Read face image and add it to NFace object
            long start = SystemClock.currentThreadTimeMillis();
            face.setImage(NImageUtils.fromUri(this, imageUri));

            // Add face image to NSubject
            subject.getFaces().add(face);

            // Create task
            task = mBiometricClient.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), subject);

            // Perform task
            mBiometricClient.performTask(task);
            status = task.getStatus();
            if (task.getError() != null) {
                showError(task.getError());
                return;
            }
            showMessage("Template creation time from URI : " + (SystemClock.currentThreadTimeMillis()-start));
            Log.d(TAG, "Template creation time from URI : " + (SystemClock.currentThreadTimeMillis()-start));

            if (subject.getFaces().size() > 1)
                showMessage(String.format("Found %d faces\n", subject.getFaces().size() - 1));

            // List attributes for all located faces
            for (NFace nface : subject.getFaces()) {
                for (NLAttributes attribute : nface.getObjects()) {
                    Rect rect = attribute.getBoundingRect();
                    showMessage(getString(R.string.msg_face_found));
                    showMessage(getString(R.string.format_face_rect, rect.left, rect.top, rect.right, rect.bottom));

                    if ((attribute.getRightEyeCenter().confidence > 0) || (attribute.getLeftEyeCenter().confidence > 0)) {
                        showMessage(getString(R.string.msg_eyes_found));
                        if (attribute.getRightEyeCenter().confidence > 0) {
                            showMessage(getString(R.string.format_first_eye_location_confidence,
                                    attribute.getRightEyeCenter().x, attribute.getRightEyeCenter().y, attribute.getRightEyeCenter().confidence));
                        }
                        if (attribute.getLeftEyeCenter().confidence > 0) {
                            showMessage(getString(R.string.format_second_eye_location_confidence,
                                    attribute.getLeftEyeCenter().x, attribute.getLeftEyeCenter().y, attribute.getLeftEyeCenter().confidence));
                        }
                    }
                    if (LICENSES[0].equals("FaceClient") || LICENSES[0].equals("FaceFastExtractor")) {
                        if (attribute.getNoseTip().confidence > 0) {
                            showMessage(getString(R.string.msg_nose_found));
                            showMessage(getString(R.string.format_location_confidence,
                                    attribute.getNoseTip().x, attribute.getNoseTip().y, attribute.getNoseTip().confidence));
                        }
                        if (attribute.getMouthCenter().confidence > 0) {
                            showMessage(getString(R.string.msg_mouth_found));
                            showMessage(getString(R.string.format_location_confidence,
                                    attribute.getMouthCenter().x, attribute.getMouthCenter().y, attribute.getMouthCenter().confidence));
                        }
                    }
                }
            }

            if (status == NBiometricStatus.OK) {
                // Save template to file
                File outputFile = new File(BiometricsTutorialsApp.TUTORIALS_OUTPUT_DATA_DIR, "nltemplate-from-image.dat");
                NFile.writeAllBytes(outputFile.getAbsolutePath(), subject.getTemplate().save());
                showMessage(getString(R.string.format_face_template_saved_to, outputFile.getAbsolutePath()));
            } else {
                showMessage(getString(R.string.format_extraction_failed, status.toString()));
            }
        } finally {
            if (subject != null) subject.dispose();
            if (face != null) face.dispose();
        }
    }

    private void enrollFromBitmap(Uri imageUri) throws IOException {
        NSubject subject = null;
        NFace face = null;
        NBiometricTask task = null;
        NBiometricStatus status = null;

        try {
            subject = new NSubject();
            face = new NFace();

            InputStream imageStrm = getApplication().getAssets().open("input/" + imageUri.getLastPathSegment());
            Bitmap bm = BitmapFactory.decodeStream(imageStrm);

            // Read face image and add it to NFace object
            long start = SystemClock.currentThreadTimeMillis();
            NImage image = NImage.fromBitmap(bm);
            face.setImage(image);

            // Add face image to NSubject
            subject.getFaces().add(face);

            // Create task
            task = mBiometricClient.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), subject);

            // Perform task
            mBiometricClient.performTask(task);
            status = task.getStatus();
            if (task.getError() != null) {
                showError(task.getError());
                return;
            }
            showMessage("Template creation time from Bitmap : " + (SystemClock.currentThreadTimeMillis()-start));
            Log.d(TAG, "Template creation time from Bitmap : " + (SystemClock.currentThreadTimeMillis()-start));

            if (subject.getFaces().size() > 1)
                showMessage(String.format("Found %d faces\n", subject.getFaces().size() - 1));

            // List attributes for all located faces
            for (NFace nface : subject.getFaces()) {
                for (NLAttributes attribute : nface.getObjects()) {
                    Rect rect = attribute.getBoundingRect();
                    showMessage(getString(R.string.msg_face_found));
                    showMessage(getString(R.string.format_face_rect, rect.left, rect.top, rect.right, rect.bottom));

                    if ((attribute.getRightEyeCenter().confidence > 0) || (attribute.getLeftEyeCenter().confidence > 0)) {
                        showMessage(getString(R.string.msg_eyes_found));
                        if (attribute.getRightEyeCenter().confidence > 0) {
                            showMessage(getString(R.string.format_first_eye_location_confidence,
                                    attribute.getRightEyeCenter().x, attribute.getRightEyeCenter().y, attribute.getRightEyeCenter().confidence));
                        }
                        if (attribute.getLeftEyeCenter().confidence > 0) {
                            showMessage(getString(R.string.format_second_eye_location_confidence,
                                    attribute.getLeftEyeCenter().x, attribute.getLeftEyeCenter().y, attribute.getLeftEyeCenter().confidence));
                        }
                    }
                    if (LICENSES[0].equals("FaceClient") || LICENSES[0].equals("FaceFastExtractor")) {
                        if (attribute.getNoseTip().confidence > 0) {
                            showMessage(getString(R.string.msg_nose_found));
                            showMessage(getString(R.string.format_location_confidence,
                                    attribute.getNoseTip().x, attribute.getNoseTip().y, attribute.getNoseTip().confidence));
                        }
                        if (attribute.getMouthCenter().confidence > 0) {
                            showMessage(getString(R.string.msg_mouth_found));
                            showMessage(getString(R.string.format_location_confidence,
                                    attribute.getMouthCenter().x, attribute.getMouthCenter().y, attribute.getMouthCenter().confidence));
                        }
                    }
                }
            }

            if (status == NBiometricStatus.OK) {
                // Save template to file
                File outputFile = new File(BiometricsTutorialsApp.TUTORIALS_OUTPUT_DATA_DIR, "nltemplate-from-image.dat");
                NFile.writeAllBytes(outputFile.getAbsolutePath(), subject.getTemplate().save());
                showMessage(getString(R.string.format_face_template_saved_to, outputFile.getAbsolutePath()));
            } else {
                showMessage(getString(R.string.format_extraction_failed, status.toString()));
            }
        } finally {
            if (subject != null) subject.dispose();
            if (face != null) face.dispose();
        }
    }

    final class InitializationTask extends AsyncTask<Object, Boolean, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgress(R.string.msg_obtaining_licenses);
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            try {
                List<String> obtainedLicenses = LicensingManager.getInstance().obtainLicenses(getApplicationContext(), LICENSES);
                if (obtainedLicenses.size() == LICENSES.length) {
                    showToast(R.string.msg_licenses_obtained);
                    showProgress(R.string.msg_initialising);
                    init();
                    enableControls(true);
                } else {
                    showToast(R.string.msg_licenses_partially_obtained);
                }
            } catch (Exception e) {
                showError(e.getMessage(), false);
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            hideProgress();
        }
    }

}
