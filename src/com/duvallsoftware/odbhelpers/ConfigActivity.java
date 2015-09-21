package com.duvallsoftware.odbhelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import pt.lighthouselabs.obd.commands.ObdCommand;
import pt.lighthouselabs.obd.commands.SpeedObdCommand;
import pt.lighthouselabs.obd.enums.ObdProtocols;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.net.TrafficStats;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.duvallsoftware.trafficsigndetector.CameraView;
import com.duvallsoftware.trafficsigndetector.R;
import com.duvallsoftware.trafficsigndetector.TrafficSignDetectorActivity;

/**
 * This class uses some base code from the OBD Reader application from
 * com.github.pires.obd.reader
 */
public class ConfigActivity extends PreferenceActivity implements OnPreferenceChangeListener {

	// Bluetooth
	public static final String BLUETOOTH_LIST_KEY = "bluetooth_list_preference";
	public static final String ENABLE_BT_KEY = "enable_bluetooth_preference";
	
	// OBD
	public static final String OBD_ENABLED_KEY = "enable_obd_preference";
	public static final String OBD_UPDATE_PERIOD_KEY = "obd_update_period_preference";
	public static final String PROTOCOLS_LIST_KEY = "obd_protocols_preference";
	public static final String IMPERIAL_UNITS_KEY = "imperial_units_preference";
	
	// Camera
	public static final String CAMERA_RESOLUTION_LIST_KEY = "camera_resolution_list_preference";
	public static final String CAMERA_ZOOM_KEY = "camera_zoom_preference";
	public static final String SAVE_IMAGES_KEY = "save_captured_images_preference";
	
	// Display
	public static final String SHOW_FPS_KEY = "show_fps_preference";
	public static final String SHOW_SPEED_KEY = "show_speed_preference";		
	public static final String SIGNS_DISPLAY_PERIOD_KEY = "signs_display_period_preference";
	
	// Sound
	public static final String ENABLE_SIGNS_VOICE_KEY = "enable_signs_voice_preference";
	public static final String ENABLE_SPEED_WARNING_VOICE_KEY = "enable_speed_warning_voice_preference";
	
	// Other	
	public static final String ENABLE_PREPROCESSING_OPTIONS_KEY = "enable_preprocessing_options_preference";
	
	/**
	 * @param prefs
	 * @return
	 */
	public static int getObdUpdatePeriod(SharedPreferences prefs) {
		// In seconds
		int period = 4000; //milliseconds
		if( prefs != null) {		
			String periodString = prefs.getString(ConfigActivity.OBD_UPDATE_PERIOD_KEY, "4"); //seconds
			try {
				period = Integer.parseInt(periodString) * 1000;
			} catch (Exception e) {
			}
	
			if (period <= 0) {
				period = 250;
			}
		}
		return period;
	}

	/**
	 * @param prefs
	 * @return
	 */
	public static ArrayList<ObdCommand> getObdCommands(SharedPreferences prefs) {
		ArrayList<ObdCommand> cmds = new ArrayList<ObdCommand>();
		cmds.add(new SpeedObdCommand());
		ArrayList<ObdCommand> ucmds = new ArrayList<ObdCommand>();
		for (int i = 0; i < cmds.size(); i++) {
			ObdCommand cmd = cmds.get(i);
			ucmds.add(cmd);
		}
		return ucmds;
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		/*
		 * Read preferences resources available at res/xml/preferences.xml
		 */
		addPreferencesFromResource(R.xml.preferences);

		ArrayList<CharSequence> pairedDeviceStrings = new ArrayList<CharSequence>();
		ArrayList<CharSequence> vals = new ArrayList<CharSequence>();
		ListPreference btDevicesList = (ListPreference) getPreferenceScreen().findPreference(BLUETOOTH_LIST_KEY);
		ArrayList<CharSequence> protocolStrings = new ArrayList<CharSequence>();
		ListPreference protocolsList = (ListPreference) getPreferenceScreen().findPreference(PROTOCOLS_LIST_KEY);
		String[] prefKeys = new String[] { OBD_UPDATE_PERIOD_KEY };
		for (String prefKey : prefKeys) {
			EditTextPreference txtPref = (EditTextPreference) getPreferenceScreen().findPreference(prefKey);
			txtPref.setOnPreferenceChangeListener(this);
		}
		
		/*
		 * Available Camera Resolutions
		 */
		ArrayList<CharSequence> resolutionStrings = new ArrayList<CharSequence>();
		ArrayList<CharSequence> resolutionValues = new ArrayList<CharSequence>();
		ListPreference resolutionsList = (ListPreference) getPreferenceScreen().findPreference(CAMERA_RESOLUTION_LIST_KEY);
		List<Size> resList = TrafficSignDetectorActivity.getCameraResolutionsList();
		ListIterator<Size> resolutionItr = resList.listIterator();
		while (resolutionItr.hasNext()) {
			Size element = resolutionItr.next();
			resolutionStrings.add(Integer.valueOf(element.width).toString() + "x" + Integer.valueOf(element.height).toString());
			resolutionValues.add(Integer.valueOf(element.width).toString());
		}
		resolutionsList.setEntries(resolutionStrings.toArray(new CharSequence[0]));
		resolutionsList.setEntryValues(resolutionValues.toArray(new CharSequence[0]));
		
		/*
		 * Available OBD protocols
		 */
		for (ObdProtocols protocol : ObdProtocols.values()) {
			protocolStrings.add(protocol.name());
		}
		protocolsList.setEntries(protocolStrings.toArray(new CharSequence[0]));
		protocolsList.setEntryValues(protocolStrings.toArray(new CharSequence[0]));		
		
		/*
		 * Let's use this device Bluetooth adapter to select which paired OBD-II
		 * compliant device we'll use.
		 */
		final BluetoothAdapter mBtAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBtAdapter == null) {
			btDevicesList.setEntries(pairedDeviceStrings.toArray(new CharSequence[0]));
			btDevicesList.setEntryValues(vals.toArray(new CharSequence[0]));

			// we shouldn't get here, still warn user
			Toast.makeText(this, "This device does not support Bluetooth.", Toast.LENGTH_LONG).show();

			return;
		}

		/*
		 * Listen for preferences click.
		 */
		final Activity thisActivity = this;
		btDevicesList.setEntries(new CharSequence[1]);
		btDevicesList.setEntryValues(new CharSequence[1]);
		btDevicesList.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				if (mBtAdapter == null || !mBtAdapter.isEnabled()) {
					Toast.makeText(thisActivity, "This device does not support Bluetooth or it is disabled.",
							Toast.LENGTH_LONG).show();
					return false;
				}
				return true;
			}
		});

		/*
		 * Get paired devices and populate preference list.
		 */
		Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device : pairedDevices) {
				pairedDeviceStrings.add(device.getName() + "\n" + device.getAddress());
				vals.add(device.getAddress());
			}
		}
		btDevicesList.setEntries(pairedDeviceStrings.toArray(new CharSequence[0]));
		btDevicesList.setEntryValues(vals.toArray(new CharSequence[0]));
	}
	 
	/**
	 * OnPreferenceChangeListener method that will validate a preferencen new
	 * value when it's changed.
	 * 
	 * @param preference
	 *            the changed preference
	 * @param newValue
	 *            the value to be validated and set if valid
	 */
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (OBD_UPDATE_PERIOD_KEY.equals(preference.getKey())) {
			try {
				int value = Integer.parseInt(newValue.toString());
				if( value < 0 || value > 60) {
					Toast.makeText(this, "Please enter a value between 0 and 60.", Toast.LENGTH_LONG).show();
					return false;
				}
				return true;
			} catch (Exception e) {
				Toast.makeText(this, "Couldn't parse '" + newValue.toString() + "' as a integer.", Toast.LENGTH_LONG)
						.show();
			}
		}
		if (CAMERA_ZOOM_KEY.equals(preference.getKey())) {
			try {
				int zoom = Integer.parseInt(newValue.toString());
				CameraView cameraView = TrafficSignDetectorActivity.getmOpenCvCameraView();
				if(cameraView != null && cameraView.isZoomValueSupported(zoom)) {
					return true;
				}
			} catch (Exception e) {
				Toast.makeText(this, "Couldn't parse value '" + newValue.toString() + "' as a integer.", Toast.LENGTH_LONG)
						.show();
			}
		}
		if (SIGNS_DISPLAY_PERIOD_KEY.equals(preference.getKey())) {
			try {
				int value = Integer.parseInt(newValue.toString());
				if( value < 0 || value > 60) {
					Toast.makeText(this, "Please enter a value between 0 and 60.", Toast.LENGTH_LONG).show();
					return false;
				}
				return true;
			} catch (Exception e) {
				Toast.makeText(this, "Couldn't parse '" + newValue.toString() + "' as a integer.", Toast.LENGTH_LONG)
						.show();
			}
		}
		return false;
	}
}