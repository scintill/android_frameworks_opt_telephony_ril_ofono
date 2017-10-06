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
import android.annotation.Nullable;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.INetworkManagementService;
import android.os.Message;
import android.os.ServiceManager;
import android.telephony.Rlog;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Pair;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo;
import com.android.internal.telephony.dataconnection.DataProfile;
import com.android.internal.telephony.gsm.SmsBroadcastConfigInfo;
import com.android.internal.telephony.uicc.IccIoResult;
import com.android.internal.telephony.uicc.IccUtils;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
import org.ofono.ConnectionContext;
import org.ofono.ConnectionManager;
import org.ofono.Manager;
import org.ofono.MessageManager;
import org.ofono.MessageWaiting;
import org.ofono.Modem;
import org.ofono.NetworkRegistration;
import org.ofono.PathAndProperties;
import org.ofono.SimManager;
import org.ofono.SupplementaryServices;
import org.ofono.VoiceCall;
import org.ofono.VoiceCallManager;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.android.internal.telephony.CommandException.Error.REQUEST_NOT_SUPPORTED;
import static com.android.internal.telephony.CommandsInterface.RadioState;

/*package*/ class RilOfono extends PropManager implements RilMiscInterface {

    /*package*/ static final String TAG = "RilOfono";
    private static final int BUILD_NUMBER = 12;
    /*package*/ static final boolean LOG_POTENTIALLY_SENSITIVE_INFO = true;

    private static final String DBUS_ADDRESS = "unix:path=/dev/socket/dbus";

    /*package*/ RilMiscInterface mMiscModule;
    /*package*/ NetworkRegistrationModule mNetworkRegistrationModule;
    /*package*/ ModemModule mModemModule;
    /*package*/ SmsModule mSmsModule;
    /*package*/ SimModule mSimModule;
    /*package*/ VoicecallModule mVoicecallModule;
    /*package*/ DatacallModule mDatacallModule;
    /*package*/ SupplementaryServicesModule mSupplementaryServicesModule;

    private RilWrapperBase mRilWrapper;
    private Handler mDbusHandler;
    private Handler mMainHandler;

    private DBusConnection mDbus;

    private String mModemPath;

    /*package*/ static RilOfono sInstance;

    /*package*/ RilOfono(final RilWrapperBase rilWrapper) {
        sInstance = this;
        Rlog.d(TAG, "RilOfono "+BUILD_NUMBER+" starting");

        mRilWrapper = rilWrapper;

        HandlerThread dbusThread = new HandlerThread("RilOfonoDbusThread");
        dbusThread.start();
        mMainHandler = new Handler(new EmptyHandlerCallback());
        mDbusHandler = new Handler(dbusThread.getLooper(), new EmptyHandlerCallback());

        runOnDbusThread(new Runnable() {
            @Override
            public void run() {
                try {
                    initDbus(DBUS_ADDRESS);
                    checkModemPresence(null);
                } catch (Throwable t) {
                    throw new RuntimeException("exception while loading", t);
                }
            }
        });
        // TODO register with TelephonyDevController ?
    }

    private final Object mModemPresenceMonitor = new Object();
    /*package*/ void checkModemPresence(@Nullable Set<String> modemInterfaces) {
        synchronized (mModemPresenceMonitor) { // for when state is changing rapidly (e.g. oFono dies and restarts) - process one at a time
            try {
                Manager manager = getOfonoInterface(Manager.class, "/");
                List<PathAndProperties> modems = getPoweredModems(manager);
                if (modems.size() > 0 && mModemModule == null) {
                    onModemUp(modems.get(0).path.getPath(), modemInterfaces);
                } else if (modems.size() == 0 && mModemModule != null) {
                    onModemDown();
                }
            } catch (DBus.Error.ServiceUnknown e) {
                Rlog.w(TAG, "checkModemPresence: oFono Manager not found: "+privStr(e.getMessage()));
            } catch (DBus.Error.NoReply e) {
                // this state can be seen if ofonod is killed. it sets Powered=false on modem, triggering this code, then goes off the bus
                Rlog.w(TAG, "checkModemPresence: oFono did not reply: "+privStr(e.getMessage()));
            }
        }
    }

    private void onModemUp(String path, @Nullable Set<String> modemInterfaces) {
        Rlog.v(TAG, "modem up " + path);
        mModemPath = path;

        Modem modem = getOfonoInterface(Modem.class);
        if (modemInterfaces == null) {
            modemInterfaces = new ArraySet<>(Arrays.asList(getProp(modem.GetProperties(), "Interfaces", new String[0])));
        }

        mMiscModule = RilOfono.this;

        // Suppress radio state notifications, because otherwise the ModemModule reports radio is
        // down during its initialization process, then the ServiceStateTracker immediately tries to power the radio,
        // fails because the module is still null, and never tries again.
        startSuppressingRadioStateNotifications();

        mModemModule = new ModemModule(modem);

        try {
            mModemModule.setRadioPower(false); // RIL.java for RIL_UNSOL_RIL_CONNECTED does this
            stopSuppressingRadioStateNotifications(RadioState.RADIO_OFF);
        } catch (Throwable t) {
            Rlog.e(TAG, "onModemAvail: setRadioPower(false) threw an exception", t);
        }

        updateModulesForInterfaces(modemInterfaces);

        // TODO What does this mean to consumers? I picked 9 because it's less than 10, which is
        // apparently when the icc*() methods we won't support were added.
        final int RIL_VERSION = 9;
        mRilWrapper.updateRilConnection(RIL_VERSION);

        // TODO call VoiceManager GetCalls() ? oFono docs on that method suggest you should at startup

        //mMainHandler.postDelayed(new Tests((SmsModule) mRilWrapper.mSmsModule), 10000);
    }

    private final Object updateModulesMonitor = new Object();

    private void updateModulesForInterfaces(@NonNull Set<String> ifaces) {
        Rlog.v(TAG, "updateModulesForInterfaces " + ifaces);

        synchronized (updateModulesMonitor) {
            // this is kind of yucky, but the point is to avoid repetition or reflection. We want
            // identical exception handling wrapped around each of the calls, but want to continue
            // to the next if an exception is encountered.
            for (Class<?> cl : new Class<?>[] {
                    NetworkRegistration.class, MessageManager.class, SimManager.class, VoiceCallManager.class,
                    ConnectionManager.class, SupplementaryServices.class,
            }) {
                try {
                    if (cl == NetworkRegistration.class) {
                        updateModuleForNetworkRegistration(ifaces);
                    } else if (cl == MessageManager.class) {
                        updateModuleForMessageManager(ifaces);
                    } else if (cl == SimManager.class) {
                        updateModuleForSimManager(ifaces);
                    } else if (cl == VoiceCallManager.class) {
                        updateModuleForVoiceCallManager(ifaces);
                    } else if (cl == ConnectionManager.class) {
                        updateModuleForConnectionManager(ifaces);
                    } else if (cl == SupplementaryServices.class) {
                        updateModuleForSupplementaryServices(ifaces);
                    } else {
                        throw new RuntimeException("invalid loop");
                    }
                } catch (DBusException e) {
                    // This should be fatal, because otherwise, in the case of signal handler
                    // removal failing, we have inactive, untracked objects reacting to signals.
                    // Better to crash and get restarted.
                    throw new RuntimeException("Got dbus exception for "+cl, privExc(e));
                } catch (Throwable t) {
                    logUncaughtException("updateModulesForInterfaces "+cl, t);
                }
            }
        }
    }

    private void updateModuleForNetworkRegistration(@NonNull Set<String> ifaces) throws DBusException {
        if (isInterfaceAbsent(NetworkRegistration.class, ifaces)) {
            mNetworkRegistrationModule = null;
        } else if (mNetworkRegistrationModule == null) {
            mNetworkRegistrationModule = new NetworkRegistrationModule(
                    getOfonoInterface(NetworkRegistration.class),
                    mRilWrapper.mVoiceNetworkStateRegistrants, mRilWrapper.mVoiceRadioTechChangedRegistrants, mRilWrapper.mSignalStrengthRegistrants
            );
        }
    }

    private void updateModuleForMessageManager(@NonNull Set<String> ifaces) throws DBusException {
        if (isInterfaceAbsent(MessageManager.class, ifaces)) {
            mSmsModule = null;
        } else if (mSmsModule == null) {
            mSmsModule = new SmsModule(
                    getOfonoInterface(MessageManager.class),
                    mRilWrapper.mGsmSmsRegistrants // TODO gsm-specific
            );
        }
    }

    private void updateModuleForSimManager(@NonNull Set<String> ifaces) throws DBusException {
        if (isInterfaceAbsent(SimManager.class, ifaces)) {
            mSimModule = null;
        } else if (mSimModule == null) {
            mSimModule = new SimModule(
                    getOfonoInterface(SimManager.class),
                    mRilWrapper.mIccStatusChangedRegistrants, mRilWrapper.mIccRefreshRegistrants
            );
        }

        if (isInterfaceAbsent(MessageWaiting.class, ifaces)) {
            if (mSimModule != null) {
                mSimModule.setMessageWaitingIface(null);
            }
        } else if (mSimModule != null && mSimModule.getMessageWaitingIface() == null) {
            mSimModule.setMessageWaitingIface(getOfonoInterface(MessageWaiting.class));
        }
    }

    private void updateModuleForVoiceCallManager(@NonNull Set<String> ifaces) throws DBusException {
        if (isInterfaceAbsent(VoiceCallManager.class, ifaces)) {
            mVoicecallModule = null;
        } else if (mVoicecallModule == null) {
            mVoicecallModule = new VoicecallModule(
                    getOfonoInterface(VoiceCallManager.class),
                    mRilWrapper.mCallStateRegistrants
            );
        }
    }

    private void updateModuleForConnectionManager(@NonNull Set<String> ifaces) throws DBusException {
        if (isInterfaceAbsent(ConnectionManager.class, ifaces) || isInterfaceAbsent(NetworkRegistration.class, ifaces)) {
            mDatacallModule = null;
        } else if (mDatacallModule == null) {
            mDatacallModule = new DatacallModule(
                    getOfonoInterface(ConnectionManager.class),
                    getOfonoInterface(NetworkRegistration.class),
                    mRilWrapper.mDataNetworkStateRegistrants, mRilWrapper.mVoiceNetworkStateRegistrants,
                    INetworkManagementService.Stub.asInterface(ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE))
            );
        }
    }

    private void updateModuleForSupplementaryServices(@NonNull Set<String> ifaces) throws DBusException {
        if (isInterfaceAbsent(SupplementaryServices.class, ifaces)) {
            mSupplementaryServicesModule = null;
        } else if (mSupplementaryServicesModule == null) {
            mSupplementaryServicesModule = new SupplementaryServicesModule(
                    getOfonoInterface(SupplementaryServices.class),
                    mRilWrapper.mUSSDRegistrants
            );
        }
    }

    private boolean isInterfaceAbsent(Class<?> cl, @NonNull Set<String> modemInterfaces) {
        return !modemInterfaces.contains(cl.getName());
    }

    private void onModemDown() {
        Rlog.v(TAG, "modem down");
        mModemPath = null;

        mMiscModule = null;
        mModemModule = null;
        mSmsModule = null;
        mSimModule = null;
        mVoicecallModule = null;
        mDatacallModule = null;
        mSupplementaryServicesModule = null;

        setRadioState(RadioState.RADIO_UNAVAILABLE);

        mRilWrapper.updateRilConnection(-1);
    }

    private void handle(Manager.ModemAdded s) {
        Rlog.v(TAG, "ModemAdded");
        Set<String> modemInterfaces = new ArraySet<>(getProp(s.properties, "Interfaces", new ArrayList<String>(0)));
        checkModemPresence(modemInterfaces);
    }

    private void handle(Manager.ModemRemoved s) {
        Rlog.v(TAG, "ModemRemoved");
        checkModemPresence(null);
    }

    private void handle(Modem.PropertyChanged s) {
        if (s.name.equals("Powered")) {
            Rlog.v(TAG, "ModemPropertyChanged Powered");
            checkModemPresence(null);
        } else if (s.name.equals("Interfaces")) {
            @SuppressWarnings("unchecked") Variant<List<String>> v = s.value;
            updateModulesForInterfaces(new ArraySet<>(v.getValue()));
        }
    }

    private void initDbus(String address) throws DBusException {
        mDbus = DBusConnection.getConnection(address);
        registerDbusSignals(new Class<?>[] {
                MessageManager.IncomingPdu.class, org.ofono.Message.PropertyChanged.class,
                VoiceCallManager.CallAdded.class, VoiceCallManager.CallRemoved.class,
                VoiceCall.PropertyChanged.class, ConnectionManager.ContextAdded.class,
                ConnectionManager.ContextRemoved.class, ConnectionContext.PropertyChanged.class,
                Modem.PropertyChanged.class, NetworkRegistration.PropertyChanged.class,
                SimManager.PropertyChanged.class, MessageWaiting.PropertyChanged.class,
                Manager.ModemAdded.class, Manager.ModemRemoved.class,
        });
    }

    private void handle(DBusSignal s) {
        // take this monitor so that any modules needed by this signal are available
        boolean handled = false;

        synchronized (updateModulesMonitor) {
            if (s instanceof MessageManager.IncomingPdu && mSmsModule != null) {
                mSmsModule.handle((MessageManager.IncomingPdu) s);
                handled = true;
            } else if (s instanceof org.ofono.Message.PropertyChanged && mSmsModule != null) {
                mSmsModule.handle((org.ofono.Message.PropertyChanged) s);
                handled = true;
            } else if (s instanceof VoiceCallManager.CallAdded && mVoicecallModule != null) {
                mVoicecallModule.handle((VoiceCallManager.CallAdded) s);
                handled = true;
            } else if (s instanceof VoiceCallManager.CallRemoved && mVoicecallModule != null) {
                mVoicecallModule.handle((VoiceCallManager.CallRemoved) s);
                handled = true;
            } else if (s instanceof VoiceCall.PropertyChanged && mVoicecallModule != null) {
                mVoicecallModule.handle((VoiceCall.PropertyChanged) s);
                handled = true;
            } else if (s instanceof ConnectionManager.ContextAdded && mDatacallModule != null) {
                mDatacallModule.handle((ConnectionManager.ContextAdded) s);
                handled = true;
            } else if (s instanceof ConnectionManager.ContextRemoved && mDatacallModule != null) {
                mDatacallModule.handle((ConnectionManager.ContextRemoved) s);
                handled = true;
            } else if (s instanceof ConnectionContext.PropertyChanged && mDatacallModule != null) {
                mDatacallModule.handle((ConnectionContext.PropertyChanged) s);
                handled = true;
            } else if (s instanceof ConnectionManager.PropertyChanged && mDatacallModule != null) {
                mDatacallModule.handle((ConnectionManager.PropertyChanged) s);
                handled = true;
            } else if (s instanceof Modem.PropertyChanged) {
                if (mModemModule != null) {
                    mModemModule.handle((Modem.PropertyChanged) s);
                }
                handle((Modem.PropertyChanged) s);
                handled = true;
            } else if (s instanceof NetworkRegistration.PropertyChanged) {
                if (mNetworkRegistrationModule != null) {
                    mNetworkRegistrationModule.handle((NetworkRegistration.PropertyChanged) s);
                    handled = true;
                }
                if (mDatacallModule != null) {
                    mDatacallModule.handle((NetworkRegistration.PropertyChanged) s);
                    handled = true;
                }
            } else if (s instanceof SimManager.PropertyChanged && mSimModule != null) {
                mSimModule.handle((SimManager.PropertyChanged) s);
                handled = true;
            } else if (s instanceof MessageWaiting.PropertyChanged && mSimModule != null) {
                mSimModule.handle((MessageWaiting.PropertyChanged) s);
                handled = true;
            } else if (s instanceof Manager.ModemAdded) {
                handle((Manager.ModemAdded) s);
                handled = true;
            } else if (s instanceof Manager.ModemRemoved) {
                handle((Manager.ModemRemoved) s);
                handled = true;
            }
        }

        if (!handled) {
            Rlog.w(TAG, "Unhandled signal " + s.getClass());
        }
    }

    // an exception catch-all (that logs exceptions with privExc()) is provided
    @SuppressWarnings({"unchecked","rawtypes"})
    /*package*/ void registerDbusSignals(Class<?>[] signalClasses) {
        DBusSigHandler handler = new DBusSigHandler() {
            @Override
            public void handle(DBusSignal s) {
                try {
                    RilOfono.this.handle(s);
                } catch (Throwable t) {
                    logUncaughtException("registerDbusSignals() handler", t);
                    // do not re-throw
                }
            }
        };

        for (Class<?> signalClass : signalClasses) {
            try {
                //Rlog.v(TAG, "addSigHandler "+signalClass.getName()+" "+typedHandler);
                mDbus.addSigHandler((Class<? extends DBusSignal>)signalClass, handler);
            } catch (DBusException e) {
                throw new RuntimeException("Unable to register dbus signal handler", e);
            }
        }
    }

    private List<PathAndProperties> getPoweredModems(Manager manager) {
        List<PathAndProperties> l = new ArrayList<>();
        for (PathAndProperties pathAndProperties : manager.GetModems()) {
            if (pathAndProperties.props.get("Powered").getValue() == Boolean.TRUE) {
                l.add(pathAndProperties);
            }
        }
        return l;
    }

    @Override
    @OkOnMainThread
    public Object getImsRegistrationState() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setSuppServiceNotifications(boolean enable) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object supplyIccPin(String pin) {
        return supplyIccPinForApp(pin, null);
    }

    @Override
    @OkOnMainThread
    public Object supplyIccPinForApp(String pin, String aid) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object supplyIccPuk(String puk, String newPin) {
        return supplyIccPukForApp(puk, newPin, null);
    }

    @Override
    @OkOnMainThread
    public Object supplyIccPukForApp(String puk, String newPin, String aid) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object supplyIccPin2(String pin2) {
        return supplyIccPin2ForApp(pin2, null);
    }

    @Override
    @OkOnMainThread
    public Object supplyIccPin2ForApp(String pin2, String aid) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object supplyIccPuk2(String puk2, String newPin2) {
        return supplyIccPuk2ForApp(puk2, newPin2, null);
    }

    @Override
    @OkOnMainThread
    public Object supplyIccPuk2ForApp(String puk2, String newPin2, String aid) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object changeIccPin(String oldPin, String newPin) {
        return changeIccPin2ForApp(oldPin, newPin, null);
    }

    @Override
    @OkOnMainThread
    public Object changeIccPinForApp(String oldPin, String newPin, String aidPtr) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object changeIccPin2(String oldPin2, String newPin2) {
        return changeIccPin2ForApp(oldPin2, newPin2, null);
    }

    @Override
    @OkOnMainThread
    public Object changeIccPin2ForApp(String oldPin2, String newPin2, String aidPtr) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object changeBarringPassword(String facility, String oldPwd, String newPwd) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object supplyDepersonalization(String netpin, String type) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getPDPContextList() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getDataCallProfile(int appType) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setDataProfile(DataProfile[] dps) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object hangupForegroundResumeBackground() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object switchWaitingOrHoldingAndActive() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object conference() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setPreferredVoicePrivacy(boolean enable) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getPreferredVoicePrivacy() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object separateConnection(int gsmIndex) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object explicitCallTransfer() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getLastCallFailCause() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getLastPdpFailCause() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getLastDataCallFailCause() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setMute(boolean enableMute) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getMute() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object sendBurstDtmf(String dtmfString, int on, int off) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    public Object sendSMSExpectMore(String smscPDU, String pdu) {
        // TODO can oFono benefit from knowing to "expect more"?
        return mSmsModule.sendSMS(smscPDU, pdu);
    }

    @Override
    @OkOnMainThread
    public Object sendCdmaSms(byte[] pdu) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object sendImsGsmSms(String smscPDU, String pdu, int retry, int messageRef) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object sendImsCdmaSms(byte[] pdu, int retry, int messageRef) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object deleteSmsOnSim(int index) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object deleteSmsOnRuim(int index) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object writeSmsToSim(int status, String smsc, String pdu) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object writeSmsToRuim(int status, String pdu) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object acknowledgeLastIncomingGsmSms(boolean success, int cause) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object acknowledgeLastIncomingCdmaSms(boolean success, int cause) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object acknowledgeIncomingGsmSmsWithPdu(boolean success, String ackPdu) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object queryCLIP() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getCLIR() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setCLIR(int clirMode) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object queryCallWaiting(int serviceClass) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setCallWaiting(boolean enable, int serviceClass) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setCallForward(int action, int cfReason, int serviceClass, String number, int timeSeconds) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object queryCallForwardStatus(int cfReason, int serviceClass, String number) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setNetworkSelectionModeAutomatic() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setNetworkSelectionModeManual(String operatorNumeric) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getAvailableNetworks() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object queryFacilityLock(String facility, String password, int serviceClass) {
        return queryFacilityLockForApp(facility, password, serviceClass, null);
    }

    @Override
    @OkOnMainThread
    public Object queryFacilityLockForApp(String facility, String password, int serviceClass, String appId) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setFacilityLock(String facility, boolean lockState, String password, int serviceClass) {
        return setFacilityLockForApp(facility, lockState, password, serviceClass, null);
    }

    @Override
    @OkOnMainThread
    public Object setFacilityLockForApp(String facility, boolean lockState, String password, int serviceClass, String appId) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object cancelPendingUssd() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object resetRadio() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setBandMode(int bandMode) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object queryAvailableBandMode() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setPreferredNetworkType(int networkType) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getPreferredNetworkType() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getNeighboringCids() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setLocationUpdates(boolean enable) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getSmscAddress() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setSmscAddress(String address) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object reportSmsMemoryStatus(boolean available) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object reportStkServiceIsRunning() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object invokeOemRilRequestRaw(byte[] data) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object invokeOemRilRequestStrings(String[] strings) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object sendTerminalResponse(String contents) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object sendEnvelope(String contents) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object sendEnvelopeWithStatus(String contents) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object handleCallSetupRequestFromSim(boolean accept) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setGsmBroadcastActivation(boolean activate) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setGsmBroadcastConfig(SmsBroadcastConfigInfo[] config) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getGsmBroadcastConfig() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getDeviceIdentity() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getCDMASubscription() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object sendCDMAFeatureCode(String FeatureCode) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object queryCdmaRoamingPreference() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setCdmaRoamingPreference(int cdmaRoamingType) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setCdmaSubscriptionSource(int cdmaSubscriptionType) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getCdmaSubscriptionSource() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setTTYMode(int ttyMode) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object queryTTYMode() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setCdmaBroadcastActivation(boolean activate) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setCdmaBroadcastConfig(CdmaSmsBroadcastConfigInfo[] configs) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getCdmaBroadcastConfig() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object exitEmergencyCallbackMode() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object requestIsimAuthentication(String nonce) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object requestIccSimAuthentication(int authContext, String data, String aid) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getCellInfoList() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object setCellInfoListRate(int rateInMillis) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object nvReadItem(int itemID) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object nvWriteItem(int itemID, String itemValue) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object nvWriteCdmaPrl(byte[] preferredRoamingList) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object nvResetConfig(int resetType) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object getHardwareConfig() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    private boolean mSuppressRadioStateNotifications = false;
    private RadioState mRadioStateAtSuppressionTime;
    private void startSuppressingRadioStateNotifications() {
        synchronized (mRilWrapper.mStateMonitor) {
            mRadioStateAtSuppressionTime = mRilWrapper.getRadioState();
            mSuppressRadioStateNotifications = true;
        }
    }

    private void stopSuppressingRadioStateNotifications(RadioState newState) {
        Rlog.v(TAG, "Stopping suppressing radiostate notifications and go to state "+newState);
        synchronized (mRilWrapper.mStateMonitor) {
            // This is a little dirty, but we toggle states to trigger BaseCommands's notifications.
            // We take the monitor so nobody else can observe or interrupt the fake state transition.
            if (newState != mRadioStateAtSuppressionTime) {
                mRilWrapper.setRadioState(mRadioStateAtSuppressionTime, true);
                mRilWrapper.setRadioState(newState, false);
            }
            mSuppressRadioStateNotifications = false;
        }
    }

    /*package*/ void setRadioState(RadioState newState) {
        Rlog.v(TAG, "Setting radio state "+newState+(mSuppressRadioStateNotifications ? " (suppress)" : ""));
        synchronized (mRilWrapper.mStateMonitor) {
            mRilWrapper.setRadioState(newState, mSuppressRadioStateNotifications);
        }
    }

    // TODO
    // things I noticed BaseCommands overrides but has an empty implementation we might need to override:
    // getModemCapability(),
    // setUiccSubscription(), requestShutdown(),
    // iccCloseLogicalChannel(), iccTransmitApduLogicalChannel(),
    // iccTransmitApduBasicChannel(), getAtr(), setLocalCallHold()

    private <T extends DBusInterface> T getOfonoInterface(Class<T> tClass) {
        return getOfonoInterface(tClass, mModemPath);
    }

    /*package*/ <T extends DBusInterface> T getOfonoInterface(Class<T> tClass, String path) {
        try {
            return mDbus.getRemoteObject("org.ofono", path, tClass);
        } catch (DBusException e) {
            throw new RuntimeException("Exception getting "+ tClass.getSimpleName(), e);
        }
    }

    private class EmptyHandlerCallback implements Handler.Callback {
        @Override
        public boolean handleMessage(Message msg) {
            return true;
        }
    }

    private static final Set<String> quietRespondOk = new HashSet<>(Arrays.asList(new String[]{
        "getSignalStrength", "getBasebandVersion", "getIMSIForApp", "getIccCardStatus", "iccIO", "iccIOForApp"
    }));
    private static final Set<String> quietRespondExc = new HashSet<>(Arrays.asList(new String[]{
        "setCdmaBroadcastActivation", "setCdmaBroadcastConfig",
    }));

    /*package*/ static void respondOk(String caller, Message response, Object o) {
        boolean quiet = quietRespondOk.contains(caller);

        PrivResponseOb privResponseOb = null;
        if (o instanceof PrivResponseOb) {
            privResponseOb = (PrivResponseOb) o;
            o = privResponseOb.o;
        }

        if (!quiet) {
            CharSequence debugString = toDebugString(o, privResponseOb != null);
            Rlog.v(TAG, "respondOk from " + caller + ": " + debugString);
        }
        if (response != null) {
            AsyncResult.forMessage(response, o, null);
            response.sendToTarget();
        }
    }

    /*package*/ static void respondExc(String caller, Message response, CommandException exc, Object o) {
        boolean quiet = quietRespondExc.contains(caller);

        PrivResponseOb privResponseOb = null;
        if (o instanceof PrivResponseOb) {
            privResponseOb = (PrivResponseOb) o;
            o = privResponseOb.o;
        }

        if (!quiet) {
            CharSequence debugString = toDebugString(o, privResponseOb != null);
            Rlog.e(TAG, "respondExc from "+caller+": "+exc.getCommandError()+" "+debugString);
        }
        if (response != null) {
            AsyncResult.forMessage(response, o, exc);
            response.sendToTarget();
        }
    }

    /*package*/ static void notifyResultAndLog(String logSuffix, RegistrantList list, Object result, boolean priv) {
        Rlog.v(TAG, "notify "+logSuffix+" "+(priv ? privStr(toDebugString(result)) : toDebugString(result)));
        list.notifyResult(result);
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

    // We often won't care much what thread it runs on, but in order to use a Handler, I guess we need
    // to specify a thread.
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

    protected static void logUncaughtException(String caller, Throwable t) {
        if (t instanceof DBus.Error.ServiceUnknown || t instanceof DBus.Error.UnknownMethod) {
            // make these briefer, as they're somewhat expected if oFono or one of its interfaces is not registered
            Rlog.e(TAG, "Uncaught exception in " + caller + ": " + privStr(t.getMessage()));
        } else {
            Rlog.e(TAG, "Uncaught exception in " + caller, privExc(t));
        }
    }

    /*
     * Works similar to Android's RegistrantList.
     */
    /*package*/ interface RegistrantList {
        void notifyResult(Object result);
    }

    private class RegisteredRilOfonoSignalHandler<T extends DBusSignal> extends Pair<Class<T>, DBusSigHandler<T>> {
        public RegisteredRilOfonoSignalHandler(Class<T> first, DBusSigHandler<T> second) {
            super(first, second);
        }
    }
}

// mostly a tag type to remind me use this correctly (only construct one for each purpose)
/*package*/ abstract class DebouncedRunnable implements Runnable {}

// does not need to be available at true runtime, just when BuildRilWrapper runs, but
// I'm not sure that can be done without spelunking into classfiles
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
/*package*/ @interface OkOnMainThread {
}
