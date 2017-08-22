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

package net.scintill.ril_ofono;

import android.telephony.Rlog;

import com.android.internal.telephony.DriverCall;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Utils {

    private static final String TAG = "OfonoUtils";

    static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZ");

    public static String formatOfonoDate(Date d) {
        return FORMAT.format(d);
    }

    public static Date parseOfonoDate(String s) {
        try {
            return FORMAT.parse(s);
        } catch (ParseException e) {
            return null;
        }
    }

    /*package*/ static DriverCall.State parseOfonoCallState(String s) {
        switch(s) {
            case "active":
                return DriverCall.State.ACTIVE;
            case "held":
                return DriverCall.State.HOLDING;
            case "dialing":
                return DriverCall.State.DIALING;
            case "alerting":
                return DriverCall.State.ALERTING;
            case "incoming":
                return DriverCall.State.INCOMING;
            case "waiting":
                return DriverCall.State.WAITING;
            default:
                Rlog.e(TAG, "Unknown/unusable state for call: "+s+"; ignoring");
                return null;
        }
    }

    // opposite of IccUtils#bcdToString
    /*package*/ static byte[] stringToBcd(String str) {
        byte[] ret = new byte[(str.length() / 2) + 1];
        for (int i = 0, j = 0; i < ret.length; i++) {
            ret[i] = (byte) (bcdNibble(str, j++) | (bcdNibble(str, j++) << 4));
        }
        return ret;
    }

    /*package*/ static byte bcdNibble(String s, int i) {
        return i < s.length() ? (byte)(s.charAt(i) - '0') : 0xf;
    }


}
