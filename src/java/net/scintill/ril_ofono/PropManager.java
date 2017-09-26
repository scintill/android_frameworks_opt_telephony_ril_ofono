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

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import libcore.util.Objects;

import static net.scintill.ril_ofono.RilOfono.privExc;
import static net.scintill.ril_ofono.RilOfono.privStr;

/*package*/ class PropManager {

    private static final String TAG = RilOfono.TAG;

    private static boolean logAndUpdateProp(Map<String, Variant<?>> propsToUpdate, String thingChangingDebugRef, String name, Variant<?> value, boolean log) {
        boolean changed;
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (propsToUpdate) {
            changed = !Objects.equal(value, propsToUpdate.put(name, value));
        }
        if (changed && log) {
            Rlog.i(TAG, thingChangingDebugRef + " propchange: " + name + "=" + privStr(value));
        }
        return changed;
    }

    protected static boolean handle2dPropChange(Map<String, Map<String, Variant<?>>> propsToUpdateRoot, String keyToUpdate, Class<?extends DBusInterface> dbusObIface, String name, Variant<?> value) {
        Map<String, Variant<?>> propsToUpdate = propsToUpdateRoot.get(keyToUpdate);
        if (propsToUpdate == null) {
            propsToUpdateRoot.put(keyToUpdate, propsToUpdate = new HashMap<>());
        }
        return logAndUpdateProp(propsToUpdate, dbusObIface.getSimpleName()+" "+keyToUpdate, name, value, true);
    }

    protected static void putOrMerge2dProps(Map<String, Map<String, Variant<?>>> rootProps, String key, Map<String, Variant<?>> props) {
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

    protected void initProps(Map<String, Variant<?>> propsToInit, Class<?extends DBusInterface> sourceObIface, DBusInterface sourceOb) throws DBusExecutionException {
        // load properties
        Map<String, Variant<?>> props;
        try {
            Method m = sourceObIface.getMethod("GetProperties");
            @SuppressWarnings("unchecked")
            Map<String, Variant<?>> o = (Map<String, Variant<?>>) m.invoke(sourceOb);
            props = o;
        } catch (NoSuchMethodException | IllegalAccessException | ClassCastException e) {
            throw new RuntimeException("unable to find GetProperties method", e);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof DBusExecutionException) {
                throw (DBusExecutionException) e.getCause();
            }
            throw new RuntimeException("error calling GetProperties() on " + sourceObIface.getSimpleName(), e.getCause());
        }

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (propsToInit) {
            try {
                Method mtOnPropChange = this.getClass().getDeclaredMethod("onPropChange", sourceObIface, String.class, Variant.class);
                for (Map.Entry<String, Variant<?>> entry : props.entrySet()) {
                    if (logAndUpdateProp(propsToInit, sourceObIface.getSimpleName()+"#init", entry.getKey(), entry.getValue(), true)) {
                        mtOnPropChange.invoke(this, sourceOb, entry.getKey(), entry.getValue());
                    }
                }
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException("unable to find onPropChange method", e);
            } catch (InvocationTargetException e) {
                Rlog.e(TAG, "exception in onPropChange()", e.getCause());
                // do not re-throw
            }
        }
    }

    /*
     * The class must have a method onPropChange(<sourceObIface> sourceOb, String name, Variant value)
     */
    /*package*/ <PropChangeSignalT extends DBusSignal> void handle(PropChangeSignalT s, DBusInterface sourceOb, Class<PropChangeSignalT> propChangeSignalClass, Map<String, Variant<?>> props, Class<? extends DBusInterface> sourceObIface) {
        handle(s, sourceOb, propChangeSignalClass, props, sourceObIface, true);
    }

    /*package*/ <PropChangeSignalT extends DBusSignal> void handle(PropChangeSignalT s, DBusInterface sourceOb, Class<PropChangeSignalT> propChangeSignalClass, Map<String, Variant<?>> props, Class<? extends DBusInterface> sourceObIface, boolean log) {
        try {
            String name = (String) propChangeSignalClass.getField("name").get(s);
            Variant<?> value = (Variant<?>) propChangeSignalClass.getField("value").get(s);
            if (logAndUpdateProp(props, sourceObIface.getSimpleName(), name, value, log)) {
                Method mtOnPropChange = this.getClass().getDeclaredMethod("onPropChange", sourceObIface, String.class, Variant.class);
                mtOnPropChange.invoke(PropManager.this, sourceOb, name, value);
            }
        } catch (NoSuchFieldException | NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException("unable to handle propchange signal", e);
        } catch (InvocationTargetException e) {
            Rlog.e(TAG, "exception in onPropChange()", privExc(e.getCause()));
            // do not re-throw
        }
    }

    @SuppressWarnings("unchecked")
    /*package*/ static <T> T getProp(Map<String, Variant<?>> props, String key, T defaultValue) {
        return props.get(key) != null ? (T) props.get(key).getValue() : defaultValue;
    }

    /*package*/ static <T> T[] getProp(Map<String, Variant<?>> props, String key, @NonNull T[] defaultValue) {
        if (props.get(key) != null) {
            @SuppressWarnings("unchecked")
            List<T> list = (List<T>) props.get(key).getValue();
            return list.toArray(defaultValue);
        } else {
            return defaultValue;
        }
    }

    /*package*/ static String getProp(Map<String, Variant<?>> props, String key, String defaultValue) {
        return props.get(key) != null ? props.get(key).getValue().toString() : defaultValue;
    }

    /*package*/ static <T extends Enum<T>> T getProp(Map<String, Variant<?>> props, String key, @NonNull T defaultValue) {
        return Enum.valueOf(defaultValue.getDeclaringClass(), getProp(props, key, defaultValue.toString()));
    }

    /** @throws IllegalArgumentException if value not set, or unmapped enum value */
    /*package*/ static <T extends Enum<T>> T getProp(Map<String, Variant<?>> props, String key, Class<T> valueClass) {
        return Enum.valueOf(valueClass, getProp(props, key, "")); // empty string should cause IllegalArgument from Enum.valueOf
    }

}
