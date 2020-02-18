/*
 * Copyright 2020 Joey Hewitt <joey@joeyhewitt.com>
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

#define RIL_SHLIB
#include <telephony/ril.h>
#include <arpa/inet.h>
#include <netutils/ifc.h>

#define PACKED __attribute__ ((packed))
#define UNUSED __attribute__ ((unused))

struct OemHookRawIfcConfigure {
	const char ifname[16];
	int ipaddr;
	uint32_t prefixLength;
	int gateway;
	int dns[2];
} PACKED;

static const struct RIL_Env *s_rilenv = NULL;

/**
 * rild calls to make requests
 *
 * @param request is one of RIL_REQUEST_*
 * @param data is pointer to data defined for that RIL_REQUEST_*
 *        data is owned by caller, and should not be modified or freed by callee
 * @param t should be used in subsequent call to RIL_onResponse
 * @param datalen the length of data
 *
 */
static void onRequest(int request, void *data, size_t datalen UNUSED, RIL_Token t) {
	if (request == RIL_REQUEST_OEM_HOOK_RAW) {
		struct OemHookRawIfcConfigure *ifccfg = data;
		int result;
		if (ifccfg->ipaddr != 0) {
            result = ifc_configure(ifccfg->ifname, ifccfg->ipaddr, ifccfg->prefixLength, ifccfg->gateway, ifccfg->dns[0], ifccfg->dns[1]);
        } else {
            ifc_remove_default_route(ifccfg->ifname);
            ifc_clear_addresses(ifccfg->ifname);
            ifc_down(ifccfg->ifname);
            // TODO result, error handling
        }
		s_rilenv->OnRequestComplete(t, RIL_E_SUCCESS, &result, sizeof(result));
	}
}

/**
 * rild calls to get the current radio state synchronously
 */
static RIL_RadioState onStateRequest() {
	return RADIO_STATE_UNAVAILABLE;
}

/**
 * rild calls to check for support
 *
 * This function returns "1" if the specified RIL_REQUEST code is
 * supported and 0 if it is not
 *
 * @param requestCode is one of RIL_REQUEST codes
 */
static int getSupports(int requestCode) {
	return requestCode == RIL_REQUEST_OEM_HOOK_RAW;
}

/**
 * rild calls to cancel a request
 *
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
static void onCancel(RIL_Token t UNUSED) {
}

/**
 * rild calls to get version
 *
 * Return a version string for your RIL implementation
 */
static const char* getVersion() {
	return "ril_ofono's helper rild library";
}

static RIL_RadioFunctions radioFunctions = {
	.version = 9,
	.onRequest = onRequest,
	.onStateRequest = onStateRequest,
	.supports = getSupports,
	.onCancel = onCancel,
	.getVersion = getVersion,
};

/* exported */ const RIL_RadioFunctions *RIL_Init(const struct RIL_Env *env, int argc UNUSED, char **argv UNUSED) {
	s_rilenv = env;
	return &radioFunctions;
}
