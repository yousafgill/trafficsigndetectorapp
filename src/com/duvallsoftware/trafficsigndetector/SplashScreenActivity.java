package com.duvallsoftware.trafficsigndetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import com.duvallsoftware.settings.TSRConfiguration;

import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.WindowManager;
import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
 
public class SplashScreenActivity extends Activity {	
	
	static AssetManager assetManager;
	private String assetsDataPath = null;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_splash_screen);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent=new Intent(getBaseContext(),TrafficSignDetectorActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
  
                // close this activity
                finish();
            }
        }, 500); // wait for 500 milliseconds
    }
    
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
				case LoaderCallbackInterface.SUCCESS: {
					Log.i(TrafficSignDetectorActivity.TAG, "OpenCV loaded successfully");
	
					// Load native library after(!) OpenCV initialization
					System.loadLibrary("gnustl_shared");
//					System.loadLibrary("lept");
//					System.loadLibrary("tess");					
					System.loadLibrary("sign_detector");
				}
				break;
				default: {
					super.onManagerConnected(status);
				}
				break;
			}
		}
	};
    
    @Override
	public void onResume() {
		super.onResume();
		// OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_5, this,
		// mLoaderCallback);
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
		
		File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		path = new File(path, "trafficsignsdetected");
		if (!path.exists()) {
			path.mkdirs();
			// initiate media scan and put the new things into the path array to
			// make the scanner aware of the location and the files you want to see
			MediaScannerConnection.scanFile(this, new String[] { path.toString() }, null, null);
		}
		
		assetManager = getApplicationContext().getAssets();
		assetsDataPath = getApplicationContext().getFilesDir().getPath();
		
		copyANNFilesToAssetPath();		
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
		for(int i=0;i<TSRConfiguration.NUMBER_WARNING_SIGNS;i++) {			
			StringBuilder annName = new StringBuilder();
			annName.append(TSRConfiguration.warning_sign_id_array[i]);
			annName.append(suffix);
			
			try {
				String annPath = assetsDataPath + File.separator + annName.toString();
				copyAsset(annName.toString(), annPath);
			} catch(Exception e) {
				Log.e(TrafficSignDetectorActivity.TAG, "Exception initializing " + annName.toString() + " ANN from file: " + e.getMessage());
			}
		}
		
		for(int i=0;i<TSRConfiguration.NUMBER_FORBIDDEN_SIGNS;i++) {
			StringBuilder annName = new StringBuilder();
			annName.append(TSRConfiguration.forbidden_sign_id_array[i]);
			annName.append(suffix);
			
			try {
				String annPath = assetsDataPath + File.separator + annName.toString();
				copyAsset(annName.toString(), annPath);
			} catch(Exception e) {
				Log.e(TrafficSignDetectorActivity.TAG, "Exception initializing " + annName.toString() + " ANN from file: " + e.getMessage());
			}
		}
		
		for(int i=0;i<TSRConfiguration.NUMBER_OBLIGATORY_SIGNS;i++) {
			StringBuilder annName = new StringBuilder();
			annName.append(TSRConfiguration.obligatory_sign_id_array[i]);
			annName.append(suffix);
			
			try {
				String annPath = assetsDataPath + File.separator + annName.toString();
				copyAsset(annName.toString(), annPath);
			} catch(Exception e) {
				Log.e(TrafficSignDetectorActivity.TAG, "Exception initializing " + annName.toString() + " ANN from file: " + e.getMessage());
			}
		}
		
		for(int i=0;i<TSRConfiguration.NUMBER_INFORMATION_SIGNS;i++) {
			StringBuilder annName = new StringBuilder();
			annName.append(TSRConfiguration.information_sign_id_array[i]);
			annName.append(suffix);
			
			try {
				String annPath = assetsDataPath + File.separator + annName.toString();
				copyAsset(annName.toString(), annPath);
			} catch(Exception e) {
				Log.e(TrafficSignDetectorActivity.TAG, "Exception initializing " + annName.toString() + " ANN from file: " + e.getMessage());
			}
		}
	}
    
    private void copyAsset(String srcAsset, String dst) {
		File f = new File(dst);
		if(!f.exists()) {
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
}
