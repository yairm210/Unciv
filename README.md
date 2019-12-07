# Unciv - Civ V for Android (+Desktop)

![](https://lh3.googleusercontent.com/l8fuQ2DnNjoD9pFnHLsli1xt8OClfr6O9GSBJJ9w7IIb2VHOyxqKZ9lNZXtMqOabCfyI=w1920-h867-rw)


[![Google Play](https://img.shields.io/badge/Google-Play-black.svg)](https://play.google.com/store/apps/details?id=com.unciv.app)
[![F-Droid](https://img.shields.io/f-droid/v/com.unciv.app)](https://f-droid.org/en/packages/com.unciv.app/)

[![Travis CI w/ Logo](https://img.shields.io/travis/yairm210/UnCiv/master.svg?logo=travis)](https://travis-ci.org/yairm210/UnCiv)  
[![Discord Chat](https://img.shields.io/discord/586194543280390151.svg)](https://discord.gg/bjrB4Xw)  

[![LibGDX](https://img.shields.io/badge/libgdx-1.9.10-red.svg)](https://libgdx.badlogicgames.com/)
[![Kotlin](https://img.shields.io/badge/kotlin-1.3.50-orange.svg)](http://kotlinlang.org/)




# What is this?

An open-source Android/Desktop port of Civ V,
Made with LibGDX on Android Studio

## What's the roadmap?

Is this order:

* Polish! As you may have noticed, Unciv is fully functional but rough around the edges. This means:
    * UI+UX improvements (suggestions welcome!)
    * Better automation, AI etc. in-game
* Development and distribution cycle
   * Automated tests - done!
   * Automated F-droid and Google Play version deployment
   * Standalone Desktop executables
   * Maybe Itch.io?
* Missing features from Vanilla - Natural wonders, city-state quests, missing civs etc.
* G&K mechanics - religion, faith etc.
* BNW mechanics - trade routes etc.

# Contributing

## How can I help?

If you're a programmer, you can join us in any of the open issue, or work on improving anything you want - once you're finished, issue a pull request and it'll go into the next version!

If not, you can help by spreading the word - vote for Unciv where you can, mention it on Reddit or Twitter etc, and help us with new ideas of how to get the word out!

If you REALLY want to donate for some reason, I have a Patreon page [here!](https://www.patreon.com/yairm210)

## How can I translate to {language}?

Awesome!

Like most open-source projects, Unciv is developed at Github

The translation files are at https://github.com/yairm210/UnCiv/tree/master/android/assets/jsons/Translations

When you feel that you're ready to add your translation to the game, you'll need to create a merge request, which takes your changes and puts them into the main version of the game - it's pretty straightforward once you do it

Do as much as you're comfortable with - it's a big game with a lot of named objects, so don't feel pressured into doing everything =)

You don't need to download anything, all translation work can be done on the Github website :)

Note that Right-to-Left languages such as Arabic and Hebrew are not supported by the framework :/

## How can I get started working on this?

- Install Android Studio
- Fork the repo, and clone your fork
- Configure an Application run configuration with DesktopLauncher as the Main Class and \<repo_folder\>\android\assets\ as the Working directory

To help you get started, the main classes are (in tree form, so you can see how the game itself saves them):

- GameInfo
   - TileMap
        - TileInfo
            - MapUnit
   - CivilizationInfo
        - CityInfo
        

## [Adding a new Civ](docs/NewCivs.md)

# FAQ

## Will you implement {feature}?

If it's in the original Civ V, then yes!

There's a lot left to implement, so it's hard to give an estimation of when exactly each feature will be added, but we're constantly improving!

If not, then the feature won't be added until we've finished all the features from the original Civ - as mentioned, this will take a while!

## Please add {Civilization}

THE most common request, hands down. Everyone wants their favorite Civ in the game, I get that. But there are so many other things to work on - automations, AI, UI, graphics, bugs, and of course other features from the original game that are currently missing. Eventually we'll have them all, but the key word is "eventually".

## Will you implement Civ VI?

Maybe, once we've finished with all of Civ V. But considering how long it took to get this far, Civ VII may be out by then.

## Is there a desktop version?

There are standalone zip files for each operating system in [Releases](https://github.com/yairm210/UnCiv/releases) which contain everything needed for Unciv to run

If you have Java 8, and are familiar with the command line, there are (considerably smaller) JARs in [Releases](https://github.com/yairm210/UnCiv/releases) which you can run with `java -jar Unciv.jar`

Be aware that the game will generate files inside the for where the Jar is located!

If you also have JDK 8 installed, you can compile Unciv on your own by cloning (or downloading and unzipping) the project, opening a terminal in the Unciv folder and run the following commands:

### Windows

Running: `gradlew desktop:run`
Building: `gradlew desktop:dist`

### Linux/Mac OS

Running: `./gradlew desktop:run`
Building: `./gradlew desktop:dist`

If the terminal returns Permission denied or Command not found on Mac/Linux, run `chmod +x ./gradlew` before running `./gradlew`. *This is a one-time procedure.*

Gradle may take up to several minutes to download files. Be patient.
After building, the output .JAR file should be in /desktop/build/libs/Unciv.jar

For actual development, you'll probably need to download Android Studio and build it yourself - see Contributing :)

## How can I learn to play? Where's the wiki?

All the tutorial information is available in-game at menu > civilopedia > tutorials

All the information is included in the amazing [Civ V wiki](https://civilization.fandom.com/wiki/)

Since this is a Civ V clone, you can search Google for how to play Civ V and there are loads of answers =)

Alternatively, you could [join us on Discord](https://discord.gg/bjrB4Xw) and ask there =D


# [Credits and 3rd parties](docs/Credits.md)
