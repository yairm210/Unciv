# Unciv - FOSS Civ V for Android+Desktop

![](/extraImages/GithubPreviewImage.png)

[![Google Play](https://img.shields.io/static/v1?label=Google&message=Play&color=607D8B&logo=google-play)](https://play.google.com/store/apps/details?id=com.unciv.app)
[![F-Droid](https://img.shields.io/f-droid/v/com.unciv.app?logo=f-droid)](https://f-droid.org/en/packages/com.unciv.app/)
[![itch.io](https://img.shields.io/static/v1?label=itch.io&message=Unciv&color=607D8B&logo=itch.io)](https://yairm210.itch.io/unciv)
[![Flathub](https://img.shields.io/flathub/v/io.github.yairm210.unciv?logo=flathub)](https://flathub.org/apps/details/io.github.yairm210.unciv)
[![pi-apps](https://img.shields.io/static/v1?label=pi-apps&message=Unciv&color=607D8B&logo=raspberry-pi)](https://github.com/Botspot/pi-apps)

![Build and deploy](https://github.com/yairm210/Unciv/workflows/Build%20and%20deploy/badge.svg)
[![Discord Chat](https://img.shields.io/discord/586194543280390151.svg)](https://discord.gg/bjrB4Xw)



## What is this?

An open-source, mod-friendly Android+Desktop remake of Civ V, made with LibGDX

## Is this any good?

Depends what you're looking for. If you're in the market for high-res graphics, amazing soundtracks, animations etc, I highly recommend Firaxis's Civ-V-like game, "Civilization V".

If you want a small, fast, moddable, FOSS, in-depth 4X that can still run on a potato, you've come to the right place :)

## What's the roadmap?

In this order:

* Polish! As you may have noticed, Unciv is fully functional but rough around the edges. This means:
    * UI+UX improvements ([suggestions welcome!](https://github.com/yairm210/Unciv/issues/new?assignees=&labels=feature&template=feature_request.md&title=Feature+request%3A+))
    * Better automation, AI etc. in-game
* Finishing off Vanilla mechanics - mostly done!
* G&K mechanics - espionage, small other changes (see [#4697](https://www.github.com/yairm210/Unciv/issues/4697))
* BNW mechanics - trade routes, world congress, etc.

## Contributing

### How can I help?

Programmers start [here](https://yairm210.github.io/Unciv/Developers/Building-Locally/)!

Translators start [here](https://yairm210.github.io/Unciv/Other/Translating/)!

Modders start [here](https://yairm210.github.io/Unciv/Modders/Mods/)!

You can join us in any of the open issue, or work on improving anything you want - once you're finished, issue a pull request and it'll go into the next version!

If not, you can help by spreading the word - vote for Unciv where you can, mention it on Reddit or Twitter etc, and help us with new ideas of how to get the word out!


## FAQ

### Will you implement {feature}?

If it's in the original Civ V, then yes!

There's a lot left to implement, so it's hard to give an estimation of when exactly each feature will be added, but we're constantly improving!

If not, then the feature won't be added until we've finished all the features from the original Civ - as mentioned, this will take a while!

#### Why not? This is its own game, why not add features that weren't in Civ V?

Having a clear vision is important for actually getting things done.

Anyone can make a suggestion. Not all are good, viable, or simple. Not many can actually implement stuff.

As an open source project, this stuff is done in our spare time, of which there isn't much.

We need a clear-cut criteria to decide what to work on and what not to work on.

### Will you implement Civ VI?

Considering how long it took to get this far, no.

### Is there a desktop version?

Yes! Windows and Linux versions are available at [itch.io](https://yairm210.itch.io/unciv), and if you're using the Itch app, your game will stay up-to-date - and we release pretty frequently so that's an issue ;)

Unciv can also be installed on macOS, a guide on how to do that can be found [here](https://yairm210.github.io/Unciv/Other/Installing-on-macOS/).

If you have Java 8, and are familiar with the command line, there are (considerably smaller) JARs in [Releases](https://github.com/yairm210/UnCiv/releases) which you can run with `java -jar Unciv.jar`.
For Mac users, you'll need to add extra parameters, `java -XstartOnFirstThread -Djava.awt.headless=true -jar Unciv.jar`.

If you use Flatpaks, there's a Flatpak by [MayeulC](https://github.com/MayeulC) and you can know more about it [here](https://github.com/flathub/io.github.yairm210.unciv). Flathub link is available in the [Downloads](#downloads) section.

If you want to build it from scratch for some reason, [we have instructions for that as well](https://yairm210.github.io/Unciv/Developers/Building-locally-without-Android-Studio/)

### How about IOS?

I'm not planning on it.

It means paying money to Apple, yet another release path,
 and since I don't have an IOS device it means I can't test it properly.

### How can I learn to play? Where's the wiki?

All the tutorial information is available in-game at menu > civilopedia > tutorials

All the information is included in the amazing [Civ V wiki](https://civilization.fandom.com/wiki/)

Since this is a Civ V clone, you can search Google for how to play Civ V and there are loads of answers =)

Alternatively, you could [join us on Discord](https://discord.gg/bjrB4Xw) and ask there =D

### Aren't you basically making a Civ V clone? Is that even legal?

This is a subject that I've heard a lot of hearsay on but no solid sources of law.

From what I gather, it is illegal:
 - To use the Civilization name
 - To impersonate the Civ games (so calling yourself civi|zation with a similar logo, for instance)
 - To use any assets from the original game (images, sound etc) - they belong to Firaxis

From what I understand, intellectual property rights apply to names, characters and settings. They do not apply to mechanics - as I'm sure you know, there are a billion Flappy Bird knockoffs

If anyone has any real legal sources, or can shed some light on the limits of what is and is not allowed, I'd be happy to hear!

## Disclosure

[Multiplayer](https://yairm210.github.io/Unciv/Other/Multiplayer/) takes advantage of Dropbox, which is *non-free software*, for syncing purposes.

Single player does not use this feature.

## Downloads

| [![](https://static.itch.io/images/badge.svg)](https://yairm210.itch.io/unciv)    |    [![](https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png)](https://play.google.com/store/apps/details?id=com.unciv.app)   |    [![](https://fdroid.gitlab.io/artwork/badge/get-it-on.png)](https://f-droid.org/en/packages/com.unciv.app/)	| [![](https://flathub.org/assets/badges/flathub-badge-en.svg)](https://flathub.org/apps/details/io.github.yairm210.unciv)
|---	|---	|---	|---	|
## Run with Docker

If you have docker and docker-compose installed, you can:

* Run ```$ docker-compose build && docker-compose up```
* Open http://localhost:6901/vnc.html?password=headless

And if you are using docker desktop:
* ```$ docker compose build && docker compose up```
* http://localhost:6901/vnc.html?password=headless

## [Credits and 3rd parties](docs/Credits.md)
