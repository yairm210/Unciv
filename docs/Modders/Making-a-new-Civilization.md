# 'My first mod' - Making a new Civilization

By the end of this tutorial, you should have a working, generally-available mod that adds a new Civilization to the game

## Create your repository

- Create a [Github account](https://github.com/join), if you don't already have one
- Go to the [mod example](https://github.com/yairm210/Unciv-mod-example)
- Click the green `Use this template` button - `Create a new repository`
- Choose your repository name and click `Create repository from template` (keep setting on 'public'!)
- Your new repository is now available!

## Fill in your Nation info

Each civ has some basic information - what the civ name is, the leader's name, colors and city names.

In addition, each civ has flavor text when declaring war, intoduction etc.

All of these need to be filled in in `jsons/Nations.json` file - see [here](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/Nations.json) for the base game file for more examples

## Get your Civ icon

Each civ has an icon, like the wreath for Rome, for instant identification.

All of these icons are white on a transparent background, and are 100x100 pixels - see [icon considerations](#icon-considerations) for details

You'll need to put your icon in the `Images/NationIcons` folder - you can navigate there and click `Add file - Create a new file` (top-right corner)

## Test it out!

- Open Unciv
- Click 'Mods' - 'Download mod from URL'
- Copy-paste your repository's URL to the textbox, and click 'Download'
- Exit the mod screen, and create a new game, selecting your mod - which will be under 'Extension mods' on the left

Congrats, your Civ is now fully playable!

!!! note

    You currently won't see any images from this mod, since it has no texture atlas - see [here](./Images-and-Audio#images-and-the-texture-atlas) for more details
    If you're on Desktop, you can restart Unciv to generate this atlas and see the images

But this nation's abilities are exactly those of the base mod. To make it truly unique, we'll need to change some Uniques ;)

## Adding unique units

Units are defined in the `jsons/Units.json` - for the base game file, see [here](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/Units.json) file, with an icon in the [UnitIcons](https://github.com/yairm210/Unciv/tree/master/android/Images.Construction/UnitIcons) folder.

The icons must be 200x200 pixels, white on transparent background - see [icon considerations](#icon-considerations) for details - and go in the `Images/UnitIcons` folder

Remember that these are unique units, so search for an existing unique unit to see how they replace their regular counterparts!

## Adding unique buildings

Same as the units - info is in `jsons/Buildings.json` - for the base game file, see [Buildings.json](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/Buildings.json) file and icons in the [BuildingIcons](https://github.com/yairm210/Unciv/tree/master/android/Images.Construction/BuildingIcons) folder, same rules for the icons apply (200x200 pixels, icon considerations)

Icons go in `Images/BuildingIcons`

## Civ Unique

Check out our [list of uniques](uniques.md) to see all the cool special effects you can add to your civilization!

## Make it searchable!

To list your mod in the Unciv Mods screen:

- Open your repository
- Click the gear icon, to the right of the 'About' label (right side, top)
- Under 'Topics', add 'unciv-mod'
- 'Save changes'

Congrats, your mod will now be shown in the mods page!

The more stars your repo has, the higher towards the top it will appear, so start gaining fans :D

## Icon considerations

ALL icons must be legally acceptable, meaning they either come from from open sources or you act according to their licence (for Creative Commons, for instance, you have to specify the source and the creator).

Icons directly from the base game belong to Firaxis, so I'm not sure we're legally allowed to use them - please use other sources!

One source I use constantly is [The Noun Project](https://thenounproject.com) - everything there is Creative Commons or open, so they can all be used!

Credits for icons should go in a `Credits.md` page.

## What's next?

You have a working mod, now it's time to go wild!

- Add the [atlas files](Images-and-Audio.md#images-and-the-texture-atlas) to your repo so your users get images
- Install Git locally, so you can change your files on your device and have those changes reflected in your repository
- Expand the abilities of your civ by adding new [uniques](uniques.md)
- Add new civs, buildings or units
- Expand into other game objects by exploring the rest of the [mod file structure](Mod-file-structure/1-Overview.md)
- Try creating a base ruleset [from this template](https://github.com/yairm210/Unciv-minimal-base-ruleset)
