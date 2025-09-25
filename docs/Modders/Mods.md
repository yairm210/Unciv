# Introduction to Mods

## What are mods?

Everyone has that thing they wish could be in the game.
Unfortunately, the game only understands code, so mods are our way to give a degree of freedom to those of us who don't code.

Mods can *add, replace and remove* basic game definitions, such as units, nations, buildings, improvements, resources and terrains.
Games loaded with these mods will function according to the mod definition.

The game only knows how to recognize existing definitions, so you can't add *new* unique abilities to nations/units/buildings/etc, only play around with existing ones

There are three main kinds of mods:

-   **Extension mods** - these add new nations/units/buildings/resources to a base ruleset - can be either to the default ruleset, or to a base ruleset mod. Easy to do and probably the better place to get started - for example, [creating a new Civilization](Making-a-new-Civilization.md)
-   **Base Ruleset mods** - these replace the entire existing ruleset - tech tree, units, policies, nations etc - to give an entirely different experience than the base game. These generally require quite a bit of work, but give a whole new experience, and so are the most popular. [A minimal example can be found here](https://github.com/yairm210/Unciv-minimal-base-ruleset) as a template to build off of ("Use this template" green button in top right, "Create a new repository"). For requirements, see [Requirements](Mod-file-structure/1-Overview.md#requirements-for-base-rulesets)
-   **Ruleset-agnostic mods** - these do not contain any ruleset-related jsons, but instead contain other affects. Audiovisual mods (including tilesets, unitsets, and UI skins) and map mods are in this category.

Creating and editing mods from your phone is NOT RECOMMENDED - it's *much easier* using a desktop device!

## Mod names

Mods need to conform to github repo naming rules, but best stay simple and use only letters, digits, and dashes `-`.
Dashes are _automatically_ converted to spaces for display and use within Unciv.

Many punctuation or extended unicode characters _might_ work, but at best potential users won't find them attractive, at worst we'll refuse support when you run into problems :smiling_imp:

## Mod components

Mods are located in a `/mods` directory, on Desktop that should be next to your .jar file.

Mods typically have 2 subfolders:

-   `jsons` - here you should put files that alter the data of game objects, the order of the files is as in [the base json files](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons). More information on these can be found [here](Mod-file-structure/1-Overview.md)
-   `Images` - here you should put game images, as in [the base image files](https://github.com/yairm210/Unciv/tree/master/android/Images).

In order to remove objects from the game, you'll need to create a ModOptions file in the `/jsons` subfolder - there's an example [here](https://github.com/yairm210/Unciv-mod-example/blob/master/Removing%20Things/jsons/ModOptions.json).

**Base Ruleset Mods** are mods that 'start from scratch' - ALL the original objects are removed, and only the objects of the mod in question are used.

This is done by adding a `"isBaseRuleset":true` configuration to your [modOptions file](Mod-file-structure/5-Miscellaneous-JSON-files.md#modoptionsjson), [like so](https://github.com/k4zoo/Civilization-6-Mod/blob/master/jsons/ModOptions.json).

## Audiovisual components

In addition to changing the rules - or even without doing so - mods can override existing graphics or sounds, or add music tracks. For details, see [Audiovisual Mods](Images-and-Audio.md).

Custom tilesets and unitsets are a subgroup of these - see [Creating a custom tileset](Creating-a-custom-tileset.md) - as are UI skin mods, see [Creating a UI skin](Creating-a-UI-skin.md).

Such mods are candidates for the "Permanent audiovisual mod" switch available on the Mod Management Screen, see [Permanent audiovisual mods](Images-and-Audio.md#permanent-audiovisual-mods).

Images need to be 'packed' before the game can use them, which the desktop version can do for you. Please make sure to read the [Texture atlas](Images-and-Audio.md#images-and-the-texture-atlas) chapter!

## Adding maps to mods

You can also add maps to mods, so they'll be available to players who download your mod.

A mod can also be maps-only, if all you want to do is share your maps.

When you've finished making your map in the Map Editor, save it, and it will be in the `/maps` folder of your game.

Copy it to a `/maps` folder in your mod, and you're done!

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

-   `unciv-mod-rulesets` (for base ruleset mods)
-   `unciv-mod-expansions` (for mods extending vanilla rulesets - please use this, **not** unciv-mod-expansion)
-   `unciv-mod-graphics` (for mods altering graphics - icons, portraits, tilesets)
-   `unciv-mod-audio` (for mods supplying music or modifying sounds)
-   `unciv-mod-maps` (for mods containing maps)
-   `unciv-mod-fun` (for mods mainly tweaking mechanics or other gameplay aspects)
-   `unciv-mod-modsofmods` (for mods extending another mod's ruleset)

When you open Unciv's Mod Manager, it will query Github's [list of repos with that topic](https://github.com/topics/unciv-mod), and now YOUR repo will appear there!
The categories will appear as annotations on the mod buttons, and the user can filter for them. They are not required for the game to use the content - e.g. you can still load maps from mods lacking the unciv-mod-maps topic.
If you want new categories, github will accept any topic, but you'll have to ask the Unciv team to enable them in the game.

If you feel there should be additional topics supported in-game, then the course of action is as follows:

-    You can add topics to your repository as you please, subject to github's terms, but if you whish them to become Unciv-supported they must begin with "unciv-mod-".
-    Once done, you can either:
     - Wait at least one release, check that your topic appeared in [ModCategories.json](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/ModCategories.json), and open a change PR for that file, removing the "hidden" attribute, telling us exactly why that topic would benefit the entire community.
     - Or, open an issue pointing us to your Mod with the new topics, asking us to do the above for you, again telling us why.

## Loading mods from other sources than github

The mod manager has a "Download mod from URL" button. As mod consumer, you can use this to load mods as zip files from sources you trust: Use cases include mods in development, clients not able to connect to github for various reasons (e.g. firewalls, no IPv4 support), or as alternative transport for loading your own mods on mobile devices.

This downloader supports simple redirections, but not hosters requiring authentication or pre-set cookies, or hosters reassembling files in the browser using javascript or other complications - a simple `http get` must suffice.

As mod author, you might need to know a few details how that button works. After all, it will lack some metadata it can normally get from github - e.g. repository name, branch names, release tags. Your storage might be an alternative git platform, but unless we code explicit support, Unciv has no way to reliably determine those. Therefore pay attention to:
- The zip content after unpacking should either contain exactly one subfolder and all the mod's files (and mod folders like `jsons` or `Images`) below that folder, or have all the mod's data directly at the top lefel of the zip content.
- The content should include a majority of files or folders Unciv knows to be part of a mod. Adding more testing, comment, asset-source or similar files/folders than there are actual payload files will result in a "Invalid Mod archive structure" message and your mod won't be accepted. This is counted on the top mod level, so moving all extra files into one subfolder should work when you're nearing that limit. Note that files named "license", "contribute.md", "readme.md" or "credits.md" _do_ count as "good" content.
- The final mod name will be taken from one of three sources: The last part of the download link's path, the content-disposition header the server sends (you can see this as the actual name the file will be saved as when you download the link directly in a browser), or the first subfolder mentioned above. Precedence is subfolder - header - path, with some rarer exceptions (e.g. when the subfolder name is included in the header/path name but more boring, or in some cases typical branch name suffixes are automatically removed).
- Therefore the best you can do is to include your mod content under a single subfolder with the desired mod name (including proper upper/lower casing), or, if your platform makes that difficult, make sure the downloaded name matches what you want to appear in your mod list (replacing blanks with dashes is allowed in all cases and recommended).

## I have the mod, now what?

The primary use of mods is to add them when starting a new game, or configuring a map. This will mean that both the ruleset of the mod, and the images, will be in use for that specific game/map.

For mods which are primarily visual or audio, there is a second use - through the mod manager, you can enable them as **permanent audiovisual mods**. This means that the images and/or sounds from the mod will replace the original media everywhere in the game, and contained music will be available - [see here](Images-and-Audio.md#supply-additional-music).

## Mod location for manual loading of mods

In general, you should never be manually-loading your mods - not only is this clunky, it's also more error-prone. Unless you have a very specific use-case, you probably **shouldn't be doing this**

When loading a mod, it needs to be in its own folder in `/mods` - this is how you will work when you're editing your mod.

In Android, you can copy them into the `Android/data/com.unciv.app/files/mods` directory.

When the app starts, they will be auto-copied into the `/data/data/com.unciv.app/files/mods`, directory, that is inaccessible to users.

In Chromebook, go to "Play files", should be on the sidebar on the left side of the window under "My files". Click the 3 vertical dots on the top right-hand corner of the window below the "X".
If the option "Show all Play folders" does not have a check next to it click it. You should see some new files that appear on your screen. *Now* navigate to `Android/data/com.unciv.app/files/mods`

## Other

You can add an image that will be displayed to users in the mod management screen by adding a "preview.jpg" _or_ "preview.png" file.

Existing mods can be found [here](https://github.com/topics/unciv-mod)!

## What's next?

Now you should try to create your first mod!

We recommend you start off by [adding a new civilization](Making-a-new-Civilization.md) as a mod, to get a hang of the process :)
