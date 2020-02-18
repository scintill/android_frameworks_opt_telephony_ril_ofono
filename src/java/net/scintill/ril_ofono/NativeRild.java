/*
 * Copyright 2020 Joey Hewitt <joey@joeyhewitt.com>
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
package net.scintill.ril_ofono;

import android.content.Context;
import android.net.NetworkUtils;
import android.telephony.Rlog;

import com.android.internal.telephony.RIL;

import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/*package*/ class NativeRild {
    private static final String TAG = RilOfono.TAG;

    private RIL mRil;

    /*package*/ NativeRild(Context ctxt) {
        mRil = new RIL(ctxt, 0, 0);
    }

    // TODO ipv4 only?
    /*package*/ void configureInterface(String interfaceName, String ipAddress, int prefixLength, String gateway, String dns1, String dns2) {
        Rlog.d(TAG, "configureInterface "+interfaceName+" "+ipAddress+" "+prefixLength+" "+gateway+" "+dns1+" "+dns2);

        byte[] interfaceNameBytes = new byte[16];
        if (interfaceName.length() >= interfaceNameBytes.length) {
            throw new IllegalArgumentException("interfaceName too long");
        }

        try {
            System.arraycopy(interfaceName.getBytes("ASCII"), 0, interfaceNameBytes, 0, interfaceName.length());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        ByteBuffer bb = ByteBuffer.allocate(36);
        bb.order(ByteOrder.nativeOrder());
        bb.put(interfaceNameBytes);
        bb.putInt(ip4AddressToInt(ipAddress));
        bb.putInt(prefixLength);
        bb.putInt(gateway != null ? ip4AddressToInt(gateway) : 0);
        bb.putInt(dns1 != null ? ip4AddressToInt(dns1) : 0);
        bb.putInt(dns2 != null ? ip4AddressToInt(dns2) : 0);
        mRil.invokeOemRilRequestRaw(bb.array(), null);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Rlog.e(TAG, "Interrupted", e);
        }
        // TODO response
    }

    /*package*/ void shutdownInterface(String interfaceName) {
        configureInterface(interfaceName, "0.0.0.0", 0, null, null, null);
    }

    private int ip4AddressToInt(String address) {
        return NetworkUtils.inetAddressToInt((Inet4Address) NetworkUtils.numericToInetAddress(address));
    }

}

