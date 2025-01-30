# Building for and testing on Android

This is a work in progress - feel free to contribute. Much of this information is not specific to Unciv and publicly available.

## Run configuration

- First, enable the android project in Gradle
    -   In .\settings.gradle.kts, replace `if (System.getenv("ANDROID_HOME") != null)` with `if (true)`.
    -   In .\build.gradle.kts, change `if (System.getenv("ANDROID_HOME") != null)` to `if (true)`.
    -   Run "Sync Project with Gradle Files" to populate the available projects and add the android project.
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

## Building an APK

Android Studio has a menu entry "Build -> Build Bundle(s) / APK(s) -> Build APK(s)."
This will build a ready-to-install APK, and when it is finished, pop a message that offers to show you the file in your local file manager.
***Important*** such locally built APK's are debug-signed and not interchangeable with Unciv downloaded from stores. You cannot update one with the other or switch without uninstalling first - losing all data.

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
