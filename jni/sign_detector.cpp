/**
 * Traffic Signs Detector
 */
#include "sign_detector.h"

const float DETECT_THRESHOLD = 0.8f;

const int NUMBER_WARNING_SIGNS = 1; 
const int NUMBER_FORBIDDEN_SIGNS = 11;
const int NUMBER_OBLIGATORY_SIGNS = 5;
const int NUMBER_INFORMATION_SIGNS = 1;	
	
const char *warning_sign_id_array[NUMBER_WARNING_SIGNS] = {};	
const char *forbidden_sign_id_array[NUMBER_FORBIDDEN_SIGNS] = { "B2", "C11b", "C13_40", "C13_50", "C13_60", "C13_70", "C13_80", "C13_100", "C13_120", "C14a", "C14b" };
const char *obligatory_sign_id_array[NUMBER_OBLIGATORY_SIGNS] = { "D1c", "D3a", "D4", "D8_40", "D8_50" };
const char *information_sign_id_array[NUMBER_INFORMATION_SIGNS] = { "H7" };

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

const int MAX_DETECTED_SIGNS = 99;
int tsr_idx = 0;

// current detected signs
TSD detected_traffic_signs[MAX_DETECTED_SIGNS];

// frame counter
long frameCounter = 0;

long time_since_last_detection;
    
// Erode & Dilate to isolate segments connected to nearby areas
const cv::Mat element = cv::getStructuringElement(cv::MORPH_RECT, cv::Size(1, 1), cv::Point(0,0));

const char* saveFilesPath = NULL;

char *assetsDataPath = NULL;

struct fann *warning_ann = NULL;
struct fann *forbidden_ann = NULL;
struct fann *obligatory_ann = NULL;
struct fann *information_ann = NULL;

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

int get_B_L_GrayImage(cv::Mat b_matrix, float *MR, float *MG, float *MB, float VH[], float HH[], int size) {
	cv::Mat b_l_matrix;

	// B matrix is the RGB image which was captured by the camera: 48x48 px
	if (b_matrix.empty())
		return -1;

	float t;
	t = (float)getTickCount();

	// Create destination "gray" matrix
	b_l_matrix = cv::Mat(size, size, CV_8U);

	// Set all "gray" Matrix elements to 0
	for (int i = 0; i < size; i++) {
		VH[i] = 0;
		HH[i] = 0;
	}

	MatIterator_<Vec3b> it, end;
	MatIterator_<uchar> itF, endF;
	int row = 0, col = 0;
	float T = 0;
	for (it = b_matrix.begin<Vec3b>(), end = b_matrix.end<Vec3b>(), itF = b_l_matrix.begin<uchar>(), endF = b_l_matrix.end<uchar>(); it != end; ++it, ++itF)
	{
		// Get the RGB components pixel values
		uint8_t red = (*it)[0];
		uint8_t green = (*it)[1];
		uint8_t blue = (*it)[2];

		(*MB) += blue;
		(*MG) += green;
		(*MR) += red;

		// Calculate gray value according to pre-defined RGB weights
		*itF = 0.49 * red + 0.29 * green + 0.22 * blue;

		// Calculate adaptive threshold
		T += ((*itF) * 1.0);
	}

	// Normalize the Threshold value
	T = ((T * 1.0) / (size * size));

	row = 0;
	col = 0;
	for (itF = b_l_matrix.begin<uchar>(), endF = b_l_matrix.end<uchar>(); itF != endF; ++itF)
	{
		// See if it's a new row
		if (col == size) {
			col = 0;
			row++;
		}

		float rowVal = VH[row];
		float colVal = HH[col];

		// Sum the matrix element proportional value (proportional to the number of elements)
		if ((*itF) > T) {
			VH[row] = rowVal + (float)(((*itF) * 1.0f) / size);
			HH[col++] = colVal + (float)(((*itF) * 1.0f) / size);
		}
		else {
			VH[row] = rowVal + 0;
			HH[col++] = colVal + 0;
		}
	}

	// Calculate Normalized Square value
	int normalized_square = (size * size) * 256;

	// Calculate the Normalized Maximum RGB values
	(*MB) = (*MB) / normalized_square;
	(*MG) = (*MG) / normalized_square;
	(*MR) = (*MR) / normalized_square;

	t = 1000 * ((float)getTickCount() - t) / getTickFrequency();
	//LOG("Duration: %f", t);

	return 0;
}

void clearDetectedSigns() {
	for(int i=0;i<tsr_idx;i++) {
		detected_traffic_signs[i].type = -1;
		detected_traffic_signs[i].id = -1;
	}
	
	tsr_idx = 0;
}

void setDetectedSignType(int sign_type) {
	// Get current time in milliseconds
	milliseconds ms = duration_cast< milliseconds >(
    	high_resolution_clock::now().time_since_epoch()
	);
	
	time_since_last_detection = ms.count();

	detected_traffic_signs[tsr_idx].type = sign_type;
	//detected_sign_type = sign_type;
}

void getFixedSizeImagefromRectangle(cv::Mat *previewFrame, cv::Mat *dst, cv::Rect rect) {
	cv::Mat& frame = *(Mat*)previewFrame;
	
	// Use 48 pixels for fixed size image
	const int fixedSize = 48;
	
	try {		
		if (rect.x + rect.width > frame.cols)
			rect.x = abs(frame.cols - rect.width);
		
		if (rect.y + rect.height > frame.rows)
			rect.y = abs(frame.rows - rect.height);
	
		// Crop frame from rectangle parameters
		cv::Mat miniMat = frame(rect);

		// Create fixed size pixel empty Image		
		cv::Mat mIntermediateMat(fixedSize, fixedSize, CV_8UC3, Scalar(0,0,0));
		
		// resize frame
		resize(miniMat, mIntermediateMat, mIntermediateMat.size(), 0, 0, INTER_CUBIC);
		
		// release resources
		miniMat.release();
		(*(Mat*)dst) =  mIntermediateMat;
	} catch(std::exception& ex) {
		LOG("Exception resizing detected image: %s\n", ex.what());
	}
}

void writeShapeFound(cv::Mat* image) {
	if( saveFilesPath == NULL )
		return;
		
	vector<int> compression_params;
	compression_params.push_back(CV_IMWRITE_JPEG_QUALITY);
	compression_params.push_back(95);

	try {
		// Get frame processing time
		milliseconds ms = duration_cast< milliseconds >(
		    high_resolution_clock::now().time_since_epoch()
		);
		
		// Detected image name
		std::ostringstream shapeFileName("");
		shapeFileName << saveFilesPath << "/trafficsignsdetected/sign_" << ms.count() << ".jpeg";
		
		//LOG("Saving image %s\n", shapeFileName.str().c_str());
		
		bool res = cv::imwrite(shapeFileName.str(), *(Mat*)image, compression_params);
		if( !res ) {
			LOG("Failed to write image %s to SDCard\n", shapeFileName.str().c_str());
		}
		else {
			//LOG("Successfuly wrote image %s to SDCard\n", shapeFileName.str().c_str());
		}
	}
	catch (std::exception& ex) {
		LOG("Exception saving image to SDCard: %s\n", ex.what());
	}
}

void writeShapeFound(cv::Mat* frame, cv::Rect rect) {	
	cv::Mat image;
	getFixedSizeImagefromRectangle(frame, &image, rect);
		
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
	
	//LOG("IMAGE BLUE PERCENTAGE: %f", blue_percent);
	
	resultMat.release();
	for (int i = 0; i < colorBlueRangesCount; i++) {
		tempMat[i].release();
	}
	
	return (blue_percent > 0.15);
}

char * getANNPath(const char *filename) {
    int strLength = strlen(assetsDataPath) + strlen(filename) + 2;
    char *file_data_path = (char*) malloc (sizeof(char)*strLength);
    
    strcpy(file_data_path, assetsDataPath);
    strcat(file_data_path, "/");
    strcat(file_data_path, filename);
    
    file_data_path[strLength-1] = '\0';
    
    return file_data_path;
}

void loadANNs() {
	// Load the Artificial Neural Network specific for each sign_type
    // It's more efficient to load the ANN's just once at the begining
    // and destroy them when onDestroy is called
	//if( NULL == warning_ann ) {
	//	warning_ann = fann_create_from_file(getANNPath("warning_traffic_signs.net"));
	//}
	if( NULL == forbidden_ann ) {
		forbidden_ann = fann_create_from_file(getANNPath("forbidden_traffic_signs.net"));
	}
	if( NULL == obligatory_ann ) {
		obligatory_ann = fann_create_from_file(getANNPath("obligatory_traffic_signs.net"));
	}
	if( NULL == information_ann ) {
		information_ann = fann_create_from_file(getANNPath("information_traffic_signs.net"));
	}
}

int runFannDetector(int sign_type, fann_type inputs[]) {
	int result = -1;
	
	fann_type *calc_out;
    struct fann *ann = NULL;
    
    if( sign_type == WARNING_SIGN ) {
    	//ann = warning_ann;    	
    }
    else if( sign_type == FORBIDDEN_SIGN ) {
    	ann = forbidden_ann;
    }
    else if( sign_type == OBLIGATORY_SIGN ) {
    	ann = obligatory_ann;
    }
    else if( sign_type == INFORMATION_SIGN ) {
    	ann = information_ann;
    }
    
    if( NULL != ann) {
    	//LOG("Running ANN ...");
	    calc_out = fann_run(ann, inputs);
	    //LOG("Running ANN DONE");
	    	    
	    //LOG("OUTPUTS: %i", ann->num_output);
	    float max = -1.0f;
	    int idx = -1;
	    
	    for(int i=0;i<ann->num_output;i++) {
		    if(calc_out[i] > DETECT_THRESHOLD) {
			    if( calc_out[i] > max ) {
			    	max = calc_out[i];
			    	idx = i;
			    }		    	
	    	}
	    }
	    
	    if( max > 0) {
	    	result = idx;
		    LOG("%d: %f", idx, max);
		}
    }
    
    // Result will have the detected sign id
    return result;
}

int testShapeFound(cv::Mat* frame, cv::Rect rect, int sign_type)
{
	cv::Mat b_matrix;
	
	getFixedSizeImagefromRectangle(frame, &b_matrix, rect);
	
	// TODO: Verify if we need this
	// Convert to original BGR color
	// cv::cvtColor(b_matrix , b_matrix, CV_RGBA2BGR);
	
    float MB = 0, MG = 0, MR = 0;
	float *VH, *HH;
    
    // B matrix is the RGB image which was captured by the camera: 48x48 px
	const int size = b_matrix.cols;

	VH = (float *)malloc(sizeof(float) * size);
	HH = (float *)malloc(sizeof(float) * size);
	memset(VH, 0, sizeof(VH));
	memset(HH, 0, sizeof(HH));

	get_B_L_GrayImage(b_matrix, &MB, &MG, &MR, VH, HH, size);	
	
	// MB + MG + MR + 48 (HH) + 48 (VH) = 99
	const int numInputs = 3 + (size * 2);
	fann_type inputs[numInputs];
	inputs[0] = MB;
	inputs[1] = MG;
	inputs[2] = MR;
	for (int h = 0, v = 0, idx = 0; idx<size; idx++, h++, v++) {
		inputs[idx + 3] = VH[h];
		inputs[idx + 3 + size] = HH[v];
	}
	
	return runFannDetector(sign_type, inputs);
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
		// TODO: TEST THIS PROPERLY - Ignore countours which are child of other contours
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
		
		// Rectangle which must be used when fetching the image for AAN
		// and also when saving the image to sdcard
		cv::Rect tri_square_bounds = boundRect[i];
		
		// For the representation of the captured sign use the boundRect
		// which is converted to the scaleFactor representation
		if (approx.size() >= 3 && approx.size() <= 6) {
			// Adjust the bounding rectangle dimensions and position with the scale factor value
			boundRect[i].height = (boundRect[i].height * inverseScaleFactor);
			boundRect[i].width = (boundRect[i].width * inverseScaleFactor);
			boundRect[i].x = (boundRect[i].x * inverseScaleFactor);
			boundRect[i].y = (boundRect[i].y * inverseScaleFactor);
		}		
		
		int temp_detected_sign = NO_SIGN;
		// Triangle
		if (approx.size() == 3)
		{
			setDetectedSignType(WARNING_SIGN);
						
			temp_detected_sign = testShapeFound(&frame, tri_square_bounds, WARNING_SIGN);
			rectangle(previewFrame, boundRect[i].tl(), boundRect[i].br(), triangleColor, 2, 8, 0);
			if(temp_detected_sign >= 0) {
				//detected_sign = temp_detected_sign;
				detected_traffic_signs[tsr_idx++].id = temp_detected_sign;				
				LOG("Found Traffic Sign at Idx: [%i]", temp_detected_sign);
			}			
			
			if(saveShapes) {
				writeShapeFound(&frame, tri_square_bounds);
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
				
				temp_detected_sign = testShapeFound(&frame, tri_square_bounds, INFORMATION_SIGN);
				rectangle(previewFrame, boundRect[i].tl(), boundRect[i].br(), rectangleColor, 3, 8, 0);
				if(temp_detected_sign >= 0) {
					detected_traffic_signs[tsr_idx++].id = temp_detected_sign;
					LOG("Found Traffic Sign at Idx: [%i]", temp_detected_sign);
				}
				
				if(saveShapes) {
					writeShapeFound(&frame, tri_square_bounds);
				}
			}
		}
		else
		{
			// Calculate the minimum circle which defines the shape contour
			cv::minEnclosingCircle(approx, center[i], contourRadius[i]);
			
			// Adjust the center of the countour shape with the scale factor value
			center[i].x = (center[i].x * inverseScaleFactor);
			center[i].y = (center[i].y * inverseScaleFactor);		

			// Detect and label circles
			cv::Rect r = cv::boundingRect(contours[i]);
			
			cv::Mat mCircle;
			getFixedSizeImagefromRectangle(&frame, &mCircle, r);
			
			// Adjust the bounding rectangle dimensions and position with the scale factor value
			r.height = (r.height * inverseScaleFactor);
			r.width = (r.width * inverseScaleFactor);
			r.x = (r.x * inverseScaleFactor);
			r.y = (r.y * inverseScaleFactor);
			
			int radius = r.width / 2;			
			
			if( imageIsBlueColored(&mCircle)) {
				setDetectedSignType(OBLIGATORY_SIGN);
				temp_detected_sign = testShapeFound(&frame, (cv::Rect)cv::boundingRect(contours[i]), OBLIGATORY_SIGN);			
			} else {
				setDetectedSignType(FORBIDDEN_SIGN);
				temp_detected_sign = testShapeFound(&frame, (cv::Rect)cv::boundingRect(contours[i]), FORBIDDEN_SIGN);
			}
			
			if (std::abs(1 - ((float)r.width / r.height)) <= 0.2 &&
				std::abs(1 - (cv::contourArea(contours[i]) / (CV_PI * std::pow(radius, 2)))) <= 0.2) {
				circle(previewFrame, center[i], (int)(contourRadius[i] * inverseScaleFactor), circleColor, 4, 8, 0);
			}
			else {
				circle(previewFrame, center[i], (int)(contourRadius[i] * inverseScaleFactor), circleColor, 4, 8, 0);
			}
			if(temp_detected_sign >= 0) {
				//detected_sign = temp_detected_sign;
				detected_traffic_signs[tsr_idx++].id = temp_detected_sign;				
				LOG("Found Traffic Sign at Idx: [%i]", temp_detected_sign);
			}
						
			if(saveShapes) {							    
				// Save shapes on SDCard for later analysis
				// Use the boundingRect since r is already set with the inverted scale factor
				writeShapeFound(&frame, (cv::Rect)cv::boundingRect(contours[i]));				
			}
			
			mCircle.release();
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
	JNIEXPORT jobjectArray JNICALL Java_com_duvallsoftware_trafficsigndetector_TrafficSignDetectorActivity_detectTrafficSigns
									(JNIEnv* env, jobject, jstring dataPath, jlong addrRgba, jint option, jboolean saveShapes, jboolean showFPS) {
		
		// Initialized fps clock
		float start = CLOCK();

		// Set Data Path
		assetsDataPath = (char*)env->GetStringUTFChars(dataPath, NULL);
		
		// Load ANN networks from files
		loadANNs();
		
		// Clear previous detected signs
		clearDetectedSigns();
		
		// Set the path where we should store the saved image files
		// Downloads/trafficsigns_detected shall be used for now
		if( saveShapes && saveFilesPath == NULL ) {
			jclass envClass = env->FindClass("android/os/Environment");
			jfieldID fieldId = env->GetStaticFieldID(envClass, "DIRECTORY_DOWNLOADS", "Ljava/lang/String;");
			jstring jstrParam = (jstring)env->GetStaticObjectField(envClass, fieldId);

			jmethodID getExtStorageDirectoryMethod = env->GetStaticMethodID(envClass, "getExternalStoragePublicDirectory",  "(Ljava/lang/String;)Ljava/io/File;");
			jobject extStorageFile = env->CallStaticObjectMethod(envClass, getExtStorageDirectoryMethod, jstrParam);
			jclass fileClass = env->FindClass("java/io/File");
			jmethodID getPathMethod = env->GetMethodID(fileClass, "getPath", "()Ljava/lang/String;");
			jstring extStoragePath = (jstring)env->CallObjectMethod(extStorageFile, getPathMethod);
			
			saveFilesPath = env->GetStringUTFChars(extStoragePath,NULL);
			
			//LOG("DOWNLOADS DIRECTORY: %s", saveFilesPath);
		}

        if( frameCounter == LONG_MAX - 1 )
        	frameCounter = 0;
        
		cv::Mat& previewFrame = *(Mat*)addrRgba;
		
		// Update scale factor if needed
		if( scaleFactor < 0 || isResolutionChanged(previewFrame.cols) ) { 
			// Base width is 480 pixels so the working frame should have this scale factor relation
			scaleFactor = (previewFrame.cols > REFERENCE_WIDTH ? REFERENCE_WIDTH / (previewFrame.cols * 1.0f) : 1.0);
			
			setInverseScaleFactor(previewFrame.cols);
			
			//LOG("Scale Factor is set to: %f", scaleFactor);
			//LOG("Inverse Scale Factor is set to: %f", inverseScaleFactor);
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
			
			frame.release();
			tempMat.release();
			resultMat.release();
			
			return (jobjectArray)env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));;
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
			
			frame.release();
			tempMat.release();
			resultMat.release();
			
			return (jobjectArray)env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));;
		}
		
		// Blur the gray image
		cv::blur(resultMat, tempMat, Size(3, 3), cv::Point(0, 0));
		cv::Canny(tempMat, resultMat, 0.3, 2, 3);
	
		if( option == VIEW_CANNY_CONVERSION ) {
			cv::resize(resultMat, previewFrame, Size(0,0), inverseScaleFactor, inverseScaleFactor);
			
			frame.release();
			tempMat.release();
			resultMat.release();
			
			return (jobjectArray)env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));;
		}
		
		// TODO: CONFIRM THIS
		// Open seems to be equivelent to erode and dilate but faster
		// http://docs.opencv.org/doc/tutorials/imgproc/opening_closing_hats/opening_closing_hats.html#opening
		cv::morphologyEx(resultMat, resultMat, MORPH_OPEN, element);
	
		if( option == VIEW_EROSION_DILATION ) {
			cv::resize(resultMat, previewFrame, Size(0,0), inverseScaleFactor, inverseScaleFactor);
			
			frame.release();
			tempMat.release();
			resultMat.release();
			
			return (jobjectArray)env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));;
		}
		
		/*
		if( detected_sign_type != NO_SIGN ) {
			// Get current time in milliseconds
			milliseconds ms = duration_cast< milliseconds >(
		    	high_resolution_clock::now().time_since_epoch()
			);
			
			// X seconds after the last detected sign set the detection to NO_SIGN
			if( ((long)ms.count() - time_since_last_detection) > SIGNAL_DETECTION_MESSAGE_TIMEOUT ) {
				detected_sign_type = NO_SIGN;
				detected_sign = NO_SIGN;
			}
		}
		*/
		
		findShapes(previewFrame, frame, resultMat, saveShapes);			
        
        if( showFPS ) {
        	float dur = CLOCK()- start;        
	        float avgfpsF = avgfps();
	        ostringstream avgfps("");
	    	avgfps << "FPS: " << avgfpsF;
	        cv::putText(previewFrame, avgfps.str().c_str(), cvPoint(5,15), FONT_HERSHEY_PLAIN, 1.0, CV_RGB(0,0,255), 1.0);
        }
        
        // Release resources
        frame.release();
		tempMat.release();
		resultMat.release();
		
		jobjectArray signs_detected = (jobjectArray)env->NewObjectArray(tsr_idx, env->FindClass("java/lang/String"), env->NewStringUTF(""));
         
		for(int i=0;i<tsr_idx;i++) {
			if( detected_traffic_signs[i].type >= 0 ) {
				switch(detected_traffic_signs[i].type) {
		        	case WARNING_SIGN:
		        		if( detected_traffic_signs[i].id < NUMBER_WARNING_SIGNS ) {
		        			//return env->NewStringUTF(warning_sign_id_array[detected_traffic_signs[i].id]);
		        			env->SetObjectArrayElement(signs_detected,i,env->NewStringUTF(warning_sign_id_array[detected_traffic_signs[i].id]));
		        		}
		        		break;
		        	case FORBIDDEN_SIGN:
		        		if( detected_traffic_signs[i].id < NUMBER_FORBIDDEN_SIGNS ) {
		        			//return env->NewStringUTF(forbidden_sign_id_array[detected_traffic_signs[i].id]);
		        			env->SetObjectArrayElement(signs_detected,i,env->NewStringUTF(forbidden_sign_id_array[detected_traffic_signs[i].id]));
		        		}	        		
		        		break;
		        	case OBLIGATORY_SIGN:
		        		if( detected_traffic_signs[i].id < NUMBER_OBLIGATORY_SIGNS ) {
		        			//return env->NewStringUTF(obligatory_sign_id_array[detected_traffic_signs[i].id]);
		        			env->SetObjectArrayElement(signs_detected,i,env->NewStringUTF(obligatory_sign_id_array[detected_traffic_signs[i].id]));
		        		}	        		
		        		break;
		        	case INFORMATION_SIGN:
		        		if( detected_traffic_signs[i].id < NUMBER_INFORMATION_SIGNS ) {
		        			//return env->NewStringUTF(information_sign_id_array[detected_traffic_signs[i].id]);
		        			env->SetObjectArrayElement(signs_detected,i,env->NewStringUTF(information_sign_id_array[detected_traffic_signs[i].id]));
		        		}	        		
		        		break;
		        }
	        }
        }

 		return signs_detected;
	}
	
	JNIEXPORT jint JNICALL Java_com_duvallsoftware_trafficsigndetector_TrafficSignDetectorActivity_destroyANNs
									(JNIEnv* env, jobject) {
		if( NULL == warning_ann ) {
			fann_destroy(warning_ann);
		}
		if( NULL == forbidden_ann ) {
			fann_destroy(forbidden_ann);
		}
		if( NULL == obligatory_ann ) {
			fann_destroy(obligatory_ann);
		}
		if( NULL == information_ann ) {
			fann_destroy(information_ann);
		}
		
		LOG("Destroyed ANNs");
		return 0;
	}
}
