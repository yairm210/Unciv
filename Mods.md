Everyone has that thing they wish could be in the game.
Unfortunately, the game only understands code, so mods are our way to give a degree of freedom to those of us who don't code.

Mods can *add, replace and remove* basic game definitions, such as units, nations, buildings, improvements, resources and terrains.
Games loaded with these mods will function according to the mod definition.

The game only knows how to recognize existing definitions, so you can't add *new* unique abilities to nations/units/buildings/etc, only to play around with existing ones

Mods are located in a `/mods` directory, on Desktop that should be next to your .jar file.

In Android, they should go into the `Android/data/com.unciv.app/files/mods` directory.

In Chromebook, go to "Play files", should be on the sidebar on the left side of the window under "My files".
Click the 3 vertical dots on the top right-hand corner of the window below the "X".
If the option "Show all Play folders" does not have a check next to it click it. You should see some new files that appear on your screen.
*Now* navigate to `Android/data/com.unciv.app/files/mods`

When loading a mod, they can be in 1 of 2 formats:
- A folder in `/mods` - this is how you will work when you're editing your mod
- A zip file in `/mods` - this is an easier way of handling prebuilt mods

Mods have 2 subfolders:
- jsons - here you should put files that alter the data of game objects, the order of the files is as in [the base json files](https://github.com/yairm210/Unciv/tree/master/android/assets/jsons)
- Images - here you should put game images, as in [the base image files](https://github.com/yairm210/Unciv/tree/master/android/Images)
These images are built (at runtime) into a single image with an 'altas', so if you see "game.atlas" and "game.png" files being generated, now you know what for.

In order to remove objects from the game, you'll need to create a ModOptions file in the `/jsons` subfolder - there's an example [here](https://github.com/yairm210/Unciv-mod-example/blob/master/Removing%20Things/jsons/ModOptions.json)

For an example, you can refer to [the example mod](https://github.com/yairm210/Unciv-mod-example) - just download the Example-Aliens-Mod and put it in a `/mods` folder next to the jar, run Unciv, start a new game, and you'll be able to enable the mod, which will allow to you pick Aliens as a playable civilization!

If you want to add a new civilization as a mod, you should check out [the Civ making instructions](https://github.com/yairm210/Unciv/wiki/Making-a-new-Civilization) to see what's required, or see the example Aliens mod =)

There is now a [list of mods](https://docs.google.com/spreadsheets/d/1043Ng9ukrL3y8MUXBVl7-C9JsQGnBi5R5mkmS2l7FFg/edit#gid=0)!