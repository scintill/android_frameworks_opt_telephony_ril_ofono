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
import android.telephony.Rlog;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.UInt16;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.scintill.ril_ofono.RilOfono.privStr;

/*package*/ class PropManager {

    private static final String TAG = RilOfono.TAG;

    private static void logAndUpdateProp(Map<String, Variant> propsToUpdate, String thingChangingDebugRef, String name, Variant value) {
        Rlog.v(TAG, thingChangingDebugRef + " propchange: " + name + "=" + privStr(value));
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (propsToUpdate) {
            propsToUpdate.put(name, value);
        }
    }

    protected static void handle2dPropChange(Map<String, Map<String, Variant>> propsToUpdateRoot, String keyToUpdate, Class<?extends DBusInterface> dbusObIface, String name, Variant value) {
        Map<String, Variant> propsToUpdate = propsToUpdateRoot.get(keyToUpdate);
        if (propsToUpdate == null) {
            propsToUpdateRoot.put(keyToUpdate, propsToUpdate = new HashMap<>());
        }
        logAndUpdateProp(propsToUpdate, dbusObIface.getSimpleName()+" "+keyToUpdate, name, value);
    }

    protected static void putOrMerge2dProps(Map<String, Map<String, Variant>> rootProps, String key, Map<String, Variant> props) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (rootProps) {
            if (!rootProps.containsKey(key)) {
                rootProps.put(key, props);
            } else {
                // retain call origination properties
                rootProps.get(key).putAll(props);
            }
        }
    }

    private void initProps(Map<String, Variant> propsToInit, Class<?extends DBusInterface> sourceObIface, DBusInterface sourceOb) {
        // load properties
        Map<String, Variant> props;
        try {
            Method m = sourceObIface.getMethod("GetProperties");
            //noinspection unchecked
            props = (Map<String, Variant>) m.invoke(sourceOb);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("unable to find GetProperties method", e);
        } catch (InvocationTargetException e) {
            try {
                if (e.getCause() instanceof DBusExecutionException) {
                    throw (DBusExecutionException) e.getCause();
                } else {
                    throw new RuntimeException("error calling GetProperties() on " + sourceObIface.getSimpleName(), e.getCause());
                }
            } catch (DBus.Error.UnknownMethod unknownMethod) {
                Rlog.w(TAG, "unable to GetProperties() on " + sourceObIface.getSimpleName());
                // probably just isn't loaded yet, so give empty props
                props = new HashMap<>();
            }
        }

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (propsToInit) {
            propsToInit.clear(); // TODO notify about removed props?
            try {
                Method m = this.getClass().getDeclaredMethod("onPropChange", sourceObIface, String.class, Variant.class);
                for (Map.Entry<String, Variant> entry : props.entrySet()) {
                    logAndUpdateProp(propsToInit, sourceObIface.getSimpleName()+"#init", entry.getKey(), entry.getValue());
                    m.invoke(this, sourceOb, entry.getKey(), entry.getValue());
                }
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("unable to find onPropChange method", e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    }

    /*
     * The consuming class must have a method onPropChange(<sourceObIface> sourceOb, String name, Variant value)
     */
    protected <PropChangeSignalT extends DBusSignal> void mirrorProps(final Class<? extends DBusInterface> sourceObIface, final DBusInterface sourceOb, final Class<PropChangeSignalT> propChangeSignalClass, final Map<String, Variant> props) {
        try {
            final Field fName = propChangeSignalClass.getField("name");
            final Field fValue = propChangeSignalClass.getField("value");
            final Method mtOnPropChange = this.getClass().getDeclaredMethod("onPropChange", sourceObIface, String.class, Variant.class);

            RilOfono.sInstance.registerDbusSignal(propChangeSignalClass, new DBusSigHandler<PropChangeSignalT>() {
                @Override
                public void handle(DBusSignal s) {
                    try {
                        String name = (String)fName.get(s);
                        Variant value = (Variant)fValue.get(s);
                        logAndUpdateProp(props, sourceObIface.getSimpleName(), name, value);
                        mtOnPropChange.invoke(PropManager.this, sourceOb, name, value);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("unable to handle propchange signal", e);
                    } catch (InvocationTargetException e) {
                        Rlog.e(TAG, "error handling propchange signal", e.getCause());
                        // do not re-throw
                    }
                }
            });
        } catch (NoSuchFieldException | NoSuchMethodException e) {
            throw new RuntimeException("unable to register propchange signal", e);
        }

        initProps(props, sourceObIface, sourceOb);
    }

    /*package*/ static <T> T getProp(Map<String, Variant> props, String key, T defaultValue) {
        //noinspection unchecked
        return props.get(key) != null ? (T) props.get(key).getValue() : defaultValue;
    }

    /*package*/ static <T> T[] getProp(Map<String, Variant> props, String key, @NonNull T[] defaultValue) {
        if (props.get(key) != null) {
            //noinspection unchecked
            List<T> list = (List<T>)(props.get(key).getValue());
            return list.toArray(defaultValue);
        } else {
            return defaultValue;
        }
    }

    /*package*/ static Integer getProp(Map<String, Variant> props, String key, Integer defaultValue) {
        if (props.get(key) == null) return defaultValue;
        Object value = props.get(key).getValue();
        if (value instanceof UInt16) return ((UInt16) value).intValue();
        return (Integer) value;
    }

    /*package*/ static Long getProp(Map<String, Variant> props, String key, Long defaultValue) {
        if (props.get(key) == null) return defaultValue;
        Object value = props.get(key).getValue();
        if (value instanceof UInt16) return ((UInt16) value).longValue();
        if (value instanceof UInt32) return ((UInt32) value).longValue();
        return (Long) value;
    }

    /*package*/ static String getProp(Map<String, Variant> props, String key, String defaultValue) {
        return props.get(key) != null ? props.get(key).getValue().toString() : defaultValue;
    }

    /*package*/ static <T extends Enum> T getProp(Map<String, Variant> props, String key, T defaultValue) {
        return (T) Enum.valueOf(defaultValue.getClass(), getProp(props, key, defaultValue.toString()));
    }


}
