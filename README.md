# Unciv - Civ V remake for Android & Desktop

![](/extraImages/GithubPreviewImage.jpg)

[![Google Play](https://img.shields.io/static/v1?label=Google&message=Play&logo=google-play)](https://play.google.com/store/apps/details?id=com.unciv.app)
[![F-Droid](https://img.shields.io/f-droid/v/com.unciv.app?logo=f-droid)](https://f-droid.org/en/packages/com.unciv.app/)
[![itch.io](https://img.shields.io/static/v1?label=itch.io&message=Unciv&color=607D8B&logo=itch.io)](https://yairm210.itch.io/unciv)
[![Flathub](https://img.shields.io/flathub/v/io.github.yairm210.unciv?logo=flathub)](https://flathub.org/apps/details/io.github.yairm210.unciv)
[![AUR](https://img.shields.io/aur/version/unciv-bin?logo=arch-linux)](https://aur.archlinux.org/packages/unciv-bin)
[![pi-apps](https://img.shields.io/badge/dynamic/json?color=c51a4a&label=Pi-Apps&logo=raspberry-pi&query=%24.Unciv.Version&url=https%3A%2F%2Fraw.githubusercontent.com%2FBotspot%2Fpi-apps-analytics%2Fmain%2Fpackage_data_v2.json)](https://github.com/Botspot/pi-apps)
![Brew](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fformulae.brew.sh%2Fapi%2Fformula%2Funciv.json&query=%24.versions.stable&logo=homebrew&label=Brew)
[![Chocolatey](https://img.shields.io/chocolatey/v/unciv?logo=chocolatey)](https://community.chocolatey.org/packages/unciv)
[![scoop-games](https://img.shields.io/scoop/v/unciv?bucket=games)](https://github.com/Calinou/scoop-games)
 
[![Build status](https://github.com/yairm210/Unciv/actions/workflows/buildAndTest.yml/badge.svg)](https://github.com/yairm210/Unciv/actions/workflows/buildAndTest.yml)
[![Discord](https://img.shields.io/discord/586194543280390151?color=%237289DA&logo=discord&logoColor=%23FFFFFF)](https://discord.gg/bjrB4Xw)


## What is this?

An open source, moddability-focused Android and Desktop remake of Civ V, made with LibGDX

## Is this any good?

Depends what you're looking for. If you're in the market for high-res graphics, amazing soundtracks, animations etc, I highly recommend Firaxis's Civ-V-like game, "Civilization V".

If you want a small, fast, moddable, FOSS, in-depth 4X that can still run on a potato, you've come to the right place :)

## How do I install?

- **Android** - [Google Play](https://play.google.com/store/apps/details?id=com.unciv.app) or [F-droid](https://f-droid.org/en/packages/com.unciv.app/)
- **Linux** - [itch.io](https://yairm210.itch.io/unciv), Flatpak via [Flathub](https://flathub.org/apps/details/io.github.yairm210.unciv), or [AUR](https://aur.archlinux.org/packages/unciv-bin)
- **Windows** - [Grab the MSI](https://github.com/yairm210/Unciv/releases/latest/download/Unciv.msi), or get from [itch.io](https://yairm210.itch.io/unciv), [Chocolatey](https://community.chocolatey.org/packages/unciv), or [Scoop](https://github.com/Calinou/scoop-games)
- **Raspberry Pi** - [Pi-apps](https://github.com/Botspot/pi-apps)
- **MacOS** - Via [Brew](https://brew.sh/) (`brew update && brew install unciv`) or install [with this guide](https://yairm210.github.io/Unciv/Other/Installing-on-macOS/) 
- Jars, APKs and Windows/Linux builds also available in [Releases](https://github.com/yairm210/Unciv/releases) (run jar with `java -jar Unciv.jar`) - *not recommended* since we update frequently and you will quickly become out-of-date
- [Build from scratch](https://yairm210.github.io/Unciv/Developers/Building-Locally/#without-android-studio) if that's your thing

## What's the roadmap?

In this order:

* Polish!
    * UI+UX improvements ([suggestions welcome!](https://github.com/yairm210/Unciv/issues/new?assignees=&labels=feature&template=feature_request.md&title=Feature+request%3A+))
    * Better automation, AI etc. in-game
* G&K mechanics - espionage, small other changes (see [#4697](https://www.github.com/yairm210/Unciv/issues/4697))
* BNW mechanics - trade routes, world congress, etc.

## Contributing

Programmers start [here](https://yairm210.github.io/Unciv/Developers/Building-Locally/)!

Translators start [here](https://yairm210.github.io/Unciv/Other/Translating/)! Language completion status [here](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/translations/completionPercentages.properties) 

Modders start [here](https://yairm210.github.io/Unciv/Modders/Mods/)!

You can join us in any of the open issue, or work on improving anything you want - once you're finished, issue a pull request and it'll go into the next version!

If not, you can help by spreading the word - vote for Unciv where you can, mention it on Reddit or Twitter etc, and help us with new ideas of how to get the word out!


## FAQ

### How about iOS?

I'm not planning on it. It means paying money to Apple, yet another release path,
 and since I don't have an iOS device it means I can't test it properly.

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

According to the [US Copyright Office FL-108](https://upload.wikimedia.org/wikipedia/commons/9/96/U.S._Copyright_Office_fl108.pdf), intellectual property rights *do not* apply to mechanics - as I'm sure you know, there are a billion Flappy Bird knockoffs

It is definitely illegal:
 - To use any assets from the original game (images, sound etc) - they belong to Firaxis

It is probably illegal (no solid sources on this):
 - To use the Civilization name
 - To impersonate the Civ games (so calling yourself civi|zation with a similar logo, for instance)

Interestingly, [Civilization is a registered trademark](https://tsdr.uspto.gov/#caseNumber=74166752&caseType=SERIAL_NO&searchType=statusSearch), but it looks like it's only *that particular logo* which is trademarked, so technically you could make another game called "Civilization" and it'll stick. In any case we're not going there :) 

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
