/*
 * fann_test.cpp
 *
 *  Created on: 21 de Mar de 2015
 *      Author: nf-vale
 */
#include "sign_detector.h"

extern char *assetsDataPath;

int testANNImage()
{
	const char *filename = "forbidden_traffic_signs.net";
    int strLength = strlen(assetsDataPath) + strlen(filename) + 2;
    char file_data_path[strLength];
    
    strcpy(file_data_path, assetsDataPath);
    strcat(file_data_path, "/");
    strcat(file_data_path, filename);
    
    file_data_path[strLength-1] = '\0';
    
	fann_type *calc_out;
    fann_type input[99] = {
    	0.308275, 0.241085, 0.582813, 165.02, 137.19, 100.23, 84.81, 78.58, 73.35, 60.37, 52.63,
    	49.71, 48.56, 37.58, 29.17, 23.17, 19.52, 16.19, 50.15, 77.48, 88.69, 107.17, 117.52,
    	103.60, 86.92, 84.52, 88.69, 78.04, 75.92, 85.63, 79.67, 63.71, 64.27, 70.79, 61.60, 55.63,
    	46.94, 28.00, 17.92, 20.06, 26.17, 30.73, 31.92, 37.13, 38.50, 42.81, 44.38, 47.85, 49.85,
    	50.33, 65.75, 63.42, 133.77, 120.29, 70.65, 94.35, 111.90, 95.77, 85.06, 81.38, 82.67, 82.52,
    	79.62, 47.15, 25.60, 26.10, 22.67, 22.10, 49.21, 89.38, 62.69, 20.48, 19.98, 16.83, 8.44, 27.90,
    	55.17, 57.92, 37.42, 30.48, 29.67, 29.42, 30.29, 35.42, 59.19, 62.02, 39.21, 23.52, 28.08, 69.27,
    	112.98, 113.52, 73.83, 81.71, 90.96, 103.90, 109.96, 100.02, 80.54
    };
    
	struct fann *ann = fann_create_from_file(file_data_path);
	
	// Initialize clock
	float start = CLOCK();
	
	for(int count=0;count<100;count++) {
        calc_out = fann_run(ann, input);
        
        LOG("RUNNED ANN");
        
        for(int i=0;i<ann->num_output;i++) {
        	LOG("%f ", calc_out[i]);
        }
    }
    float dur = CLOCK()- start;
    LOG("DURATION: %f ", dur);
    
    fann_destroy(ann);
}

extern "C" {
	JNIEXPORT void JNICALL Java_com_duvallsoftware_trafficsigndetector_TrafficSignDetectorActivity_runFannDetector(JNIEnv* env,
			jobject, jstring dataPath) {
		
		/*
		// Load assets from files
		AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
	    AAsset* asset = AAssetManager_open(mgr, (const char *) "xpto", AASSET_MODE_UNKNOWN);
	    if (NULL == asset) {
	        LOG("_ASSET_NOT_FOUND_");
	    }
	    long size = AAsset_getLength(asset);
	    char* buffer = (char*) malloc (sizeof(char)*size);
	    AAsset_read (asset,buffer,size);
	    AAsset_close(asset);
	    */
	    
	    const char *filename = "forbidden_traffic_signs.net";
	    const char *assetsDataPath = env->GetStringUTFChars(dataPath, NULL);
	    int strLength = strlen(assetsDataPath) + strlen(filename) + 2;
	    char file_data_path[strLength];
	    
	    strcpy(file_data_path, assetsDataPath);
	    strcat(file_data_path, "/");
	    strcat(file_data_path, filename);
	    
	    file_data_path[strLength-1] = '\0';
	    /*
		FILE *fp;
		// "/data/data/com.duvallsoftware.trafficsigndetector/files/forbidden_traffic_signs.net"
		fp=fopen(file_data_path, "r");
		
		const size_t line_size = 1024;
		char* line = (char*)calloc(line_size, sizeof (size_t));
		       
		if( NULL == fp ){
		   LOG("ERRO AO ABRIR O FILE %s", file_data_path);
		} else {			
			while (fgets(line, line_size, fp) != NULL){
			    LOG("%s\n", line);
			}
			fclose(fp);
	   	}
	   	
	   	if (line)
	    	free(line);
	    
	    */
		
	    fann_type *calc_out;
	    fann_type input[99] = {
	    	0.308275, 0.241085, 0.582813, 165.02, 137.19, 100.23, 84.81, 78.58, 73.35, 60.37, 52.63,
	    	49.71, 48.56, 37.58, 29.17, 23.17, 19.52, 16.19, 50.15, 77.48, 88.69, 107.17, 117.52,
	    	103.60, 86.92, 84.52, 88.69, 78.04, 75.92, 85.63, 79.67, 63.71, 64.27, 70.79, 61.60, 55.63,
	    	46.94, 28.00, 17.92, 20.06, 26.17, 30.73, 31.92, 37.13, 38.50, 42.81, 44.38, 47.85, 49.85,
	    	50.33, 65.75, 63.42, 133.77, 120.29, 70.65, 94.35, 111.90, 95.77, 85.06, 81.38, 82.67, 82.52,
	    	79.62, 47.15, 25.60, 26.10, 22.67, 22.10, 49.21, 89.38, 62.69, 20.48, 19.98, 16.83, 8.44, 27.90,
	    	55.17, 57.92, 37.42, 30.48, 29.67, 29.42, 30.29, 35.42, 59.19, 62.02, 39.21, 23.52, 28.08, 69.27,
	    	112.98, 113.52, 73.83, 81.71, 90.96, 103.90, 109.96, 100.02, 80.54
	    };
	    
	    cv::Mat b_matrix;
	    float MB = 0, MG = 0, MR = 0;
		float *VH, *HH;
	    
	    // B matrix is the RGB image which was captured by the camera: 48x48 px
		const int size = b_matrix.cols;
	
		VH = (float *)malloc(sizeof(float) * size);
		HH = (float *)malloc(sizeof(float) * size);
		memset(VH, 0, sizeof(VH));
		memset(HH, 0, sizeof(HH));
	
		get_B_L_GrayImage(b_matrix, &MB, &MG, &MR, VH, HH, size);
	    
		struct fann *ann = fann_create_from_file(file_data_path);
		
		//fann_print_parameters(ann);
		
		// Initialize clock
		float start = CLOCK();
		
		for(int count=0;count<100;count++) {
	        calc_out = fann_run(ann, input);
	        
	        LOG("RUNNED ANN");
	        
	        for(int i=0;i<ann->num_output;i++) {
	        	LOG("%f ", calc_out[i]);
	        }
	    }
	    float dur = CLOCK()- start;
	    LOG("DURATION: %f ", dur);
	    
	    fann_destroy(ann);
	}
}


