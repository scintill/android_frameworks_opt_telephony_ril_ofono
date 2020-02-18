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
import android.os.Registrant;
import android.telephony.Rlog;

import com.android.internal.telephony.BaseCommands;
import com.android.internal.telephony.CommandException;

import org.freedesktop.DBus;

import java.lang.reflect.Field;

import static com.android.internal.telephony.RILConstants.NO_PHONE;
import static net.scintill.ril_ofono.RilOfono.notifyResultAndLog;
import static net.scintill.ril_ofono.RilOfono.privExc;
import static net.scintill.ril_ofono.RilOfono.privStr;

public abstract class RilWrapperBase extends SaneBaseCommands {

    protected static final String TAG = RilOfono.TAG;

    /*package*/ final Object mStateMonitor = super.mStateMonitor;

    protected final RilOfono mRilOfono;

    protected static android.os.Message sCurrentMsg;

    protected RilWrapperBase(Context ctx) {
        super(ctx);
        mPhoneType = NO_PHONE;
        mRilOfono = new RilOfono(this, ctx);
    }

    /*package*/ void updateRilConnection(int version) {
        this.mRilVersion = version;
        if (mRilConnectedRegistrants != null) {
            notifyResultAndLog("ril connected", mRilConnectedRegistrants, version, false);
        }
    }

    @Override
    public void setPhoneType(final int a) {
        mPhoneType = a;
    }

    @Override
    public boolean needsOldRilFeature(final String a) {
        return false;
    }

    /*package*/ static android.os.Message getCurrentMessage() {
        return sCurrentMsg;
    }

    /*package*/ static final Object RETURN_LATER = new Object() {
        @Override
        public String toString() {
            return "RETURN_LATER";
        }
    };

    /*package*/ RilOfono.RegistrantList mGsmSmsRegistrants = new DynamicRegistrantListFromField("mGsmSmsRegistrant");
    /*package*/ RilOfono.RegistrantList mUSSDRegistrants = new DynamicRegistrantListFromField("mUSSDRegistrant");
    /*package*/ RilOfono.RegistrantList mSignalStrengthRegistrants = new DynamicRegistrantListFromField("mSignalStrengthRegistrant");

    /*package*/ RilOfono.RegistrantList
            mVoiceNetworkStateRegistrants = new RegistrantListAndroidTypeWrapper(super.mVoiceNetworkStateRegistrants),
            mIccStatusChangedRegistrants = new RegistrantListAndroidTypeWrapper(super.mIccStatusChangedRegistrants),
            mIccRefreshRegistrants = new RegistrantListAndroidTypeWrapper(super.mIccRefreshRegistrants),
            mVoiceRadioTechChangedRegistrants = new RegistrantListAndroidTypeWrapper(super.mVoiceRadioTechChangedRegistrants),
            mCallStateRegistrants = new RegistrantListAndroidTypeWrapper(super.mCallStateRegistrants),
            mDataNetworkStateRegistrants = new RegistrantListAndroidTypeWrapper(super.mDataNetworkStateRegistrants),
            mRilConnectedRegistrants = new RegistrantListAndroidTypeWrapper(super.mRilConnectedRegistrants);

    /*package*/ void setRadioState(RadioState newState, boolean suppressNotifications) {
        if (!suppressNotifications) {
            super.setRadioState(newState);
        } else {
            mState = newState;
        }
    }

    @Override
    public RadioState getRadioState() {
        synchronized (mStateMonitor) {
            return super.getRadioState();
        }
    }

    ///////////////////////////
    // Synchronous methods
    ///////////////////////////
    // (Currently BuildRilWrapper doesn't support them. If we have real implementations,
    // it would probably be better to fix that and put them in appropriate modules.)

    @Override
    public void setLocalCallHold(boolean lchStatus) {
        throw new CommandException(CommandException.Error.REQUEST_NOT_SUPPORTED);
    }

    @Override
    public void testingEmergencyCall() {
        throw new CommandException(CommandException.Error.REQUEST_NOT_SUPPORTED);
    }

    ///////////////////////////
    // Helpers
    ///////////////////////////

    /*
     * Helps paper over the difference between a single registrant stored in a mutable field, and a list
     * of registrants.
     */
    private class DynamicRegistrantListFromField implements RilOfono.RegistrantList {
        Field mField;
        DynamicRegistrantListFromField(String fieldname) {
            try {
                mField = Utils.getField(RilWrapperBase.class, fieldname);
                mField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("unable to create dynamic registrant list from field "+fieldname, e);
            }
        }

        @Override
        public void notifyResult(Object result) {
            try {
                Registrant registrant = (Registrant) mField.get(RilWrapperBase.this);
                if (registrant != null) registrant.notifyResult(result);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("unable to get registrant", e);
            }
        }
    }

    private class RegistrantListAndroidTypeWrapper implements RilOfono.RegistrantList {
        android.os.RegistrantList mList;
        RegistrantListAndroidTypeWrapper(android.os.RegistrantList list) {
            mList = list;
        }
        @Override
        public void notifyResult(Object result) {
            mList.notifyResult(result);
        }
    }

    protected static void respondOk(String caller, android.os.Message msg, Object ret) {
        if (ret == RETURN_LATER) {
            Rlog.v(TAG, caller+" will return later");
        } else {
            RilOfono.respondOk(caller, msg, ret);
        }
    }

}
