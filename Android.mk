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
	LOCAL_PACKAGE_NAME := RilOfono
	LOCAL_SRC_FILES := $(call all-java-files-under,src/java) \
						$(call all-java-files-under,lib/java/dbus) \
						$(call all-java-files-under,lib/java/debug) \
						$(call all-java-files-under,lib/java/ofono)
	LOCAL_JAVA_LIBRARIES := telephony-common
	LOCAL_CERTIFICATE := platform

	# TODO not sure what we *should* do with these, but for now it seems easier to get going by turning them off...
	LOCAL_DEX_PREOPT := false
	LOCAL_PROGUARD_ENABLED := disabled
include $(BUILD_PACKAGE)
