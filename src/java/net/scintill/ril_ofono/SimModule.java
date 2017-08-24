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
import android.telephony.Rlog;

import com.android.internal.telephony.uicc.IccCardApplicationStatus;
import com.android.internal.telephony.uicc.IccCardStatus;
import com.android.internal.telephony.uicc.IccCardStatus.CardState;

import org.freedesktop.dbus.Variant;
import org.ofono.MessageWaiting;
import org.ofono.SimManager;

import java.util.HashMap;
import java.util.Map;

import static com.android.internal.telephony.CommandException.Error.GENERIC_FAILURE;
import static net.scintill.ril_ofono.RilOfono.respondExc;
import static net.scintill.ril_ofono.RilOfono.respondOk;
import static net.scintill.ril_ofono.RilOfono.runOnMainThreadDebounced;

/*package*/ class SimModule extends PropManager {

    private static final String TAG = RilOfono.TAG;

    private RegistrantList mIccStatusChangedRegistrants;

    private MessageWaiting mMsgWaiting;
    private final Map<String, Variant> mMsgWaitingProps = new HashMap<>();

    private SimManager mSim;
    private final Map<String, Variant> mSimProps = new HashMap<>();
    private final SimFiles mSimFiles = new SimFiles(mSimProps, mMsgWaitingProps);

    private static final String SIM_APP_ID = "00";

    /*package*/ SimModule(RegistrantList iccStatusChangedRegistrants) {
        mIccStatusChangedRegistrants = iccStatusChangedRegistrants;

        mSim = RilOfono.sInstance.getOfonoInterface(SimManager.class);
        mMsgWaiting = RilOfono.sInstance.getOfonoInterface(MessageWaiting.class);

        mirrorProps(SimManager.class, mSim, SimManager.PropertyChanged.class, mSimProps);
        mirrorProps(MessageWaiting.class, mMsgWaiting, MessageWaiting.PropertyChanged.class, mMsgWaitingProps);
    }

    @RilMethod
    public void iccIOForApp(int command, int fileid, String path, int p1, int p2, int p3, String data, String pin2, String aid, Message response) {
        mSimFiles.iccIOForApp(command, fileid, path, p1, p2, p3, data, pin2, aid, response);
    }

    @RilMethod
    public void getIMSIForApp(String aid, Message result) {
        // TODO GSM-specific?
        String imsi = getProp(mSimProps, "SubscriberIdentity", (String)null);
        if (imsi != null) {
            respondOk("getIMSIForApp", result, new PrivResponseOb(imsi), true);
        } else {
            respondExc("getIMSIForApp", result, GENERIC_FAILURE, null);
        }
    }

    @RilMethod
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

        respondOk("getIccCardStatus", result, new PrivResponseOb(cardStatus), true);
    }

    protected void onPropChange(SimManager simManager, String name, Variant value) {
        // TODO check if something that we report actually changed?
        runOnMainThreadDebounced(mFnNotifySimChanged, 350);
    }

    protected void onPropChange(MessageWaiting messageWaiting, String name, Variant value) {
        // no action needed other than mirroring props
    }

    private DebouncedRunnable mFnNotifySimChanged = new DebouncedRunnable() {
        @Override
        public void run() {
            Rlog.d(TAG, "notify iccStatusChanged");
            mIccStatusChangedRegistrants.notifyRegistrants();
        }
    };

}
