package com.neurotec.tutorials.biometrics;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import com.neurotec.biometrics.NBiometricOperation;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NBiometricTask;
import com.neurotec.biometrics.NFace;
import com.neurotec.biometrics.NLAttributes;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.NTemplateSize;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.images.NImage;
import com.neurotec.lang.NCore;
import com.neurotec.media.NMediaReader;
import com.neurotec.media.NMediaSource;
import com.neurotec.media.NMediaType;
import com.neurotec.samples.app.BaseActivity;
import com.neurotec.samples.app.DirectoryViewer;
import com.neurotec.samples.licensing.LicensingManager;
import com.neurotec.samples.util.NImageUtils;

public class DetectFacialFeaturesFromImageStream extends BaseActivity {
	private static final String TAG = "DetectFacialFeaturesFromImageStream";
	private static final int REQUEST_CODE_GET_RECORD = 1;
	private static final String IMAGES_FOLDER_PATH = "Neurotechnology/Data/biometrics-tutorials/input/faceImageCollection/";
	private static final int MAXIMUM_FRAMES = 20;

	//=========================================================================
	// CHOOSE LICENCES !!!
	//=========================================================================
	// ONE of the below listed licenses is required for unlocking this sample's functionality. Choose a license that you currently have on your device.
	// If you are using a TRIAL version - choose any of them.

	//private static final String[] LICENSES = new String[] {"FaceExtractor"};
	private static final String[] LICENSES = new String[]{"FaceClient"};
	//private static final String[] LICENSES = new String[]{"FaceFastExtractor"};

	//=========================================================================

	private EditText mRTSPAddress;
	private Button mLoadFile;
	private Button mRTSPCamera;
	private Button mLoadFromDirectory;
	private TextView mStatus;

	private NBiometricClient mBiometricClient;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		NCore.setContext(this);
		setContentView(R.layout.tutorial_detect_facial_features_from_image_stream);

		mStatus = (TextView) findViewById(R.id.tutorials_results);

		mRTSPAddress = (EditText) findViewById(R.id.tutorials_field_1);
		mRTSPAddress.setHint(getString(R.string.msg_url_for_rtsp_camera));

		mRTSPCamera = (Button) findViewById(R.id.tutorials_button_1);
		mRTSPCamera.setText(getString(R.string.msg_rtsp_camera));
		mRTSPCamera.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (validateRTSPAddress(mRTSPAddress.getText().toString())) {
					NMediaSource source;
					try {
						source = NMediaSource.fromUrl(mRTSPAddress.getText().toString());
						startDetecting(source, null);
					} catch (IOException e) {
						showMessage(e.toString());
						Log.e(TAG, "Exception", e);
					}
				} else {
					showMessage(getString(R.string.msg_no_rtsp_url));
				}
			}
		});
		mLoadFile = (Button) findViewById(R.id.tutorials_button_2);
		mLoadFile.setText(getString(R.string.msg_video_file));
		mLoadFile.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(DetectFacialFeaturesFromImageStream.this, DirectoryViewer.class);
				intent.putExtra(DirectoryViewer.ASSET_DIRECTORY_LOCATION, BiometricsTutorialsApp.TUTORIALS_ASSETS_DIR);
				startActivityForResult(intent, REQUEST_CODE_GET_RECORD);
			}
		});
		mLoadFromDirectory = (Button) findViewById(R.id.tutorials_button_3);
		mLoadFromDirectory.setText(getString(R.string.msg_load_static_image_files));
		mLoadFromDirectory.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// Loading static image files from given folder IMAGES_FOLDER_PATH
				File dir = new File(Environment.getExternalStorageDirectory(), IMAGES_FOLDER_PATH);
				if (dir.isDirectory()) {
					File[] files = dir.listFiles(new FileFilter() {
						@Override
						public boolean accept(File pathname) {
							return pathname.isFile();
						}
					});
					Uri[] filesUri = new Uri[files.length];

					for (int i = 0; i < files.length; i++) {
						filesUri[i] = Uri.fromFile(files[i]);
						showMessage("File loaded: " + filesUri[i].getLastPathSegment());
					}

					startDetecting(null, filesUri);
				} else {
					showMessage("Given path is not directory.");
					return;
				}
			}
		});
		enableControls(false);
		new InitializationTask().execute();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			if (requestCode == REQUEST_CODE_GET_RECORD) {
				try {
					NMediaSource source = NMediaSource.fromFile(data.getData().getPath());
					startDetecting(source, null);
				} catch (Exception e) {
					showMessage(e.toString());
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
		// Initialize NBiometricClient
		mBiometricClient.initialize();
		// Detect all face features
		if (LICENSES[0].equals("FaceClient") || LICENSES[0].equals("FaceFastExtractor")) {
			mBiometricClient.setFacesDetectAllFeaturePoints(true);
		}
	}

	private void enableControls(final boolean enabled) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mRTSPAddress.setEnabled(enabled);
				mLoadFile.setEnabled(enabled);
				mRTSPCamera.setEnabled(enabled);
				mLoadFromDirectory.setEnabled(enabled);
			}
		});
	}

	private void showMessage(final String message) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mStatus.append(message + "\n");
			}
		});
	}

	private boolean validateRTSPAddress(String rtspUrl) {
		return rtspUrl != null && !rtspUrl.isEmpty();
	}

	private void startDetecting(NMediaSource source, Uri[] files) {
		NSubject subject = new NSubject();
		NFace face = new NFace();
		face.setHasMoreSamples(true);
		subject.getFaces().add(face);

		try {
			NMediaReader reader = null;
			boolean isReaderUsed = false;
			if (source != null) {
				reader = new NMediaReader(source, EnumSet.of(NMediaType.VIDEO), true);
				isReaderUsed = true;
			} else if (files == null) {
				Throwable th = new Throwable("No source found.");
				if (th != null) {
					throw th;
				}
			}

			// Creating face segment detection task
			NBiometricStatus status;
			NBiometricTask task = mBiometricClient.createTask(EnumSet.of(NBiometricOperation.DETECT_SEGMENTS), subject);

			int maximumFrames = isReaderUsed ? MAXIMUM_FRAMES : files.length;

			NImage image = null;
			if (isReaderUsed)
				reader.start();
			for (int i = 0; i < maximumFrames; i++) {
				image = isReaderUsed ? reader.readVideoSample().getImage() : NImageUtils.fromUri(this, files[i]);

				if (image == null) break;

				face.setImage(image);
				mBiometricClient.performTask(task);
				Throwable th = task.getError();
				if (th != null) {
					throw th;
				}

				status = task.getStatus();
				showMessage(String.format("%d st face detection status: %s", i, status.toString()));
				// Print extraction results
				if (status == NBiometricStatus.OK) {
					// Get face detection details, if face was detected (optional)
					for (NFace nface : subject.getFaces()) {
						for (NLAttributes attribute : nface.getObjects()) {
							showMessage("Face:");
							showMessage(String.format("\tLocation = (%d, %d), width = %d, height = %d\n", attribute.getBoundingRect().left, attribute.getBoundingRect().top,
									attribute.getBoundingRect().width(), attribute.getBoundingRect().height()));

							if ((attribute.getRightEyeCenter().confidence > 0) || (attribute.getLeftEyeCenter().confidence > 0)) {
								showMessage("\tFound eyes:");
								if (attribute.getRightEyeCenter().confidence > 0) {
									showMessage(String.format("\t\tRight: location = (%d, %d), confidence = %d%n", attribute.getRightEyeCenter().x, attribute.getRightEyeCenter().y,
											attribute.getRightEyeCenter().confidence));
								}
								if (attribute.getLeftEyeCenter().confidence > 0) {
									showMessage(String.format("\t\tLeft: location = (%d, %d), confidence = %d%n", attribute.getLeftEyeCenter().x, attribute.getLeftEyeCenter().y,
											attribute.getLeftEyeCenter().confidence));
								}
							}
							if (LICENSES[0].equals("FaceClient") || LICENSES[0].equals("FaceFastExtractor")) {
								if (attribute.getNoseTip().confidence > 0) {
									showMessage("\tFound nose:");
									showMessage(String.format("\t\tLocation = (%d, %d), confidence = %d%n", attribute.getNoseTip().x, attribute.getNoseTip().y, attribute.getNoseTip().confidence));
								}
								if (attribute.getMouthCenter().confidence > 0) {
									showMessage("\tFound mouth:");
									showMessage(String.format("\t\tLocation = (%d, %d), confidence = %d%n", attribute.getMouthCenter().x, attribute.getMouthCenter().y, attribute.getMouthCenter().confidence));
								}
							}
						}
					}
				} else {
					showMessage("Extraction failed: " + status);
					th = task.getError();
					if (th != null) {
						throw th;
					}
				}
				image.dispose();
			}
			if (isReaderUsed)
				reader.stop();

			// Reset HasMoreSamples value since we finished loading images
			face.setHasMoreSamples(false);
		} catch (Throwable th) {
			showError(th);
		} finally {
			if (face != null) {
				face.dispose();
			}
			if (subject != null) {
				subject.dispose();
			}
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
				List<String> obtainedLicenses = LicensingManager.getInstance().obtainLicenses(DetectFacialFeaturesFromImageStream.this, LICENSES);
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
