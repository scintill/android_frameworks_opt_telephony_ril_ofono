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

# Build third-party libraries (separately so we can ignore warnings)
include $(CLEAR_VARS)
	LOCAL_MODULE := RilOfono.3rdparty
	LOCAL_SRC_FILES := $(call find-subdir-files,lib/java/dbus -path lib/java/dbus/org/freedesktop/dbus/test -prune -o \( -name "*.java" -print \)) \
						$(call all-java-files-under,lib/java/debug) \
						$(call all-java-files-under,lib/java/ofono)
include $(BUILD_STATIC_JAVA_LIBRARY)

# Build our Java RIL (as a lib, because we need the RilWrapper to complete building, but building the RilWrapper needs the rest)
include $(CLEAR_VARS)
	LOCAL_MODULE := RilOfono.lib
	LOCAL_SRC_FILES := $(call all-java-files-under,src/java)
	LOCAL_STATIC_JAVA_LIBRARIES := RilOfono.3rdparty
	LOCAL_JAVA_LIBRARIES := telephony-common

	LOCAL_JAVACFLAGS := -Xlint
include $(BUILD_STATIC_JAVA_LIBRARY)

# Build the wrapper build tool
# XXX Building to target (not host) because it seems easier that way.
# As a static library, I think it will not actually end up on the target.
include $(CLEAR_VARS)
	LOCAL_MODULE := RilOfono.buildtool
	LOCAL_SRC_FILES := $(call all-java-files-under,build/java)
	LOCAL_STATIC_JAVA_LIBRARIES := RilOfono.lib
	LOCAL_JAVA_LIBRARIES := telephony-common

	LOCAL_JAVACFLAGS := -Xlint
include $(BUILD_STATIC_JAVA_LIBRARY)

# Define RilWrapper build process
RILOFONO_WRAPPER_JAVA := $(call generated-sources-dir-for,APPS,RilOfono)/RilWrapper.java
$(RILOFONO_WRAPPER_JAVA): PRIVATE_CUSTOM_TOOL = java -cp $(call normalize-path-list,$^) net.scintill.ril_ofono.BuildRilWrapper > "$@"
$(RILOFONO_WRAPPER_JAVA): $(foreach lib,RilOfono.buildtool framework telephony-common,$(call intermediates-dir-for,JAVA_LIBRARIES,$(lib),,COMMON)/classes.jar)
	$(transform-generated-source)

# Build RIL package
include $(CLEAR_VARS)
	LOCAL_PACKAGE_NAME := RilOfono
	LOCAL_GENERATED_SOURCES := $(RILOFONO_WRAPPER_JAVA)
	LOCAL_STATIC_JAVA_LIBRARIES := RilOfono.lib
	LOCAL_JAVA_LIBRARIES := telephony-common
	LOCAL_CERTIFICATE := platform

	LOCAL_JAVACFLAGS := -Xlint

	# TODO not sure what we *should* do with these, but for now it seems easier to get going by turning them off...
	LOCAL_DEX_PREOPT := false
	LOCAL_PROGUARD_ENABLED := disabled
include $(BUILD_PACKAGE)
