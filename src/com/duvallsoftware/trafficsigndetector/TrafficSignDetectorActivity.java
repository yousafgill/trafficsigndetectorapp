package com.duvallsoftware.trafficsigndetector;

import java.io.File;
import java.util.List;
import java.util.ListIterator;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Camera.Size;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

public class TrafficSignDetectorActivity extends Activity implements CvCameraViewListener2 {
    private static final String    TAG = "TrafficSignsDetector::Activity";
    
    private static final int       VIEW_HLS_CONVERSION		= 0;
    private static final int       VIEW_COLOR_EXTRACTION	= 1;
    private static final int       VIEW_CANNY_CONVERSION	= 2;
    private static final int       VIEW_EROSION_DILATION	= 3;
    private static final int       VIEW_DETECT_SHAPES		= 4;
    private static final int       VIEW_SIGNS_RECOGNIZE		= 5;

    private int                    mViewMode;
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
    private MenuItem			   mItemTestFann;
    private MenuItem			   mItemNoZoom;
    private MenuItem			   mItemZoom2;
    private MenuItem			   mItemZoom4;
    private MenuItem			   mItemSaveShapes;
    private MenuItem			   mItemShowFPS;

    private SubMenu mResolutionMenu;
    private MenuItem[] mResolutionMenuItems;
    
    private List<Size> mResolutionList;
    
    private SharedPreferences sharedpreferences;
    private static final String zoomPref = "zoomKey";
    private static final String saveShapesPref = "saveShapesKey";
    private static final String showFPSPref = "showFPSKey";
    
    
    private CameraView   mOpenCvCameraView;

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
        
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);        
        path = new File(path, "trafficsignsdetected");
        if(!path.exists()) {
        	path.mkdirs();
        	// initiate media scan and put the new things into the path array to
            // make the scanner aware of the location and the files you want to see
            MediaScannerConnection.scanFile(this, new String[] {path.toString()}, null, null);
        }
        
        sharedpreferences = getSharedPreferences("TrafficSignDetectionPrefs", Context.MODE_PRIVATE);        
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
        mItemTestFann = menu.add(R.string.fann_test);
        
        mItemNoZoom = menu.add(R.string.no_zoom);
        mItemZoom2 = menu.add(R.string.zoom_2);
        mItemZoom4 = menu.add(R.string.zoom_4);
        
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
        //OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_5, this, mLoaderCallback);
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
    	Log.i(TAG, "called onCameraViewStarted");
    	mResolutionList = mOpenCvCameraView.getResolutionList();
    	
    	if( mResolutionList != null && mResolutionList.size() > 0) {
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
    	
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mIntermediateMat = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
        
        // Only apply after camera was initialized because of zoom setting
        applyPreferences();
    }
    
    private void applyPreferences() {
    	if (sharedpreferences.contains(zoomPref))
        {
           mOpenCvCameraView.setZoom(sharedpreferences.getInt(zoomPref, 2));
        }
    	if (sharedpreferences.contains(saveShapesPref))
        {
           saveShapes = sharedpreferences.getBoolean(saveShapesPref, true);
        }
    	if (sharedpreferences.contains(showFPSPref))
        {
           showFPS = sharedpreferences.getBoolean(showFPSPref, true);
        }
    }

    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
        mIntermediateMat.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {    	
    	mRgba = inputFrame.rgba();
        DetectTrafficSigns(mRgba.getNativeObjAddr(), mViewMode, saveShapes, showFPS);
        return mRgba;
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
        } else if (item == mItemTestFann) {
            testFann();
        } else if (item == mItemNoZoom) {
            mOpenCvCameraView.setZoom(1);            
            editor.putInt(zoomPref, 1);
        } else if (item == mItemZoom2) {
            mOpenCvCameraView.setZoom(2);
            editor.putInt(zoomPref, 2);
        } else if (item == mItemZoom4) {
        	mOpenCvCameraView.setZoom(4);
        	editor.putInt(zoomPref, 4);
        } else if (item == mItemSaveShapes) {
        	saveShapes = !saveShapes;
        	editor.putBoolean(saveShapesPref, saveShapes);
        } else if (item == mItemShowFPS) {
        	showFPS = !showFPS;
        	editor.putBoolean(showFPSPref, showFPS);
        } else {
        	Log.i(TAG, "called onOptionsItemSelected: selected item group id: " + item.getGroupId());
        	if( item.getGroupId() == 1 ) {        		
	        	int id = item.getItemId();
	        	Log.i(TAG, "called onOptionsItemSelected; selected item id: " + item.getGroupId());
	        	
	            Size resolution = mResolutionList.get(id);
	            mOpenCvCameraView.setResolution(resolution);
	            resolution = mOpenCvCameraView.getResolution();
	            String caption = Integer.valueOf(resolution.width).toString() + "x" + Integer.valueOf(resolution.height).toString();
	            Toast.makeText(this, caption, Toast.LENGTH_LONG).show();
	            
	            Log.i(TAG, "called onOptionsItemSelected: " + caption);
        	}
        }
        
        // Save preferences
        editor.commit();
        
        return true;
    }

    private static native void DetectTrafficSigns(long matAddrRgba, int viewMode, boolean saveShapes, boolean showFPS);
    private static native void testFann();
}
