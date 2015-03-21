package com.duvallsoftware.trafficsigndetector;

import java.util.List;
import java.util.ListIterator;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Bundle;
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
    
    private MenuItem               mItemHLSConversion;
    private MenuItem               mItemColorExtraction;
    private MenuItem               mItemCannyConversion;
    private MenuItem               mItemErosionDilation;
    private MenuItem               mItemDetectShapes;
    private MenuItem               mItemSignsRecognize;
    private MenuItem               mItemPreviewResolution;

    private SubMenu mResolutionMenu;
    private MenuItem[] mResolutionMenuItems;
    
    private List<Size> mResolutionList;
    
    private CameraView   mOpenCvCameraView;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemHLSConversion = menu.add("HLS View");
        mItemColorExtraction = menu.add("Color Exraction");
        mItemCannyConversion = menu.add("Canny Conversion");
        mItemErosionDilation = menu.add("Erosion Dilation");
        mItemDetectShapes = menu.add("Detect Shapes");
        mItemSignsRecognize = menu.add("Signs Recognize");
        
        mResolutionMenu = menu.addSubMenu("Resolution");
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
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_9, this, mLoaderCallback);
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
	    		if( mResolutionList.get(i).width > 300 &&  mResolutionList.get(i).width < 400 ) {
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
    }

    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
        mIntermediateMat.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        DetectTrafficSigns(mRgba.getNativeObjAddr(), mViewMode);
        return mRgba;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected: selected item: " + item);

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

        return true;
    }

    public native void DetectTrafficSigns(long matAddrRgba, int viewMode);
}
