# Introduction to Mods

## What are mods?

Everyone has that thing they wish could be in the game.
Unfortunately, the game only understands code, so mods are our way to give a degree of freedom to those of us who don't code.

Mods can *add, replace and remove* basic game definitions, such as units, nations, buildings, improvements, resources and terrains.
Games loaded with these mods will function according to the mod definition.

The game only knows how to recognize existing definitions, so you can't add *new* unique abilities to nations/units/buildings/etc, only play around with existing ones

There are three main kinds of mods:

-   **Extension mods** - these add new nations/units/buildings/resources to a base ruleset - can be either to the default ruleset, or to a base ruleset mod. Easy to do and probably the better place to get started.
-   **Base Ruleset mods** - these replace the entire existing ruleset - tech tree, units, policies, nations etc - to give an entirely different experience than the base game. These generally require quite a bit of work, but give a whole new experience, and so are the most popular.
-   **Ruleset-agnostic mods** - these do not contain any ruleset-related jsons, but instead contain other affects. Audiovisual mods (including tilesets, unitsets, and UI skins) and map mods are in this category.

Creating and editing mods from your phone is NOT RECOMMENDED - it's *much easier* using a desktop device!

## Audiovisual Mods

In addition to changing the rules - or even without doing so, mods can override existing graphics or sounds, or add music tracks. The game also has the ability to display graphics that are not included in the base game at all, such as leader portrait or wonder splash images, that must be provided by mods. For details, see [Audiovisual Mods](3-Images-and-Audio.md).

Custom tilesets and unitsets are a subgroup of these - see [Creating a custom tileset](4-Creating-a-custom-tileset.md) - as are UI skin mods, see [Creating a UI skin](5-Creating-a-UI-skin.md)

Such mods are candidates for the "Permanent audiovisual mod" switch available on the Mod Management Screen.
Note that this feature includes graphics or sounds from the selected mod in _all_ games, even those started before installing the mod.

In case of a mod bringing both changed rules and audiovisuals, the 'permanent' feature will include only the media on all games, to use the rules you will still need to select the mod for a new game.

## Mod names

Mods need to conform to github repo naming rules, but best stay simple and use only letters, digits, and dashes `-`.
Dashes are _automatically_ converted to spaces for display and use within Unciv.

Many punctuation or extended unicode characters _might_ work, but at best potential users won't find them attractive, at worst we'll refuse support when you run into problems :smiling_imp:

## Mod components

Mods are located in a `/mods` directory, on Desktop that should be next to your .jar file.

Mods typically have 2 subfolders:

-   `jsons` - here you should put files that alter the data of game objects, the order of the files is as in [the base json files](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons). More information on these can be found [here](Mod file structure/1-Overview.md)
-   `Images` - here you should put game images, as in [the base image files](/https://github.com/yairm210/Unciv/tree/master/android/Images). Please read [about atlases](#more-on-images-and-the-texture-atlas) for important details.

In order to remove objects from the game, you'll need to create a ModOptions file in the `/jsons` subfolder - there's an example [here](https://github.com/yairm210/Unciv-mod-example/blob/master/Removing%20Things/jsons/ModOptions.json)

In a base ruleset mod, ALL the original objects are removed - this is done by adding a `"isBaseRuleset":true` configuration to your modOptions file, [like so](https://github.com/k4zoo/Civilization-6-Mod/blob/master/jsons/ModOptions.json)

For an example, you can refer to [the example mod](https://github.com/yairm210/Unciv-mod-example) - just download the Example-Aliens-Mod and put it in a `/mods` folder next to the jar, run Unciv, start a new game, and you'll be able to enable the mod, which will allow to you pick Aliens as a playable civilization!

If you want to add a new civilization as a mod, you should check out [the Civ making instructions](2-Making-a-new-Civilization.md) to see what's required, or see the example Aliens mod =)

### More on Images and the texture atlas

When running on Desktop, images are combined on game startup into a large `game.png` file with a corresponding `.atlas` file.

This means that if you're developing your mod on an Android version of Unciv (not recommended!) you won't be able to generate these images files - you can ask someone in the Discord server to help you out

For your players, the individual images aren't important - only the combined images actually register to the game, so you need to include them in your repository and keep them up to date.

Actually omitting the original images would work for these uses, but we still recommend including them, so developers running from source can access them.

#### Extremely image-heavy mods

If your mod has lots of images (or large ones), the textures might 'spill' into additional texture ".png" files - 2048x2048 is the limit for a single texture pack.

This is not good for performance, which is why the base game controls which kinds of images go together into one texture(+atlas).

This works for mods, too: Create not only one Images folder, but several, the additional ones named "Images.xyz", where xyz will become the filename of the additional texture file (So don't use both Images and Images.game - those will clash). Look at the Unciv base game to get a better idea how that works.

To minimize texture swaps, try to group them by the situation where in the game they are needed. You can distibute by folder, but having the same subfolders under several "Images.xyz" and distributing the images between them will also work.

### Adding maps to mods

You can also add maps to mods, so they'll be available to players who download your mod.

A mod can also be maps-only, if all you want to do is share your maps.

When you've finished making your map in the Map Editor, save it, and it will be in the `/maps` folder of your game.

Copy it to a `/maps` folder in your *mod*, and you're done!

## Getting your mod out there

In order to make your mod downloadable by anyone, you need to create a Github repository (instructions [here](https://docs.github.com/en/github/getting-started-with-github/create-a-repo))

The Images and jsons folders need to be in the root directory of the repo - see [here](https://github.com/yairm210/Unciv-IV-mod) for example.

You can then manually download the mod from within the Mod Manager in Unciv:

-   From Unciv's main screen, click "Mods"
-   Click "Download mod from URL", and enter the location of your Github page
-   The game will automatically download and extract your mod, and it'll be ready to use!

Once you've tested that your mod CAN be downloaded, and that it works well once downloaded, you're ready for the final stage - GETTING IT TO THE USERS AUTOMATICALLY.

In order to do this, all you need to do is:

-   Go to your Github page
-   Click the gear icon next to the About (top-right part of the page)
-   In 'Topics', add "unciv-mod"

Optionally add one or more of the following topics to mark your mod as belonging to specific categories:

-   unciv-mod-rulesets (for base ruleset mods)
-   unciv-mod-expansions (for mods extending vanilla rulesets - please use this, **not** unciv-mod-expansion)
-   unciv-mod-graphics (for mods altering graphics - icons, portraits, tilesets)
-   unciv-mod-audio (for mods supplying music or modifying sounds)
-   unciv-mod-maps (for mods containing maps)
-   unciv-mod-fun (for mods mainly tweaking mechanics or other gameplay aspects)
-   unciv-mod-modsofmods (for mods extending another mod's ruleset)

When you open Unciv's Mod Manager, it will query Github's [list of repos with that topic](https://github.com/topics/unciv-mod), and now YOUR repo will appear there!
The categories will appear als annotations on the mod buttons, and the user can filter for them. They are not required for the game to use the content - e.g. you can still load maps from mods lacking the unciv-mod-maps topic.
If you want new categories, github will accept any topic, but you'll have to ask the Unciv team to enable them in the game.

If you feel there should be additional topics supported in-game, then the course of action is as follows:

-    You can add topics to your repository as you please, subject to github's terms, but if you whish them to become Unciv-supported they must begin with "unciv-mod-".
-    Once done, you can either:
     - Wait at least one release, check that your topic appeared in [ModCategories.json](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/ModCategories.json), and open a change PR for that file, removing the "hidden" attribute, telling us exactly why that topic would benefit the entire community.
     - Or, open an issue pointing us to your Mod with the new topics, asking us to do the above for you, again telling us why.

## I have the mod, now what?

The primary use of mods is to add them when starting a new game, or configuring a map. This will mean that both the ruleset of the mod, and the images, will be in use for that specific game/map.

For mods which are primarily visual or audio, there is a second use - through the mod manager, you can enable them as **permanent audiovisual mods**. This means that the images and/or sounds from the mod will replace the original media everywhere in the game, and contained music will be available - [see here](3-Images-and-Audio.md#supply-additional-music).

## Mod location for manual loading of mods

In general, you should never be manually-loading your mods - not only is this clunky, it's also more error-prone. Unless you have a very specific use-case, you probably shouldn't be doing this.

In Android, they should go into the `Android/data/com.unciv.app/files/mods` directory.

In Chromebook, go to "Play files", should be on the sidebar on the left side of the window under "My files". Click the 3 vertical dots on the top right-hand corner of the window below the "X".
If the option "Show all Play folders" does not have a check next to it click it. You should see some new files that appear on your screen. *Now* navigate to `Android/data/com.unciv.app/files/mods`

When loading a mod, it needs to be in its own folder in `/mods` - this is how you will work when you're editing your mod.

## Other

You can add an image that will be displayed to users in the mod management screen by adding a "preview.jpg" _or_ "preview.png" file.

Existing mods can be found [here](https://github.com/topics/unciv-mod)!

A list of uniques and how to use them can be found [here](Unique-parameters.md)!

Some images don't exist at all in the base game, but can be added in mods. For more info, see [Audiovisual Mods](3-Images-and-Audio.md).
