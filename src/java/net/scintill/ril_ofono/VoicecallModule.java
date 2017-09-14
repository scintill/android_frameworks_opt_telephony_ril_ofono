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
import android.telephony.Rlog;
import android.text.TextUtils;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.DriverCall;
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
import static com.android.internal.telephony.CommandException.Error.REQUEST_NOT_SUPPORTED;
import static com.android.internal.telephony.PhoneConstants.PRESENTATION_ALLOWED;
import static com.android.internal.telephony.PhoneConstants.PRESENTATION_RESTRICTED;
import static com.android.internal.telephony.PhoneConstants.PRESENTATION_UNKNOWN;
import static net.scintill.ril_ofono.RilOfono.RegistrantList;
import static net.scintill.ril_ofono.RilOfono.notifyResultAndLog;
import static net.scintill.ril_ofono.RilOfono.privExc;
import static net.scintill.ril_ofono.RilOfono.privStr;
import static net.scintill.ril_ofono.RilOfono.runOnMainThreadDebounced;

/*package*/ class VoicecallModule extends PropManager implements RilVoicecallInterface {

    private static String TAG = RilOfono.TAG;

    private VoiceCallManager mCallManager;
    private RegistrantList mCallStateRegistrants;

    /*package*/ VoicecallModule(RegistrantList callStateRegistrants) {
        mCallStateRegistrants = callStateRegistrants;

        mCallManager = RilOfono.sInstance.getOfonoInterface(VoiceCallManager.class);

        RilOfono.sInstance.registerDbusSignal(VoiceCallManager.CallAdded.class, this);
        RilOfono.sInstance.registerDbusSignal(VoiceCall.PropertyChanged.class, this);
        RilOfono.sInstance.registerDbusSignal(VoiceCallManager.CallRemoved.class, this);
    }

    @Override
    @OkOnMainThread
    public Object getCurrentCalls() {
        List<DriverCall> calls = new ArrayList<>(mCallsProps.size());
        //Rlog.d(TAG, "mCallsProps= "+privStr(mCallsProps));
        for (Map<String, Variant<?>> callProps : mCallsProps.values()) {
            DriverCall call = new DriverCall();
            call.state = Utils.parseOfonoCallState(getProp(callProps, "State", ""));
            call.index = getProp(callProps, PROPNAME_CALL_INDEX, -1);
            if (call.state == null || call.index == -1) {
                Rlog.e(TAG, "Skipping unknown call: "+privStr(callProps));
                continue; // <--- skip unknown call
            }

            call.isMpty = false;
            call.isMT = !getProp(callProps, PROPNAME_CALL_MOBORIG, false);
            call.als = 0; // SimulatedGsmCallState
            call.isVoice = true;
            call.isVoicePrivacy = false; // oFono doesn't tell us

            call.number = getProp(callProps, "LineIdentification", "");
            call.TOA = PhoneNumberUtils.TOA_Unknown;
            if (call.number.equals("withheld")) {
                call.numberPresentation = PRESENTATION_RESTRICTED;
            } else if (TextUtils.isEmpty(call.number)) {
                call.numberPresentation = PRESENTATION_UNKNOWN;
            } else {
                call.numberPresentation = PRESENTATION_ALLOWED;
                call.TOA = PhoneNumberUtils.toaFromString(call.number);
            }

            call.name = getProp(callProps, "Name", "");
            if (call.name.equals("withheld")) {
                call.namePresentation = PRESENTATION_RESTRICTED;
            } else if (TextUtils.isEmpty(call.name)) {
                call.namePresentation = PRESENTATION_UNKNOWN;
            } else {
                call.namePresentation = PRESENTATION_ALLOWED;
            }

            // Fix up this state that confuses Android - DIALING is only for outgoing.
            // It might be a bug in the oFono QMI voicecall driver, as there is a comment about
            // whether it's right to map CALL_STATE_SETUP to dialing.
            if (call.state == DriverCall.State.DIALING && call.isMT) {
                call.state = DriverCall.State.INCOMING;
            }

            calls.add(call);
        }
        Collections.sort(calls); // not sure why, but original RIL does
        return new PrivResponseOb(calls);
    }

    private final Map<String, Map<String, Variant<?>>> mCallsProps = new HashMap<>();
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
        Map<String, Variant<?>> newCallProps = new HashMap<>(s.properties);
        newCallProps.put(PROPNAME_CALL_INDEX, new Variant<>(mAvailableCallIndices.remove()));
        putOrMerge2dProps(mCallsProps, callPath, newCallProps);

        notifyResultAndLog("call state - added", mCallStateRegistrants, null, false);
    }

    public void handle(VoiceCall.PropertyChanged s) {
        if (handle2dPropChange(mCallsProps, s.getPath(), VoiceCall.class, s.name, s.value)) {
            runOnMainThreadDebounced(mFnNotifyCallStateChanged, 200);
        }
    }

    public void handle(VoiceCallManager.CallRemoved s) {
        String callPath = s.path.getPath();
        Rlog.d(TAG, "handle CallRemoved");
        int callIndex = getProp(mCallsProps.get(callPath), PROPNAME_CALL_INDEX, -1);
        mCallsProps.remove(callPath);
        if (callIndex != -1) mAvailableCallIndices.add(callIndex);
        notifyResultAndLog("call state - removed", mCallStateRegistrants, null, false);
    }

    private String getDbusPathForCallIndex(int i) {
        synchronized (mCallsProps) {
            for (Map.Entry<String, Map<String, Variant<?>>> entry : mCallsProps.entrySet()) {
                if (getProp(entry.getValue(), PROPNAME_CALL_INDEX, -1) == i) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    @Override
    public Object dial(final String address, int clirMode) {
        final String clirModeStr;
        switch (clirMode) {
            case CommandsInterface.CLIR_DEFAULT: clirModeStr = "default"; break;
            case CommandsInterface.CLIR_INVOCATION: clirModeStr = "enabled"; break;
            case CommandsInterface.CLIR_SUPPRESSION: clirModeStr = "disabled"; break;
            default:
                throw new IllegalArgumentException("unknown CLIR constant "+clirMode);
        }

        Path dialedCallPath = mCallManager.Dial(address, clirModeStr);
        Map<String, Variant<?>> dialedCallProps = new HashMap<>();
        dialedCallProps.put(PROPNAME_CALL_MOBORIG, new Variant<>(true));
        putOrMerge2dProps(mCallsProps, dialedCallPath.getPath(), dialedCallProps);
        Rlog.d(TAG, "dialed "+dialedCallPath.getPath());
        return null;
    }

    @Override
    public Object dial(String address, int clirMode, UUSInfo uusInfo) {
        if (uusInfo != null) {
            throw new CommandException(MODE_NOT_SUPPORTED);
        } else {
            return dial(address, clirMode);
        }
    }

    @Override
    public Object hangupConnection(final int gsmIndex) {
        String callPath = getDbusPathForCallIndex(gsmIndex);
        if (callPath == null) {
            throw new CommandException(NO_SUCH_ELEMENT);
        }
        VoiceCall call = RilOfono.sInstance.getOfonoInterface(VoiceCall.class, callPath);
        call.Hangup();
        return null;
    }

    @Override
    public Object hangupWaitingOrBackground() {
        boolean oneSucceeded = false, oneExcepted = false;
        for (Map.Entry<String, Map<String, Variant<?>>> callPropsEntry : mCallsProps.entrySet()) {
            String callPath = callPropsEntry.getKey();
            Map<String, Variant<?>> callProps = callPropsEntry.getValue();
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
            return null;
        } else if (oneExcepted) {
            throw new CommandException(GENERIC_FAILURE);
        } else {
            throw new CommandException(NO_SUCH_ELEMENT);
        }
    }

    @Override
    public Object acceptCall() {
        for (Map.Entry<String, Map<String, Variant<?>>> callPropsEntry : mCallsProps.entrySet()) {
            String callPath = callPropsEntry.getKey();
            Map<String, Variant<?>> callProps = callPropsEntry.getValue();
            if (Utils.parseOfonoCallState(getProp(callProps, "State", "")) == DriverCall.State.INCOMING) {
                VoiceCall call = RilOfono.sInstance.getOfonoInterface(VoiceCall.class, callPath);
                call.Answer();
                return null;
            }
        }

        throw new CommandException(CommandException.Error.NO_SUCH_ELEMENT);
    }

    @Override
    public Object rejectCall() {
        // TODO RIL.java sends UDUB, which may not be the same as what we're indirectly asking oFono to do here
        return hangupWaitingOrBackground();
    }

    // XXX oFono doesn't seem to support timed DTMF presses; it just sends them at a fixed rate and duration
    // we can't control.

    @Override
    public Object sendDtmf(char c) {
        mCallManager.SendTones(String.valueOf(c));
        return null;
    }

    @Override
    public Object startDtmf(char c) {
        return sendDtmf(c);
    }

    @Override
    @OkOnMainThread
    public Object stopDtmf() {
        // I feel like we should give some indication we're not faithfully implementing the interface
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    final DebouncedRunnable mFnNotifyCallStateChanged = new DebouncedRunnable() {
        @Override
        public void run() {
            notifyResultAndLog("call state", mCallStateRegistrants, null, false);
        }
    };

}
