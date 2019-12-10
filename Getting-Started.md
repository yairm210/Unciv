This is a guide to editing, building, running and deploying Unciv from code!

So first things first - the initial "No assumptions" setup to have Unciv run from-code on your computer!

* Install Android Studio - it's free and awesome! Be aware that it's a long download!
* Install Git, it's the way for us to work together on this project. UI is optional, I personally recommend Sourcetree
* Getting the code
   * Create a Github account, if you don't already have one
   * Fork the repo (click the "Fork" button on the top-right corner of https://github.com/yairm210/Unciv) - this will create a "copy" of the code on your account, at https://github.com/YourUsername/Unciv
   * Clone your fork with git - the location will be https://github.com/YourUsername/Unciv.git, visible from the green "Clone or download" button at https://github.com/YourUsername/Unciv
* Load the project in Android Studio
* In Android Studio, Run > Edit configurations.
  * Click "+" to add a new configuration
  * Choose "Application"
  * Set the main class to `com.unciv.app.desktop.DesktopLauncher` and <repo_folder>\android\assets\ as the Working directory, Use classpath of module desktop, OK to close the window
* Select the Desktop configuration and click the green arrow button to run!

Unciv uses Gradle to specify dependencies and how to run. In the background, the Gradle gnomes will be off fetching the packages (a one-time effort) and, once that's done, will the the project!

Congratulations! Unciv should now be running on your computer! Now we can start changing some code, and later we'll see how your changes make it into the main repository!