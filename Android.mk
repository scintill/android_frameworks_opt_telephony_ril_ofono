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
include $(CLEAR_VARS)

# TODO what are the performance implications of the below line? I literally spent hours trying to figure out why I couldn't load from my framework jar, and this "solves" it.
# I was getting the log "I/art     (10799): DexFile_isDexOptNeeded file /system/framework/arm/ril_ofono.odex is out of date for /system/framework/ril_ofono.jar"
# in logcat, followed by dex2oat failing with "Failed to open dex from file descriptor for zip file '/system/framework/ril_ofono.jar': Entry not found"
# I have no idea what magic lets similar jars like input.jar work (art doesn't think their odex is out of date) but mine doesn't. I tried building in the tree,
# under frameworks/base/cmds in case that has special treatment, etc.
# Anyway, since there's some mystery-problem making our odex not match the jar, the solution is not to use an odex...
LOCAL_DEX_PREOPT := false

LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_MODULE := ril_ofono
include $(BUILD_JAVA_LIBRARY)
