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
import android.os.Message;

import com.android.internal.telephony.BaseCommands;
import com.android.internal.telephony.RadioCapability;

/*package*/ abstract class SaneBaseCommands extends BaseCommands {

    public SaneBaseCommands(Context context) {
        super(context);
    }

    // re-abstract methods that were "implemented" with an empty body that never returns to callers

    @Override
    public abstract void sendSMSExpectMore(String smscPDU, String pdu, Message result);

    @Override
    public abstract void testingEmergencyCall();

    @Override
    public abstract void setUiccSubscription(int appIndex, boolean activate, Message response);

    @Override
    public abstract void setDataAllowed(boolean allowed, Message response);

    @Override
    public abstract void requestShutdown(Message result);

    @Override
    public abstract void getRadioCapability(Message result);

    @Override
    public abstract void setRadioCapability(RadioCapability rc, Message response);

    @Override
    public abstract void startLceService(int reportIntervalMs, boolean pullMode, Message result);

    @Override
    public abstract void stopLceService(Message result);

    @Override
    public abstract void pullLceData(Message result);

    @Override
    public abstract void setLocalCallHold(boolean lchStatus);

    @Override
    public abstract void iccOpenLogicalChannel(String AID, Message response);

    @Override
    public abstract void iccOpenLogicalChannel(String AID, byte p2, Message response);

    @Override
    public abstract void iccCloseLogicalChannel(int channel, Message response);

    @Override
    public abstract void iccTransmitApduLogicalChannel(int channel, int cla, int instruction,
                                              int p1, int p2, int p3, String data,
                                              Message response);

    @Override
    public abstract void iccTransmitApduBasicChannel(int cla, int instruction, int p1, int p2,
                                            int p3, String data, Message response);

    @Override
    public abstract void getAtr(Message response);

}
