# Copyright 2017 Joey Hewitt <joey@joeyhewitt.com>
#
# This file is part of ril_ofono.
#
# ril_ofono is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# ril_ofono is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with ril_ofono.  If not, see <http://www.gnu.org/licenses/>.

LOCAL_PATH := $(call my-dir)

# Build our Java RIL
include $(CLEAR_VARS)
	LOCAL_MODULE := ril_ofono
	# TODO what are the performance implications of the below line? I literally spent hours trying to figure out why I couldn't load from my framework jar, and this "solves" it.
	# I was getting the log "I/art     (10799): DexFile_isDexOptNeeded file /system/framework/arm/ril_ofono.odex is out of date for /system/framework/ril_ofono.jar"
	# in logcat, followed by dex2oat failing with "Failed to open dex from file descriptor for zip file '/system/framework/ril_ofono.jar': Entry not found"
	# I have no idea what magic lets similar jars like input.jar work (art doesn't think their odex is out of date) but mine doesn't. I tried building in the tree,
	# under frameworks/base/cmds in case that has special treatment, etc.
	# Anyway, since there's some mystery-problem making our odex not match the jar, the solution is not to use an odex...
	LOCAL_DEX_PREOPT := false

	LOCAL_SRC_FILES := $(call all-java-files-under,src/java)
include $(BUILD_JAVA_LIBRARY)

# Build libril.so glue that interfaces between rild and our Java
include $(CLEAR_VARS)
	LOCAL_MODULE := libril_ofono
	LOCAL_SRC_FILES := $(call all-c-files-under,src/c)
	LOCAL_SHARED_LIBRARIES := liblog libart libnativehelper libandroid_runtime
	LOCAL_CFLAGS := -Wall -Wextra -Werror

	# https://stackoverflow.com/a/33945805
	LOCAL_C_INCLUDES += ${JNI_H_INCLUDE}
	LOCAL_LDFLAGS += \
		-Wl,--version-script,$(LOCAL_PATH)/version-script.txt
include $(BUILD_SHARED_LIBRARY)

# libsigchain.so, for historical reasons(?), is a stub that calls abort(). Programs linking against libart are supposed to locally
# define its symbols and override the .so's, but I couldn't figure out how to do that when rild is the program loading me, who is loading libart...
# I tried some dlopen() stuff but couldn't make it work. For now, we'll build this and sub it in for the stubbed libsigchain.so file when we start rild.
# Maybe we can do some tricks with pre-opening it and pointing the fd somewhere else, or extended Android-specific features of the dlfcn API.
include $(CLEAR_VARS)
	LOCAL_MODULE := libsigchain-real

	LOCAL_SHARED_LIBRARIES := liblog libdl
	LOCAL_WHOLE_STATIC_LIBRARIES += libsigchain
	LOCAL_LDFLAGS += \
		-Wl,--version-script,art/sigchainlib/version-script.txt
include $(BUILD_SHARED_LIBRARY)

