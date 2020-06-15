package com.neurotec.tutorials.biometrics;

import android.app.Activity;
import android.app.ProgressDialog;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NFinger;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.NTemplateSize;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.biometrics.standards.CBEFFBDBFormatIdentifiers;
import com.neurotec.biometrics.standards.CBEFFBiometricOrganizations;
import com.neurotec.biometrics.standards.FMRecord;
import com.neurotec.images.NImage;
import com.neurotec.io.NFile;
import com.neurotec.lang.NCore;
import com.neurotec.licensing.NLicense;
import com.neurotec.licensing.NLicenseInfo;
import com.neurotec.licensing.NLicenseProductInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.EnumSet;

public class ManageLicenseSnFile extends Activity {

    private final String TAG = "CID";


    public static final String LICENSE_FINGER_DETECTION = "Biometrics.FingerDetection";
    public static final String LICENSE_FINGER_EXTRACTION = "Biometrics.FingerExtraction";
    public static final String LICENSE_FINGER_MATCHING = "Biometrics.FingerMatching";
    public static final String LICENSE_FINGER_MATCHING_FAST = "Biometrics.FingerMatchingFast";
    public static final String LICENSE_FINGER_STANDARDS_FINGERS = "Biometrics.Standards.Fingers";
    public static final String LICENSE_FINGER_STANDARDS_FINGER_TEMPLATES = "Biometrics.Standards.FingerTemplates";
    public static final String LICENSE_FINGER_DEVICES_SCANNERS = "Devices.FingerScanners";
    public static final String LICENSE_FINGER_WSQ = "Images.WSQ";

    private static final String[] PRODUCT_LICENSES = new String[]{"FingerClient"};
//    private static final String[] LICENSES = new String[]{
//            LICENSE_FINGER_DETECTION,
//            LICENSE_FINGER_EXTRACTION,
//            LICENSE_FINGER_MATCHING,
//            LICENSE_FINGER_MATCHING_FAST,
//            LICENSE_FINGER_STANDARDS_FINGERS,
//            LICENSE_FINGER_STANDARDS_FINGER_TEMPLATES,
//            LICENSE_FINGER_DEVICES_SCANNERS,
//            LICENSE_FINGER_WSQ
//    };

    private final String BASE_FOLDER = "/sdcard/Neurotechnology/";
    private final String SN_FILE_LOCATION = BASE_FOLDER+ "Sn/";
    private final String ID_FILE_LOCATION = BASE_FOLDER+ "Id/";
    private final String LIC_FILE_LOCATION = BASE_FOLDER+ "Licenses/";

    private NBiometricClient mBiometricClient;

    private TextView tvTextResult = null;
    private Button btnGenerateId = null;
    private Button btnGenerateLic = null;
    private Button btnActivateLic = null;
    private Button btnObtainComp = null;
    private Button btnInitClient = null;
    private Button btnExtractFp = null;
    private Button btnDeactivateLic = null;
    private Button btnLicInfo = null;

    private final String ADDRESS = "/local";
    private final String PORT = "5000";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NCore.setContext(this);
        setContentView(R.layout.manage_licenses_sn_files);

        this.initializeLayoutComponents();
    }


    /* Initializes all layout file component objects. */
    private void
    initializeLayoutComponents() {

        tvTextResult = findViewById(R.id.textView);

        btnGenerateId = findViewById(R.id.buttonGenerateID);
        btnGenerateId.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                generateId();
            }
        });

        btnGenerateLic = findViewById(R.id.buttonGenerateLic);
        btnGenerateLic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                generateLic();
            }
        });

        btnActivateLic = findViewById(R.id.buttonActivateLic);
        btnActivateLic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                activateLic();
            }
        });

        btnObtainComp = findViewById(R.id.buttonObtainComp);
        btnObtainComp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ObtainComponentsTask obtainComponentsTask = new ObtainComponentsTask();
                obtainComponentsTask.execute();
            }
        });

        btnInitClient = findViewById(R.id.buttonInitClient);
        btnInitClient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                InitializationTask intiTask = new InitializationTask();
                intiTask.execute();
            }
        });

        btnExtractFp = findViewById(R.id.buttonExtractFp);
        btnExtractFp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Uri uri = Uri.parse("/sdcard/fingerprint.png");
                try {
                    enroll(uri);
                } catch (IOException e) {
                    showError("ERROR - enroll failed");
                    showError("ERROR: " + e.toString());
                    e.printStackTrace();
                }
            }
        });
        btnExtractFp.setEnabled(false);

        btnDeactivateLic = findViewById(R.id.buttonDeactivateLicence);
        btnDeactivateLic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deactivateLic();
            }
        });

        btnLicInfo = findViewById(R.id.buttonLicInfo);
        btnLicInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getLicenseInfo();
            }
        });

    }

    private void generateId(){

        File snFolder = new File(SN_FILE_LOCATION);

        File[] snList = snFolder.listFiles();

        if(snList.length != 1) {
        }
        else {
            try {
                FileReader snReader = new FileReader(snList[0]);
                String string = "";
                StringBuilder stringBuilder = new StringBuilder();
                BufferedReader reader = new BufferedReader(snReader);
                while (true) {
                    try {
                        if ((string = reader.readLine()) == null) break;
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                    stringBuilder.append(string).append("\n");
                }
                String snValue = stringBuilder.toString();
                Log.d(TAG, "snValue = " + snValue );
                String ID = NLicense.generateID(snValue);
                showMessage("RESULT: ID generation Success");
                File idFile = new File(ID_FILE_LOCATION + snList[0].getName() + ".id");
                if (!idFile.exists()) {
                    idFile.createNewFile();
                }
                OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(idFile, false),
                        "windows-1252");
                writer.write(ID);
                writer.close();
                showMessage("ID File: " + idFile.getAbsolutePath());
            } catch (IOException e) {
                showError("ERROR: ID generation failed");
                showError("REASON: " + e.toString());
            }


        }
    }

    private void generateLic(){

        File idFolder = new File(ID_FILE_LOCATION);

        File[] idList = idFolder.listFiles();

        if(idList.length != 1) {
        }
        else {
            try {
                String str ="";
                FileReader idReader = new FileReader(idList[0]);
                StringBuilder stringBuilder = new StringBuilder();
                BufferedReader reader = new BufferedReader(idReader);
                while (true) {
                    try {
                        if ((str = reader.readLine()) == null) break;
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                    stringBuilder.append(str).append("\n");
                }
                String id = stringBuilder.toString();
                Log.d(TAG, "id = " + id );
                String lic = NLicense.activateOnline(id);
                showMessage(idList[0].getAbsolutePath() +" activated");
                showMessage("Lic generated");

                File licFile = new File(LIC_FILE_LOCATION + idList[0].getName() + ".lic");
                if (!licFile.exists()) {
                    licFile.createNewFile();
                }
                OutputStreamWriter writer = new OutputStreamWriter(
                        new FileOutputStream(licFile, false),
                        "windows-1252");
                writer.write(lic);
                writer.close();
                showMessage("Lic saved - " + licFile.getAbsolutePath());

            } catch (FileNotFoundException e) {
                showError("ERROR: Lic activation online failed");
                showError("REASON: " + e.toString());
                return;
            } catch (IOException e) {
                showError("ERROR: IO - Lic activation online  failed");
                showError("REASON: " + e.toString());
                return;
            }catch (Exception e){
                showError("ERROR: Lic activation online  failed");
                showError("REASON: " + e.toString());
                return;
            }
            showMessage("RESULT: Lic activation Success");
        }
    }

    private void deactivateLic(){

        File licFolder = new File(LIC_FILE_LOCATION);

        File[] licList = licFolder.listFiles();

        if(licList.length> 0){
            for (File lic: licList
            ) {
                try {
                    showMessage("LIC deactivation: " + lic.getAbsolutePath() + " to deactivate");
                    String str ="";
                    FileReader snReader = new FileReader(lic);
                    StringBuilder stringBuilder = new StringBuilder();
                    BufferedReader reader = new BufferedReader(snReader);
                    while (true) {
                        try {
                            if ((str = reader.readLine()) == null) break;
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        }
                        stringBuilder.append(str).append("\n");
                    }
                    String license = stringBuilder.toString();
                    Log.d(TAG, "license = " + license );
                    for(String product: PRODUCT_LICENSES){
                        NLicense.release(product);
                    }
                    NLicense.deactivateOnline(license);
                    showMessage(lic.getAbsolutePath() +" deactivated");
                    lic.delete();

                } catch (FileNotFoundException e) {
                    showError("ERROR: Lic deactivated failed");
                    showError("REASON: " + e.toString());
                    return;
                } catch (IOException e) {
                    showError("ERROR: IO - Lic deactivated failed");
                    showError("REASON: " + e.toString());
                    return;
                }catch (Exception e){
                    showError("ERROR: Lic deactivated failed");
                    showError("REASON: " + e.toString());
                    return;
                }
                showMessage("RESULT :Lic deactivated Success");

            }
        }else {
            showError("ERROR: No Lice file found");
        }
    }

    private void activateLic(){

        File licFolder = new File(LIC_FILE_LOCATION);

        File[] licList = licFolder.listFiles();

        if(licList.length> 0){
            for (File lic: licList
                 ) {
                try {
                    showMessage("LIC Activation: " + lic.getAbsolutePath() + " to activate");
                    String str ="";
                    FileReader snReader = new FileReader(lic);
                    StringBuilder stringBuilder = new StringBuilder();
                    BufferedReader reader = new BufferedReader(snReader);
                    while (true) {
                        try {
                            if ((str = reader.readLine()) == null) break;
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        }
                        stringBuilder.append(str).append("\n");
                    }
                    String license = stringBuilder.toString();
                    Log.d(TAG, "license = " + license );
                    NLicense.add(license);
                    showMessage(lic.getAbsolutePath() +" added");

                } catch (FileNotFoundException e) {
                    showError("ERROR: Lic activation failed");
                    showError("REASON: " + e.toString());
                    return;
                } catch (IOException e) {
                    showError("ERROR: IO - Lic activation failed");
                    showError("REASON: " + e.toString());
                    return;
                }catch (Exception e){
                    showError("ERROR: Lic activation failed");
                    showError("REASON: " + e.toString());
                    return;
                }
                showMessage("RESULT :Lic activation Success");

            }
        }else {
            showError("ERROR: No Lice file found");
        }
    }

    private void getLicenseInfo(){

        File licFolder = new File(LIC_FILE_LOCATION);

        File[] licList = licFolder.listFiles();

        if(licList.length> 0){
            for (File lic: licList
            ) {
                try {
                    showMessage("LIC found: " + lic.getAbsolutePath());
                    String str ="";
                    FileReader snReader = new FileReader(lic);
                    StringBuilder stringBuilder = new StringBuilder();
                    BufferedReader reader = new BufferedReader(snReader);
                    while (true) {
                        try {
                            if ((str = reader.readLine()) == null) break;
                        }
                        catch (IOException e) {
                            e.printStackTrace();
                        }
                        stringBuilder.append(str).append("\n");
                    }
                    String license = stringBuilder.toString();
                    Log.d(TAG, "license = " + license );
                    NLicenseInfo licInfo = NLicense.getLicenseInfoOnline(license);
                    showMessage(lic.getAbsolutePath() + " info:");
                    for(NLicenseProductInfo productInfo : licInfo.getLicenses()){
                        showMessage(productInfo.getLicenseType().name());
                    }


                } catch (FileNotFoundException e) {
                    showError("ERROR: Lic info failed");
                    showError("REASON: " + e.toString());
                    return;
                } catch (IOException e) {
                    showError("ERROR: IO - Lic info failed");
                    showError("REASON: " + e.toString());
                    return;
                }catch (Exception e){
                    showError("ERROR: Lic info failed");
                    showError("REASON: " + e.toString());
                    return;
                }

            }
        }else {
            showError("ERROR: No Lic file found");
        }
    }

    private void enroll(Uri imageUri) throws IOException {
        NSubject subject = null;
        NFinger finger = null;
        NBiometricTask task = null;
        NBiometricStatus status = null;

        try {
            subject = new NSubject();
            finger = new NFinger();

            // Read finger image and add it to NFinger object
            NImage nFpImage =  NImage.fromFile(imageUri.getPath());
            nFpImage.setHorzResolution(500);
            nFpImage.setVertResolution(500);
            finger.setImage(nFpImage);

            // Add finger image to NSubject
            subject.getFingers().add(finger);

            // Create task
            task = mBiometricClient.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), subject);

            // Perform task
            mBiometricClient.performTask(task);
            status = task.getStatus();
            if (task.getError() != null) {
                showError(task.getError().toString());
                return;
            }

            if (status == NBiometricStatus.OK) {
                // Save template to file
                File outputFile = new File(BiometricsTutorialsApp.TUTORIALS_OUTPUT_DATA_DIR, "nfrecord-from-image.dat");
                NFile.writeAllBytes(outputFile.getAbsolutePath(), subject.getTemplateBuffer(CBEFFBiometricOrganizations.ISO_IEC_JTC_1_SC_37_BIOMETRICS,
                        CBEFFBDBFormatIdentifiers.ISO_IEC_JTC_1_SC_37_BIOMETRICS_FINGER_MINUTIAE_RECORD_FORMAT,
                        FMRecord.VERSION_ISO_CURRENT));

                showMessage("Template created");
                showMessage("Template Size = " + outputFile.length());
                showMessage( getString(R.string.format_finger_record_saved_to, outputFile.getAbsolutePath()));
            } else {
                showError(getString(R.string.format_extraction_failed, status));
            }
        } catch (IOException e) {
            showError(getString(R.string.format_finger_record_saving_error, e.getMessage()));
        } finally {
            if (subject != null) subject.dispose();
            if (finger != null) finger.dispose();
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
        // Set finger template size (large is recommended for enrolment to database) (optional)
        mBiometricClient.setFingersTemplateSize(NTemplateSize.LARGE);
        // Initialize NBiometricClient
        mBiometricClient.initialize();
    }

    private void enableControls(final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnExtractFp.setEnabled(enable);
            }
        });
    }

    final class ObtainComponentsTask extends AsyncTask<Object, Boolean, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgress(getString(R.string.msg_obtaining_licenses));
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            try {
                for(String product: PRODUCT_LICENSES){
                    showMessage("Try to obtain license " + product);
                    if(NLicense.obtain(ADDRESS, PORT, product)){
                        showMessage("License " + product + " obtained");
                        return true;
                    } else {
                        showMessage("License " + product + " obtaining FAILED");
                        return false;
                    }
                }
            } catch (Exception e) {
                showError(e.getMessage());
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            hideProgress();
        }
    }


    final class InitializationTask extends AsyncTask<Object, Boolean, Boolean> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgress(getString(R.string.msg_obtaining_licenses));
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            try {
                init();
                enableControls(true);
            } catch (Exception e) {
                showError(e.getMessage());
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            hideProgress();
        }
    }


    private ProgressDialog mProgressDialog;

    protected void showProgress(final String message) {
        hideProgress();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressDialog = ProgressDialog.show(ManageLicenseSnFile.this, "", message);
            }
        });
    }

    protected void hideProgress() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null && mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                }
            }
        });
    }

    protected void showError(String message) {
        tvTextResult.append("\n" + "Error - " + message);
        Log.e(TAG, "Error - " + message);
    }

    protected void showMessage(String message) {
        Log.d(TAG, message);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvTextResult.append("\n" +  message);
            }
        });
    }

}
