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

import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.SmsConstants;

import org.ofono.MessageManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Tests implements Runnable {

    private static final String TAG = RilOfono.TAG;

    SmsModule mSmsModule;

    public Tests(SmsModule ril) {
        mSmsModule = ril;
    }

    @Override
    public void run() {
        try {
            injectReceivedMessage("123", "Test123!", new Date(), false);
            injectReceivedMessage("456", "Test 🌠", new Date(System.currentTimeMillis()+1000000), false);
            injectReceivedMessage("777", "Immediate!", new Date(System.currentTimeMillis()), true);
        } catch (Throwable t) {
            Log.e(TAG, "Error running tests", t);
        }
    }

    private void injectReceivedMessage(String sender, String text, Date date, boolean immediate) throws Throwable {
        byte[] pdu = createReceivedMessage(sender, text, date, immediate);
        MessageManager.IncomingPdu s = new MessageManager.IncomingPdu("/", pdu, (byte)((pdu.length-1) & 0xff));
        mSmsModule.handle(s);
    }

    private byte[] createReceivedMessage(String sender, String contentText, Date date, boolean immediate) throws IOException, ReflectiveOperationException {
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
        return os.toByteArray();
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
