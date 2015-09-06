LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# Float FANN Module
LOCAL_MODULE    := float-fann
LOCAL_SRC_FILES := fann/floatfann.c \
       fann/fann_cascade.c \
       fann/fann_error.c \
       fann/fann_io.c \
       fann/fann_train_data.c \
       fann/fann.c
LOCAL_C_INCLUDES += $(LOCAL_PATH)/fann/include
include $(BUILD_STATIC_LIBRARY)

# FANN Module
include $(CLEAR_VARS)
LOCAL_MODULE    := fann
LOCAL_SRC_FILES := fann/fann_cascade.c \
       fann/fann_error.c \
       fann/fann_io.c \
       fann/fann_train_data.c \
       fann/fann.c
LOCAL_C_INCLUDES += $(LOCAL_PATH)/fann/include
include $(BUILD_STATIC_LIBRARY)

##leptonica
#LEPTONICA_LOCAL := $(LOCAL_PATH)/leptonica
#LEPTONICA_PATH := $(LEPTONICA_LOCAL)/src
#
#include $(CLEAR_VARS)
#
#LOCAL_MODULE := liblept
#LOCAL_SRC_FILES := liblept.so
#LOCAL_EXPORT_C_INCLUDES := \
#  $(LEPTONICA_LOCAL) \
#  $(LEPTONICA_PATH)/src
#
#include $(PREBUILT_SHARED_LIBRARY)
#
##tesseract
#TESSERACT_LOCAL := $(LOCAL_PATH)/tesseract
#TESSERACT_PATH := $(TESSERACT_LOCAL)/src
#
#include $(CLEAR_VARS)
#
#LOCAL_MODULE := libtess
#LOCAL_SRC_FILES := libtess.so
#LOCAL_EXPORT_C_INCLUDES := \
#  $(LOCAL_PATH) \
#  $(TESSERACT_PATH)/api \
#  $(TESSERACT_PATH)/ccmain \
#  $(TESSERACT_PATH)/ccstruct \
#  $(TESSERACT_PATH)/ccutil \
#  $(TESSERACT_PATH)/classify \
#  $(TESSERACT_PATH)/cube \
#  $(TESSERACT_PATH)/cutil \
#  $(TESSERACT_PATH)/dict \
#  $(TESSERACT_PATH)/opencl \
#  $(TESSERACT_PATH)/neural_networks/runtime \
#  $(TESSERACT_PATH)/textord \
#  $(TESSERACT_PATH)/viewer \
#  $(TESSERACT_PATH)/wordrec \
#  $(LEPTONICA_PATH)/src \
#  $(TESSERACT_LOCAL)
#LOCAL_SHARED_LIBRARIES := liblept
#
#include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
#include C:/Android/Development/OpenCV-2.4.10-android-sdk/sdk/native/jni/OpenCV.mk
#include C:/Android/Development/OpenCV-3.0-RC1-android-sdk/sdk/native/jni/OpenCV.mk
include C:/Android/Development/OpenCV-3.0-android-sdk/sdk/native/jni/OpenCV.mk

LOCAL_MODULE    := sign_detector
LOCAL_SRC_FILES := fann_test.cpp sign_detector.cpp android_fopen.c
#LOCAL_LDLIBS +=  -llog -ldl

LOCAL_LDLIBS            += -lm -llog -landroid -l
LOCAL_STATIC_LIBRARIES  += float-fann fann
#LOCAL_SHARED_LIBRARIES += liblept libtess
LOCAL_CFLAGS            += -std=c++11 -I$(LOCAL_PATH)/fann/include

include $(BUILD_SHARED_LIBRARY)

