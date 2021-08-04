This page is a work in progress. Information it contains may be incomplete.


The JSON files that make up mods can have many different fields, and as not all are used in the base game, this wiki page will contain the full information of each. It will also give a short explanation of the syntax of JSON files.

# General Overview of JSON files

Almost all JSON files start with a "[" and end with a "]". In between these are different objects of the type you are describing, each of which is contained between a "{" and a "}". For example, a very simple units.json may look like:
```
[
    {
        "name": "Warrior",
        "cost": 16
    },
    {
        "name": "Spearman",
        "cost": 24,
        "promotions": ["Shock I", "Drill I"]
    }
]
```
This file contains two unit objects, one for a warrior and one for a spearman. These objects have different attributes, in this case "name", "cost" and "promotions". All these attributes have a certain type, a String (text) for "name", an Integer for "cost" and a List of Strings for "promotions".

There are different types of attributes:
| type | notes |
| --------- | ----- |
| String | A word or sentence. Should be between double quotes (") |
| Integer | A number. Can be both positive or negative. Should **not** be between quotes |
| Boolean | A value that can either be 'true' or 'false'. Should **not** be between quotes |
| List of [type] | If multiple values could apply (such as with the promotions above), they should be put inside a list. Each element of the list should be written like a normal attribute, seperated by comma's, and enclosed between square braces. E.g.: ["Shock I", "Shock II"] or [1, 2, 3]. |
| Object | The most complicated type of attribute. An object is comprised of multiple attributes, each of which again has a type. These attributes have a key (the part before the ":") and a value (the part behind it). For an example, see below. |

Example of a Buildings.json adding a new "Cultural Library" building which gives +50% science and +50% culture:
```
[
    {
        "name": "Cultural Library"
        "percentStatBonus" : {"science": 50, "culture": 50}
    }
]
```
The keys in this example are "science" and "culture", and both have the value "50".

In some sense you can see from these types that JSON files themselves are actually a list of objects, each describing a single tech, unit or something else.


# Information on JSON files used in the game

Many parts of Unciv are moddable, and for each there is a seperate json file. There is a json file for buildings, for units, for promotions units can have, for technologies, etc. The different new buildings or units you define can also have lots of different attributes, though not all are required. Below are tables documenting all the different attributes everything can have. Only the attributes which are noted to be 'required' must be provided. All others have a default value that will be used when it is omitted.



## Buildings.json
[link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Buildings.json)

This file should contain all the buildings and wonders you want to use in your mod.

Each building can have the following attributes:
| attribute | Type | Optional or not | notes |
| --------- | ---- | --------------- | ----- |
| name | String | required | Name of the building |
| cost | Integer (≥0) | defaults to 0 | Amount of production required to build the building |
| food | Integer | defaults to 0 | Food produced by the building |
| production | Integer | defaults to 0 | Production produced by the building |
| gold | Integer | defaults to 0 | etc. |
| happiness | Integer | defaults to 0 | |
| culture | Integer | defaults to 0 | |
| science | Integer | defaults to 0 | |
| faith | Integer | defaults to 0 | |
| maintenance | Integer (≥0) | defaults to 0 | Maintenance cost of the building |
| isWonder | Boolean | defaults to false | Whether this building is a global wonder |
| isNationalWonder | Boolean | defaults to false | Whether this building is a national wonder |
| requiredBuilding | String | defaults to none | A building that has to be built before this building can be built. Must be in [Buildings.json](Buildings.json) |
| requiredBuildingInAllCities | String | defaults to none | A building that has to be built in all cities before this building can be built. Must be in [Buildings.json](Buildings.json) |
| cannotBeBuiltWith | String | defaults to none | The building [cannotBeBuiltWith] and this building cannot exist in the same city together. Should be in [Buildings.json](Buildings.json) |
| providesFreeBuilding | String | defaults to none | When the building is built, [providesFreeBuilding] is also automatically added to the city |
| requiredTech | String | defaults to none | The tech that should be researched before this building may be built. Must be in Techs.json |
| requiredResource | String | defaults to none | The resource that is consumed when building this building. Must be in TileResources.json |
| requiredNearbyImprovedResources | List of Strings | defaults to none | The building can only be built if any of the resources in this list are within the borders of this city and have been improved. Each resource must be in TileResources.json |
| replaces | String | defaults to none | The name of a building that should be replaced by this building. Must be in [Buildings.json](Buildings.json) |
| uniqueTo | String | defaults to none | If supplied, only the nation with this name can build this building. Must be in Nations.json |
| xpForNewUnits | Integer | defaults to 0 | XP granted automatically to units built in this city |
| cityStrength | Integer | defaults to 0 | Strength bonus the city in which this building is built receives |
| cityHealth | Integer | defaults to 0 | Health bonus the city in which this building is built receives |
| hurryCostModifier | Integer | defaults to 0 | When this building is bought using gold or faith, the price is increased by this much percent |
| quote | String | defaults to none | If this building is a (national) wonder, this string will be shown on the completion popup |
| uniques | List of Strings | defaults to none | List of unique abilities this building has. Most of these can be found [here](https://github.com/yairm210/Unciv/wiki/Uniques#buildings-only) |
| replacementTextForUniques | String | defaults to none | If provided, this string will be shown instead of all of the uniques |
| percentStatBonus | Object | defaults to none | Percentual bonus for stats provided by the building. Valid keys are the names of stats (production, gold, science, etc.), valid values are Integers (≥0) |
| greatPersonPoints | Object | defaults to none | How many great person points for each type will be generated per turn. Valid keys are the stat names (production, gold, science, etc.), valid values are Integers (≥0) |
| specialistSlots | Object | defaults to none | Specialist slots provided by this building. Valid keys are the names of specialists (as defined in [Specialists.json](Specialists.json)), valid values are Integers, the amount of slots provided for this specialist |




## Eras.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Eras.json)

This file should contain all the era's you want to use in your mod. Due to this file being recently added, it is not required, yet it will be in the future.

Each era can have the following attributes:
| attribute | Type | optional or not | notes |
| --------- | ---- | --------------- | ----- |
| name | String | required | Name of the era |
| researchAgreementCost | Integer (≥0) | defaults to 300 | Cost of research agreements were the most technologically advanced civ is in this era |
| iconRGB | List of 3 Integers | defaults to [255,255,255] | RGB color that icons for technologies of this era should have in the Tech screen |
| startingSettlerCount | Integer (≥0) | defaults to 1 | Amount of settler units that should be spawned when starting a game in this era |
| startingSettlerUnit | String | defaults to "Settler" | Name of the unit that should be used for the previous field. Must be in [Units.json](Units.json) |
| startingWokerCount | Integer (≥0) | defaults to 0 | Amount of worker units that should be spawned when starting a game in this era |
| startingWorkerUnit | String | defaults to "Worker" | Name of the unit that should be used for the previous field. Must be in [Units.json ](Units.json) |
| startingMilitaryUnitCount | Integer (≥0) | defaults to 1 | Amount of military units that should be spawned when starting a game in this era |
| startingMilitaryUnit | String | defaults to "Warrior" | Name of the unit that should be used for the previous field. Must be in [Units.json ](Units.json)|
| startingGold | Integer (≥0) | defaults to 0 | Amount of gold each civ should receive when starting a game in this era |
| startingCulture | Integer (≥0) | defaults to 0 | Amount of culture each civ should receive when starting a game in this era |
| settlerPopulation | Integer (>0) | defaults to 1 | Default amount of population each city should have when settled when starting a game in this era |
| settlerBuildings | List of Strings | defaults to none | Buildings that should automatically be built whenever a city is settled when starting a game in this era |
| startingObsoleteWonders | List of Strings | defaults to none | Wonders (and technically buildings) that should be impossible to built when starting a game in this era. Used in the base game to remove all wonders older than 2 era's |

## Specialists.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Specialists.json)

This file should contain a list of all possible specialists that you want in your mod.

Each specialist can have the following attributes:
| attribute | type | optional or not | notes |
| --------- | ---- | --------------- | ----- |
| name | String | required | Name of the specialist |
| food | Integer | defaults to 0 | Amount of food produced by this specialist |
| production | Integer | defaults to 0 | Amount of production produced by this specialist |
| gold | Integer | defaults to 0 | etc. |
| culture | Integer | defaults to 0 | |
| science | Integer | defaults to 0 |
| faith | Integer | defaults to 0 |
| color | List of 3 Integers | required | Color of the image for this specialist |
| greatPersonPoints | Object | defaults to none | Great person points generated by this specialist. Valid keys are the stat names (production, gold, science, etc.), valid values are Integers (≥0) | 




## Units.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Units.json)

This file should contain a list of all the units, both military and civilian, that you want to use in your mod.

Each unit can have the following attributes:
| attribute | Type | optional or not | notes |
| --------- | ---- | -------- | ----- |
| name | String | required | The name of the units (required) |
| unitType | String | required | The type of the unit: Civilian, Melee, Ranged, Scout, Mounted, Armor, Siege, WaterCivilian, WaterMelee, WaterRanged, WaterSubmarine, WaterAircraftCarrier, Fighter, Bomber, AtomicBomber, or Missile. This decides many of the properties of the unit. |
| cost | Integer (≥0) | defaults to 0 | The amount of production required to build this unit |
| movement | Integer (≥0) | defaults to 0 | The amount of movement points the unit has by default |
| strength | Integer (≥0) | defaults to 0 | The melee attack and defensive strength of the unit |
| rangedStrength | Integer (≥0) | defaults to 0 | The ranged attack strength of the unit |
| range | Integer (≥0) | defaults to 2 | The default range from which ranged attacks can be preformed |
| interceptRange | Integer (≥0) | defaults to 0 | Air units attacking within in this range will be intercepted |
| requiredTech | String | defaults to none | The tech required to build this unit. Must be in Techs.json |
| obsoleteTech | String | defaults to none | After researching this tech, the unit can no longer be build. Must be in Techs.json |
| requiredResource | String | defaults to none | Resource that is consumed by building this unit. Must be in TileResources.json |
| upgradesTo | String | defaults to none | Unit that this unit can upgrade to when it is available. Must be in Units.json |
| replaces | String | defaults to none | If this unit is unique to a nation, this is the unit it replaces. Must be in [Units.json](Units.json) |
| uniqueTo | String | defaults to none | The nation that this unit is unique to. Must be in Nations.json |
| hurryCostModifier | Integer | defaults to 0 | If this unit is bought for gold/faith, it's price is increased by so much percent |
| promotions | List of Strings | defaults to none | A list of all the promotions the unit automatically receives upon being built. Each promotion must be in UnitPromotions.json |
| uniques | List of Strings | defaults to none | A list of the unique abilities this unit has. A list of almost all uniques can be found [here](../Uniques#unit-uniques) |
| replacementTextForUniques | String | defaults to none | If provided, this will be displayed instead of the list of uniques. Can be used for better formatting. |
| attackSound | String | defaults to none | The sound that is to be played when this unit attacks. For possible values, see [sounds](#Sounds)


## techs.json

Technologies can have the following attributes:
- name: String - The name of the technology
- cost: Integer - The amount of science required to research this tech
- prerequisites: List of strings - A list of the names of techs that are prerequisites of this tech. Only direct prerequisites are necessary.

## Sounds
Standard values are below. The sounds themselves can be found [here](https://github.com/yairm210/Unciv/tree/master/android/assets/sounds).

arrow, artillery, bombard, bombing, cannon, chimes, choir, click, coin, construction, elephant, fortify, gdrAttack, horse, jetgun, machinegun, metalhit, missile, nonmetalhit, nuke, paper, policy, promote, setup, shipguns, shot, slider, swap, tankshot, throw, torpedo, upgrade, whoosh.

Mods can add their own sounds, as long as any new value in attackSound has a corresponding sound file in the mod's sound folder, using one of the formats mp3, ogg or wav (file name extension must match codec used). Remember, names are case sensitive. Small sizes strongly recommended, Unciv's own sounds use 24kHz joint stereo 8-bit VBR at about 50-100kBps.

## Civilopedia text
Any 'thing' defined in json and listed in the Civilopedia can supply extra text, specifically for the Civilopedia. This can be used to explain special considerations better when the automatically generated display is insufficient, or for 'flavour', background stories and the like. Such text can be formatted and linked to other Civilopedia entries, within limits.

An example of the format is:
```json
        "civilopediaText": [
			{"text":"Ancient ruins provide a one-time random bonus when explored"},
			{"separator":true},
			{"text":"This line is red and links to the Scout including icons", "link":"Unit/Scout", "color":"red"},
			{"text":"A big fat header sporting a golden star", "header":1, "starred":true, "color":"#ffeb7f"},
		],
```
List of attributes - note not all combinations are valid:
|attribute|type|description|
|---------|----|-----------|
|`text`|String|Text to display.|
|`link`|String|Create link and icon, format: Category/Name or _external_ link ('http://','https://','mailto:').|
|`icon`|String|Show icon without linking, format: Category/Name.|
|`extraImage`|String|Display an Image instead of text. Can be a path found in a texture atlas or or the name of a png or jpg in the ExtraImages folder.|
|`imageSize`|Float|Width in world units of the [extraImage], height is calculated preserving aspect ratio. Defaults to available width.|
|`header`|Integer|Header level. 1 means double text size and decreases from there.|
|`size`|Integer|Text size, default is 18. Use `size` or `header` but not both.|
|`indent`|Integer|Indent level. 0 means text will follow icons, 1 aligns to the right of all icons, each further step is 30 units.|
|`padding`|Float|Vertical padding between rows, defaults to 5 units.|
|`color`|String|Sets text color, accepts names or 6/3-digit web colors (e.g. #FFA040).|
|`separator`|Boolean|Renders a separator line instead of text. Can be combined only with `color` and `size` (line width, default 2).|
|`starred`|Boolean|Decorates text with a star icon - if set, it receives the `color` instead of the text.|
|`centered`|Boolean|Centers the line (and turns off automatic wrap).|

The lines from json will 'surround' the automatically generated lines such that the latter are inserted just above the first json line carrying a link, if any. If no json lines have links, they will be inserted between the automatic title and the automatic info. This method may, however, change in the future.

## Work in progress
I'll work more on this later