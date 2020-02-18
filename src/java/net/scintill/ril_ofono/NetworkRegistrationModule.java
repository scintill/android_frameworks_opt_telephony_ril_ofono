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

import android.telephony.Rlog;
import android.telephony.SignalStrength;

import com.android.internal.telephony.CommandException;

import org.freedesktop.dbus.Variant;
import org.ofono.NetworkRegistration;

import java.util.HashMap;
import java.util.Map;

import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_EDGE;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_GPRS;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_GSM;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_HSDPA;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_HSPA;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_HSUPA;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_LTE;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_UMTS;
import static android.telephony.ServiceState.RIL_RADIO_TECHNOLOGY_UNKNOWN;
import static com.android.internal.telephony.CommandException.Error.GENERIC_FAILURE;
import static com.android.internal.telephony.CommandException.Error.REQUEST_NOT_SUPPORTED;
import static net.scintill.ril_ofono.RilOfono.RegistrantList;
import static net.scintill.ril_ofono.RilOfono.notifyResultAndLog;
import static net.scintill.ril_ofono.RilOfono.runOnMainThreadDebounced;

/*package*/ class NetworkRegistrationModule extends PropManager implements RilNetworkRegistrationInterface {

    private static final String TAG = RilOfono.TAG;
    private NetworkRegistration mNetReg;
    private final Map<String, Variant<?>> mNetRegProps = new HashMap<>();

    private final RegistrantList mVoiceNetworkStateRegistrants;
    private final RegistrantList mVoiceRadioTechChangedRegistrants;
    private final RilOfono.RegistrantList mSignalStrengthRegistrants;

    /*package*/ NetworkRegistrationModule(NetworkRegistration netReg, RegistrantList voiceNetworkStateRegistrants, RegistrantList voiceRadioTechChangedRegistrants, RegistrantList signalStrengthRegistrants) {
        Rlog.v(TAG, "NetworkRegistrationModule()");
        mNetReg = netReg;
        mVoiceNetworkStateRegistrants = voiceNetworkStateRegistrants;
        mVoiceRadioTechChangedRegistrants = voiceRadioTechChangedRegistrants;
        mSignalStrengthRegistrants = signalStrengthRegistrants;

        initProps(mNetRegProps, NetworkRegistration.class, netReg);
    }

    /*package*/ void handle(NetworkRegistration.PropertyChanged s) {
        handle(s, mNetReg, NetworkRegistration.PropertyChanged.class, mNetRegProps, NetworkRegistration.class);
    }

    @Override
    @OkOnMainThread
    public Object getSignalStrength() {
        // TODO gsm-specific
        Byte signalPercent = getProp(mNetRegProps, "Strength", (Byte)null);
        return new SignalStrength(
                signalPercent == null ? 99 : (int)Math.round(signalPercent / 100.0 * 31),
                99, -1, -1, -1, -1, -1, true);
    }

    @Override
    @OkOnMainThread
    public Object getVoiceRegistrationState() {
        OfonoRegistrationState state = getProp(mNetRegProps, "Status", OfonoRegistrationState.unknown);
        if (!state.isRegistered()) {
            return new String[]{ ""+state.ts27007Creg, "-1", "-1" };
        } else {
            return new PrivResponseOb(new String[]{
                    ""+state.ts27007Creg,
                    getProp(mNetRegProps, "LocationAreaCode", "-1"),
                    getProp(mNetRegProps, "CellId", "-1"),
                    ""+getProp(mNetRegProps, "Technology", OfonoNetworkTechnology.none).serviceStateInt,
            });
        }
    }

    @Override
    @OkOnMainThread
    public Object getOperator() {
        boolean registered = getProp(mNetRegProps, "Status", OfonoRegistrationState.unknown).isRegistered();
        String name = getProp(mNetRegProps, "Name", "");
        String mcc = getProp(mNetRegProps, "MobileCountryCode", "");
        String mnc = getProp(mNetRegProps, "MobileNetworkCode", "");
        if (registered && mcc.length() > 0 && mnc.length() > 0 && name.length() > 0) {
            return new String[] {
                    name, name, /* TODO does Ofono offer distinct short and long names? */
                    mcc+mnc
            };
        } else {
            return new String[] { null, null, null };
        }
    }

    @Override
    @OkOnMainThread
    public Object getVoiceRadioTechnology() {
        return getVoiceRadioTechnologyImpl();
    }

    @Override
    @OkOnMainThread
    public Object getNetworkSelectionMode() {
        String mode = getProp(mNetRegProps, "Mode", (String)null);
        if (mode == null) { // TODO remove this?
            Rlog.w(TAG, "getNetworkSelectionMode(): oFono is not reporting the mode; returning CommandException");
            throw new CommandException(GENERIC_FAILURE);
        } else {
            return new int[]{ mode.equals("manual") ? 1 : 0 };
        }
    }

    protected void onPropChange(NetworkRegistration netReg, String name, Variant<?> value) {
        if (name.equals("Strength")) {
            notifyResultAndLog("signal strength", mSignalStrengthRegistrants, getSignalStrength(), false);
        } else {
            runOnMainThreadDebounced(mFnNotifyNetworkChanged, 350);
        }
        // TODO data network registration?
    }

    final DebouncedRunnable mFnNotifyNetworkChanged = new DebouncedRunnable() {
        @Override
        public void run() {
            notifyResultAndLog("voice netstate", mVoiceNetworkStateRegistrants, null, false);
            notifyResultAndLog("voice radiotech changed", mVoiceRadioTechChangedRegistrants, getVoiceRadioTechnologyImpl(), false);
        }
    };

    private Object getVoiceRadioTechnologyImpl() {
        // TODO is this really the right value?
        return new int[]{ getProp(mNetRegProps, "Technology", OfonoNetworkTechnology.none).serviceStateInt };
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
        none(RIL_RADIO_TECHNOLOGY_UNKNOWN),
        gprs(RIL_RADIO_TECHNOLOGY_GPRS), hsdpa(RIL_RADIO_TECHNOLOGY_HSDPA), hsupa(RIL_RADIO_TECHNOLOGY_HSUPA),
        ;
        public int serviceStateInt;
        OfonoNetworkTechnology(int serviceStateInt) {
            this.serviceStateInt = serviceStateInt;
        }
        static OfonoNetworkTechnology fromSetupDataCallValue(int i) {
            for (OfonoNetworkTechnology tech : values()) {
                // see DataConnection#getDataTechnology
                if (tech.serviceStateInt + 2 == i) {
                    return tech;
                }
            }
            return null;
        }
    }

    @Override
    @OkOnMainThread
    public Object startLceService(int reportIntervalMs, boolean pullMode) {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object stopLceService() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    @Override
    @OkOnMainThread
    public Object pullLceData() {
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

}
