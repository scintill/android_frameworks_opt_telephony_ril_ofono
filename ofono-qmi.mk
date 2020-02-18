# Copyright 2019 Joey Hewitt <joey@joeyhewitt.com>
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


# Include this file in a product makefile to install ril_ofono with all dependencies,
# configured for a QMI device.


BOARD_OFONO_DRIVER := gobi

PRODUCT_PACKAGES += \
	mdm9k-boot mdm9k-efsd \
	ofonod dbus-daemon \
	RilOfono librilofono_rild

PRODUCT_PROPERTY_OVERRIDES += \
	ro.telephony.ril_class=net.scintill.ril_ofono/.RilWrapper \
	rild.libpath=/system/lib/librilofono_rild.so

# XXX we may eventually need to get parameters in here for device paths etc.
# prefer LOCAL_INIT_RC if it becomes available in our branch of the /build repo
# XXX LOCAL_PATH refers to device tree's path, kind of hacky
PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/../../../frameworks/opt/telephony_ril_ofono/init.ofono-qmi.rc:root/init.ofono-qmi.rc \
    $(LOCAL_PATH)/../../../frameworks/opt/telephony_ril_ofono/dbus.conf:system/etc/dbus.conf
