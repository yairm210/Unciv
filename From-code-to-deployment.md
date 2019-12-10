# From code to deployment

So, your code works! You've solved all the bugs and now you just need to get it out to everyone!

So, how does THAT work????

The process has two major parts, one is "Getting your code in the main repository" and the other is "Deploying versions" - as a developer, you'll be taking an active part in the first process, but the second process is on me =)

## Getting your code in the main repo

* First off, push your changes with Git to your own branch at https://github.com/YourUsername/Unciv.git. I hope you've been doing this during development too, but that's none of my business \*sips tea\*
* Issue a pull request from https://github.com/YourUsername/Unciv - from the Pull Requests is the simplest
* The Travis build will check that your proposed change builds properly and doesn't crash the game in an obvious way
* I'll go over your pull request and will ask questions and request changes - this is not just for the code to be good, it's mostly so you can learn how the repo works for the next change you make =)
* When everything looks good, I'll merge your code in!

## Deploying versions

When I'm ready to release a new version I:
* Change the versionCode and versionName in the android build.gradle so that Google Play and F-droid can recognize that it's a different release
* Upload the new version to Google Play - we start at a 10% rollout, after a day with no major problems go to 30%, and after another day to 100%. If you were counting that means that most players will get the new version after 2+ days.
   * If there were problems, we halt the current rollout, fix the problems, and release a patch version, which starts at 10% again.
* Add a tag to the commit of the version. When Travis sees that we've added a tag, it will run a build, and this time (because of the configuration we put in the travis.yml file), it will:
   * Compile an unsigned APK for Android
   * Pack a .jar file for every operating system with Java
   * Use Linux and Windows JDKs to create standalone zips for 32 and 64 bit systems
   * Upload all of these files to a new release on Github, which will get added to the [Releases](https://github.com/yairm210/Unciv/releases) page
* The F-Droid bot checks periodically if we added a new tag. When it recognizes that we did, it will update the [yaml file here](https://gitlab.com/fdroid/fdroiddata/blob/master/metadata/com.unciv.app.yml) (requires human acceptance)
   * When the bot next runs and sees that there's a version it doesn't have a release for, it will attempt to build the new release. The log of the build will be added [here](https://f-droid.org/wiki/page/com.unciv.app/lastbuild) (redirects to the latest build), and the new release will eventually be available [here](https://f-droid.org/en/packages/com.unciv.app/)