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

import java.lang.reflect.Field;

import static com.android.internal.telephony.CommandException.Error.GENERIC_FAILURE;
import static com.android.internal.telephony.RILConstants.NO_PHONE;
import static net.scintill.ril_ofono.RilOfono.notifyResultAndLog;
import static net.scintill.ril_ofono.RilOfono.privExc;
import static net.scintill.ril_ofono.RilOfono.respondExc;
import static net.scintill.ril_ofono.RilOfono.runOnDbusThread;

public final class RilWrapper extends BaseCommands {

    private RilInterface mRilImpl;
    private static final String TAG = RilOfono.TAG;

    private static android.os.Message sCurrentMsg;

    public RilWrapper(Context ctx, int networkMode, int cdmaSubscription, Integer instanceId) {
        super(ctx);
        mPhoneType = NO_PHONE;
        mRilImpl = new RilOfono(this);
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

    ///////////////////////////
    // Promote some members to package visibility
    ///////////////////////////

    /*package*/ RilOfono.RegistrantList
            mVoiceNetworkStateRegistrants = new RegistrantListAndroidTypeWrapper(super.mVoiceNetworkStateRegistrants),
            mIccStatusChangedRegistrants = new RegistrantListAndroidTypeWrapper(super.mIccStatusChangedRegistrants),
            mVoiceRadioTechChangedRegistrants = new RegistrantListAndroidTypeWrapper(super.mVoiceRadioTechChangedRegistrants),
            mCallStateRegistrants = new RegistrantListAndroidTypeWrapper(super.mCallStateRegistrants),
            mDataNetworkStateRegistrants = new RegistrantListAndroidTypeWrapper(super.mDataNetworkStateRegistrants),
            mRilConnectedRegistrants = new RegistrantListAndroidTypeWrapper(super.mRilConnectedRegistrants);

    /*package*/ void setRadioStateHelper(RadioState newState) {
        setRadioState(newState);
    }

    ///////////////////////////
    // Helpers
    ///////////////////////////

    /*
     * Helps paper over the difference between a single registrant stored in a mutable field, and a list
     * of registrants.
     */
    class DynamicRegistrantListFromField implements RilOfono.RegistrantList {
        Field mField;
        DynamicRegistrantListFromField(String fieldname) {
            try {
                mField = Utils.getField(RilWrapper.this, fieldname);
                mField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException("unable to create dynamic registrant list from field "+fieldname, e);
            }
        }

        @Override
        public void notifyResult(Object result) {
            try {
                Registrant registrant = (Registrant) mField.get(RilWrapper.this);
                if (registrant != null) registrant.notifyResult(result);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("unable to get registrant", e);
            }
        }
    }

    class RegistrantListAndroidTypeWrapper implements RilOfono.RegistrantList {
        android.os.RegistrantList mList;
        RegistrantListAndroidTypeWrapper(android.os.RegistrantList list) {
            mList = list;
        }
        @Override
        public void notifyResult(Object result) {
            mList.notifyResult(result);
        }
    }

    private static void respondOk(String caller, android.os.Message msg, Object ret) {
        if (ret == RETURN_LATER) {
            Rlog.v(TAG, caller+" will return later");
        } else {
            RilOfono.respondOk(caller, msg, ret);
        }
    }

    ///////////////////////////
    // Autogenerated wrappers
    ///////////////////////////

    public void acceptCall(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.acceptCall();
                    respondOk("acceptCall", msg, ret);
                } catch (CommandException exc) {
                    respondExc("acceptCall", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in acceptCall", privExc(thr));
                    respondExc("acceptCall", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void acknowledgeIncomingGsmSmsWithPdu(final boolean a, final String b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.acknowledgeIncomingGsmSmsWithPdu(a, b);
                    respondOk("acknowledgeIncomingGsmSmsWithPdu", msg, ret);
                } catch (CommandException exc) {
                    respondExc("acknowledgeIncomingGsmSmsWithPdu", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in acknowledgeIncomingGsmSmsWithPdu", privExc(thr));
                    respondExc("acknowledgeIncomingGsmSmsWithPdu", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void acknowledgeLastIncomingCdmaSms(final boolean a, final int b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.acknowledgeLastIncomingCdmaSms(a, b);
                    respondOk("acknowledgeLastIncomingCdmaSms", msg, ret);
                } catch (CommandException exc) {
                    respondExc("acknowledgeLastIncomingCdmaSms", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in acknowledgeLastIncomingCdmaSms", privExc(thr));
                    respondExc("acknowledgeLastIncomingCdmaSms", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void acknowledgeLastIncomingGsmSms(final boolean a, final int b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.acknowledgeLastIncomingGsmSms(a, b);
                    respondOk("acknowledgeLastIncomingGsmSms", msg, ret);
                } catch (CommandException exc) {
                    respondExc("acknowledgeLastIncomingGsmSms", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in acknowledgeLastIncomingGsmSms", privExc(thr));
                    respondExc("acknowledgeLastIncomingGsmSms", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void cancelPendingUssd(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.cancelPendingUssd();
                    respondOk("cancelPendingUssd", msg, ret);
                } catch (CommandException exc) {
                    respondExc("cancelPendingUssd", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in cancelPendingUssd", privExc(thr));
                    respondExc("cancelPendingUssd", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void changeBarringPassword(final String a, final String b, final String c, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.changeBarringPassword(a, b, c);
                    respondOk("changeBarringPassword", msg, ret);
                } catch (CommandException exc) {
                    respondExc("changeBarringPassword", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in changeBarringPassword", privExc(thr));
                    respondExc("changeBarringPassword", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void changeIccPin(final String a, final String b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.changeIccPin(a, b);
                    respondOk("changeIccPin", msg, ret);
                } catch (CommandException exc) {
                    respondExc("changeIccPin", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in changeIccPin", privExc(thr));
                    respondExc("changeIccPin", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void changeIccPin2(final String a, final String b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.changeIccPin2(a, b);
                    respondOk("changeIccPin2", msg, ret);
                } catch (CommandException exc) {
                    respondExc("changeIccPin2", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in changeIccPin2", privExc(thr));
                    respondExc("changeIccPin2", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void changeIccPin2ForApp(final String a, final String b, final String c, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.changeIccPin2ForApp(a, b, c);
                    respondOk("changeIccPin2ForApp", msg, ret);
                } catch (CommandException exc) {
                    respondExc("changeIccPin2ForApp", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in changeIccPin2ForApp", privExc(thr));
                    respondExc("changeIccPin2ForApp", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void changeIccPinForApp(final String a, final String b, final String c, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.changeIccPinForApp(a, b, c);
                    respondOk("changeIccPinForApp", msg, ret);
                } catch (CommandException exc) {
                    respondExc("changeIccPinForApp", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in changeIccPinForApp", privExc(thr));
                    respondExc("changeIccPinForApp", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void conference(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.conference();
                    respondOk("conference", msg, ret);
                } catch (CommandException exc) {
                    respondExc("conference", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in conference", privExc(thr));
                    respondExc("conference", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void deactivateDataCall(final int a, final int b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.deactivateDataCall(a, b);
                    respondOk("deactivateDataCall", msg, ret);
                } catch (CommandException exc) {
                    respondExc("deactivateDataCall", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in deactivateDataCall", privExc(thr));
                    respondExc("deactivateDataCall", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void deleteSmsOnRuim(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.deleteSmsOnRuim(a);
                    respondOk("deleteSmsOnRuim", msg, ret);
                } catch (CommandException exc) {
                    respondExc("deleteSmsOnRuim", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in deleteSmsOnRuim", privExc(thr));
                    respondExc("deleteSmsOnRuim", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void deleteSmsOnSim(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.deleteSmsOnSim(a);
                    respondOk("deleteSmsOnSim", msg, ret);
                } catch (CommandException exc) {
                    respondExc("deleteSmsOnSim", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in deleteSmsOnSim", privExc(thr));
                    respondExc("deleteSmsOnSim", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void dial(final String a, final int b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.dial(a, b);
                    respondOk("dial", msg, ret);
                } catch (CommandException exc) {
                    respondExc("dial", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in dial", privExc(thr));
                    respondExc("dial", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void dial(final String a, final int b, final com.android.internal.telephony.UUSInfo c, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.dial(a, b, c);
                    respondOk("dial", msg, ret);
                } catch (CommandException exc) {
                    respondExc("dial", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in dial", privExc(thr));
                    respondExc("dial", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void exitEmergencyCallbackMode(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.exitEmergencyCallbackMode();
                    respondOk("exitEmergencyCallbackMode", msg, ret);
                } catch (CommandException exc) {
                    respondExc("exitEmergencyCallbackMode", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in exitEmergencyCallbackMode", privExc(thr));
                    respondExc("exitEmergencyCallbackMode", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void explicitCallTransfer(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.explicitCallTransfer();
                    respondOk("explicitCallTransfer", msg, ret);
                } catch (CommandException exc) {
                    respondExc("explicitCallTransfer", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in explicitCallTransfer", privExc(thr));
                    respondExc("explicitCallTransfer", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getAvailableNetworks(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getAvailableNetworks();
                    respondOk("getAvailableNetworks", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getAvailableNetworks", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getAvailableNetworks", privExc(thr));
                    respondExc("getAvailableNetworks", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getBasebandVersion(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getBasebandVersion();
                    respondOk("getBasebandVersion", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getBasebandVersion", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getBasebandVersion", privExc(thr));
                    respondExc("getBasebandVersion", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getCDMASubscription(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getCDMASubscription();
                    respondOk("getCDMASubscription", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getCDMASubscription", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getCDMASubscription", privExc(thr));
                    respondExc("getCDMASubscription", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getCLIR(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getCLIR();
                    respondOk("getCLIR", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getCLIR", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getCLIR", privExc(thr));
                    respondExc("getCLIR", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getCdmaBroadcastConfig(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getCdmaBroadcastConfig();
                    respondOk("getCdmaBroadcastConfig", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getCdmaBroadcastConfig", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getCdmaBroadcastConfig", privExc(thr));
                    respondExc("getCdmaBroadcastConfig", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getCdmaSubscriptionSource(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getCdmaSubscriptionSource();
                    respondOk("getCdmaSubscriptionSource", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getCdmaSubscriptionSource", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getCdmaSubscriptionSource", privExc(thr));
                    respondExc("getCdmaSubscriptionSource", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getCellInfoList(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getCellInfoList();
                    respondOk("getCellInfoList", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getCellInfoList", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getCellInfoList", privExc(thr));
                    respondExc("getCellInfoList", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getCurrentCalls(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getCurrentCalls();
                    respondOk("getCurrentCalls", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getCurrentCalls", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getCurrentCalls", privExc(thr));
                    respondExc("getCurrentCalls", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getDataCallList(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getDataCallList();
                    respondOk("getDataCallList", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getDataCallList", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getDataCallList", privExc(thr));
                    respondExc("getDataCallList", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getDataCallProfile(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getDataCallProfile(a);
                    respondOk("getDataCallProfile", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getDataCallProfile", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getDataCallProfile", privExc(thr));
                    respondExc("getDataCallProfile", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getDataRegistrationState(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getDataRegistrationState();
                    respondOk("getDataRegistrationState", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getDataRegistrationState", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getDataRegistrationState", privExc(thr));
                    respondExc("getDataRegistrationState", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getDeviceIdentity(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getDeviceIdentity();
                    respondOk("getDeviceIdentity", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getDeviceIdentity", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getDeviceIdentity", privExc(thr));
                    respondExc("getDeviceIdentity", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getGsmBroadcastConfig(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getGsmBroadcastConfig();
                    respondOk("getGsmBroadcastConfig", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getGsmBroadcastConfig", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getGsmBroadcastConfig", privExc(thr));
                    respondExc("getGsmBroadcastConfig", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getHardwareConfig(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getHardwareConfig();
                    respondOk("getHardwareConfig", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getHardwareConfig", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getHardwareConfig", privExc(thr));
                    respondExc("getHardwareConfig", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getIMEI(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getIMEI();
                    respondOk("getIMEI", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getIMEI", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getIMEI", privExc(thr));
                    respondExc("getIMEI", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getIMEISV(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getIMEISV();
                    respondOk("getIMEISV", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getIMEISV", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getIMEISV", privExc(thr));
                    respondExc("getIMEISV", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getIMSI(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getIMSI();
                    respondOk("getIMSI", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getIMSI", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getIMSI", privExc(thr));
                    respondExc("getIMSI", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getIMSIForApp(final String a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getIMSIForApp(a);
                    respondOk("getIMSIForApp", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getIMSIForApp", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getIMSIForApp", privExc(thr));
                    respondExc("getIMSIForApp", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getIccCardStatus(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getIccCardStatus();
                    respondOk("getIccCardStatus", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getIccCardStatus", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getIccCardStatus", privExc(thr));
                    respondExc("getIccCardStatus", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getImsRegistrationState(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getImsRegistrationState();
                    respondOk("getImsRegistrationState", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getImsRegistrationState", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getImsRegistrationState", privExc(thr));
                    respondExc("getImsRegistrationState", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getLastCallFailCause(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getLastCallFailCause();
                    respondOk("getLastCallFailCause", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getLastCallFailCause", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getLastCallFailCause", privExc(thr));
                    respondExc("getLastCallFailCause", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getLastDataCallFailCause(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getLastDataCallFailCause();
                    respondOk("getLastDataCallFailCause", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getLastDataCallFailCause", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getLastDataCallFailCause", privExc(thr));
                    respondExc("getLastDataCallFailCause", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getLastPdpFailCause(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getLastPdpFailCause();
                    respondOk("getLastPdpFailCause", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getLastPdpFailCause", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getLastPdpFailCause", privExc(thr));
                    respondExc("getLastPdpFailCause", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getMute(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getMute();
                    respondOk("getMute", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getMute", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getMute", privExc(thr));
                    respondExc("getMute", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getNeighboringCids(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getNeighboringCids();
                    respondOk("getNeighboringCids", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getNeighboringCids", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getNeighboringCids", privExc(thr));
                    respondExc("getNeighboringCids", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getNetworkSelectionMode(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getNetworkSelectionMode();
                    respondOk("getNetworkSelectionMode", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getNetworkSelectionMode", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getNetworkSelectionMode", privExc(thr));
                    respondExc("getNetworkSelectionMode", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getOperator(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getOperator();
                    respondOk("getOperator", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getOperator", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getOperator", privExc(thr));
                    respondExc("getOperator", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getPDPContextList(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getPDPContextList();
                    respondOk("getPDPContextList", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getPDPContextList", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getPDPContextList", privExc(thr));
                    respondExc("getPDPContextList", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getPreferredNetworkType(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getPreferredNetworkType();
                    respondOk("getPreferredNetworkType", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getPreferredNetworkType", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getPreferredNetworkType", privExc(thr));
                    respondExc("getPreferredNetworkType", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getPreferredVoicePrivacy(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getPreferredVoicePrivacy();
                    respondOk("getPreferredVoicePrivacy", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getPreferredVoicePrivacy", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getPreferredVoicePrivacy", privExc(thr));
                    respondExc("getPreferredVoicePrivacy", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getSignalStrength(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getSignalStrength();
                    respondOk("getSignalStrength", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getSignalStrength", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getSignalStrength", privExc(thr));
                    respondExc("getSignalStrength", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getSmscAddress(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getSmscAddress();
                    respondOk("getSmscAddress", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getSmscAddress", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getSmscAddress", privExc(thr));
                    respondExc("getSmscAddress", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getVoiceRadioTechnology(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getVoiceRadioTechnology();
                    respondOk("getVoiceRadioTechnology", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getVoiceRadioTechnology", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getVoiceRadioTechnology", privExc(thr));
                    respondExc("getVoiceRadioTechnology", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void getVoiceRegistrationState(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.getVoiceRegistrationState();
                    respondOk("getVoiceRegistrationState", msg, ret);
                } catch (CommandException exc) {
                    respondExc("getVoiceRegistrationState", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in getVoiceRegistrationState", privExc(thr));
                    respondExc("getVoiceRegistrationState", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void handleCallSetupRequestFromSim(final boolean a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.handleCallSetupRequestFromSim(a);
                    respondOk("handleCallSetupRequestFromSim", msg, ret);
                } catch (CommandException exc) {
                    respondExc("handleCallSetupRequestFromSim", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in handleCallSetupRequestFromSim", privExc(thr));
                    respondExc("handleCallSetupRequestFromSim", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void hangupConnection(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.hangupConnection(a);
                    respondOk("hangupConnection", msg, ret);
                } catch (CommandException exc) {
                    respondExc("hangupConnection", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in hangupConnection", privExc(thr));
                    respondExc("hangupConnection", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void hangupForegroundResumeBackground(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.hangupForegroundResumeBackground();
                    respondOk("hangupForegroundResumeBackground", msg, ret);
                } catch (CommandException exc) {
                    respondExc("hangupForegroundResumeBackground", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in hangupForegroundResumeBackground", privExc(thr));
                    respondExc("hangupForegroundResumeBackground", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void hangupWaitingOrBackground(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.hangupWaitingOrBackground();
                    respondOk("hangupWaitingOrBackground", msg, ret);
                } catch (CommandException exc) {
                    respondExc("hangupWaitingOrBackground", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in hangupWaitingOrBackground", privExc(thr));
                    respondExc("hangupWaitingOrBackground", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void iccIO(final int a, final int b, final String c, final int d, final int e, final int f, final String g, final String h, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.iccIO(a, b, c, d, e, f, g, h);
                    respondOk("iccIO", msg, ret);
                } catch (CommandException exc) {
                    respondExc("iccIO", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in iccIO", privExc(thr));
                    respondExc("iccIO", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void iccIOForApp(final int a, final int b, final String c, final int d, final int e, final int f, final String g, final String h, final String i, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.iccIOForApp(a, b, c, d, e, f, g, h, i);
                    respondOk("iccIOForApp", msg, ret);
                } catch (CommandException exc) {
                    respondExc("iccIOForApp", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in iccIOForApp", privExc(thr));
                    respondExc("iccIOForApp", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void invokeOemRilRequestRaw(final byte[] a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.invokeOemRilRequestRaw(a);
                    respondOk("invokeOemRilRequestRaw", msg, ret);
                } catch (CommandException exc) {
                    respondExc("invokeOemRilRequestRaw", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in invokeOemRilRequestRaw", privExc(thr));
                    respondExc("invokeOemRilRequestRaw", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void invokeOemRilRequestStrings(final String[] a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.invokeOemRilRequestStrings(a);
                    respondOk("invokeOemRilRequestStrings", msg, ret);
                } catch (CommandException exc) {
                    respondExc("invokeOemRilRequestStrings", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in invokeOemRilRequestStrings", privExc(thr));
                    respondExc("invokeOemRilRequestStrings", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void nvReadItem(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.nvReadItem(a);
                    respondOk("nvReadItem", msg, ret);
                } catch (CommandException exc) {
                    respondExc("nvReadItem", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in nvReadItem", privExc(thr));
                    respondExc("nvReadItem", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void nvResetConfig(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.nvResetConfig(a);
                    respondOk("nvResetConfig", msg, ret);
                } catch (CommandException exc) {
                    respondExc("nvResetConfig", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in nvResetConfig", privExc(thr));
                    respondExc("nvResetConfig", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void nvWriteCdmaPrl(final byte[] a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.nvWriteCdmaPrl(a);
                    respondOk("nvWriteCdmaPrl", msg, ret);
                } catch (CommandException exc) {
                    respondExc("nvWriteCdmaPrl", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in nvWriteCdmaPrl", privExc(thr));
                    respondExc("nvWriteCdmaPrl", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void nvWriteItem(final int a, final String b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.nvWriteItem(a, b);
                    respondOk("nvWriteItem", msg, ret);
                } catch (CommandException exc) {
                    respondExc("nvWriteItem", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in nvWriteItem", privExc(thr));
                    respondExc("nvWriteItem", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void queryAvailableBandMode(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.queryAvailableBandMode();
                    respondOk("queryAvailableBandMode", msg, ret);
                } catch (CommandException exc) {
                    respondExc("queryAvailableBandMode", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in queryAvailableBandMode", privExc(thr));
                    respondExc("queryAvailableBandMode", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void queryCLIP(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.queryCLIP();
                    respondOk("queryCLIP", msg, ret);
                } catch (CommandException exc) {
                    respondExc("queryCLIP", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in queryCLIP", privExc(thr));
                    respondExc("queryCLIP", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void queryCallForwardStatus(final int a, final int b, final String c, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.queryCallForwardStatus(a, b, c);
                    respondOk("queryCallForwardStatus", msg, ret);
                } catch (CommandException exc) {
                    respondExc("queryCallForwardStatus", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in queryCallForwardStatus", privExc(thr));
                    respondExc("queryCallForwardStatus", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void queryCallWaiting(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.queryCallWaiting(a);
                    respondOk("queryCallWaiting", msg, ret);
                } catch (CommandException exc) {
                    respondExc("queryCallWaiting", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in queryCallWaiting", privExc(thr));
                    respondExc("queryCallWaiting", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void queryCdmaRoamingPreference(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.queryCdmaRoamingPreference();
                    respondOk("queryCdmaRoamingPreference", msg, ret);
                } catch (CommandException exc) {
                    respondExc("queryCdmaRoamingPreference", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in queryCdmaRoamingPreference", privExc(thr));
                    respondExc("queryCdmaRoamingPreference", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void queryFacilityLock(final String a, final String b, final int c, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.queryFacilityLock(a, b, c);
                    respondOk("queryFacilityLock", msg, ret);
                } catch (CommandException exc) {
                    respondExc("queryFacilityLock", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in queryFacilityLock", privExc(thr));
                    respondExc("queryFacilityLock", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void queryFacilityLockForApp(final String a, final String b, final int c, final String d, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.queryFacilityLockForApp(a, b, c, d);
                    respondOk("queryFacilityLockForApp", msg, ret);
                } catch (CommandException exc) {
                    respondExc("queryFacilityLockForApp", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in queryFacilityLockForApp", privExc(thr));
                    respondExc("queryFacilityLockForApp", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void queryTTYMode(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.queryTTYMode();
                    respondOk("queryTTYMode", msg, ret);
                } catch (CommandException exc) {
                    respondExc("queryTTYMode", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in queryTTYMode", privExc(thr));
                    respondExc("queryTTYMode", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void rejectCall(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.rejectCall();
                    respondOk("rejectCall", msg, ret);
                } catch (CommandException exc) {
                    respondExc("rejectCall", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in rejectCall", privExc(thr));
                    respondExc("rejectCall", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void reportSmsMemoryStatus(final boolean a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.reportSmsMemoryStatus(a);
                    respondOk("reportSmsMemoryStatus", msg, ret);
                } catch (CommandException exc) {
                    respondExc("reportSmsMemoryStatus", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in reportSmsMemoryStatus", privExc(thr));
                    respondExc("reportSmsMemoryStatus", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void reportStkServiceIsRunning(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.reportStkServiceIsRunning();
                    respondOk("reportStkServiceIsRunning", msg, ret);
                } catch (CommandException exc) {
                    respondExc("reportStkServiceIsRunning", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in reportStkServiceIsRunning", privExc(thr));
                    respondExc("reportStkServiceIsRunning", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void requestIccSimAuthentication(final int a, final String b, final String c, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.requestIccSimAuthentication(a, b, c);
                    respondOk("requestIccSimAuthentication", msg, ret);
                } catch (CommandException exc) {
                    respondExc("requestIccSimAuthentication", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in requestIccSimAuthentication", privExc(thr));
                    respondExc("requestIccSimAuthentication", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void requestIsimAuthentication(final String a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.requestIsimAuthentication(a);
                    respondOk("requestIsimAuthentication", msg, ret);
                } catch (CommandException exc) {
                    respondExc("requestIsimAuthentication", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in requestIsimAuthentication", privExc(thr));
                    respondExc("requestIsimAuthentication", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void resetRadio(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.resetRadio();
                    respondOk("resetRadio", msg, ret);
                } catch (CommandException exc) {
                    respondExc("resetRadio", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in resetRadio", privExc(thr));
                    respondExc("resetRadio", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void sendBurstDtmf(final String a, final int b, final int c, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.sendBurstDtmf(a, b, c);
                    respondOk("sendBurstDtmf", msg, ret);
                } catch (CommandException exc) {
                    respondExc("sendBurstDtmf", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in sendBurstDtmf", privExc(thr));
                    respondExc("sendBurstDtmf", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void sendCDMAFeatureCode(final String a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.sendCDMAFeatureCode(a);
                    respondOk("sendCDMAFeatureCode", msg, ret);
                } catch (CommandException exc) {
                    respondExc("sendCDMAFeatureCode", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in sendCDMAFeatureCode", privExc(thr));
                    respondExc("sendCDMAFeatureCode", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void sendCdmaSms(final byte[] a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.sendCdmaSms(a);
                    respondOk("sendCdmaSms", msg, ret);
                } catch (CommandException exc) {
                    respondExc("sendCdmaSms", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in sendCdmaSms", privExc(thr));
                    respondExc("sendCdmaSms", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void sendDtmf(final char a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.sendDtmf(a);
                    respondOk("sendDtmf", msg, ret);
                } catch (CommandException exc) {
                    respondExc("sendDtmf", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in sendDtmf", privExc(thr));
                    respondExc("sendDtmf", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void sendEnvelope(final String a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.sendEnvelope(a);
                    respondOk("sendEnvelope", msg, ret);
                } catch (CommandException exc) {
                    respondExc("sendEnvelope", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in sendEnvelope", privExc(thr));
                    respondExc("sendEnvelope", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void sendEnvelopeWithStatus(final String a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.sendEnvelopeWithStatus(a);
                    respondOk("sendEnvelopeWithStatus", msg, ret);
                } catch (CommandException exc) {
                    respondExc("sendEnvelopeWithStatus", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in sendEnvelopeWithStatus", privExc(thr));
                    respondExc("sendEnvelopeWithStatus", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void sendImsCdmaSms(final byte[] a, final int b, final int c, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.sendImsCdmaSms(a, b, c);
                    respondOk("sendImsCdmaSms", msg, ret);
                } catch (CommandException exc) {
                    respondExc("sendImsCdmaSms", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in sendImsCdmaSms", privExc(thr));
                    respondExc("sendImsCdmaSms", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void sendImsGsmSms(final String a, final String b, final int c, final int d, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.sendImsGsmSms(a, b, c, d);
                    respondOk("sendImsGsmSms", msg, ret);
                } catch (CommandException exc) {
                    respondExc("sendImsGsmSms", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in sendImsGsmSms", privExc(thr));
                    respondExc("sendImsGsmSms", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void sendSMS(final String a, final String b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.sendSMS(a, b);
                    respondOk("sendSMS", msg, ret);
                } catch (CommandException exc) {
                    respondExc("sendSMS", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in sendSMS", privExc(thr));
                    respondExc("sendSMS", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void sendSMSExpectMore(final String a, final String b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.sendSMSExpectMore(a, b);
                    respondOk("sendSMSExpectMore", msg, ret);
                } catch (CommandException exc) {
                    respondExc("sendSMSExpectMore", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in sendSMSExpectMore", privExc(thr));
                    respondExc("sendSMSExpectMore", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void sendTerminalResponse(final String a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.sendTerminalResponse(a);
                    respondOk("sendTerminalResponse", msg, ret);
                } catch (CommandException exc) {
                    respondExc("sendTerminalResponse", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in sendTerminalResponse", privExc(thr));
                    respondExc("sendTerminalResponse", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void sendUSSD(final String a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.sendUSSD(a);
                    respondOk("sendUSSD", msg, ret);
                } catch (CommandException exc) {
                    respondExc("sendUSSD", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in sendUSSD", privExc(thr));
                    respondExc("sendUSSD", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void separateConnection(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.separateConnection(a);
                    respondOk("separateConnection", msg, ret);
                } catch (CommandException exc) {
                    respondExc("separateConnection", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in separateConnection", privExc(thr));
                    respondExc("separateConnection", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setBandMode(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setBandMode(a);
                    respondOk("setBandMode", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setBandMode", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setBandMode", privExc(thr));
                    respondExc("setBandMode", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setCLIR(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setCLIR(a);
                    respondOk("setCLIR", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setCLIR", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setCLIR", privExc(thr));
                    respondExc("setCLIR", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setCallForward(final int a, final int b, final int c, final String d, final int e, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setCallForward(a, b, c, d, e);
                    respondOk("setCallForward", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setCallForward", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setCallForward", privExc(thr));
                    respondExc("setCallForward", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setCallWaiting(final boolean a, final int b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setCallWaiting(a, b);
                    respondOk("setCallWaiting", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setCallWaiting", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setCallWaiting", privExc(thr));
                    respondExc("setCallWaiting", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setCdmaBroadcastActivation(final boolean a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setCdmaBroadcastActivation(a);
                    respondOk("setCdmaBroadcastActivation", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setCdmaBroadcastActivation", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setCdmaBroadcastActivation", privExc(thr));
                    respondExc("setCdmaBroadcastActivation", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setCdmaBroadcastConfig(final com.android.internal.telephony.cdma.CdmaSmsBroadcastConfigInfo[] a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setCdmaBroadcastConfig(a);
                    respondOk("setCdmaBroadcastConfig", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setCdmaBroadcastConfig", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setCdmaBroadcastConfig", privExc(thr));
                    respondExc("setCdmaBroadcastConfig", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setCdmaRoamingPreference(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setCdmaRoamingPreference(a);
                    respondOk("setCdmaRoamingPreference", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setCdmaRoamingPreference", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setCdmaRoamingPreference", privExc(thr));
                    respondExc("setCdmaRoamingPreference", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setCdmaSubscriptionSource(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setCdmaSubscriptionSource(a);
                    respondOk("setCdmaSubscriptionSource", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setCdmaSubscriptionSource", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setCdmaSubscriptionSource", privExc(thr));
                    respondExc("setCdmaSubscriptionSource", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setCellInfoListRate(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setCellInfoListRate(a);
                    respondOk("setCellInfoListRate", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setCellInfoListRate", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setCellInfoListRate", privExc(thr));
                    respondExc("setCellInfoListRate", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setDataAllowed(final boolean a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setDataAllowed(a);
                    respondOk("setDataAllowed", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setDataAllowed", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setDataAllowed", privExc(thr));
                    respondExc("setDataAllowed", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setDataProfile(final com.android.internal.telephony.dataconnection.DataProfile[] a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setDataProfile(a);
                    respondOk("setDataProfile", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setDataProfile", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setDataProfile", privExc(thr));
                    respondExc("setDataProfile", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setFacilityLock(final String a, final boolean b, final String c, final int d, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setFacilityLock(a, b, c, d);
                    respondOk("setFacilityLock", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setFacilityLock", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setFacilityLock", privExc(thr));
                    respondExc("setFacilityLock", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setFacilityLockForApp(final String a, final boolean b, final String c, final int d, final String e, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setFacilityLockForApp(a, b, c, d, e);
                    respondOk("setFacilityLockForApp", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setFacilityLockForApp", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setFacilityLockForApp", privExc(thr));
                    respondExc("setFacilityLockForApp", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setGsmBroadcastActivation(final boolean a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setGsmBroadcastActivation(a);
                    respondOk("setGsmBroadcastActivation", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setGsmBroadcastActivation", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setGsmBroadcastActivation", privExc(thr));
                    respondExc("setGsmBroadcastActivation", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setGsmBroadcastConfig(final com.android.internal.telephony.gsm.SmsBroadcastConfigInfo[] a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setGsmBroadcastConfig(a);
                    respondOk("setGsmBroadcastConfig", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setGsmBroadcastConfig", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setGsmBroadcastConfig", privExc(thr));
                    respondExc("setGsmBroadcastConfig", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setInitialAttachApn(final String a, final String b, final int c, final String d, final String e, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setInitialAttachApn(a, b, c, d, e);
                    respondOk("setInitialAttachApn", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setInitialAttachApn", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setInitialAttachApn", privExc(thr));
                    respondExc("setInitialAttachApn", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setLocationUpdates(final boolean a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setLocationUpdates(a);
                    respondOk("setLocationUpdates", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setLocationUpdates", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setLocationUpdates", privExc(thr));
                    respondExc("setLocationUpdates", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setMute(final boolean a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setMute(a);
                    respondOk("setMute", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setMute", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setMute", privExc(thr));
                    respondExc("setMute", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setNetworkSelectionModeAutomatic(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setNetworkSelectionModeAutomatic();
                    respondOk("setNetworkSelectionModeAutomatic", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setNetworkSelectionModeAutomatic", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setNetworkSelectionModeAutomatic", privExc(thr));
                    respondExc("setNetworkSelectionModeAutomatic", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setNetworkSelectionModeManual(final String a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setNetworkSelectionModeManual(a);
                    respondOk("setNetworkSelectionModeManual", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setNetworkSelectionModeManual", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setNetworkSelectionModeManual", privExc(thr));
                    respondExc("setNetworkSelectionModeManual", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setPreferredNetworkType(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setPreferredNetworkType(a);
                    respondOk("setPreferredNetworkType", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setPreferredNetworkType", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setPreferredNetworkType", privExc(thr));
                    respondExc("setPreferredNetworkType", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setPreferredVoicePrivacy(final boolean a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setPreferredVoicePrivacy(a);
                    respondOk("setPreferredVoicePrivacy", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setPreferredVoicePrivacy", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setPreferredVoicePrivacy", privExc(thr));
                    respondExc("setPreferredVoicePrivacy", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setRadioPower(final boolean a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setRadioPower(a);
                    respondOk("setRadioPower", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setRadioPower", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setRadioPower", privExc(thr));
                    respondExc("setRadioPower", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setSmscAddress(final String a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setSmscAddress(a);
                    respondOk("setSmscAddress", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setSmscAddress", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setSmscAddress", privExc(thr));
                    respondExc("setSmscAddress", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setSuppServiceNotifications(final boolean a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setSuppServiceNotifications(a);
                    respondOk("setSuppServiceNotifications", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setSuppServiceNotifications", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setSuppServiceNotifications", privExc(thr));
                    respondExc("setSuppServiceNotifications", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setTTYMode(final int a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setTTYMode(a);
                    respondOk("setTTYMode", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setTTYMode", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setTTYMode", privExc(thr));
                    respondExc("setTTYMode", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void setupDataCall(final String a, final String b, final String c, final String d, final String e, final String f, final String g, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.setupDataCall(a, b, c, d, e, f, g);
                    respondOk("setupDataCall", msg, ret);
                } catch (CommandException exc) {
                    respondExc("setupDataCall", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in setupDataCall", privExc(thr));
                    respondExc("setupDataCall", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void startDtmf(final char a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.startDtmf(a);
                    respondOk("startDtmf", msg, ret);
                } catch (CommandException exc) {
                    respondExc("startDtmf", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in startDtmf", privExc(thr));
                    respondExc("startDtmf", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void stopDtmf(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.stopDtmf();
                    respondOk("stopDtmf", msg, ret);
                } catch (CommandException exc) {
                    respondExc("stopDtmf", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in stopDtmf", privExc(thr));
                    respondExc("stopDtmf", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void supplyDepersonalization(final String a, final String b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.supplyDepersonalization(a, b);
                    respondOk("supplyDepersonalization", msg, ret);
                } catch (CommandException exc) {
                    respondExc("supplyDepersonalization", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in supplyDepersonalization", privExc(thr));
                    respondExc("supplyDepersonalization", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void supplyIccPin(final String a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.supplyIccPin(a);
                    respondOk("supplyIccPin", msg, ret);
                } catch (CommandException exc) {
                    respondExc("supplyIccPin", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in supplyIccPin", privExc(thr));
                    respondExc("supplyIccPin", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void supplyIccPin2(final String a, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.supplyIccPin2(a);
                    respondOk("supplyIccPin2", msg, ret);
                } catch (CommandException exc) {
                    respondExc("supplyIccPin2", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in supplyIccPin2", privExc(thr));
                    respondExc("supplyIccPin2", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void supplyIccPin2ForApp(final String a, final String b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.supplyIccPin2ForApp(a, b);
                    respondOk("supplyIccPin2ForApp", msg, ret);
                } catch (CommandException exc) {
                    respondExc("supplyIccPin2ForApp", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in supplyIccPin2ForApp", privExc(thr));
                    respondExc("supplyIccPin2ForApp", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void supplyIccPinForApp(final String a, final String b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.supplyIccPinForApp(a, b);
                    respondOk("supplyIccPinForApp", msg, ret);
                } catch (CommandException exc) {
                    respondExc("supplyIccPinForApp", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in supplyIccPinForApp", privExc(thr));
                    respondExc("supplyIccPinForApp", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void supplyIccPuk(final String a, final String b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.supplyIccPuk(a, b);
                    respondOk("supplyIccPuk", msg, ret);
                } catch (CommandException exc) {
                    respondExc("supplyIccPuk", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in supplyIccPuk", privExc(thr));
                    respondExc("supplyIccPuk", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void supplyIccPuk2(final String a, final String b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.supplyIccPuk2(a, b);
                    respondOk("supplyIccPuk2", msg, ret);
                } catch (CommandException exc) {
                    respondExc("supplyIccPuk2", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in supplyIccPuk2", privExc(thr));
                    respondExc("supplyIccPuk2", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void supplyIccPuk2ForApp(final String a, final String b, final String c, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.supplyIccPuk2ForApp(a, b, c);
                    respondOk("supplyIccPuk2ForApp", msg, ret);
                } catch (CommandException exc) {
                    respondExc("supplyIccPuk2ForApp", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in supplyIccPuk2ForApp", privExc(thr));
                    respondExc("supplyIccPuk2ForApp", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void supplyIccPukForApp(final String a, final String b, final String c, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.supplyIccPukForApp(a, b, c);
                    respondOk("supplyIccPukForApp", msg, ret);
                } catch (CommandException exc) {
                    respondExc("supplyIccPukForApp", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in supplyIccPukForApp", privExc(thr));
                    respondExc("supplyIccPukForApp", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void switchWaitingOrHoldingAndActive(final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.switchWaitingOrHoldingAndActive();
                    respondOk("switchWaitingOrHoldingAndActive", msg, ret);
                } catch (CommandException exc) {
                    respondExc("switchWaitingOrHoldingAndActive", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in switchWaitingOrHoldingAndActive", privExc(thr));
                    respondExc("switchWaitingOrHoldingAndActive", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void writeSmsToRuim(final int a, final String b, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.writeSmsToRuim(a, b);
                    respondOk("writeSmsToRuim", msg, ret);
                } catch (CommandException exc) {
                    respondExc("writeSmsToRuim", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in writeSmsToRuim", privExc(thr));
                    respondExc("writeSmsToRuim", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

    public void writeSmsToSim(final int a, final String b, final String c, final android.os.Message msg) {
        runOnDbusThread(new Runnable() {
            public void run() {
                sCurrentMsg = msg;
                try {
                    Object ret = mRilImpl.writeSmsToSim(a, b, c);
                    respondOk("writeSmsToSim", msg, ret);
                } catch (CommandException exc) {
                    respondExc("writeSmsToSim", msg, exc, null);
                } catch (Throwable thr) {
                    Rlog.e(TAG, "Uncaught exception in writeSmsToSim", privExc(thr));
                    respondExc("writeSmsToSim", msg, new CommandException(GENERIC_FAILURE), null);
                }
            }
        });
    }

}
