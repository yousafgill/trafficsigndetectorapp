package com.duvallsoftware.trafficsigndetector;

import java.util.ArrayList;
import java.util.List;

import org.opencv.android.JavaCameraView;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.AttributeSet;

public class CameraView extends JavaCameraView {

    private static final String TAG = "TrafficSignsDetector::CameraView";

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public List<Size> getResolutionList() {
    	if(mCamera != null) {
    		return mCamera.getParameters().getSupportedPreviewSizes();
    	}
    	return new ArrayList<Size>();
    }

    public void setResolution(Size resolution) {
        disconnectCamera();
        mMaxHeight = resolution.height;
        mMaxWidth = resolution.width;
        
        connectCamera(getWidth(), getHeight());
    }

    public Size getResolution() {
    	if(mCamera != null) {
    		mCamera.getParameters().getPreviewSize();
    	}
        return null;
    }
    
    public Integer getExposureCompensation() {
    	if(mCamera != null) {
    		return mCamera.getParameters().getExposureCompensation();
    	}
    	return null;
    }
    
    public void setExposureCompensation(int value) {
    	if(mCamera != null) {    		
    		Camera.Parameters parameters = mCamera.getParameters();
    		if(value < parameters.getMinExposureCompensation()) {
    			value = parameters.getMinExposureCompensation();
    		}
    		if(value > parameters.getMaxExposureCompensation()) {
    			value = parameters.getMaxExposureCompensation();
    		}
    		parameters.setExposureCompensation(value);
			mCamera.setParameters(parameters);
    	}
    }
    
    public void setOptimizedParameters() {
    	if(mCamera != null) {
    			//setExposureCompensation(-12);
    		
	    		Camera.Parameters parameters = mCamera.getParameters();
	    		
	    		parameters.setFocusMode("FOCUS_MODE_CONTINUOUS_VIDEO");
	    		parameters.setVideoStabilization(true);
	    		parameters.setWhiteBalance("WHITE_BALANCE_AUTO");
	    		parameters.setAutoExposureLock(true);
	    		parameters.setAutoWhiteBalanceLock(true);
	    		
				mCamera.setParameters(parameters);
    	}
    }
    
    public void setExposureDefaultValue() {
    	setExposureCompensation(0);
    }
    
    public Integer getMaxExposureCompensation() {
    	if(mCamera != null) {
    		return mCamera.getParameters().getMaxExposureCompensation();
    	}
    	return null;
    }
    
    public Integer getMinExposureCompensation() {
    	if(mCamera != null) {
    		return mCamera.getParameters().getMinExposureCompensation();
    	}
    	return null;
    }
    
	public void setZoom(int zoom) {
		if (isZoomValueSupported(zoom)) {
			Camera.Parameters parameters = mCamera.getParameters();
				parameters.setZoom(zoom);
				mCamera.setParameters(parameters);
		}
	}
	
	public boolean isZoomValueSupported(int zoom) {
		Camera.Parameters parameters = mCamera.getParameters();		
		if (parameters.isZoomSupported()) {
			int maxZoom = parameters.getMaxZoom();
			if (zoom >= 0 && zoom <= parameters.getMaxZoom()) {
				return true;
			}
		}
		
		// 0 means no zoom and is always supported
		if(zoom == 0) return true;
		
		return false;
	}
}
