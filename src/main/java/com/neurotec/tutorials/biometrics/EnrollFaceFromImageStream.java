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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

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
import com.neurotec.io.NFile;
import com.neurotec.lang.NCore;
import com.neurotec.media.NMediaReader;
import com.neurotec.media.NMediaSource;
import com.neurotec.media.NMediaType;
import com.neurotec.samples.app.BaseActivity;
import com.neurotec.samples.app.DirectoryViewer;
import com.neurotec.samples.licensing.LicensingManager;
import com.neurotec.samples.util.NImageUtils;
import com.neurotec.util.NCollectionChangedAction;
import com.neurotec.util.event.NCollectionChangeEvent;
import com.neurotec.util.event.NCollectionChangeListener;

public class EnrollFaceFromImageStream extends BaseActivity {

	private static final String TAG = "CID";
	private static final int REQUEST_CODE_GET_RECORD = 1;
	private static final String IMAGES_FOLDER_PATH = "Neurotechnology/Data/biometrics-tutorials/output/";
	private static final String ANDROID_ASSET_DESCRIPTOR = "file:///android_asset/";

	//=========================================================================
	// CHOOSE LICENCES !!!
	//=========================================================================
	// ONE of the below listed licenses is required for unlocking this sample's functionality. Choose a license that you currently have on your device.
	// If you are using a TRIAL version - choose any of them.

	//private static final String[] LICENSES = new String[]{"FaceExtractor"};
	private static final String[] LICENSES = new String[]{"FaceClient"};
	//private static final String[] LICENSES = new String[]{"FaceFastExtractor"};


	//=========================================================================

	private EditText mRTSPAddress;
	private Button mLoadFile;
	private Button mRTSPCamera;
	private Button mLoadFromDirectory;
	private TextView mStatus;

	private NBiometricClient mBiometricClient;

	private ArrayList<NLAttributes> monitoredAttributes = new ArrayList<NLAttributes>();
	private static NFace face = new NFace();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		NCore.setContext(this);
		setContentView(R.layout.tutorial_enroll_face_from_image_stream);

		mStatus = (TextView) findViewById(R.id.tutorials_results);

//		mRTSPAddress = (EditText) findViewById(R.id.tutorials_field_1);
//		mRTSPAddress.setHint(getString(R.string.msg_url_for_rtsp_camera));
//
//		mRTSPCamera = (Button) findViewById(R.id.tutorials_button_1);
//		mRTSPCamera.setText(getString(R.string.msg_rtsp_camera));
//		mRTSPCamera.setOnClickListener(new View.OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				if (validateRTSPAddress(mRTSPAddress.getText().toString())) {
//					NMediaSource source;
//					try {
//						source = NMediaSource.fromUrl(mRTSPAddress.getText().toString());
//						startEnrolling(source, null);
//					} catch (IOException e) {
//						showMessage(e.toString());
//						Log.e(TAG, "Exception", e);
//					}
//				} else {
//					showMessage(getString(R.string.msg_no_rtsp_url));
//				}
//			}
//		});
//		mLoadFile = (Button) findViewById(R.id.tutorials_button_2);
//		mLoadFile.setText(getString(R.string.msg_video_file));
//		mLoadFile.setOnClickListener(new View.OnClickListener() {
//			@Override
//			public void onClick(View v) {
//				Intent intent = new Intent(EnrollFaceFromImageStream.this, DirectoryViewer.class);
//				intent.putExtra(DirectoryViewer.ASSET_DIRECTORY_LOCATION, BiometricsTutorialsApp.TUTORIALS_ASSETS_DIR);
//				startActivityForResult(intent, REQUEST_CODE_GET_RECORD);
//			}
//		});
		mLoadFromDirectory = (Button) findViewById(R.id.tutorials_button_3);
		mLoadFromDirectory.setText(getString(R.string.msg_load_static_image_files));
		mLoadFromDirectory.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				// Loading static image files from given folder IMAGES_FOLDER_PATH
				File dir = new File(Environment.getExternalStorageDirectory(), IMAGES_FOLDER_PATH);
				showMessage(dir.toString());
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

					startEnrolling(null, filesUri);
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
		if (requestCode == REQUEST_CODE_GET_RECORD) {
			if (resultCode == RESULT_OK) {
				try {
					String path = null;
					if (data.getData().toString().contains(ANDROID_ASSET_DESCRIPTOR)) {
						path = data.getData().toString().replace(ANDROID_ASSET_DESCRIPTOR, "");
					} else {
						path = data.getData().getPath();
					}
					NMediaSource source = NMediaSource.fromFile(path);
					startEnrolling(source, null);
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
		// Detect all face features
		if (LICENSES[0].equals("FaceClient") || LICENSES[0].equals("FaceFastExtractor")) {
			mBiometricClient.setFacesDetectAllFeaturePoints(true);
		}
		// Initialize NBiometricClient
		mBiometricClient.initialize();
	}

	private void enableControls(final boolean enabled) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				//mRTSPAddress.setEnabled(enabled);
				//mLoadFile.setEnabled(enabled);
				//mRTSPCamera.setEnabled(enabled);
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

	private void startEnrolling(NMediaSource source, Uri[] files) {
		NSubject subject = new NSubject();
		face = new NFace();
		face.setHasMoreSamples(true);

		face.addPropertyChangeListener(facePropertyChange);
		face.getObjects().addCollectionChangeListener(objectsCollectionChanged);
		for (NLAttributes item : (NLAttributes[]) face.getObjects().toArray()) {
			monitoredAttributes.add(item); item.addPropertyChangeListener(attributesPropertyChange);
		}
		subject.getFaces().add(face);

		//mBiometricClient.setFacesLivenessMode(NLivenessMode.ACTIVE);
		mBiometricClient.setFacesLivenessThreshold((byte)1);
		mBiometricClient.setFacesLivenessMode(NLivenessMode.SIMPLE);


		try {
			// Start extraction from stream
			NBiometricStatus status = NBiometricStatus.NONE;
			NBiometricTask task = mBiometricClient.createTask(EnumSet.of(NBiometricOperation.CREATE_TEMPLATE), subject);

			NImage image = NImageUtils.fromUri(this, files[0]);

			int i = 1;
			while ((image != null) && (status == NBiometricStatus.NONE)) {
				face.setImage(image);
				mBiometricClient.performTask(task);
				Throwable th = task.getError();
				if (th != null) {
					throw th;
				}
				status = task.getStatus();
				Log.d(TAG, "Image " + i + " - Task status = " + status.toString());
				image.dispose();

				if (i >= files.length) break;

				image = NImageUtils.fromUri(this, files[i++]);
			}

			// Reset HasMoreSamples value since we finished loading images
			face.setHasMoreSamples(false);

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
				showMessage("Extraction failed: " + status);
				Throwable th = task.getError();
				if (th != null) {
					throw th;
				}
			}
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

	private final PropertyChangeListener facePropertyChange = new PropertyChangeListener() {
		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if ("Image".equals(evt.getPropertyName())) {
				//updateImage(); // At this point you know image (parameter with name "Image") was changed so you might want to redraw it
			}
		}
	};

	private final NCollectionChangeListener objectsCollectionChanged = new
			NCollectionChangeListener() {
				@Override
				public void collectionChanged(NCollectionChangeEvent event) {
					if (event.getAction() == NCollectionChangedAction.RESET) {
						for (NLAttributes item : monitoredAttributes) { item.removePropertyChangeListener(attributesPropertyChange);
						}
						monitoredAttributes.clear();
					} else if (event.getAction() == NCollectionChangedAction.ADD) {
						for (Object item : event.getNewItems()) {
							NLAttributes attributes = (NLAttributes) item;
							monitoredAttributes.add(attributes);
							attributes.addPropertyChangeListener(attributesPropertyChange);
						}
					} else if (event.getAction() ==
							NCollectionChangedAction.REMOVE) {
						for (Object item : event.getOldItems()) {
							NLAttributes attributes = (NLAttributes) item;
							monitoredAttributes.remove(attributes);
							attributes.removePropertyChangeListener(attributesPropertyChange);
						}
					}
					repaint();
				}
			};

	private final PropertyChangeListener attributesPropertyChange = new
			PropertyChangeListener() {
				@Override
				public void propertyChange(PropertyChangeEvent evt) {
					repaint(); // You might want to redraw as NLAttribute property has changed
				}
			};

	private void repaint()
	{
		NLAttributes[] attributesArray = (NLAttributes[]) face.getObjects().toArray();
		for (int i = 0; i < attributesArray.length; i++)
		{
			NLAttributes attributes = attributesArray[i];
			EnumSet<NLivenessAction> action = attributes.getLivenessAction();
			byte score = attributes.getLivenessScore();
			boolean rotation = action.contains(NLivenessAction.ROTATE_YAW);
			boolean blink = action.contains(NLivenessAction.BLINK);
			boolean keepStill = action.contains(NLivenessAction.KEEP_STILL);

			if (rotation)
			{
				float yaw = attributes.getYaw();
				showMessage("yaw: " + yaw);
				float targetYaw = attributes.getLivenessTargetYaw();
				showMessage(("target yaw: " + targetYaw));
				if (targetYaw > yaw)
				{
					showMessage("rotate right");
				}
				if (yaw > targetYaw)
				{
					showMessage("rotate left");
				}
			}

			if (blink) showMessage("Blink");

			if (keepStill)
			{
				showMessage("Keep still " + score);
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
				List<String> obtainedLicenses = LicensingManager.getInstance().obtainLicenses(EnrollFaceFromImageStream.this, LICENSES);
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

			Log.d(TAG, "Quality = " + mBiometricClient.getFacesQualityThreshold());
		}
	}

}