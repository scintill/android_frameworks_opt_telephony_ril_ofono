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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This is the interface we publish to rild.
 */
public class Ril implements RadioFunctions {

	private static final String TAG = "ril_ofonoJ";

	/**
	 * @param env the interface rild gave us to interact with it
	 * @param args arguments from rild
	 */
	@Override
	public void init(Rild env, String[] args) {
		Log.d("ril_ofono", "hello from Java land");
	}

	/**
	 * RIL_Request Function
	 *
	 * @param request is one of RIL_REQUEST_*
	 * @param data is pointer to data defined for that RIL_REQUEST_*
	 *        data is owned by caller, and should not be modified or freed by callee
	 * @param t should be used in subsequent call to RIL_onResponse
	 *
	 */
	@Override
	public void onRequest(int request, byte[] data, RilToken t) {
		throw new RuntimeException("not implemented");
	}

	/**
	 * This function should return the current radio state synchronously
	 */
	@Override
	public int getRadioState() {
		throw new RuntimeException("not implemented");
	}

	/**
	 * This function returns "1" if the specified RIL_REQUEST code is
	 * supported and 0 if it is not
	 *
	 * @param requestCode is one of RIL_REQUEST codes
	 */
	@Override
	public boolean supports(int requestCode) {
		throw new RuntimeException("not implemented");
	}

	/**
	 * This function is called from a separate thread--not the
	 * thread that calls RIL_RequestFunc--and indicates that a pending
	 * request should be cancelled.
	 *
	 * On cancel, the callee should do its best to abandon the request and
	 * call RIL_onRequestComplete with RIL_Errno CANCELLED at some later point.
	 *
	 * Subsequent calls to  RIL_onRequestComplete for this request with
	 * other results will be tolerated but ignored. (That is, it is valid
	 * to ignore the cancellation request)
	 *
	 * RIL_Cancel calls should return immediately, and not wait for cancellation
	 *
	 * Please see ITU v.250 5.6.1 for how one might implement this on a TS 27.007
	 * interface
	 *
	 * @param t token wants to be canceled
	 */
	@Override
	public void cancel(RilToken t) {
		throw new RuntimeException("not implemented");
	}

	private static final String DUMMY_INVOCATION_VALUE = "_dummy_invocation_";

    // Early initialization
    static {
		// Get some core lib native methods registered. Pretty fragile, but seems better
		// than alternatives for now.
		// https://stackoverflow.com/questions/13000561/cli-on-dalvikvm-fails-on-jni-lib
		// https://android-review.googlesource.com/#/c/157981/
		// https://github.com/pfalcon/micropython-projs/blob/master/android/pm.py
        try {
			// The WithFramework class is meant to be invoked to preload before invoking another
			// class's main.
            Class<?> c = Class.forName("com.android.internal.util.WithFramework");
            Method m = c.getMethod("main", String[].class);
            m.invoke(null, (Object) new String[]{Ril.class.getName(), DUMMY_INVOCATION_VALUE});
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("bootstrap failed", e);
        }
	}

	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals(DUMMY_INVOCATION_VALUE)) {
			return;
		}

		// in case we run from command-line to test the build:
		Log.d(TAG, "hello from Java main()");
	}

}
