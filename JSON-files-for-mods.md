This page is a work in progress. Information it contains may be incomplete.


The JSON files that make up mods can have many different fields, and as not all are used in the base game, this wiki page will contain the full information of each. It will also give a short explanation of the syntax of JSON files.

# Table of Contents
* [General Overview of JSON files](#general-overview-of-json-files)
* Civilization-related JSON files
* * [Beliefs.json](#beliefsjson)
* * [Buildings.json](#buildingsjson)
* * [Nations.json](#nationsjson)
* * [Policies.json](#policiesjson)
* * [Quests.json](#questsjson)
* * [Religions.json](#religionsjson)
* * [Specialists.json](#specialistsjson)
* * [Techs.json](#techsjson)
* Map-related JSON files
* * [(Terrains.json)](#work-in-progress)
* * [(TileImprovements.json)](#work-in-progress)
* * [TileResources.json](#tileresourcesjson)
* * [Ruins.json](#ruinsjson)
* Unit-related JSON files
* * [Units.json](#unitsjson)
* * [UnitPromotions.json](#unitpromotionsjson)
* * [UnitTypes.json](#unittypesjson)
* Miscellaneous JSON files
* * [Difficulties.json](#difficultiesjson)
* * [Eras.json](#erasjson)
* * [ModOptions.json](#modoptionsjson)
* [Stats](#stats)
* [Sounds](#sounds)
* [Civilopedia text](#civilopedia-text)


# General Overview of JSON files

Resources: [json.org](https://www.json.org/), [ISO standard](https://standards.iso.org/ittf/PubliclyAvailableStandards/c071616_ISO_IEC_21778_2017.zip)

Almost all Unciv JSON files start with a "[" and end with a "]". In between these are different objects of the type you are describing, each of which is contained between a "{" and a "}". For example, a very simple units.json may look like:
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

In some sense you can see from these types that JSON files themselves are actually a list of objects, each describing a single building, unit or something else.


# Information on JSON files used in the game

Many parts of Unciv are moddable, and for each there is a seperate json file. There is a json file for buildings, for units, for promotions units can have, for technologies, etc. The different new buildings or units you define can also have lots of different attributes, though not all are required. Below are tables documenting all the different attributes everything can have. Only the attributes which are noted to be 'required' must be provided. All others have a default value that will be used when it is omitted.

## Beliefs.json
[link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Beliefs.json)

This file contains the beliefs that can be chosen for religions in your mod.

Each belief can have the following attributes:
| attribute | Type | Optional or not | notes |
| --------- | ---- | --------------- | ----- |
| name | String | Required | Name of the belief |
| type | String | Required | The type of the belief. Valid values are: "Pantheon" and "Follower". Later "Founder" will be added, but this has not been implemented yet |
| uniques | List of Strings | defaults to none | The unique abilities this belief adds to cities following it. May be chosen from the list of building uniques [here](https://github.com/yairm210/Unciv/wiki/Uniques#buildings-only), as well as the general uniques on that page |
| civilopediaText | List | Default empty | see [civilopediaText chapter](#civilopedia-text) |

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
| requiredTech | String | defaults to none | The tech that should be researched before this building may be built. Must be in [Techs.json](#techsjson) |
| requiredResource | String | defaults to none | The resource that is consumed when building this building. Must be in [TileResources.json](#tileresourcesjson) |
| requiredNearbyImprovedResources | List of Strings | defaults to none | The building can only be built if any of the resources in this list are within the borders of this city and have been improved. Each resource must be in TileResources.json |
| replaces | String | defaults to none | The name of a building that should be replaced by this building. Must be in [Buildings.json](#buildingsjson) |
| uniqueTo | String | defaults to none | If supplied, only the nation with this name can build this building. Must be in [Nations.json](#nationsjson) |
| xpForNewUnits | Integer | defaults to 0 | XP granted automatically to units built in this city |
| cityStrength | Integer | defaults to 0 | Strength bonus the city in which this building is built receives |
| cityHealth | Integer | defaults to 0 | Health bonus the city in which this building is built receives |
| hurryCostModifier | Integer | defaults to 0 | When this building is bought using gold or faith, the price is increased by this much percent |
| quote | String | defaults to none | If this building is a (national) wonder, this string will be shown on the completion popup |
| uniques | List of Strings | defaults to none | List of unique abilities this building has. Most of these can be found [here](../Uniques#buildings-only) |
| replacementTextForUniques | String | defaults to none | If provided, this string will be shown instead of all of the uniques |
| percentStatBonus | Object | defaults to none | Percentual bonus for stats provided by the building. Valid keys are the names of stats (production, gold, science, etc.), valid values are Integers (≥0) |
| greatPersonPoints | Object | defaults to none | How many great person points for each type will be generated per turn. Valid keys are the names of great people (Great Scientist, Great Engineer, etc. .), valid values are Integers (≥0) |
| specialistSlots | Object | defaults to none | Specialist slots provided by this building. Valid keys are the names of specialists (as defined in [Specialists.json](Specialists.json)), valid values are Integers, the amount of slots provided for this specialist |
| civilopediaText | List | Default empty | see [civilopediaText chapter](#civilopedia-text) |


## Difficulties.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Difficulties.json)

This file defines the difficulty levels a player can choose when starting a new game.

Each difficulty level can have the following attributes:
| Attribute | Type | Mandatory | Notes |
| --------- | ---- | ------- | ----- |
| name | String | Required | Name of the difficulty level |
| baseHappiness | Integer | Default 0 |
| extraHappinessPerLuxury | Float | Default 0 |
| researchCostModifier | Float | Default 1 |
| unitCostModifier | Float | Default 1 |
| buildingCostModifier | Float | Default 1 |
| policyCostModifier | Float | Default 1 |
| unhappinessModifier | Float | Default 1 |
| barbarianBonus | Float | Default 0 |
| playerBonusStartingUnits | List of Units | Default empty | Can also be 'Era Starting Unit', maps to `startingMilitaryUnit` of the Eras file. All other units must be in [units.json(#unitsjson)] |
| aiCityGrowthModifier | Float | Default 1 |
| aiUnitCostModifier | Float | Default 1 |
| aiBuildingCostModifier | Float | Default 1 |
| aiWonderCostModifier | Float | Default 1 |
| aiBuildingMaintenanceModifier | Float | Default 1 |
| aiUnitMaintenanceModifier | Float | Default 1 |
| aiFreeTechs | List of Techs | Default empty |
| aiMajorCivBonusStartingUnits | List of Units | Default empty | See above |
| aiCityStateBonusStartingUnits | List of Units | Default empty | See above |
| aiUnhappinessModifier | Float | Default 1 |
| aisExchangeTechs | Boolean | | Unimplemented |
| turnBarbariansCanEnterPlayerTiles | Integer | Default 0 |
| clearBarbarianCampReward | Integer | Default 25 |


## Eras.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Eras.json)

This file should contain all the era's you want to use in your mod. Due to this file being recently added, it is not required, yet it will be in the future.

Each era can have the following attributes:
| attribute | Type | optional or not | notes |
| --------- | ---- | --------------- | ----- |
| name | String | required | Name of the era |
| researchAgreementCost | Integer (≥0) | defaults to 300 | Cost of research agreements were the most technologically advanced civ is in this era |
| iconRGB | List of 3 Integers | defaults to [255,255,255] | RGB color that icons for technologies of this era should have in the Tech screen |
| unitBaseBuyCost | Integer (≥0) | defaults to 200 | Base cost of buying units with Faith, Food, Science or Culture when no other cost is provided |
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


## ModOptions.json
This file is a little different:
- Does not exist in Vanilla ruleset
- Is entirely optional but will be created after downloading a mod

The file can have the following attributes, including the values Unciv sets (no point in a mod author setting those):
| Attribute | Type | Defaults | Notes |
|-----------|------|-----------|-------|
| isBaseRuleset | Boolean | false | Differentiates mods that change the vanilla ruleset or replace it |
| maxXPfromBarbarians | Integer | 30 | ...as the name says... |
| uniques | List | empty | Mod-wide specials, [see here](../Uniques/#modoptions-uniques) |
| techsToRemove | List | empty | List of [Technologies](#techsjson) to remove (isBaseRuleset=false only) |
| buildingsToRemove | List | empty | List of [Buildings or Wonders](#buildingsjson) to remove (isBaseRuleset=false only) |
| unitsToRemove | List | empty | List of [Units](#unitsjson) to remove (isBaseRuleset=false only) |
| nationsToRemove | List | empty | List of [Nations](#nationsjson) to remove (isBaseRuleset=false only) |
| lastUpdated | String | empty | Set automatically after download - Last repository update, not necessarily last content change |
| modUrl | String | empty | Set automatically after download - URL of repository |
| author | String | empty | Set automatically after download - Owner of repository |
| modSize | Integer | empty | Set automatically after download - kB in entire repository, not sum of default branch files |


## Nations.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Nations.json)

This file contains all the nations and city states, including Barbarians and Spectator.

| Attribute | Type | Optional? | Notes |
|-----------|------|-----------|-------|
| name | String | Required |  |
| leaderName | String | Default empty | Omit only for city states! If you want LeaderPortraits, the image file names must match exactly, including case. |
| style | String | Default empty | Modifier appended to pixel unit image names |
| adjective | String | Default empty | Currently unused |
| cityStateType | Enum | Default absent | Distinguishes Major Civilizations from City States (Cultured, Maritime, Mercantile, Militaristic) |
| startBias | List | Default empty | Zero or more of: terrainFilter or "Avoid [terrainFilter]" |
| preferredVictoryType | Enum | Default Neutral | Neutral, Cultural, Diplomatic, Domination or Scientific |
| startIntroPart1 | String | Default empty | Introductory blurb shown to Player on game start... |
| startIntroPart2 | String | Default empty | ... second paragraph. ***NO*** "TBD"!!! Leave empty to skip that alert. |
| declaringWar | String | Default empty | another greeting |
| attacked | String | Default empty | another greeting |
| defeated | String | Default empty | another greeting |
| introduction | String | Default empty | another greeting |
| neutralHello | String | Default empty | another greeting |
| hateHello | String | Default empty | another greeting |
| tradeRequest | String | Default empty | another greeting |
| innerColor | 3x Integer | Default black | R, G, B for outer ring of nation icon |
| outerColor | 3x Integer | Required | R, G, B for inner circle of nation icon |
| uniqueName | String | Default empty | Decorative name for the special characteristic of this Nation |
| uniqueText | String | Default empty | Replacement text for "uniques". If empty, uniques are listed individually. |
| uniques | List | Default empty | Properties of the civilization - see [here](../Uniques#general-uniques) |
| cities | List | Default empty | City names used sequentially for newly founded cities. |
| civilopediaText | List | Default empty | see [civilopediaText chapter](#civilopedia-text) |


## Policies.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Policies.json)

This file lists the available social policies that can be "bought" with culture.

They are organized in 'branches', each branch has an 'opener', one or more 'member' policies, and a 'finisher'. Therefore this file is organized using two levels - branch and member policy. The properties of the 'opener' are defined with the branch level, while the 'finisher' has an entry on the member level which _must_ be named as branch name + " Complete", case sensitive.

Each policy branch can have the following properties:
| Attribute | Type | Optional? | Notes |
|-----------|------|-----------|-------|
| name | String | Required |  |
| era | String | Required | Unlocking era as defined in [Eras.json](#erasjson) |
| uniques | List | Default empty | List of effects, [see here](../Uniques#general-uniques) |
| policies | List | Default empty | List of member policies |

Each member policy can have the following properties:
| Attribute | Type | Optional? | Notes |
|-----------|------|-----------|-------|
| name | String | Required |  |
| row | Integer | Required | Placement in UI, each unit approximately half the icon size |
| column | Integer | Required | Placement in UI, each unit approximately half the icon size |
| requires | List | Default empty | List of prerequisite policy names |
| uniques | List | Default empty | List of effects, [see here](../Uniques#general-uniques) |


## Quests.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Quests.json)

This file contains the Quests that may be given to major Civilizations by City States.

| Attribute | Type | Optional? | Notes |
|-----------|------|-----------|-------|
| name | String | Required | Unique identifier name of the quest, it is also shown |
| description | String | Required | Description of the quest shown to players |
| type | Enum | Default Individual | Individual or Global |
| influece | Float | Default 40 | Influence reward gained on quest completion |
| duration | Integer | Default 0 | Maximum number of turns to complete the quest, 0 if there's no turn limit |
| minimumCivs | Integer | Default 1 | Minimum number of Civs needed to start the quest. It is meaningful only for type = Global |


## Religions.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Religions.json)

This is just a list of Strings specifying all predefined Religion names. Corresponding icons must exist, that's all to it. After all, they're just containers for [Beliefs](#beliefsjson).


## Ruins.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Ruins.json)

This file contains the possible rewards ancient ruins give. It is not required, if omitted, the default file for the game is used, even in baseRuleSet mods.

Each of the objects in the file represents a single reward you can get from ruins. It has the following properties:


| attribute | Type | optional or not | notes |
| --------- | ---- | --------------- | ----- |
| name | String | required | Name of the ruins. Never shown to the user, but they have to be distinct |
| notification | String | required | Notification added to the user when this reward is chosen. If omitted, an empty notification is shown. Some notifications may have parameters, refer to the table below. |
| weight | Integer (≥0) | defaults to 1 | Weight this reward should have. Higher weights result in a higher chance of it being chosen* |
| uniques | List of Strings | defaults to none | [uniques](https://github.com/yairm210/Unciv/wiki/Uniques#one-time-effect) or [uniques](https://github.com/yairm210/Unciv/wiki/Uniques#one-time-effect-units) that will trigger when entering the ruins. If more than 1 unique is added, the notification will be shown multiple times due to a bug. |
| excludedDifficulties | List of Strings | defaults to None | A list of all difficulties on which this reward may _not_ be awarded |


* The exact algorithm for choosing a reward is the following: 
- Create a list of all possible rewards, with rewards with a higher weight appearing multiple times. A reward with weight one will appear once, a reward with weight two will appear twice, etc. 
- Shuffle this list
- Try give rewards starting from the top of the list. If any of the uniques of the rewards is valid in this context, reward it and stop trying more rewards.

### Notifications

Some of the rewards ruins can give will have results that are not deterministic when writing it in the JSON, so creating a good notification for it would be impossible. An example for this would be the "Gain [50]-[100] [Gold]" unique, which will give a random amount of gold. For this reason, we allow some notifications to have parameters, in which values will be filled, such as "You found [goldAmount] gold in the ruins!". All the uniques which have this property can be found below.

| unique | parameters |
| ------ | ---------- |
| Free [] found in the ruins | The name of the unit will be filled in the notification, including unique units of the nation |
| [] population in a random city | The name of the city to which the population is added will be filled in the notification |
| Gain []-[] [] | The exact amount of the stat gained will be filled in the notification |
| [] free random reasearchable Tech(s) from the [] | The notification must have placeholders equal to the number of techs granted this way. Each of the names of these free techs will be filled in the notification |
| Gain enough Faith for a Pantheon | The amount of faith gained is filled in the notification |
| Gain engouh Faith for []% of a Great Prophet | The amount of faith gained is filled in the notifciation |

### Specific uniques

A few uniques can be added to ancient ruin effects to modify when they can be earned. These are:
- "Only available after [amount] turns"
- "Hidden when religion is disabled"
- "Hidden after a great prophet has been earned"

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
| greatPersonPoints | Object | defaults to none | Great person points generated by this specialist. Valid keys are the names of the great person(Great Scientist, Great Merachant, etc.), valid values are Integers (≥0) | 




## Units.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Units.json)

This file should contain a list of all the units, both military and civilian, that you want to use in your mod.

Each unit can have the following attributes:
| attribute | Type | optional or not | notes |
| --------- | ---- | -------- | ----- |
| name | String | required | The name of the units (required) |
| unitType | String | required | The type of the unit. Must be in [UnitTypes.json](UnitTypes.json) |
| cost | Integer (≥0) | defaults to 0 | The amount of production required to build this unit |
| movement | Integer (≥0) | defaults to 0 | The amount of movement points the unit has by default |
| strength | Integer (≥0) | defaults to 0 | The melee attack and defensive strength of the unit. If this and rangedStrength are ommited or 0, the unit will be a civilian |
| rangedStrength | Integer (≥0) | defaults to 0 | The ranged attack strength of the unit. If omitted, the unit cannot ranged attack |
| range | Integer (≥0) | defaults to 2 | The default range from which ranged attacks can be preformed |
| interceptRange | Integer (≥0) | defaults to 0 | Air units attacking within in this range will be intercepted |
| requiredTech | String | defaults to none | The tech required to build this unit. Must be in [Techs.json](#techsjson) |
| obsoleteTech | String | defaults to none | After researching this tech, the unit can no longer be build. Must be in [Techs.json](#techsjson) |
| requiredResource | String | defaults to none | Resource that is consumed by building this unit. Must be in [TileResources.json](#tilereousrcesjson) |
| upgradesTo | String | defaults to none | Unit that this unit can upgrade to when it is available. Must be in [Units.json](#unitsjson) |
| replaces | String | defaults to none | If this unit is unique to a nation, this is the unit it replaces. Must be in [Units.json](#unitsjson)(Units.json) |
| uniqueTo | String | defaults to none | The nation that this unit is unique to. Must be in [Nations.json](#nationsjson) |
| hurryCostModifier | Integer | defaults to 0 | If this unit is bought for gold/faith, it's price is increased by so much percent |
| promotions | List of Strings | defaults to none | A list of all the promotions the unit automatically receives upon being built. Each promotion must be in [UnitPromotions.json](#unitpromotionsjson) |
| uniques | List of Strings | defaults to none | A list of the unique abilities this unit has. A list of almost all uniques can be found [here](../Uniques#unit-uniques) |
| replacementTextForUniques | String | defaults to none | If provided, this will be displayed instead of the list of uniques. Can be used for better formatting. |
| attackSound | String | defaults to none | The sound that is to be played when this unit attacks. For possible values, see [sounds](#Sounds)
| civilopediaText | List | Default empty | see [civilopediaText chapter](#civilopedia-text) |


## UnitPromotions.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/UnitPromotions.json)

This file lists the available unit promotions.

Each promotion must have an icon, except progressions ending in " I", " II", " III" (no IV V VI allowed) are rendered by looking up an icon without those suffixes and adding stars.

Remember, promotions can be "bought" with XP, but also granted by the unit type, buildings, wonders and such. They are preserved when a unit upgrades, therefore special properties of nation unique units that can be inherited when they upgrade should be in a promotion, not uniques/stats in the units json (example: Slinger withdraw).

Each promotion can have the following properties:
| Attribute | Type | Optional? | Notes |
|-----------|------|-----------|-------|
| name | String | Required | See above for "I, II, III" progressions |
| prerequisites | List | Default empty | Prerequisite promotions |
| effect | String | Default empty | Deprecated, use uniques instead |
| unitTypes | List | Default empty | The unit types for which this promotion applies as specified in [UnitTypes.json](#unittypesjson) |
| uniques | List | Default empty | List of effects, [see here](../Uniques#unit-uniques) |
| civilopediaText | List | Default empty | see [civilopediaText chapter](#civilopedia-text) |


## UnitTypes.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/UnitTypes.json)

This optional file is used for defining new types of units. The names of these can be used in unitFilters, and these types determine what domain the unit moves in: over land, over water or through the air. If the file is ommitted, the following are automatically added:
Civilian, Melee, Ranged, Scout, Mounted, Armor, Siege, WaterCivilian, WaterMelee, WaterRanged, WaterSubmarine, WaterAircraftCarrier, Fighter, Bomber, AtomicBomber, and Missile.

| attribute | Type | optional or not | notes |
| --------- | ---- | -------- | ----- |
| name | String | required | The name of the unit type |
| movementType | String | required | The domain through which the unit moves. Allowed values: "Water", "Land", "Air" |
| uniques | List of String | defaults to none | A list of the unique abilities every unit of this type has. A list of almost all uniques can be found [here](../Uniques#unit-uniques) |

## Techs.json
[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Techs.json)

This file contains all the technologies. It is organized into an outer list of 'columns' which in turn contain one or more tech each.

#### Column structure
| Attribute | Type | Optional? | Notes |
|-----------|------|-----------|-------|
| columnNumber | Integer | Required | Horizontal placement in the Tech Tree. |
| era | String | Required | References [Eras.json](#erasjson). |
| techCost | Integer | Required | Default cost of the techs in this column. |
| buildingCost | Integer | Required | Default cost of buildings requiring this tech. |
| wonderCost | Integer | Required | Default cost of wonders requiring this tech. |
| techs | List of Techs | Required | List of techs as follows - pay attention to the nesting of {} and []. |

#### Tech structure
| Attribute | Type | Optional? | Notes |
|-----------|------|-----------|-------|
| name | String | Required | The name of this Technology. |
| row | Integer | Defaults to 0 | Vertical placement in the Tech Tree, must be unique per column. |
| cost | Integer | Defaults to column techCost | The amount of science required to research this tech. |
| prerequisites | List | Default empty | A list of the names of techs that are prerequisites of this tech. Only direct prerequisites are necessary. |
| quote | String | Default empty | A nice story presented to the player when they research this tech. |
| uniques | List | Default empty | Properties granted by the tech - see [here](../Uniques#general-uniques). |
| civilopediaText | List | Default empty | see [civilopediaText chapter](#civilopedia-text). |


## TileResources.json
This file lists the resources that a map tile can have.

Note the predefined resource _types_ cannot be altered in json.

Note also that resources have two visual representations - icon and pixel graphic in the tileset. Omitting the icon results in a horribly ugly user interface, while omitting tileset graphics will just miss out on an optional visualization. If you provide a pixel graphic for FantasyHex, please be aware of the layering system and the ruleVariants in the tileset json. A single graphic may suffice if it has lots of transparency, as it will be drawn on top of terrain and features but below an improvement - if the single improvement graphic exists at all.

Each resource can have the following properties:
| Attribute | Type | Optional? | Notes |
|-----------|------|-----------|-------|
| name | String | Required |  |
| resourceType | String | Default Bonus | Bonus, Luxury or Strategic |
| terrainsCanBeFoundOn | List | Default empty | [Terrains](#terrainsjson) that allow this resource |
| <stat> | Float | Optional | Per-turn bonus yield for the tile, see [Stats](#stats), can be repeated |
| improvement | String | Default empty | The improvement ([TileImprovements.json](#tileimprovementsjson)) for this resource |
| improvementStats | Object | Default empty | The additional yield when improved as sub-object with one or more [Stats](#stats) |
| revealedBy | String | Default empty | The technology name required to see, work and improve this resource |
| unique | String | Default empty | Effects, [see here](../Uniques#terrain-uniques) - sorry, at the moment only a single one |
| civilopediaText | List | Default empty | see [civilopediaText chapter](#civilopedia-text) |


## Stats

Terrains, features, resources and improvements may list yield statistics. They can be one of the following:
- production, food, gold, science, culture, happiness, faith

If an object carries general stats, any combination (or none) of these can be specified. For specialized stats, they might come as sub-object in a named field. Example:
```json
		"gold": 2,
		"improvement": "Quarry",
		"improvementStats": {"gold": 1,"production": 1},
```

The values are usually integers, though the underlying code supports floating point. The effects are, however, insufficiently tested and therefore -so far- using fractional stats is unsupproted. Go ahead and thoroughly test that in a mod and help out with feedback 😁.


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
