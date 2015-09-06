package com.duvallsoftware.trafficsigndetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
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
import android.content.SharedPreferences.Editor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera.Size;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;
import android.media.AudioManager;

import com.duvallsoftware.odbhelpers.AbstractGatewayService;
import com.duvallsoftware.odbhelpers.ConfigActivity;
import com.duvallsoftware.odbhelpers.MockObdGatewayService;
import com.duvallsoftware.odbhelpers.ObdCommandJob;
import com.duvallsoftware.odbhelpers.ObdGatewayService;
import com.duvallsoftware.trafficsigndetector.R;
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
	
	private static final String TAG = "TrafficSignsDetector::Activity";

	private static final int NUMBER_WARNING_SIGNS = 6; 
	private static final int NUMBER_FORBIDDEN_SIGNS = 15;
	private static final int NUMBER_OBLIGATORY_SIGNS = 6;
	private static final int NUMBER_INFORMATION_SIGNS = 2;	
		
	private static final String warning_sign_id_array[] = { "A1a", "A1b", "B1", "B8", "B9a", "B9b" };	
	private static final String forbidden_sign_id_array[] = { "B2", "C1", "C2", "C11a", "C11b", "C13_40", "C13_50", "C13_60", "C13_70", "C13_80", "C13_90", "C13_100", "C13_120", "C14a", "C14b" };
	private static final String obligatory_sign_id_array[] = { "D1a", "D1c", "D3a", "D4", "D8_40", "D8_50" };
	private static final String information_sign_id_array[] = { "B6", "H7" };
	
	private static final int NO_SIGN = 0;
	private static final int WARNING_SIGN = 1;
	private static final int FORBIDDEN_SIGN = 2;
	private static final int OBLIGATORY_SIGN = 3;
	private static final int INFORMATION_SIGN = 4;

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
	private MenuItem mItemPreviewResolution;
	private MenuItem mItemNoZoom;
	private MenuItem mItemZoomMinus;
	private MenuItem mItemZoomPlus;
	// XXX: Remove on final version
	private MenuItem mItemSaveShapes;
	private MenuItem mItemShowFPS;

	private SubMenu mResolutionMenu;
	private MenuItem[] mResolutionMenuItems;

	private List<Size> mResolutionList;

	private SharedPreferences sharedpreferences;
	private static final String zoomPref = "zoomKey";
	private static final String saveShapesPref = "saveShapesKey";
	private static final String showFPSPref = "showFPSKey";
	private static final String resolutionWidthPref = "resolutionWidthPref";

	private CameraView mOpenCvCameraView;

	static AssetManager assetManager;
	private boolean forceCopy = false;
	private String assetsDataPath = null;

	// Bluetooth related
	private static boolean bluetoothDefaultIsEnable = false;
	private boolean preRequisites = true;
	private int currentSpeed = 0;

	// 3 Seconds
	private static final int SHOW_SIGN_DURATION = 3000;

	private HashMap<String, DetectedSign> detectedSigns = new HashMap<String, DetectedSign>();

	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
				case LoaderCallbackInterface.SUCCESS: {
					Log.i(TAG, "OpenCV loaded successfully");
	
					// Load native library after(!) OpenCV initialization
					System.loadLibrary("gnustl_shared");
//					System.loadLibrary("lept");
//					System.loadLibrary("tess");					
					System.loadLibrary("sign_detector");
	
					mOpenCvCameraView.enableView();
					
					initTrafficSignsDetector(assetsDataPath);
				}
				break;
				default: {
					super.onManagerConnected(status);
				}
				break;
			}
		}
	};

	public TrafficSignDetectorActivity() {
		Log.i(TAG, "Instantiated new " + this.getClass());
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.surface_view);

		mOpenCvCameraView = (CameraView) findViewById(R.id.activity_surface_view);
		mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
		mOpenCvCameraView.setCvCameraViewListener(this);

		mViewMode = VIEW_DETECT_SHAPES;

		// XXX: Remove on final version
		File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		path = new File(path, "trafficsignsdetected");
		if (!path.exists()) {
			path.mkdirs();
			// initiate media scan and put the new things into the path array to
			// make the scanner aware of the location and the files you want to see
			MediaScannerConnection.scanFile(this, new String[] { path.toString() }, null, null);
		}

		assetManager = getApplicationContext().getAssets();
		
		sharedpreferences = getSharedPreferences("TrafficSignDetectionPrefs", Context.MODE_PRIVATE);
		assetsDataPath = getApplicationContext().getFilesDir().getPath();

		copyANNFilesToAssetPath();

		final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
		if (btAdapter != null)
			bluetoothDefaultIsEnable = btAdapter.isEnabled();
	}
	
	private void copyANNFilesToAssetPath() {
		// Copy ANN files to asset path available from JNI context
		String obligatoryANN = "obligatory_traffic_signs.net";
		String obligatoryDataANN = assetsDataPath + File.separator + obligatoryANN;

		String informationANN = "information_traffic_signs.net";
		String informationDataANN = assetsDataPath + File.separator + informationANN;

		String forbiddenANN = "forbidden_traffic_signs.net";
		String forbiddenDataANN = assetsDataPath + File.separator + forbiddenANN;
		
		String warningANN = "warning_traffic_signs.net";
		String warningDataANN = assetsDataPath + File.separator + warningANN;		

		copyAsset(obligatoryANN, obligatoryDataANN);
		copyAsset(informationANN, informationDataANN);
		copyAsset(forbiddenANN, forbiddenDataANN);
		copyAsset(warningANN, warningDataANN);		
		
		String suffix = "_traffic_signs.net";
		for(int i=0;i<NUMBER_WARNING_SIGNS;i++) {			
			StringBuilder annName = new StringBuilder();
			annName.append(warning_sign_id_array[i]);
			annName.append(suffix);
			
			try {
				String annPath = assetsDataPath + File.separator + annName.toString();
				copyAsset(annName.toString(), annPath);
			} catch(Exception e) {
				Log.e(TAG, "Exception initializing " + annName.toString() + " ANN from file: " + e.getMessage());
			}
		}
		
		for(int i=0;i<NUMBER_FORBIDDEN_SIGNS;i++) {
			StringBuilder annName = new StringBuilder();
			annName.append(forbidden_sign_id_array[i]);
			annName.append(suffix);
			
			try {
				String annPath = assetsDataPath + File.separator + annName.toString();
				copyAsset(annName.toString(), annPath);
			} catch(Exception e) {
				Log.e(TAG, "Exception initializing " + annName.toString() + " ANN from file: " + e.getMessage());
			}
		}
		
		for(int i=0;i<NUMBER_OBLIGATORY_SIGNS;i++) {
			StringBuilder annName = new StringBuilder();
			annName.append(obligatory_sign_id_array[i]);
			annName.append(suffix);
			
			try {
				String annPath = assetsDataPath + File.separator + annName.toString();
				copyAsset(annName.toString(), annPath);
			} catch(Exception e) {
				Log.e(TAG, "Exception initializing " + annName.toString() + " ANN from file: " + e.getMessage());
			}
		}
		
		for(int i=0;i<NUMBER_INFORMATION_SIGNS;i++) {
			StringBuilder annName = new StringBuilder();
			annName.append(information_sign_id_array[i]);
			annName.append(suffix);
			
			try {
				String annPath = assetsDataPath + File.separator + annName.toString();
				copyAsset(annName.toString(), annPath);
			} catch(Exception e) {
				Log.e(TAG, "Exception initializing " + annName.toString() + " ANN from file: " + e.getMessage());
			}
		}
	}
	
	private void initializeOCR() {
		String str = getApplicationInfo().dataDir;
		File localFile = new File(str, "tessdata");
		if (!localFile.isDirectory()) {
			localFile.mkdir();
		}
		try {
			InputStream is = getAssets().open("eng.traineddata");
			FileOutputStream os = new FileOutputStream(str + "/tessdata/eng.traineddata");
			byte[] arrayOfByte = new byte[1024];
			for (;;) {
				int i = is.read(arrayOfByte);
				if (i <= 0) {
					is.close();
					os.close();
					return;
				}
				os.write(arrayOfByte, 0, i);
			}
		} catch (IOException e) {
			e.printStackTrace();
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

					((AudioManager) getSystemService("audio")).setSpeakerphoneOn(true);
					tts.speak("road sign recognition free started", 1, null);
					return;
				}
				isTTSInitialized = false;
			}
		});
	}

	private void copyAsset(String srcAsset, String dst) {
		File f = new File(dst);
		//if(!f.exists()) {
			if (assetManager == null) {
				assetManager = getApplicationContext().getAssets();
			}
			try {
				InputStream is = assetManager.open(srcAsset);
				OutputStream os = new FileOutputStream(dst);
	
				byte[] buffer = new byte[4096];
				while (is.read(buffer) > 0) {
					os.write(buffer);
				}
	
				os.flush();
				os.close();
				is.close();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		//}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();

		Log.i(TAG, "called onPrepareOptionsMenu");

		mItemHLSConversion = menu.add(R.string.hls_view);
		mItemColorExtraction = menu.add(R.string.color_extraction);
		mItemCannyConversion = menu.add(R.string.canny_conversion);
		mItemErosionDilation = menu.add(R.string.erosion_dilation);
		mItemDetectShapes = menu.add(R.string.detect_shapes);
		mItemSignsRecognize = menu.add(R.string.signs_recognize);

		mItemNoZoom = menu.add(R.string.no_zoom);
		mItemZoomMinus = menu.add(R.string.zoom_minus);
		mItemZoomPlus = menu.add(R.string.zoom_plus);

		// XXX: Remove on final version
		mItemSaveShapes = menu.add((saveShapes ? R.string.disable_shapes_save : R.string.enable_shapes_save));

		mItemShowFPS = menu.add((showFPS ? R.string.hide_fps : R.string.show_fps));

		mResolutionMenu = menu.addSubMenu(R.string.resolution);
		mResolutionList = mOpenCvCameraView.getResolutionList();
		mResolutionMenuItems = new MenuItem[mResolutionList.size()];

		ListIterator<Size> resolutionItr = mResolutionList.listIterator();
		int idx = 0;
		while (resolutionItr.hasNext()) {
			Size element = resolutionItr.next();
			mResolutionMenuItems[idx] = mResolutionMenu.add(1, idx, Menu.NONE, Integer.valueOf(element.width)
					.toString() + "x" + Integer.valueOf(element.height).toString());
			idx++;
		}

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.i(TAG, "called onCreateOptionsMenu");

		return true;
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}

	@Override
	public void onResume() {
		super.onResume();
		// OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_5, this,
		// mLoaderCallback);
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);

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
		}
	}

	public void onDestroy() {
		super.onDestroy();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();

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
		Log.i(TAG, "called onCameraViewStarted");
		
		if(mOpenCvCameraView == null) {
			Log.e(TAG, "mOpenCvCameraView is NULL");
			return;
		}
		
		mResolutionList = mOpenCvCameraView.getResolutionList();

		if (mResolutionList != null && mResolutionList.size() > 0 && !sharedpreferences.contains(resolutionWidthPref)) {
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
		String caption = Integer.valueOf(resolution.width).toString() + "x"
				+ Integer.valueOf(resolution.height).toString();
		Toast.makeText(this, caption, Toast.LENGTH_LONG).show();

		Log.i(TAG, "called onOptionsItemSelected: " + caption);
	}

	private void applyPreferences() {
		if (sharedpreferences.contains(saveShapesPref)) {
			saveShapes = sharedpreferences.getBoolean(saveShapesPref, true);
		}
		if (sharedpreferences.contains(showFPSPref)) {
			showFPS = sharedpreferences.getBoolean(showFPSPref, true);
		}
		if (sharedpreferences.contains(resolutionWidthPref)) {
			int resolutionWidth = sharedpreferences.getInt(resolutionWidthPref, 640);

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
		}
		if (sharedpreferences.contains(zoomPref)) {
			mZoom = sharedpreferences.getInt(zoomPref, 2);
			mOpenCvCameraView.setZoom(mZoom);
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
			// e.printStackTrace();
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

				Log.i(TAG, "Found new Sign: " + detected_sign.getSignId());
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

					// TODO: Fix problems with transparencies (maybe by using
					// bitmaps ???)
					Imgproc.cvtColor(image, imageDst, Imgproc.COLOR_BGRA2RGBA);
					imageDst.copyTo(mRgba.colRange(x, x + imgWidth).rowRange(y, y + imgWidth));
					image.release();
					imageDst.release();
				} catch (Exception e) {
					Log.i(TAG, "Exception: " + e.getMessage());
				}
			}
		}

		return mRgba;
	}

	private Mat resource2mat(Uri filepath) {
		Mat mat = new Mat();

		try {
			File resource = new File(new URI(filepath.getPath()));
			Bitmap bmp = BitmapFactory.decodeFile(resource.getAbsolutePath());
			Utils.bitmapToMat(bmp, mat);
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return mat;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		Log.i(TAG, "called onOptionsItemSelected: selected item: " + item);

		// Open preferences for edition
		Editor editor = sharedpreferences.edit();

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
		} else if (item == mItemNoZoom) {
			mOpenCvCameraView.setZoom(1);
			editor.putInt(zoomPref, 1);
		} else if (item == mItemZoomMinus) {
			if (mZoom - 1 >= 0)
				mZoom--;

			mOpenCvCameraView.setZoom(mZoom);
			editor.putInt(zoomPref, mZoom);
		} else if (item == mItemZoomPlus) {
			if (mZoom + 1 < 10)
				mZoom++;
			mOpenCvCameraView.setZoom(mZoom);
			editor.putInt(zoomPref, mZoom);
			// XXX: Remove on final version
		} else if (item == mItemSaveShapes) {
			saveShapes = !saveShapes;
			editor.putBoolean(saveShapesPref, saveShapes);
		} else if (item == mItemShowFPS) {
			showFPS = !showFPS;
			editor.putBoolean(showFPSPref, showFPS);
		} else {
			if (item.getGroupId() == 1) {
				int id = item.getItemId();

				Size resolution = mResolutionList.get(id);
				mOpenCvCameraView.setResolution(resolution);
				resolution = mOpenCvCameraView.getResolution();

				editor.putInt(resolutionWidthPref, resolution.width);

				String caption = Integer.valueOf(resolution.width).toString() + "x"
						+ Integer.valueOf(resolution.height).toString();
				Toast.makeText(this, caption, Toast.LENGTH_LONG).show();
			}
		}

		// Save preferences
		editor.commit();

		return true;
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

	// OBD related stuff
	private SharedPreferences prefs;
	public Map<String, String> commandResult = new HashMap<String, String>();

	private void updateSpeed(final ObdCommandJob job) {
		// cmdID.equals(AvailableCommandNames.SPEED.toString());
		SpeedObdCommand command = (SpeedObdCommand) job.getCommand();
		currentSpeed = command.getMetricSpeed();
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
		updateSpeed(job);
	}

	private final Runnable mQueueCommands = new Runnable() {
		public void run() {
			if (service != null && service.isRunning() && service.queueEmpty()) {
				queueCommands();
				commandResult.clear();
			}
			// run again in period defined in preferences
			new Handler().postDelayed(mQueueCommands, ConfigActivity.getObdUpdatePeriod(prefs));
		}
	};

	private boolean isServiceBound = false;
	private AbstractGatewayService service;
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
		protected Object clone() throws CloneNotSupportedException {
			return super.clone();
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			Log.d(TAG, className.toString() + " service is unbound");
			isServiceBound = false;
		}
	};

	/**
	   *
	   */
	private void queueCommands() {
		if (isServiceBound) {
			service.queueJob(new ObdCommandJob(new SpeedObdCommand()));
		}
	}

	private void doBindService() {
		if (!isServiceBound) {
			Log.d(TAG, "Binding OBD service..");
			if (preRequisites) {
				Intent serviceIntent = new Intent(this, ObdGatewayService.class);
				bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
			} else {
				Intent serviceIntent = new Intent(this, MockObdGatewayService.class);
				bindService(serviceIntent, serviceConn, Context.BIND_AUTO_CREATE);
			}
		}
	}

	private void doUnbindService() {
		if (isServiceBound) {
			if (service.isRunning()) {
				service.stopService();
			}
			Log.d(TAG, "Unbinding OBD service..");
			unbindService(serviceConn);
			isServiceBound = false;
		}
	}
}
