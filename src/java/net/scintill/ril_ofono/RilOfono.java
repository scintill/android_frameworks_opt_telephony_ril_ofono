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
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telephony.Rlog;
import com.android.internal.telephony.SerranoRIL;

// SerranoRIL the class CM12 on my Galaxy S4 Mini uses
public class RilOfono extends SerranoRIL {

    private static final String TAG = "RilOfono";

    private static final int BUILD_NUMBER = 5;

    public RilOfono(Context context, int preferredNetworkType, int cdmaSubscription, Integer instanceId) {
        super(context, preferredNetworkType, cdmaSubscription, instanceId);
        Rlog.d(TAG, String.format("RilOfono %d starting", BUILD_NUMBER));
    }

    /**
     * Add emoji to the network operator name, to demonstate this class is working.
     *
     * @param callerResult
     */
    @Override
    public void getOperator(final Message callerResult) {
        super.getOperator(messageWithCallback(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message result) {
                String star = "🌠";
                AsyncResult ar = (AsyncResult) result.obj;
                String[] operatorNames = (String[]) ar.result;
                operatorNames[0] = star + operatorNames[0] + star;
                operatorNames[1] = star + operatorNames[1] + star;
                AsyncResult.forMessage(callerResult, ar.result, ar.exception);
                callerResult.sendToTarget();
                return true;
            }
        }));
    }

    public Message messageWithCallback(Handler.Callback callback) {
        return Message.obtain(new Handler(callback));
    }

}
