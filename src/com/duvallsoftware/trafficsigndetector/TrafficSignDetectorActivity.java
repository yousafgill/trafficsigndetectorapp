package com.duvallsoftware.trafficsigndetector;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import pt.lighthouselabs.obd.commands.SpeedObdCommand;
import pt.lighthouselabs.obd.enums.AvailableCommandNames;
import roboguice.RoboGuice;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.duvallsoftware.odbhelpers.AbstractGatewayService;
import com.duvallsoftware.odbhelpers.ConfigActivity;
import com.duvallsoftware.odbhelpers.ObdCommandJob;
import com.duvallsoftware.odbhelpers.ObdGatewayService;
import com.duvallsoftware.trafficsigndetector.R.raw;

public class TrafficSignDetectorActivity extends Activity implements CvCameraViewListener2 {
	static {
		RoboGuice.setUseAnnotationDatabases(false);
	}

	private static final String SPEED_SIGNS_PREFIX = "c13_";
	
	private static int DEFAULT_MAX_SPEED = 120;
	private static int currentMaxSpeed = DEFAULT_MAX_SPEED;
	
	private boolean isDebug = true;
	
	private TextToSpeech tts;
	private boolean isTTSInitialized = false;
	
	private static native String[] detectTrafficSigns(long matAddrRgba, int viewMode, boolean saveShapes, boolean showFPS);
	private static native void initTrafficSignsDetector(String assetsDataPath, boolean ocrEnabled);
	private static native void destroyANNs();
	
	public static final String TAG = "TrafficSignsDetector::Activity";

	private static final int VIEW_HLS_CONVERSION = 0;
	private static final int VIEW_COLOR_EXTRACTION = 1;
	private static final int VIEW_CANNY_CONVERSION = 2;
	private static final int VIEW_EROSION_DILATION = 3;
	private static final int VIEW_DETECT_SHAPES = 4;
	private static final int VIEW_SIGNS_RECOGNIZE = 5;

	private int mViewMode;
	private int mZoom;
	private Mat mRgba;
	private Mat mIntermediateMat;
	private Mat mGray;

	// Bluetooth
	private boolean bluetoothPrefEnabled;
	
	// Camera
	private boolean saveShapes;
	
	// Display
	private boolean showFPS;
	private boolean showSpeed;
	
	// OBD
	private boolean obdEnabled;

	// Sound
	private boolean signsWarningVoiceEnabled;
	private boolean speedWarningVoiceEnabled;
	
	// Other
	private boolean enablepreProcessingMenuOptions;
	
	private MenuItem mItemHLSConversion;
	private MenuItem mItemColorExtraction;
	private MenuItem mItemCannyConversion;
	private MenuItem mItemErosionDilation;
	private MenuItem mItemDetectShapes;
	private MenuItem mItemSignsRecognize;
	private MenuItem mUpdatePreferences;

	private static List<Camera.Size> mResolutionList;
	
	private SharedPreferences prefs;
	
	// OBD related stuff
	public Map<String, String> commandResult = new HashMap<String, String>();
	private boolean isServiceBound = false;
	private AbstractGatewayService service;
	private Integer currentSpeed = 0;
	private String currentSpeedStr = null;
	private String currentRawSpeedStr = "";

	private TextView mSpeedTextView = null;
	
	// Camera related stuff
	private static CameraView mOpenCvCameraView = null;
	public static CameraView getmOpenCvCameraView() {
		return mOpenCvCameraView;
	}
	
	public static List<Camera.Size> getCameraResolutionsList() {
		return mResolutionList;
	}

	// Bluetooth related
	private static boolean bluetoothDefaultIsEnable = false;
	private boolean preRequisites = true;	

	// Default Timings
	private int display_sign_duration = 30; //seconds
	private final int VOICE_WARNING_INTERVALS = 10; //seconds
	private final String TIME_SPEED_WARNING_KEY = "speedWarningKey";
	
	private HashMap<String, Long> warningTimingsHasMap = new HashMap<String, Long>();
	
	private HashMap<String, TrafficSign> detectedSigns = new HashMap<String, TrafficSign>();
	private HashMap<String, TrafficSign> detectedSignsWarnings = new HashMap<String, TrafficSign>();	

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.surface_view);
		
		mOpenCvCameraView = (CameraView) findViewById(R.id.activity_surface_view);
		mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
		mOpenCvCameraView.setCvCameraViewListener(this);		
		
		mOpenCvCameraView.setOptimizedParameters();
		
		mViewMode = VIEW_DETECT_SHAPES;
		
		prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		mSpeedTextView = (TextView) findViewById(R.id.speedTextView);
		
		// Get initial Bluetooth state - will restore this state when App finishes
		final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
		if (btAdapter != null)
			bluetoothDefaultIsEnable = btAdapter.isEnabled();
		
		// Initialize the TSR engine
		initTrafficSignsDetector(getApplicationContext().getFilesDir().getPath(), SplashScreenActivity.OCREnabled);
		
		// Initialize the TTS engine
		if(!isTTSInitialized){
			initializeTTS();
		}
	}
	
	private void initializeTTS() {
		tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
			@SuppressWarnings("deprecation")
			public void onInit(int paramAnonymousInt) {
				if (paramAnonymousInt == 0) {
					isTTSInitialized = true;
					if (tts.isLanguageAvailable(Locale.US) >= 0) {
						tts.setLanguage(Locale.US);
					}

					new Thread(new Runnable() {
			            @Override
			            public void run() {
			            	((AudioManager) getSystemService("audio")).setSpeakerphoneOn(true);
							tts.speak("Traffic Sign Recognition application started", 1, null);
//							tts.speak("Please be aware that the this application doesn't aim to replace" +
//							" the vehicle driver in any operation. The aim of the application is to give" + 
//							" additional information to enhance the driver experience.", 1, null);
			            }
			        }).start();
					
					return;
				}
				isTTSInitialized = false;
			}
		});
	}
	
	
	
	// Region - Preferences region
	public void callPreferences(View view) {
		updateConfig();
	}
	
	public void toggleSounds(View view) {
		ImageButton btn = (ImageButton) findViewById(R.id.muteButton);
		
		// Only changes actual status, not the preferences
		if(signsWarningVoiceEnabled || speedWarningVoiceEnabled) {
			signsWarningVoiceEnabled = false;
			speedWarningVoiceEnabled = false;
			
			btn.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_lock_silent_mode_off));
		} else {
			if (prefs.contains(ConfigActivity.ENABLE_SIGNS_VOICE_KEY)) {
				signsWarningVoiceEnabled = prefs.getBoolean(ConfigActivity.ENABLE_SIGNS_VOICE_KEY, true);
			}
			if (prefs.contains(ConfigActivity.ENABLE_SPEED_WARNING_VOICE_KEY)) {
				speedWarningVoiceEnabled = prefs.getBoolean(ConfigActivity.ENABLE_SPEED_WARNING_VOICE_KEY, true);
			}
			btn.setImageDrawable(getResources().getDrawable(android.R.drawable.ic_lock_silent_mode));
		}
	}
	
	private void setDefaultResolution() {
		Camera.Size resolution = mResolutionList.get(mResolutionList.size() - 1);

		for (int i = 0; i < mResolutionList.size(); i++) {
			// Resolution preferences: 480x360; 640x480; 3..x2..
			if (mResolutionList.get(i).width == 480) {
				resolution = mResolutionList.get(i);
				break;
			} else if (mResolutionList.get(i).width == 640) {
				resolution = mResolutionList.get(i);
				break;
			} else if (mResolutionList.get(i).width > 300 && mResolutionList.get(i).width < 400) {
				resolution = mResolutionList.get(i);
				break;
			}
		}

		mOpenCvCameraView.setResolution(resolution);
		resolution = mOpenCvCameraView.getResolution();
		if( resolution != null){
			String caption = Integer.valueOf(resolution.width).toString() + "x"
					+ Integer.valueOf(resolution.height).toString();
			Toast.makeText(this, caption, Toast.LENGTH_LONG).show();
		}
	}

	private void applyPreferences() {
		// Bluetooth
		if (prefs.contains(ConfigActivity.ENABLE_BT_KEY)) {
			bluetoothPrefEnabled = prefs.getBoolean(ConfigActivity.ENABLE_BT_KEY, true);
		}
		
		// Display
		if (prefs.contains(ConfigActivity.SHOW_FPS_KEY)) {
			showFPS = prefs.getBoolean(ConfigActivity.SHOW_FPS_KEY, true);
		}
		if (prefs.contains(ConfigActivity.SHOW_SPEED_KEY)) {
			showSpeed = prefs.getBoolean(ConfigActivity.SHOW_SPEED_KEY, true);
//			mSpeedTextView.setVisibility((showSpeed ? View.VISIBLE : View.GONE));
		}
		if (prefs.contains(ConfigActivity.SIGNS_DISPLAY_PERIOD_KEY)) {
			String displaySignDurationDefaultValue = "5";
			String display_sign_duration_str = prefs.getString(ConfigActivity.SIGNS_DISPLAY_PERIOD_KEY, displaySignDurationDefaultValue); // seconds
			try {
				display_sign_duration = Integer.parseInt(display_sign_duration_str);
			} catch(NumberFormatException e) { 
				display_sign_duration = 30; // default value
			}
		}
		if (prefs.contains(ConfigActivity.DEFAULT_MAXIMUM_SPEED_KEY)) {
			String default_max_speed_str = prefs.getString(ConfigActivity.DEFAULT_MAXIMUM_SPEED_KEY, "0");
			try {
				DEFAULT_MAX_SPEED = Integer.parseInt(default_max_speed_str);
			} catch(NumberFormatException e) { 
				DEFAULT_MAX_SPEED = 120; // default value
			}
			currentMaxSpeed = DEFAULT_MAX_SPEED;
		}
		
		// OBD
		if (prefs.contains(ConfigActivity.OBD_ENABLED_KEY)) {
			obdEnabled = prefs.getBoolean(ConfigActivity.OBD_ENABLED_KEY, true);
		}

		// Sound
		if (prefs.contains(ConfigActivity.ENABLE_SIGNS_VOICE_KEY)) {
			signsWarningVoiceEnabled = prefs.getBoolean(ConfigActivity.ENABLE_SIGNS_VOICE_KEY, true);
		}
		if (prefs.contains(ConfigActivity.ENABLE_SPEED_WARNING_VOICE_KEY)) {
			speedWarningVoiceEnabled = prefs.getBoolean(ConfigActivity.ENABLE_SPEED_WARNING_VOICE_KEY, true);
		}
		
		ImageButton btn = (ImageButton) findViewById(R.id.muteButton);
		if(btn != null) {
			if(signsWarningVoiceEnabled || speedWarningVoiceEnabled) {			
				btn.setVisibility(ImageView.VISIBLE);
			} else {
				btn.setVisibility(ImageView.INVISIBLE);
			}
		}
		
		// Camera - NOTE: Resolution and zoom are only applied after camera initialized
		if (prefs.contains(ConfigActivity.SAVE_IMAGES_KEY)) {
			saveShapes = prefs.getBoolean(ConfigActivity.SAVE_IMAGES_KEY, false);
		}
		
		// Other
		
		if (prefs.contains(ConfigActivity.ENABLE_PREPROCESSING_OPTIONS_KEY)) {
			enablepreProcessingMenuOptions = prefs.getBoolean(ConfigActivity.ENABLE_PREPROCESSING_OPTIONS_KEY, false);
		}
	}
	
	private void applyCameraPreferences() {
		if(mOpenCvCameraView == null) return;		
		
		mResolutionList = mOpenCvCameraView.getResolutionList();
		if (mResolutionList != null && mResolutionList.size() > 0) {
			if(!prefs.contains(ConfigActivity.CAMERA_RESOLUTION_LIST_KEY)) {
				setDefaultResolution();
			} else {
				try {
					String resolutionWidthPref = prefs.getString(ConfigActivity.CAMERA_RESOLUTION_LIST_KEY, "372");
					int resolutionWidth = Integer.valueOf(resolutionWidthPref).intValue();
	
					Camera.Size resolution = null;
					for (Camera.Size resSize : mResolutionList) {
						if (resSize.width == resolutionWidth) {
							resolution = resSize;
							break;
						}
					}
					if (resolution != null) {
						// No need for validation because entries are retrieve from
						// camera supported resolutions
						mOpenCvCameraView.setResolution(resolution);
	
						String caption = Integer.valueOf(resolution.width).toString() + "x"
								+ Integer.valueOf(resolution.height).toString();
						Toast.makeText(this, caption, Toast.LENGTH_LONG).show();
					} else {
						setDefaultResolution();
					}
				} catch(NumberFormatException e) {
					Log.e(TAG, e.getMessage());
					setDefaultResolution();
				}
			}
		}
		
		
		if (prefs.contains(ConfigActivity.CAMERA_ZOOM_KEY)) {
			String zoomStr = prefs.getString(ConfigActivity.CAMERA_ZOOM_KEY, "0");
			try{
				mZoom = Integer.parseInt(zoomStr);			
				mOpenCvCameraView.setZoom(mZoom);
			} catch(NumberFormatException e) {
				Log.e(TAG, e.getMessage());
			}
		}
	}
	// EndRegion
	
	// Region - Lifecycle methods
	@Override
	public void onResume() {
		super.onResume();
		
		mOpenCvCameraView.enableView();		
		
		// Only enable Blueetooth and OBD query if both activated
		if(bluetoothPrefEnabled && obdEnabled) {
			// get Bluetooth device
			final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
	
			if(btAdapter != null) {
				if(!btAdapter.isEnabled()) {
					Log.d(TAG, "Enable Bluetooth");
					if(!btAdapter.enable()) {
						Log.d(TAG, "Failed to enable Bluetooth");
						showDialog(R.string.bluetooth_disabled);
					} else {
						startLiveData();
						preRequisites = true;
					}
				}
			}
		}
		
		// Only apply zoom settings after camera was initialized
		applyPreferences();
	}

	@Override
	public void onPause() {
		super.onPause();
		
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
		
		doUnbindService();
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
		if(isTTSInitialized) {
        	tts.stop();
        }
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if(isTTSInitialized) {
        	tts.shutdown();
        	isTTSInitialized = false;
        }
		
		// Disable Bluetooth if it was disabled when starting the APP
		final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
		if (btAdapter != null && btAdapter.isEnabled() && !bluetoothDefaultIsEnable) {
			Log.d(TAG, "Disable Bluetooth");
			btAdapter.disable();
		}
				
		// Free JNI resources
		destroyANNs();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();

		if(enablepreProcessingMenuOptions) {
			mItemHLSConversion = menu.add(R.string.hls_view);
			mItemColorExtraction = menu.add(R.string.color_extraction);
			mItemCannyConversion = menu.add(R.string.canny_conversion);
			mItemErosionDilation = menu.add(R.string.erosion_dilation);
			mItemDetectShapes = menu.add(R.string.detect_shapes);
			mItemSignsRecognize = menu.add(R.string.signs_recognize);
		}
		
		mUpdatePreferences = menu.add(R.string.updatePreferences);
		mResolutionList = mOpenCvCameraView.getResolutionList();
		
		return super.onPrepareOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item == mItemHLSConversion) {
			mViewMode = VIEW_HLS_CONVERSION;
		} else if (item == mItemColorExtraction) {
			mViewMode = VIEW_COLOR_EXTRACTION;
		} else if (item == mItemCannyConversion) {
			mViewMode = VIEW_CANNY_CONVERSION;
		} else if (item == mItemErosionDilation) {
			mViewMode = VIEW_EROSION_DILATION;
		} else if (item == mItemDetectShapes) {
			mViewMode = VIEW_DETECT_SHAPES;
		} else if (item == mItemSignsRecognize) {
			mViewMode = VIEW_SIGNS_RECOGNIZE;
		}else if(item == mUpdatePreferences) {
	        updateConfig();
		}

		return true;
	}
	
	@Override
	public void onCameraViewStarted(int width, int height) {
		if(mOpenCvCameraView == null) {
			Log.e(TAG, "mOpenCvCameraView is NULL");
			return;
		}		

		mRgba = new Mat(height, width, CvType.CV_8UC4);
		mIntermediateMat = new Mat(height, width, CvType.CV_8UC4);
		mGray = new Mat(height, width, CvType.CV_8UC1);

		// Only apply zoom after camera was initialized
		applyCameraPreferences();
	}
	
	@Override
	public void onCameraViewStopped() {
		mRgba.release();
		mGray.release();
		mIntermediateMat.release();
	}

	@Override
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		mRgba = inputFrame.rgba();

		return processSignsDetection(mRgba);
	}
	// EndRegion
	
	
	// Region - Signs Detection methods
	/*
	 * Remove signs which are being displayed for more than defined timeframe
	 * Speed signs are displayed the double time long than other signs
	 */
	private void expireDetectedSigns() {
		long currentMillis = System.currentTimeMillis();
		for (Iterator<Map.Entry<String, TrafficSign>> it = detectedSigns.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, TrafficSign> entry = it.next();
			int signDisplayDuration = display_sign_duration;
			if(((TrafficSign) entry.getValue()).getSignId().toLowerCase().startsWith(SPEED_SIGNS_PREFIX)) {
				// Display the Speed Signs a little bit longer 
				signDisplayDuration *= (int)1.5;
			}
			if (currentMillis - ((TrafficSign) entry.getValue()).getDetectedTimestamp() > (signDisplayDuration * 1000)) {
				it.remove();
			}
		}
		
		for (Iterator<Map.Entry<String, TrafficSign>> it = detectedSignsWarnings.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, TrafficSign> entry = it.next();
			int signWarningTimeLapse = 2000; // 2 seconds (in miliseconds)

			if (currentMillis - ((TrafficSign) entry.getValue()).getDetectedTimestamp() > signWarningTimeLapse) {
				it.remove();
			}
		}
		
		// Update the current max Speed allowed
		currentMaxSpeed = DEFAULT_MAX_SPEED;
		for (Iterator<Map.Entry<String, TrafficSign>> it = detectedSigns.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, TrafficSign> entry = it.next();
			String signId = ((TrafficSign) entry.getValue()).getSignId().toLowerCase();
			if(signId.startsWith(SPEED_SIGNS_PREFIX)) {
				try {
					currentMaxSpeed = Integer.valueOf(signId.substring(signId.indexOf(SPEED_SIGNS_PREFIX))).intValue();
					Log.d(TAG, "Max speed set to " + currentMaxSpeed);
				} catch(NumberFormatException e) {}
			}
		}		
	}
	
	private void drawDetectedSigns(Mat mRgba) {
		// Images are 111 x 111 pixels
		final int padding = 20;
		final int imgWidth = 111;
		final int x = padding;

		int i = 0, y_pos_idx = -1;
		for (TrafficSign detected_sign : detectedSigns.values()) {
			if (y_pos_idx++ == 3) {
				y_pos_idx = 0;
			}

			int imageSrcID = getResId(detected_sign.getSignId().toLowerCase(), raw.class);
			if (imageSrcID >= 0) {
				try {
					int y = mRgba.rows() - imgWidth - padding - (i++ * (imgWidth + padding));

					Mat image = Utils.loadResource(getApplicationContext(), imageSrcID, -1);
					Mat imageDst = new Mat();

					Imgproc.cvtColor(image, imageDst, Imgproc.COLOR_BGRA2RGBA);
					imageDst.copyTo(mRgba.colRange(x, x + imgWidth).rowRange(y, y + imgWidth));
					image.release();
					imageDst.release();
				} catch (Exception e) {
					Log.d(TAG, "Exception: " + e.getMessage());
				}
			}
		}
	}
	
	private void updateCurrentSpeedDisplay() {
		// Set Current Speed		
		if(mSpeedTextView.getVisibility() == View.VISIBLE){			
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// Set Current Speed					
					mSpeedTextView.setText(currentSpeedStr);
					try {
						int currentSpeedValue = Integer.valueOf(currentSpeedStr).intValue();
						// Only play voice warning if it is defined to ON
						if(speedWarningVoiceEnabled && currentSpeedValue > currentMaxSpeed) {
							Long last_speed_warning_timestamp = warningTimingsHasMap.get(TIME_SPEED_WARNING_KEY);
							// Warn only if last warning was before defined interval
							if( last_speed_warning_timestamp == null || 
									System.currentTimeMillis() > (last_speed_warning_timestamp + (VOICE_WARNING_INTERVALS * 1000))) {
								warningTimingsHasMap.put(TIME_SPEED_WARNING_KEY, System.currentTimeMillis());
								say("Warning! Current vehicle speed is above limit!");
							}
						}
					} catch(NumberFormatException e){}
				}
			});
			
//			Imgproc.putText(mRgba, currentSpeedStr, new Point(180, 430), Core.FONT_HERSHEY_DUPLEX, 2, new Scalar(255, 0, 0, 255), 5);
//			Imgproc.putText(mRgba, "RAW SPEED: " + currentRawSpeedStr, new Point(180, 450), Core.FONT_HERSHEY_DUPLEX, 4, new Scalar(255, 0, 0, 255), 2);
		}
	}
	
	/*
	 * Call JNI sign detector and process detected signs, if any
	 */
	private Mat processSignsDetection(Mat mRgba) {
		// Remove signs which are being displayed for more than defined timeframe
		expireDetectedSigns();

		String detected_signs[] = detectTrafficSigns(mRgba.getNativeObjAddr(), mViewMode, saveShapes, showFPS);
		// Add new signs to hashmap
		if (detected_signs != null) {
			for (int i = 0; i < detected_signs.length; i++) {
				final TrafficSign detected_sign = new TrafficSign(detected_signs[i], System.currentTimeMillis());
				// TODO: Skip adding multiple speed signs (C13_XXX)- add only the one with lower limit
				// This should also take into account possible existent speed signs in the hashmap
				String signId = detected_sign.getSignId();
				Log.d(TAG, "Found new Sign: " + detected_sign.getSignId());
				
				// XXX: if the speed signs changes this needs to be updated
				// TODO: find a better way of dealing with this
				boolean addDetectedSign = true;
				
				// If it's a speed sign check if there are other speed signs on the table
				// Only one speed sign (the one with the lowest speed allowance) will be displayed
				if(signId.toLowerCase().startsWith(SPEED_SIGNS_PREFIX)) {
					try {
						int signSpeedValue = Integer.valueOf(signId.toLowerCase().substring(SPEED_SIGNS_PREFIX.length())).intValue();
						Log.d(TAG, "Found new Sign with value = " + signSpeedValue);
					    // Make a copy of the current hash
						HashMap<String, TrafficSign> tempDetectedSigns = (HashMap<String, TrafficSign>) detectedSigns.clone();
						Iterator<String> it = tempDetectedSigns.keySet().iterator();
						
						if(it.hasNext()) {
							while(it.hasNext()) {
								String element = (String)it.next();
								Log.d(TAG, "Test Hash element " + element + " ...");
								if(element.toLowerCase().startsWith(SPEED_SIGNS_PREFIX)) {
									String signIdentifier = element.toLowerCase().substring(SPEED_SIGNS_PREFIX.length());
									Log.d(TAG, "Test Hash element identifier = " + signIdentifier);
									try {
										int elementSpeedValue = Integer.valueOf(signIdentifier).intValue();
										Log.d(TAG, "Test Hash element identifier value = " + elementSpeedValue);
										// new sign has lower speed value than existing
										// Remove it from the signs hashmap
										if(signSpeedValue > elementSpeedValue) {
											Log.d(TAG, "New detected sign has higher value = " + signSpeedValue + ". Don't add it to the hash table");
											addDetectedSign = false;
										} else {
											Log.d(TAG, "New detected sign has lower value = " + signSpeedValue + ". Add it to the hash table");
											// remove existent one with higher speed value
											detectedSigns.remove(element);
											Log.d(TAG, "Remove Sign " + element + " from the hash table");											
										}
									} catch(NumberFormatException e){
										// Shouldn't happen
										e.printStackTrace();
									}
								}
							}
						}
					} catch(NumberFormatException e){
						// Shouldn't happen
						e.printStackTrace();
					}
				}
				
				if(addDetectedSign) {
					Log.d(TAG, "Add detected sign " + signId + " to the hash table");
					detectedSigns.put(detected_sign.getSignId(), detected_sign);
					
					Log.d(TAG, "signsWarningVoiceEnabled: " + signsWarningVoiceEnabled);
					Log.d(TAG, "isTTSInitialized: " + isTTSInitialized);
					// If voice warnings are on, warn about the detected sign
					if(signsWarningVoiceEnabled && isTTSInitialized && !detectedSignsWarnings.containsKey(signId)) {
											detectedSignsWarnings.put(signId, detected_sign);
						String detectedSignKey = detected_sign.getSignId().toLowerCase();
						Long last_sign_warning_timestamp = warningTimingsHasMap.get(detectedSignKey);
						// Warn only if last warning was before defined interval
						if( last_sign_warning_timestamp == null || 
								System.currentTimeMillis() > (last_sign_warning_timestamp + (VOICE_WARNING_INTERVALS * 1000))) {
							warningTimingsHasMap.put(detectedSignKey, System.currentTimeMillis());
							say(getResString(detectedSignKey));
						}						
					}
				}
			}
		}

		drawDetectedSigns(mRgba);		

		updateCurrentSpeedDisplay();
		
		return mRgba;
	}
	
	private void say(final String text) {
		new Thread(new Runnable() {
            @SuppressWarnings("deprecation")
			@Override
            public void run() {
            	((AudioManager) getSystemService("audio")).setSpeakerphoneOn(true);
				tts.speak(text, 1, null);
            }
        }).start();
	}

	public String getResString(String resName) {
		try {
			int resId = getResources().getIdentifier(resName, "string", getPackageName());
			return getString(resId);
		} catch (Exception e) {
			return "";
		}
	}
	
	public static int getResId(String resName, Class<?> c) {
		try {
			Field idField = c.getDeclaredField(resName);
			return idField.getInt(idField);
		} catch (Exception e) {
			return -1;
		}
	}	
	// EndRegion
	
	
	// Region - OBD related methods		
	public static String LookUpCommand(String txt) {
		for (AvailableCommandNames item : AvailableCommandNames.values()) {
			if (item.getValue().equals(txt))
				return item.name();
		}
		return txt;
	}
	
	public void stateUpdate(final ObdCommandJob job) {		
		try {
			final String cmdName = job.getCommand().getName();
			String cmdResult = "";
			final String cmdID = LookUpCommand(cmdName);			

			if (job.getState().equals(ObdCommandJob.ObdCommandJobState.EXECUTION_ERROR)) {
				cmdResult = job.getCommand().getResult();
			} else {
				cmdResult = job.getCommand().getFormattedResult();
				if (AvailableCommandNames.SPEED.getValue().equals(cmdName)) {
					currentSpeedStr = job.getCommand().getFormattedResult();
					currentRawSpeedStr = job.getCommand().getResult();
					
					if(currentSpeedStr != null && mSpeedTextView.getVisibility() != View.VISIBLE){
						setSpeedTextViewVisibility();
					}				
				}
			}
			
			if(isDebug) {
				commandResult.put(cmdID, cmdResult);
				Date dt = new Date();
				String sdt = DateFormat.format("yyyyMMdd  kk:mm", dt).toString();
				appendLog(sdt + ": " + cmdID + " => " + cmdResult);
			}
		} catch (Exception e) {
			Log.e(TAG, "Exception -> " + e);
		}
	}

	private final Runnable mQueueCommands = new Runnable() {
		public void run() {
			if( !isServiceBound ) return;
			
			Log.d(TAG, "mQueueCommands called ...");
			if (service != null && service.isRunning() && service.queueEmpty()) {
				queueCommands();
				commandResult.clear();
			}
			// run again in period defined in preferences
			new Handler().postDelayed(mQueueCommands, ConfigActivity.getObdUpdatePeriod(prefs));
		}
	};

	
	private ServiceConnection serviceConn = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder binder) {
			Log.d(TAG, className.toString() + " service is bound");			
			service = ((AbstractGatewayService.AbstractGatewayServiceBinder) binder).getService();
			service.setContext(TrafficSignDetectorActivity.this);
			isServiceBound = true;
			Log.d(TAG, "Starting live data");
			try {
				service.startService();
			} catch (Exception ioe) {
				Log.e(TAG, "Failure Starting live data");
				doUnbindService();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			Log.d(TAG, className.toString() + " service disconnected");
			isServiceBound = false;
		}
	};

	/**
	   *
	   */
	private void queueCommands() {
		if (isServiceBound && service != null) {
			service.queueJob(new ObdCommandJob(new SpeedObdCommand()));
		}
	}

	private void startLiveData() {
		Log.d(TAG, "Starting live data..");
		doBindService();

		// start command execution
		new Handler().post(mQueueCommands);
	}

	private void stopLiveData() {
		Log.d(TAG, "Stopping live data..");

		doUnbindService();
	}
	  
	private void doBindService() {
		if (!isServiceBound) {
			Log.d(TAG, "Binding OBD service..");
			// Make sure the prerequistes include the OBD device check
			if (preRequisites) {
				Log.d(TAG, "Binding OBD ObdGatewayService service..");
				Intent serviceIntent = new Intent(this, ObdGatewayService.class);
				isServiceBound = getApplicationContext().bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
			} else {
				Log.d(TAG, "Not Binding to OBDGatewayService service.");
			}			
		}
		setSpeedTextViewVisibility();
	}

	private void doUnbindService() {
		if (service != null && service.isRunning()) {
			service.stopService();
		}
		if(serviceConn != null) {
			Log.d(TAG, "Unbinding OBD service..");
			try {
				unbindService(serviceConn);
			} catch(Exception e) {}
		}
		isServiceBound = false;
		
		setSpeedTextViewVisibility();
	}
	
	private void updateConfig() {
		startActivity(new Intent(this, ConfigActivity.class));
	}
	
	private void setSpeedTextViewVisibility(){
		if(currentSpeedStr != null && showSpeed && obdEnabled) {
			mSpeedTextView.setVisibility((showSpeed ? View.VISIBLE : View.GONE));
		}
	}
	
	private void appendLog(String text) {
		File logFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		logFile = new File(logFile, "traffic_signs.log");
		if (!logFile.exists()) {
			try {
				logFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {
			BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
			buf.append(text);
			buf.newLine();
			buf.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	// EndRegion
}
