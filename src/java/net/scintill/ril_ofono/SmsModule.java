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
import android.telephony.SmsMessage;

import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.SmsResponse;

import org.freedesktop.dbus.Path;
import org.ofono.MessageManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.android.internal.telephony.CommandException.Error.GENERIC_FAILURE;
import static net.scintill.ril_ofono.RilOfono.privStr;
import static net.scintill.ril_ofono.RilOfono.respondExc;
import static net.scintill.ril_ofono.RilOfono.respondOk;
import static net.scintill.ril_ofono.RilOfono.runOnDbusThread;
import static net.scintill.ril_ofono.RilOfono.runOnMainThread;

/*package*/ class SmsModule {

    private static final String TAG = RilOfono.TAG;

    private MessageManager mMessenger;
    private RilOfono.RegistrantList mSmsRegistrants;

    AtomicInteger mSmsRef = new AtomicInteger(1);
    final Map<String, Message> mMapSmsDbusPathToSenderCallback = new HashMap<>();
    // TODO synchronization on this map object is how I ensure signals don't arrive before the entry into this map,
    // but are there any adverse effects of synchronizing so broadly?

    /*package*/ SmsModule(RilOfono.RegistrantList smsRegistrants) {
        mSmsRegistrants = smsRegistrants;

        mMessenger = RilOfono.sInstance.getOfonoInterface(MessageManager.class);
        RilOfono.sInstance.registerDbusSignal(MessageManager.IncomingPdu.class, this);
        RilOfono.sInstance.registerDbusSignal(org.ofono.Message.PropertyChanged.class, this);
    }

    @RilMethod
    public void sendSMS(String smscPDUStr, String pduStr, final Message response) {
        Rlog.d(TAG, "sendSMS");
        // TODO gsm-specific?
        if (smscPDUStr == null) smscPDUStr = "00";

        final byte[] smscPDU = IccUtils.hexStringToBytes(smscPDUStr);
        final byte[] pdu = IccUtils.hexStringToBytes(pduStr);

        runOnDbusThread(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (mMapSmsDbusPathToSenderCallback) {
                        // TODO timeout on this method? at least we're not on the main thread,
                        // but we could block anything else trying to get on the dbus thread
                        Path sentMessage = mMessenger.SendPdu(smscPDU, pdu);
                        mMapSmsDbusPathToSenderCallback.put(sentMessage.getPath(), response);
                    }
                } catch (Throwable t) {
                    // TODO catch format errors that oFono found and return a better error code?
                    Rlog.e(TAG, "Error sending msg", t);
                    respondExc("sendSMS", response, GENERIC_FAILURE, null);
                }
            }
        });
    }

    public void handleSendSmsComplete(String msgDbusPath, String status) {
        // find callback from sendSMS()
        Message senderCb;
        synchronized (mMapSmsDbusPathToSenderCallback) {
            senderCb = mMapSmsDbusPathToSenderCallback.get(msgDbusPath);
        }
        if (senderCb == null) {
            Rlog.e(TAG, "Got a signal about a message we don't know about! path="+msgDbusPath);
        } else {
            // we currently have no use for the sms reference numbers, but let's give out sensible ones
            boolean success = status.equals("sent");
            String ackPdu = ""; // we don't have one, I don't think SmsResponse uses it either
            if (success) {
                respondOk("sendSMS", senderCb, new SmsResponse(mSmsRef.incrementAndGet(), ackPdu, -1));
            } else {
                respondExc("sendSMS", senderCb, GENERIC_FAILURE, new SmsResponse(mSmsRef.incrementAndGet(), ackPdu, -1));
            }
        }
    }

    public void handle(final MessageManager.IncomingPdu s) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                try {
                    SmsMessage msg = SmsMessage.createFromPdu(normalizePdu(s.pdu, s.tpdu_len), SmsConstants.FORMAT_3GPP);
                    try {
                        // someone decided to swallow exceptions and return null as the wrapped object, so check it
                        if (msg != null) msg.getTimestampMillis();
                    } catch (NullPointerException e) {
                        // SmsMessage probably logged more information about the cause, but I want to know what the PDU was
                        Rlog.e(TAG, "Null returned from parsing incoming PDU "+privStr(IccUtils.bytesToHexString(s.pdu)+" tpdu_len="+s.tpdu_len));
                    }
                    mSmsRegistrants.notifyResult(msg);
                } catch (Throwable t) {
                    Rlog.e(TAG, "Error handling incoming PDU "+privStr(IccUtils.bytesToHexString(s.pdu)+" tpdu_len="+s.tpdu_len));
                }
            }
        });
    }

    private static byte[] normalizePdu(byte[] pdu, int tpdu_len) {
        // pdu might have smsc prefixed. Android PDU parser assumes it's always there
        if (tpdu_len == pdu.length) {
            byte[] npdu = new byte[pdu.length + 1];
            npdu[0] = 0;
            System.arraycopy(pdu, 0, npdu, 1, pdu.length);
            return npdu;
        } else {
            return pdu;
        }
    }

    public void handle(org.ofono.Message.PropertyChanged s) {
        if (s.name.equals("State")) {
            String value = (String) s.value.getValue();
            if (value.equals("sent") || value.equals("failed")) {
                handleSendSmsComplete(s.getPath(), value);
            }
        }
    }

}
