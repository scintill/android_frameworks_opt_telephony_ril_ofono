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
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SmsMessage;

import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.SmsResponse;

import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Variant;
import org.ofono.MessageManager;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import static com.android.internal.telephony.CommandException.Error.GENERIC_FAILURE;
import static com.android.internal.telephony.CommandException.Error.INVALID_PARAMETER;
import static net.scintill.ril_ofono.PropManager.getProp;
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
        RilOfono.sInstance.registerDbusSignal(MessageManager.IncomingMessage.class, this);
        RilOfono.sInstance.registerDbusSignal(MessageManager.ImmediateMessage.class, this);
        RilOfono.sInstance.registerDbusSignal(org.ofono.Message.PropertyChanged.class, this);
    }

    @RilMethod
    public void sendSMS(String smscPDUStr, String pduStr, final Message response) {
        Rlog.d(TAG, "sendSMS");
        // TODO gsm-specific?
        // TODO is there a way to preserve the whole pdu to ofono? should we check for special things that ofono won't do, and refuse to send if the PDU contains them?

        final SmsMessage msg = parseSmsPduStrs(smscPDUStr, pduStr);

        if (msg == null || msg.getRecipientAddress() == null || msg.getMessageBody() == null) {
            respondExc("sendSMS", response, INVALID_PARAMETER, null);
        } else {
            runOnDbusThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        synchronized (mMapSmsDbusPathToSenderCallback) {
                            // TODO timeout on this method? at least we're not on the main thread,
                            // but we could block anything else trying to get on the dbus thread
                            Path sentMessage = mMessenger.SendMessage(msg.getRecipientAddress(), msg.getMessageBody());
                            mMapSmsDbusPathToSenderCallback.put(sentMessage.getPath(), response);
                        }
                    } catch (Throwable t) {
                        Rlog.e(TAG, "Error sending msg", t);
                        respondExc("sendSMS", response, GENERIC_FAILURE, null);
                    }
                }
            });
        }
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

    private void handleIncomingMessage(String content, Map<String, Variant> info) {
        String dateStr = (String) info.get("SentTime").getValue();
        String sender = (String) info.get("Sender").getValue();

        //Rlog.d(TAG, "handleIncomingMessage "+privStr(sender)+" "+dateStr+" "+privStr(content));

        Date date = Utils.parseOfonoDate(dateStr);
        if (date == null) {
            Rlog.e(TAG, "error parsing SMS date "+dateStr);
            date = new Date();
        }

        final Object msg = createReceivedMessage(sender, content, date, getProp(info, "Immediate", false));
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mSmsRegistrants.notifyResult(msg);
            }
        });
    }

    public void handle(MessageManager.IncomingMessage s) {
        handleIncomingMessage(s.message, s.info);
    }

    public void handle(MessageManager.ImmediateMessage s) {
        s.info.put("Immediate", new Variant<>(true));
        handleIncomingMessage(s.message, s.info);
    }

    public void handle(org.ofono.Message.PropertyChanged s) {
        if (s.name.equals("State")) {
            String value = (String) s.value.getValue();
            if (value.equals("sent") || value.equals("failed")) {
                handleSendSmsComplete(s.getPath(), value);
            }
        }
    }

    private SmsMessage parseSmsPduStrs(String smscPDUStr, String pduStr) {
        if (smscPDUStr == null) {
            smscPDUStr = "00"; // see PduParser; means no smsc
        }
        try {
            return SmsMessage.createFromPdu(IccUtils.hexStringToBytes(smscPDUStr + pduStr), SmsConstants.FORMAT_3GPP);
        } catch (Throwable t) {
            // SmsMessage should have logged information about the error
            return null;
        }
    }

    private SmsMessage createReceivedMessage(String sender, String contentText, Date date, boolean immediate) {
        try {
            // see SmsMessage#parsePdu
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            os.write(new byte[] {
                    0x00, // null sc address
                    0x00, // deliver type. no reply path or user header
            });
            byte[] bcdSender = PhoneNumberUtils.networkPortionToCalledPartyBCD(sender);
            os.write((bcdSender.length - 1) * 2); // BCD digit count, excluding TOA.
            os.write(bcdSender);

            // build a submit pdu so it will encode the message for us
            // it turned out to not be as convenient as I hoped, but probably still better than
            // writing/copying here
            com.android.internal.telephony.gsm.SmsMessage.SubmitPdu submitPduOb =
                    com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(null, "0", contentText, false);
            byte[] submitPdu = new byte[1 + submitPduOb.encodedMessage.length];
            submitPdu[0] = 0x00; // null sc adddr, so it will parse below
            System.arraycopy(submitPduOb.encodedMessage, 0, submitPdu, 1, submitPduOb.encodedMessage.length);
            com.android.internal.telephony.gsm.SmsMessage msg =
                    com.android.internal.telephony.gsm.SmsMessage.createFromPdu(submitPdu);
            if (msg == null) throw new RuntimeException("unable to parse submit pdu to create deliver pdu");

            // finish writing the deliver
            int dataCodingScheme = Utils.callPrivateMethod(msg, Integer.class, "getDataCodingScheme");
            os.write(new byte[]{
                    0x00, // protocol identifier
                    (byte) (dataCodingScheme | (immediate ? 0x10 : 0))
            });
            os.write(getScTimestamp(date));
            byte[] payload = msg.getUserData();
            os.write(AospUtils.getEncodingType(dataCodingScheme) != SmsConstants.ENCODING_7BIT ?
                    payload.length : // octet length
                    // septet length - we can't tell how many meaningful septets there are, but
                    // I think in the case of 7bit it will always be the length of the string
                    contentText.length());
            os.write(payload);
            return SmsMessage.createFromPdu(os.toByteArray(), SmsConstants.FORMAT_3GPP);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] getScTimestamp(Date d) {
        // opposite of SmsMessage#getSCTimestampMillis()
        // value is BCD nibble-swapped ymdhmszz (z = zone)
        SimpleDateFormat fmt = new SimpleDateFormat("ssmmHHddMMyy", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        StringBuilder b = new StringBuilder(fmt.format(d));
        b.reverse();
        b.append("00"); // TODO preserve real tz?
        return IccUtils.hexStringToBytes(b.toString());
    }

}
