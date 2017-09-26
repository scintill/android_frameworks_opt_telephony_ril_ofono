package org.ofono.Error;

import org.freedesktop.dbus.exceptions.DBusExecutionException;

public class NotAttached extends DBusExecutionException {
    public NotAttached(String message) {
        super(message);
    }
}