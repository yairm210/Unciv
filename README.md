# [UnCiv - Civ V for Android](https://play.google.com/store/apps/details?id=com.unciv.app)

![](https://lh3.googleusercontent.com/UKRJog9ZI6w93hYLf_VXIKP5gRU9jP8IW3Ka9FhbFasdMjiFTA-ktmGzCMD-HFMsZw=w1920-h867-rw)

[![Travis CI w/ Logo](https://img.shields.io/travis/yairm210/UnCiv/master.svg?logo=travis)](https://travis-ci.org/yairm210/UnCiv)  
[![LibGDX](https://img.shields.io/badge/libgdx-1.9.10-red.svg)](https://libgdx.badlogicgames.com/)
[![Kotlin](https://img.shields.io/badge/kotlin-1.3.50-orange.svg)](http://kotlinlang.org/)

[![Discord Chat](https://img.shields.io/discord/586194543280390151.svg)](https://discord.gg/bjrB4Xw)  


# What is this?

An Android port of Civ V
Made with LibGDX on Android Studio

## What's been implemented?

* Map tiles (including water), resources and improvements
* Units and movement
  * Air units
* Cities, production and buildings
  * Population and Specialists
* Science, Cultural and Domination victories
* Policies and Golden Ages
* Combat and barbarians
   * Promotions and combat modifiers
* Other civilizations, diplomacy and trade
* City-states
* Multiplayer (hotseat and across internet)
* Map editor

## What's the roadmap?

Is this order:

* Polish! As you may have noticed, Unciv is fully functional but rough around the edges. This means:
    * UI+UX improvements (suggestions welcome!)
    * Filling out gaps (missing civs, wonders, etc)
    * Better automation, AI etc.
    
* Missing features from Vanilla - Natural wonders, city-state quests, rivers etc.
* G&K mechanics - religion, faith etc.
* BNW mechanics - trade routes etc.


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

Unciv is developed with LibGDX, which supports desktop.

However, it's difficult enough for me to handle one release cycle (on Google Play), so I'm not planning on creating a second release cycle for desktop.

If you really want to, you could install Android Studio and build it yourself :)

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

## [Adding a new Civ](docs/NewCivs.md)

# [Credits and 3rd parties](docs/Credits.md)
