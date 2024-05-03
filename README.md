# Unciv - Civ V remake for Android & Desktop

![](/extraImages/GithubPreviewImage.jpg)

[![Google Play](https://img.shields.io/static/v1?label=Google&message=Play&color=607D8B&logo=google-play)](https://play.google.com/store/apps/details?id=com.unciv.app)
[![F-Droid](https://img.shields.io/f-droid/v/com.unciv.app?logo=f-droid)](https://f-droid.org/en/packages/com.unciv.app/)
[![itch.io](https://img.shields.io/static/v1?label=itch.io&message=Unciv&color=607D8B&logo=itch.io)](https://yairm210.itch.io/unciv)
[![Flathub](https://img.shields.io/flathub/v/io.github.yairm210.unciv?logo=flathub)](https://flathub.org/apps/details/io.github.yairm210.unciv)
[![AUR](https://img.shields.io/aur/version/unciv-bin?logo=arch-linux)](https://aur.archlinux.org/packages/unciv-bin)
[![pi-apps](https://img.shields.io/static/v1?label=pi-apps&message=Unciv&color=607D8B&logo=raspberry-pi)](https://github.com/Botspot/pi-apps)
[![macport](https://img.shields.io/badge/dynamic/json?logo=apple&label=MacPorts&prefix=v&query=version&url=https%3A%2F%2Fports.macports.org%2Fapi%2Fv1%2Fports%2Funciv%2F)](https://ports.macports.org/port/unciv/)
 
[![Build status](https://github.com/yairm210/Unciv/actions/workflows/buildAndTest.yml/badge.svg)](https://github.com/yairm210/Unciv/actions/workflows/buildAndTest.yml)
[![Discord](https://img.shields.io/discord/586194543280390151?color=%237289DA&logo=discord&logoColor=%23FFFFFF)](https://discord.gg/bjrB4Xw)


## What is this?

An open source, moddability-focused Android and Desktop remake of Civ V, made with LibGDX

## Is this any good?

Depends what you're looking for. If you're in the market for high-res graphics, amazing soundtracks, animations etc, I highly recommend Firaxis's Civ-V-like game, "Civilization V".

If you want a small, fast, moddable, FOSS, in-depth 4X that can still run on a potato, you've come to the right place :)

## What's the roadmap?

In this order:

* Polish! As you may have noticed, Unciv is fully functional but rough around the edges. This means:
    * UI+UX improvements ([suggestions welcome!](https://github.com/yairm210/Unciv/issues/new?assignees=&labels=feature&template=feature_request.md&title=Feature+request%3A+))
    * Better automation, AI etc. in-game
* G&K mechanics - espionage, small other changes (see [#4697](https://www.github.com/yairm210/Unciv/issues/4697))
* BNW mechanics - trade routes, world congress, etc.

## Contributing

Programmers start [here](https://yairm210.github.io/Unciv/Developers/Building-Locally/)!

Translators start [here](https://yairm210.github.io/Unciv/Other/Translating/)!

Modders start [here](https://yairm210.github.io/Unciv/Modders/Mods/)!

You can join us in any of the open issue, or work on improving anything you want - once you're finished, issue a pull request and it'll go into the next version!

If not, you can help by spreading the word - vote for Unciv where you can, mention it on Reddit or Twitter etc, and help us with new ideas of how to get the word out!


## FAQ

## How do I install?

- **Android** - [Google Play](https://play.google.com/store/apps/details?id=com.unciv.app) or [F-droid](https://f-droid.org/en/packages/com.unciv.app/)
- **Windows/Linux** - [itch.io](https://yairm210.itch.io/unciv), Flatpak via [Flathub](https://flathub.org/apps/details/io.github.yairm210.unciv), or [AUR](https://aur.archlinux.org/packages/unciv-bin)
- **Raspberry Pi** - [Pi-apps](https://github.com/Botspot/pi-apps)
- **MacOS** - Install [with this guide](https://yairm210.github.io/Unciv/Other/Installing-on-macOS/) 
- Jars, APKs and Windows/Linux builds also available in [Releases](https://github.com/yairm210/Unciv/releases) (run jar with `java -jar Unciv.jar`) - *not recommended* since we update frequently and you will quickly become out-of-date
- [Build from scratch](https://yairm210.github.io/Unciv/Developers/Building-locally-without-Android-Studio/) if that's your thing

### How about IOS?

I'm not planning on it. It means paying money to Apple, yet another release path,
 and since I don't have an IOS device it means I can't test it properly.
 
### Steam release?

Steam has decided that they don't want to host Unciv, they probably don't want to risk legal issues with Firaxis (although those should be non-existent, see below)
 
### Will you implement {feature}?

If it's in the original Civ V, then yes!

If not, then the feature won't be added to the base game - possibly it will be added as a way to mod the game, which is constantly expanding

#### Why not? This is its own game, why not add features that weren't in Civ V?

Having a clear vision is important for actually getting things done.

Anyone can make a suggestion. Not all are good, viable, or simple. Not many can actually implement stuff.

As an open source project, this stuff is done in our spare time, of which there isn't much.

We need a clear-cut criteria to decide what to work on and what not to work on.

#### Will you implement Civ VI?

Considering how long it took to get this far, no.

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

## Run with Docker [![Docker](https://github.com/yairm210/Unciv/actions/workflows/dockerPublish.yml/badge.svg)](https://github.com/yairm210/Unciv/actions/workflows/dockerPublish.yml)

If you have docker compose installed:

 ```$ docker compose build && docker compose up```

and then goto http://localhost:6901/vnc.html?password=headless

If just docker:

```$ docker build . -t unciv && docker run -d -p 6901:6901 -p 5901:5901 unciv  ```

Or just use our already built one:

```$ docker run -d -p 6901:6901 -p 5901:5901 ghcr.io/yairm210/unciv ```

and then goto http://localhost:6901/vnc.html?password=headless
## [Credits and 3rd parties](docs/Credits.md)
