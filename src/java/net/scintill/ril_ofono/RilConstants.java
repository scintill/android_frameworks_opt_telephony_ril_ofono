/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright 2017 Joey Hewitt <joey@joeyhewitt.com>
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

/*
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

/*
 * ril_ofono: Started by copying android_frameworks_base/telephony/java/com/android/internal/telephony/RILConstants.java.
 */

package net.scintill.ril_ofono;

public final class RilConstants {
    // From the top of ril.cpp
    static final int RIL_ERRNO_INVALID_RESPONSE = -1;

    static final int MAX_INT = 0x7FFFFFFF;

    // from RIL_Errno
    static final int SUCCESS = 0;
    static final int RADIO_NOT_AVAILABLE = 1;              /* If radio did not start or is resetting */
    static final int GENERIC_FAILURE = 2;
    static final int PASSWORD_INCORRECT = 3;               /* for PIN/PIN2 methods only! */
    static final int SIM_PIN2 = 4;                         /* Operation requires SIM PIN2 to be entered */
    static final int SIM_PUK2 = 5;                         /* Operation requires SIM PIN2 to be entered */
    static final int REQUEST_NOT_SUPPORTED = 6;
    static final int REQUEST_CANCELLED = 7;
    static final int OP_NOT_ALLOWED_DURING_VOICE_CALL = 8; /* data operation is not allowed during voice call in
                                                 class C */
    static final int OP_NOT_ALLOWED_BEFORE_REG_NW = 9;     /* request is not allowed before device registers to
                                                 network */
    static final int SMS_SEND_FAIL_RETRY = 10;             /* send sms fail and need retry */
    static final int SIM_ABSENT = 11;                      /* ICC card is absent */
    static final int SUBSCRIPTION_NOT_AVAILABLE = 12;      /* fail to find CDMA subscription from specified
                                                 location */
    static final int MODE_NOT_SUPPORTED = 13;              /* HW does not support preferred network type */
    static final int FDN_CHECK_FAILURE = 14;               /* send operation barred error when FDN is enabled */
    static final int ILLEGAL_SIM_OR_ME = 15;               /* network selection failure due
                                                 to wrong SIM/ME and no
                                                 retries needed */
    static final int MISSING_RESOURCE = 16;                /* no logical channel available */
    static final int NO_SUCH_ELEMENT = 17;                 /* application not found on SIM */
    static final int DIAL_MODIFIED_TO_USSD = 18;           /* DIAL request modified to USSD */
    static final int DIAL_MODIFIED_TO_SS = 19;             /* DIAL request modified to SS */
    static final int DIAL_MODIFIED_TO_DIAL = 20;           /* DIAL request modified to DIAL with different data*/
    static final int USSD_MODIFIED_TO_DIAL = 21;           /* USSD request modified to DIAL */
    static final int USSD_MODIFIED_TO_SS = 22;             /* USSD request modified to SS */
    static final int USSD_MODIFIED_TO_USSD = 23;           /* USSD request modified to different USSD request */
    static final int SS_MODIFIED_TO_DIAL = 24;             /* SS request modified to DIAL */
    static final int SS_MODIFIED_TO_USSD = 25;             /* SS request modified to USSD */
    static final int SUBSCRIPTION_NOT_SUPPORTED = 26;      /* Subscription not supported */
    static final int SS_MODIFIED_TO_SS = 27;               /* SS request modified to different SS request */


    /* NETWORK_MODE_* See ril.h RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE */
    static final int NETWORK_MODE_WCDMA_PREF     = 0; /* GSM/WCDMA (WCDMA preferred) */
    static final int NETWORK_MODE_GSM_ONLY       = 1; /* GSM only */
    static final int NETWORK_MODE_WCDMA_ONLY     = 2; /* WCDMA only */
    static final int NETWORK_MODE_GSM_UMTS       = 3; /* GSM/WCDMA (auto mode, according to PRL)
                                            AVAILABLE Application Settings menu*/
    static final int NETWORK_MODE_CDMA           = 4; /* CDMA and EvDo (auto mode, according to PRL)
                                            AVAILABLE Application Settings menu*/
    static final int NETWORK_MODE_CDMA_NO_EVDO   = 5; /* CDMA only */
    static final int NETWORK_MODE_EVDO_NO_CDMA   = 6; /* EvDo only */
    static final int NETWORK_MODE_GLOBAL         = 7; /* GSM/WCDMA, CDMA, and EvDo (auto mode, according to PRL)
                                            AVAILABLE Application Settings menu*/
    static final int NETWORK_MODE_LTE_CDMA_EVDO  = 8; /* LTE, CDMA and EvDo */
    static final int NETWORK_MODE_LTE_GSM_WCDMA  = 9; /* LTE, GSM/WCDMA */
    static final int NETWORK_MODE_LTE_CDMA_EVDO_GSM_WCDMA = 10; /* LTE, CDMA, EvDo, GSM/WCDMA */
    static final int NETWORK_MODE_LTE_ONLY       = 11; /* LTE Only mode. */
    static final int NETWORK_MODE_LTE_WCDMA      = 12; /* LTE/WCDMA */

    static final int CDMA_CELL_BROADCAST_SMS_DISABLED = 1;
    static final int CDMA_CELL_BROADCAST_SMS_ENABLED  = 0;

    static final int NO_PHONE = 0;
    static final int GSM_PHONE = 1;
    static final int CDMA_PHONE = 2;
    static final int SIP_PHONE  = 3;
    static final int THIRD_PARTY_PHONE = 4;
    static final int IMS_PHONE = 5;

    static final int LTE_ON_CDMA_UNKNOWN = -1;
    static final int LTE_ON_CDMA_FALSE = 0;
    static final int LTE_ON_CDMA_TRUE = 1;

    static final int CDM_TTY_MODE_DISABLED = 0;
    static final int CDM_TTY_MODE_ENABLED = 1;

    static final int CDM_TTY_FULL_MODE = 1;
    static final int CDM_TTY_HCO_MODE = 2;
    static final int CDM_TTY_VCO_MODE = 3;

    /* Setup a packet data connection. See ril.h RIL_REQUEST_SETUP_DATA_CALL */
    static final int SETUP_DATA_TECH_CDMA      = 0;
    static final int SETUP_DATA_TECH_GSM       = 1;

    static final int SETUP_DATA_AUTH_NONE      = 0;
    static final int SETUP_DATA_AUTH_PAP       = 1;
    static final int SETUP_DATA_AUTH_CHAP      = 2;
    static final int SETUP_DATA_AUTH_PAP_CHAP  = 3;

    String SETUP_DATA_PROTOCOL_IP     = "IP";
    String SETUP_DATA_PROTOCOL_IPV6   = "IPV6";
    String SETUP_DATA_PROTOCOL_IPV4V6 = "IPV4V6";

    /* Deactivate data call reasons */
    static final int DEACTIVATE_REASON_NONE = 0;
    static final int DEACTIVATE_REASON_RADIO_OFF = 1;
    static final int DEACTIVATE_REASON_PDP_RESET = 2;

    /* NV config radio reset types. */
    static final int NV_CONFIG_RELOAD_RESET = 1;
    static final int NV_CONFIG_ERASE_RESET = 2;
    static final int NV_CONFIG_FACTORY_RESET = 3;

/*
cat include/telephony/ril.h | \
   egrep '^#define' | \
   sed -re 's/^#define +([^ ]+)* +([^ ]+)/    static final int \1 = \2;/' \
   >>java/android/com.android.internal.telephony/gsm/RILConstants.java
*/

    /**
     * No restriction at all including voice/SMS/USSD/SS/AV64
     * and packet data.
     */
    static final int RIL_RESTRICTED_STATE_NONE = 0x00;
    /**
     * Block emergency call due to restriction.
     * But allow all normal voice/SMS/USSD/SS/AV64.
     */
    static final int RIL_RESTRICTED_STATE_CS_EMERGENCY = 0x01;
    /**
     * Block all normal voice/SMS/USSD/SS/AV64 due to restriction.
     * Only Emergency call allowed.
     */
    static final int RIL_RESTRICTED_STATE_CS_NORMAL = 0x02;
    /**
     * Block all voice/SMS/USSD/SS/AV64
     * including emergency call due to restriction.
     */
    static final int RIL_RESTRICTED_STATE_CS_ALL = 0x04;
    /**
     * Block packet data access due to restriction.
     */
    static final int RIL_RESTRICTED_STATE_PS_ALL = 0x10;

    /** Data profile for RIL_REQUEST_SETUP_DATA_CALL */
    public static final int DATA_PROFILE_DEFAULT   = 0;
    public static final int DATA_PROFILE_TETHERED  = 1;
    public static final int DATA_PROFILE_IMS       = 2;
    public static final int DATA_PROFILE_FOTA      = 3;
    public static final int DATA_PROFILE_CBS       = 4;
    public static final int DATA_PROFILE_OEM_BASE  = 1000;
    public static final int DATA_PROFILE_INVALID   = 0xFFFFFFFF;

    static final int RIL_REQUEST_GET_SIM_STATUS = 1;
    static final int RIL_REQUEST_ENTER_SIM_PIN = 2;
    static final int RIL_REQUEST_ENTER_SIM_PUK = 3;
    static final int RIL_REQUEST_ENTER_SIM_PIN2 = 4;
    static final int RIL_REQUEST_ENTER_SIM_PUK2 = 5;
    static final int RIL_REQUEST_CHANGE_SIM_PIN = 6;
    static final int RIL_REQUEST_CHANGE_SIM_PIN2 = 7;
    static final int RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION = 8;
    static final int RIL_REQUEST_GET_CURRENT_CALLS = 9;
    static final int RIL_REQUEST_DIAL = 10;
    static final int RIL_REQUEST_GET_IMSI = 11;
    static final int RIL_REQUEST_HANGUP = 12;
    static final int RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND = 13;
    static final int RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND = 14;
    static final int RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE = 15;
    static final int RIL_REQUEST_CONFERENCE = 16;
    static final int RIL_REQUEST_UDUB = 17;
    static final int RIL_REQUEST_LAST_CALL_FAIL_CAUSE = 18;
    static final int RIL_REQUEST_SIGNAL_STRENGTH = 19;
    static final int RIL_REQUEST_VOICE_REGISTRATION_STATE = 20;
    static final int RIL_REQUEST_DATA_REGISTRATION_STATE = 21;
    static final int RIL_REQUEST_OPERATOR = 22;
    static final int RIL_REQUEST_RADIO_POWER = 23;
    static final int RIL_REQUEST_DTMF = 24;
    static final int RIL_REQUEST_SEND_SMS = 25;
    static final int RIL_REQUEST_SEND_SMS_EXPECT_MORE = 26;
    static final int RIL_REQUEST_SETUP_DATA_CALL = 27;
    static final int RIL_REQUEST_SIM_IO = 28;
    static final int RIL_REQUEST_SEND_USSD = 29;
    static final int RIL_REQUEST_CANCEL_USSD = 30;
    static final int RIL_REQUEST_GET_CLIR = 31;
    static final int RIL_REQUEST_SET_CLIR = 32;
    static final int RIL_REQUEST_QUERY_CALL_FORWARD_STATUS = 33;
    static final int RIL_REQUEST_SET_CALL_FORWARD = 34;
    static final int RIL_REQUEST_QUERY_CALL_WAITING = 35;
    static final int RIL_REQUEST_SET_CALL_WAITING = 36;
    static final int RIL_REQUEST_SMS_ACKNOWLEDGE = 37;
    static final int RIL_REQUEST_GET_IMEI = 38;
    static final int RIL_REQUEST_GET_IMEISV = 39;
    static final int RIL_REQUEST_ANSWER = 40;
    static final int RIL_REQUEST_DEACTIVATE_DATA_CALL = 41;
    static final int RIL_REQUEST_QUERY_FACILITY_LOCK = 42;
    static final int RIL_REQUEST_SET_FACILITY_LOCK = 43;
    static final int RIL_REQUEST_CHANGE_BARRING_PASSWORD = 44;
    static final int RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE = 45;
    static final int RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC = 46;
    static final int RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL = 47;
    static final int RIL_REQUEST_QUERY_AVAILABLE_NETWORKS = 48;
    static final int RIL_REQUEST_DTMF_START = 49;
    static final int RIL_REQUEST_DTMF_STOP = 50;
    static final int RIL_REQUEST_BASEBAND_VERSION = 51;
    static final int RIL_REQUEST_SEPARATE_CONNECTION = 52;
    static final int RIL_REQUEST_SET_MUTE = 53;
    static final int RIL_REQUEST_GET_MUTE = 54;
    static final int RIL_REQUEST_QUERY_CLIP = 55;
    static final int RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE = 56;
    static final int RIL_REQUEST_DATA_CALL_LIST = 57;
    static final int RIL_REQUEST_RESET_RADIO = 58;
    static final int RIL_REQUEST_OEM_HOOK_RAW = 59;
    static final int RIL_REQUEST_OEM_HOOK_STRINGS = 60;
    static final int RIL_REQUEST_SCREEN_STATE = 61;
    static final int RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION = 62;
    static final int RIL_REQUEST_WRITE_SMS_TO_SIM = 63;
    static final int RIL_REQUEST_DELETE_SMS_ON_SIM = 64;
    static final int RIL_REQUEST_SET_BAND_MODE = 65;
    static final int RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE = 66;
    static final int RIL_REQUEST_STK_GET_PROFILE = 67;
    static final int RIL_REQUEST_STK_SET_PROFILE = 68;
    static final int RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND = 69;
    static final int RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE = 70;
    static final int RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM = 71;
    static final int RIL_REQUEST_EXPLICIT_CALL_TRANSFER = 72;
    static final int RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE = 73;
    static final int RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE = 74;
    static final int RIL_REQUEST_GET_NEIGHBORING_CELL_IDS = 75;
    static final int RIL_REQUEST_SET_LOCATION_UPDATES = 76;
    static final int RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE = 77;
    static final int RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE = 78;
    static final int RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE = 79;
    static final int RIL_REQUEST_SET_TTY_MODE = 80;
    static final int RIL_REQUEST_QUERY_TTY_MODE = 81;
    static final int RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE = 82;
    static final int RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE = 83;
    static final int RIL_REQUEST_CDMA_FLASH = 84;
    static final int RIL_REQUEST_CDMA_BURST_DTMF = 85;
    static final int RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY = 86;
    static final int RIL_REQUEST_CDMA_SEND_SMS = 87;
    static final int RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE = 88;
    static final int RIL_REQUEST_GSM_GET_BROADCAST_CONFIG = 89;
    static final int RIL_REQUEST_GSM_SET_BROADCAST_CONFIG = 90;
    static final int RIL_REQUEST_GSM_BROADCAST_ACTIVATION = 91;
    static final int RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG = 92;
    static final int RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG = 93;
    static final int RIL_REQUEST_CDMA_BROADCAST_ACTIVATION = 94;
    static final int RIL_REQUEST_CDMA_SUBSCRIPTION = 95;
    static final int RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM = 96;
    static final int RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM = 97;
    static final int RIL_REQUEST_DEVICE_IDENTITY = 98;
    static final int RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE = 99;
    static final int RIL_REQUEST_GET_SMSC_ADDRESS = 100;
    static final int RIL_REQUEST_SET_SMSC_ADDRESS = 101;
    static final int RIL_REQUEST_REPORT_SMS_MEMORY_STATUS = 102;
    static final int RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING = 103;
    static final int RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE = 104;
    static final int RIL_REQUEST_ISIM_AUTHENTICATION = 105;
    static final int RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU = 106;
    static final int RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS = 107;
    static final int RIL_REQUEST_VOICE_RADIO_TECH = 108;
    static final int RIL_REQUEST_GET_CELL_INFO_LIST = 109;
    static final int RIL_REQUEST_SET_UNSOL_CELL_INFO_LIST_RATE = 110;
    static final int RIL_REQUEST_SET_INITIAL_ATTACH_APN = 111;
    static final int RIL_REQUEST_IMS_REGISTRATION_STATE = 112;
    static final int RIL_REQUEST_IMS_SEND_SMS = 113;
    static final int RIL_REQUEST_SIM_TRANSMIT_APDU_BASIC = 114;
    static final int RIL_REQUEST_SIM_OPEN_CHANNEL = 115;
    static final int RIL_REQUEST_SIM_CLOSE_CHANNEL = 116;
    static final int RIL_REQUEST_SIM_TRANSMIT_APDU_CHANNEL = 117;
    static final int RIL_REQUEST_NV_READ_ITEM = 118;
    static final int RIL_REQUEST_NV_WRITE_ITEM = 119;
    static final int RIL_REQUEST_NV_WRITE_CDMA_PRL = 120;
    static final int RIL_REQUEST_NV_RESET_CONFIG = 121;
    static final int RIL_REQUEST_SET_UICC_SUBSCRIPTION = 122;
    static final int RIL_REQUEST_ALLOW_DATA = 123;
    static final int RIL_REQUEST_GET_HARDWARE_CONFIG = 124;
    static final int RIL_REQUEST_SIM_AUTHENTICATION = 125;
    static final int RIL_REQUEST_GET_DC_RT_INFO = 126;
    static final int RIL_REQUEST_SET_DC_RT_INFO_RATE = 127;
    static final int RIL_REQUEST_SET_DATA_PROFILE = 128;
    static final int RIL_REQUEST_SHUTDOWN = 129;
    static final int RIL_REQUEST_GET_RADIO_CAPABILITY = 130;
    static final int RIL_REQUEST_SET_RADIO_CAPABILITY = 131;

    static final int RIL_UNSOL_RESPONSE_BASE = 1000;
    static final int RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED = 1000;
    static final int RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED = 1001;
    static final int RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED = 1002;
    static final int RIL_UNSOL_RESPONSE_NEW_SMS = 1003;
    static final int RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT = 1004;
    static final int RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM = 1005;
    static final int RIL_UNSOL_ON_USSD = 1006;
    static final int RIL_UNSOL_ON_USSD_REQUEST = 1007;
    static final int RIL_UNSOL_NITZ_TIME_RECEIVED = 1008;
    static final int RIL_UNSOL_SIGNAL_STRENGTH = 1009;
    static final int RIL_UNSOL_DATA_CALL_LIST_CHANGED = 1010;
    static final int RIL_UNSOL_SUPP_SVC_NOTIFICATION = 1011;
    static final int RIL_UNSOL_STK_SESSION_END = 1012;
    static final int RIL_UNSOL_STK_PROACTIVE_COMMAND = 1013;
    static final int RIL_UNSOL_STK_EVENT_NOTIFY = 1014;
    static final int RIL_UNSOL_STK_CALL_SETUP = 1015;
    static final int RIL_UNSOL_SIM_SMS_STORAGE_FULL = 1016;
    static final int RIL_UNSOL_SIM_REFRESH = 1017;
    static final int RIL_UNSOL_CALL_RING = 1018;
    static final int RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED = 1019;
    static final int RIL_UNSOL_RESPONSE_CDMA_NEW_SMS = 1020;
    static final int RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS = 1021;
    static final int RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL = 1022;
    static final int RIL_UNSOL_RESTRICTED_STATE_CHANGED = 1023;
    static final int RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE = 1024;
    static final int RIL_UNSOL_CDMA_CALL_WAITING = 1025;
    static final int RIL_UNSOL_CDMA_OTA_PROVISION_STATUS = 1026;
    static final int RIL_UNSOL_CDMA_INFO_REC = 1027;
    static final int RIL_UNSOL_OEM_HOOK_RAW = 1028;
    static final int RIL_UNSOL_RINGBACK_TONE = 1029;
    static final int RIL_UNSOL_RESEND_INCALL_MUTE = 1030;
    static final int RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED = 1031;
    static final int RIL_UNSOl_CDMA_PRL_CHANGED = 1032;
    static final int RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE = 1033;
    static final int RIL_UNSOL_RIL_CONNECTED = 1034;
    static final int RIL_UNSOL_VOICE_RADIO_TECH_CHANGED = 1035;
    static final int RIL_UNSOL_CELL_INFO_LIST = 1036;
    static final int RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED = 1037;
    static final int RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED = 1038;
    static final int RIL_UNSOL_SRVCC_STATE_NOTIFY = 1039;
    static final int RIL_UNSOL_HARDWARE_CONFIG_CHANGED = 1040;
    static final int RIL_UNSOL_DC_RT_INFO_CHANGED = 1041;
    static final int RIL_UNSOL_RADIO_CAPABILITY = 1042;
    static final int RIL_UNSOL_ON_SS = 1043;
    static final int RIL_UNSOL_STK_CC_ALPHA_NOTIFY = 1044;

    static final int RADIO_STATE_OFF = 0;
    static final int RADIO_STATE_UNAVAILABLE = 1;
	/* static final intermediate values were deprecated */
    static final int RADIO_STATE_ON = 10;


	/* string translation methods from android_frameworks_opt_telephony/src/java/com/android/internal/telephony/RIL.java */
    static String
    requestToString(int request) {
/*
 cat libs/telephony/ril_commands.h \
 | egrep "^ *{RIL_" \
 | sed -re 's/\{RIL_([^,]+),[^,]+,([^}]+).+/case RIL_\1: return "\1";/'
*/
        switch(request) {
            case RIL_REQUEST_GET_SIM_STATUS: return "GET_SIM_STATUS";
            case RIL_REQUEST_ENTER_SIM_PIN: return "ENTER_SIM_PIN";
            case RIL_REQUEST_ENTER_SIM_PUK: return "ENTER_SIM_PUK";
            case RIL_REQUEST_ENTER_SIM_PIN2: return "ENTER_SIM_PIN2";
            case RIL_REQUEST_ENTER_SIM_PUK2: return "ENTER_SIM_PUK2";
            case RIL_REQUEST_CHANGE_SIM_PIN: return "CHANGE_SIM_PIN";
            case RIL_REQUEST_CHANGE_SIM_PIN2: return "CHANGE_SIM_PIN2";
            case RIL_REQUEST_ENTER_NETWORK_DEPERSONALIZATION: return "ENTER_NETWORK_DEPERSONALIZATION";
            case RIL_REQUEST_GET_CURRENT_CALLS: return "GET_CURRENT_CALLS";
            case RIL_REQUEST_DIAL: return "DIAL";
            case RIL_REQUEST_GET_IMSI: return "GET_IMSI";
            case RIL_REQUEST_HANGUP: return "HANGUP";
            case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND: return "HANGUP_WAITING_OR_BACKGROUND";
            case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND: return "HANGUP_FOREGROUND_RESUME_BACKGROUND";
            case RIL_REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE: return "REQUEST_SWITCH_WAITING_OR_HOLDING_AND_ACTIVE";
            case RIL_REQUEST_CONFERENCE: return "CONFERENCE";
            case RIL_REQUEST_UDUB: return "UDUB";
            case RIL_REQUEST_LAST_CALL_FAIL_CAUSE: return "LAST_CALL_FAIL_CAUSE";
            case RIL_REQUEST_SIGNAL_STRENGTH: return "SIGNAL_STRENGTH";
            case RIL_REQUEST_VOICE_REGISTRATION_STATE: return "VOICE_REGISTRATION_STATE";
            case RIL_REQUEST_DATA_REGISTRATION_STATE: return "DATA_REGISTRATION_STATE";
            case RIL_REQUEST_OPERATOR: return "OPERATOR";
            case RIL_REQUEST_RADIO_POWER: return "RADIO_POWER";
            case RIL_REQUEST_DTMF: return "DTMF";
            case RIL_REQUEST_SEND_SMS: return "SEND_SMS";
            case RIL_REQUEST_SEND_SMS_EXPECT_MORE: return "SEND_SMS_EXPECT_MORE";
            case RIL_REQUEST_SETUP_DATA_CALL: return "SETUP_DATA_CALL";
            case RIL_REQUEST_SIM_IO: return "SIM_IO";
            case RIL_REQUEST_SEND_USSD: return "SEND_USSD";
            case RIL_REQUEST_CANCEL_USSD: return "CANCEL_USSD";
            case RIL_REQUEST_GET_CLIR: return "GET_CLIR";
            case RIL_REQUEST_SET_CLIR: return "SET_CLIR";
            case RIL_REQUEST_QUERY_CALL_FORWARD_STATUS: return "QUERY_CALL_FORWARD_STATUS";
            case RIL_REQUEST_SET_CALL_FORWARD: return "SET_CALL_FORWARD";
            case RIL_REQUEST_QUERY_CALL_WAITING: return "QUERY_CALL_WAITING";
            case RIL_REQUEST_SET_CALL_WAITING: return "SET_CALL_WAITING";
            case RIL_REQUEST_SMS_ACKNOWLEDGE: return "SMS_ACKNOWLEDGE";
            case RIL_REQUEST_GET_IMEI: return "GET_IMEI";
            case RIL_REQUEST_GET_IMEISV: return "GET_IMEISV";
            case RIL_REQUEST_ANSWER: return "ANSWER";
            case RIL_REQUEST_DEACTIVATE_DATA_CALL: return "DEACTIVATE_DATA_CALL";
            case RIL_REQUEST_QUERY_FACILITY_LOCK: return "QUERY_FACILITY_LOCK";
            case RIL_REQUEST_SET_FACILITY_LOCK: return "SET_FACILITY_LOCK";
            case RIL_REQUEST_CHANGE_BARRING_PASSWORD: return "CHANGE_BARRING_PASSWORD";
            case RIL_REQUEST_QUERY_NETWORK_SELECTION_MODE: return "QUERY_NETWORK_SELECTION_MODE";
            case RIL_REQUEST_SET_NETWORK_SELECTION_AUTOMATIC: return "SET_NETWORK_SELECTION_AUTOMATIC";
            case RIL_REQUEST_SET_NETWORK_SELECTION_MANUAL: return "SET_NETWORK_SELECTION_MANUAL";
            case RIL_REQUEST_QUERY_AVAILABLE_NETWORKS : return "QUERY_AVAILABLE_NETWORKS ";
            case RIL_REQUEST_DTMF_START: return "DTMF_START";
            case RIL_REQUEST_DTMF_STOP: return "DTMF_STOP";
            case RIL_REQUEST_BASEBAND_VERSION: return "BASEBAND_VERSION";
            case RIL_REQUEST_SEPARATE_CONNECTION: return "SEPARATE_CONNECTION";
            case RIL_REQUEST_SET_MUTE: return "SET_MUTE";
            case RIL_REQUEST_GET_MUTE: return "GET_MUTE";
            case RIL_REQUEST_QUERY_CLIP: return "QUERY_CLIP";
            case RIL_REQUEST_LAST_DATA_CALL_FAIL_CAUSE: return "LAST_DATA_CALL_FAIL_CAUSE";
            case RIL_REQUEST_DATA_CALL_LIST: return "DATA_CALL_LIST";
            case RIL_REQUEST_RESET_RADIO: return "RESET_RADIO";
            case RIL_REQUEST_OEM_HOOK_RAW: return "OEM_HOOK_RAW";
            case RIL_REQUEST_OEM_HOOK_STRINGS: return "OEM_HOOK_STRINGS";
            case RIL_REQUEST_SCREEN_STATE: return "SCREEN_STATE";
            case RIL_REQUEST_SET_SUPP_SVC_NOTIFICATION: return "SET_SUPP_SVC_NOTIFICATION";
            case RIL_REQUEST_WRITE_SMS_TO_SIM: return "WRITE_SMS_TO_SIM";
            case RIL_REQUEST_DELETE_SMS_ON_SIM: return "DELETE_SMS_ON_SIM";
            case RIL_REQUEST_SET_BAND_MODE: return "SET_BAND_MODE";
            case RIL_REQUEST_QUERY_AVAILABLE_BAND_MODE: return "QUERY_AVAILABLE_BAND_MODE";
            case RIL_REQUEST_STK_GET_PROFILE: return "REQUEST_STK_GET_PROFILE";
            case RIL_REQUEST_STK_SET_PROFILE: return "REQUEST_STK_SET_PROFILE";
            case RIL_REQUEST_STK_SEND_ENVELOPE_COMMAND: return "REQUEST_STK_SEND_ENVELOPE_COMMAND";
            case RIL_REQUEST_STK_SEND_TERMINAL_RESPONSE: return "REQUEST_STK_SEND_TERMINAL_RESPONSE";
            case RIL_REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM: return "REQUEST_STK_HANDLE_CALL_SETUP_REQUESTED_FROM_SIM";
            case RIL_REQUEST_EXPLICIT_CALL_TRANSFER: return "REQUEST_EXPLICIT_CALL_TRANSFER";
            case RIL_REQUEST_SET_PREFERRED_NETWORK_TYPE: return "REQUEST_SET_PREFERRED_NETWORK_TYPE";
            case RIL_REQUEST_GET_PREFERRED_NETWORK_TYPE: return "REQUEST_GET_PREFERRED_NETWORK_TYPE";
            case RIL_REQUEST_GET_NEIGHBORING_CELL_IDS: return "REQUEST_GET_NEIGHBORING_CELL_IDS";
            case RIL_REQUEST_SET_LOCATION_UPDATES: return "REQUEST_SET_LOCATION_UPDATES";
            case RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE: return "RIL_REQUEST_CDMA_SET_SUBSCRIPTION_SOURCE";
            case RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE: return "RIL_REQUEST_CDMA_SET_ROAMING_PREFERENCE";
            case RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE: return "RIL_REQUEST_CDMA_QUERY_ROAMING_PREFERENCE";
            case RIL_REQUEST_SET_TTY_MODE: return "RIL_REQUEST_SET_TTY_MODE";
            case RIL_REQUEST_QUERY_TTY_MODE: return "RIL_REQUEST_QUERY_TTY_MODE";
            case RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE: return "RIL_REQUEST_CDMA_SET_PREFERRED_VOICE_PRIVACY_MODE";
            case RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE: return "RIL_REQUEST_CDMA_QUERY_PREFERRED_VOICE_PRIVACY_MODE";
            case RIL_REQUEST_CDMA_FLASH: return "RIL_REQUEST_CDMA_FLASH";
            case RIL_REQUEST_CDMA_BURST_DTMF: return "RIL_REQUEST_CDMA_BURST_DTMF";
            case RIL_REQUEST_CDMA_SEND_SMS: return "RIL_REQUEST_CDMA_SEND_SMS";
            case RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE: return "RIL_REQUEST_CDMA_SMS_ACKNOWLEDGE";
            case RIL_REQUEST_GSM_GET_BROADCAST_CONFIG: return "RIL_REQUEST_GSM_GET_BROADCAST_CONFIG";
            case RIL_REQUEST_GSM_SET_BROADCAST_CONFIG: return "RIL_REQUEST_GSM_SET_BROADCAST_CONFIG";
            case RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG: return "RIL_REQUEST_CDMA_GET_BROADCAST_CONFIG";
            case RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG: return "RIL_REQUEST_CDMA_SET_BROADCAST_CONFIG";
            case RIL_REQUEST_GSM_BROADCAST_ACTIVATION: return "RIL_REQUEST_GSM_BROADCAST_ACTIVATION";
            case RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY: return "RIL_REQUEST_CDMA_VALIDATE_AND_WRITE_AKEY";
            case RIL_REQUEST_CDMA_BROADCAST_ACTIVATION: return "RIL_REQUEST_CDMA_BROADCAST_ACTIVATION";
            case RIL_REQUEST_CDMA_SUBSCRIPTION: return "RIL_REQUEST_CDMA_SUBSCRIPTION";
            case RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM: return "RIL_REQUEST_CDMA_WRITE_SMS_TO_RUIM";
            case RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM: return "RIL_REQUEST_CDMA_DELETE_SMS_ON_RUIM";
            case RIL_REQUEST_DEVICE_IDENTITY: return "RIL_REQUEST_DEVICE_IDENTITY";
            case RIL_REQUEST_GET_SMSC_ADDRESS: return "RIL_REQUEST_GET_SMSC_ADDRESS";
            case RIL_REQUEST_SET_SMSC_ADDRESS: return "RIL_REQUEST_SET_SMSC_ADDRESS";
            case RIL_REQUEST_EXIT_EMERGENCY_CALLBACK_MODE: return "REQUEST_EXIT_EMERGENCY_CALLBACK_MODE";
            case RIL_REQUEST_REPORT_SMS_MEMORY_STATUS: return "RIL_REQUEST_REPORT_SMS_MEMORY_STATUS";
            case RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING: return "RIL_REQUEST_REPORT_STK_SERVICE_IS_RUNNING";
            case RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE: return "RIL_REQUEST_CDMA_GET_SUBSCRIPTION_SOURCE";
            case RIL_REQUEST_ISIM_AUTHENTICATION: return "RIL_REQUEST_ISIM_AUTHENTICATION";
            case RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU: return "RIL_REQUEST_ACKNOWLEDGE_INCOMING_GSM_SMS_WITH_PDU";
            case RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS: return "RIL_REQUEST_STK_SEND_ENVELOPE_WITH_STATUS";
            case RIL_REQUEST_VOICE_RADIO_TECH: return "RIL_REQUEST_VOICE_RADIO_TECH";
            case RIL_REQUEST_GET_CELL_INFO_LIST: return "RIL_REQUEST_GET_CELL_INFO_LIST";
            case RIL_REQUEST_SET_UNSOL_CELL_INFO_LIST_RATE: return "RIL_REQUEST_SET_CELL_INFO_LIST_RATE";
            case RIL_REQUEST_SET_INITIAL_ATTACH_APN: return "RIL_REQUEST_SET_INITIAL_ATTACH_APN";
            case RIL_REQUEST_SET_DATA_PROFILE: return "RIL_REQUEST_SET_DATA_PROFILE";
            case RIL_REQUEST_IMS_REGISTRATION_STATE: return "RIL_REQUEST_IMS_REGISTRATION_STATE";
            case RIL_REQUEST_IMS_SEND_SMS: return "RIL_REQUEST_IMS_SEND_SMS";
            case RIL_REQUEST_SIM_TRANSMIT_APDU_BASIC: return "RIL_REQUEST_SIM_TRANSMIT_APDU_BASIC";
            case RIL_REQUEST_SIM_OPEN_CHANNEL: return "RIL_REQUEST_SIM_OPEN_CHANNEL";
            case RIL_REQUEST_SIM_CLOSE_CHANNEL: return "RIL_REQUEST_SIM_CLOSE_CHANNEL";
            case RIL_REQUEST_SIM_TRANSMIT_APDU_CHANNEL: return "RIL_REQUEST_SIM_TRANSMIT_APDU_CHANNEL";
            case RIL_REQUEST_NV_READ_ITEM: return "RIL_REQUEST_NV_READ_ITEM";
            case RIL_REQUEST_NV_WRITE_ITEM: return "RIL_REQUEST_NV_WRITE_ITEM";
            case RIL_REQUEST_NV_WRITE_CDMA_PRL: return "RIL_REQUEST_NV_WRITE_CDMA_PRL";
            case RIL_REQUEST_NV_RESET_CONFIG: return "RIL_REQUEST_NV_RESET_CONFIG";
            case RIL_REQUEST_SET_UICC_SUBSCRIPTION: return "RIL_REQUEST_SET_UICC_SUBSCRIPTION";
            case RIL_REQUEST_ALLOW_DATA: return "RIL_REQUEST_ALLOW_DATA";
            case RIL_REQUEST_GET_HARDWARE_CONFIG: return "GET_HARDWARE_CONFIG";
            case RIL_REQUEST_SIM_AUTHENTICATION: return "RIL_REQUEST_SIM_AUTHENTICATION";
            case RIL_REQUEST_SHUTDOWN: return "RIL_REQUEST_SHUTDOWN";
            case RIL_REQUEST_SET_RADIO_CAPABILITY:
                    return "RIL_REQUEST_SET_RADIO_CAPABILITY";
            case RIL_REQUEST_GET_RADIO_CAPABILITY:
                    return "RIL_REQUEST_GET_RADIO_CAPABILITY";
            default: return "<unknown request>";
        }
    }

    static String
    responseToString(int request)
    {
/*
 cat libs/telephony/ril_unsol_commands.h \
 | egrep "^ *{RIL_" \
 | sed -re 's/\{RIL_([^,]+),[^,]+,([^}]+).+/case RIL_\1: return "\1";/'
*/
        switch(request) {
            case RIL_UNSOL_RESPONSE_RADIO_STATE_CHANGED: return "UNSOL_RESPONSE_RADIO_STATE_CHANGED";
            case RIL_UNSOL_RESPONSE_CALL_STATE_CHANGED: return "UNSOL_RESPONSE_CALL_STATE_CHANGED";
            case RIL_UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED: return "UNSOL_RESPONSE_VOICE_NETWORK_STATE_CHANGED";
            case RIL_UNSOL_RESPONSE_NEW_SMS: return "UNSOL_RESPONSE_NEW_SMS";
            case RIL_UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT: return "UNSOL_RESPONSE_NEW_SMS_STATUS_REPORT";
            case RIL_UNSOL_RESPONSE_NEW_SMS_ON_SIM: return "UNSOL_RESPONSE_NEW_SMS_ON_SIM";
            case RIL_UNSOL_ON_USSD: return "UNSOL_ON_USSD";
            case RIL_UNSOL_ON_USSD_REQUEST: return "UNSOL_ON_USSD_REQUEST";
            case RIL_UNSOL_NITZ_TIME_RECEIVED: return "UNSOL_NITZ_TIME_RECEIVED";
            case RIL_UNSOL_SIGNAL_STRENGTH: return "UNSOL_SIGNAL_STRENGTH";
            case RIL_UNSOL_DATA_CALL_LIST_CHANGED: return "UNSOL_DATA_CALL_LIST_CHANGED";
            case RIL_UNSOL_SUPP_SVC_NOTIFICATION: return "UNSOL_SUPP_SVC_NOTIFICATION";
            case RIL_UNSOL_STK_SESSION_END: return "UNSOL_STK_SESSION_END";
            case RIL_UNSOL_STK_PROACTIVE_COMMAND: return "UNSOL_STK_PROACTIVE_COMMAND";
            case RIL_UNSOL_STK_EVENT_NOTIFY: return "UNSOL_STK_EVENT_NOTIFY";
            case RIL_UNSOL_STK_CALL_SETUP: return "UNSOL_STK_CALL_SETUP";
            case RIL_UNSOL_SIM_SMS_STORAGE_FULL: return "UNSOL_SIM_SMS_STORAGE_FULL";
            case RIL_UNSOL_SIM_REFRESH: return "UNSOL_SIM_REFRESH";
            case RIL_UNSOL_CALL_RING: return "UNSOL_CALL_RING";
            case RIL_UNSOL_RESPONSE_SIM_STATUS_CHANGED: return "UNSOL_RESPONSE_SIM_STATUS_CHANGED";
            case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS: return "UNSOL_RESPONSE_CDMA_NEW_SMS";
            case RIL_UNSOL_RESPONSE_NEW_BROADCAST_SMS: return "UNSOL_RESPONSE_NEW_BROADCAST_SMS";
            case RIL_UNSOL_CDMA_RUIM_SMS_STORAGE_FULL: return "UNSOL_CDMA_RUIM_SMS_STORAGE_FULL";
            case RIL_UNSOL_RESTRICTED_STATE_CHANGED: return "UNSOL_RESTRICTED_STATE_CHANGED";
            case RIL_UNSOL_ENTER_EMERGENCY_CALLBACK_MODE: return "UNSOL_ENTER_EMERGENCY_CALLBACK_MODE";
            case RIL_UNSOL_CDMA_CALL_WAITING: return "UNSOL_CDMA_CALL_WAITING";
            case RIL_UNSOL_CDMA_OTA_PROVISION_STATUS: return "UNSOL_CDMA_OTA_PROVISION_STATUS";
            case RIL_UNSOL_CDMA_INFO_REC: return "UNSOL_CDMA_INFO_REC";
            case RIL_UNSOL_OEM_HOOK_RAW: return "UNSOL_OEM_HOOK_RAW";
            case RIL_UNSOL_RINGBACK_TONE: return "UNSOL_RINGBACK_TONE";
            case RIL_UNSOL_RESEND_INCALL_MUTE: return "UNSOL_RESEND_INCALL_MUTE";
            case RIL_UNSOL_CDMA_SUBSCRIPTION_SOURCE_CHANGED: return "CDMA_SUBSCRIPTION_SOURCE_CHANGED";
            case RIL_UNSOl_CDMA_PRL_CHANGED: return "UNSOL_CDMA_PRL_CHANGED";
            case RIL_UNSOL_EXIT_EMERGENCY_CALLBACK_MODE: return "UNSOL_EXIT_EMERGENCY_CALLBACK_MODE";
            case RIL_UNSOL_RIL_CONNECTED: return "UNSOL_RIL_CONNECTED";
            case RIL_UNSOL_VOICE_RADIO_TECH_CHANGED: return "UNSOL_VOICE_RADIO_TECH_CHANGED";
            case RIL_UNSOL_CELL_INFO_LIST: return "UNSOL_CELL_INFO_LIST";
            case RIL_UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED:
                return "UNSOL_RESPONSE_IMS_NETWORK_STATE_CHANGED";
            case RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED:
                    return "RIL_UNSOL_UICC_SUBSCRIPTION_STATUS_CHANGED";
            case RIL_UNSOL_SRVCC_STATE_NOTIFY:
                    return "UNSOL_SRVCC_STATE_NOTIFY";
            case RIL_UNSOL_HARDWARE_CONFIG_CHANGED: return "RIL_UNSOL_HARDWARE_CONFIG_CHANGED";
            case RIL_UNSOL_RADIO_CAPABILITY:
                    return "RIL_UNSOL_RADIO_CAPABILITY";
            case RIL_UNSOL_ON_SS: return "UNSOL_ON_SS";
            case RIL_UNSOL_STK_CC_ALPHA_NOTIFY: return "UNSOL_STK_CC_ALPHA_NOTIFY";
            default: return "<unknown response>";
        }
    }
}
