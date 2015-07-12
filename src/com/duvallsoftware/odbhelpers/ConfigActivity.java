package com.duvallsoftware.odbhelpers;

import java.util.ArrayList;
import java.util.Set;

import pt.lighthouselabs.obd.commands.ObdCommand;
import pt.lighthouselabs.obd.commands.SpeedObdCommand;
import pt.lighthouselabs.obd.enums.ObdProtocols;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.widget.Toast;

import com.duvallsoftware.trafficsigndetector.R;

/**
 * This class uses some base code from the OBD Reader application from
 * com.github.pires.obd.reader
 */
public class ConfigActivity extends PreferenceActivity implements OnPreferenceChangeListener {

	public static final String BLUETOOTH_LIST_KEY = "bluetooth_list_preference";
	public static final String OBD_UPDATE_PERIOD_KEY = "obd_update_period_preference";
	public static final String PROTOCOLS_LIST_KEY = "obd_protocols_preference";
	public static final String ENABLE_BT_KEY = "enable_bluetooth_preference";
	public static final String CONFIG_READER_KEY = "reader_config_preference";
	public static final String IMPERIAL_UNITS_KEY = "imperial_units_preference";

	/**
	 * @param prefs
	 * @return
	 */
	public static int getObdUpdatePeriod(SharedPreferences prefs) {
		// In seconds
		String periodString = prefs.getString(ConfigActivity.OBD_UPDATE_PERIOD_KEY, "4");
		int period = 4000; // by default 4000ms

		try {
			period = Integer.parseInt(periodString) * 1000;
		} catch (Exception e) {
		}

		if (period <= 0) {
			period = 250;
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
			boolean selected = prefs.getBoolean(cmd.getName(), true);
			if (selected)
				ucmds.add(cmd);
		}
		return ucmds;
	}

	/**
	 * @param prefs
	 * @return
	 */
	public static String[] getReaderConfigCommands(SharedPreferences prefs) {
		String cmdsStr = prefs.getString(CONFIG_READER_KEY, "atsp0\natz");
		String[] cmds = cmdsStr.split("\n");
		return cmds;
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		/*
		 * Read preferences resources available at res/xml/preferences.xml
		 */
		addPreferencesFromResource(R.xml.preferences);

		ArrayList<CharSequence> pairedDeviceStrings = new ArrayList<CharSequence>();
		ArrayList<CharSequence> vals = new ArrayList<CharSequence>();
		ListPreference listBtDevices = (ListPreference) getPreferenceScreen().findPreference(BLUETOOTH_LIST_KEY);
		ArrayList<CharSequence> protocolStrings = new ArrayList<CharSequence>();
		ListPreference listProtocols = (ListPreference) getPreferenceScreen().findPreference(PROTOCOLS_LIST_KEY);
		String[] prefKeys = new String[] { OBD_UPDATE_PERIOD_KEY };
		for (String prefKey : prefKeys) {
			EditTextPreference txtPref = (EditTextPreference) getPreferenceScreen().findPreference(prefKey);
			txtPref.setOnPreferenceChangeListener(this);
		}

		/*
		 * Available OBD protocols
		 */
		for (ObdProtocols protocol : ObdProtocols.values()) {
			protocolStrings.add(protocol.name());
		}
		listProtocols.setEntries(protocolStrings.toArray(new CharSequence[0]));
		listProtocols.setEntryValues(protocolStrings.toArray(new CharSequence[0]));

		/*
		 * Let's use this device Bluetooth adapter to select which paired OBD-II
		 * compliant device we'll use.
		 */
		final BluetoothAdapter mBtAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBtAdapter == null) {
			listBtDevices.setEntries(pairedDeviceStrings.toArray(new CharSequence[0]));
			listBtDevices.setEntryValues(vals.toArray(new CharSequence[0]));

			// we shouldn't get here, still warn user
			Toast.makeText(this, "This device does not support Bluetooth.", Toast.LENGTH_LONG).show();

			return;
		}

		/*
		 * Listen for preferences click.
		 * 
		 * TODO there are so many repeated validations :-/
		 */
		final Activity thisActivity = this;
		listBtDevices.setEntries(new CharSequence[1]);
		listBtDevices.setEntryValues(new CharSequence[1]);
		listBtDevices.setOnPreferenceClickListener(new OnPreferenceClickListener() {
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
		listBtDevices.setEntries(pairedDeviceStrings.toArray(new CharSequence[0]));
		listBtDevices.setEntryValues(vals.toArray(new CharSequence[0]));
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
				Double.parseDouble(newValue.toString());
				return true;
			} catch (Exception e) {
				Toast.makeText(this, "Couldn't parse '" + newValue.toString() + "' as a number.", Toast.LENGTH_LONG)
						.show();
			}
		}
		return false;
	}
}