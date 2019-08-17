# Goal

The goal of this project is to write an Android RIL on top of [oFono](https://01.org/ofono) (my fork [here](https://github.com/scintill/android_external_ofono)). For my convenience and code-safety, the RIL is written in Java as an implementation of the AOSP telephony framework's `CommandsInterface`, omitting the traditional `rild` and shared library parts.

# Roadmap

- [x] Alpha implementation
	- [x] Write a RIL class that the com.android.phone app (via the telephony framework) will load. This class will interface with oFono.
	- [x] The oFono instance will be using the `rilmodem` driver. Yes, this first version of the RIL will be built on a RIL! This will hopefully mean fewer "moving parts" and missing functionality to start, allowing us to focus on the basic architecture.
- [x] Pivot oFono onto the `qmimodem` driver
	- [x] Interface for `qmuxd` ([qmiserial2qmuxd](https://github.com/scintill/qmiserial2qmuxd/))
	- [x] Implement more features (some work will probably be needed in the qmimodem driver), refine things like error-handling, test
	- [x] Get rid of `qmuxd` and any other proprietary stuff we are relying on
- [ ] More testing and refinements
	- As of October 2017, I had some successful tests with a fully open-source stack (**no proprietary code on the apps processor!**) on Replicant 6 and GT-I9195, but there is some flakiness compared to when I was running on top of CyanogenMod 12 with proprietary blobs (I think `qmuxd` and `rmt_storage` would be the only relevant blobs, but maybe there are other bits that helped initialize things into a stable state.)

# Usage
1. Build from Replicant 6 checkout with [this manifest](https://github.com/scintill/android/tree/replicant-6.0). (My device is `serranoltexx`, but this manifest will probably work with any Serrano device, or even any Samsung MSM8930-based device if you add its device tree from LineageOS.)
1. Running
	* In theory, all you need to do is flash the build and boot, but in reality it currently takes some fiddling to get the modem booted.
		* Signs of success: rmtfsd logging in the main log about file requests; "apr_tal:Modem Is Up" in dmesg, ofonod logging in the radio log about QMI calls; kernel log does *not* have messages about the modem continually resetting.
		* Try writing "20" to /sys/devices/virtual/smdpkt/smdcntl0/open_timeout . Once you get it going, write the normal value "0".
		* You might need to `restart ofonod` as root, after the modem and rmtfsd appear to be talking.
		* You might try writing "1" to /sys/kernel/debug/modem_debug/reset_modem to reboot the modem if things seem stuck in a non-working state.
1. Debugging
    * You can forward the dbus to your PC: Add `<auth>ANONYMOUS</auth><allow_anonymous />` to /system/etc/dbus.conf, and `adb forward localfilesystem:/tmp/dbus-android localreserved:dbus`. Then use the dbus address `unix:path=/tmp/dbus-android` for dbus-send, d-feet, etc.
    * Look for log tags `RilOfono`, `ofonod` (in the radio log) and `rmtfsd` (in the main log)

# Features

Nothing is thoroughly tested nor tried on a broad selection of hardware/networks yet, but the following have been implemented and tested:

* Basic SMS sending and receiving (multipart messages and some international characters tested)
* MMS sending and receiving
* Basic voicecalls: dial, receive, answer, decline, hang up, send DTMF tones in call
* User-intiated USSD (special dial codes, such as `#999#` which displays balance on my carrier)
* Basic data connections
	* When testing, you might need to turn off wifi to ensure the data connection gets used.
* Reporting to Android: phone #, ICCID, voicemail #, voicemail messages alert

# Resources and Credit
* Thanks to Bjorn Andersson @andersson and Linaro for [`rmtfs`](https://github.com/andersson/rmtfs) and their Qualcomm SoC mainlining work.
* Thanks to Alexander Couzens @lynxis and Sysmocom for [oFono patches](https://git.sysmocom.de/ofono/) for voicecalls etc. on QMI.
* https://github.com/nitdroid/ofono-ril for some help on mapping ofono properties to Android RIL

# Bugs and TODO
* make dbus exceptions be checked exceptions, so the compiler will find them and I have to handle them
* better fix for org.ofono.Message proguard kept class? (see 74caeb2ac309cfe8b0690cf9db4949cf3523b427)

# Code review

These should be reviewed more closely for correctness and safety:

* My changes to oFono (logging, SMS PDU patches)
* selinux policy

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
