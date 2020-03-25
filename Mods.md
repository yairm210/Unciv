Everyone has that thing they wish could be in the game.
Unfortunately, the game only understands code, so mods are our way to give a degree of freedom to those of us who don't code.

Mods currently only work in Desktop.

Mods can *add or replace* basic game definitions, such as units, nations, buildings, improvements, resources and terrains.
Games loaded with these mods will function according to the mod definition.

There are a couple of things that this means:
- The game only knows how to recognize existing definitions, so you can't add *new* unique abilities to nations/units/buildings/etc, only to play around with existing ones
- You can't, as of yet, change e.g. the tech tree or policy branches, since you can only add and not remove definitions.

Mods are located in a `/mods` directory, on Desktop that should be next to your .jar file.
Each folder in the /mods directory is a different mod.

Mods have 2 subfolders:
- jsons - here you should put files that alter the data of game objects, the order of the files is as in https://github.com/yairm210/Unciv/tree/master/android/assets/jsons
- Images - here you should put game images, as in https://github.com/yairm210/Unciv/tree/master/android/Images
These images are built (at runtime) into a single image with an 'altas', so if you see "game.atlas" and "game.png" files being generated, now you know what for

For an example, you can refer to https://github.com/yairm210/Unciv-mod-example - just download the ExampleIncaMod and put it in a /mods folder next to the jar, run Unciv, start a new game, and you'll be able to enable the mod, which will allow to you pick Inca as a playable civilization!

If you want to add a new civilization as a mod, you should check out https://github.com/yairm210/Unciv/wiki/Making-a-new-Civilization to see what's required, or see the example Inca mod =)

There is now a [list of mods](https://docs.google.com/spreadsheets/d/1043Ng9ukrL3y8MUXBVl7-C9JsQGnBi5R5mkmS2l7FFg/edit#gid=0)!