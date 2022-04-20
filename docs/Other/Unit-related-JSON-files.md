# Unit-related JSON files

## Units.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/Units.json)

This file should contain a list of all the units, both military and civilian, that you want to use in your mod.

Each unit can have the following attributes:

| Attribute | Type | Optional | Notes |
| --------- | ---- | -------- | ----- |
| name | String | required | The name of the units (required) |
| unitType | String | required | The type of the unit. Must be in [UnitTypes.json](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/UnitTypes.json) |
| cost | Integer (≥0) | defaults to 0 | The amount of production required to build this unit |
| movement | Integer (≥0) | defaults to 0 | The amount of movement points the unit has by default |
| strength | Integer (≥0) | defaults to 0 | The melee attack and defensive strength of the unit. If this and rangedStrength are ommited or 0, the unit will be a civilian |
| rangedStrength | Integer (≥0) | defaults to 0 | The ranged attack strength of the unit. If omitted, the unit cannot ranged attack |
| range | Integer (≥0) | defaults to 2 | The default range from which ranged attacks can be preformed |
| interceptRange | Integer (≥0) | defaults to 0 | Air units attacking within in this range will be intercepted |
| requiredTech | String | defaults to none | The tech required to build this unit. Must be in [Techs.json](Civilization-related-JSON-files.md#techsjson) |
| obsoleteTech | String | defaults to none | After researching this tech, the unit can no longer be build. Must be in [Techs.json](Civilization-related-JSON-files.md#techsjson) |
| requiredResource | String | defaults to none | Resource that is consumed by building this unit. Must be in [TileResources.json](Map-related-JSON-files.md#tileresourcesjson) |
| upgradesTo | String | defaults to none | Unit that this unit can upgrade to when it is available. Must be in [Units.json](#unitsjson) |
| replaces | String | defaults to none | If this unit is unique to a nation, this is the unit it replaces. Must be in [Units.json](#unitsjson) |
| uniqueTo | String | defaults to none | The nation that this unit is unique to. Must be in [Nations.json](Civilization-related-JSON-files.md#nationsjson) |
| hurryCostModifier | Integer | defaults to 0 | If this unit is bought for gold/faith, it's price is increased by so much percent |
| promotions | List of Strings | defaults to none | A list of all the promotions the unit automatically receives upon being built. Each promotion must be in [UnitPromotions.json](#unitpromotionsjson) |
| uniques | List of Strings | defaults to none | A list of the unique abilities this unit has. A list of almost all uniques can be found [here](../Modders/Unique-parameters.md#unit-uniques) |
| replacementTextForUniques | String | defaults to none | If provided, this will be displayed instead of the list of uniques. Can be used for better formatting. |
| attackSound | String | defaults to none | The sound that is to be played when this unit attacks. For possible values, see [Sounds](#../Modders/Images-and-Audio.md#sounds)
| civilopediaText | List | Default empty | see [civilopediaText chapter](Miscellaneous-JSON-files.md#civilopedia-text) |


## UnitPromotions.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/UnitPromotions.json)

This file lists the available unit promotions.

Each promotion must have an icon, except progressions ending in " I", " II", " III" (no IV V VI allowed) are rendered by looking up an icon without those suffixes and adding stars.

Remember, promotions can be "bought" with XP, but also granted by the unit type, buildings, wonders and such. They are preserved when a unit upgrades, therefore special properties of nation unique units that can be inherited when they upgrade should be in a promotion, not uniques/stats in the units json (example: Slinger withdraw).

Each promotion can have the following properties:

| Attribute | Type | Optional | Notes |
| --------- | ---- | -------- | ----- |
| name | String | Required | See above for "I, II, III" progressions |
| prerequisites | List | Default empty | Prerequisite promotions |
| effect | String | Default empty | Deprecated, use uniques instead |
| unitTypes | List | Default empty | The unit types for which this promotion applies as specified in [UnitTypes.json](#unittypesjson) |
| uniques | List | Default empty | List of effects, [see here](../Modders/Unique-parameters.md#unit-uniques) |
| civilopediaText | List | Default empty | see [civilopediaText chapter](Miscellaneous-JSON-files.md#civilopedia-text) |

## UnitTypes.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/UnitTypes.json)

This optional file is used for defining new types of units. The names of these can be used in unitFilters, and these types determine what domain the unit moves in: over land, over water or through the air. If the file is ommitted, the following are automatically added:
Civilian, Melee, Ranged, Scout, Mounted, Armor, Siege, WaterCivilian, WaterMelee, WaterRanged, WaterSubmarine, WaterAircraftCarrier, Fighter, Bomber, AtomicBomber, and Missile.

| Attribute | Type | Optional | Notes |
| --------- | ---- | -------- | ----- |
| name | String | required | The name of the unit type |
| movementType | String | required | The domain through which the unit moves. Allowed values: "Water", "Land", "Air" |
| uniques | List of String | defaults to none | A list of the unique abilities every unit of this type has. A list of almost all uniques can be found [here](../Modders/Unique-parameters.md#unit-uniques) |
