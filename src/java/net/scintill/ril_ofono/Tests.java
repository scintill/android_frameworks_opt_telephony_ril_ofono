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

import android.util.Log;

import org.freedesktop.dbus.Variant;
import org.ofono.MessageManager;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Tests implements Runnable {

    private static final String TAG = "RilOfono Tests";

    SmsModule mSmsModule;

    public Tests(SmsModule ril) {
        mSmsModule = ril;
    }

    @Override
    public void run() {
        try {
            createReceivedMessage("123", "Test123!", new Date(), false);
            createReceivedMessage("456", "Test 🌠", new Date(System.currentTimeMillis()+1000000), false);
            createReceivedMessage("777", "Immediate!", new Date(System.currentTimeMillis()), true);
        } catch (Throwable t) {
            Log.e(TAG, "Error running tests", t);
        }
    }

    private void createReceivedMessage(String sender, String text, Date date, boolean immediate) throws Throwable {
        Map<String, Variant> info = new HashMap<>();
        info.put("Sender", new Variant<>(sender));
        info.put("SentTime", new Variant<>(Utils.formatOfonoDate(date)));
        if (!immediate) {
            MessageManager.IncomingMessage s = new MessageManager.IncomingMessage("/", text, info);
            mSmsModule.handle(s);
        } else {
            MessageManager.ImmediateMessage s = new MessageManager.ImmediateMessage("/", text, info);
            mSmsModule.handle(s);
        }
    }

}
