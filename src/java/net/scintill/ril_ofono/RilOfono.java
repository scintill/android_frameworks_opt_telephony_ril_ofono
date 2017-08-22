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
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;
import android.telephony.SignalStrength;
import android.telephony.SmsMessage;
import android.text.TextUtils;

import com.android.internal.telephony.BaseCommands;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.SmsConstants;
import com.android.internal.telephony.SmsResponse;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
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
import org.ofono.VoiceCall;
import org.ofono.VoiceCallManager;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_EDGE;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_GSM;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_UMTS;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
import static com.android.internal.telephony.CommandException.Error.GENERIC_FAILURE;
import static com.android.internal.telephony.CommandException.Error.INVALID_PARAMETER;
import static com.android.internal.telephony.CommandException.Error.MODE_NOT_SUPPORTED;
import static com.android.internal.telephony.CommandException.Error.NO_SUCH_ELEMENT;
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
    private VoiceCallManager mCallManager;

    public RilOfono(Context context, int preferredNetworkType, int cdmaSubscription, Integer instanceId) {
        super(context);
        Rlog.d(TAG, "RilOfono "+BUILD_NUMBER+" starting");

        mPhoneType = RILConstants.NO_PHONE;

        HandlerThread dbusThread = new HandlerThread("dbus");
        dbusThread.start();
        mMainHandler = new Handler(new EmptyHandlerCallback());
        mDbusHandler = new Handler(dbusThread.getLooper(), new EmptyHandlerCallback());

        mDbusHandler.post(new Runnable() {
            @SuppressWarnings("unchecked")
            @Override
            public void run() {
                try {
                    mDbus = DBusConnection.getConnection(DBUS_ADDRESS);
                    mModem = mDbus.getRemoteObject(OFONO_BUS_NAME, MODEM_PATH, Modem.class);
                    mSim = mDbus.getRemoteObject(OFONO_BUS_NAME, MODEM_PATH, SimManager.class);
                    mNetReg = mDbus.getRemoteObject(OFONO_BUS_NAME, MODEM_PATH, NetworkRegistration.class);
                    mMessenger = mDbus.getRemoteObject(OFONO_BUS_NAME, MODEM_PATH, MessageManager.class);
                    mCallManager = mDbus.getRemoteObject(OFONO_BUS_NAME, MODEM_PATH, VoiceCallManager.class);
                    DBusSigHandler sigHandler = new DbusSignalHandler();
                    mDbus.addSigHandler(Manager.ModemAdded.class, sigHandler);
                    mDbus.addSigHandler(Manager.ModemRemoved.class, sigHandler);
                    mDbus.addSigHandler(Modem.PropertyChanged.class, sigHandler);
                    mDbus.addSigHandler(NetworkRegistration.PropertyChanged.class, sigHandler);
                    mDbus.addSigHandler(SimManager.PropertyChanged.class, sigHandler);
                    mDbus.addSigHandler(org.ofono.Message.PropertyChanged.class, sigHandler);
                    mDbus.addSigHandler(MessageManager.IncomingMessage.class, sigHandler);
                    mDbus.addSigHandler(MessageManager.ImmediateMessage.class, sigHandler);
                    mDbus.addSigHandler(VoiceCallManager.CallAdded.class, sigHandler);
                    mDbus.addSigHandler(VoiceCall.PropertyChanged.class, sigHandler);
                    mDbus.addSigHandler(VoiceCallManager.CallRemoved.class, sigHandler);
                    initProps();
                    onModemChange(false); // initialize starting state
                } catch (DBusException e) {
                    logException("RilOfono", e);
                    System.exit(-1); // XXX how to better react to this?
                }
            }
        });

        //mMainHandler.postDelayed(new Tests(this), 10000);
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
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void supplyIccPuk(String puk, String newPin, Message result) {
        supplyIccPukForApp(puk, newPin, null, result);
    }

    @Override
    public void supplyIccPukForApp(String puk, String newPin, String aid, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void supplyIccPin2(String pin2, Message result) {
        supplyIccPin2ForApp(pin2, null, result);
    }

    @Override
    public void supplyIccPin2ForApp(String pin2, String aid, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void supplyIccPuk2(String puk2, String newPin2, Message result) {
        supplyIccPuk2ForApp(puk2, newPin2, null, result);
    }

    @Override
    public void supplyIccPuk2ForApp(String puk2, String newPin2, String aid, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void changeIccPin(String oldPin, String newPin, Message result) {
        changeIccPin2ForApp(oldPin, newPin, null, result);
    }

    @Override
    public void changeIccPinForApp(String oldPin, String newPin, String aidPtr, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void changeIccPin2(String oldPin2, String newPin2, Message result) {
        changeIccPin2ForApp(oldPin2, newPin2, null, result);
    }

    @Override
    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aidPtr, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void supplyDepersonalization(String netpin, String type, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getCurrentCalls(Message result) {
        try {
            List<DriverCall> calls = new ArrayList<>(mCallsProps.size());
            Rlog.d(TAG, "mCallsProps= "+mCallsProps); // TODO sensitive info
            for (Map<String, Variant> callProps : mCallsProps.values()) {
                DriverCall call = new DriverCall();
                call.state = Utils.parseOfonoCallState(getProp(callProps, "State", ""));
                call.index = getProp(callProps, PROPNAME_CALL_INDEX, -1);
                if (call.state == null || call.index == -1) {
                    Rlog.e(TAG, "Skipping unknown call: "+callProps); // TODO could be sensitive
                    continue; // <--- skip unknown call
                }

                String lineId = getProp(callProps, "LineIdentification", "");
                if (lineId.length() == 0 || lineId.equals("withheld")) lineId = null;
                call.TOA = PhoneNumberUtils.toaFromString(lineId);
                call.isMpty = false;
                call.isMT = !getProp(callProps, PROPNAME_CALL_MOBORIG, false);
                call.als = 0; // SimulatedGsmCallState
                call.isVoice = true;
                call.isVoicePrivacy = false; // oFono doesn't tell us
                call.number = lineId;
                call.numberPresentation = PhoneConstants.PRESENTATION_UNKNOWN;
                call.name = getProp(callProps, "Name", "");
                call.namePresentation = PhoneConstants.PRESENTATION_UNKNOWN;
                // TODO check if + is shown in number
                calls.add(call);
            }
            Collections.sort(calls); // not sure why, but original RIL does
            respondOk("getCurrentCalls", result, calls);
        } catch (Throwable t) {
            Rlog.e(TAG, "Error getting calls", t); // TODO potentially sensitive info
            respondExc("getCurrentCalls", result, GENERIC_FAILURE, null);
        }
    }

    private final Map<String, Map<String, Variant>> mCallsProps = new HashMap<>();
    private ConcurrentLinkedQueue<Integer> mAvailableCallIndices; {
        mAvailableCallIndices = new ConcurrentLinkedQueue<>();
        // GsmCallTracker.MAX_CONNECTIONS = 7, CDMA allows 8
        for (int i = 1; i <= 7; i++) mAvailableCallIndices.add(i);
    }

    private static final String PROPNAME_CALL_INDEX = "_RilOfono_CallIndex";
    private static final String PROPNAME_CALL_MOBORIG = "_RilOfono_CallMobileOriginating";

    public void handle(VoiceCallManager.CallAdded s) {
        String callPath = s.path.getPath();
        Rlog.d(TAG, "handle CallAdded "+ callPath);
        Map<String, Variant> newCallProps = new HashMap<>(s.properties);
        newCallProps.put(PROPNAME_CALL_INDEX, new Variant<>(mAvailableCallIndices.remove()));
        putOrMerge2dProps(mCallsProps, callPath, newCallProps);

        mMainHandler.post(mFnNotifyCallStateChanged);
    }

    public void handle(VoiceCall.PropertyChanged s) {
        handle2dPropChange(mCallsProps, s.getPath(), VoiceCall.class, s.name, s.value);
        postDebounced(mMainHandler, mFnNotifyCallStateChanged, 200);
    }

    public void handle(VoiceCallManager.CallRemoved s) {
        String callPath = s.path.getPath();
        Rlog.d(TAG, "handle CallRemoved");
        int callIndex = getProp(mCallsProps.get(callPath), PROPNAME_CALL_INDEX, -1);
        mCallsProps.remove(callPath);
        if (callIndex != -1) mAvailableCallIndices.add(callIndex);
        mMainHandler.post(mFnNotifyCallStateChanged);
    }

    private String getDbusPathForCallIndex(int i) {
        synchronized (mCallsProps) {
            for (Map.Entry<String, Map<String, Variant>> entry : mCallsProps.entrySet()) {
                if (getProp(entry.getValue(), PROPNAME_CALL_INDEX, -1) == i) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    @Override
    public void getPDPContextList(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getDataCallList(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void dial(final String address, int clirMode, final Message result) {
        final String clirModeStr;
        switch (clirMode) {
            case CLIR_DEFAULT: clirModeStr = "default"; break;
            case CLIR_INVOCATION: clirModeStr = "enabled"; break;
            case CLIR_SUPPRESSION: clirModeStr = "disabled"; break;
            default:
                throw new IllegalArgumentException("unknown CLIR constant "+clirMode);
        }

        mDbusHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Path dialedCallPath = mCallManager.Dial(address, clirModeStr);
                    Map<String, Variant> dialedCallProps = new HashMap<>();
                    dialedCallProps.put(PROPNAME_CALL_MOBORIG, new Variant<>(true));
                    putOrMerge2dProps(mCallsProps, dialedCallPath.getPath(), dialedCallProps);
                    Rlog.d(TAG, "dialed "+dialedCallPath.getPath());
                    respondOk("dial", result, null);
                } catch (Throwable t) {
                    Rlog.e(TAG, "Error dialing", t); // TODO possibly sensitive information
                    respondExc("dial", result, GENERIC_FAILURE, null);
                }
            }
        });
    }

    @Override
    public void dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        if (uusInfo != null) {
            respondExc(getCallerMethodName(), result, MODE_NOT_SUPPORTED, null);
        } else {
            dial(address, clirMode, result);
        }
    }

    @Override
    public void hangupConnection(final int gsmIndex, final Message result) {
        mDbusHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    String callPath = getDbusPathForCallIndex(gsmIndex);
                    if (callPath == null) {
                        respondExc("hangupConnection", result, NO_SUCH_ELEMENT, null);
                        return;
                    }
                    VoiceCall call = mDbus.getRemoteObject(OFONO_BUS_NAME, callPath, VoiceCall.class);
                    call.Hangup();
                    respondOk("hangupConnection", result, null);
                } catch (Throwable t) {
                    Rlog.e(TAG, "Error hanging up", t); // TODO could be sensitive info
                    respondExc("hangupConnection", result, GENERIC_FAILURE, null);
                }
            }
        });
    }

    @Override
    public void hangupWaitingOrBackground(final Message result) {
        mDbusHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean oneSucceeded = false, oneExcepted = false;
                for (Map.Entry<String, Map<String, Variant>> callPropsEntry : mCallsProps.entrySet()) {
                    String callPath = callPropsEntry.getKey();
                    Map<String, Variant> callProps = callPropsEntry.getValue();
                    try {
                        DriverCall.State callState = Utils.parseOfonoCallState(getProp(callProps, "State", ""));
                        // TODO which states should be hungup? should we only hang up one?
                        if (callState != null) {
                            switch (callState) {
                                case INCOMING:
                                case HOLDING:
                                case WAITING:
                                    VoiceCall call = mDbus.getRemoteObject(OFONO_BUS_NAME, callPath, VoiceCall.class);
                                    call.Hangup();
                                    oneSucceeded = true;
                                    break;
                                default:
                                    // skip
                            }
                        }
                    } catch (Throwable t) {
                        oneExcepted = true;
                        Rlog.e(TAG, "Error checking/hangingup call", t); // TODO could be sensitive
                    }
                }

                if (oneSucceeded) {
                    respondOk("hangupWaitingOrBackground", result, null);
                } else if (oneExcepted) {
                    respondExc("hangupWaitingOrBackground", result, GENERIC_FAILURE, null);
                } else {
                    respondExc("hangupWaitingOrBackground", result, NO_SUCH_ELEMENT, null);
                }
            }
        });
    }

    @Override
    public void acceptCall(final Message result) {
        mDbusHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    for (Map.Entry<String, Map<String, Variant>> callPropsEntry : mCallsProps.entrySet()) {
                        String callPath = callPropsEntry.getKey();
                        Map<String, Variant> callProps = callPropsEntry.getValue();
                        if (Utils.parseOfonoCallState(getProp(callProps, "State", "")) == DriverCall.State.INCOMING) {
                            VoiceCall call = mDbus.getRemoteObject(OFONO_BUS_NAME, callPath, VoiceCall.class);
                            call.Answer();
                            respondOk("acceptCall", result, null);
                        }
                    }
                } catch (Throwable t) {
                    Rlog.e(TAG, "Error accepting call", t); // TODO sensitive
                    respondExc("acceptCall", result, GENERIC_FAILURE, null);
                }
            }
        });
    }

    @Override
    public void rejectCall(Message result) {
        // TODO RIL.java sends UDUB, which may not be the same as what we're indirectly asking oFono to do here
        hangupWaitingOrBackground(result);
    }

    @Override
    public void hangupForegroundResumeBackground(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void switchWaitingOrHoldingAndActive(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void conference(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setPreferredVoicePrivacy(boolean enable, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getPreferredVoicePrivacy(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void separateConnection(int gsmIndex, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void explicitCallTransfer(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getLastCallFailCause(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getLastPdpFailCause(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getLastDataCallFailCause(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setMute(boolean enableMute, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getMute(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
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
            respondOk("getIMSIForApp", result, imsi, true);
        } else {
            respondExc("getIMSIForApp", result, GENERIC_FAILURE, null);
        }
    }

    @Override
    public void getIMEI(Message result) {
        // TODO GSM-specific?
        respondOk("getIMEI", result, getProp(mModemProps, "Serial", ""), true);
    }

    @Override
    public void getIMEISV(Message result) {
        // TODO GSM-specific?
        respondOk("getIMEISV", result, getProp(mModemProps, "SoftwareVersionNumber", ""), true);
    }


    @Override
    public void getSignalStrength(Message response) {
        // TODO I can't seem to find this on the ofono bus, but supposedly it's supported
        // make up a low strength
        SignalStrength s = new SignalStrength(20, 1, -1, -1, -1, -1, -1, true);
        respondOk("getSignalStrength", response, s, true);
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
    private final String STAR_EMOJI = "🌠"; // make it obvious we're running this RIL

    @Override
    public void getOperator(Message response) {
        boolean registered = getProp(mNetRegProps, "Status", OfonoRegistrationState.unknown).isRegistered();
        String name = getProp(mNetRegProps, "Name", "");
        String mcc = getProp(mNetRegProps, "MobileCountryCode", "");
        String mnc = getProp(mNetRegProps, "MobileNetworkCode", "");
        name = STAR_EMOJI + name + STAR_EMOJI;
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
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void startDtmf(char c, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void stopDtmf(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void sendBurstDtmf(String dtmfString, int on, int off, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
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

    private void handleIncomingMessage(String content, Map<String, Variant> info) {
        String dateStr = (String) info.get("SentTime").getValue();
        String sender = (String) info.get("Sender").getValue();

        // note: sensitive data
        //Rlog.d(TAG, "handleIncomingMessage "+sender+" "+dateStr+" "+content);

        Date date = Utils.parseOfonoDate(dateStr);
        if (date == null) {
            Rlog.e(TAG, "error parsing SMS date "+dateStr);
            date = new Date();
        }

        final Object msg = createReceivedMessage(sender, content, date, getProp(info, "Immediate", false));
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mGsmSmsRegistrant.notifyResult(msg);
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

    @Override
    public void sendCdmaSms(byte[] pdu, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void sendImsGsmSms(String smscPDU, String pdu, int retry, int messageRef, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void deleteSmsOnSim(int index, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void deleteSmsOnRuim(int index, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void writeSmsToRuim(int status, String pdu, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
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
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void iccIO(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, Message response) {
        iccIOForApp(command, fileid, path, p1, p2, p3, data, pin2, null, response);
    }

    @Override
    public void iccIOForApp(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, String aid, Message response) {
        String humanPath = path + "/" + Integer.toHexString(fileid);
        // note: could be sensitive data
        //Rlog.d(TAG, "iccIO " + command + " " + humanPath + " " + p1 + " " + p2 + " " + p3 + " " + data + " " + pin2 + " " + aid);

        // note: responses could be sensitive data
        final int COMMAND_GET_RESPONSE = 0xc0;
        final int COMMAND_READ_BINARY = 0xb0;
        final int COMMAND_READ_RECORD = 0xb2;
        if (command == COMMAND_GET_RESPONSE) {
            SimFile file = getSimFile(path, fileid);
            if (file != null) {
                respondOk("iccIOForApp GetResponse " + humanPath, response, new IccIoResult(0x90, 0x00, file.getResponse()), true);
            } else {
                respondOk("iccIOForApp GetResponse " + humanPath, response, new IccIoResult(0x94, 0x00, new byte[0]), true);
            }
        } else if (command == COMMAND_READ_BINARY) {
            int offset = p1 << 8 + p2;
            int length = p3 & 0xff;
            SimFile file = getSimFile(path, fileid);
            if (file != null) {
                byte[] filePiece = new byte[length];
                System.arraycopy(file.mData, offset, filePiece, 0, length);
                respondOk("iccIOForApp ReadBinary " + humanPath, response, new IccIoResult(0x90, 0x00, filePiece), true);
            } else {
                respondOk("iccIOForApp ReadBinary " + humanPath, response, new IccIoResult(0x94, 0x00, new byte[0]), true);
            }
        } else if (command == COMMAND_READ_RECORD) {
            // TODO ignoring some semantics of READ_RECORD...
            SimFile file = getSimFile(path, fileid);
            if (file != null) {
                respondOk("iccIOForApp ReadRecord " + humanPath, response, new IccIoResult(0x90, 0x00, file.mData), true);
            } else {
                respondOk("iccIOForApp ReadRecord " + humanPath, response, new IccIoResult(0x94, 0x00, new byte[0]), true);
            }
        } else {
            respondExc("iccIOForApp "+command+" "+humanPath, response, REQUEST_NOT_SUPPORTED, null, true);
        }
    }

    @Override
    public void queryCLIP(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getCLIR(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setCLIR(int clirMode, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void queryCallWaiting(int serviceClass, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setCallWaiting(boolean enable, int serviceClass, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setCallForward(int action, int cfReason, int serviceClass, String number, int timeSeconds, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void queryCallForwardStatus(int cfReason, int serviceClass, String number, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setNetworkSelectionModeAutomatic(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setNetworkSelectionModeManual(String operatorNumeric, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
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
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getBasebandVersion(Message response) {
        respondOk("getBaseBandVersion", response, getProp(mModemProps, "Revision", ""), true);
    }

    @Override
    public void queryFacilityLock(String facility, String password, int serviceClass, Message response) {
        queryFacilityLockForApp(facility, password, serviceClass, null, response);
    }

    @Override
    public void queryFacilityLockForApp(String facility, String password, int serviceClass, String appId, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setFacilityLock(String facility, boolean lockState, String password, int serviceClass, Message response) {
        setFacilityLockForApp(facility, lockState, password, serviceClass, null, response);
    }

    @Override
    public void setFacilityLockForApp(String facility, boolean lockState, String password, int serviceClass, String appId, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void sendUSSD(String ussdString, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void cancelPendingUssd(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void resetRadio(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setBandMode(int bandMode, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void queryAvailableBandMode(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setPreferredNetworkType(int networkType, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getPreferredNetworkType(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getNeighboringCids(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setLocationUpdates(boolean enable, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getSmscAddress(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setSmscAddress(String address, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void reportSmsMemoryStatus(boolean available, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void reportStkServiceIsRunning(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);    }

    @Override
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);    }

    @Override
    public void sendTerminalResponse(String contents, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void sendEnvelope(String contents, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void sendEnvelopeWithStatus(String contents, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void handleCallSetupRequestFromSim(boolean accept, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setGsmBroadcastActivation(boolean activate, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getGsmBroadcastConfig(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getDeviceIdentity(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getCDMASubscription(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void sendCDMAFeatureCode(String FeatureCode, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setPhoneType(int phoneType) {
        mPhoneType = phoneType;
    }

    @Override
    public void queryCdmaRoamingPreference(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setCdmaSubscriptionSource(int cdmaSubscriptionType, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
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
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setupDataCall(String radioTechnology, String profile, String apn, String user, String password, String authType, String protocol, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void deactivateDataCall(int cid, int reason, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setCdmaBroadcastActivation(boolean activate, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null, true);
    }

    @Override
    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null, true);
    }

    @Override
    public void getCdmaBroadcastConfig(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null, true);
    }

    @Override
    public void exitEmergencyCallbackMode(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
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

        respondOk("getIccCardStatus", result, cardStatus, true);
    }

    @Override
    public void requestIsimAuthentication(String nonce, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void requestIccSimAuthentication(int authContext, String data, String aid, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
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
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setCellInfoListRate(int rateInMillis, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void setInitialAttachApn(String apn, String protocol, int authType, String username, String password, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void nvReadItem(int itemID, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void nvWriteItem(int itemID, String itemValue, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void nvResetConfig(int resetType, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    public void getHardwareConfig(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
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
    public void handle(NetworkRegistration.PropertyChanged s) { handlePropChange(mNetReg, s.name, s.value); }
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
        handlePropChange(mModemProps, Modem.class.getSimpleName(), name, value);
        if (name.equals("Online")) {
            final boolean online = (Boolean) value.getValue();
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    setRadioState(online ? RadioState.RADIO_ON : RadioState.RADIO_OFF);
                }
            });
        }
    }

    private void handlePropChange(NetworkRegistration netReg, String name, Variant value) {
        handlePropChange(mNetRegProps, NetworkRegistration.class.getSimpleName(), name, value);
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
        handlePropChange(mSimProps, SimManager.class.getSimpleName(), name, value);
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

    private void handlePropChange(Map<String, Variant> propsToUpdate, String thingChangingDebugRef, String name, Variant value) {
        // some of these are sensitive enough they shouldn't be logged
        Rlog.d(TAG, thingChangingDebugRef + " propchange: " + name + "=" + value);
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (propsToUpdate) {
            propsToUpdate.put(name, value);
        }
    }

    private void handle2dPropChange(Map<String, Map<String, Variant>> propsToUpdateRoot, String keyToUpdate, Class<?extends DBusInterface> dbusObIface, String name, Variant value) {
        Map<String, Variant> propsToUpdate = propsToUpdateRoot.get(keyToUpdate);
        if (propsToUpdate == null) {
            propsToUpdateRoot.put(keyToUpdate, propsToUpdate = new HashMap<>());
        }
        handlePropChange(propsToUpdate, dbusObIface.getSimpleName()+" "+keyToUpdate, name, value);
    }

    private void putOrMerge2dProps(Map<String, Map<String, Variant>> rootProps, String key, Map<String, Variant> props) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (rootProps) {
            if (!rootProps.containsKey(key)) {
                rootProps.put(key, props);
            } else {
                // retain call origination properties
                rootProps.get(key).putAll(props);
            }
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
        // TODO call VoiceManager GetCalls() ? oFono docs on that method suggest you should at startup
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
    // things I noticed BaseCommands overrides but has an empty implementation we might need to override:
    // getDataCallProfile(), getModemCapability(),
    // setUiccSubscription(), setDataProfile(), setDataAllowed(), requestShutdown(),
    // iccOpenLogicalChannel(), iccCloseLogicalChannel(), iccTransmitApduLogicalChannel(),
    // iccTransmitApduBasicChannel(), getAtr(), setLocalCallHold()

    private String getCallerMethodName() {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        return elements[3].getMethodName();
    }

    private static final String OFONO_BUS_NAME = "org.ofono";
    private static final String MODEM_PATH = "/ril_0";

    private class EmptyHandlerCallback implements Handler.Callback {
        @Override
        public boolean handleMessage(Message msg) {
            return true;
        }
    }

    private class DbusSignalHandler implements DBusSigHandler {
        public void handle(DBusSignal s) {
            try {
                RilOfono.class.getMethod("handle", s.getClass()).invoke(RilOfono.this, s);
            } catch (IllegalAccessException | NoSuchMethodException e) {
                Rlog.e(TAG, "Unexpected exception while delegating dbus signal", e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    }

    private void respondOk(String caller, Message response, Object o) {
        respondOk(caller, response, o, false);
    }

    private void respondOk(String caller, Message response, Object o, boolean quiet) {
        // TODO at least some of these are sensitive enough they shouldn't be logged
        if (!quiet) {
            Rlog.d(TAG, "respondOk from " + caller + ": " + toDebugString(o));
        }
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

    // mostly a tag type to remind me use this correctly (only construct one for each purpose)
    abstract class DebouncedRunnable implements Runnable {}
    DebouncedRunnable mFnNotifyNetworkChanged;
    DebouncedRunnable mFnNotifySimChanged;
    final DebouncedRunnable mFnNotifyCallStateChanged = new DebouncedRunnable() {
        @Override
        public void run() {
            mCallStateRegistrants.notifyResult(null);
        }
    };

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
            int dataCodingScheme = callPrivateMethod(msg, Integer.class, "getDataCodingScheme");
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

    public CharSequence toDebugString(Object o) {
        if (o instanceof byte[]) {
            return IccUtils.bytesToHexString((byte[])o);
        } else if (o instanceof IccIoResult) {
            IccIoResult iccIoResult = (IccIoResult)o;
            return iccIoResult.toString()+" "+IccUtils.bytesToHexString(iccIoResult.payload);
        } else if (o instanceof Object[]) {
            return "{"+TextUtils.join(", ", (Object[])o)+"}";
        } else if (o instanceof int[]) {
            Integer[] sa = new Integer[((int[])o).length];
            int i = 0;
            for (int j : (int[]) o) {
                sa[i++] = j;
            }
            return toDebugString(sa);
        } else {
            return String.valueOf(o);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T callPrivateMethod(Object o, Class<T> returnClass, String methodName)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        Method m = o.getClass().getDeclaredMethod(methodName);
        m.setAccessible(true);
        return (T) m.invoke(o);
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
