# Unit-related JSON files

## Units.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/Units.json)

This file should contain a list of all the units, both military and civilian, that you want to use in your mod.

Each unit has the following structure:

| Attribute                 | Type            | Default  | Notes                                                                                                                                               |
|---------------------------|-----------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| name                      | String          | Required |                                                                                                                                                     |
| unitType                  | String          | Required | The type of the unit. Must be in [UnitTypes.json](#unittypesjson)                                                                                   |
| cost                      | Integer         | -1       | The amount of production required to build this unit. The production needed is always positive                                                      |
| movement                  | Integer         | 0        | The amount of movement points the unit has by                                                                                                       |
| strength                  | Integer         | 0        | The melee attack and defensive strength of the unit. If this and rangedStrength are omitted or 0, the unit will be a civilian                       |
| rangedStrength            | Integer         | 0        | The ranged attack and defensive strength of the unit. If omitted, the unit cannot ranged attack. If used, strength must be set too.                 |
| religiousStrength         | Integer         | 0        | The religious attack and defensive strength of the unit                                                                                             |
| range                     | Integer         | 2        | The range from which ranged attacks can be preformed                                                                                                |
| interceptRange            | Integer         | 0        | Air units attacking within in this range will be intercepted                                                                                        |
| requiredTech              | String          | none     | The tech required to build this unit. Must be in [Techs.json](2-Civilization-related-JSON-files.md#techsjson)                                       |
| obsoleteTech              | String          | none     | After researching this tech, the unit can no longer be build. Must be in [Techs.json](2-Civilization-related-JSON-files.md#techsjson)               |
| requiredResource          | String          | none     | Resource that is consumed by building this unit. Must be in [TileResources.json](3-Map-related-JSON-files.md#tileresourcesjson)                     |
| upgradesTo                | String          | none     | Unit that this unit can upgrade to when it is available. Must be in [Units.json](#unitsjson)                                                        |
| replaces                  | String          | none     | If this unit is unique to a nation, this is the unit it replaces. Must be in [Units.json](#unitsjson)                                               |
| uniqueTo                  | String          | none     | The nation that this unit is unique to. Must be in [Nations.json](2-Civilization-related-JSON-files.md#nationsjson)                                 |
| hurryCostModifier         | Integer         | 0        | If this unit is bought for gold, its price is increased by so much percent                                                                          |
| promotions                | List of Strings | empty    | A list of all the promotions the unit automatically receives upon being built. Each promotion must be in [UnitPromotions.json](#unitpromotionsjson) |
| uniques                   | List of Strings | empty    | List of [unique abilities](../uniques.md) this unit has                                                                                             |
| replacementTextForUniques | String          | none     | If provided, this will be displayed instead of the list of uniques. Can be used for better formatting.                                              |
| attackSound               | String          | none     | The sound that is to be played when this unit attacks. For possible values, see [Sounds](../../Images-and-Audio.md#sounds)                          |
| civilopediaText           | List            | empty    | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text)                                                                       |

## UnitPromotions.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/UnitPromotions.json)

This file lists the available unit promotions.

Each promotion must have an icon, except progressions ending in " I", " II", " III" (no IV V VI allowed) are rendered by looking up an icon without those suffixes and adding stars.

Remember, promotions can be "bought" with XP, but also granted by the unit type, buildings, wonders and such. They are preserved when a unit upgrades, therefore special properties of nation unique units that can be inherited when they upgrade should be in a promotion, not uniques/stats in the units json (example: Slinger withdraw).

Each promotion has the following structure:

| Attribute       | Type            | Default  | Notes                                                                                                                                                                                                                                                                         |
|-----------------|-----------------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name            | String          | Required | See above for "I, II, III" progressions                                                                                                                                                                                                                                       |
| prerequisites   | List of Strings | empty    | Prerequisite promotions                                                                                                                                                                                                                                                       |
| column          | Integer         | Optional | Determines placement order on the promotion picker screen. Name is historical, these coordinates no longer control placement directly. Promotions without coordinates are ensured to be placed last. (…)                                                                      |
| row             | Integer         | Optional | … In base mods without any coordinates, promotions without prerequisites are sorted alphabetically and placed top down, the rest of the screen will structure the dependencies logically. If your mod has a "Heal instantly", it is suggested to use row=0 to place it on top |
| unitTypes       | List of Strings | empty    | The unit types for which this promotion applies as specified in [UnitTypes.json](#unittypesjson)                                                                                                                                                                              |
| uniques         | List of Strings | empty    | List of [unique abilities](../uniques.md) this promotion grants to the units                                                                                                                                                                                               |
| civilopediaText | List            | empty    | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text)                                                                                                                                                                                                 |
| innerColor      | List            | empty    | Color of the *icon*                                                                                                                                                                                                                                                           |
| outerColor      | List            | empty    | Color of the *background*                                                                                                                                                                                                                                                     |

## UnitTypes.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/UnitTypes.json)

This optional file is used for defining new types of units. The names of these can be used in unitFilters, and these types determine what domain the unit moves in: over land, over water or through the air.
For base ruleset Mods, if the file is omitted or contains an empty list, [all types from the Vanilla ruleset](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/UnitTypes.json) are automatically added:
Civilian, Melee, Ranged, Scout, Mounted, Armor, Siege, WaterCivilian, WaterMelee, WaterRanged, WaterSubmarine, WaterAircraftCarrier, Fighter, Bomber, AtomicBomber, and Missile.

Each unit type has the following structure:

| Attribute    | Type           | Default  | Notes                                                                                    |
|--------------|----------------|----------|------------------------------------------------------------------------------------------|
| name         | String         | Required |                                                                                          |
| movementType | Enum           | Required | The domain through which the unit moves. Allowed values: "Water", "Land", "Air"          |
| uniques      | List of String | none     | List of [unique abilities](../uniques.md) this promotion grants to units of this type |
| civilopediaText | List        | empty    | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text)            |

## GreatPeople.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Gods%20%26%20Kings/GreatPeople.json)

Provides a list of names that can be applied to Great People.

| Attribute    | Type           | Default  | Notes |
|--------------|----------------|----------|-------|
| name         | String         | Required | A unique name for the great person. |
| units        | List of String | Required | A list of Great Person units that this name applies to. Example: "Great Scientist", "Great Engineer", etc. |
| uniques      | List of String | none     | List of [triggerable uniques](../uniques.md) that are applied to the unit |
| civilopediaText | List        | empty    | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text) |
