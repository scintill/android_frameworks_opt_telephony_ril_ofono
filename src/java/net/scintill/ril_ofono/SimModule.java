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

import android.os.Registrant;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;

import org.freedesktop.dbus.Variant;
import org.ofono.MessageWaiting;
import org.ofono.SimManager;

import java.util.HashMap;
import java.util.Map;

import static com.android.internal.telephony.CommandException.Error.GENERIC_FAILURE;
import static net.scintill.ril_ofono.RilOfono.RegistrantList;
import static net.scintill.ril_ofono.RilOfono.notifyResultAndLog;
import static net.scintill.ril_ofono.RilOfono.runOnMainThreadDebounced;

/*package*/ class SimModule extends PropManager implements RilSimInterface {

    private static final String TAG = RilOfono.TAG;

    private RegistrantList mIccStatusChangedRegistrants;

    private final Map<String, Variant<?>> mMsgWaitingProps = new HashMap<>();

    private final Map<String, Variant<?>> mSimProps = new HashMap<>();
    private final SimFiles mSimFiles;

    /*package*/ static final String SIM_APP_ID = "00";

    /*package*/ SimModule(RegistrantList iccStatusChangedRegistrants, RegistrantList iccRefreshRegistrants) {
        mIccStatusChangedRegistrants = iccStatusChangedRegistrants;
        mSimFiles = new SimFiles(mSimProps, mMsgWaitingProps, iccRefreshRegistrants);

        SimManager sim = RilOfono.sInstance.getOfonoInterface(SimManager.class);
        MessageWaiting msgWaiting = RilOfono.sInstance.getOfonoInterface(MessageWaiting.class);

        mirrorProps(SimManager.class, sim, SimManager.PropertyChanged.class, mSimProps);
        mirrorProps(MessageWaiting.class, msgWaiting, MessageWaiting.PropertyChanged.class, mMsgWaitingProps);
    }

    @Override
    @OkOnMainThread
    public Object iccIOForApp(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, String aid) {
        return mSimFiles.iccIOForApp(command, fileid, path, p1, p2, p3, data, pin2, aid);
    }

    @Override
    @OkOnMainThread
    public Object iccIO(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2) {
        return iccIOForApp(command, fileid, path, p1, p2, p3, data, pin2, null);
    }

    @Override
    @OkOnMainThread
    public Object getIMSIForApp(String aid) {
        // TODO GSM-specific?
        String imsi = getProp(mSimProps, "SubscriberIdentity", (String)null);
        if (imsi != null) {
            return new PrivResponseOb(imsi);
        } else {
            throw new CommandException(GENERIC_FAILURE);
        }
    }

    @Override
    @OkOnMainThread
    public Object getIMSI() {
        return getIMSIForApp(null);
    }

    @Override
    @OkOnMainThread
    public Object getIccCardStatus() {
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

        return new PrivResponseOb(cardStatus);
    }

    protected void onPropChange(SimManager simManager, String name, Variant<?> value) {
        // TODO check if something that we report actually changed?
        runOnMainThreadDebounced(mFnNotifySimChanged, 350);
        mSimFiles.onPropChange(mSimProps, name);
    }

    protected void onPropChange(MessageWaiting messageWaiting, String name, Variant<?> value) {
        mSimFiles.onPropChange(mMsgWaitingProps, name);
    }

    private DebouncedRunnable mFnNotifySimChanged = new DebouncedRunnable() {
        @Override
        public void run() {
            notifyResultAndLog("icc status", mIccStatusChangedRegistrants, null, false);
        }
    };

}
