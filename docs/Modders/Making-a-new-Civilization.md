# Making a new Civilization

So you want to add your favorite civilization?

There are a few steps required, so we'll walk you through them!

## Fill in your Nation info

Each civ has some basic information - what the civ name is, the leader's name, colors and city names.

In addition, each civ has flavor text when declaring war, intoduction etc.

All of these need to be filled in in [Nations.json](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/Nations.json)

## Get your Civ icon

Each civ has an icon, like the wreath for Rome, for instant identification.

All of these icons are white on a transparent background, and are 100x100 pixels - see [icon considerations](#icon-considerations) for details

You'll need to put your icon in the [NationIcons folder](https://github.com/yairm210/Unciv/tree/master/android/Images.NationIcons/NationIcons).

Same as with the nation name and leader name, the unique ability should also be put in the Nations translation file for bonus points =)

Congrats, your Civ is now fully playable!

But apart from the flavor, they are boring gameplay-wise, so now we need to add unique abilities!

## Adding unique units

Units in general are added in the [Units.json](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/Units.json) file, with an icon in the [UnitIcons](https://github.com/yairm210/Unciv/tree/master/android/Images.Construction/UnitIcons) folder.

The icon must be 200x200 pixels, white on transparent background - see [icon considerations](#icon-considerations) for details

Remember that these are unique units, so search for an existing unique unit to see how they replace their regular counterparts!

## Adding unique buildings

Same as the units - info is in the [Buildings.json](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20&%20Kings/Buildings.json) file and icons in the [BuildingIcons](https://github.com/yairm210/Unciv/tree/master/android/Images.Construction/BuildingIcons) folder, same rules for the icons apply (200x200 pixels, icon considerations)

## Civ Unique

Check out our [list of uniques](Unique-parameters.md) to see all the cool special effects you can add to your civilization!

## Icon considerations

ALL icons must be legally acceptable, meaning they either come from from open sources or you act according to their licence (for Creative Commons, for instance, you have to specify the source and the creator).

Icons directly from the base game belong to Firaxis, so I'm not sure we're legally allowed to use them - please use other sources!

One source I use constantly is [The Noun Project](https://thenounproject.com) - everything there is Creative Commons or open, so they can all be used!

Credits for icons should go in the [Credits](./Credits.md) page.
