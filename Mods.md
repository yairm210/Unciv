## What's this about?

Everyone has that thing they wish could be in the game.
Unfortunately, the game only understands code, so mods are our way to give a degree of freedom to those of us who don't code.

Mods can *add, replace and remove* basic game definitions, such as units, nations, buildings, improvements, resources and terrains.
Games loaded with these mods will function according to the mod definition.

The game only knows how to recognize existing definitions, so you can't add *new* unique abilities to nations/units/buildings/etc, only play around with existing ones

There are two kinds of mods:

- Extension mods - these add new nations/units/buildings/resources to a base ruleset - can be either to the default ruleset, or to a base ruleset mod. Easy to do and probably the better place to get started.

- Base Ruleset mods - these replace the entire existing ruleset - tech tree, units, policies, nations etc - to give an entirely different experience than the base game. These generally require quite a bit of work, but give a whole new experience, and so are the most popular.

Creating and editing mods from your phone is NOT RECOMMENDED - it's *much easier* using a desktop device!

## Mod components

Mods are located in a `/mods` directory, on Desktop that should be next to your .jar file.

Mods have 2 subfolders:
- jsons - here you should put files that alter the data of game objects, the order of the files is as in [the base json files](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons)
- Images - here you should put game images, as in [the base image files](https://github.com/yairm210/Unciv/tree/master/android/Images)
These images are built (at runtime) into a single image with an 'altas', so if you see "game.atlas" and "game.png" files being generated, now you know what for.

In order to remove objects from the game, you'll need to create a ModOptions file in the `/jsons` subfolder - there's an example [here](https://github.com/yairm210/Unciv-mod-example/blob/master/Removing%20Things/jsons/ModOptions.json)

In a base ruleset mod, ALL the original objects are removed - this is done by adding a `"isBaseRuleset":true` configuration to your modOptions file, [like so](https://github.com/k4zoo/Civilization-6-Mod/blob/master/jsons/ModOptions.json)

For an example, you can refer to [the example mod](https://github.com/yairm210/Unciv-mod-example) - just download the Example-Aliens-Mod and put it in a `/mods` folder next to the jar, run Unciv, start a new game, and you'll be able to enable the mod, which will allow to you pick Aliens as a playable civilization!

If you want to add a new civilization as a mod, you should check out [the Civ making instructions](https://github.com/yairm210/Unciv/wiki/Making-a-new-Civilization) to see what's required, or see the example Aliens mod =)

### Adding maps to mods

You can also add maps to mods, so they'll be available to players who download your mod.

A mod can also be maps-only, if all you want to do is share your maps.

When you've finished making your map in the Map Editor, save it, and it will be in the `/maps` folder of your game.

Copy it to a `/maps` folder in your *mod*, and you're done!

## Getting your mod out there

In order to make your mod downloadable by anyone, you need to create a Github repository (instructions [here](https://docs.github.com/en/github/getting-started-with-github/create-a-repo))

**When creating the repository, it's important that the main branch is called "master", not "main"**

The Images and jsons folders need to be in the root directory of the repo - see [here](https://github.com/yairm210/Unciv-IV-mod) for example.

You can then manually download the mod from within the Mod Manager in Unciv:

- From Unciv's main screen, click "Mods"
- Click "Download mod from URL", and enter the location of your Github page
- The game will automatically download and extract your mod, and it'll be ready to use!

Once you've tested that your mod CAN be downloaded, and that it works well once downloaded, you're ready for the final stage - GETTING IT TO THE USERS AUTOMATICALLY.

In order to do this, all you need to do is:

- Go to your Github page
- Click the gear icon next to the About (top-right part of the page)
- In 'Topics', add "unciv-mod"

When you open your app, it will query Github's [list of repos with that topic](https://github.com/topics/unciv-mod), and now YOUR repo will appear there!

## I have the mod, now what?

The primary use of mods is to add them when starting a new game, or configuring a map. This will mean that both the ruleset of the mod, and the images, will be in use for that specific game/map.

For mods which are primarily visual, there is a second use - through the mod manager, you can enable them as **permanent visual mods**. This means that the images from the mod will replace the original images everywhere in the game.

## Mod location for manual loading of mods

In general, you should never be manually-loading your mods - not only is this clunky, it's also more error-prone. Unless you have a very specific use-case, you probably shouldn't be doing this.

In Android, they should go into the `Android/data/com.unciv.app/files/mods` directory.

In Chromebook, go to "Play files", should be on the sidebar on the left side of the window under "My files".
Click the 3 vertical dots on the top right-hand corner of the window below the "X".
If the option "Show all Play folders" does not have a check next to it click it. You should see some new files that appear on your screen.
*Now* navigate to `Android/data/com.unciv.app/files/mods`

When loading a mod, it needs to be in its own folder in `/mods` - this is how you will work when you're editing your mod.

## Religion

Religion is a work in progress, and doesn't exist in the base game, but can be added into mods by adding a Beliefs.json file in the json files.

Currently the only implemented mechanic is gathering faith and getting a Pantheon belief.

Of course, you'll need to add some way for civs to gain faith in order for the Pantheon choice!

[Here](https://github.com/yairm210/unciv-religion-mod-example) is an example of a mod that adds several Pantheon beliefs, and adds the Shrine building to allow Faith accumulation.

## Other

Existing mods can be found [here](https://github.com/topics/unciv-mod)!

A list of uniques and how to use them can be found [here](uniques)!

Some images don't exist at all in the base game, but can be added in mods. For example, [here](https://github.com/yairm210/Unciv-leader-portrait-mod-example) is an example of how to add leader portraits to mods, which can compliment the base game.