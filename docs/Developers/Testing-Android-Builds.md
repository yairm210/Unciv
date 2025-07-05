# Building for and testing on Android

This is a work in progress - feel free to contribute. Much of this information is not specific to Unciv and publicly available.

## Run configuration

A successful Gradle sync should automatically create a Run configuration for "android".
If not, you might try creating one yourself (however, if the config is missing due to an incomplete Gradle sync, then you likely won't be able to choose the module):

- In Android Studio, Run > Edit configurations (be sure the Gradle sync is finished successfully first).
    -   Click "+" to add a new configuration
    -   Choose "Android App"
    -   Give the configuration a name, we recommend "Android"
    -   Set module to `Unciv.android.main`
    -   On the Miscellaneous tab, we recommend checking both logcat options
    -   On the Debugger tab, we recommend checking `Automatically attach on Debug.waitForDebugger()`
    -   That's it, the rest can be left as defaults.

## Physical devices

Debugging on physical devices is actually easiest.
With Studio running, you will have adb running, and any newly connected device that generally allows debugging will ask to confirm your desktop's fingerprint (use an USB cable for this tutorial, IP is another matter).
Once adb sees the device and your desktop is authorized from the device, it will be available and preselected on the device select-box to the right of your "android" run configuration and you can start debugging just like the desktop version.
**Note** A debug session does not end after selecting Exit from Unciv's menus - swipe it out of the recents list to end the debug session. Hitting the stop button in Studio is less recommended. That's an Android feature.

### `adb` on Linux

For the above (using USB) to succeed on Linux, you may need to prepare permissions. The exact requirements depend on the distribution - if in doubt, search online resources.
For a Mint 22.1 distro it's as follows, but the steps should be mappable to other distros easily:
- Ensure you're a member of group `plugdev` (bash: `groups`). It's default on Mint, but if not, add yourself (`sudo usermod -a -G plugdev $USER`).
- Ensure a matching udev rule exists. This can depend on the maker of the device you wish to connect. To find the maker ID, while the device is connected: `lsusb`.
  Find your device and jot down the first 4-digit hex code after "ID". In many cases that will be `18d1` - e.g. LineageOS installs will mostly pretend to be 18d1, even if it's different hardware.
- Look for existing rules in `/etc/udev/rules.d/`. A typical name would be `51-android.rules`. Those are text files, and all are processed, so guess from their names and if in doubt check them all.
- Edit or create the rules file. On Mint 22+, using xed under sudo is fine, otherwise you might prefer to use another editor like nano. `sudo xed /etc/udev/rules.d/51-android.rules`
- In the typical case, the file would have one line: `SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", MODE="0666", GROUP="plugdev"`. Yes, replace that `18d1` with your maker ID. If the file and any lines for different maker IDs already exist, add another line.
- Save the file ~and reboot.~ Just joking - we're on Linux, so a `sudo udevadm control --reload-rules && sudo udevadm trigger` will do.
- Try `adb kill-server && adb devices`: Your device should be in the list, and Studio will be able to talk to it.
- You will likely have to re-authorize your computer from the device. Make sure no notification drawer or other system UI is covering and hiding the prompt.
- If you still have problems, make sure no gradle daemons are still running under the old permissions: `./gradlew --status`. If there are, kill them: `./gradlew --stop` (or skip the status right away).

## Building an APK

Android Studio has a menu entry "Build -> Build Bundle(s) / APK(s) -> Build APK(s)."
This will build a ready-to-install APK, and when it is finished, pop a message that offers to show you the file in your local file manager.
***Important*** such locally built APK's are debug-signed and not interchangeable with Unciv downloaded from stores. You cannot update one with the other or switch without uninstalling first - losing all data.
The command line equivalent is: `./gradlew android:assembleDebug`. Then find the apk yourself under `android/build/outputs/apk/debug`.

## Virtual devices (AVD)

(TODO)
- Install Emulator
- Intel HAXM: Deprecated by Intel but still recommended
- Download system image
    - Choice: Match host architecture, w/o Google, older is faster...?
- Configure AVD
- Debug on AVD

## Unciv's log output

Unciv's log system runs on top of the Android SDK one, and filters and tags the messages before passing them to the system.

Like the desktop variant, it has a 'release' mode where all logging from Unciv code is dropped.
A release is detected when the actual APK manifest says debuggable=false - all possibilities discussed here are debug builds in that sense.
Running from Studio it does not matter which button you use - Run or Debug - both deploy a debug build, the difference is only whether it attaches the debugger right away.
An APK built from Studio is also always a debug build.

Therefore, logging is always enabled unless you run a store version.
You can override this by providing an intent extra: In your Run configuration, on the "General" Tab, add in the "Launch Flags" field: `--ez debugLogging false`.
The override can also be controlled without Studio using the activity manager:
```
adb shell am start com.unciv.app/com.unciv.app.AndroidLauncher --ez debugLogging true
```
(or `am start...` directly from a device terminal) will turn on logging for a release (store) build.

The log system's filtering capabilities that work by providing `-D` options to the Java virtual machine cannot be controlled on Android as far as we know.
(TODO - document those in the desktop/Studio wiki article)

## Reading the logcat

(TODO)
- Studio
    - If the logcat window is missing: View - Tool Windows - Logcat
- Studio's filtering
    - When you debug Unciv, a matching filter is pre-applied to the Logcat window, but the tool can actually show the entire system log, including those for other apps.
    - Using `package:com.unciv.app tag:Unciv` as filter is useful to see only the output of Unciv's own logging system.
- logcat apps on the device
    - `com.pluscubed.matloglibre`? Outdated.
- logcat apps need root or specific authorization
    - `adb shell pm grant <logcat app's package id> android.permission.READ_LOGS`
