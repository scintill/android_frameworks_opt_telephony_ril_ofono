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

import android.text.TextUtils;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccRefreshResponse;

import org.freedesktop.dbus.Variant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.internal.telephony.CommandException.Error.REQUEST_NOT_SUPPORTED;
import static net.scintill.ril_ofono.PropManager.getProp;

/*package*/ class SimFiles {

    private static final String TAG = "RilOfono";

    private final Map<String, Variant<?>> mMsgWaitingProps;
    private final Map<String, Variant<?>> mSimProps;

    private RilOfono.RegistrantList mIccRefreshRegistrants;

    private static final int COMMAND_GET_RESPONSE = 0xc0;
    private static final int COMMAND_READ_BINARY = 0xb0;
    private static final int COMMAND_READ_RECORD = 0xb2;

    private static final int TYPE_EF = 4;
    private static final int EF_TYPE_TRANSPARENT = 0;
    private static final int EF_TYPE_LINEAR_FIXED = 1;

    private static final int ADN_FOOTER_SIZE = 14;

    /*package*/ SimFiles(Map<String, Variant<?>> simProps, Map<String, Variant<?>> msgWaitingProps, RilOfono.RegistrantList iccRefreshRegistrants) {
        mSimProps = simProps;
        mMsgWaitingProps = msgWaitingProps;
        mIccRefreshRegistrants = iccRefreshRegistrants;
        initSimFiles();
    }

    public Object iccIOForApp(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, String aid) {
        // TS 102 221
        //String humanPath = path + "/" + Integer.toHexString(fileid);
        //Rlog.d(TAG, "iccIO " + command + " " + humanPath + " " + RilOfono.privStr(p1) + " " + RilOfono.privStr(p2) + " " + RilOfono.privStr(p3) + " " + RilOfono.privStr(data) + " " + RilOfono.privStr(pin2) + " " + aid);

        SimFile file = getSimFile(path, fileid);
        if (file == null) {
            return new IccIoResult(0x94, 0x00, new byte[0]);
        } else {
            if (command == COMMAND_GET_RESPONSE) {
                return new PrivResponseOb(new IccIoResult(0x90, 0x00, file.getResponse()));
            } else if (command == COMMAND_READ_BINARY) {
                int offset = p1 << 8 + p2;
                int length = p3 & 0xff;
                byte[] filePiece = new byte[length];
                System.arraycopy(file.mData, offset, filePiece, 0, length);
                return new PrivResponseOb(new IccIoResult(0x90, 0x00, filePiece));
            } else if (command == COMMAND_READ_RECORD) {
                // XXX ignoring some semantics of READ_RECORD...
                return new PrivResponseOb(new IccIoResult(0x90, 0x00, file.mData));
            } else {
                throw new CommandException(REQUEST_NOT_SUPPORTED);
            }
        }
    }

    private Map<String, SimFileFunction> mSimFiles = new HashMap<>();
    private Map<String, List<Integer>> mMapPropToDependentFileId = new HashMap<>();

    private SimFile getSimFile(String path, int fileid) {
        SimFileFunction sfg = mSimFiles.get(path + IntegralToString.intToHexString(fileid, false, 4));
        return sfg != null ? sfg.getSimFile() : null;
    }

    interface SimFileFunction {
        SimFile getSimFile();
    }

    private void addSimFile(String path, int fileid, SimFileFunction fileFunction, String... dependentPropNames) {
        mSimFiles.put(path + IntegralToString.intToHexString(fileid, false, 4), fileFunction);
        for (String dependentPropName : dependentPropNames) {
            String mapkey = dependentPropName;
            if (mMapPropToDependentFileId.get(mapkey) == null) {
                List<Integer> newList = new ArrayList<>();
                newList.add(fileid);
                mMapPropToDependentFileId.put(mapkey, newList);
            } else {
                mMapPropToDependentFileId.get(mapkey).add(fileid);
            }
        }
    }

    /*package*/ void notifyPropChangeForPotentialFileRefresh(String propName) {
        List<Integer> fileIds = mMapPropToDependentFileId.get(propName);
        if (fileIds != null) {
            for (int fileId : fileIds) {
                IccRefreshResponse irr = new IccRefreshResponse();
                irr.aid = SimModule.SIM_APP_ID;
                irr.refreshResult = IccRefreshResponse.REFRESH_RESULT_FILE_UPDATE;
                irr.efId = fileId;
                RilOfono.notifyResultAndLog("SIM file refresh "+Integer.toHexString(fileId), mIccRefreshRegistrants, irr, false);
            }
        }
    }

    private void initSimFiles() {
        final String CARD_IDENTIFIER = "CardIdentifier";
        final String SUBSCRIBER_NUMBERS = "SubscriberNumbers";
        final String VOICEMAIL_MAILBOX_NUMBER = "VoicemailMailboxNumber";
        final String VOICEMAIL_WAITING = "VoicemailWaiting";
        final String VOICEMAIL_MESSAGE_COUNT = "VoicemailMessageCount";

        addSimFile(IccConstants.MF_SIM, IccConstants.EF_ICCID, new SimFileFunction() {
            @Override
            public SimFile getSimFile() {
                String iccid = getProp(mSimProps, CARD_IDENTIFIER, (String)null);
                if (TextUtils.isEmpty(iccid)) return null;

                SimFile file = new SimFile();
                file.mType = TYPE_EF;
                file.mResponseDataStructure = EF_TYPE_TRANSPARENT;
                file.mData = Utils.stringToBcd(iccid);
                return file;
            }
        }, CARD_IDENTIFIER);

        addSimFile(IccConstants.MF_SIM + IccConstants.DF_TELECOM, IccConstants.EF_MSISDN, new SimFileFunction() {
            @Override
            public SimFile getSimFile() {
                String[] numbers = getProp(mSimProps, SUBSCRIBER_NUMBERS, new String[0]);
                if (numbers.length == 0) return null;

                SimFile file = new SimFile();
                file.mType = TYPE_EF;
                file.mResponseDataStructure = EF_TYPE_LINEAR_FIXED;
                file.mData = new AdnRecord(null, numbers[0]).buildAdnString(ADN_FOOTER_SIZE);
                return file;
            }
        }, SUBSCRIBER_NUMBERS);

        addSimFile(IccConstants.MF_SIM + IccConstants.DF_GSM, IccConstants.EF_MBI, new SimFileFunction() {
            @Override
            public SimFile getSimFile() {
                String voicemailNumber = getProp(mMsgWaitingProps, VOICEMAIL_MAILBOX_NUMBER, (String)null);
                if (TextUtils.isEmpty(voicemailNumber)) return null;

                // TS 151 011
                SimFile file = new SimFile();
                file.mType = TYPE_EF;
                file.mResponseDataStructure = EF_TYPE_LINEAR_FIXED;
                file.mData = new byte[] { 0x01 /*voicemail index to the below record (we only have one)*/, 0x00, 0x00, 0x00 };
                return file;
            }
        }, VOICEMAIL_MAILBOX_NUMBER);

        addSimFile(IccConstants.MF_SIM + IccConstants.DF_GSM, IccConstants.EF_MBDN, new SimFileFunction() {
            @Override
            public SimFile getSimFile() {
                String voicemailNumber = getProp(mMsgWaitingProps, VOICEMAIL_MAILBOX_NUMBER, (String)null);
                if (TextUtils.isEmpty(voicemailNumber)) return null;

                SimFile file = new SimFile();
                file.mType = TYPE_EF;
                file.mResponseDataStructure = EF_TYPE_LINEAR_FIXED;
                file.mData = new AdnRecord(null, voicemailNumber).buildAdnString(ADN_FOOTER_SIZE);
                return file;
            }
        }, VOICEMAIL_MAILBOX_NUMBER);

        addSimFile(IccConstants.MF_SIM + IccConstants.DF_GSM, IccConstants.EF_MWIS, new SimFileFunction() {
            @Override
            public SimFile getSimFile() {
                boolean waiting = getProp(mMsgWaitingProps, VOICEMAIL_WAITING, Boolean.FALSE);
                int count = getProp(mMsgWaitingProps, VOICEMAIL_MESSAGE_COUNT, (Number)0).byteValue() & 0xff;

                SimFile file = new SimFile();
                file.mType = TYPE_EF;
                file.mResponseDataStructure = EF_TYPE_LINEAR_FIXED;
                file.mData = new byte[] { (byte)(waiting ? 1 : 0), (byte)count };
                return file;
            }
        }, VOICEMAIL_WAITING, VOICEMAIL_MESSAGE_COUNT);
    }

    class SimFile {
        public byte mType;
        public byte mResponseDataStructure;
        public byte[] mData;
        public byte[] getResponse() {
            // see IccFileHandler for offsets
            return new byte[] {
                    0x00, 0x00, // rfu
                    (byte)((mData.length >> 8) & 0xff), (byte)(mData.length & 0xff),
                    0x00, 0x00, // file id
                    mType,
                    0x00, // rfu
                    0x00, 0x00, 0x00, // access condition
                    0x00, // status
                    0x00, // length
                    mResponseDataStructure, // structure
                    (byte) mData.length, // record length
            };
        }
    }

}
