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

/**
 * This is the interface we publish to rild.
 */
public interface RadioFunctions {

	/**
	 * @param env the interface rild gave us to interact with it
	 * @param args arguments from rild
	 */
	public void init(Rild env, String[] args);

	/**
	 * RIL_Request Function
	 *
	 * @param request is one of RIL_REQUEST_*
	 * @param data is pointer to data defined for that RIL_REQUEST_*
	 *        data is owned by caller, and should not be modified or freed by callee
	 * @param t should be used in subsequent call to RIL_onResponse
	 *
	 */
	public void onRequest(int request, byte[] data, RilToken t);

	/**
	 * This function should return the current radio state synchronously
	 */
	public int getRadioState();

	/**
	 * This function returns "1" if the specified RIL_REQUEST code is
	 * supported and 0 if it is not
	 *
	 * @param requestCode is one of RIL_REQUEST codes
	 */
	public boolean supports(int requestCode);

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
	public void cancel(RilToken t);

	/**
	 * Return a version string for your RIL implementation
	 */
	public String getVersion();

}
