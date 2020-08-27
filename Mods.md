## What's this about?

Everyone has that thing they wish could be in the game.
Unfortunately, the game only understands code, so mods are our way to give a degree of freedom to those of us who don't code.

Mods can *add, replace and remove* basic game definitions, such as units, nations, buildings, improvements, resources and terrains.
Games loaded with these mods will function according to the mod definition.

The game only knows how to recognize existing definitions, so you can't add *new* unique abilities to nations/units/buildings/etc, only to play around with existing ones

## Mod location

Mods are located in a `/mods` directory, on Desktop that should be next to your .jar file.

In Android, they should go into the `Android/data/com.unciv.app/files/mods` directory.

In Chromebook, go to "Play files", should be on the sidebar on the left side of the window under "My files".
Click the 3 vertical dots on the top right-hand corner of the window below the "X".
If the option "Show all Play folders" does not have a check next to it click it. You should see some new files that appear on your screen.
*Now* navigate to `Android/data/com.unciv.app/files/mods`

When loading a mod, it needs to be in its own folder in `/mods` - this is how you will work when you're editing your mod

## Mod components

Mods have 2 subfolders:
- jsons - here you should put files that alter the data of game objects, the order of the files is as in [the base json files](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons)
- Images - here you should put game images, as in [the base image files](https://github.com/yairm210/Unciv/tree/master/android/Images)
These images are built (at runtime) into a single image with an 'altas', so if you see "game.atlas" and "game.png" files being generated, now you know what for.

In order to remove objects from the game, you'll need to create a ModOptions file in the `/jsons` subfolder - there's an example [here](https://github.com/yairm210/Unciv-mod-example/blob/master/Removing%20Things/jsons/ModOptions.json)

For an example, you can refer to [the example mod](https://github.com/yairm210/Unciv-mod-example) - just download the Example-Aliens-Mod and put it in a `/mods` folder next to the jar, run Unciv, start a new game, and you'll be able to enable the mod, which will allow to you pick Aliens as a playable civilization!

If you want to add a new civilization as a mod, you should check out [the Civ making instructions](https://github.com/yairm210/Unciv/wiki/Making-a-new-Civilization) to see what's required, or see the example Aliens mod =)

### Adding maps to mods

You can also add maps to mods, so they'll be available to players who download your mod.

A mod can also be maps-only, if all you want to do is share your maps.

When you've finished making your map in the Map Editor, save it, and it will be in the `/maps` folder of your game.

Copy it to a `/maps` folder in your *mod*, and you're done!

## Getting your mod out there

There are 2 main venues for disseminating mods.

One is our Discord server, which has a dedicated channel.

The Brand New way of doing it is by putting your mod in a Github repository, and then users can download your mod directly to their phone!

The Images and jsons folders need to be in the root directory of the repo - see [here](https://github.com/yairm210/Unciv-IV-mod) for example.

Once you've tested that your mod CAN be downloaded, and that it works well once downloaded, you're ready for the final stage - GETTING IT TO THE USERS AUTOMATICALLY.

In order to do this, all you need to do is:

- Go to your Github page
- Click the gear icon next to the About (top-right part of the page)
- In 'Topics', add "unciv-mod"

When you open your app, it will query Github's list of repos with that topic, and now YOUR repo will appear there!

## Downloading mods from within the game

- From within an existing game, open Menu - Options and enable "Experimental mod manager".
- Return to the main menu, click "Mods"
- You should now see a list of your mods and a "Download mod" button.
- Click "Download mod", enter the location of your Github page
- The game will automatically download and extract your mod, and it'll be ready to use!

## Other

There is now a [list of mods](https://docs.google.com/spreadsheets/d/1043Ng9ukrL3y8MUXBVl7-C9JsQGnBi5R5mkmS2l7FFg/edit#gid=0)!