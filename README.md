# Goal

The goal of this project is to write an Android RIL daemon implemented on top of [oFono](https://01.org/ofono), with a focus on supporting Qualcomm phones.

# Roadmap

1. Alpha implementation in Java
	1. Write a RIL class that the com.android.phone app (via the telephony framework) will load. This class will interface with oFono.
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
1. System patches
	* See `patches` file for pseudo-patches to get an idea of what I'm running
		* Patch telephony framework to be able to load the RIL class from another package
	* system/core/rootdir/init.rc - add dbus and ofono services
	* install dbus conf file in /system/etc/dbus.conf
	* sepolicy updates for dbus and radio interop
1. Build from CM12.1 checkout
	* `mmm ~/Projects/qcril/android_hardware_ril_ofono`
	* Might depend on the rest of android or at least RIL having been built before.
	* Run the `start` script in the root directory.

# Resources and Credit
* https://github.com/nitdroid/ofono-ril for some help on mapping ofono properties to Android RIL

# TODO
* dexopt/proguard? - see notes in Android.mk
* make sure socket operations on the main thread trigger strict mode exceptions (are Unix sockets a loophole?)
* make dbus exceptions be checked exceptions, so the compiler will find them and I have to handle them

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
