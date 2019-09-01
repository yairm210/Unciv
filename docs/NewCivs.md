# Making a new Civilization

So you want to add your favorite civilization?

There are a few steps requires, so we'll walk you through them!

## Fill in your Nation info

Each civ has some basic information - what the civ name is, the leader's name, colors and city names.

In addition, each civ has flavor text when declaring war, intoduction etc.

All of these need to be filled in in (Nations.json)[android/assets/jsons/Nations.json]

Adding your Civ and leader names in the [Nations translation file](android/assets/jsons/Translations/Diplomacy%2CTrade%2CNations.json)
will notify translators that they should translate them =)

## Get your Civ icon

Each civ has an icon, like the wreath for Rome, for instant identification.

All of these icons are white on a transparent background, and are 100x100 pixels.

You'll need to put your icon in the (NationIcons folder)[android/Images/NationIcons].

Same as with the nation name and leader name, the unique ability should also be put in the Nations translation file for bonus points =)


Congrats, your Civ is now fully playable!

But apart from the flavor, they are boring gameplay-wise, so now we need to add unique abilities!

## Adding unique units

Units in general are added in the (Units.json)[android/assets/jsons/Units.json] file, with an icon in the
 (UnitIcons)[android/Images/UnitIcons] folder.

The icon must be 200x200 pixels, white on transparent background.

Remember that these are unique units, so search for an existing unique unit to see how they replace their regular counterparts!

Again, (translation file)[android/assets/jsons/Translations/Units%2CPromotions.json] for bonus points!

## Adding unique buildings

Same as the units - info is in the (Buildings.json)[android/assets/jsons/Buildings.json] file 
and icons in the (BuildingIcons)[android/Images/BuildingIcons] folder, 
same rules for the icons apply.

Again, (translation file)[android/assets/jsons/Translations/Buildings.json] for bonus points!

## Civ Unique

All Civ uniques require touching actual code - you can try it if ou feel you're up to it, but if not,
 send me an email to yairm210@hotmail.com (if you've finished all of the above) and I'll be happy to lend you a hand!