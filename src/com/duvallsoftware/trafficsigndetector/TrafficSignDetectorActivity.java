package com.duvallsoftware.trafficsigndetector;

import java.lang.reflect.Field;
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
import org.opencv.core.Point;
import org.opencv.core.Scalar;
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
import android.hardware.Camera.Size;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import com.duvallsoftware.odbhelpers.AbstractGatewayService;
import com.duvallsoftware.odbhelpers.ConfigActivity;
import com.duvallsoftware.odbhelpers.MockObdGatewayService;
import com.duvallsoftware.odbhelpers.ObdCommandJob;
import com.duvallsoftware.odbhelpers.ObdGatewayService;
import com.duvallsoftware.trafficsigndetector.R.raw;

public class TrafficSignDetectorActivity extends Activity implements CvCameraViewListener2 {
	static {
		RoboGuice.setUseAnnotationDatabases(false);
	}

	private TextToSpeech tts;
	private boolean isTTSInitialized = false;
	
	private static native String[] detectTrafficSigns(long matAddrRgba, int viewMode, boolean saveShapes, boolean showFPS);
	private static native void initTrafficSignsDetector(String assetsDataPath);
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

	private boolean saveShapes;
	private boolean showFPS;

	private MenuItem mItemHLSConversion;
	private MenuItem mItemColorExtraction;
	private MenuItem mItemCannyConversion;
	private MenuItem mItemErosionDilation;
	private MenuItem mItemDetectShapes;
	private MenuItem mItemSignsRecognize;
	private MenuItem mUpdatePreferences;

	private static List<Size> mResolutionList;
	
	private SharedPreferences prefs;
	
	// OBD related stuff
	public Map<String, String> commandResult = new HashMap<String, String>();
	private boolean isServiceBound = false;
	private AbstractGatewayService service;

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
	private Integer currentSpeed = 0;

	// 3 Seconds
	private static final int SHOW_SIGN_DURATION = 3000;

	private HashMap<String, DetectedSign> detectedSigns = new HashMap<String, DetectedSign>();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.surface_view);
		
		mOpenCvCameraView = (CameraView) findViewById(R.id.activity_surface_view);
		mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
		mOpenCvCameraView.setCvCameraViewListener(this);		
		
		mViewMode = VIEW_DETECT_SHAPES;
		
		prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
		if (btAdapter != null)
			bluetoothDefaultIsEnable = btAdapter.isEnabled();		
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();

		mItemHLSConversion = menu.add(R.string.hls_view);
		mItemColorExtraction = menu.add(R.string.color_extraction);
		mItemCannyConversion = menu.add(R.string.canny_conversion);
		mItemErosionDilation = menu.add(R.string.erosion_dilation);
		mItemDetectShapes = menu.add(R.string.detect_shapes);
		mItemSignsRecognize = menu.add(R.string.signs_recognize);
		
		mUpdatePreferences = menu.add(R.string.updatePreferences);

		mResolutionList = mOpenCvCameraView.getResolutionList();
		
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
		
		doUnbindService();
	}

	@Override
	public void onResume() {
		super.onResume();
		
		mOpenCvCameraView.enableView();
		initTrafficSignsDetector(getApplicationContext().getFilesDir().getPath());
		
		initializeTTS();
		
		// get Bluetooth device
		final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

		preRequisites = btAdapter != null && btAdapter.isEnabled();
		if (!preRequisites) {
			Log.i(TAG, "Enable Bluetooth");
			preRequisites = btAdapter.enable();
		}

		if (!preRequisites) {
			Log.i(TAG, "Failed to enable Bluetooth");
			showDialog(R.string.bluetooth_disabled);
		} else {
			startLiveData();
		}
	}

	public void onDestroy() {
		super.onDestroy();
		
		if(isTTSInitialized) {
        	tts.shutdown();
        }
		
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();

		doUnbindService();
		
		// Disable Bluetooth if it was disabled when starting the APP
		final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
		if (btAdapter != null && btAdapter.isEnabled() && !bluetoothDefaultIsEnable) {
			Log.i(TAG, "Disable Bluetooth");
			btAdapter.disable();
		}
				
		// Free JNI resources
		destroyANNs();
	}

	public void onCameraViewStarted(int width, int height) {
		if(mOpenCvCameraView == null) {
			Log.e(TAG, "mOpenCvCameraView is NULL");
			return;
		}
		
		mResolutionList = mOpenCvCameraView.getResolutionList();
		if (mResolutionList != null && mResolutionList.size() > 0 && !prefs.contains(ConfigActivity.CAMERA_RESOLUTION_LIST_KEY)) {			
			setDefaultResolution();
		}

		mRgba = new Mat(height, width, CvType.CV_8UC4);
		mIntermediateMat = new Mat(height, width, CvType.CV_8UC4);
		mGray = new Mat(height, width, CvType.CV_8UC1);

		// Only apply after camera was initialized because of zoom setting
		applyPreferences();
	}

	private void setDefaultResolution() {
		Size resolution = mResolutionList.get(mResolutionList.size() - 1);

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
		if (prefs.contains(ConfigActivity.SAVE_IMAGES_KEY)) {
			saveShapes = prefs.getBoolean(ConfigActivity.SAVE_IMAGES_KEY, false);
		}
		
		if (prefs.contains(ConfigActivity.SHOW_FPS_KEY)) {
			showFPS = prefs.getBoolean(ConfigActivity.SHOW_FPS_KEY, true);
		}
		
		try {
			String resolutionWidthPref = prefs.getString(ConfigActivity.CAMERA_RESOLUTION_LIST_KEY, "372");
			int resolutionWidth = Integer.valueOf(resolutionWidthPref).intValue();

			if (mResolutionList.size() > 0) {
				Size resolution = null;
				for (Size resSize : mResolutionList) {
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
			}
		}catch(NumberFormatException e) {
			Log.e(TAG, e.getMessage());
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

	public void onCameraViewStopped() {
		mRgba.release();
		mGray.release();
		mIntermediateMat.release();
	}

	public static int getResId(String resName, Class<?> c) {
		try {
			Field idField = c.getDeclaredField(resName);
			return idField.getInt(idField);
		} catch (Exception e) {
			return -1;
		}
	}

	private void expireDetectedSigns() {
		long currentMillis = System.currentTimeMillis();
		for (Iterator<Map.Entry<String, DetectedSign>> it = detectedSigns.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, DetectedSign> entry = it.next();
			if (currentMillis - ((DetectedSign) entry.getValue()).getDetectedTimestamp() > SHOW_SIGN_DURATION) {
				it.remove();
			}
		}
	}

	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		mRgba = inputFrame.rgba();

		expireDetectedSigns();

		String detected_signs[] = detectTrafficSigns(mRgba.getNativeObjAddr(), mViewMode, saveShapes,
				showFPS);
		// Add new signs to hasmap
		if (detected_signs != null) {
			for (int i = 0; i < detected_signs.length; i++) {
				DetectedSign detected_sign = new DetectedSign(detected_signs[i], System.currentTimeMillis());
				detectedSigns.put(detected_sign.getSignId(), detected_sign);
//				Log.i(TAG, "Found new Sign: " + detected_sign.getSignId());
			}
		}

		// Images are 111 x 111 pixels
		final int padding = 20;
		final int imgWidth = 111;
		final int x = padding;

		int i = 0, y_pos_idx = -1;
		for (DetectedSign detected_sign : detectedSigns.values()) {
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
					Log.i(TAG, "Exception: " + e.getMessage());
				}
			}
		}

		// Set Current Speed
		Imgproc.putText(mRgba, "SPEED: " + (currentSpeed != null ? currentSpeed : 0) + " km/h", new Point(180, 410), 3, 1, new Scalar(255, 0, 0, 255), 2);
		
		return mRgba;
	}

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

	private void initializeTTS() {
		tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
			@SuppressWarnings("deprecation")
			public void onInit(int paramAnonymousInt) {
				if (paramAnonymousInt == 0) {
					isTTSInitialized = true;
					if (tts.isLanguageAvailable(Locale.US) >= 0) {
						tts.setLanguage(Locale.US);
					}

					((AudioManager) getSystemService("audio")).setSpeakerphoneOn(true);
					tts.speak("Traffic Sign Recognition application started", 1, null);
					tts.speak("Please be aware that the this application doesn't aim to replace" +
					" the vehicle driver in any operation. The aim of the application is to give" + 
					" additional information to enhance the driver experience.", 1, null);
					return;
				}
				isTTSInitialized = false;
			}
		});
	}
	
	@SuppressWarnings("unused")
	private class DetectedSign {

		private String signId = null;
		private long detectedTimestamp = -1;

		public DetectedSign(String signId, long timestampt) {
			this.signId = signId;
			this.detectedTimestamp = timestampt;
		}

		/**
		 * @return the detectedTimestamp
		 */
		public long getDetectedTimestamp() {
			return detectedTimestamp;
		}

		/**
		 * @param detectedTimestamp
		 *            the detectedTimestamp to set
		 */		
		public void setDetectedTimestamp(long detectedTimestamp) {
			this.detectedTimestamp = detectedTimestamp;
		}

		/**
		 * @return the signId
		 */
		public String getSignId() {
			return signId;
		}

		/**
		 * @param signId
		 *            the signId to set
		 */
		public void setSignId(String signId) {
			this.signId = signId;
		}
	}

	private void updateConfig() {
		startActivity(new Intent(this, ConfigActivity.class));
	}	

	private void updateSpeed(final ObdCommandJob job) {
		SpeedObdCommand command = (SpeedObdCommand) job.getCommand();
		currentSpeed = command.getMetricSpeed();
		Log.d("OBD-II Operation", "Current Speed: " + currentSpeed);
	}
	
	public static String LookUpCommand(String txt) {
		for (AvailableCommandNames item : AvailableCommandNames.values()) {
			if (item.getValue().equals(txt))
				return item.name();
		}
		return txt;
	}

	public void stateUpdate(final ObdCommandJob job) {
		final String cmdName = job.getCommand().getName();
		String cmdResult = "";
		final String cmdID = LookUpCommand(cmdName);

		if (job.getState().equals(ObdCommandJob.ObdCommandJobState.EXECUTION_ERROR)) {
			cmdResult = job.getCommand().getResult();
		} else {
			cmdResult = job.getCommand().getFormattedResult();
		}

		commandResult.put(cmdID, cmdResult);
		if( job.getCommand() instanceof SpeedObdCommand ) {
			updateSpeed(job);
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
			isServiceBound = true;
			service = ((AbstractGatewayService.AbstractGatewayServiceBinder) binder).getService();
			service.setContext(TrafficSignDetectorActivity.this);
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
			boolean status = false;
			
			Log.d(TAG, "Binding OBD service..");
			// Make sure the prerequistes include the OBD device check
			if (preRequisites) {
				Log.d(TAG, "Binding OBD ObdGatewayService service..");
				Intent serviceIntent = new Intent(this, ObdGatewayService.class);
				status = getApplicationContext().bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
			} else {
				Log.d(TAG, "Binding OBD MockObdGatewayService service..");
				Intent serviceIntent = new Intent(this, MockObdGatewayService.class);
				status = getApplicationContext().bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);				
			}			
			Log.d(TAG, "Binding OBD ObdGatewayService service Result: " + status);
			
			isServiceBound = status;
		}
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
	}
}
