/**
 * Traffic Signs Detector
 */
#include <jni.h>
#include <opencv2/opencv.hpp>
#include <sstream>
#include <android/log.h>
#include <chrono>
#include <limits.h>
#include <sys/timeb.h>
#include <fstream>
#include <iostream>

#define DEBUG 1

#define  LOG_TAG "SIGN DETECTOR JNI"
#ifdef DEBUG
#define  LOG(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#else
#define  LOG(...)
#endif

using namespace std;
using namespace cv;
using namespace std::chrono;

#define VIEW_HLS_CONVERSION 0
#define VIEW_COLOR_EXTRACTION 1
#define VIEW_CANNY_CONVERSION 2
#define VIEW_EROSION_DILATION 3
#define VIEW_DETECT_SHAPES 4
#define VIEW_SIGNS_RECOGNIZE 5

#define NO_SIGN 0
#define WARNING_SIGN 1
#define FORBIDDEN_SIGN 2
#define OBLIGATORY_SIGN 3
#define INFORMATION_SIGN 4
#define END_FORBIDDEN_SIGN 5 //not used

#define SIGNAL_DETECTION_MESSAGE_TIMEOUT 3000

#define REFERENCE_WIDTH 480

const int NUM_RANGES = 7;
cv::Mat colorRangesMat[NUM_RANGES];

cv::Scalar colorRanges[NUM_RANGES][2] = {
	{ cv::Scalar(0, 40, 50), cv::Scalar(10, 195, 255) }, //RED
	{ cv::Scalar(170, 30, 50), cv::Scalar(180, 195, 255) }, //RED
	{ cv::Scalar(110, 24, 24), cv::Scalar(153, 62, 85) }, //RED

	{ cv::Scalar(100, 63, 166), cv::Scalar(132, 103, 255) }, //BLUE
	{ cv::Scalar(100, 105, 105), cv::Scalar(132, 170, 180) }, //BLUE
	{ cv::Scalar(100, 105, 106), cv::Scalar(132, 170, 255) }, //BLUE
	{ cv::Scalar(100, 30, 204), cv::Scalar(132, 170, 255) } //BLUE
};

int savedWidth = 0;
float scaleFactor = -1.0f;
float inverseScaleFactor = 1.0f;

const int borderWidth = 2;

const Scalar circleColor(43, 245, 96);
const Scalar rectangleColor(204, 0, 102);
const Scalar triangleColor(255, 128, 0);

// current detected sign type
int detected_sign_type = NO_SIGN;

// frame counter
long frameCounter = 0;

long time_since_last_detection;
    
// Erode & Dilate to isolate segments connected to nearby areas
cv::Mat element = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(1, 1), cv::Point(0,0));

float CLOCK()
{
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC,  &t);
    return (t.tv_sec * 1000)+(t.tv_nsec*1e-6);
}

float _avgdur=0;
float _fpsstart=0;
float _avgfps=0;
float _fps1sec=0;

float avgdur(float newdur)
{
    _avgdur=0.98*_avgdur+0.02*newdur;
    return _avgdur;
}

float avgfps()
{
    if(CLOCK()-_fpsstart>1000)      
    {
        _fpsstart=CLOCK();
        _avgfps=0.7*_avgfps+0.3*_fps1sec;
        _fps1sec=0;
    }
    _fps1sec++;
    return _avgfps;
}

/**
 * Helper function to find a cosine of angle between vectors
 * from pt0->pt1 and pt0->pt2
 */
static float angle(cv::Point pt1, cv::Point pt2, cv::Point pt0)
{
	float dx1 = pt1.x - pt0.x;
	float dy1 = pt1.y - pt0.y;
	float dx2 = pt2.x - pt0.x;
	float dy2 = pt2.y - pt0.y;
	return (dx1*dx2 + dy1*dy2)/sqrt((dx1*dx1 + dy1*dy1)*(dx2*dx2 + dy2*dy2) + 1e-10);
}

void setDetectedSignType(int sign_type) {
	// Get current time in milliseconds
	milliseconds ms = duration_cast< milliseconds >(
    	high_resolution_clock::now().time_since_epoch()
	);
	
	time_since_last_detection = ms.count();

	detected_sign_type = sign_type;
}

void get64x64ImagefromRectangle(cv::Mat *previewFrame, cv::Mat *dst, cv::Rect rect) {
	cv::Mat& frame = *(Mat*)previewFrame;
	
	try {
		(*(Mat*)dst) =  frame(rect);
	
		// Increase the rectangle size by X pixels to capture all sign
		const int incPixels = 12;
		
		if ( (rect.x - incPixels / 2 + rect.width + incPixels) <= frame.cols &&
				(rect.y - incPixels / 2 + rect.height + incPixels) <= frame.rows &&
					(rect.x - (incPixels/2)) >= 0 && (rect.y - (incPixels/2)) >= 0 ) {
			rect.height += incPixels;
			rect.width += incPixels;
			rect.x -= (incPixels/2);
			rect.y -= (incPixels/2);
		}
			
		// Crop frame from rectangle parameters
		cv::Mat miniMat = frame(rect);

		// Create 64x64 pixel empty Image		
		cv::Mat mIntermediateMat(64, 64, CV_8UC3, Scalar(0,0,0));
		
		// resize frame to 64x64 pixels
		resize(miniMat, mIntermediateMat, mIntermediateMat.size(), 0, 0, INTER_CUBIC);
		(*(Mat*)dst) =  mIntermediateMat;
	} catch(std::exception& ex) {
		LOG("Exception resizing detected image: %s\n", ex.what());
	}
}

void writeShapeFound(cv::Mat* image) {
	std::ostringstream shapeFileName;

	vector<int> compression_params;
	compression_params.push_back(CV_IMWRITE_JPEG_QUALITY);
	compression_params.push_back(95);

	try {
		// Get frame processing time
		milliseconds ms = duration_cast< milliseconds >(
		    high_resolution_clock::now().time_since_epoch()
		);
		
		// Detected image name
		shapeFileName.clear();
		shapeFileName.str("");
		shapeFileName << "/mnt/sdcard/Downloads/trafficsignsdetected/sign_" << ms.count() << ".jpeg";
		
		LOG("Saving image %s\n", shapeFileName.str().c_str());
		
		bool res = cv::imwrite(shapeFileName.str(), *(Mat*)image, compression_params);

		if( !res ) {
			LOG("Failed to write image %s to SDCard\n", shapeFileName.str().c_str());
		}
		else {
			LOG("Successfuly wrote image %s to SDCard\n", shapeFileName.str().c_str());
		}
	}
	catch (std::exception& ex) {
		LOG("Exception saving image %s to SDCard: %s\n", shapeFileName.str().c_str(), ex.what());
	}
}

void writeShapeFound(cv::Mat* frame, cv::Rect rect) {	
	cv::Mat image;
	get64x64ImagefromRectangle(frame, &image, rect);
		
	// Convert to original BGR color
	cv::cvtColor(image , image , CV_RGBA2BGR);
	    
	writeShapeFound(&image);
}

// Blue obligatory traffic signs have a great percentage of blue color
// so it's easier to test them instead of the red forbidden traffic signs
bool imageIsBlueColored(cv::Mat * origImage) {	
	cv::Mat& image = *(Mat*)origImage;
	
	const int colorBlueRangesStartIndex = 3;
	const int colorBlueRangesCount = 4;
	
	cv::Mat resultMat;
	
	// Convert the image from RGBA into an HLS image
	cv::cvtColor(image , image , CV_RGBA2RGB);
	cv::cvtColor(image, image, CV_RGB2HLS);		
	
	cv::Mat tempMat[colorBlueRangesCount];
	
	for (int idx = colorBlueRangesStartIndex, i = 0; idx < NUM_RANGES; idx++, i++) {
		cv::inRange(image, colorRanges[idx][0], colorRanges[idx][1], tempMat[i]);
		if (i == 0) {
			resultMat = tempMat[0];
		}
		else {
			resultMat |= tempMat[i];
		}
	}
	
	float image_size = (image.cols * image.rows);
	float blue_percent = ((float) cv::countNonZero(resultMat))/image_size;
	
	LOG("IMAGE BLUE PERCENTAGE: %f", blue_percent);
	
	return (blue_percent > 0.15);
}

void findShapes(cv::Mat previewFrame, cv::Mat frame, cv::Mat canny, bool saveShapes) {
	// Find contours
	std::vector<std::vector<cv::Point> > contours;
	std::vector<Vec4i> hierarchy;
	std::vector<cv::Point> approx;

	cv::findContours(canny.clone(), contours, hierarchy, CV_RETR_TREE, CV_CHAIN_APPROX_SIMPLE, Point(0, 0));

	/// Approximate contours to polygons + get bounding rects and circles
	std::vector<cv::Rect> boundRect(contours.size());
	std::vector<cv::Point2f>center(contours.size());
	std::vector<float>contourRadius(contours.size());	

	for (int i = 0; i < contours.size(); i++)
	{
		// XXX: TEST THIS - Ignore countours which are child of other contours
		if (hierarchy[i][3] != -1) {
			continue;
		}		
		
		// Approximate contour with accuracy proportional to the contour perimeter
		cv::approxPolyDP(cv::Mat(contours[i]), approx, cv::arcLength(cv::Mat(contours[i]), true)*0.035, true);

		// Skip small or non-convex objects
		if (std::fabs(cv::contourArea(contours[i])) < 200 || !cv::isContourConvex(approx))
			continue;

		boundRect[i] = cv::boundingRect(Mat(approx));
		// Ignore big shapes and disfrom shapes (relation between width and height should not be more than 2 : 1 either way)
		if (boundRect[i].width > 60 || boundRect[i].height > 60 || (boundRect[i].width > (2 * boundRect[i].height)) || ((2 * boundRect[i].width) < boundRect[i].height))
			continue;
		
		if (approx.size() >= 3 && approx.size() <= 6) {
			// Adjust the bounding rectangle dimensions and position with the scale factor value
			boundRect[i].height = (boundRect[i].height * inverseScaleFactor);
			boundRect[i].width = (boundRect[i].width * inverseScaleFactor);
			boundRect[i].x = (boundRect[i].x * inverseScaleFactor);
			boundRect[i].y = (boundRect[i].y * inverseScaleFactor);
		}
		
		// Triangle
		if (approx.size() == 3)
		{
			setDetectedSignType(WARNING_SIGN);
			rectangle(previewFrame, boundRect[i].tl(), boundRect[i].br(), triangleColor, 2, 8, 0);
			if(saveShapes) {
				writeShapeFound(&frame, boundRect[i]);
			}
		}
		// Rectangle
		else if (approx.size() >= 4 && approx.size() <= 6)
		{
			// Number of vertices of polygonal curve
			size_t vtc = approx.size();

			// Get the cosines of all corners
			std::vector<float> cos;
			for (int j = 2; j < vtc + 1; j++) {
				cos.push_back(angle(approx[j%vtc], approx[j - 2], approx[j - 1]));
			}

			// Sort ascending the cosine values
			std::sort(cos.begin(), cos.end());

			// Get the lowest and the highest cosine
			float mincos = cos.front();
			float maxcos = cos.back();

			// Use the degrees obtained above and the number of vertices
			// to determine the shape of the contour
			if (vtc == 4 && mincos >= -0.1 && maxcos <= 0.3) {
				setDetectedSignType(INFORMATION_SIGN);
				rectangle(previewFrame, boundRect[i].tl(), boundRect[i].br(), rectangleColor, 3, 8, 0);
				if(saveShapes) {
					writeShapeFound(&frame, boundRect[i]);
				}
			}
		}
		else
		{
			cv::minEnclosingCircle(approx, center[i], contourRadius[i]);
			
			// Adjust the center of the countour shape with the scale factor value
			center[i].x = (center[i].x * inverseScaleFactor);
			center[i].y = (center[i].y * inverseScaleFactor);		

			// Detect and label circles
			cv::Rect r = cv::boundingRect(contours[i]);
			
			cv::Mat mCircle;
			get64x64ImagefromRectangle(&frame, &mCircle, r);
			
			// Adjust the bounding rectangle dimensions and position with the scale factor value
			r.height = (r.height * inverseScaleFactor);
			r.width = (r.width * inverseScaleFactor);
			r.x = (r.x * inverseScaleFactor);
			r.y = (r.y * inverseScaleFactor);
			
			int radius = r.width / 2;			
			
			if( imageIsBlueColored(&mCircle)) {
				setDetectedSignType(OBLIGATORY_SIGN);
			} else {
				setDetectedSignType(FORBIDDEN_SIGN);
			}
			
			if (std::abs(1 - ((float)r.width / r.height)) <= 0.2 &&
				std::abs(1 - (cv::contourArea(contours[i]) / (CV_PI * std::pow(radius, 2)))) <= 0.2) {
				circle(previewFrame, center[i], (int)(contourRadius[i] * inverseScaleFactor), circleColor, 4, 8, 0);
			}
			else {
				circle(previewFrame, center[i], (int)(contourRadius[i] * inverseScaleFactor), circleColor, 4, 8, 0);
			}
			
			if(saveShapes) {							    
				// Save shapes on SDCard for later analysis
				writeShapeFound(&frame, r);				
			}
		}
	}
}

void setInverseScaleFactor(int width) {
	inverseScaleFactor = (width > REFERENCE_WIDTH ? (width * 1.0f)/REFERENCE_WIDTH : 1.0);
}

bool isResolutionChanged(int currentWidth) {
	if( currentWidth != savedWidth ) {
		LOG("Resolution has changed - New Widht: %d", currentWidth);
		savedWidth = currentWidth;
		return true;
	}
	
	return false;
}

extern "C" {
	JNIEXPORT void JNICALL Java_com_duvallsoftware_trafficsigndetector_TrafficSignDetectorActivity_DetectTrafficSigns
									(JNIEnv*, jobject, jlong addrRgba, jint option, jboolean saveShapes, jboolean showFPS);
	
	JNIEXPORT void JNICALL Java_com_duvallsoftware_trafficsigndetector_TrafficSignDetectorActivity_DetectTrafficSigns
									(JNIEnv*, jobject, jlong addrRgba, jint option, jboolean saveShapes, jboolean showFPS) {
		// Initialized fps clock
		float start = CLOCK();

        if( frameCounter == LONG_MAX - 1 )
        	frameCounter = 0;
        
		cv::Mat& previewFrame = *(Mat*)addrRgba;
		
		// Update scale factor if needed
		if( scaleFactor < 0 || isResolutionChanged(previewFrame.cols) ) { 
			// Base width is 480 pixels so the working frame should have this scale factor relation
			scaleFactor = (previewFrame.cols > REFERENCE_WIDTH ? REFERENCE_WIDTH / (previewFrame.cols * 1.0f) : 1.0);
			
			setInverseScaleFactor(previewFrame.cols);
			
			LOG("Scale Factor is set to: %f", scaleFactor);
			LOG("Inverse Scale Factor is set to: %f", inverseScaleFactor);
		}		
				
		cv::Mat frame, tempMat, resultMat;	
		
		// Apply the scale factor to the working frame
		cv::resize(previewFrame, frame, Size(0,0), scaleFactor, scaleFactor);			
		
		const int frameH = frame.size().height;
		const int frameW = frame.size().width;
		
		// Clear unmonitored area
		rectangle(frame, cv::Point(0, 0), cv::Point((frameW / 4), frameH), Scalar(0, 0, 0), -1, 8, 0);
		rectangle(frame, cv::Point(0, frameH - (frameH / 4)), cv::Point(frameW, frameH), Scalar(0, 0, 0), -1, 8, 0);
		
		// Display monitored (rectangle) area
		rectangle(previewFrame, cv::Point(previewFrame.cols / 4, 0), cv::Point(previewFrame.cols - borderWidth/2, previewFrame.rows - (previewFrame.rows / 4)), Scalar(127, 127, 127), borderWidth, 8, 0);		
	
		// Convert the image from RGBA into an HLS image
		cv::cvtColor(frame , tempMat , CV_RGBA2RGB);
		cv::cvtColor(tempMat, tempMat, CV_RGB2HLS);
		
		if( option == VIEW_HLS_CONVERSION ) {
			cv::resize(tempMat, previewFrame, Size(0,0), inverseScaleFactor, inverseScaleFactor);
			return;
		}
	
		for (int i = 0; i < NUM_RANGES; i++) {
			cv::inRange(tempMat, colorRanges[i][0], colorRanges[i][1], colorRangesMat[i]);
			if (i == 0) {
				resultMat = colorRangesMat[0];
			}
			else {
				resultMat |= colorRangesMat[i];
			}
		}
	
		if( option == VIEW_COLOR_EXTRACTION ) {
			cv::resize(resultMat, previewFrame, Size(0,0), inverseScaleFactor, inverseScaleFactor);
			return;
		}
		
		// Blur the gray image
		cv::blur(resultMat, tempMat, Size(3, 3), cv::Point(0, 0));
		cv::Canny(tempMat, resultMat, 0.3, 2, 3);
	
		if( option == VIEW_CANNY_CONVERSION ) {
			cv::resize(resultMat, previewFrame, Size(0,0), inverseScaleFactor, inverseScaleFactor);
			return;
		}
		
		// Open seems to be equivelent to erode and dilate but faster XXX: TB CONFIRMED
		// http://docs.opencv.org/doc/tutorials/imgproc/opening_closing_hats/opening_closing_hats.html#opening
		cv::morphologyEx(resultMat, resultMat, MORPH_OPEN, element);
	
		if( option == VIEW_EROSION_DILATION ) {
			cv::resize(resultMat, previewFrame, Size(0,0), inverseScaleFactor, inverseScaleFactor);
			return;
		}
		
		if( detected_sign_type != NO_SIGN ) {
			// Get current time in milliseconds
			milliseconds ms = duration_cast< milliseconds >(
		    	high_resolution_clock::now().time_since_epoch()
			);
			
			// X seconds after rhe last detected sign set the detection to NO_SIGN
			if( ((long)ms.count() - time_since_last_detection) > SIGNAL_DETECTION_MESSAGE_TIMEOUT ) {
				detected_sign_type = NO_SIGN;
			}
		}
		
		findShapes(previewFrame, frame, resultMat, saveShapes);		
		
		if( detected_sign_type != NO_SIGN ) {
			ostringstream detectedSign("");
			
			switch(detected_sign_type) {
				case WARNING_SIGN:
					detectedSign << "WARNING";
					break;
				case FORBIDDEN_SIGN:
					detectedSign << "FORBIDDEN";
					break;
				case OBLIGATORY_SIGN:
					detectedSign << "OBLIGATORY";
					break;
				case INFORMATION_SIGN:
					detectedSign << "INFORMATION";
					break;
				case END_FORBIDDEN_SIGN:
					detectedSign << "END FORBIDDEN";
					break;				
			}
			
			cv::putText(previewFrame, detectedSign.str().c_str(), cvPoint(5,35), FONT_HERSHEY_PLAIN, 1.25, CV_RGB(255,0,0), 1.25);
		}
        
        if( showFPS ) {
        	float dur = CLOCK()- start;        
	        float avgfpsF = avgfps();
	        ostringstream avgfps("");
	    	avgfps << "FPS: " << avgfpsF;
	        cv::putText(previewFrame, avgfps.str().c_str(), cvPoint(5,15), FONT_HERSHEY_PLAIN, 1.0, CV_RGB(0,0,255), 1.0);
        }
	}
}
