# Unciv - FOSS Civ V for Android+Desktop

![](/extraImages/GithubPreviewImage.png)

[![Google Play](https://img.shields.io/static/v1?label=Google&message=Play&color=607D8B&logo=google-play)](https://play.google.com/store/apps/details?id=com.unciv.app)
[![F-Droid](https://img.shields.io/f-droid/v/com.unciv.app)](https://f-droid.org/en/packages/com.unciv.app/)
[![itch.io](https://img.shields.io/static/v1?label=itch.io&message=Unciv&color=607D8B&logo=itch.io)](https://yairm210.itch.io/unciv)

[![Travis CI w/ Logo](https://img.shields.io/travis/yairm210/Unciv/master.svg?logo=travis)](https://travis-ci.com/yairm210/Unciv)
![Build and deploy](https://github.com/yairm210/Unciv/workflows/Build%20and%20deploy/badge.svg)
[![Discord Chat](https://img.shields.io/discord/586194543280390151.svg)](https://discord.gg/bjrB4Xw)



# What is this?

An open-source, mod-friendly Android+Desktop remake of Civ V, made with LibGDX

## Is this any good?

Depends what you're looking for. If you're in the market for high-res graphics, amazing soundtracks, animations etc, I highly recommend Firaxis's Civ-V-like game, "Civilization V".

If you want a small, fast, moddable, FOSS, in-depth 4X that can still run on a potato, you've come to the right place :)

## What's the roadmap?

Is this order:

* Polish! As you may have noticed, Unciv is fully functional but rough around the edges. This means:
    * UI+UX improvements (suggestions welcome!)
    * Better automation, AI etc. in-game
* Development and distribution cycle - Done! (tests, f-droid/Google Play/itch.io deployment)
* Missing features from Vanilla - Natural wonders , city-state quests, missing civs etc. - mostly done!
* G&K mechanics - religion, faith etc.
* BNW mechanics - trade routes etc.

# Contributing

## How can I help?

Programmers start [here](https://github.com/yairm210/Unciv/wiki/Getting-Started)!

Translators start [here](https://github.com/yairm210/Unciv/wiki/Translating)!

Modders start [here](https://github.com/yairm210/Unciv/wiki/Mods)!

You can join us in any of the open issue, or work on improving anything you want - once you're finished, issue a pull request and it'll go into the next version!

If not, you can help by spreading the word - vote for Unciv where you can, mention it on Reddit or Twitter etc, and help us with new ideas of how to get the word out!


# FAQ

## Will you implement {feature}?

If it's in the original Civ V, then yes!

There's a lot left to implement, so it's hard to give an estimation of when exactly each feature will be added, but we're constantly improving!

If not, then the feature won't be added until we've finished all the features from the original Civ - as mentioned, this will take a while!

## Please add {Civilization}

THE most common request, hands down. Everyone wants their favorite Civ in the game, I get that. But there are so many other things to work on - automations, AI, UI, graphics, bugs, and of course other features from the original game that are currently missing. Eventually we'll have them all, but the key word is "eventually".

In the meantime, you can [mod it yourself](https://github.com/yairm210/Unciv/wiki/Mods)!

## Will you implement Civ VI?

Considering how long it took to get this far, no.

## World wrap?

I've tried this with no success, if you figure out how to do this well PR it :)

## Is there a desktop version?

Yes! Windows and Linux versions are available at [itch.io](https://yairm210.itch.io/unciv), and if you're using the Itch app, your game will stay up-to-date - and we release pretty frequently so that's an issue ;)

If you have Java 8, and are familiar with the command line, there are (considerably smaller) JARs in [Releases](https://github.com/yairm210/UnCiv/releases) which you can run with `java -jar Unciv.jar`. This is also (currently) the only way to run the game on MacOS.

If you want to build it from sratch for some reason, [we have instructions for that as well](https://github.com/yairm210/Unciv/wiki/Building-locally-without-Android-Studio)

## How about IOS?

I'm not planning on it.

It means paying money to Apple, yet another release path,
 and since I don't have an IOS device it means I can't test it properly.
 
## How come this isn't working on my Raspberry Pi?

LibGDX doesn't work on Raspberry, and so neither does Unciv. 

If you're really invested, I'd be thrilled if you could make it work - this seems to be possible, see 
https://github.com/chrishumphreys/LIbGDX-Pi for his detailed instructions

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

## Disclosure

Multiplayer takes advantage of Dropbox, which is *non-free software*, for syncing purposes.

Single player does not use this feature.

# [Credits and 3rd parties](docs/Credits.md)
