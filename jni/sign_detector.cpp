/**
 * Traffic Signs Detector
 */
#include <jni.h>
#include <opencv2/opencv.hpp>

using namespace cv;

#define VIEW_HLS_CONVERSION 0
#define VIEW_COLOR_EXTRACTION 1
#define VIEW_CANNY_CONVERSION 2
#define VIEW_EROSION_DILATION 3
#define VIEW_DETECT_SHAPES 4
#define VIEW_SIGNS_RECOGNIZE 5

const float scaleFactor = 3.0f;
Scalar circleColor(43, 215, 96);
Scalar rectangleColor(204, 0, 102);
Scalar triangleColor(255, 128, 0);

// Erode & Dilate to isolate segments connected to nearby areas
cv::Mat element = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(1, 1), cv::Point(0,0));

/**
 * Helper function to find a cosine of angle between vectors
 * from pt0->pt1 and pt0->pt2
 */
static double angle(cv::Point pt1, cv::Point pt2, cv::Point pt0)
{
	double dx1 = pt1.x - pt0.x;
	double dy1 = pt1.y - pt0.y;
	double dx2 = pt2.x - pt0.x;
	double dy2 = pt2.y - pt0.y;
	return (dx1*dx2 + dy1*dy2)/sqrt((dx1*dx1 + dy1*dy1)*(dx2*dx2 + dy2*dy2) + 1e-10);
}

void findShapes(cv::Mat frame, cv::Mat canny) {
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
		// Approximate contour with accuracy proportional to the contour perimeter
		cv::approxPolyDP(cv::Mat(contours[i]), approx, cv::arcLength(cv::Mat(contours[i]), true)*0.035, true);

		// Skip small or non-convex objects
		if (std::fabs(cv::contourArea(contours[i])) < 200 || !cv::isContourConvex(approx))
			continue;

		boundRect[i] = cv::boundingRect(Mat(approx));
		// Ignore big shapes and disfrom shapes (relation between width and height should not be more than 2 : 1 either way)
		if (boundRect[i].width > 60 || boundRect[i].height > 60 || (boundRect[i].width > (2 * boundRect[i].height)) || ((2 * boundRect[i].width) < boundRect[i].height))
			continue;

		if (approx.size() == 3)
		{
			rectangle(frame, boundRect[i].tl(), boundRect[i].br(), triangleColor, 2, 8, 0);
		}
		else if (approx.size() >= 4 && approx.size() <= 6)
		{
			// Number of vertices of polygonal curve
			size_t vtc = approx.size();

			// Get the cosines of all corners
			std::vector<double> cos;
			for (int j = 2; j < vtc + 1; j++)
				cos.push_back(angle(approx[j%vtc], approx[j - 2], approx[j - 1]));

			// Sort ascending the cosine values
			std::sort(cos.begin(), cos.end());

			// Get the lowest and the highest cosine
			double mincos = cos.front();
			double maxcos = cos.back();

			// Use the degrees obtained above and the number of vertices
			// to determine the shape of the contour
			if (vtc == 4 && mincos >= -0.1 && maxcos <= 0.3) {
				rectangle(frame, boundRect[i].tl(), boundRect[i].br(), rectangleColor, 3, 8, 0);
			}
		}
		else
		{
			cv::minEnclosingCircle(approx, center[i], contourRadius[i]);

			// Detect and label circles
			cv::Rect r = cv::boundingRect(contours[i]);
			int radius = r.width / 2;

			if (std::abs(1 - ((double)r.width / r.height)) <= 0.2 &&
				std::abs(1 - (cv::contourArea(contours[i]) / (CV_PI * std::pow(radius, 2)))) <= 0.2) {
				circle(frame, center[i], (int)contourRadius[i], circleColor, 4, 8, 0);
			}
			else {
				circle(frame, center[i], (int)contourRadius[i], circleColor, 4, 8, 0);
			}
		}
	}
}

extern "C" {
	JNIEXPORT void JNICALL Java_com_duvallsoftware_trafficsigndetector_TrafficSignDetectorActivity_DetectTrafficSigns(JNIEnv*, jobject, jlong addrRgba, jint option);
	
	JNIEXPORT void JNICALL Java_com_duvallsoftware_trafficsigndetector_TrafficSignDetectorActivity_DetectTrafficSigns(JNIEnv*, jobject, jlong addrRgba, jint option) {
		Mat& frame = *(Mat*)addrRgba;
		cv::Mat tempMat, resultMat;		
		
		const int NUM_RANGES = 7;
		cv::Mat colorRangesMat[NUM_RANGES];
	
		cv::Scalar colorRanges[NUM_RANGES][2] = {
			{ cv::Scalar(0, 30, 50), cv::Scalar(13, 215, 255) }, //RED
			{ cv::Scalar(170, 30, 50), cv::Scalar(180, 215, 255) }, //RED
			{ cv::Scalar(110, 24, 24), cv::Scalar(153, 62, 85) }, //RED
	
			{ cv::Scalar(100, 63, 166), cv::Scalar(110, 103, 255) }, //BLUE
			{ cv::Scalar(98, 105, 105), cv::Scalar(106, 134, 180) }, //BLUE
			{ cv::Scalar(96, 105, 106), cv::Scalar(106, 166, 255) }, //BLUE
			{ cv::Scalar(103, 26, 204), cv::Scalar(116, 91, 255) } //BLUE
		};
	
		// Convert the image from RGBA into an HLS image
		cv::cvtColor(frame , tempMat , CV_RGBA2RGB);
		cv::cvtColor(tempMat, tempMat, CV_RGB2HLS);
		
		if( option == VIEW_HLS_CONVERSION ) {
			frame = tempMat;
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
			frame = resultMat;
			return;
		}
		
		// Blur the gray image
		cv::blur(resultMat, tempMat, Size(3, 3), cv::Point(0, 0));
		cv::Canny(tempMat, resultMat, 0.3, 2, 3);
	
		if( option == VIEW_CANNY_CONVERSION ) {
			frame = resultMat;
			return;
		}
		
		cv::erode(resultMat, tempMat, element);
		cv::dilate(tempMat, resultMat, element);
	
		if( option == VIEW_EROSION_DILATION ) {
			frame = resultMat;
			return;
		}
		
		findShapes(frame, resultMat);
	}
}
