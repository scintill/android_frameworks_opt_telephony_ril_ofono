/*
 * Copyright 2017 Joey Hewitt <joey@joeyhewitt.com>
 *
 * This file is part of ril_ofono.
 *
 * ril_ofono is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ril_ofono is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ril_ofono.  If not, see <http://www.gnu.org/licenses/>.
 */

#define RIL_SHLIB
#include <telephony/ril.h>
typedef struct RIL_Env RIL_Env;

#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <dlfcn.h>

#define LOG_TAG "ril_ofono"
#include <utils/Log.h>

static unsigned char g_inited = 0;
static JNIEnv *g_jenv = NULL;
static jobject g_radiofuncs = NULL;
static JavaVM *g_jvm = NULL;

#define PKG "net/scintill/ril_ofono"

/**
 * intialize dalvik
 * @return 0 for success
 */
static int initVm() {
	// http://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/functions.html
	// http://www.developer.com/java/data/how-to-create-a-jvm-instance-in-jni.html
	// TODO use libnativehelper's JniInvation? https://stackoverflow.com/questions/11573406/android-c-c-native-calls-java-apis
	JavaVMInitArgs vmArgs;
	vmArgs.version = JNI_VERSION_1_6;
	// TODO set up hooks for exit etc?
	JavaVMOption jvmOpts[] = {
		{ "-classpath", 0 },
		{ "/system/framework/ril_ofono.jar", 0 },
	};
	vmArgs.nOptions = sizeof(jvmOpts) / sizeof(jvmOpts[0]);
	vmArgs.options = jvmOpts;
	vmArgs.ignoreUnrecognized = JNI_FALSE;
	putenv("ANDROID_DATA=/data/misc/radio"); // TODO

	if (JNI_CreateJavaVM(&g_jvm, &g_jenv, &vmArgs) != JNI_OK) {
		RLOGE("failed to create JVM");
		return 1;
	}
	return 0;
}

/**
 * initialize main Java object
 * @return 0 for success
 */
static int initJobject(const RIL_Env *env, int argc, char *argv[]) {
	// find Ril class
	jclass jcls = (*g_jenv)->FindClass(g_jenv, PKG"/Ril");
	if (!jcls) {
		RLOGE("error finding class "PKG"/Ril");
		goto fail;
	}

	// construct
	jmethodID mid = (*g_jenv)->GetMethodID(g_jenv, jcls, "<init>", "()V");
	if (!mid) {
		RLOGE("error finding "PKG"/Ril constructor");
		goto fail;
	}

	g_radiofuncs = (*g_jenv)->NewObject(g_jenv, jcls, mid);
	if (!g_radiofuncs) {
		RLOGE("error constructing "PKG"/Ril");
		goto fail;
	}

	// call init()
	mid = (*g_jenv)->GetMethodID(g_jenv, jcls, "init", "(L"PKG"/Rild;[Ljava/lang/String;)V");
	if (!mid) {
		RLOGE("error finding "PKG"/Ril init() method");
		goto fail;
	}

	// TODO args
	(*g_jenv)->CallVoidMethod(g_jenv, g_radiofuncs, mid, NULL, NULL);

	return 0;

fail:
	if ((*g_jenv)->ExceptionOccurred(g_jenv)) {
		jniLogException(g_jenv, ANDROID_LOG_ERROR, LOG_TAG, NULL);
	}
	(*g_jvm)->DestroyJavaVM(g_jvm);
	return 1;
}

/**
 * rild calls to make requests
 *
 * @param request is one of RIL_REQUEST_*
 * @param data is pointer to data defined for that RIL_REQUEST_*
 *        data is owned by caller, and should not be modified or freed by callee
 * @param t should be used in subsequent call to RIL_onResponse
 * @param datalen the length of data
 *
 */
static void onRequest(int request, void *data, size_t datalen, RIL_Token t) {
}

/**
 * rild calls to get the current radio state synchronously
 */
static RIL_RadioState onStateRequest() {
	return RADIO_STATE_UNAVAILABLE;
}

/**
 * rild calls to check for support
 *
 * This function returns "1" if the specified RIL_REQUEST code is
 * supported and 0 if it is not
 *
 * @param requestCode is one of RIL_REQUEST codes
 */
static int getSupports(int requestCode) {
	return 0;
}

/**
 * rild calls to cancel a request
 *
 * This function is called from a separate thread--not the
 * thread that calls RIL_RequestFunc--and indicates that a pending
 * request should be cancelled.
 *
 * On cancel, the callee should do its best to abandon the request and
 * call RIL_onRequestComplete with RIL_Errno CANCELLED at some later point.
 *
 * Subsequent calls to  RIL_onRequestComplete for this request with
 * other results will be tolerated but ignored. (That is, it is valid
 * to ignore the cancellation request)
 *
 * RIL_Cancel calls should return immediately, and not wait for cancellation
 *
 * Please see ITU v.250 5.6.1 for how one might implement this on a TS 27.007
 * interface
 *
 * @param t token wants to be canceled
 */
static void onCancel(RIL_Token t) {
}

/**
 * rild calls to get version
 *
 * Return a version string for your RIL implementation
 */
static const char* getVersion() {
	return "ril_ofono v0.0";
}

static RIL_RadioFunctions radioFunctions = {
	.version = 9,//RIL_VERSION
    .onRequest = onRequest,
    .onStateRequest = onStateRequest,
    .supports = getSupports,
    .onCancel = onCancel,
    .getVersion = getVersion,
};

/* exported */ const RIL_RadioFunctions *RIL_Init(const RIL_Env *env, int argc, char **argv) {
	RLOGD("RIL_Init2()");
	if (g_inited) {
		RLOGE("rild called RIL_Init() a second time; failing");
		return NULL;
	}
	g_inited = 1;

	//  helper functions will log details if they fail
	if (initVm()) {
		return NULL;
	}
	if (initJobject(env, argc, argv)) {
		return NULL;
	}

	return &radioFunctions;
}
