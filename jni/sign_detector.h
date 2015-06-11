#ifndef _SIGN_DETECTOR_H_
#define _SIGN_DETECTOR_H_

#include <jni.h>
#include <opencv2/opencv.hpp>
#include <android/log.h>
#include <chrono>
#include <sys/timeb.h>
#include <limits.h>

#include <stdio.h>
#include <stdlib.h>

// FANN related
#include "fann.h"
#include "floatfann.h"

/*
#include <sstream>
#include <fstream>
#include <iostream>
*/

#define DEBUG 1

#define  LOG_TAG "SIGN DETECTOR JNI"
#ifdef DEBUG
#define  LOG(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#else
#define  LOG(...)
#endif

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

typedef struct traffic_sign {
	int type = NO_SIGN;
	int id = NO_SIGN;
} TSD;

using namespace std;
using namespace cv;
using namespace std::chrono;

float CLOCK();

int get_B_L_GrayImage(cv::Mat b_matrix, float *MR, float *MG, float *MB, float VH[], float HH[], int size);

int testShapeFound(cv::Mat* frame, cv::Rect rect, cv::Mat* dst);
#endif