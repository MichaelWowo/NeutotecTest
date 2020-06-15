package com.neurotec.tutorials.biometrics;

import android.app.Application;
import android.os.Environment;
import android.util.Log;

import com.neurotec.licensing.NLicenseManager;

import java.io.File;

public class BiometricsTutorialsApp extends Application {

	private static final String TAG = BiometricsTutorialsApp.class.getSimpleName();
	private static final String OUTPUT_DIR_NAME = "output";

	public static final String APP_NAME = "biometrics-tutorials";
	public static final String FILE_SEPARATOR = System.getProperty("file.separator");
	public static final String NEUROTECHNOLOGY_DIRECTORY = Environment.getExternalStorageDirectory().getAbsolutePath() + FILE_SEPARATOR + "Neurotechnology";
	public static final String REPORTS_DIRECTORY_PATH = "Reports";
	public static final String SAMPLE_DATA_DIR_NAME = "Data";
	public static final String TUTORIALS_OUTPUT_DATA_DIR = NEUROTECHNOLOGY_DIRECTORY + FILE_SEPARATOR + SAMPLE_DATA_DIR_NAME;
	public static final String TUTORIALS_ASSETS_DIR = "input";

	@Override
	public void onCreate() {
		super.onCreate();

		try {
			System.setProperty("jna.nounpack", "true");
			System.setProperty("java.io.tmpdir", getCacheDir().getAbsolutePath());
		} catch (Exception e) {
			Log.e(TAG, "Exception", e);
		}
	}

	public static File getDataDirectory() {
		File directory = null;
		try {
			directory = new File(NEUROTECHNOLOGY_DIRECTORY);
			if (!directory.exists()) {
				directory.mkdirs();
			}
		} catch (SecurityException e) {
			Log.e(TAG, "Exception", e);
		}
		return directory;

	}
}
