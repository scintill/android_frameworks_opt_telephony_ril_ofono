# Goal

The goal of this project is to write an Android RIL daemon implemented on top of [oFono](https://01.org/ofono), with a focus on supporting Qualcomm phones.

# Roadmap

1. Alpha implementation in Java
	1. For simplicity, it will run on Linux but keep Android compatibility in mind (targeting a Dalvik command-line app)
	1. For simplicity, oFono will also be running on Linux
	1. The oFono instance will be using the `rilmodem` driver. Yes, this first version of the RIL will be built on a RIL! This will hopefully mean fewer "moving parts" and missing functionality to start, allowing us to focus on the basic architecture.
	1. The `rild` sockets for the original RIL and this one will be mapped over something like `adb forward` so it's transparent
1. Port the new `rild` to run on Android on-device
	1. Write `Android.mk` file, init script to run it, make any tweaks for the runtime subset that Android has
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
1. Build from CM12.1 checkout
	* `mmm ~/Projects/qcril/android_hardware_ril_ofono`
	* Might depend on the rest of android or at least RIL having been built before.

# TODO
* enable dexopt - see notes in Android.mk

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