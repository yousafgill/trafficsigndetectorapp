package com.duvallsoftware.trafficsigndetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
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

import android.app.Activity;
import android.content.Context;
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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import com.duvallsoftware.trafficsigndetector.R.raw;

public class TrafficSignDetectorActivity extends Activity implements CvCameraViewListener2 {
    private static final String    TAG = "TrafficSignsDetector::Activity";
    
    private static final int       NO_SIGN					= 0;
    private static final int       WARNING_SIGN				= 1;
    private static final int       FORBIDDEN_SIGN			= 2;
    private static final int       OBLIGATORY_SIGN			= 3;
    private static final int       INFORMATION_SIGN			= 4;
    
    private static final int       VIEW_HLS_CONVERSION		= 0;
    private static final int       VIEW_COLOR_EXTRACTION	= 1;
    private static final int       VIEW_CANNY_CONVERSION	= 2;
    private static final int       VIEW_EROSION_DILATION	= 3;
    private static final int       VIEW_DETECT_SHAPES		= 4;
    private static final int       VIEW_SIGNS_RECOGNIZE		= 5;

    private int                    mViewMode;
    private int					   mZoom;
    private Mat                    mRgba;
    private Mat                    mIntermediateMat;
    private Mat                    mGray;
    
    private boolean				   saveShapes;
    private boolean				   showFPS;
    
    private MenuItem               mItemHLSConversion;
    private MenuItem               mItemColorExtraction;
    private MenuItem               mItemCannyConversion;
    private MenuItem               mItemErosionDilation;
    private MenuItem               mItemDetectShapes;
    private MenuItem               mItemSignsRecognize;
    private MenuItem               mItemPreviewResolution;
    private MenuItem			   mItemNoZoom;
    private MenuItem			   mItemZoomMinus;
    private MenuItem			   mItemZoomPlus;
    // XXX: Remove on final version
    private MenuItem			   mItemSaveShapes;
    private MenuItem			   mItemShowFPS;

    private SubMenu mResolutionMenu;
    private MenuItem[] mResolutionMenuItems;
    
    private List<Size> mResolutionList;
    
    private SharedPreferences sharedpreferences;
    private static final String zoomPref = "zoomKey";
    private static final String saveShapesPref = "saveShapesKey";
    private static final String showFPSPref = "showFPSKey";
    private static final String resolutionWidthPref = "resolutionWidthPref";   
    
    private CameraView   mOpenCvCameraView;

    private AssetManager mgr;
    private boolean forceCopy = false;
    private String assetsDataPath = null;
    
    // 3 Seconds
    private static final int SHOW_SIGN_DURATION = 3000;
    
    private HashMap<String, DetectedSign> detectedSigns = new HashMap<String, DetectedSign>();
    
    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("gnustl_shared");
                    System.loadLibrary("sign_detector");

                    mOpenCvCameraView.enableView();                    
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
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
        if(!path.exists()) {
        	path.mkdirs();
        	// initiate media scan and put the new things into the path array to
            // make the scanner aware of the location and the files you want to see
            MediaScannerConnection.scanFile(this, new String[] {path.toString()}, null, null);
        }
                
        sharedpreferences = getSharedPreferences("TrafficSignDetectionPrefs", Context.MODE_PRIVATE);
        assetsDataPath = getApplicationContext().getFilesDir().getPath();
        
        String obligatoryANN = "obligatory_traffic_signs.net";
        String obligatoryDataANN = assetsDataPath + File.separator + obligatoryANN;
        
        String informationANN = "information_traffic_signs.net";
        String informationDataANN = assetsDataPath + File.separator + informationANN;
        
        String forbiddenANN = "forbidden_traffic_signs.net";
        String forbiddenDataANN = assetsDataPath + File.separator + forbiddenANN;
        
        mgr = getApplicationContext().getAssets();
        
        copyAsset(obligatoryANN, obligatoryDataANN);
        copyAsset(informationANN, informationDataANN);
        copyAsset(forbiddenANN, forbiddenDataANN);
    }

    private void copyAsset(String srcAsset, String dst) {
//		File f = new File(dst);		        
//    	if(!f.exists()) {
	    	if( mgr == null ) {
	    		mgr = getApplicationContext().getAssets();
	    	}
	    	try {
		        InputStream is = mgr.open(srcAsset);
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
//      }
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
        mItemSaveShapes = menu.add( (saveShapes ? R.string.disable_shapes_save : R.string.enable_shapes_save) );
        
        mItemShowFPS = menu.add( (showFPS ? R.string.hide_fps : R.string.show_fps) );
        
        mResolutionMenu = menu.addSubMenu(R.string.resolution);
        mResolutionList = mOpenCvCameraView.getResolutionList();
        mResolutionMenuItems = new MenuItem[mResolutionList.size()];

        ListIterator<Size> resolutionItr = mResolutionList.listIterator();
        int idx = 0;
		while (resolutionItr.hasNext()) {
			Size element = resolutionItr.next();
			mResolutionMenuItems[idx] = mResolutionMenu.add(1, idx, Menu.NONE,
					Integer.valueOf(element.width).toString() + "x"
							+ Integer.valueOf(element.height).toString());
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
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
//        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_5, this, mLoaderCallback);
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        
        // Free JNI resources
        destroyANNs();
    }

    public void onCameraViewStarted(int width, int height) {
    	Log.i(TAG, "called onCameraViewStarted");
    	mResolutionList = mOpenCvCameraView.getResolutionList();
    	
    	if( mResolutionList != null && mResolutionList.size() > 0 && !sharedpreferences.contains(resolutionWidthPref)) {
    		setDefaultResolution();
    	}
    	
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mIntermediateMat = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
        
        // Only apply after camera was initialized because of zoom setting
        applyPreferences();
        
        //runFannDetector(assetsDataPath);
    }
    
    private void setDefaultResolution() {
    	Size resolution = mResolutionList.get(mResolutionList.size() - 1);
    	
    	for(int i=0;i<mResolutionList.size();i++) {
    		// Resolution preferences: 480x360; 640x480; 3..x2..
    		if( mResolutionList.get(i).width == 480 ) {
    			resolution = mResolutionList.get(i);
    			break;
    		}
    		else if( mResolutionList.get(i).width == 640 ) {
    			resolution = mResolutionList.get(i);
    			break;
    		}
    		else if( mResolutionList.get(i).width > 300 &&  mResolutionList.get(i).width < 400 ) {
    			resolution = mResolutionList.get(i);
    			break;
    		}
    	}
    	
        mOpenCvCameraView.setResolution(resolution);
        resolution = mOpenCvCameraView.getResolution();
        String caption = Integer.valueOf(resolution.width).toString() + "x" + Integer.valueOf(resolution.height).toString();
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
			int resolutionWidth = sharedpreferences.getInt(resolutionWidthPref,
					640);

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

					String caption = Integer.valueOf(resolution.width)
							.toString()
							+ "x"
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
            //e.printStackTrace();
            return -1;
        } 
    }
    
    private void expireDetectedSigns() {
    	long currentMillis = System.currentTimeMillis();
    	for(Iterator<Map.Entry<String, DetectedSign>> it = detectedSigns.entrySet().iterator(); it.hasNext(); ) {
    		Map.Entry<String, DetectedSign> entry = it.next();
    		if( currentMillis - ((DetectedSign)entry.getValue()).getDetectedTimestamp() > SHOW_SIGN_DURATION ) {
    			it.remove();
    		}
	    }
    }
    
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {    	
    	mRgba = inputFrame.rgba();
    	
    	expireDetectedSigns();
    	
        String detected_signs[] = detectTrafficSigns(assetsDataPath, mRgba.getNativeObjAddr(), mViewMode, saveShapes, showFPS);
        // Add new signs to hasmap
        if( detected_signs != null ) {
	        for(int i=0;i<detected_signs.length;i++) {
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
        for(DetectedSign detected_sign : detectedSigns.values()) {
	        if(y_pos_idx++ == 3) {
	        	y_pos_idx = 0;
	        }
	        
	        int imageSrcID = getResId(detected_sign.getSignId().toLowerCase(), raw.class);	        
	        if( imageSrcID >= 0 ) {
				try {
	        		int y = mRgba.rows() - imgWidth - padding - (i++ * (imgWidth + padding));
	        		
		        	Mat image = Utils.loadResource(getApplicationContext(), imageSrcID, -1);
		        	Mat imageDst = new Mat();
		        	
		        	// TODO: Fix problems with transparencies (maybe by using bitmaps ???)
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
        	if( mZoom -1 >= 0)
        		mZoom--;
        	
            mOpenCvCameraView.setZoom(mZoom );
            editor.putInt(zoomPref, mZoom);
        } else if (item == mItemZoomPlus) {
        	if( mZoom + 1 < 10)
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
        	if( item.getGroupId() == 1 ) {        		
	        	int id = item.getItemId();
	        	
	            Size resolution = mResolutionList.get(id);
	            mOpenCvCameraView.setResolution(resolution);
	            resolution = mOpenCvCameraView.getResolution();
	            
	            editor.putInt(resolutionWidthPref, resolution.width);
	            
	            String caption = Integer.valueOf(resolution.width).toString() + "x" + Integer.valueOf(resolution.height).toString();
	            Toast.makeText(this, caption, Toast.LENGTH_LONG).show();
        	}
        }
        
        // Save preferences
        editor.commit();
        
        return true;
    }

    private static native String[] detectTrafficSigns(String assetsDataPath, long matAddrRgba, int viewMode, boolean saveShapes, boolean showFPS);
//    private static native void runFannDetector(String assetsDataPath);
    private static native void destroyANNs();
    
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
		 * @param detectedTimestamp the detectedTimestamp to set
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
		 * @param signId the signId to set
		 */
		public void setSignId(String signId) {
			this.signId = signId;
		}
    }
}
