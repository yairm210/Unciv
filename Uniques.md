## Overview

Every type of object has some traits that are shared across all, or most, objects of its kind. For example, a building's stat increase, cost and required tech; a unit's type, movement and attack; a resource's type, improvement and bonus stats from improvement. All such traits have their own fields in the said object types.

But there are also other traits, that are only in a small subset of objects will have. Units that can attack submarines, or can move after attacking, or  has a combat bonus against a certain other type of unit. Buildings that give a free great person, or improve stats dependent on the population of a city, or provide extra yield to certain tiles. These traits cannot be given their own fields due to the huge number of them.

Instead, every special trait that an object has is encoded into a single parameter: the list of unique traits, or "uniques".

In the json files, this looks something like `"uniques": ["Requires a [Market] in all cities", "Cost increases by [30] per owned city"]`.

As seen in the above example, in order to provide flexibility and generalization, Uniques have certain *parameters*, marked by the fact that they are inside square braces. These parameters can be changed, and the game will recognize the text inside them and act accordingly.

## Parameter types

Parameters come in various types, and will be addressed as such inside the [square brackets].

- amount - This indicates a whole, (usually) positive number, like "2" or "13".
- unitName, buildingName, improvementName etc - Rather self explanatory. Examples: "Warrior", "Library', and "Mine", accordingly.
- stat - This is one of the 6 major stats in the game - "Gold", "Science", "Production", "Food", "Happiness" and "Culture". Note that the stat names need to be capitalized!
- stats - see below
- tileFilter - see below
- unitType - 

### stats

This indicates a text comprised of specific stats and is slightly more complex.

Each stats is comprised of several stat changes, each in the form of "+{amount} {stat}", where 'stat' is one of the size major stats mentioned above.
For example: "+1 Science".

These can be strung together with ", " between them, for example: "+2 Production, +3 Food".

### tileFilter

tilefilters allow us to specify tiles according to a number of different aspects:

- Base terrain
- Terrain features
- Base terrain uniques
- Terrain feature uniques
- Tile improvements
- "Water"
- "River" (as in all 'river on tile' contexts, it means 'adjacent to a river on at least on side')

So for instance, the unique "[stats] from [tileFilter] tiles in this city" can match several cases:
- "[+2 Food] from [Lakes] tiles in this city"
- "[+1 Gold] from [Water] tiles in this city"
- "[+1 Production] from [Forest] tiles in this city"

Please note that using resources is only supported in some, not all, use cases (specifically, "[+2 Gold] from [Marble] tiles in this city" is okay)

This is due to the fact that resources can be visible to some civs while invisible to others.

### unitFilter

unitFilters allow us to activate uniques for specific units, based on:

- unit name
- unit type
- "Land" for land units
- "Water" for water units
- "Air" for air units
- "non-air" for non-air units
- "Military" for military units
- "All", as a catch all for all units.

### cityFilter

cityFilters allow us to choose the range of cities affected by this unique:

- "in this city"
- "in all cities"
- "in all coastal cities"
- "in capital"
- "in all cities with a world wonder"

## Building uniques

### Stat uniques

"[stats] Per [amount] Population in this city" - provides the given stats for every [amount] of population. For instance, "[+2 Science] Per [2] Population in this city" would provide only 4 Science in a city with 5 population - since there are only 2 'sets' of 2 population in the city, each providing 2 Science.

"[stats] from [tileFilter] tiles in this city" - Adds the given stats to the yield of tiles matching the filter. The yield is still received by the tiles being worked - so even if you have 5 such tiles, but none of them are worked, the city will remain unaffected.

"+[amount]% growth in all cities", "+[amount]% growth in capital" - 'Growth' is the amount of food retained by a city after calculating all bonuses and removing food eaten by population - that is, the food that leads to population growth.

### Production uniques

"+[amount]% Production when constructing [unitFilter] units", "+[amount]% Production when constructing [unitFilter] units [cityFilter]" - The city produces extra Production when a unit fitting the filter in under construction.

### Location uniques

"Must be on [tileFilter]", "Must not be on [tileFilter]" - limits the buildings that can be built in a city according the the tile type that the city center is built on.

"Must be next to [tileFilter]" - Same. In addition to the regular tileFilter options, accepts "Fresh water" as an option, which includes river-adjacent tiles on top of lake-adjacent tiles (which is merely a consequence of Lakes having a 'Fresh water' uniques and tileFilter accepting Base Terrain uniques)


### Special

"Indicates the capital city" - Unciv requires a specific building to indicate the capital city, which is used for many things. In total overhaul mods, you can change the building that indicates this.

"Free Technology" - Self explanatory.
