package com.neurotec.tutorials.biometrics;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.neurotec.lang.NCore;
import com.neurotec.plugins.NDataFileManager;

public class BiometricsTutorials extends ListActivity  {

	// ===========================================================
	// Private static fields
	// ===========================================================

	private static final Comparator<Map<String, Object>> DISPLAY_NAME_COMPARATOR = new Comparator<Map<String, Object>>() {
		private final Collator collator = Collator.getInstance();

		@Override
		public int compare(Map<String, Object> map1, Map<String, Object> map2) {
			return collator.compare(map1.get(KEY_TITLE), map2.get(KEY_TITLE));
		}
	};
	private static final String KEY_TITLE = "title";
	private static final String KEY_INTENT = "intent";

	private static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1;
	private static final String WARNING_PROCEED_WITH_NOT_GRANTED_PERMISSIONS = "Do you wish to proceed without granting all permissions?";
	private static final String WARNING_NOT_ALL_GRANTED = "Some permissions are not granted.";
	private static final String MESSAGE_ALL_PERMISSIONS_GRANTED = "All permissions granted";

	private static final String TAG = BiometricsTutorials.class.getSimpleName();

	// ===========================================================
	// Public static fields
	// ===========================================================

	public static final String CATEGORY_NEUROTEC_TUTORIAL = BiometricsTutorials.class.getPackage().getName() + ".CATEGORY_NEUROTEC_TUTORIAL";

	// ===========================================================
	// Private fields
	// ===========================================================

	private Map<String, Integer> mPermissions = new HashMap<String, Integer>();

	// ===========================================================
	// Private methods
	// ===========================================================

	private List<Map<String, Object>> getData() {
		List<Map<String, Object>> myData = new ArrayList<Map<String, Object>>();

		Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
		mainIntent.addCategory(CATEGORY_NEUROTEC_TUTORIAL);

		PackageManager pm = getPackageManager();
		List<ResolveInfo> list = pm.queryIntentActivities(mainIntent, 0);

		if (null == list) {
			return myData;
		}

		int len = list.size();

		for (int i = 0; i < len; i++) {
			ResolveInfo info = list.get(i);
			CharSequence labelSeq = info.loadLabel(pm);
			String label;
			if (labelSeq == null) {
				label = info.activityInfo.name;
			} else {
				label = labelSeq.toString();
			}
			addItem(myData, label, activityIntent(info.activityInfo.applicationInfo.packageName, info.activityInfo.name));
		}

		Collections.sort(myData, DISPLAY_NAME_COMPARATOR);

		return myData;
	}

	private Intent activityIntent(String pkg, String componentName) {
		Intent result = new Intent();
		result.setClassName(pkg, componentName);
		return result;
	}

	private void addItem(List<Map<String, Object>> data, String name, Intent intent) {
		Map<String, Object> temp = new HashMap<String, Object>();
		temp.put(KEY_TITLE, name);
		temp.put(KEY_INTENT, intent);
		data.add(temp);
	}

	private void showDialogOK(String message, DialogInterface.OnClickListener okListener) {
		new AlertDialog.Builder(this)
				.setMessage(message)
				.setPositiveButton("OK", okListener)
				.setNegativeButton("Cancel", okListener)
				.create()
				.show();
	}

	private String[] getNotGrantedPermissions() {
		List<String> neededPermissions = new ArrayList<String>();

		int storagePermission = getApplicationContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
		int phonePermission = getApplicationContext().checkSelfPermission(Manifest.permission.READ_PHONE_STATE);
		int cameraPermission = getApplicationContext().checkSelfPermission(Manifest.permission.CAMERA);
		int microphonePermission = getApplicationContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO);

		if (phonePermission != PackageManager.PERMISSION_GRANTED) {
			neededPermissions.add(Manifest.permission.READ_PHONE_STATE);
		}
		if (storagePermission != PackageManager.PERMISSION_GRANTED) {
			neededPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
		}
		if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
			neededPermissions.add(Manifest.permission.CAMERA);
		}
		if (microphonePermission != PackageManager.PERMISSION_GRANTED) {
			neededPermissions.add(Manifest.permission.RECORD_AUDIO);
		}

		return neededPermissions.toArray(new String[neededPermissions.size()]);
	}

	private void requestPermissions(String[] permissions) {
		ActivityCompat.requestPermissions(this, permissions, REQUEST_ID_MULTIPLE_PERMISSIONS);
	}

	// ===========================================================
	// Activity events
	// ===========================================================

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		NCore.setContext(this);
		setListAdapter(new SimpleAdapter(this, getData(), android.R.layout.simple_list_item_1, new String[]{KEY_TITLE}, new int[]{android.R.id.text1}));
		getListView().setTextFilterEnabled(true);
		NDataFileManager.getInstance().addFromDirectory("data", false);
		String[] neededPermissions = getNotGrantedPermissions();
		if (neededPermissions.length != 0) {
			requestPermissions(neededPermissions);
		}
	}

	public void onRequestPermissionsResult(int requestCode, final String permissions[], int[] grantResults) {
		switch (requestCode) {
			case REQUEST_ID_MULTIPLE_PERMISSIONS: {
				// Initialize the map with permissions
				mPermissions.clear();
				mPermissions.put(Manifest.permission.READ_PHONE_STATE, PackageManager.PERMISSION_GRANTED);
				mPermissions.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);
				mPermissions.put(Manifest.permission.CAMERA, PackageManager.PERMISSION_GRANTED);
				mPermissions.put(Manifest.permission.RECORD_AUDIO, PackageManager.PERMISSION_GRANTED);
				// Fill with actual results from user
				if (grantResults.length > 0) {
					for (int i = 0; i < permissions.length; i++) {
						mPermissions.put(permissions[i], grantResults[i]);
					}
					// Check if at least one is not granted
					if (mPermissions.get(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
							|| mPermissions.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
							|| mPermissions.get(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
							|| mPermissions.get(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
						showDialogOK(WARNING_PROCEED_WITH_NOT_GRANTED_PERMISSIONS,
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
										switch (which) {
											case DialogInterface.BUTTON_POSITIVE:
												Log.w(TAG, WARNING_NOT_ALL_GRANTED);
												for (Map.Entry<String, Integer> entry : mPermissions.entrySet()) {
													if (entry.getValue() != PackageManager.PERMISSION_GRANTED) {
														Log.w(TAG, entry.getKey() + ": PERMISSION_DENIED");
													}
												}
												break;
											case DialogInterface.BUTTON_NEGATIVE:
												requestPermissions(permissions);
												break;
											default:
												throw new AssertionError("Unrecognised permission dialog parameter value");
										}
									}
								});
					} else {
						Log.i(TAG, MESSAGE_ALL_PERMISSIONS_GRANTED);
					}
				}
			}
		}
	}

	// ===========================================================
	// List events
	// ===========================================================

	@SuppressWarnings("unchecked")
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Map<String, Object> map = (Map<String, Object>) l.getItemAtPosition(position);
		Intent intent = (Intent) map.get(KEY_INTENT);
		startActivity(intent);
	}

}
