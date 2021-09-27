* [Beliefs.json](#beliefsjson)
* [Buildings.json](#buildingsjson)
* [Nations.json](#nationsjson)
* [Policies.json](#policiesjson)
* [Quests.json](#questsjson)
* [Religions.json](#religionsjson)
* [Specialists.json](#specialistsjson)
* [Techs.json](#techsjson)


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
| requiredNearbyImprovedResources | List of Strings | defaults to none | The building can only be built if any of the resources in this list are within the borders of this city and have been improved. Each resource must be in [TileResources.json](#tileresourcesjson) |
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
| startBias | List | Default empty | Zero or more of: terrainFilter or "Avoid [terrainFilter]". Two or more will be logically "and"-ed, and if the filters result in no choices, the entire attribute is ignored (e.g. `"startBias": ["Snow","Tundra"]` will _never_ work). |
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
