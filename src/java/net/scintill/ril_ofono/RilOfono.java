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

import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Registrant;
import android.telephony.Rlog;
import android.text.TextUtils;

import com.android.internal.telephony.BaseCommands;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.UUSInfo;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccUtils;

import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.exceptions.DBusException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.android.internal.telephony.CommandException.Error.REQUEST_NOT_SUPPORTED;
import static net.scintill.ril_ofono.Utils.getCallerMethodName;

public class RilOfono extends BaseCommands implements CommandsInterface {

    /*package*/ static final String TAG = "RilOfono";
    private static final int BUILD_NUMBER = 11;
    /*package*/ static final boolean LOG_POTENTIALLY_SENSITIVE_INFO = true;

    /*
     * @TODO What does this mean to consumers? I picked 9 because it's less than 10, which is
     * apparently when the icc*() methods we probably won't support were added.
     */
    private static final int RIL_VERSION = 9;

    private static final String DBUS_ADDRESS = "unix:path=/dev/socket/dbus";

    private Handler mDbusHandler;
    private Handler mMainHandler;

    private ModemModule mModemModule;
    private SmsModule mSmsModule;
    private SimModule mSimModule;
    private CallModule mCallModule;
    private DataConnModule mDataConnModule;
    private SupplementaryServicesModule mSupplSvcsModule;

    private DBusConnection mDbus;

    /*package*/ static RilOfono sInstance;

    public RilOfono(Context context, int preferredNetworkType, int cdmaSubscription, Integer instanceId) {
        super(context);
        sInstance = this;
        Rlog.d(TAG, "RilOfono "+BUILD_NUMBER+" starting");

        mPhoneType = RILConstants.NO_PHONE;

        HandlerThread dbusThread = new HandlerThread("dbus");
        dbusThread.start();
        mMainHandler = new Handler(new EmptyHandlerCallback());
        mDbusHandler = new Handler(dbusThread.getLooper(), new EmptyHandlerCallback());

        try {
            mDbus = DBusConnection.getConnection(DBUS_ADDRESS);
        } catch (DBusException e) {
            logException("RilOfono", e);
            System.exit(-1); // XXX how to better react to this?
        }
        mModemModule = new ModemModule(mVoiceNetworkStateRegistrants, mVoiceRadioTechChangedRegistrants);
        mSmsModule = new SmsModule(new DynamicRegistrantListFromField("mGsmSmsRegistrant")); // TODO gsm-specific
        mSimModule = new SimModule(mIccStatusChangedRegistrants);
        mCallModule = new CallModule(mCallStateRegistrants);
        mDataConnModule = new DataConnModule(mDataNetworkStateRegistrants);
        mSupplSvcsModule = new SupplementaryServicesModule(new DynamicRegistrantListFromField("mUSSDRegistrant"));
        mModemModule.onModemChange(false); // initialize starting state

        // TODO register with TelephonyDevController ?

        //mMainHandler.postDelayed(new Tests(mSmsModule), 10000);
    }

    /*package*/ void onModemAvail() {
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

    @Override
    @RilMethod
    public void getImsRegistrationState(Message result) {
        respondExc("getImsRegistrationState", result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setSuppServiceNotifications(boolean enable, Message result) {
        respondExc("setSuppServiceNotifications", result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void supplyIccPin(String pin, Message result) {
        supplyIccPinForApp(pin, null, result);
    }

    @Override
    @RilMethod
    public void supplyIccPinForApp(String pin, String aid, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void supplyIccPuk(String puk, String newPin, Message result) {
        supplyIccPukForApp(puk, newPin, null, result);
    }

    @Override
    @RilMethod
    public void supplyIccPukForApp(String puk, String newPin, String aid, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void supplyIccPin2(String pin2, Message result) {
        supplyIccPin2ForApp(pin2, null, result);
    }

    @Override
    @RilMethod
    public void supplyIccPin2ForApp(String pin2, String aid, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void supplyIccPuk2(String puk2, String newPin2, Message result) {
        supplyIccPuk2ForApp(puk2, newPin2, null, result);
    }

    @Override
    @RilMethod
    public void supplyIccPuk2ForApp(String puk2, String newPin2, String aid, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void changeIccPin(String oldPin, String newPin, Message result) {
        changeIccPin2ForApp(oldPin, newPin, null, result);
    }

    @Override
    @RilMethod
    public void changeIccPinForApp(String oldPin, String newPin, String aidPtr, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void changeIccPin2(String oldPin2, String newPin2, Message result) {
        changeIccPin2ForApp(oldPin2, newPin2, null, result);
    }

    @Override
    @RilMethod
    public void changeIccPin2ForApp(String oldPin2, String newPin2, String aidPtr, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void changeBarringPassword(String facility, String oldPwd, String newPwd, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void supplyDepersonalization(String netpin, String type, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getCurrentCalls(Message result) {
        mCallModule.getCurrentCalls(result);
    }

    @Override
    @RilMethod
    public void getPDPContextList(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getDataCallList(Message result) {
        mDataConnModule.getDataCallList(result);
    }

    @Override
    @RilMethod
    public void getDataCallProfile(int appType, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setDataProfile(DataProfile[] dps, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setDataAllowed(boolean allowed, Message result) {
        mDataConnModule.setDataAllowed(allowed, result);
    }

    @Override
    @RilMethod
    public void dial(String address, int clirMode, Message result) {
        mCallModule.dial(address, clirMode, result);
    }

    @Override
    @RilMethod
    public void dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        mCallModule.dial(address, clirMode, uusInfo, result);
    }

    @Override
    @RilMethod
    public void hangupForegroundResumeBackground(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void switchWaitingOrHoldingAndActive(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void conference(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setPreferredVoicePrivacy(boolean enable, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getPreferredVoicePrivacy(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void separateConnection(int gsmIndex, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void acceptCall(Message result) {
        mCallModule.acceptCall(result);
    }

    @Override
    @RilMethod
    public void rejectCall(Message result) {
        mCallModule.rejectCall(result);
    }

    @Override
    @RilMethod
    public void explicitCallTransfer(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getLastCallFailCause(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getLastPdpFailCause(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getLastDataCallFailCause(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setMute(boolean enableMute, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getMute(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getSignalStrength(Message response) {
        mModemModule.getSignalStrength(response);
    }

    @Override
    @RilMethod
    public void getVoiceRegistrationState(Message response) {
        mModemModule.getVoiceRegistrationState(response);
    }

    @Override
    @RilMethod
    public void getIMSI(Message result) {
        getIMSIForApp(null, result);
    }

    @Override
    @RilMethod
    public void getIMSIForApp(String aid, Message result) {
        mSimModule.getIMSIForApp(aid, result);
    }

    @Override
    @RilMethod
    public void getIMEI(Message result) {
        mModemModule.getIMEI(result);
    }

    @Override
    @RilMethod
    public void getIMEISV(Message result) {
        mModemModule.getIMEISV(result);
    }

    @Override
    @RilMethod
    public void hangupConnection(int gsmIndex, Message result) {
        mCallModule.hangupConnection(gsmIndex, result);
    }

    @Override
    @RilMethod
    public void hangupWaitingOrBackground(Message result) {
        mCallModule.hangupWaitingOrBackground(result);
    }

    @Override
    @RilMethod
    public void getDataRegistrationState(Message response) {
        mDataConnModule.getDataRegistrationState(response);
    }

    @Override
    @RilMethod
    public void getOperator(Message response) {
        mModemModule.getOperator(response);
    }

    @Override
    @RilMethod
    public void sendDtmf(char c, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void startDtmf(char c, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void stopDtmf(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void sendBurstDtmf(String dtmfString, int on, int off, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void sendSMS(String smscPDUStr, String pduStr, Message response) {
        mSmsModule.sendSMS(smscPDUStr, pduStr, response);
    }

    @Override
    @RilMethod
    public void sendSMSExpectMore(String smscPDU, String pdu, Message result) {
        // TODO can oFono benefit from knowing to "expect more"?
        sendSMS(smscPDU, pdu, result);
    }

    @Override
    @RilMethod
    public void sendCdmaSms(byte[] pdu, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void sendImsGsmSms(String smscPDU, String pdu, int retry, int messageRef, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void sendImsCdmaSms(byte[] pdu, int retry, int messageRef, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void deleteSmsOnSim(int index, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void deleteSmsOnRuim(int index, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void writeSmsToSim(int status, String smsc, String pdu, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void writeSmsToRuim(int status, String pdu, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setRadioPower(boolean on, Message response) {
        mModemModule.setRadioPower(on, response);
    }

    @Override
    @RilMethod
    public void acknowledgeLastIncomingGsmSms(boolean success, int cause, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void acknowledgeLastIncomingCdmaSms(boolean success, int cause, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void iccIO(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, Message response) {
        iccIOForApp(command, fileid, path, p1, p2, p3, data, pin2, null, response);
    }

    @Override
    @RilMethod
    public void iccIOForApp(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, String aid, Message response) {
        mSimModule.iccIOForApp(command, fileid, path, p1, p2, p3, data, pin2, aid, response);
    }

    @Override
    @RilMethod
    public void queryCLIP(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getCLIR(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setCLIR(int clirMode, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void queryCallWaiting(int serviceClass, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setCallWaiting(boolean enable, int serviceClass, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setCallForward(int action, int cfReason, int serviceClass, String number, int timeSeconds, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void queryCallForwardStatus(int cfReason, int serviceClass, String number, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setNetworkSelectionModeAutomatic(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setNetworkSelectionModeManual(String operatorNumeric, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getNetworkSelectionMode(Message response) {
        mModemModule.getNetworkSelectionMode(response);
    }

    @Override
    @RilMethod
    public void getAvailableNetworks(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getBasebandVersion(Message response) {
        mModemModule.getBasebandVersion(response);
    }

    @Override
    @RilMethod
    public void queryFacilityLock(String facility, String password, int serviceClass, Message response) {
        queryFacilityLockForApp(facility, password, serviceClass, null, response);
    }

    @Override
    @RilMethod
    public void queryFacilityLockForApp(String facility, String password, int serviceClass, String appId, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setFacilityLock(String facility, boolean lockState, String password, int serviceClass, Message response) {
        setFacilityLockForApp(facility, lockState, password, serviceClass, null, response);
    }

    @Override
    @RilMethod
    public void setFacilityLockForApp(String facility, boolean lockState, String password, int serviceClass, String appId, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void sendUSSD(String ussdString, Message response) {
        mSupplSvcsModule.sendUSSD(ussdString, response);
    }

    @Override
    @RilMethod
    public void cancelPendingUssd(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void resetRadio(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setBandMode(int bandMode, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void queryAvailableBandMode(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setPreferredNetworkType(int networkType, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getPreferredNetworkType(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getNeighboringCids(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setLocationUpdates(boolean enable, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getSmscAddress(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setSmscAddress(String address, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void reportSmsMemoryStatus(boolean available, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void reportStkServiceIsRunning(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);    }

    @Override
    @RilMethod
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);    }

    @Override
    @RilMethod
    public void sendTerminalResponse(String contents, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void sendEnvelope(String contents, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void sendEnvelopeWithStatus(String contents, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void handleCallSetupRequestFromSim(boolean accept, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setGsmBroadcastActivation(boolean activate, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getGsmBroadcastConfig(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getDeviceIdentity(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getCDMASubscription(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void sendCDMAFeatureCode(String FeatureCode, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setPhoneType(int phoneType) {
        mPhoneType = phoneType;
    }

    @Override
    @RilMethod
    public void queryCdmaRoamingPreference(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setCdmaSubscriptionSource(int cdmaSubscriptionType, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getCdmaSubscriptionSource(Message response) {
        respondExc("getCdmaSubscriptionSource", response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setTTYMode(int ttyMode, Message response) {
        respondExc("setTTYMode", response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void queryTTYMode(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setupDataCall(String radioTechnology, String profile, String apn, String user, String password, String authType, String protocol, Message result) {
        mDataConnModule.setupDataCall(radioTechnology, profile, apn, user, password, authType, protocol, result);
    }

    @Override
    @RilMethod
    public void deactivateDataCall(int cid, int reason, Message result) {
        mDataConnModule.deactivateDataCall(cid, reason, result);
    }

    @Override
    @RilMethod
    public void setCdmaBroadcastActivation(boolean activate, Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null, true);
    }

    @Override
    @RilMethod
    public void setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null, true);
    }

    @Override
    @RilMethod
    public void getCdmaBroadcastConfig(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null, true);
    }

    @Override
    @RilMethod
    public void exitEmergencyCallbackMode(Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getIccCardStatus(Message result) {
        mSimModule.getIccCardStatus(result);
    }

    @Override
    @RilMethod
    public void requestIsimAuthentication(String nonce, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void requestIccSimAuthentication(int authContext, String data, String aid, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getVoiceRadioTechnology(Message result) {
        mModemModule.getVoiceRadioTechnology(result);
    }

    @Override
    @RilMethod
    public void getCellInfoList(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setCellInfoListRate(int rateInMillis, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void setInitialAttachApn(String apn, String protocol, int authType, String username, String password, Message result) {
        mDataConnModule.setInitialAttachApn(apn, protocol, authType, username, password, result);
    }

    @Override
    @RilMethod
    public void nvReadItem(int itemID, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void nvWriteItem(int itemID, String itemValue, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void nvWriteCdmaPrl(byte[] preferredRoamingList, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void nvResetConfig(int resetType, Message response) {
        respondExc(getCallerMethodName(), response, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public void getHardwareConfig(Message result) {
        respondExc(getCallerMethodName(), result, REQUEST_NOT_SUPPORTED, null);
    }

    @Override
    @RilMethod
    public boolean needsOldRilFeature(String feature) {
        return false;
    }

    @Override
    @RilMethod // just to make it accessible in this package
    protected void setRadioState(RadioState newState) {
        super.setRadioState(newState);
    }

    /*package*/ static void logException(String m, Throwable t) {
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
    // getModemCapability(),
    // setUiccSubscription(), requestShutdown(),
    // iccOpenLogicalChannel(), iccCloseLogicalChannel(), iccTransmitApduLogicalChannel(),
    // iccTransmitApduBasicChannel(), getAtr(), setLocalCallHold()

    private static final String OFONO_BUS_NAME = "org.ofono";
    private static final String MODEM_PATH = "/ril_0";

    /*package*/ <T extends DBusInterface> T getOfonoInterface(Class<T> tClass) {
        return getOfonoInterface(tClass, MODEM_PATH);
    }

    /*package*/ <T extends DBusInterface> T getOfonoInterface(Class<T> tClass, String path) {
        try {
            return mDbus.getRemoteObject(OFONO_BUS_NAME, path, tClass);
        } catch (Throwable t) {
            Rlog.e(TAG, "Exception getting "+tClass.getSimpleName(), t);
            return null;
        }
    }

    /*package*/ <T extends DBusSignal> void registerDbusSignal(Class<T> signalClass, DBusSigHandler<T> handler) {
        try {
            mDbus.addSigHandler(signalClass, handler);
        } catch (DBusException e) {
            throw new RuntimeException(e);
        }
    }

    /*package*/ <T extends DBusSignal> void registerDbusSignal(Class<T> signalClass, final Object handler) {
        try {
            final Method m = handler.getClass().getMethod("handle", signalClass);
            mDbus.addSigHandler(signalClass, new DBusSigHandler<T>() {
                @Override
                public void handle(T s) {
                    try {
                        m.invoke(handler, s);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Unexpected exception while delegating dbus signal", e);
                    } catch (InvocationTargetException e) {
                        Rlog.e(TAG, "Unexpected exception while delegating dbus signal", e.getCause());
                        // do not re-throw
                    }
                }
            });
        } catch (DBusException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Unable to register dbus signal handler", e);
        }
    }

    private class EmptyHandlerCallback implements Handler.Callback {
        @Override
        public boolean handleMessage(Message msg) {
            return true;
        }
    }

    /*package*/ static void respondOk(String caller, Message response, Object o) {
        respondOk(caller, response, o, false);
    }

    /*package*/ static void respondOk(String caller, Message response, Object o, boolean quiet) {
        PrivResponseOb privResponseOb = null;
        if (o instanceof PrivResponseOb) {
            privResponseOb = (PrivResponseOb) o;
            o = privResponseOb.o;
        }

        if (!quiet) {
            CharSequence debugString = toDebugString(o, privResponseOb != null);
            Rlog.d(TAG, "respondOk from " + caller + ": " + debugString);
        }
        if (response != null) {
            AsyncResult.forMessage(response, o, null);
            response.sendToTarget();
        }
    }

    /*package*/ static void respondExc(String caller, Message response, CommandException.Error err, Object o) {
        respondExc(caller, response, err, o, false);
    }

    /*package*/ static void respondExc(String caller, Message response, CommandException.Error err, Object o, boolean quiet) {
        PrivResponseOb privResponseOb = null;
        if (o instanceof PrivResponseOb) {
            privResponseOb = (PrivResponseOb) o;
            o = privResponseOb.o;
        }

        if (!quiet) {
            CharSequence debugString = toDebugString(o, privResponseOb != null);
            Rlog.d(TAG, "respondExc from "+caller+": "+err+" "+debugString);
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

    private static void postDebounced(Handler h, DebouncedRunnable r, long delayMillis) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (h) {
            if (!h.hasCallbacks(r)) {
                h.sendMessageDelayed(Message.obtain(h, r), delayMillis);
            }
        }
    }

    /*package*/ static void runOnDbusThread(Runnable r) {
        sInstance.mDbusHandler.post(r);
    }

    /*package*/ static void runOnMainThread(Runnable r) {
        sInstance.mMainHandler.post(r);
    }

    /*package*/ static void runOnMainThreadDebounced(DebouncedRunnable r, long delayMillis) {
        postDebounced(sInstance.mMainHandler, r, delayMillis);
    }

    public static CharSequence toDebugString(Object o) {
        return toDebugString(o, false);
    }

    public static CharSequence toDebugString(Object o, boolean isSensitive) {
        if (isSensitive && !LOG_POTENTIALLY_SENSITIVE_INFO) {
            // we could potentially have type-specific partial dumping here
            return privStr("");
        }

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

    /*package*/ static String privStr(Object o) {
        //noinspection ConstantConditions
        return LOG_POTENTIALLY_SENSITIVE_INFO ? String.valueOf(o) : "XXXXX";
    }

    /*package*/ static Throwable privExc(Throwable t) {
        // TODO find a way to pass back the safe parts instead of null?
        //noinspection ConstantConditions
        return LOG_POTENTIALLY_SENSITIVE_INFO ? t : null;
    }

    /*
     * Works similar to Android's RegistrantList.
     */
    /*package*/ interface RegistrantList {
        void notifyResult(Object result);
    }

    /*
     * Helps paper over the difference between a single registrant stored in a mutable field, and a list
     * of registrants.
     */
    class DynamicRegistrantListFromField implements RegistrantList {
        Field mField;
        DynamicRegistrantListFromField(String fieldname) {
            try {
                mField = Utils.getField(RilOfono.this, fieldname);
                mField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("unable to create dynamic registrant list from field "+fieldname, e);
            }
        }

        @Override
        public void notifyResult(Object result) {
            try {
                Registrant registrant = (Registrant) mField.get(RilOfono.this);
                registrant.notifyResult(result);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("unable to get registrant", e);
            }
        }
    }

}

// mostly a tag type to remind me use this correctly (only construct one for each purpose)
/*package*/ abstract class DebouncedRunnable implements Runnable {}

// simple human-oriented marker saying that the annotated method is part of the RIL interface
// (useful for the module classes that mix more internal methods)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD})
/*package*/ @interface RilMethod {
}
