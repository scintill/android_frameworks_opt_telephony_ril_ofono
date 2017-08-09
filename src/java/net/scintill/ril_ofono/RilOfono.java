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

import android.annotation.NonNull;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.Rlog;
import android.telephony.SignalStrength;
import android.text.TextUtils;

import com.android.internal.telephony.BaseCommands;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.SmsResponse;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsMessage;
import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;
import com.android.internal.telephony.uicc.IccConstants;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccUtils;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt16;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.ofono.Manager;
import org.ofono.MessageManager;
import org.ofono.Modem;
import org.ofono.NetworkRegistration;
import org.ofono.SimManager;
import org.ofono.Struct1;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_EDGE;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_GSM;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_UMTS;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
import static com.android.internal.telephony.CommandException.Error.GENERIC_FAILURE;
import static com.android.internal.telephony.CommandException.Error.INVALID_PARAMETER;
import static com.android.internal.telephony.CommandException.Error.REQUEST_NOT_SUPPORTED;

public class RilOfono extends BaseCommands implements CommandsInterface {

    private static final String TAG = "RilOfono";

    private static final int BUILD_NUMBER = 11;

    /*
     * @TODO What does this mean to consumers? I picked 9 because it's less than 10, which is
     * apparently when the icc*() methods we probably won't support were added.
     */
    private static final int RIL_VERSION = 9;

    private static final String DBUS_ADDRESS = "unix:path=/dev/socket/dbus";

    private Handler mDbusHandler;
    private Handler mMainHandler;

    private DBusConnection mDbus;
    private Modem mModem;
    private final Map<String, Variant> mModemProps = new HashMap<>();
    private NetworkRegistration mNetReg;
    private final Map<String, Variant> mNetRegProps = new HashMap<>();
    private SimManager mSim;
    private final Map<String, Variant> mSimProps = new HashMap<>();
    private MessageManager mMessenger;

    public RilOfono(Context context, int preferredNetworkType, int cdmaSubscription, Integer instanceId) {
        super(context);
        Rlog.d(TAG, String.format("RilOfono %d starting", BUILD_NUMBER));

        mPhoneType = RILConstants.NO_PHONE;

        HandlerThread dbusThread = new HandlerThread("dbus");
        dbusThread.start();
        mMainHandler = new Handler(new EmptyHandlerCallback());
        mDbusHandler = new Handler(dbusThread.getLooper(), new EmptyHandlerCallback());

        mDbusHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mDbus = DBusConnection.getConnection(DBUS_ADDRESS);
                    mModem = mDbus.getRemoteObject(OFONO_BUS_NAME, MODEM_PATH, Modem.class);
                    mSim = mDbus.getRemoteObject(OFONO_BUS_NAME, MODEM_PATH, SimManager.class);
                    mNetReg = mDbus.getRemoteObject(OFONO_BUS_NAME, MODEM_PATH, NetworkRegistration.class);
                    mMessenger = mDbus.getRemoteObject(OFONO_BUS_NAME, MODEM_PATH, MessageManager.class);
                    delegateSigHandler(Manager.ModemAdded.class);
                    delegateSigHandler(Manager.ModemRemoved.class);
                    delegateSigHandler(Modem.PropertyChanged.class);
                    delegateSigHandler(NetworkRegistration.PropertyChanged.class);
                    delegateSigHandler(SimManager.PropertyChanged.class);
                    delegateSigHandler(org.ofono.Message.PropertyChanged.class);
                    initProps();
                    onModemChange(false); // initialize starting state
                } catch (DBusException e) {
                    logException("RilOfono", e);
                    System.exit(-1); // XXX how to better react to this?
                }
            }
        });
    }

    @Override
    public void getImsRegistrationState(Message result) {
        respondExc("getImsRegistrationState", result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setSuppServiceNotifications(boolean enable, Message result) {
        respondExc("setSuppServiceNotifications", result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void supplyIccPin(String pin, Message result) {
        supplyIccPinForApp(pin, null, result);
    }

    @Override
    public void supplyIccPinForApp(String pin, String aid, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void supplyIccPuk(String puk, String newPin, Message result) {
        supplyIccPukForApp(puk, newPin, null, result);
    }

    @Override
    public void supplyIccPukForApp(String puk, String newPin, String aid, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void supplyIccPin2(String pin2, Message result) {
        supplyIccPin2ForApp(pin2, null, result);
    }

    @Override
    public void supplyIccPin2ForApp(String pin2, String aid, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void supplyIccPuk2(String puk2, String newPin2, Message result) {
        supplyIccPuk2ForApp(puk2, newPin2, null, result);
    }

    @Override
    public void supplyIccPuk2ForApp(String puk2, String newPin2, String aid, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void changeIccPin(String oldPin, String newPin, Message result) {
        changeIccPin2ForApp(oldPin, newPin, null, result);
    }

    @Override
    public void changeIccPinForApp(String oldPin, String newPin, String aidPtr, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void changeIccPin2(String oldPin2, String newPin2, Message result) {
        changeIccPin2ForApp(oldPin2, newPin2, null, result);
    }

    @Override
    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aidPtr, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void supplyDepersonalization(String netpin, String type, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getCurrentCalls(Message result) {
        // the call tracker makes a lot of spam if we log this
        respondExc("getCurrentCalls", result, REQUEST_NOT_SUPPORTED, null, true);
    }

    @Override
    public void getPDPContextList(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getDataCallList(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void dial(String address, int clirMode, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getIMSI(Message result) {
        getIMSIForApp(null, result);
    }

    @Override
    public void getIMSIForApp(String aid, Message result) {
        // TODO GSM-specific?
        String imsi = getProp(mSimProps, "SubscriberIdentity", (String)null);
        if (imsi != null) {
            respondOk("getIMSIForApp", result, imsi);
        } else {
            respondExc("getIMSIForApp", result, GENERIC_FAILURE, null);
        }
    }

    @Override
    public void getIMEI(Message result) {
        // TODO GSM-specific?
        respondWithModemProp("getIMEI", "Serial", result);
    }

    @Override
    public void getIMEISV(Message result) {
        // TODO GSM-specific? correct?
        respondWithModemProp("getIMEISV", "SoftwareVersionNumber", result);
    }

    @Override
    public void hangupConnection(int gsmIndex, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void hangupWaitingOrBackground(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void hangupForegroundResumeBackground(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void switchWaitingOrHoldingAndActive(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void conference(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setPreferredVoicePrivacy(boolean enable, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getPreferredVoicePrivacy(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void separateConnection(int gsmIndex, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void acceptCall(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void rejectCall(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void explicitCallTransfer(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getLastCallFailCause(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getLastPdpFailCause(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getLastDataCallFailCause(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setMute(boolean enableMute, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getMute(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getSignalStrength(Message response) {
        // TODO I can't seem to find this on the ofono bus, but supposedly it's supported
        // make up a low strength
        SignalStrength s = new SignalStrength(20, 1, -1, -1, -1, -1, -1, true);
        respondOk("getSignalStrength", response, s);
    }

    @Override
    public void getVoiceRegistrationState(Message response) {
        OfonoRegistrationState state = getProp(mNetRegProps, "Status", OfonoRegistrationState.unknown);
        if (!state.isRegistered()) {
            respondOk("getVoiceRegistrationState", response, new String[]{ ""+state.ts27007Creg, "-1", "-1" });
        } else {
            respondOk("getVoiceRegistrationState", response, new String[]{
                    ""+state.ts27007Creg,
                    getProp(mNetRegProps, "LocationAreaCode", "-1"),
                    getProp(mNetRegProps, "CellId", "-1")
            });
        }
    }

    @Override
    public void getDataRegistrationState(Message response) {
        respondExc("getDataRegistrationState", response, REQUEST_NOT_SUPPORTED, null);
    }

    @SuppressWarnings("FieldCanBeLocal")
    private final String DEBUG_DECORATOR = "🌠"; // make it obvious we're running this RIL

    @Override
    public void getOperator(Message response) {
        boolean registered = getProp(mNetRegProps, "Status", OfonoRegistrationState.unknown).isRegistered();
        String name = getProp(mNetRegProps, "Name", "");
        String mcc = getProp(mNetRegProps, "MobileCountryCode", "");
        String mnc = getProp(mNetRegProps, "MobileNetworkCode", "");
        name = DEBUG_DECORATOR + name + DEBUG_DECORATOR;
        if (registered && mcc.length() > 0 && mnc.length() > 0 && name.length() > 0) {
            respondOk("getOperator", response, new String[] {
                    name, name, /* TODO does Ofono offer distinct short and long names? */
                    mcc+mnc
            });
        } else {
            respondOk("getOperator", response, new String[] { null, null, null });
        }
    }

    @Override
    public void sendDtmf(char c, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void startDtmf(char c, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void stopDtmf(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void sendBurstDtmf(String dtmfString, int on, int off, Message result) {
        genericTrace(); // XXX NYI

    }

    AtomicInteger mSmsRef = new AtomicInteger(1);
    final Map<String, Message> mMapSmsDbusPathToSenderCallback = new HashMap<>();
    // TODO synchronization on this map object is how I ensure signals don't arrive before the entry into this map,
    // but are there any adverse effects of synchronizing so broadly?

    @Override
    public void sendSMS(String smscPDUStr, String pduStr, final Message response) {
        Rlog.d(TAG, "sendSMS");
        // TODO gsm-specific?
        // TODO is there a way to preserve the whole pdu to ofono? should we check for special things that ofono won't do, and refuse to send if the PDU contains them?

        final SmsMessage msg = parseSmsPduStrs(smscPDUStr, pduStr);

        if (msg == null || msg.getRecipientAddress() == null || msg.getMessageBody() == null) {
            respondExc("sendSMS", response, INVALID_PARAMETER, null);
        } else {
            mDbusHandler.post(new Runnable() {
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

    @Override
    public void sendSMSExpectMore(String smscPDU, String pdu, Message result) {
        // TODO can oFono benefit from knowing to "expect more"?
        sendSMS(smscPDU, pdu, result);
    }

    @Override
    public void sendCdmaSms(byte[] pdu, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void sendImsGsmSms(String smscPDU, String pdu, int retry, int messageRef, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void deleteSmsOnSim(int index, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void deleteSmsOnRuim(int index, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void writeSmsToRuim(int status, String pdu, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setRadioPower(final boolean on, final Message response) {
        Rlog.v(TAG, "setRadioPower("+on+")");

        mDbusHandler.post(new Runnable() {
            @Override
            public void run() {
                mModem.SetProperty("Online", new Variant<>(on));
                respondOk("setRadioPower", response, null);
            }
        });
    }

    @Override
    public void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void iccIO(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, Message response) {
        iccIOForApp(command, fileid, path, p1, p2, p3, data, pin2, null, response);
    }

    @Override
    public void iccIOForApp(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, String aid, Message response) {
        String humanPath = path + "/" + Integer.toHexString(fileid);
        Rlog.d(TAG, "iccIO " + command + " " + humanPath + " " + p1 + " " + p2 + " " + p3 + " " + data + " " + pin2 + " " + aid);

        final int COMMAND_GET_RESPONSE = 0xc0;
        final int COMMAND_READ_BINARY = 0xb0;
        final int COMMAND_READ_RECORD = 0xb2;
        if (command == COMMAND_GET_RESPONSE) {
            SimFile file = getSimFile(path, fileid);
            if (file != null) {
                respondOk("iccIOForApp GetResponse " + humanPath, response, new IccIoResult(0x90, 0x00, file.getResponse()));
            } else {
                respondOk("iccIOForApp GetResponse " + humanPath, response, new IccIoResult(0x94, 0x00, new byte[0]));
            }
        } else if (command == COMMAND_READ_BINARY) {
            int offset = p1 << 8 + p2;
            int length = p3 & 0xff;
            SimFile file = getSimFile(path, fileid);
            if (file != null) {
                byte[] filePiece = new byte[length];
                System.arraycopy(file.mData, offset, filePiece, 0, length);
                respondOk("iccIOForApp ReadBinary " + humanPath, response, new IccIoResult(0x90, 0x00, filePiece));
            } else {
                respondOk("iccIOForApp ReadBinary " + humanPath, response, new IccIoResult(0x94, 0x00, new byte[0]));
            }
        } else if (command == COMMAND_READ_RECORD) {
            // TODO ignoring some semantics of READ_RECORD...
            SimFile file = getSimFile(path, fileid);
            if (file != null) {
                respondOk("iccIOForApp ReadRecord " + humanPath, response, new IccIoResult(0x90, 0x00, file.mData));
            } else {
                respondOk("iccIOForApp ReadRecord " + humanPath, response, new IccIoResult(0x94, 0x00, new byte[0]));
            }
        } else {
            respondExc("iccIOForApp "+command+" "+humanPath, response, REQUEST_NOT_SUPPORTED, null);
        }
    }

    @Override
    public void queryCLIP(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getCLIR(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setCLIR(int clirMode, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void queryCallWaiting(int serviceClass, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setCallWaiting(boolean enable, int serviceClass, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setCallForward(int action, int cfReason, int serviceClass, String number, int timeSeconds, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void queryCallForwardStatus(int cfReason, int serviceClass, String number, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setNetworkSelectionModeAutomatic(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setNetworkSelectionModeManual(String operatorNumeric, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getNetworkSelectionMode(Message response) {
        String mode = getProp(mNetRegProps, "Mode", (String)null);
        if (mode == null) {
            respondExc("getNetworkSelectionMode", response, GENERIC_FAILURE, null);
        } else {
            respondOk("getNetworkSelectionMode", response, new int[]{ mode.equals("manual") ? 1 : 0 });
        }
    }

    @Override
    public void getAvailableNetworks(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getBasebandVersion(Message response) {
        respondWithModemProp("getBaseBandVersion", "Revision", response);
    }

    @Override
    public void queryFacilityLock(String facility, String password, int serviceClass, Message response) {
        queryFacilityLockForApp(facility, password, serviceClass, null, response);
    }

    @Override
    public void queryFacilityLockForApp(String facility, String password, int serviceClass, String appId, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setFacilityLock(String facility, boolean lockState, String password, int serviceClass, Message response) {
        setFacilityLockForApp(facility, lockState, password, serviceClass, null, response);
    }

    @Override
    public void setFacilityLockForApp(String facility, boolean lockState, String password, int serviceClass, String appId, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void sendUSSD(String ussdString, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void cancelPendingUssd(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void resetRadio(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setBandMode(int bandMode, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void queryAvailableBandMode(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setPreferredNetworkType(int networkType, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getPreferredNetworkType(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getNeighboringCids(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setLocationUpdates(boolean enable, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getSmscAddress(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setSmscAddress(String address, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void reportSmsMemoryStatus(boolean available, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void reportStkServiceIsRunning(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        genericTrace(); // XXX NYI
    }

    @Override
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        genericTrace(); // XXX NYI
    }

    @Override
    public void sendTerminalResponse(String contents, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void sendEnvelope(String contents, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void sendEnvelopeWithStatus(String contents, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void handleCallSetupRequestFromSim(boolean accept, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setGsmBroadcastActivation(boolean activate, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getGsmBroadcastConfig(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getDeviceIdentity(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getCDMASubscription(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void sendCDMAFeatureCode(String FeatureCode, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setPhoneType(int phoneType) {
        mPhoneType = phoneType;
    }

    @Override
    public void queryCdmaRoamingPreference(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setCdmaSubscriptionSource(int cdmaSubscriptionType, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getCdmaSubscriptionSource(Message response) {
        respondExc("getCdmaSubscriptionSource", response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setTTYMode(int ttyMode, Message response) {
        respondExc("setTTYMode", response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void queryTTYMode(Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setupDataCall(String radioTechnology, String profile, String apn, String user, String password, String authType, String protocol, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void deactivateDataCall(int cid, int reason, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setCdmaBroadcastActivation(boolean activate, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getCdmaBroadcastConfig(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void exitEmergencyCallbackMode(Message response) {
        genericTrace(); // XXX NYI

    }

    private static final String SIM_APP_ID = "00";

    @Override
    public void getIccCardStatus(Message result) {
        // TODO GSM-specific? can we/should we do more?
        IccCardStatus cardStatus = new IccCardStatus();
        cardStatus.mCdmaSubscriptionAppIndex = -1;
        cardStatus.mImsSubscriptionAppIndex = -1;

        Boolean present = getProp(mSimProps, "Present", (Boolean)null);
        if (present == null) {
            cardStatus.mCardState = CardState.CARDSTATE_ERROR;
        } else {
            cardStatus.mCardState = present ? CardState.CARDSTATE_PRESENT : CardState.CARDSTATE_ABSENT;
        }

        IccCardApplicationStatus gsmAppStatus = new IccCardApplicationStatus();
        gsmAppStatus.app_type = IccCardApplicationStatus.AppType.APPTYPE_SIM;
        gsmAppStatus.app_state = IccCardApplicationStatus.AppState.APPSTATE_READY;
        gsmAppStatus.aid = SIM_APP_ID;
        gsmAppStatus.app_label = "Ofono SIM";
        gsmAppStatus.pin1 = IccCardStatus.PinState.PINSTATE_DISABLED; // TODO
        gsmAppStatus.pin2 = IccCardStatus.PinState.PINSTATE_DISABLED; // TODO

        if (cardStatus.mCardState == CardState.CARDSTATE_PRESENT) {
            cardStatus.mGsmUmtsSubscriptionAppIndex = 0;
            cardStatus.mApplications = new IccCardApplicationStatus[] { gsmAppStatus };
        } else {
            cardStatus.mGsmUmtsSubscriptionAppIndex = -1;
            cardStatus.mApplications = new IccCardApplicationStatus[0];
        }

        cardStatus.mUniversalPinState = IccCardStatus.PinState.PINSTATE_DISABLED; // TODO

        respondOk("getIccCardStatus", result, cardStatus);
    }

    @Override
    public void requestIsimAuthentication(String nonce, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void requestIccSimAuthentication(int authContext, String data, String aid, Message response) {
        genericTrace(); // XXX NYI

    }

    private Object getVoiceRadioTechnologyAsyncResult() {
        // TODO is this really the right value?
        try {
            OfonoNetworkTechnology tech = getProp(mNetRegProps, "Technology", OfonoNetworkTechnology._unknown);
            return new int[]{ tech.serviceStateInt };
        } catch (Throwable t) {
            Rlog.e(TAG, "Error getting voice radio tech", t);
            return new int[] { OfonoNetworkTechnology._unknown.serviceStateInt };
        }
    }

    @Override
    public void getVoiceRadioTechnology(Message result) {
        respondOk("getVoiceRadioTechnology", result, getVoiceRadioTechnologyAsyncResult());
    }

    @Override
    public void getCellInfoList(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setCellInfoListRate(int rateInMillis, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void setInitialAttachApn(String apn, String protocol, int authType, String username, String password, Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void nvReadItem(int itemID, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void nvWriteItem(int itemID, String itemValue, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void nvResetConfig(int resetType, Message response) {
        genericTrace(); // XXX NYI

    }

    @Override
    public void getHardwareConfig(Message result) {
        genericTrace(); // XXX NYI

    }

    @Override
    public boolean needsOldRilFeature(String feature) {
        return false;
    }

    private void onModemChange(boolean assumeManagerPresent) {
        try {
            Manager manager = mDbus.getRemoteObject(OFONO_BUS_NAME, "/", Manager.class);
            List<Struct1> modems = manager.GetModems();
            if (modems.size() > 0) {
                Rlog.v(TAG, "modem avail");
                // TODO figure out how to properly get modem object out of "modems" array? We
                // get a Proxy object that gives us nothing useful and throws errors when we
                // try to call methods it should have. Bug in autogenerated class stuff?
                onModemAvail();
            } else {
                Rlog.v(TAG, "modem gone");
                // TODO what?
            }
        } catch (DBus.Error.ServiceUnknown e) {
            //noinspection StatementWithEmptyBody
            if (assumeManagerPresent) {
                throw e;
            } else {
                // ignore
            }
        } catch (Throwable t) {
            logException("onModemChange", t);
        }
    }

    public void handle(Manager.ModemAdded s) {
        onModemChange(true);
    }

    public void handle(Manager.ModemRemoved s) {
        onModemChange(true);
    }

    // simple property-mirroring signal handlers should delegate to handlePropChange()
    public void handle(Modem.PropertyChanged s) {
        handlePropChange(mModem, s.name, s.value);
    }

    public void handle(NetworkRegistration.PropertyChanged s) {
        handlePropChange(mNetReg, s.name, s.value);
    }

    public void handle(SimManager.PropertyChanged s) {
        handlePropChange(mSim, s.name, s.value);
    }

    public void handle(org.ofono.Message.PropertyChanged s) {
        if (s.name.equals("State")) {
            String value = (String) s.value.getValue();
            if (value.equals("sent") || value.equals("failed")) {
                handleSendSmsComplete(s.getPath(), value);
            }
        }
    }

    // first parameter is just to select the method by type
    private void handlePropChange(Modem modem, String name, Variant value) {
        handlePropChange(mModemProps, Modem.class, name, value);
        // TODO correct thread?
        if (name.equals("Online")) {
            boolean online = (Boolean) value.getValue();
            setRadioState(online ? RadioState.RADIO_ON : RadioState.RADIO_OFF);
        }
    }

    private void handlePropChange(NetworkRegistration netReg, String name, Variant value) {
        handlePropChange(mNetRegProps, NetworkRegistration.class, name, value);
        if (mFnNotifyNetworkChanged == null) mFnNotifyNetworkChanged = new DebouncedRunnable() {
            @Override
            public void run() {
                Rlog.d(TAG, "notify voiceNetworkState");
                mVoiceNetworkStateRegistrants.notifyRegistrants();
                Rlog.d(TAG, "notify voiceRadioTechChanged");
                mVoiceRadioTechChangedRegistrants.notifyResult(getVoiceRadioTechnologyAsyncResult());
            }
        };
        postDebounced(mMainHandler, mFnNotifyNetworkChanged, 350);
        // TODO data network registration?
    }

    private void handlePropChange(SimManager sim, String name, Variant value) {
        handlePropChange(mSimProps, SimManager.class, name, value);
        // TODO check if something that we report actually changed?
        if (mFnNotifySimChanged == null) mFnNotifySimChanged = new DebouncedRunnable() {
            @Override
            public void run() {
                Rlog.d(TAG, "notify iccStatusChanged");
                mIccStatusChangedRegistrants.notifyRegistrants();
            }
        };
        postDebounced(mMainHandler, mFnNotifySimChanged, 350);
    }

    private void handlePropChange(Map<String, Variant> propsToUpdate, Class<?extends DBusInterface> dbusObIface, String name, Variant value) {
        // TODO at least some of these are sensitive enough they shouldn't be logged
        Rlog.d(TAG, dbusObIface.getSimpleName() + " propchange: " + name + "=" + value);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (propsToUpdate) {
            propsToUpdate.put(name, value);
        }
    }

    private void initProps() {
        initProps(mModemProps, Modem.class, mModem);
        initProps(mNetRegProps, NetworkRegistration.class, mNetReg);
        initProps(mSimProps, SimManager.class, mSim);
    }

    private void initProps(Map<String, Variant> propsToInit, Class<?extends DBusInterface> sourceObIface, DBusInterface sourceOb) {
        // load properties
        Map<String, Variant> props;
        try {
            Method m = sourceObIface.getMethod("GetProperties");
            //noinspection unchecked
            props = (Map<String, Variant>) m.invoke(sourceOb);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("unable to find GetProperties method", e);
        } catch (InvocationTargetException e) {
            try {
                if (e.getCause() instanceof DBusExecutionException) {
                    throw (DBusExecutionException) e.getCause();
                } else {
                    throw new RuntimeException("error calling GetProperties() on " + sourceObIface.getSimpleName(), e.getCause());
                }
            } catch (DBus.Error.UnknownMethod unknownMethod) {
                Rlog.w(TAG, "unable to GetProperties() on " + sourceObIface.getSimpleName());
                // probably just isn't loaded yet, so give empty props
                props = new HashMap<>();
            }
        }

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (propsToInit) {
            propsToInit.clear(); // TODO notify about removed props?
            try {
                Method m = this.getClass().getDeclaredMethod("handlePropChange", sourceObIface, String.class, Variant.class);
                for (Map.Entry<String, Variant> entry : props.entrySet()) {
                    propsToInit.put(entry.getKey(), entry.getValue());
                    m.invoke(this, sourceOb, entry.getKey(), entry.getValue());
                }
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("unable to find handlePropChange method", e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    }

    private <T> T getProp(Map<String, Variant> props, String key, T defaultValue) {
        //noinspection unchecked
        return props.get(key) != null ? (T) props.get(key).getValue() : defaultValue;
    }

    private <T> T[] getProp(Map<String, Variant> props, String key, @NonNull T[] defaultValue) {
        if (props.get(key) != null) {
            //noinspection unchecked
            List<T> list = (List<T>)(props.get(key).getValue());
            return list.toArray(defaultValue);
        } else {
            return defaultValue;
        }
    }

    private Integer getProp(Map<String, Variant> props, String key, Integer defaultValue) {
        if (props.get(key) == null) return defaultValue;
        Object value = props.get(key).getValue();
        if (value instanceof UInt16) return ((UInt16) value).intValue();
        return (Integer) value;
    }

    private Long getProp(Map<String, Variant> props, String key, Long defaultValue) {
        if (props.get(key) == null) return defaultValue;
        Object value = props.get(key).getValue();
        if (value instanceof UInt16) return ((UInt16) value).longValue();
        if (value instanceof UInt32) return ((UInt32) value).longValue();
        return (Long) value;
    }

    private String getProp(Map<String, Variant> props, String key, String defaultValue) {
        return props.get(key) != null ? props.get(key).getValue().toString() : defaultValue;
    }

    private <T extends Enum> T getProp(Map<String, Variant> props, String key, T defaultValue) {
        return (T) Enum.valueOf(defaultValue.getClass(), getProp(props, key, defaultValue.toString()));
    }

    private void onModemAvail() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                // see RIL.java for RIL_UNSOL_RIL_CONNECTED
                setRadioPower(false, cbToMsg(new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message msg) {
                        AsyncResult ar = (AsyncResult) msg.obj;
                        if (ar.exception == null) {
                            setRadioState(RadioState.RADIO_OFF);
                        } else {
                            setRadioState(RadioState.RADIO_UNAVAILABLE);
                            logException("onModemAvail setRadioPower", ar.exception);
                        }
                        return true;
                    }
                }));
                Rlog.d(TAG, "notifyRegistrantsRilConnectionChanged");
                updateRilConnection(RIL_VERSION);
            }
        });
    }

    private void logException(String m, Throwable t) {
        Rlog.e(TAG, "Uncaught exception in "+m, t);
    }

    private void updateRilConnection(int version) {
        this.mRilVersion = version;
        if (mRilConnectedRegistrants != null) {
            mRilConnectedRegistrants.notifyResult(version);
        }
    }

    // TODO
    // things I noticed BaseCommands overrides but has an empty implementation:
    // getDataCallProfile(), onRadioAvailable(), getModemCapability(),
    // setUiccSubscription(), setDataProfile(), setDataAllowed(), requestShutdown(),
    // iccOpenLogicalChannel(), iccCloseLogicalChannel(), iccTransmitApduLogicalChannel(),
    // iccTransmitApduBasicChannel(), getAtr(), setLocalCallHold()

    private String getCallerMethodName() {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        return elements[4].getMethodName();
    }

    /**
     * Log the name of the method that called this one.
     */
    private void genericTrace() {
        Rlog.v(TAG, getCallerMethodName()+"()");
    }

    private static final String OFONO_BUS_NAME = "org.ofono";
    private static final String MODEM_PATH = "/ril_0";

    private class EmptyHandlerCallback implements Handler.Callback {
        @Override
        public boolean handleMessage(Message msg) {
            return true;
        }
    }

    private <T extends DBusSignal> void delegateSigHandler(Class<T> type) throws DBusException {
        mDbus.addSigHandler(type, new DBusSigHandler<T>() {
            @Override
            public void handle(T s) {
                try {
                    RilOfono.class.getMethod("handle", s.getClass()).invoke(RilOfono.this, s);
                } catch (IllegalAccessException | NoSuchMethodException e) {
                    Rlog.e(TAG, "Unexpected exception while delegating dbus event", e);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e.getCause());
                }
            }
        });
    }

    private void respondOk(String caller, Message response, Object o) {
        // TODO at least some of these are sensitive enough they shouldn't be logged
        Rlog.d(TAG, "respondOk from "+caller+": "+toDebugString(o));
        if (response != null) {
            AsyncResult.forMessage(response, o, null);
            response.sendToTarget();
        }
    }

    private void respondExc(String caller, Message response, CommandException.Error err, Object o) {
        respondExc(caller, response, err, o, false);
    }

    private void respondExc(String caller, Message response, CommandException.Error err, Object o, boolean quiet) {
        if (!quiet) {
            Rlog.d(TAG, "respondExc from "+caller+": "+err+" "+o);
        }
        if (response != null) {
            // fill in generic exception if needed - at least GsmCallTracker
            AsyncResult.forMessage(response, o, new CommandException(err));
            response.sendToTarget();
        }
    }

    private Message cbToMsg(Handler.Callback cb) {
        return new Handler(cb).obtainMessage();
    }

    // TODO rewrite using getProp() ?
    private void respondWithModemProp(String caller, String propertyName, Message response) {
        Variant value = mModemProps.get(propertyName);
        respondOk(caller, response, value != null ? value.getValue() : null);
    }

    // mostly a tag type to remind me use this correctly (only construct one for each purpose)
    abstract class DebouncedRunnable implements Runnable {}
    DebouncedRunnable mFnNotifyNetworkChanged;
    DebouncedRunnable mFnNotifySimChanged;

    private void postDebounced(Handler h, DebouncedRunnable r, long delayMillis) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (h) {
            if (!h.hasCallbacks(r)) {
                h.sendMessageDelayed(Message.obtain(h, r), delayMillis);
            }
        }
    }

    enum OfonoRegistrationState {
        unregistered(0), registered(1), searching(2), denied(3), unknown(4), roaming(5);
        public int ts27007Creg;
        OfonoRegistrationState(int ts27007Creg) {
            this.ts27007Creg = ts27007Creg;
        }
        public boolean isRegistered() {
            return this == registered || this == roaming;
        }
    }

    enum OfonoNetworkTechnology {
        gsm(RIL_RADIO_TECHNOLOGY_GSM), edge(RIL_RADIO_TECHNOLOGY_EDGE), umts(RIL_RADIO_TECHNOLOGY_UMTS),
        hspa(RIL_RADIO_TECHNOLOGY_HSPA), lte(RIL_RADIO_TECHNOLOGY_LTE),
        _unknown(RIL_RADIO_TECHNOLOGY_UNKNOWN);
        public int serviceStateInt;
        OfonoNetworkTechnology(int serviceStateInt) {
            this.serviceStateInt = serviceStateInt;
        }
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

    private SimFile getSimFile(String path, int fileid) {
        SimFile file = new SimFile();
        final int TYPE_EF = 4;
        final int EF_TYPE_TRANSPARENT = 0;
        final int EF_TYPE_LINEAR_FIXED = 1;
        if (path.equals(IccConstants.MF_SIM) && fileid == IccConstants.EF_ICCID) {
            String iccid = getProp(mSimProps, "CardIdentifier", (String)null);
            if (!TextUtils.isEmpty(iccid)) {
                file.mType = TYPE_EF;
                file.mResponseDataStructure = EF_TYPE_TRANSPARENT;
                file.mData = stringToBcd(iccid);
            }
        } else if (path.equals(IccConstants.MF_SIM + IccConstants.DF_TELECOM) && fileid == IccConstants.EF_MSISDN) {
            String[] numbers = getProp(mSimProps, "SubscriberNumbers", new String[0]);
            if (numbers.length > 0) {
                file.mType = TYPE_EF;
                file.mResponseDataStructure = EF_TYPE_LINEAR_FIXED;
                file.mData = new AdnRecord(null, numbers[0]).buildAdnString(14 /*AdnRecord.FOOTER_SIZE*/);
                Rlog.d(TAG, "subscribernumber "+numbers[0]+"="+toDebugString(file.mData));
            }
        } else {
            return null;
        }
        return file;
    }

    private SmsMessage parseSmsPduStrs(String smscPDUStr, String pduStr) {
        if (smscPDUStr == null) {
            smscPDUStr = "00"; // see PduParser; means no smsc
        }
        try {
            return SmsMessage.createFromPdu(IccUtils.hexStringToBytes(smscPDUStr + pduStr));
        } catch (Throwable t) {
            // SmsMessage should have logged information about the error
            return null;
        }
    }

    // opposite of IccUtils#bcdToString
    private byte[] stringToBcd(String str) {
        byte[] ret = new byte[(str.length() / 2) + 1];
        for (int i = 0, j = 0; i < ret.length; i++) {
            ret[i] = (byte) (bcdNibble(str, j++) | (bcdNibble(str, j++) << 4));
        }
        return ret;
    }

    private byte bcdNibble(String s, int i) {
        return i < s.length() ? (byte)(s.charAt(i) - '0') : 0xf;
    }

    public String toDebugString(Object o) {
        if (o instanceof byte[]) {
            return IccUtils.bytesToHexString((byte[])o);
        } else if (o instanceof IccIoResult) {
            IccIoResult iccIoResult = (IccIoResult)o;
            return iccIoResult.toString()+" "+IccUtils.bytesToHexString(iccIoResult.payload);
        } else {
            return String.valueOf(o);
        }
    }

}
