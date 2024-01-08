# Images and Audio

## Images and the texture atlas

Images need to be 'packed' before the game can use them. This preparation step needs to happen only once (as long as the original graphics are not changed).
The result one ore more a pairs of files - a texture in [png format](https://en.wikipedia.org/wiki/PNG) and a corresponding [atlas](https://en.wikipedia.org/wiki/Texture_atlas) file.
If you have a single `Images`folder, the default such pair is named `game.png`/`game.atlas`.
For your players, the individual images aren't important - only the combined images actually register to the game, so you need to include them in your repository and keep them up to date.
We still recommend including the originals in a mod, so other developers running from source can access them.
With original images included, you can use a development environment using git, have it linked to your repository, while using a symlink to allow Unciv to see the mod - and pack your atlas for you on every launch.
If you're developing your mod on an Android version of Unciv (not recommended!) you won't be able to generate these packed files directly.

### Ways to pack texture atlases

- Launch the desktop version with your mod (your mod's main folder is a subfolder of the game's "mods" folder, or symlinked there).
- You can ask someone in the Discord server to help you out.
- You can use external tools, [e.g. gdx-texture-packer-gui](https://github.com/crashinvaders/gdx-texture-packer-gui). Utmost care needs to be taken that the files can be discovered by Unciv and internal relative paths are correct.
- The Unciv repo itself has a feature that can pack images on github runners - documentation still needs to be done.

### Multiple texture atlases

If your mod has lots of images (or large ones), the textures might 'spill' into additional texture files - 2048x2048 is the limit for a single texture pack. You will see a `game2.png`, possibly a `game3.png` or more appear.
This is not good for performance, which is why the base game controls which kinds of images go together into one texture(+atlas).
This works for mods, too: Create not only one Images folder, but several, the additional ones named "Images.xyz", where xyz will become the filename of the additional texture file (So don't use both Images and Images.game - those will clash). Look at the Unciv base game to get a better idea how that works.
To minimize texture swaps, try to group them by the situation where in the game they are needed. You can distibute by folder, but having the same subfolders under several "Images.xyz" and distributing the images between them will also work.

A file `Atlases.json` (uppercase 'A') in the mod root (not in `Images` or in `jsons`) controls which atlases to load, which in turn control which texture (`.png`) files are read.
This file is automatically created by the built-in packer. Only the `game.atlas` file is read by default for backward compatibility.
If you use external tools and multiple atlases, you will need to maintain this file yourself - it is a simple json array of strings, each a file name without the `.atlas` extension (saved as UTF-8 without byte order mark).

### Texture packer settings

The texture packers built into Unciv will look for a `TexturePacker.settings` file in each `Images` directory (_not_ under `jsons`).
With this file you can tune the packer - e.g. control pixel interpolation filters.
It is a json of a [Gdx TexturePacker.Settings](https://libgdx.com/wiki/tools/texture-packer#settings) instance.
The default settings are as shown in the Gdx documentation linked above if you do supply a settings file, but without such a file, some fields have different defaults:
- `maxWidth`, `maxHeight`: 2048
- `fast`: true
- `paddingX`, `paddingY`: 8
- `duplicatePadding`: true
- `filterMin`: MipMapLinearLinear
- `filterMag`: MipMapLinearLinear unless the atlas name ends in `Icons`, then Linear.

### Texture atlas encoding

Due to certain circumstances, please make sure names and paths that will be mapped in an atlas use **only ascii**. Not all parts of the loader enforce strict UTF-8 usage, sorry.
Symptoms if you fail to heed this: mod works on a Chinese Windows box but not on a western one or vice-versa, or mod works on a Chinese Windows box but not a Chinese Linux box or vice-versa, or mod works on a Chinese Windows box with default settings but not on the same box with "Use unicode UTF-8 for worldwide language support" turned on.
This does not technically apply to the atlas name itself when multiple atlases are used (the xyz part in "Images.xyz"), but we nevertheless recommend the same rule for consistency.

## Permanent audiovisual mods

The following chapters describe possibilities that will work while a mod is ***active***.
It is either selected for the current game (during new game creation, cannot be changed after that for saved games), meaning all its rules and resources will be used.
_Or_ it is marked as 'Permanent audiovisual mod' in the mod manager (you must select it in the 'installed' column to get the checkbox).
In that case only graphics and audio will be active, the rule changes will be ignored (if it contains any) unless the first way is _also_ used.
Note that this feature includes graphics or sounds from the selected mod in _all_ games, even those started before installing the mod.
Repeat: In case of a mod bringing both changed rules and audiovisuals, the 'permanent' feature will include only the media on all games, to use the rules you will still need to select the mod for a new game.

Note that the Mod author can (and often should) control whether the checkbox appears using [ModOptions](Mod-file-structure/5-Miscellaneous-JSON-files.md#modoptionsjson) [uniques](uniques.md#modoptions-uniques).

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

Additionally, there there are some kinds of images where the game has display capability but does not supply graphics itself, as described in the next paragraphs:

### Adding custom Fonts

You can add custom `.ttf` fonts into the game: place `.ttf` file inside of `/fonts/` directory of your mod. The font you have added will be visible and choosable in `Options-Advanced` tab at the top of font list as `<fontname> (<modname>)`.

All fonts are rendered by default at 50 pixel size and rescaled later for the game's needs. Currently fonts are NOT mipmapped on minification.

### Adding Wonder Splash Screens

You can add wonder images to mods and they'll be displayed instead of the standard icon when a wonder is finished. The image needs to be a .png and 2:1 ratio so for example 200x100 px.

Add the images to `/Images/WonderImages/`. They need to be named according to the name field in `Buildings.json`, so for example "Temple of Artemis.png" or "Stonehenge.png"

Remember, to be compatible with mobile devices, a fresh atlas needs to be generated including these.

### Adding Leader Portraits

The base game comes without Leader Portraits, but is able to display them in greetings, Civilopedia, diplomacy screens, or the nation picker. A mod can supply these, by adding their images to `/Images/LeaderIcons/`. The file name must correspond exactly with the leader name of a nation as defined in Nations.json, or they will be ignored.

These work best if they are square, between 100x100 and 256x256 pixels, and include some transparent border within that area.

For example, [here](https://github.com/yairm210/Unciv-leader-portrait-mod-example) is mod showing how to add leader portraits, which can complement the base game.

### Adding Portraits

The base game uses flat icons, surrounded with colored circles as backgrounds (e.g. for units to fit the civilization's flag colors), to denote entities such as: units, buildings, techs, resources, improvements, religions, promotions, uniques, unit actions and nations in the UI. A mod can supply "Portraits" - static images that will remain uncolored - by adding images to `/Images/<entityType>Portraits/` (e.g. `/Images/BuildingPortraits/`, /Images/ResourcesPortraits/, etc), which will be used in all UI elements (except for unit icons in the world map). The file name must correspond exactly with the unit/building/tech/resource/etc name  defined in corresponding JSONs (e.g. Units.json, Buildings.json, TileResources.json, etc) or have the same name as the file they suppose to replace, or they will be ignored.

If mod supplies '/Images/<entityType>Portraits/Background.png' images, they will be used as a background for corresponding portraits instead of default circle.

Portraits and backgrounds work best if they are full RGB square, between 100x100 and 256x256 pixels, and include some transparent border within that area.

For example, [here](https://github.com/vegeta1k95/Civ-5-Icons) is mod showing how to add custom portraits, which can complement the base game.

### Adding icons for Unit Types

The Unit Types as defined in [UnitTypes.json](Mod-file-structure/4-Unit-related-JSON-files.md#unittypesjson) have no icons in the base game, but Civilopedia can decorate their entries if you supply images named 'Images/UnitTypeIcons/<UnitType>.png'.
(while you're at it, you may override the default icon for the Unit Type _category header_ - it's 'UnitTypes.png' in the same folder, or the icons used for the movement domains - 'DomainLand', 'DomainWater', 'DomainAir')

### Adding icons for Beliefs

The individual Beliefs - as opposed to Belief types, as defined in [Beliefs.json](Mod-file-structure/2-Civilization-related-JSON-files.md#beliefsjson) have no icons in the base game, but Civilopedia can decorate their entries if you supply images named 'Images/ReligionIcons/<Belief>.png'.
Civilopedia falls back to the icon for the Belief type - as you can see in the base game, but individual icons have precedence if they exist.

### Adding Victory illustrations

You can enable pictures for each of the Victories, illustrating their progress. That could be a Spaceship under construction, showing the parts you've added, or cultural progress as you complete Policy branches. They will be shown on a new tab of the Victory Screen.

For this, you need to create a number of images. In the following, `<>` denote names as they appear in [VictoryTypes.json](../Other/Miscellaneous-JSON-files.md#victorytypes-json), untranslated, and these file names (like any other in Unciv) are case-sensitive. All files are optional, except Background as noted:

* `VictoryIllustrations/<name>/Background.png` - this determines overall dimensions, the others must not exceed its size and should ideally have identical size. Mandatory, if this file is missing, no illustrations will be shown for this Victory Type.
* `VictoryIllustrations/<name>/Won.png` - shown if _you_ (the viewing player) won this Victory.
* `VictoryIllustrations/<name>/Lost.png` - shown if a competitor won this Victory - or you have completed this Victory, but have won a different one before.
* `VictoryIllustrations/<name>/<milestone>.png` - One image for each entry in the `milestones` field without an `[amount]`, name taken verbatim but _without_ square brackets, spaces preserved.
* `VictoryIllustrations/<name>/<milestone> <index>.png` - For entries in the `milestones` field with an `[amount]`, one image per step, starting at index 1.
* `VictoryIllustrations/<name>/<component>.png` - One image for each unique entry in the `requiredSpaceshipParts` field, that is, for parts that can only be built once. Spaces in unit names must be preserved.
* `VictoryIllustrations/<name>/<component> <index>.png` - For parts in the `requiredSpaceshipParts` field that must be built several times, one per instance. Spaces in unit names must be preserved, and there must be one space between the name and the index. Indexes start at 1.

Remember - these are logical names as they are indexed in your atlas file, if you let Unciv pack for you, the `VictoryIllustrations` folder should be placed under `<mod>/Images` - or maybe `<mod>/Images.Victories` if you want these images to occupy a separate `Victories.atlas` (Do not omit the `Images` folder even if left empty, the texture packer needs it as marker to do its task).

That's almost all there is - no json needed, and works as ['Permanent audiovisual mod'](#permanent-audiovisual-mods). The Background image is the trigger, and if it's present all part images must be present too, or your spaceship crashes before takeoff, taking Unciv along with it. That was a joke, all other images are optional, it could just look boring if you omit the wrong ones.

As for "almost" - all images are overlaid one by one over the Background one, so they must all be the same size. Except for Won and Lost - those, if their condition is met, _replace_ the entire rest, so they can be different sizes than the background. The part images are overlaid over the background image in no guaranteed order, therefore they should use _transparency_ to avoid hiding parts of each other.

One way to create a set is to take one final image, select all parts that should be the centerpiece itself not background (use lasso, magic wand or similar tools, use antialiasing and feathering as you see fit), copy and paste as new layer. Then apply desaturation and/or curves to the selection on the background layer to only leave a hint of how the completed victory will look like. Now take apart the centerpiece - do a selection fitting one part name, copy and paste as new layer (in place), then delete the selected part from the original centerpiece layer. Rinse and repeat, then export each layer separately as png with the appropriate filenames.
There's no suggested size, but keep in mind textures are a maximum of 2048x2048 pixels, and if you want your images packed properly, several should fit into one texture. They will be scaled down if needed to no more than 80% screen size, preserving aspect ratio.

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

| Description                                        | Prefix            | [^M] |           Suffix | [^X]  | Flags |
|----------------------------------------------------|:------------------|:----:|-----------------:|:-----:|:-----:|
| Automatic next-track[^0]                           |                   |      |          Ambient |       |       |
| Launch game[^1]                                    |                   |      |             Menu |       |       |
| Every 10th turn                                    | (player civ name) | [^M] | Peace or War[^2] |       | [^F]  |
| New game: Select a mod                             | (mod name)        | [^M] |            Theme |       | [^S]  |
| New game: Pick a nation for a player               | (nation name)     | [^M] |   Theme or Peace |       | [^S]  |
| Diplomacy: Select player                           | (nation name)     | [^M] | Peace or War[^3] |       | [^S]  |
| First contact[^4]                                  | (civ name)        | [^M] |   Theme or Peace | [^X]  |       |
| War declaration[^5]                                | (civ name)        | [^M] |              War | [^X]  |       |
| Civ defeated                                       | (civ name)        |      |           Defeat | [^X]  |       |
| Player wins                                        | (civ name)        |      |          Victory | [^X]  |       |
| Golden Age                                         | (civ name)        | [^M] |           Golden | [^X]  |       |
| Wonder built                                       | (wonder name)     | [^M] |           Wonder | [^X]  |       |
| Tech researched                                    | (tech name)       | [^M] |       Researched | [^X]  |       |
| Map editor: Select nation start location           | (nation name)     | [^M] |            Theme |       | [^S]  |
| Options: Volume slider or Default track downloaded |                   |      |                  |       | [^D]  |
| Music controls (Options or from Menu) Next track   |                   |      |          Ambient |       |       |

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

## Supply Leader Voices

Sound files named from a Nation name and the corresponding text message's [field name](Mod-file-structure/2-Civilization-related-JSON-files.md#nationsjson),
placed in a mod's `voices` folder, will play whenever that message is displayed. Nation name and message name must be joined with a dot '.', for example `voices/Zulu.defeated.ogg`.

Leader voice audio clips will be streamed, not cached, so they are allowed to be long - however, if another Leader voice or a city ambient sound needs to be played, they will be cut off without fade-out
Also note that voices for City-State leaders work only for those messages a City-state can actually use: `attacked`, `defeated`, and `introduction`.
