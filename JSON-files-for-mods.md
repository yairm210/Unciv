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
        "promotions": ["Shock I"]
    }
]
```
This file contains two unit objects, one for a warrior and one for a spearman. These objects have different attributes, in this case "name" and "cost". All these attributes have a certain type, a string (text) for its name, an integer for its cost or a list for its promotions.

# Information on JSON files used in the game

## Eras.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Eras.json)

This file should contain all the era's you use in your mod. Due to this file being recently added, it is not required, yet it will be in the future.

Each era can have the following attributes:
| attribute | Type | optional or not | notes |
| --------- | ---- | --------------- | ----- |
| name | String | required | Name of the era |
| researchAgreementCost | Integer (≥0) | defaults to 300 | Cost of research agreements were the most technologically advanced civ is in this era |
| iconRGB | List of 3 Integers | defaults to none | RGB color that icons for technologies of this era should have in the Tech screen |
| startingSettlerCount | Integer (≥0) | defaults to 1 | Amount of settler units that should be spawned when starting a game in this era |
| startingSettlerUnit | String | defaults to "Settler" | Name of the unit that should be used for the previous field |
| startingWokerCount | Integer (≥0) | defaults to 0 | Amount of worker units that should be spawned when starting a game in this era |
| startingWorkerUnit | String | defaults to "Worker" | Name of the unit that should be used for the previous field |
| startingMilitaryUnitCount | Integer (≥0) | defaults to 1 | Amount of military units that should be spawned when starting a game in this era |
| startingMilitaryUnit | String | defaults to "Warrior" | Name of the unit that should be used for the previous field |
| startingGold | Integer (≥0) | defaults to 0 | Amount of gold each civ should receive when starting a game in this era |
| startingCulture | Integer (≥0) | defaults to 0 | Amount of culture each civ should receive when starting a game in this era |
| settlerPopulation | Integer (>0) | defaults to 1 | Default amount of population each city should have when settled when starting a game in this era |
| settlerBuildings | List of Strings | defaults to none | Buildings that should automatically be built whenever a city is settled when starting a game in this era |
| startingObsoleteWonders | List of Strings | defaults to none | Wonders (and technically buildings) that should be impossible to built when starting a game in this era. Used in the base game to remove all wonders older than 2 era's |




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
| requiredTech | String | defaults to none | The tech required to build this unit. Should be in Techs.json |
| obsoleteTech | String | defaults to none | After researching this tech, the unit can no longer be build. Should be in Techs.json |
| requiredResource | String | defaults to none | Resource that is consumed by building this unit. Should be in TileResources.json |
| upgradesTo | String | defaults to none | Unit that this unit can upgrade to when it is available. Should be in Units.json |
| replaces | String | defaults to none | If this unit is unique to a nation, this is the unit it replaces. Should be in Units.json |
| uniqueTo | String | defaults to none | The nation that this unit is unique to. Should be in Nations.json |
| hurryCostModifier | Integer | defaults to 0 | If this unit is bought for gold/faith, it's price is increased by so much percent |
| promotions | List of Strings | defaults to none | A list of all the promotions the unit automatically receives upon being built. Each promotion should be in UnitPromotions.json |
| uniques | List of Strings | defaults to none | A list of the unique abilities this unit has. A list of almost all uniques can be found [here](../Uniques#unit-uniques) |
| replacementTextForUniques | String | defaults to none | If provided, this will be displayed instead of the list of uniques. Can be used for better formatting. |
| attackSound | String | defaults to none | The sound that is to be played when this unit attacks. For possible values, see [sounds](#Sounds)


## techs.json

Technologies can have the following attributes:
- name: String - The name of the technology
- cost: Integer - The amount of science required to research this tech
- prerequisites: List of strings - A list of the names of techs that are prerequisites of this tech. Only direct prerequisites are necessary.

## Sounds
Possible values are below. The sounds themselves can be found [here](https://github.com/yairm210/Unciv/tree/master/android/assets/sounds).

arrow, bombard, chimes, choir, click, coin, construction, fortify, gdrAttack, horse, machinegun, metalhit, nonmetalhit, nuke, paper, policy, promote, setup, shot, slider, throw, upgrade, whoosh.

I'll work more on this later