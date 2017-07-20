# Goal

The goal of this project is to write an Android RIL daemon implemented on top of [oFono](https://01.org/ofono), with a focus on supporting Qualcomm phones.

# Roadmap

1. Alpha implementation in Java
	1. `rild` will load our `.so` file, which gives `rild` thunking functions to Java, and gives Java thunking functions to `rild` (for e.g. unsolicited messages)
	1. For simplicity, oFono will be running on Linux
	1. The oFono instance will be using the `rilmodem` driver. Yes, this first version of the RIL will be built on a RIL! This will hopefully mean fewer "moving parts" and missing functionality to start, allowing us to focus on the basic architecture.
	1. The `rild` sockets for the original RIL will be mapped over something like `adb forward` so it's transparent
1. Port oFono to Android on-device
	1. This old port might help: https://github.com/nitdroid/platform_external_ofono
1. Pivot oFono onto the `qmimodem` driver
	1. May need to write an interface for `qmuxd`, or stop using it at that point (this may kill other hardware)
1. Implement missing features in oFono's `qmimodem` (e.g. voice calls)
	1. At this point we may hit some serious walls in regards to reverse-engineering this stuff. If that's a showstopper, I hope the work can still be useful to someone who wants to run an open Android RIL on a platform oFono supports better.
1. Port to another language? (Rust, go? I'm sticking to Java to lower my learning curve and workload for now.)

# Usage
1. Patch your Java framework (frameworks/opt/telephony/src/java/com/android/internal/telephony/RIL.java) to let you control where it tries to connect to `rild`
	* I made a property for it, so I can change it like so: `adb shell setprop debug.scintill.rilsocket test \; su root killall com.android.phone` (change to empty string to unset)
1. System patches
	* See `patches` file for pseudo-patches to get an idea of what I'm running
	* sepolicy
	* I changed my ril-daemon init service to clear out LD_PRELOAD, but I think it's not actually necessary
1. Build from CM12.1 checkout
	* `mmm ~/Projects/qcril/android_hardware_ril_ofono`
	* Might depend on the rest of android or at least RIL having been built before.
	* Run the `start` script in the root directory.

# TODO
* enable dexopt - see notes in Android.mk
* threading - interaction between JNI, Dalvik, and rild?
	* general review of the JNI/C stuff for security and robustness would be good
* figure out proper solution for libsigchain - see notes in Android.mk
* better storage path of oat files for the VM? - TODO in libril.c where I set ANDROID_DATA env var
* hardcoded RIL version number in libril.c, to match what my proprietary rild expects
* proper SELinux policy. I pushed forward on a full dalvik-cache support to try to get something working, but now I think it was another issue and the dalvik-cache isn't technically needed. maybe best to get some minimal cache access set up so it doesn't bail, but falls back to imageless, than to change security-sensitive things given my low level of understanding SELinux right now

# License

> ril_ofono is free software: you can redistribute it and/or modify
> it under the terms of the GNU General Public License as published by
> the Free Software Foundation, either version 3 of the License, or
> (at your option) any later version.

> ril_ofono is distributed in the hope that it will be useful,
> but WITHOUT ANY WARRANTY; without even the implied warranty of
> MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
> GNU General Public License for more details.

> You should have received a copy of the GNU General Public License
> along with ril_ofono.  If not, see <http://www.gnu.org/licenses/>.
