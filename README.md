# Unciv - Civ V for Android (+Desktop)

![](/extraImages/Feature%20graphic.png)


[![Google Play](https://img.shields.io/badge/Google-Play-black.svg)](https://play.google.com/store/apps/details?id=com.unciv.app)
[![F-Droid](https://img.shields.io/f-droid/v/com.unciv.app)](https://f-droid.org/en/packages/com.unciv.app/)

[![Travis CI w/ Logo](https://img.shields.io/travis/yairm210/UnCiv/master.svg?logo=travis)](https://travis-ci.org/yairm210/UnCiv)  
[![Discord Chat](https://img.shields.io/discord/586194543280390151.svg)](https://discord.gg/bjrB4Xw)  

[![LibGDX](https://img.shields.io/badge/libgdx-1.9.10-red.svg)](https://libgdx.badlogicgames.com/)
[![Kotlin](https://img.shields.io/badge/kotlin-1.3.710-orange.svg)](http://kotlinlang.org/)


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
   * Automated F-droid and Google Play version deployment - done!
   * Standalone Desktop executables - done!
   * Maybe Itch.io?
* Missing features from Vanilla - Natural wonders (done!), city-state quests, missing civs etc.
* G&K mechanics - religion, faith etc.
* BNW mechanics - trade routes etc.

# Contributing

## How can I help?

Programmers start [here!](https://github.com/yairm210/Unciv/wiki/Getting-Started)

Translators start [here!](https://github.com/yairm210/Unciv/wiki/Translating)

Modders start [here!](https://github.com/yairm210/Unciv/wiki/Mods)

You can join us in any of the open issue, or work on improving anything you want - once you're finished, issue a pull request and it'll go into the next version!

If not, you can help by spreading the word - vote for Unciv where you can, mention it on Reddit or Twitter etc, and help us with new ideas of how to get the word out!


# FAQ

## Will you implement {feature}?

If it's in the original Civ V, then yes!

There's a lot left to implement, so it's hard to give an estimation of when exactly each feature will be added, but we're constantly improving!

If not, then the feature won't be added until we've finished all the features from the original Civ - as mentioned, this will take a while!

## Please add {Civilization}

THE most common request, hands down. Everyone wants their favorite Civ in the game, I get that. But there are so many other things to work on - automations, AI, UI, graphics, bugs, and of course other features from the original game that are currently missing. Eventually we'll have them all, but the key word is "eventually".

In the meantime, you can [mod it yourself!](https://github.com/yairm210/Unciv/wiki/Mods)

## Will you implement Civ VI?

Maybe, once we've finished with all of Civ V. But considering how long it took to get this far, Civ VII may be out by then.

## Is there a desktop version?

There are standalone zip files for each operating system in [Releases](https://github.com/yairm210/UnCiv/releases) which contain everything needed for Unciv to run

If you have Java 8, and are familiar with the command line, there are (considerably smaller) JARs in [Releases](https://github.com/yairm210/UnCiv/releases) which you can run with `java -jar Unciv.jar`

Be aware that the game will generate files inside the folder where the Jar is located!

If you also have JDK 8 installed, you can compile Unciv on your own by cloning (or downloading and unzipping) the project, opening a terminal in the Unciv folder and run the following commands:

### Windows

Running: `gradlew desktop:run`

Building: `gradlew desktop:dist`

### Linux/Mac OS

Running: `./gradlew desktop:run`

Building: `./gradlew desktop:dist`

If the terminal returns `Permission denied` or `Command not found` on Mac/Linux, run `chmod +x ./gradlew` first. *This is a one-time procedure.*

Gradle may take up to several minutes to download files. Be patient.
After building, the output .JAR file should be in /desktop/build/libs/Unciv.jar

For actual development, you'll probably need to download Android Studio and build it yourself - see Contributing :)

## How can I learn to play? Where's the wiki?

All the tutorial information is available in-game at menu > civilopedia > tutorials

All the information is included in the amazing [Civ V wiki](https://civilization.fandom.com/wiki/)

Since this is a Civ V clone, you can search Google for how to play Civ V and there are loads of answers =)

Alternatively, you could [join us on Discord](https://discord.gg/bjrB4Xw) and ask there =D

## Aren't you basically making a Civ V clone? Is that even legal?

This is a subject that I've heard a lot of hearsay on but no solid sources of law.

From what I gather, it is illegal:
 - To use the Civilization name
 - To impersonate the Civ games (so calling yourself civi|zation with a similar logo, for instance)
 - To use any assets from the original game (images, sound etc) - they belong to Firaxis

From what I understand, intellectual property rights apply to names, characters and settings. They do not apply to mechanics - as I'm sure you know, there are a billion Flappy Bird knockoffs

If anyone has any real legal sources, or can shed some light on the limits of what is and is not allowed, I'd be happy to hear!

# [Credits and 3rd parties](docs/Credits.md)
