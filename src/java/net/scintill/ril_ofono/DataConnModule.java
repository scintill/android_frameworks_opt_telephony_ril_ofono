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
import android.text.TextUtils;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.dataconnection.DataCallResponse;
import com.android.internal.telephony.dataconnection.DcFailCause;

import net.scintill.ril_ofono.ModemModule.OfonoNetworkTechnology;
import net.scintill.ril_ofono.ModemModule.OfonoRegistrationState;

import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Variant;
import org.ofono.ConnectionContext;
import org.ofono.ConnectionManager;
import org.ofono.StructPathAndProps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.android.internal.telephony.CommandException.Error.MODE_NOT_SUPPORTED;
import static com.android.internal.telephony.CommandException.Error.NO_SUCH_ELEMENT;
import static com.android.internal.telephony.CommandException.Error.REQUEST_NOT_SUPPORTED;
import static net.scintill.ril_ofono.RilOfono.RegistrantList;
import static net.scintill.ril_ofono.RilOfono.notifyResultAndLog;
import static net.scintill.ril_ofono.RilOfono.privStr;
import static net.scintill.ril_ofono.RilOfono.runOnMainThreadDebounced;

/*package*/ class DataConnModule extends PropManager {

    private static final String TAG = RilOfono.TAG;

    private ConnectionManager mConnMan;
    private RegistrantList mDataNetworkStateRegistrants;

    private final Map<String, Variant> mConnManProps = new HashMap<>();
    private final Map<String, Map<String, Variant>> mConnectionsProps = new HashMap<>();

    DataConnModule(RegistrantList dataNetworkStateRegistrants) {
        mDataNetworkStateRegistrants = dataNetworkStateRegistrants;

        mConnMan = RilOfono.sInstance.getOfonoInterface(ConnectionManager.class);

        RilOfono.sInstance.registerDbusSignal(ConnectionManager.ContextAdded.class, this);
        RilOfono.sInstance.registerDbusSignal(ConnectionManager.ContextRemoved.class, this);
        mirrorProps(ConnectionManager.class, mConnMan, ConnectionManager.PropertyChanged.class, mConnManProps);
        RilOfono.sInstance.registerDbusSignal(ConnectionContext.PropertyChanged.class, this);
    }

    @RilMethod
    public Object getDataRegistrationState() {
        return new String[] {
            // see e.g. GsmServiceStateTracker for the values and offsets, though some appear unused
            ""+(getProp(mConnManProps, "Attached", Boolean.FALSE) ? OfonoRegistrationState.registered : OfonoRegistrationState.unregistered).ts27007Creg,
            "", "", // unused?
            ""+getProp(mConnManProps, "Bearer", OfonoNetworkTechnology._unknown).serviceStateInt,
        };
    }

    @RilMethod
    public Object getDataCallList() {
        return new PrivResponseOb(getDataCallListImpl());
    }

    @RilMethod
    public Object setupDataCall(String radioTechnologyStr, String profile, String apnStr, String user, String password, String authType, String protocol) {
        OfonoNetworkTechnology radioTechnology = OfonoNetworkTechnology.fromSetupDataCallValue(Integer.valueOf(radioTechnologyStr));
        OfonoNetworkTechnology currentRadioTechnology = getProp(mConnManProps, "Bearer", OfonoNetworkTechnology._unknown);

        Rlog.d(TAG, "setupDataCall "+radioTechnology+"("+radioTechnologyStr+") "+privStr(profile+" "+apnStr+" "+user+" "+password+" "+authType)+" "+protocol);

        // let's be stringent for now...
        if (radioTechnology != currentRadioTechnology) {
            Rlog.e(TAG, "Unable to provide requested radio technology "+radioTechnology+"("+radioTechnologyStr+"); current is "+currentRadioTechnology);
            throw new CommandException(MODE_NOT_SUPPORTED);
        }
        if (!profile.equals(String.valueOf(RILConstants.DATA_PROFILE_DEFAULT))) {
            Rlog.e(TAG, "Unable to provide non-default data call profile "+profile);
            throw new CommandException(MODE_NOT_SUPPORTED);
        }

        final Apn apn = new Apn();
        apn.apn = apnStr;
        apn.username = user;
        apn.password = password;
        apn.authType = Integer.parseInt(authType);
        apn.protocol = protocol;

        Path ctxPath = mConnMan.AddContext("internet");
        ConnectionContext ctx = RilOfono.sInstance.getOfonoInterface(ConnectionContext.class, ctxPath.getPath());
        apn.setOnContext(ctx);
        setContextActive(ctx, true);
        return new PrivResponseOb(getDataCallResponse(ctxPath.getPath(), ctx.GetProperties()));
    }

    @RilMethod
    public Object deactivateDataCall(int cid, int reason) {
        Rlog.d(TAG, "deactivateDataCall "+cid+" "+reason);
        String path = getPathFromUniqueIntId(cid);
        if (path == null) {
            throw new CommandException(NO_SUCH_ELEMENT);
        }
        mConnMan.RemoveContext(new Path(path));
        forgetAboutUniqueId(path);
        return null;
    }

    private DataCallResponse getDataCallResponse(String dbusPath, Map<String, Variant> props) {
        Map<String, Variant> ipSettings = getProp(props, "Settings", new HashMap<String, Variant>());
        // TODO ipv6?

        if (!getProp(props, "Active", Boolean.FALSE)) {
            return null;
        }

        DataCallResponse dcr = new DataCallResponse();
        // see RIL#getDataCallResponse for guidance on these values
        dcr.version = 11;
        dcr.status = DcFailCause.NONE.getErrorCode();
        dcr.suggestedRetryTime = -1;
        dcr.cid = getUniqueIntId(dbusPath);
        dcr.active = getProp(props, "Active", Boolean.FALSE) ? DATA_CONNECTION_ACTIVE_PH_LINK_UP : DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE;
        dcr.type = ""; // I don't think anything is using this, and I'm not sure the valid values

        dcr.ifname = getProp(ipSettings, "Interface", "");
        if (TextUtils.isEmpty(dcr.ifname)) {
            Rlog.e(TAG, "empty Interface for datacall "+dbusPath+"; skipping");
            return null;
        }

        dcr.addresses = singleStringToArray(getProp(ipSettings, "Address", ""));
        dcr.dnses = getProp(ipSettings, "DomainNameServers", new String[0]);
        dcr.gateways = singleStringToArray(getProp(ipSettings, "Gateway", ""));
        dcr.mtu = PhoneConstants.UNSET_MTU;
        return dcr;
    }

    private List<DataCallResponse> getDataCallListImpl() {
        List<DataCallResponse> list = new ArrayList<>();
        //Rlog.d(TAG, "mConnectionsProps="+privStr(mConnectionsProps));
        for (Map.Entry<String, Map<String, Variant>> connectionPropsEntry : mConnectionsProps.entrySet()) {
            String dbusPath = connectionPropsEntry.getKey();
            Map<String, Variant> props = connectionPropsEntry.getValue();
            DataCallResponse dcr = getDataCallResponse(dbusPath, props);
            if (dcr != null) {
                list.add(dcr);
            }
        }
        //Rlog.d(TAG, "getDataCallList return "+privStr(list));
        return list;
    }

    @RilMethod
    public Object setDataAllowed(boolean allowed) {
        Rlog.d(TAG, "setDataAllowed " + allowed);
        /*
         * from oFono docs:
         * 		boolean Powered [readwrite]
		 * Controls whether packet radio use is allowed. Setting
		 * this value to off detaches the modem from the
		 * Packet Domain network.
         */
        mConnMan.SetProperty("Powered", new Variant<>(allowed));
        return null;
    }

    @RilMethod
    public Object setInitialAttachApn(String apn, String protocol, int authType, String username, String password) {
        // not sure what this means, or whether it's applicable to oFono
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    private void setContextActive(ConnectionContext ctx, boolean active) {
        ctx.SetProperty("Active", new Variant<>(active));
    }

    public void handle(ConnectionContext.PropertyChanged s) {
        handle2dPropChange(mConnectionsProps, s.getPath(), ConnectionContext.class, s.name, s.value);
        runOnMainThreadDebounced(mFnNotifyDataNetworkState, 200);
    }

    public void onPropChange(ConnectionManager connMan, String name, Variant value) {
        if (name.equals("Attached")) {
            mConnectionsProps.clear();
            if (value.getValue().equals(Boolean.TRUE)) {
                List<StructPathAndProps> contexts = mConnMan.GetContexts();
                for (StructPathAndProps pandp : contexts) {
                    mConnectionsProps.put(pandp.path.getPath(), new HashMap<>(pandp.props)); // copy because DBus's is immutable
                }
            } else {
                Rlog.d(TAG, "data contexts not attached");
            }
        }
        runOnMainThreadDebounced(mFnNotifyDataNetworkState, 200);
    }

    public void handle(ConnectionManager.ContextAdded s) {
        mConnectionsProps.put(s.path.getPath(), new HashMap<>(s.properties)); // copy because DBus's is immutable
        runOnMainThreadDebounced(mFnNotifyDataNetworkState, 200);
    }

    public void handle(ConnectionManager.ContextRemoved s) {
        forgetAboutUniqueId(s.path.getPath());
        mConnectionsProps.remove(s.path.getPath());
        runOnMainThreadDebounced(mFnNotifyDataNetworkState, 200);
    }

    private final DebouncedRunnable mFnNotifyDataNetworkState = new DebouncedRunnable() {
        @Override
        public void run() {
            Object calls = getDataCallListImpl();
            Rlog.d(TAG, "notify dataNetworkState "+privStr(calls));
            notifyResultAndLog("data netstate", mDataNetworkStateRegistrants, calls, true);
        }
    };

    private final Map<String, Integer> mPathToConnIdMap = new HashMap<>();
    private int mNextConnId = 1;

    private int getUniqueIntId(String contextName) {
        synchronized (mPathToConnIdMap) {
            if (!mPathToConnIdMap.containsKey(contextName)) {
                int thisConnId = mNextConnId;
                mPathToConnIdMap.put(contextName, thisConnId);
                mNextConnId++;
                return thisConnId;
            } else {
                return mPathToConnIdMap.get(contextName);
            }
        }
    }

    private String getPathFromUniqueIntId(int i) {
        // this should be small enough that I don't anticipate performance issues scanning for value
        for (Map.Entry<String, Integer> entry : mPathToConnIdMap.entrySet()) {
            if (entry.getValue() == i) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void forgetAboutUniqueId(String path) {
        mPathToConnIdMap.remove(path);
    }

    // from com.android.internal.telephony.dataconnection.DcController ; not accessible to us
    static final int DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE = 0;
    static final int DATA_CONNECTION_ACTIVE_PH_LINK_DORMANT = 1;
    static final int DATA_CONNECTION_ACTIVE_PH_LINK_UP = 2;
    static final int DATA_CONNECTION_ACTIVE_UNKNOWN = Integer.MAX_VALUE;

    class Apn {
        public String apn;
        public String protocol;
        public int authType;
        public String username;
        public String password;

        public void setOnContext(ConnectionContext ctx) {
            if (!protocol.equalsIgnoreCase("IP")) {
                throw new IllegalArgumentException("unsupported protocol"); // TODO ipv6?
            }

            ctx.SetProperty("AccessPointName", new Variant<>(apn));
            ctx.SetProperty("Protocol", new Variant<>("ip"));
            OfonoAuthMethod authMethod = OfonoAuthMethod.fromRilConstant(authType);
            if (authMethod != null) {
                ctx.SetProperty("AuthenticationMethod", new Variant<>(authMethod.toString()));
            }
            ctx.SetProperty("Username", new Variant<>(username));
            ctx.SetProperty("Password", new Variant<>(password));
        }
    }

    enum OfonoAuthMethod {
        pap, chap;
        static OfonoAuthMethod fromRilConstant(int i) {
            switch(i) {
                case RILConstants.SETUP_DATA_AUTH_PAP: return pap;
                case RILConstants.SETUP_DATA_AUTH_CHAP: return chap;
                default:
                    Rlog.e(TAG, "unknown/non-mapping authmethod constant "+i+"; not setting method");
                    return null;
            }
        }
    }

    private String[] singleStringToArray(String s) {
        return !TextUtils.isEmpty(s) ? new String[] { s } : new String[0];
    }

}
