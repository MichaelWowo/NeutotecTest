package com.neurotec.tutorials.biometrics;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import com.neurotec.beans.NParameterBag;
import com.neurotec.beans.NParameterDescriptor;
import com.neurotec.biometrics.NBiometric;
import com.neurotec.biometrics.NBiometricCaptureOption;
import com.neurotec.biometrics.NBiometricStatus;
import com.neurotec.biometrics.NFace;
import com.neurotec.biometrics.NLivenessMode;
import com.neurotec.biometrics.NSubject;
import com.neurotec.biometrics.NTemplateSize;
import com.neurotec.biometrics.client.NBiometricClient;
import com.neurotec.biometrics.view.NFaceView;
import com.neurotec.devices.NCamera;
import com.neurotec.devices.NDevice;
import com.neurotec.devices.NDeviceManager;
import com.neurotec.devices.NDeviceType;
import com.neurotec.io.NFile;
import com.neurotec.lang.NCore;
import com.neurotec.plugins.NPlugin;
import com.neurotec.plugins.NPluginState;
import com.neurotec.samples.app.BaseActivity;
import com.neurotec.samples.licensing.LicensingManager;
import com.neurotec.samples.view.BaseDialogFragment;
import com.neurotec.util.concurrent.CompletionHandler;

public final class EnrollFaceFromCamera extends BaseActivity {

	private interface CameraSelectionFragmentListener {
		void onCameraSelected(Source source, String url);
	}

	//=========================================================================
	// CHOOSE LICENCES !!!
	//=========================================================================
	// ONE of the below listed licenses is required for unlocking this sample's functionality. Choose a license that you currently have on your device.
	// If you are using a TRIAL version - choose any of them.

	//private static final String[] LICENSES = new String[]{"FaceExtractor"};
	private static final String[] LICENSES = new String[]{"FaceClient"};
	//private static final String[] LICENSES = new String[]{"FaceFastExtractor"};

	//=========================================================================

	private NFaceView mFaceView;
	private Button mButtonExtract;
	private Button mSelectSource;
	private TextView mStatus;
	private Source mSource = Source.CAMERA;
	private String mRTSPUrl;

	private NBiometricClient mBiometricClient;
	private CameraSelectionFragmentListener mListener = new CameraSelectionFragmentListener() {

		@Override
		public void onCameraSelected(Source source, String url) {
			mSource = source;
			mRTSPUrl = url;
			startCapturing(mSource, mRTSPUrl);
		}
	};

	private enum Source {
		CAMERA(0),
		RTSP_CAMERA(1);

		private int value;

		Source(int value) {
			this.value = value;
		}

		public static Source fromInt(int value) {
			for (Source v : values()) {
				if (v.getValue() == value) {
					return v;
				}
			}
			return null;
		}

		public int getValue() {
			return value;
		}

	}

	private final PropertyChangeListener biometricPropertyChanged = new PropertyChangeListener() {
		@Override
		public void propertyChange(PropertyChangeEvent evt) {
			if ("Status".equals(evt.getPropertyName())) {
				final NBiometricStatus status = ((NBiometric) evt.getSource()).getStatus();
				runOnUiThread(new Runnable() {
					public void run() {
						showMessage(status.toString());
					}
				});
			}
		}
	};

	private CompletionHandler<NBiometricStatus, NSubject> completionHandler = new CompletionHandler<NBiometricStatus, NSubject>() {
		@Override
		public void completed(NBiometricStatus result, NSubject subject) {
			if (result == NBiometricStatus.OK) {
				showMessage(getString(R.string.msg_template_created));

				NFace face = subject.getFaces().get(0);
				// Save face image to file
				File outputFile = new File(BiometricsTutorialsApp.TUTORIALS_OUTPUT_DATA_DIR, "face-image-from-camera.jpg");
				try {
					face.getImage().save(outputFile.getAbsolutePath());
					showMessage(getString(R.string.format_face_image_saved_to, outputFile.getAbsolutePath()));
				} catch (IOException e) {
				}

				// Save face template to file
				outputFile = new File(BiometricsTutorialsApp.TUTORIALS_OUTPUT_DATA_DIR, "nltemplate-from-camera.dat");
				try {
					NFile.writeAllBytes(outputFile.getAbsolutePath(), subject.getTemplateBuffer());
					showMessage(getString(R.string.format_face_template_saved_to, outputFile.getAbsolutePath()));
				} catch (IOException e) {
				}
			} else {
				showMessage(getString(R.string.format_extraction_failed, result));
			}
			if (result != NBiometricStatus.CANCELED) {
				startCapturing(mSource, mRTSPUrl);
			}
		}

		@Override
		public void failed(Throwable exc, NSubject subject) {
			exc.printStackTrace();
			startCapturing(mSource, mRTSPUrl);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		NCore.setContext(this);
		setContentView(R.layout.tutorial_enroll_face_from_camera_camera_view);
		mFaceView = (NFaceView) findViewById(R.id.camera_view);
		mStatus = (TextView) findViewById(R.id.text_view_status);
		mButtonExtract = (Button) findViewById(R.id.button_extract);
		mButtonExtract.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				stopCapturing();
			}
		});
		mSelectSource = (Button) findViewById(R.id.button_select_source);
		mSelectSource.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mBiometricClient.cancel();
				CameraSelectionFragment.newInstance(mSource, mRTSPUrl, mListener).show(getFragmentManager(), "source");
			}
		});
		new InitializationTask().execute();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		hideProgress();
		mBiometricClient.setFacesLivenessMode(NLivenessMode.PASSIVE);
		if (mBiometricClient != null) {
			mBiometricClient.cancel();
			mBiometricClient.dispose();
			mBiometricClient = null;
		}
	}

	private void init() {
		mBiometricClient = new NBiometricClient();
		mBiometricClient.setUseDeviceManager(true);
		// Set type of the device used
		mBiometricClient.getDeviceManager().setDeviceTypes(EnumSet.of(NDeviceType.CAMERA));
		// Initialize NBiometricClient
		mBiometricClient.initialize();
	}

	private boolean validateRTSPAddress(String rtspUrl) {
		return rtspUrl != null && !rtspUrl.isEmpty();
	}

	private NDevice connectDevice(NDeviceManager deviceManager, String url, Source source) {
		switch (source) {
			case CAMERA: {
				// Get count of connected devices
				int count = deviceManager.getDevices().size();
				if (count == 0) {
					throw new RuntimeException("No cameras found, exiting!");
				}
				// Select the first available camera
				if (count > 1) {
					return deviceManager.getDevices().get(1);
				}
				return deviceManager.getDevices().get(0);
			}
			case RTSP_CAMERA: {
				if (validateRTSPAddress(url)) {
					NPlugin plugin = NDeviceManager.getPluginManager().getPlugins().get("Media");
					if ((plugin.getState() == NPluginState.PLUGGED) && (NDeviceManager.isConnectToDeviceSupported(plugin))) {
						NParameterDescriptor[] parameters = NDeviceManager.getConnectToDeviceParameters(plugin);
						NParameterBag bag = new NParameterBag(parameters);
						bag.setProperty("DisplayName", "IP Camera");
						bag.setProperty("Url", url);
						return deviceManager.connectToDevice(plugin, bag.toPropertyBag());
					}
					throw new RuntimeException("Failed to connect specified device!");
				} else {
					throw new RuntimeException(getString(R.string.msg_no_rtsp_url));
				}
			}
			default:
				throw new AssertionError("Not recognised input source");
		}
	}

	private void startCapturing(Source source, String url) {
		try {
			NSubject subject = new NSubject();
			NFace face = new NFace();
			face.addPropertyChangeListener(biometricPropertyChanged);
			face.setCaptureOptions(EnumSet.of(NBiometricCaptureOption.MANUAL));
			mFaceView.setFace(face);
			subject.getFaces().add(face);
			NCamera camera = (NCamera) connectDevice(mBiometricClient.getDeviceManager(), url, source);
			mBiometricClient.setFaceCaptureDevice(camera);

			mBiometricClient.setFacesLivenessMode(NLivenessMode.PASSIVE);
			mBiometricClient.capture(subject, subject, completionHandler);
			showMessage(getString(R.string.msg_turn_camera_to_face));
		} catch (Exception ex) {
			showError(ex);
		}
	}

	private void stopCapturing() {
		mBiometricClient.force();
	}

	private void showMessage(final String message) {
		Log.d("CID", "showMessage: " + message);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mStatus.append(message + "\n");
			}
		});
	}

	// ===========================================================
	// Dialog fragment
	// ===========================================================

	public static class CameraSelectionFragment extends BaseDialogFragment {

		private static final String EXTRA_SOURCE = "message";
		private static final String EXTRA_URL = "message";

		public static CameraSelectionFragment newInstance(Source src, String url, CameraSelectionFragmentListener listener) {
			CameraSelectionFragment frag = new CameraSelectionFragment();
			Bundle args = new Bundle();
			args.putInt(EXTRA_SOURCE, src.getValue());
			args.putString(EXTRA_URL, url);
			frag.setArguments(args);
			frag.mListener = listener;
			return frag;
		}

		private CameraSelectionFragmentListener mListener;

		public CameraSelectionFragment() {
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
			alertDialogBuilder.setTitle(R.string.msg_select_source);

			LayoutInflater factory = LayoutInflater.from(getActivity());
			final View cameraSelection = factory.inflate(R.layout.tutorial_enroll_face_from_camera_camera_source_selection, null);
			alertDialogBuilder.setView(cameraSelection);

			final Source source = Source.fromInt(getArguments().getInt(EXTRA_SOURCE));
			final String url = getArguments().getString(EXTRA_URL);

			final RadioGroup radioGroup = (RadioGroup) cameraSelection.findViewById(R.id.radio_group_1);
			radioGroup.check(source == Source.CAMERA ? R.id.radio_button_1 : R.id.radio_button_2);

			RadioButton camera = (RadioButton) cameraSelection.findViewById(R.id.radio_button_1);
			camera.setText(R.string.msg_camera);

			RadioButton rtsp = (RadioButton) cameraSelection.findViewById(R.id.radio_button_2);
			rtsp.setText(R.string.msg_rtsp_camera);

			final EditText rtspUrl = (EditText) cameraSelection.findViewById(R.id.tutorials_field_1);
			rtspUrl.setHint(url != null ? url : getString(R.string.msg_enter_rtsp_camera_url));

			alertDialogBuilder.setPositiveButton(R.string.msg_yes, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					Source userSource;
					String userUrl = null;
					switch (radioGroup.getCheckedRadioButtonId()) {
						case R.id.radio_button_1:
							userSource = Source.CAMERA;
							break;
						case R.id.radio_button_2:
							userSource = Source.RTSP_CAMERA;
							userUrl = rtspUrl.getText().toString();
							break;
						default:
							throw new AssertionError("Not recognised source");
					}
					mListener.onCameraSelected(userSource, userUrl);
					dialog.dismiss();
				}
			});
			alertDialogBuilder.setNegativeButton(R.string.msg_cancel, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					mListener.onCameraSelected(source, url);
					dialog.dismiss();
				}
			});

			return alertDialogBuilder.create();
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
				List<String> obtainedLicenses = LicensingManager.getInstance().obtainLicenses(EnrollFaceFromCamera.this, LICENSES);
				if (obtainedLicenses.size() == LICENSES.length) {
					showToast(R.string.msg_licenses_obtained);
					showProgress(R.string.msg_initialising);
					init();
					startCapturing(mSource, mRTSPUrl);
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

