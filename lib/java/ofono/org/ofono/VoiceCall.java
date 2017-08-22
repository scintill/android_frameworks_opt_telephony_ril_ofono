package org.ofono;

import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;

import java.util.Map;

public interface VoiceCall extends DBusInterface {
    public static class PropertyChanged extends DBusSignal {
        public final String name;
        public final Variant value;

        public PropertyChanged(String path, String name, Variant value) throws DBusException {
            super(path, name, value);
            this.name = name;
            this.value = value;
        }
    }

    public static class DisconnectReason extends DBusSignal {
        public final String path;
        public final String reason;

        public DisconnectReason(String path, String reason) throws DBusException {
            super(path, reason);
            this.path = path;
            this.reason = reason;
        }
    }

    public Map<String, Variant> GetProperties();

    public void Deflect(String number);

    public void Hangup();

    public void Answer();
}
