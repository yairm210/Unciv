- [Overview](#overview)
  - [Generated Documentation](#generated-documentation)
  - [Unique locations](#unique-locations)
  - [Parameter types](#parameter-types)
    * [stats](#stats)
    * [tileFilter](#tilefilter)
    * [unitFilter](#unitfilter)
    * [cityFilter](#cityfilter)
    * [buildingFilter](#constructionfilter)


## Overview

Every type of object has some traits that are shared across all, or most, objects of its kind. For example, a building's stat increase, cost and required tech; a unit's type, movement and attack; a resource's type, improvement and bonus stats from improvement. All such traits have their own fields in the said object types.

But there are also other traits, that are only in a small subset of objects will have. Units that can see submarines from more than one tile away, or can move after attacking, or  has a combat bonus against a certain other type of unit. Buildings that give a free great person, or improve stats dependent on the population of a city, or provide extra yield to certain tiles. These traits cannot be given their own fields due to the huge number of them.

Instead, every special trait that an object has is encoded into a single parameter: the list of unique traits, or "uniques".

In the json files, this looks something like `"uniques": ["Requires a [Market] in all cities", "Cost increases by [30] per owned city"]`.

As seen in the above example, in order to provide flexibility and generalization, Uniques have certain *parameters*, marked by the fact that they are inside square braces. These parameters can be changed, and the game will recognize the text inside them and act accordingly.

A list of all available uniques can be found [here](https://github.com/yairm210/Unciv/blob/master/docs/uniques.md).

### Generated Documentation

This part of the wiki is human-edited and partially out of date. However, we now have automatically generated documentation, complete for all Uniques that have been updated to the new UniqueType system. It is part of the main source tree and [can be found here.](/docs/Modders/uniques.mdiques.md). This version should always be up-to-date with the uniques and conditionals currently supported in the game.

### Unique locations

Most uniques are "Global uniques" - meaning, they can be put in one of these places:
- Nation uniques - Always active for a specific Nation
- Policy uniques - Active once the policy has been chosen
- Building uniques - Active once the building has been constructed in any city
- Tech uniques - Active once the tech has been researched
- Era uniques - Active once in the specified era
- Religion uniques - Founder & Enhancer beliefs from your religion

Most uniques are *ongoing* - they describe something continuous. Some, however, are one-time actions (free technology, free unit, etc) - these cannot be put in a Nation unique, since unlike the other categories, there is no specific time to activate them. Such uniques will be marked in the documentation as "one time effect".

### Parameter types

Parameters come in various types, and will be addressed as such inside the [square brackets].

- amount - This indicates a whole number, possibly with a + or - sign, such as "2", "+13", or "-3".
- unitName, buildingName, improvementName etc - Rather self explanatory. Examples: "Warrior", "Library", and "Mine", accordingly.
- stat - This is one of the 7 major stats in the game - "Gold", "Science", "Production", "Food", "Happiness", "Culture" and "Faith". Note that the stat names need to be capitalized!
- stats, tileFilter, unitFilter, cityFilter, constructionFilter/buildingFilter - these are more complex and are addressed individually

#### stats

This indicates a text comprised of specific stats and is slightly more complex.

Each stats is comprised of several stat changes, each in the form of "+{amount} {stat}", where 'stat' is one of the seven major stats mentioned above.
For example: "+1 Science".

These can be strung together with ", " between them, for example: "+2 Production, +3 Food".

A full example would be, for the "[stats] from every [buildingName]" unique:

"[+1 Culture, +1 Gold] from every [Barracks]"

#### tileFilter

TileFilters are split up into two parts: terrainFilters and improvementFilters. TerrainFilters only check if the tile itself has certain characteristics, while the improvementFilters only checks the improvement on a tile. Using the tileFilter itself will check both of these.

terrainFilters allow us to specify tiles according to a number of different aspects:

- A filter names a specific json attribute (by name):
    - Base terrain
    - Terrain features
    - Base terrain uniques
    - Terrain feature uniques
    - Resource
    - Natural wonder
- Or the filter is a constant string choosing a derived test:
    - "All"
    - "Water", "Land"
    - "Coastal" (at least one direct neighbor is a coast)
    - "River" (as in all 'river on tile' contexts, it means 'adjacent to a river on at least one side')
    - "Open terrain", "Rough terrain" (note all terrain not having the rough unique is counted as open)
    - "Friendly Land" - land belonging to you, or other civs with open borders to you
    - "Foreign Land" - any land that isn't friendly land
    - "Enemy land" - any land belonging to a civ you are at war with
    - "Water resource", "Strategic resource", "Luxury resource", "Bonus resource"
    - "Natural Wonder" (as opposed to above which means testing for a specific Natural Wonder by name, this tests for any of them)

Please note all of these are _case-sensitive_.

Also note: Resource filters depend on whether a viewing civ is known in the context where the filter runs. Water and specific tests require a viewing civ, and if the resource needs a tech to be visible, that tech to be researched by the viewing civ. The other resource category tests can succeed without a known viewing civ only for resources not requiring any tech. So - test your mod!

So for instance, the unique "[stats] from [tileFilter] tiles [cityFilter]" can match several cases:
- "[+2 Food] from [Lakes] tiles [in this city]"
- "[+1 Gold] from [Water] tiles [in all cities]"
- "[+1 Production] from [Forest] tiles [in all coastal cities]"

Please note that using resources is most use cases, but not in combat ones.

This is due to the fact that resources can be visible to some civs while invisible to others - so if you're attacking with a +10% combat bonus from Coal, while the enemy can't see coal, it could get weird.

improvementFilters only check for the improvements on a tile. The following are implemented:
- improvement name (Note that "Road" and "Railroad" _do_ work as improvementFilters, but not as tileFilters at the moment.)
- "All"
- "Great Improvements", "Great"
- "All Road" - for Roads & Railroads

#### unitFilter

unitFilters allow us to activate uniques for specific units, based on:

- unit name
- unit type - e.g. Melee, Ranged, WaterSubmarine, etc.
- "Land", "Water", "Air"
- "land units", "water units", "air units"
- "non-air" for non-air non-missile units
- "Military", "military units"
- "Civilian", "civilian units"
- "All"
- "Melee"
- "Ranged"
- "Nuclear Weapon"
- "Great Person", "Great"
- "Embarked"
- "Wounded", "wounded units"
- "Barbarians", "Barbarian"
- "City-State"
- Any exact unique the unit has
- Any exact unique the unit type has
- Any combination of the above (will match only if all match). The format is "{filter1} {filter2}" and can match any number of filters. For example: "[{Military} {Water}]" units, "[{Wounded} {Armor}]" units, etc. No space or other text is allowed between the "[" and the first "{".

#### cityFilter

cityFilters allow us to choose the range of cities affected by this unique:

- "in this city"
- "in all cities"
- "in other cities"
- "in all coastal cities"
- "in capital"
- "in all non-occupied cities" - all cities that are not puppets and don't have extra unhappiness from being recently conquered
- "in all cities with a world wonder"
- "in all cities connected to capital"
- "in all cities with a garrison"
- "in non-enemy foreign cities" - In all cities owned by civs other than you that you are not at war with
- "in foreign cities"
- "in annexed cities"
- "in holy cities"
- "in City-State cities"
- "in cities following this religion" - Should only be used in pantheon/follower uniques for religions
- "in all cities in which the majority religion is a major religion"
- "in all cities in which the majority religion is a enhanced religion"

### constructionFilter

ConstructionFilters allow us to activate uniques while constructing certain buildings or units.
For units, the UnitFilter is called. For Buildings, the following options are implemented:

- "All"
- "Buildings", "Building"
- "Wonders", "Wonders"
- "National Wonder"
- "World Wonder" -- All wonders that are not national wonders
- building name
- The name of the building it replaces (so for example uniques for libraries will apply to paper makers as well)
- an exact unique the building has (e.g.: "spaceship part")
- "Culture", "Gold", etc. if the building is "stat-related" for that stat. Stat-related buildings are defined as one of the following:
  - Provides that stat directly (e.g. +1 Culture)
  - Provides a percentage bonus for that stat (e.g. +10% Production)
  - Provides that stat as a bonus for resources (e.g. +1 Food for Wheat)
  - Provides that stat per some amount of population (e.g. +1 Science for every 2 population [cityFilter])

### Conditionals

Some uniques also allow for the placing of conditionals. These are conditions that need to be met for the unique to be active. In the unique "[+10]% Growth \<when at war\>", the `<when at war>` part is a conditional, denoted by the pointy brackets. Making a building with this unique will provide a 10% Growth boost to cities with this building, but only as long as the empire is at war.

Multiple conditionals can be applied to the same unique, for example, you can have a promotion with the following unique:
"[+33]% Strength \<vs [Armored] units\> \<in [Open terrain] tiles\>"
Which will only apply the strength boost when fighting armored units in open terrain.

This system is currently in development, so only a small amount of conditionals exist, and only a few uniques can have conditionals for now. It will be expanded greatly, improving the amount of combinations that can be made and therefore the amount of different uniques that exist.
Uniques that support conditionals will be denoted with a "©" sign for now.
"Quantity of Resources gifted by City-States increased by 100%" - Replaced with "Quantity of Resources gifted by City-States increased by [amount]%"
