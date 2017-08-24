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
import android.os.RegistrantList;
import android.telephony.PhoneNumberUtils;
import android.telephony.Rlog;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.DriverCall;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.UUSInfo;

import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Variant;
import org.ofono.VoiceCall;
import org.ofono.VoiceCallManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.android.internal.telephony.CommandException.Error.GENERIC_FAILURE;
import static com.android.internal.telephony.CommandException.Error.MODE_NOT_SUPPORTED;
import static com.android.internal.telephony.CommandException.Error.NO_SUCH_ELEMENT;
import static net.scintill.ril_ofono.RilOfono.privExc;
import static net.scintill.ril_ofono.RilOfono.privStr;
import static net.scintill.ril_ofono.RilOfono.respondExc;
import static net.scintill.ril_ofono.RilOfono.respondOk;
import static net.scintill.ril_ofono.RilOfono.runOnDbusThread;
import static net.scintill.ril_ofono.RilOfono.runOnMainThread;
import static net.scintill.ril_ofono.RilOfono.runOnMainThreadDebounced;

/*package*/ class CallModule extends PropManager {

    private static String TAG = RilOfono.TAG;

    private VoiceCallManager mCallManager;
    private RegistrantList mCallStateRegistrants;

    /*package*/ CallModule(RegistrantList callStateRegistrants) {
        mCallStateRegistrants = callStateRegistrants;

        mCallManager = RilOfono.sInstance.getOfonoInterface(VoiceCallManager.class);

        RilOfono.sInstance.registerDbusSignal(VoiceCallManager.CallAdded.class, this);
        RilOfono.sInstance.registerDbusSignal(VoiceCall.PropertyChanged.class, this);
        RilOfono.sInstance.registerDbusSignal(VoiceCallManager.CallRemoved.class, this);
    }

    @RilMethod
    public void getCurrentCalls(Message result) {
        try {
            List<DriverCall> calls = new ArrayList<>(mCallsProps.size());
            //Rlog.d(TAG, "mCallsProps= "+privStr(mCallsProps));
            for (Map<String, Variant> callProps : mCallsProps.values()) {
                DriverCall call = new DriverCall();
                call.state = Utils.parseOfonoCallState(getProp(callProps, "State", ""));
                call.index = getProp(callProps, PROPNAME_CALL_INDEX, -1);
                if (call.state == null || call.index == -1) {
                    Rlog.e(TAG, "Skipping unknown call: "+privStr(callProps));
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
            respondOk("getCurrentCalls", result, new PrivResponseOb(calls));
        } catch (Throwable t) {
            Rlog.e(TAG, "Error getting calls", privExc(t));
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

        runOnMainThread(mFnNotifyCallStateChanged);
    }

    public void handle(VoiceCall.PropertyChanged s) {
        handle2dPropChange(mCallsProps, s.getPath(), VoiceCall.class, s.name, s.value);
        runOnMainThreadDebounced(mFnNotifyCallStateChanged, 200);
    }

    public void handle(VoiceCallManager.CallRemoved s) {
        String callPath = s.path.getPath();
        Rlog.d(TAG, "handle CallRemoved");
        int callIndex = getProp(mCallsProps.get(callPath), PROPNAME_CALL_INDEX, -1);
        mCallsProps.remove(callPath);
        if (callIndex != -1) mAvailableCallIndices.add(callIndex);
        runOnMainThread(mFnNotifyCallStateChanged);
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

    @RilMethod
    public void dial(final String address, int clirMode, final Message result) {
        final String clirModeStr;
        switch (clirMode) {
            case CommandsInterface.CLIR_DEFAULT: clirModeStr = "default"; break;
            case CommandsInterface.CLIR_INVOCATION: clirModeStr = "enabled"; break;
            case CommandsInterface.CLIR_SUPPRESSION: clirModeStr = "disabled"; break;
            default:
                throw new IllegalArgumentException("unknown CLIR constant "+clirMode);
        }

        runOnDbusThread(new Runnable() {
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
                    Rlog.e(TAG, "Error dialing", privExc(t));
                    respondExc("dial", result, GENERIC_FAILURE, null);
                }
            }
        });
    }

    @RilMethod
    public void dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        if (uusInfo != null) {
            respondExc("dial", result, MODE_NOT_SUPPORTED, null);
        } else {
            dial(address, clirMode, result);
        }
    }

    @RilMethod
    public void hangupConnection(final int gsmIndex, final Message result) {
        runOnDbusThread(new Runnable() {
            @Override
            public void run() {
                try {
                    String callPath = getDbusPathForCallIndex(gsmIndex);
                    if (callPath == null) {
                        respondExc("hangupConnection", result, NO_SUCH_ELEMENT, null);
                        return;
                    }
                    VoiceCall call = RilOfono.sInstance.getOfonoInterface(VoiceCall.class, callPath);
                    call.Hangup();
                    respondOk("hangupConnection", result, null);
                } catch (Throwable t) {
                    Rlog.e(TAG, "Error hanging up", privExc(t));
                    respondExc("hangupConnection", result, GENERIC_FAILURE, null);
                }
            }
        });
    }

    @RilMethod
    public void hangupWaitingOrBackground(final Message result) {
        runOnDbusThread(new Runnable() {
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
                                    VoiceCall call = RilOfono.sInstance.getOfonoInterface(VoiceCall.class, callPath);
                                    call.Hangup();
                                    oneSucceeded = true;
                                    break;
                                default:
                                    // skip
                            }
                        }
                    } catch (Throwable t) {
                        oneExcepted = true;
                        Rlog.e(TAG, "Error checking/hangingup call", privExc(t));
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

    @RilMethod
    public void acceptCall(final Message result) {
        runOnDbusThread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (Map.Entry<String, Map<String, Variant>> callPropsEntry : mCallsProps.entrySet()) {
                        String callPath = callPropsEntry.getKey();
                        Map<String, Variant> callProps = callPropsEntry.getValue();
                        if (Utils.parseOfonoCallState(getProp(callProps, "State", "")) == DriverCall.State.INCOMING) {
                            VoiceCall call = RilOfono.sInstance.getOfonoInterface(VoiceCall.class, callPath);
                            call.Answer();
                            respondOk("acceptCall", result, null);
                        }
                    }
                } catch (Throwable t) {
                    Rlog.e(TAG, "Error accepting call", privExc(t));
                    respondExc("acceptCall", result, GENERIC_FAILURE, null);
                }
            }
        });
    }

    @RilMethod
    public void rejectCall(Message result) {
        // TODO RIL.java sends UDUB, which may not be the same as what we're indirectly asking oFono to do here
        hangupWaitingOrBackground(result);
    }

    final DebouncedRunnable mFnNotifyCallStateChanged = new DebouncedRunnable() {
        @Override
        public void run() {
            mCallStateRegistrants.notifyResult(null);
        }
    };

}
