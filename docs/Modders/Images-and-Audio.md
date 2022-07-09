# Images and Audio

## Permanent audiovisual mods

The following chapters describe possibilities that will work while a mod is ***active***. It is either selected for the current game (during new game creation, cannot be changed after that for saved games), meaning all its rules and resources will be used. _Or_ it is marked as 'Permanent audiovisual mod' in the mod manager (you must select it in the 'installed' column to get the checkbox). In that case only graphics and audio will be active, the rule changes will be ignored (if it contains any) unless the first way is _also_ used.

## Override built-in graphics

If a mod supplies an image with the same name and path as one included in the base game (and its [atlas](Mods.md#more-on-images-and-the-texture-atlas) is up to date), and the mod is active, the mod's graphics will be used instead of the built-in one.

For example, if you include a file named "Images/OtherIcons/Link.png" in your mod, you will be overriding the little chain links icon denoting linked lines in Civilopedia. The first part of the path is not relevant for overriding, it controls which of a set of atlas files will carry the image, but for selection in the game only the rest of the path is relevant. So, to override "Images.Tech/TechIcons/Archery.png" you could place your image as "Images/TechIcons/Archery.png" and it would work because the "TechIcons/Archery" part is the key.

Please note, as for adding items, your graphics should keep the size and color choices of the original, or the result may be surprising, e.g. when the game tries to tint such an image.

## Supply additional graphics

You will need to supply the graphics for new elements - a new unit needs its icon just as a new nation does. The rules are:

-   The path and name of the image file need to conform to the rule: `Image[.AtlasName]/Type-specific/Objectname.png` (Type-specific means "TechIcons" for a Technology, "NationIcons" for a Nation and so on. See vanilla game folders. Objectname is the exact name as defined in json, before translation.)
-   All path parts are case sensitive.
-   Unit Pixel sprites and [Tilesets](Creating-a-custom-tileset.md) follow special rules.
-   Promotions can be named "`[Unitname] ability`". In such a case, if `UnitIcons/Unitname.png` exists it will fall back to that unit icon when `UnitPromotionIcons/Unitname ability.png` is missing.
-   Promotions can be named "Something I" (or " II" or " III"). The suffix will be removed and painted as little stars, only the base `UnitPromotionIcons/Something.png` will be loaded.
-   The special rules for promotions can be combined, e.g. "`[Warrior] ability III`" will fall back to the Warrior unit icon and paint 3 Stars on it.

Additionally, there there are two kinds of images where the game has display capability but does not supply graphics itself, as described in the next paragraphs:

### Adding Wonder Splash Screens

You can add wonder images to mods and they'll be displayed instead of the standard icon when a wonder is finished. The image needs to be a .png and 2:1 ratio so for example 200x100 px.

Add the images to `/Images/WonderImages/`. They need to be named according to the name field in `Buildings.json`, so for example "Temple of Artemis.png" or "Stonehenge.png"

Remember, to be compatible with mobile devices, a fresh atlas needs to be generated including these.

### Adding Leader Portraits

The base game comes without Leader Portraits, but is able to display them in greetings, Civilopedia, diplomacy screens, or the nation picker. A mod can supply these, by adding their images to `/Images/LeaderIcons/`. The file name must correspond exactly with the leader name of a nation as defined in Nations.json, or they will be ignored.

These work best if they are square, between 100x100 and 256x256 pixels, and include some transparent border within that area.

For example, [here](https://github.com/yairm210/Unciv-leader-portrait-mod-example) is mod showing how to add leader portraits, which can complement the base game.

## Sounds

Standard values are below. The sounds themselves can be found [here](/sounds).

-   _arrow, artillery, bombard, bombing, cannon, chimes, choir, click, coin, construction, elephant, fortify, gdrAttack, horse, jetgun, machinegun, metalhit, missile, nonmetalhit, nuke, paper, policy, promote, setup, shipguns, shot, slider, swap, tankshot, throw, torpedo, upgrade, whoosh_.

Mods can add their own sounds, as long as any new value in attackSound has a corresponding sound file in the mod's sound folder, using one of the formats mp3, ogg or wav (file name extension must match codec used). Remember, names are case sensitive. Small sizes strongly recommended, Unciv's own sounds use 24kHz joint stereo 8-bit VBR at about 50-100kBps.

## Override built-in sounds

This works like graphics, except no atlas is involved. E.g. you include a sounds/Click.mp3, it will play instead of the normal click sound. These files must stay short and small. A sound larger than 1MB when uncompressed may break or not play at all on mobile devices. Unciv tries to standardize on 24kHz sample rate, joint stereo, low-bitrate VBR (-128kbps) mp3. Only mp3 and ogg formats will be recognized (but an existing mp3 can be overridden with an ogg file).

## Supply additional music

Sound files (mp3 or ogg) in a mod /music folder will be recognized and used when the mod is active. Except for context-specific music as described in the following paragraphs, tracks will play randomly from all available tracks (with a little bias to avoid close repetition of tracks). There is no overriding - a "thatched-villagers.mp3" in a mod will play in addition to and with the same likelihood as the file that the base game offers to download for you. There is no hard technical limit on bitrate or length, but large bandwidth requirements may lead to stuttering (The end of a "next turn", right before the world map is updated, and with very large maps, is the most likely to cause this).

### Context-sensitive music: Overview

The Music Controller will generally play one track after another, with a pause (can be changed in options) between. While the "Leave game?" confirmation dialog is opened playback will fade out and pause and can resume when it is closed.

There are various 'triggers' in the game code initiating a choice for a new track. The new track will, if necessary, fade out the currently playing track quickly before it starts playing. Track choice involves context provided by the trigger and a random factor, and an attempt is made to not repeat any track until at least eight others have played.

Mods can provide their own music folder, and if they are active its contents will be treated exactly the same as those in the main music folder. Mods should control usage of their tracks by careful choice of file name. Mod developers can watch console output for messages logging track choice with trigger parameters or loading errors.

One track is special: The Thatched Villagers (see also credits.md). The game is able to download it if the music folder is empty, and it is played when the music volume slider is used. It is also a fallback track should certain problems occur (a broken file, however, will shut down the player until another trigger happens).

### Context-sensitive music: List of Triggers

Triggers indicate context (call it intent, mood, whatever, it doesn't matter) by optionally providing a prefix and/or suffix to match against the file name. There are a few flags as well influencing choice or behaviour - one flag function is to make prefix or suffix mandatory, meaning if no available file matches the track chooser will do nothing. Otherwise, a next track will always be chosen from the available list by sorting and then picking the first entry. Sorting is done by in order of precedence: Prefix match, Suffix match, Recently played, and a random number. Therefore, as currently no triggers have an empty prefix, files matching none of the prefixes will never play unless there are less than eight files matching the requested prefix.

The current list of triggers is as follows:

| Description | Prefix | [^M] | Suffix | [^X] | Flags |
| ----------- |:------ |:----:| ------:|:----:|:-----:|
| Automatic next-track[^0] | | | Ambient | | |
| Launch game[^1] | | | Menu | | |
| Every 10th turn | (player civ name) | [^M] | Peace or War[^2] | | [^F] |
| New game: Select a mod | (mod name) | [^M] | Theme | | [^S] |
| New game: Pick a nation for a player | (nation name) | [^M] | Theme or Peace | | [^S] |
| Diplomacy: Select player | (nation name) | [^M] | Peace or War[^3] | | [^S] |
| First contact[^4] | (civ name) | [^M] | Theme or Peace | [^X] | |
| War declaration[^5] | (civ name) | [^M] | War | [^X] | |
| Civ defeated | (civ name) | | Defeat | [^X] | |
| Golden Age | (civ name) | [^M] | Golden | [^X] | |
| Wonder built | (wonder name) | [^M] | Wonder | [^X] | |
| Tech researched | (tech name) | [^M] | Researched | [^X] | |
| Map editor: Select nation start location | (nation name) | [^M] | Theme | | [^S] |
| Options: Volume slider or Default track downloaded | | | | | [^D] |
| Options: Click currently playing label[^6] | | [^M] | Ambient | | [^S] |

Legend:

-   [^N]: **Not implemented**
-   [^M]: Prefix must match. If no matching file is found, the trigger will do nothing.
-   [^X]: Suffix must match. If no matching file is found, the trigger will do nothing.
-   [^S]: Stop after playback. No automatic next choice.
-   [^F]: Slow fadeout of replaced track.
-   [^D]: Always plays the default file.
-   [^0]: Whenever a track finishes and the configured silence has elapsed, an 'Ambient' track without any context is chosen. Also triggered by 'resume' (e.g. switching to another app and back on Android)
-   [^1]: First opening of the Main Menu (or the initial language picker).
-   [^2]: Whether the active player is at war with anybody.
-   [^3]: According to your relation to the picked player.
-   [^4]: Excluding City States.
-   [^5]: Both in the alert when another player declares War on you and declaring War yourself in Diplomacy screen.
-   [^6]: Yes these flags are not optimal.
