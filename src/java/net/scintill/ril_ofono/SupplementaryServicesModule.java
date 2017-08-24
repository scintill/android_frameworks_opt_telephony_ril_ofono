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

import android.os.Message;
import android.telephony.Rlog;

import org.freedesktop.dbus.Variant;
import org.ofono.Pair;
import org.ofono.SupplementaryServices;

import static com.android.internal.telephony.CommandException.Error.GENERIC_FAILURE;
import static com.android.internal.telephony.CommandsInterface.USSD_MODE_NOTIFY;
import static com.android.internal.telephony.CommandsInterface.USSD_MODE_NOT_SUPPORTED;
import static net.scintill.ril_ofono.RilOfono.privExc;
import static net.scintill.ril_ofono.RilOfono.respondExc;
import static net.scintill.ril_ofono.RilOfono.respondOk;
import static net.scintill.ril_ofono.RilOfono.runOnDbusThread;

/*package*/ class SupplementaryServicesModule {

    private static final String TAG = RilOfono.TAG;

    private final SupplementaryServices mSupplSvcs;
    private final RilOfono.RegistrantList mUSSDRegistrants;

    /*package*/ SupplementaryServicesModule(RilOfono.RegistrantList USSDRegistrant) {
        mUSSDRegistrants = USSDRegistrant;

        mSupplSvcs = RilOfono.sInstance.getOfonoInterface(SupplementaryServices.class);
    }

    @RilMethod
    public void sendUSSD(final String ussdString, final Message response) {
        // TODO network-initiated USSD. apparently they're rare, and it doesn't look like the rild backend of oFono supports them
        runOnDbusThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // TODO do on a separate thread? oFono docs seem to imply this will block everything anyway
                    Pair<String, Variant> ussdResponse = mSupplSvcs.Initiate(ussdString);
                    respondOk("sendUSSD", response, null);
                    if (!ussdResponse.a.equals("USSD")) {
                        mUSSDRegistrants.notifyResult(new String[]{""+USSD_MODE_NOT_SUPPORTED, null});
                    } else {
                        mUSSDRegistrants.notifyResult(new String[]{""+USSD_MODE_NOTIFY, (String) ussdResponse.b.getValue()});
                    }
                } catch (Throwable t) {
                    Rlog.e(TAG, "Error initiating USSD", privExc(t));
                    respondExc("sendUSSD", response, GENERIC_FAILURE, null);
                }
            }
        });
    }

}
