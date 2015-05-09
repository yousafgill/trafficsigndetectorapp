package com.duvallsoftware.trafficsigndetector;

import java.util.List;

import org.opencv.android.JavaCameraView;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.Log;

public class CameraView extends JavaCameraView {

    private static final String TAG = "TrafficSignsDetector::CameraView";

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public List<Size> getResolutionList() {
        return mCamera.getParameters().getSupportedPreviewSizes();
    }

    public void setResolution(Size resolution) {
        disconnectCamera();
        mMaxHeight = resolution.height;
        mMaxWidth = resolution.width;
        
        connectCamera(getWidth(), getHeight());
    }

    public Size getResolution() {
        return mCamera.getParameters().getPreviewSize();
    }
    
	public void setZoom(int zoom) {
		Camera.Parameters parameters = mCamera.getParameters();		
		if (parameters.isZoomSupported()) {
			int maxZoom = parameters.getMaxZoom();
			if (zoom >= 0 && zoom < maxZoom) {
				parameters.setZoom(zoom);
				mCamera.setParameters(parameters);
			}
		}
		//super.setZoomValue(zoom);
	}
}
