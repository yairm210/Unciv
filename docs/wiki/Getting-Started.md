This is a guide to editing, building, running and deploying Unciv from code

So first things first - the initial "No assumptions" setup to have Unciv run from-code on your computer!

* Install Android Studio - it's free and awesome! Be aware that it's a long download!
* Install Git, it's the way for us to work together on this project. UI is optional, Android Studio has good Git tools built in :)
* Getting the code
   * Create a Github account, if you don't already have one
   * Fork the repo (click the "Fork" button on the top-right corner of https://github.com/yairm210/Unciv) - this will create a "copy" of the code on your account, at https://github.com/YourUsername/Unciv
   * Clone your fork with git - the location will be https://github.com/YourUsername/Unciv.git, visible from the green "Clone or download" button at https://github.com/YourUsername/Unciv
* Load the project in Android Studio, Gradle will attempt the initial sync. If this is your first time with Android Studio, this may require you to accept the Android Build-tools licenses, which works differently on every device, so search for your OS-specific solution.
  * A new install may not be able to do the initial sync - this comes in the form of `Unable to find method ''void org.apache.commons.compress.archivers.zip.ZipFile.<init>(java.nio.channels.SeekableByteChannel)''` errors when you try to sync. If you have this problem go into File > Settings > Appearance & Behavior > System Settings > Android SDK
    * Click "SDK Tools"
    * Select "Show Package Details" in the bottom right
    * Choose version 30.0.2 under "Android SDK Build-Tools 31"
    * Click "Apply"
* In Android Studio, Run > Edit configurations.
  * Click "+" to add a new configuration
  * Choose "Application"
  * Set the module to `Unciv.desktop`, main class to `com.unciv.app.desktop.DesktopLauncher` and `<repo_folder>\android\assets\` as the Working directory, OK to close the window
    * If you get a `../../docs/uniques.md (No such file or directory)` error that means you forgot to set the working directory!
* Select the Desktop configuration and click the green arrow button to run!
* I also recommend going to Settings > Version Control > Commit and turning off 'Before commit - perform code analysis'

Unciv uses Gradle to specify dependencies and how to run. In the background, the Gradle gnomes will be off fetching the packages (a one-time effort) and, once that's done, will build the project!

Congratulations! Unciv should now be running on your computer! Now we can start changing some code, and later we'll see how your changes make it into the main repository!

Now would be a good time to get to know the project in general at [the Project Structure overview!](./Project-structure-and-major-classes.md)
