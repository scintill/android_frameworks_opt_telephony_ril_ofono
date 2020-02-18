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

import android.annotation.Nullable;
import android.net.NetworkUtils;
import android.os.INetworkManagementService;
import android.os.RemoteException;
import android.telephony.Rlog;
import android.text.TextUtils;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.dataconnection.DataCallResponse;
import com.android.internal.telephony.dataconnection.DcFailCause;

import net.scintill.ril_ofono.NetworkRegistrationModule.OfonoNetworkTechnology;
import net.scintill.ril_ofono.NetworkRegistrationModule.OfonoRegistrationState;

import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.Variant;
import org.ofono.ConnectionContext;
import org.ofono.ConnectionManager;
import org.ofono.Error.NotAttached;
import org.ofono.NetworkRegistration;
import org.ofono.StructPathAndProps;

import java.net.Inet4Address;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import libcore.io.Memory;

import static com.android.internal.telephony.CommandException.Error.MODE_NOT_SUPPORTED;
import static com.android.internal.telephony.CommandException.Error.NO_SUCH_ELEMENT;
import static com.android.internal.telephony.CommandException.Error.OP_NOT_ALLOWED_BEFORE_REG_NW;
import static com.android.internal.telephony.CommandException.Error.REQUEST_NOT_SUPPORTED;
import static net.scintill.ril_ofono.RilOfono.RegistrantList;
import static net.scintill.ril_ofono.RilOfono.notifyResultAndLog;
import static net.scintill.ril_ofono.RilOfono.privExc;
import static net.scintill.ril_ofono.RilOfono.privStr;
import static net.scintill.ril_ofono.RilOfono.runOnMainThreadDebounced;

/*package*/ class DatacallModule extends PropManager implements RilDatacallInterface {

    private static final String TAG = RilOfono.TAG;

    private ConnectionManager mConnMan;
    private NetworkRegistration mNetReg;
    private INetworkManagementService mNetworkManagementService;
    private NativeRild mNativeRild;
    private RegistrantList mDataNetworkStateRegistrants;
    private RegistrantList mVoiceNetworkStateRegistrants;

    private final Map<String, Variant<?>> mNetRegProps = new HashMap<>();
    private final Map<String, Variant<?>> mConnManProps = new HashMap<>();
    private final Map<String, Map<String, Variant<?>>> mConnectionsProps = new HashMap<>();
    private final Map<String, String> mLastInterface = new HashMap<>();

    DatacallModule(ConnectionManager connMan, NetworkRegistration netReg, RegistrantList dataNetworkStateRegistrants, RegistrantList voiceNetworkStateRegistrants, INetworkManagementService networkManagementService, NativeRild nativeRild) {
        Rlog.v(TAG, "DatacallModule()");
        mConnMan = connMan;
        mNetReg = netReg;
        mDataNetworkStateRegistrants = dataNetworkStateRegistrants;
        mVoiceNetworkStateRegistrants = voiceNetworkStateRegistrants;
        mNetworkManagementService = networkManagementService;
        mNativeRild = nativeRild;

        initProps(mConnManProps, ConnectionManager.class, mConnMan);
        initProps(mNetRegProps, NetworkRegistration.class, mNetReg);

        // initialize current connections
        for (StructPathAndProps p : mConnMan.GetContexts()) {
            mConnectionsProps.put(p.path.getPath(), new HashMap<>(p.props)); // copy because DBus's is immutable
        }
        getDataCallListImpl(); // XXX populate mLastInterface by side-effect
    }

    /*package*/ void handle(ConnectionManager.PropertyChanged s) {
        handle(s, mConnMan, ConnectionManager.PropertyChanged.class, mConnManProps, ConnectionManager.class);
    }

    /*package*/ void handle(NetworkRegistration.PropertyChanged s) {
        handle(s, mNetReg, NetworkRegistration.PropertyChanged.class, mNetRegProps, NetworkRegistration.class, false);
        // suppress logs because the NetworkRegistrationModule will be outputting them and I don't want double
    }

    @Override
    @OkOnMainThread
    public Object getDataRegistrationState() {
        // XXX Hack. Somehow we are not getting this prop updated correctly.
        // oFono source has a comment about LTE effectively is always attached, so let's follow that.
        boolean attached =
            isLte() || // TODO if LTE, check registered?
            getProp(mConnManProps, "Attached", Boolean.FALSE);

        return new String[] {
            // see e.g. GsmServiceStateTracker for the values and offsets, though some appear unused
            ""+(attached ? OfonoRegistrationState.registered : OfonoRegistrationState.unregistered).ts27007Creg,
            "", "", // unused?
            ""+getBearerTechnology().serviceStateInt,
        };
    }

    private boolean isLte() {
        return getBearerTechnology().equals(OfonoNetworkTechnology.lte);
    }


    @Override
    @OkOnMainThread
    public Object getDataCallList() {
        return new PrivResponseOb(getDataCallListImpl());
    }

    @Override
    public Object setupDataCall(String radioTechnologyIntStr, String profile, String apnStr, String user, String password, String authType, String protocol) {
        OfonoNetworkTechnology radioTechnology = OfonoNetworkTechnology.fromSetupDataCallValue(Integer.valueOf(radioTechnologyIntStr));
        OfonoNetworkTechnology currentRadioTechnology = getBearerTechnology();

        Rlog.d(TAG, "setupDataCall "+radioTechnology+"("+radioTechnologyIntStr+") "+privStr(profile+" "+apnStr+" "+user+" "+password+" "+authType)+" "+protocol);

        // let's be stringent for now...
        if (radioTechnology != currentRadioTechnology) {
            Rlog.e(TAG, "Unable to provide requested radio technology "+radioTechnology+"("+radioTechnologyIntStr+"); current is "+currentRadioTechnology);
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

        String ctxPath;
        ConnectionContext ctx;
        if (!isLte()) {
            mConnMan.DeactivateAll();
            mConnMan.SetProperty("Powered", new Variant<>(false));
            mConnMan.ResetContexts();
            mConnMan.SetProperty("Powered", new Variant<>(true));
            ctxPath = mConnMan.AddContext("internet").getPath();
            ctx = RilOfono.sInstance.getOfonoInterface(ConnectionContext.class, ctxPath);
            try {
                apn.setOnContext(ctx);
                setContextActive(ctx, true);
            } catch (NotAttached e) {
                throw new CommandException(OP_NOT_ALLOWED_BEFORE_REG_NW);
            }
        } else {
            ctxPath = findDefaultContext();
            if (ctxPath == null) {
                Rlog.w(TAG, "no connection context available in setupDataCall(). not registered?");
                throw new CommandException(OP_NOT_ALLOWED_BEFORE_REG_NW);
            }
            ctx = RilOfono.sInstance.getOfonoInterface(ConnectionContext.class, ctxPath);
            // don't modify the context, just return it so the caller can query/watch its configuration
        }
        return new PrivResponseOb(getDataCallResponse(ctxPath, ctx.GetProperties()));
    }

    @Nullable
    private String findDefaultContext() {
        for (Map.Entry<String, Map<String, Variant<?>>> connectionPropsEntry : mConnectionsProps.entrySet()) {
            String dbusPath = connectionPropsEntry.getKey();
            Map<String, Variant<?>> props = connectionPropsEntry.getValue();
            if (getProp(props, "AccessPointName", "").equals("automatic")) { // TODO use Name instead? it's not available for some reason
                return dbusPath;
            }
        }
        return null;
    }

    @Override
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

    private DataCallResponse getDataCallResponse(String dbusPath, Map<String, Variant<?>> props) {
        Map<String, Variant<?>> ipSettings = getProp(props, "Settings", new HashMap<String, Variant<?>>());
        // TODO ipv6?

        DataCallResponse dcr = new DataCallResponse();
        // see RIL#getDataCallResponse for guidance on these values
        dcr.version = 11;
        dcr.status = DcFailCause.NONE.getErrorCode();
        dcr.suggestedRetryTime = -1;
        dcr.cid = getUniqueIntId(dbusPath);
        dcr.active = getProp(props, "Active", Boolean.FALSE) ? DATA_CONNECTION_ACTIVE_PH_LINK_UP : DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE;
        dcr.type = ""; // I don't think anything is using this, and I'm not sure the valid values

        dcr.ifname = getProp(ipSettings, "Interface", (String)null);
        if (dcr.ifname != null) {
            mLastInterface.put(dbusPath, dcr.ifname);
        }
        dcr.addresses = singleStringToArray(getProp(ipSettings, "Address", ""));
        if (dcr.addresses.length > 0) {
            String netmask = getProp(ipSettings, "Netmask", (String)null);
            if (!TextUtils.isEmpty(netmask)) {
                dcr.addresses[0] += "/"+getPrefixLength((Inet4Address)NetworkUtils.numericToInetAddress(netmask));
            }
        }
        dcr.dnses = getProp(ipSettings, "DomainNameServers", new String[0]);
        dcr.gateways = singleStringToArray(getProp(ipSettings, "Gateway", ""));
        dcr.mtu = PhoneConstants.UNSET_MTU;
        //Rlog.d(TAG, "getDataCallResponse "+dbusPath+" "+props+" -> "+dcr, new Exception());
        return dcr;
    }

    // something in the framework seems to assume ArrayList, so make it part of our signature here
    private ArrayList<DataCallResponse> getDataCallListImpl() {
        ArrayList<DataCallResponse> list = new ArrayList<>();
        //Rlog.d(TAG, "mConnectionsProps="+privStr(mConnectionsProps));
        for (Map.Entry<String, Map<String, Variant<?>>> connectionPropsEntry : mConnectionsProps.entrySet()) {
            String dbusPath = connectionPropsEntry.getKey();
            Map<String, Variant<?>> props = connectionPropsEntry.getValue();
            DataCallResponse dcr = getDataCallResponse(dbusPath, props);
            if (dcr != null) {
                list.add(dcr);
            }
        }
        //Rlog.d(TAG, "getDataCallList return "+privStr(list));
        return list;
    }

    @Override
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

    @Override
    @OkOnMainThread
    public Object setInitialAttachApn(String apn, String protocol, int authType, String username, String password) {
        // not sure what this means, or whether it's applicable to oFono
        throw new CommandException(REQUEST_NOT_SUPPORTED);
    }

    private void setContextActive(ConnectionContext ctx, boolean active) {
        ctx.SetProperty("Active", new Variant<>(active));
    }

    /*package*/ void handle(ConnectionContext.PropertyChanged s) {
        if (handle2dPropChange(mConnectionsProps, s.getPath(), ConnectionContext.class, s.name, s.value)) {
            runOnMainThreadDebounced(mFnNotifyDataNetworkState, 200);
        }
        if (s.name.equals("Settings") || s.name.equals("Active")) {
            try {
                Map<String, Variant<?>> connectionProps = mConnectionsProps.get(s.getPath());
                if (connectionProps != null) { // might have been deleted already in the case of going inactive
                    onContextIp4SettingsChange(s.getPath(), getDataCallResponse(s.getPath(), connectionProps));
                }
            } catch (RemoteException e) {
                Rlog.e(TAG, "Uncaught RemoteException while setting up/down data context", privExc(e));
            }
        }
    }

    protected void onPropChange(ConnectionManager connMan, String name, Variant<?> value) {
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

    protected void onPropChange(NetworkRegistration netReg, String name, Variant<?> value) {
        // XXX workaround possible oFono bug. We may never find out we're Attached without this...
        if (name.equals("Status")) {
            initProps(mConnManProps, ConnectionManager.class, mConnMan);
        }
    }

    /*package*/ void handle(ConnectionManager.ContextAdded s) {
        mConnectionsProps.put(s.path.getPath(), new HashMap<>(s.properties)); // copy because DBus's is immutable
        runOnMainThreadDebounced(mFnNotifyDataNetworkState, 200);
    }

    /*package*/ void handle(ConnectionManager.ContextRemoved s) {
        String dbusPath = s.path.getPath();

        // tear down interface
        try {
            Map<String, Variant<?>> connectionProps = mConnectionsProps.get(dbusPath);
            if (connectionProps != null) {
                DataCallResponse dcr = getDataCallResponse(dbusPath, connectionProps);
                dcr.active = DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE;
                onContextIp4SettingsChange(dbusPath, dcr);
            }
        } catch (Throwable e) {
            Rlog.e(TAG, "handle(" + s.getClass() + ") " + dbusPath + ": Unexpected exception", privExc(e));
        }

        forgetAboutUniqueId(dbusPath);
        mConnectionsProps.remove(dbusPath);
        runOnMainThreadDebounced(mFnNotifyDataNetworkState, 200);
    }

    private final DebouncedRunnable mFnNotifyDataNetworkState = new DebouncedRunnable() {
        @Override
        public void run() {
            notifyResultAndLog("data netstate", mDataNetworkStateRegistrants, getDataCallListImpl(), true);

            // XXX This one seems out-of-place, but as far as I can tell it's the way to get
            // ServiceStateTracker to poll us and discover our data registration state.
            notifyResultAndLog("voice netstate (because of data netstate)", mVoiceNetworkStateRegistrants, null, false);
        }
    };

    // TODO it's IPV4 only
    private boolean onContextIp4SettingsChange(String dbusPath, DataCallResponse dcr) throws RemoteException {
        //Rlog.d(TAG, "onContextIp4SettingsChange() dcr="+dcr);
        if (dcr == null) {
            Rlog.e(TAG, "onContextIpSettingsChange: invalid DataCallResponse");
            return false;
        }

        if (dcr.active == DATA_CONNECTION_ACTIVE_PH_LINK_INACTIVE) {
            // dcr's iface seems to be null at this point, so use the last one we knew about
            if (mLastInterface.get(dbusPath) != null) {
                String iface = mLastInterface.get(dbusPath);
                mNativeRild.shutdownInterface(iface);
            } else {
                Rlog.e(TAG, "Interface unknown for context "+dbusPath+"; unable to ifdown");
                return false;
            }
        } else if (dcr.active == DATA_CONNECTION_ACTIVE_PH_LINK_UP) {
            if (dcr.ifname == null) {
                Rlog.w(TAG, "Got an active connection with no interface; ignoring (might be mid-statechange)");
                return false;
            }
            if (dcr.addresses.length != 1) {
                // we currently can't get this state from oFono
                Rlog.e(TAG, "Got interface with "+dcr.addresses.length+" addresses; ignoring");
                return false;
            }

            String[] addrPieces = dcr.addresses[0].split("/");
            mNativeRild.configureInterface(
                dcr.ifname,
                addrPieces[0],
                Integer.parseInt(addrPieces[1]),
                dcr.gateways.length >= 1 ? dcr.gateways[0] : null,
                dcr.dnses.length >= 1 ? dcr.dnses[0] : null,
                dcr.dnses.length >= 2 ? dcr.dnses[1] : null
            );
        } else {
            Rlog.w(TAG, "Ignoring context with unknown state dcr.active="+ dcr.active);
            return false;
        }

        return true;
    }

    private int getPrefixLength(Inet4Address netmask) {
        return Integer.bitCount(Memory.peekInt(netmask.getAddress(), 0, ByteOrder.BIG_ENDIAN));
    }

    private OfonoNetworkTechnology getBearerTechnology() {
        // ConnectionManager Bearer property is optional, so fall back on voice radio tech
        OfonoNetworkTechnology tech = getProp(mConnManProps, "Bearer", OfonoNetworkTechnology.none);
        if (tech == OfonoNetworkTechnology.none) {
            tech = getProp(mNetRegProps, "Technology", OfonoNetworkTechnology.none);
        }
        return tech;
    }

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
                ctx.SetProperty("Username", new Variant<>(username));
                ctx.SetProperty("Password", new Variant<>(password));
            } else {
                ctx.SetProperty("Username", new Variant<>(""));
                ctx.SetProperty("Password", new Variant<>(""));
            }
        }
    }

    enum OfonoAuthMethod {
        pap, chap;
        static OfonoAuthMethod fromRilConstant(int i) {
            switch(i) {
                case RILConstants.SETUP_DATA_AUTH_PAP: return pap;
                case RILConstants.SETUP_DATA_AUTH_CHAP: return chap;
                case RILConstants.SETUP_DATA_AUTH_NONE: return null;
                default:
                    Rlog.e(TAG, "unknown/unsupported authmethod constant "+i+"; not setting method");
                    return null;
            }
        }
    }

    private String[] singleStringToArray(String s) {
        return !TextUtils.isEmpty(s) ? new String[] { s } : new String[0];
    }

}
