package net.scintill.ril_ofono;

import android.telephony.Rlog;

import static com.android.internal.telephony.SmsConstants.ENCODING_16BIT;
import static com.android.internal.telephony.SmsConstants.ENCODING_7BIT;
import static com.android.internal.telephony.SmsConstants.ENCODING_8BIT;
import static com.android.internal.telephony.SmsConstants.ENCODING_KSC5601;
import static com.android.internal.telephony.SmsConstants.ENCODING_UNKNOWN;

public class AospUtils {
    private static final String LOG_TAG = "AospUtils";

    /*
     * Based on com.android.internal.telephony.gsm.SmsMessage in CM12
     *
     * Copyright (C) 2006 The Android Open Source Project
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *      http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
    public static int getEncodingType(int dataCodingScheme) {
        boolean hasMessageClass = false;
        boolean userDataCompressed = false;

        int encodingType = ENCODING_UNKNOWN;

        // Look up the data encoding scheme
        if ((dataCodingScheme & 0x80) == 0) {
            userDataCompressed = (0 != (dataCodingScheme & 0x20));
            hasMessageClass = (0 != (dataCodingScheme & 0x10));

            if (userDataCompressed) {
                Rlog.w(LOG_TAG, "4 - Unsupported SMS data coding scheme "
                        + "(compression) " + (dataCodingScheme & 0xff));
            } else {
                switch ((dataCodingScheme >> 2) & 0x3) {
                    case 0: // GSM 7 bit default alphabet
                        encodingType = ENCODING_7BIT;
                        break;

                    case 2: // UCS 2 (16bit)
                        encodingType = ENCODING_16BIT;
                        break;

                    case 1: // 8 bit data
                        //Support decoding the user data payload as pack GSM 8-bit (a GSM alphabet string
                        //that's stored in 8-bit unpacked format) characters.
                        encodingType = ENCODING_8BIT;
                        break;

                    case 3: // reserved
                        Rlog.w(LOG_TAG, "1 - Unsupported SMS data coding scheme "
                                + (dataCodingScheme & 0xff));
                        encodingType = ENCODING_8BIT;
                        break;
                }
            }
        } else if ((dataCodingScheme & 0xf0) == 0xf0) {
            hasMessageClass = true;
            userDataCompressed = false;

            if (0 == (dataCodingScheme & 0x04)) {
                // GSM 7 bit default alphabet
                encodingType = ENCODING_7BIT;
            } else {
                // 8 bit data
                encodingType = ENCODING_8BIT;
            }
        } else if ((dataCodingScheme & 0xF0) == 0xC0
                || (dataCodingScheme & 0xF0) == 0xD0
                || (dataCodingScheme & 0xF0) == 0xE0) {
            // 3GPP TS 23.038 V7.0.0 (2006-03) section 4

            // 0xC0 == 7 bit, don't store
            // 0xD0 == 7 bit, store
            // 0xE0 == UCS-2, store

            if ((dataCodingScheme & 0xF0) == 0xE0) {
                encodingType = ENCODING_16BIT;
            } else {
                encodingType = ENCODING_7BIT;
            }

            userDataCompressed = false;
            boolean active = ((dataCodingScheme & 0x08) == 0x08);
            // bit 0x04 reserved
        } else if ((dataCodingScheme & 0xC0) == 0x80) {
            // 3GPP TS 23.038 V7.0.0 (2006-03) section 4
            // 0x80..0xBF == Reserved coding groups
            if (dataCodingScheme == 0x84) {
                // This value used for KSC5601 by carriers in Korea.
                encodingType = ENCODING_KSC5601;
            } else {
                Rlog.w(LOG_TAG, "5 - Unsupported SMS data coding scheme "
                        + (dataCodingScheme & 0xff));
            }
        } else {
            Rlog.w(LOG_TAG, "3 - Unsupported SMS data coding scheme "
                    + (dataCodingScheme & 0xff));
        }

        return encodingType;
    }
}
