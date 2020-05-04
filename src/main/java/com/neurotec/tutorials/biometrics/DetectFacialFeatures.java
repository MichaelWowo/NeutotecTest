package com.neurotec.tutorials.biometrics;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.EnumSet;
import java.util.List;

import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NFace;
import com.neurotec.biometrics.NLAttributes;
import com.neurotec.biometrics.NLFeaturePoint;
import com.neurotec.biometrics.NLProperty;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.NTemplateSize;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.samples.app.BaseActivity;
import com.neurotec.samples.app.DirectoryViewer;
import com.neurotec.samples.licensing.LicensingManager;
import com.neurotec.samples.util.NImageUtils;
import com.neurotec.samples.util.ResourceUtils;

public final class DetectFacialFeatures extends BaseActivity {

	private static final String TAG = DetectFacialFeatures.class.getSimpleName();
	private static final int REQUEST_CODE_GET_IMAGE = 1;

	//=========================================================================
	// CHOOSE LICENCES !!!
	//=========================================================================
	// ONE of the below listed licenses is required for unlocking this sample's functionality. Choose a license that you currently have on your device.
	// If you are using a TRIAL version - choose any of them.

	//private static final String[] LICENSES = new String[] {"FaceExtractor"};
	private static final String[] LICENSES = new String[]{"FaceClient"};
	//private static final String[] LICENSES = new String[]{"FaceFastExtractor"};

	//=========================================================================

	private Button mButton;
	private TextView mResult;
	private BackgroundTask mTask;

	private NBiometricClient mBiometricClient;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tutorial_detect_facial_features);
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
		new InitializationTask().execute();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_CODE_GET_IMAGE) {
			if (resultCode == RESULT_OK) {
				try {
					mTask = new BackgroundTask();
					showMessage(getString(R.string.msg_detecting));
					mTask.execute(data.getData());
				} catch (Exception e) {
					showMessage(e.getMessage());
					Log.e(TAG, "Exception", e);
				}
			}
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
		// Set which features should be detected
		if (LICENSES[0].equals("FaceClient") || LICENSES[0].equals("FaceFastExtractor")) {
			mBiometricClient.setFacesDetectBaseFeaturePoints(true);
			mBiometricClient.setFacesRecognizeExpression(true);
			mBiometricClient.setFacesDetectProperties(true);
			mBiometricClient.setFacesDetermineGender(true);
			mBiometricClient.setFacesDetermineAge(true);
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

	private class BackgroundTask extends AsyncTask<Uri, String, Boolean> {

		@Override
		protected void onProgressUpdate(String... messages) {
			for (String message : messages) {
				showMessage(message);
			}
		}

		@Override
		protected Boolean doInBackground(Uri... params) {
			if (isCancelled()) {
				return null;
			}

			NSubject subject = null;
			NFace face = null;
			NBiometricTask task = null;

			try {
				subject = new NSubject();
				face = new NFace();

				Log.d(CredenceLivenessDetection.TAG, "uri = " + params[0].toString());

				// Read face image and add it to NFace object
				face.setImage(NImageUtils.fromUri(DetectFacialFeatures.this, params[0]));

				// Add face image to NSubject
				subject.getFaces().add(face);

				// Detect multiple faces
				subject.setMultipleSubjects(true);


				// Create segment detection task
				task = mBiometricClient.createTask(EnumSet.of(NBiometricOperation.DETECT_SEGMENTS), subject);

				// Perform task
				mBiometricClient.performTask(task);

				Throwable taskError = task.getError();
				if (taskError != null) {
					publishProgress(taskError.getMessage());
					return false;
				}

				// Get detection details of the extracted face
				if (task.getStatus() == NBiometricStatus.OK) {
					publishProgress(getString(R.string.format_found_faces, face.getObjects().size()));

					for (NLAttributes attributes : face.getObjects()) {
						publishProgress(getString(R.string.format_face_rect,
								attributes.getBoundingRect().left, attributes.getBoundingRect().top,
								attributes.getBoundingRect().right, attributes.getBoundingRect().bottom
						));

						printNleFeaturePoint("LeftEyeCenter", attributes.getLeftEyeCenter());
						printNleFeaturePoint("RightEyeCenter", attributes.getRightEyeCenter());

						if (LICENSES[0].equals("FaceClient") || LICENSES[0].equals("FaceFastExtractor")) {
							printNleFeaturePoint("MouthCenter", attributes.getMouthCenter());
							printNleFeaturePoint("NoseTip", attributes.getNoseTip());

							if (attributes.getAge() == 254) {
								publishProgress(getString(R.string.msg_age_not_detected));
							} else {
								publishProgress(getString(R.string.format_age, attributes.getAge()));
							}
							if (attributes.getGenderConfidence() == 255) {
								publishProgress(getString(R.string.msg_gender_not_detected));
							} else {
								publishProgress(getString(R.string.format_gender_and_confidence, attributes.getGender(), attributes.getGenderConfidence()));
							}
							if (attributes.getExpressionConfidence() == 255) {
								publishProgress(getString(R.string.msg_expression_not_detected));
							} else {
								publishProgress(getString(R.string.msg_expression_and_confidence, ResourceUtils.getEnum(DetectFacialFeatures.this, attributes.getExpression()), attributes.getExpressionConfidence()));
							}
							if (attributes.getBlinkConfidence() == 255) {
								publishProgress(getString(R.string.msg_blink_not_detected));
							} else {
								publishProgress(getString(R.string.msg_blink_and_confidence, attributes.getProperties().contains(NLProperty.BLINK), attributes.getBlinkConfidence()));
							}
							if (attributes.getMouthOpenConfidence() == 255) {
								publishProgress(getString(R.string.msg_mouth_open_not_detected));
							} else {
								publishProgress(getString(R.string.msg_mouth_open_and_confidence, attributes.getProperties().contains(NLProperty.MOUTH_OPEN), attributes.getMouthOpenConfidence()));
							}
							if (attributes.getGlassesConfidence() == 255) {
								publishProgress(getString(R.string.msg_glasses_not_detected));
							} else {
								publishProgress(getString(R.string.msg_glasses_and_confidence, attributes.getProperties().contains(NLProperty.GLASSES), attributes.getGlassesConfidence()));
							}
							if (attributes.getDarkGlassesConfidence() == 255) {
								publishProgress(getString(R.string.msg_dark_glasses_not_detected));
							} else {
								publishProgress(getString(R.string.msg_dark_glasses_and_confidence, attributes.getProperties().contains(NLProperty.DARK_GLASSES), attributes.getDarkGlassesConfidence()));
							}
						}
					}
				} else {
					publishProgress(getString(R.string.msg_faces_not_found, task.getStatus()));
				}
			} catch (Exception e) {
				publishProgress(e.getMessage());
				return false;
			} finally {
				if (subject != null) subject.dispose();
				if (face != null) face.dispose();
				if (task != null) task.dispose();
			}
			return true;
		}

		private void printNleFeaturePoint(String name, NLFeaturePoint point) {
			if (point.confidence == 0) {
				publishProgress(String.format("\t%s feature unavailable. confidence: 0%n", name));
				return;
			}
			publishProgress(String.format("\t%s feature found. X: %d, Y: %d, confidence: %d%n", name, point.x, point.y, point.confidence));
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
				List<String> obtainedLicenses = LicensingManager.getInstance().obtainLicenses(DetectFacialFeatures.this, LICENSES);
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
