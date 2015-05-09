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

# Double FANN Module
include $(CLEAR_VARS)
LOCAL_MODULE    := double-fann
LOCAL_SRC_FILES := fann/doublefann.c \
       fann/fann_cascade.c \
       fann/fann_error.c \
       fann/fann_io.c \
       fann/fann_train_data.c \
       fann/fann.c
LOCAL_C_INCLUDES += $(LOCAL_PATH)/fann/include
include $(BUILD_STATIC_LIBRARY)

# Static FANN Module
include $(CLEAR_VARS)
LOCAL_MODULE    := fixed-fann
LOCAL_SRC_FILES := fann/fixedfann.c \
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

# MAIN Module FANN
include $(CLEAR_VARS)
#include C:/Android/Development/OpenCV-2.4.10-android-sdk/sdk/native/jni/OpenCV.mk
include C:/Android/Development/OpenCV-3.0-RC1-android-sdk/sdk/native/jni/OpenCV.mk

LOCAL_MODULE    := sign_detector
LOCAL_SRC_FILES := fann_test.cpp sign_detector.cpp
#LOCAL_LDLIBS +=  -llog -ldl

LOCAL_LDLIBS            += -lm -llog -landroid -l
LOCAL_STATIC_LIBRARIES  += float-fann double-fann fixed-fann fann
LOCAL_CFLAGS            += -std=c++11 -I$(LOCAL_PATH)/fann/include

include $(BUILD_SHARED_LIBRARY)
