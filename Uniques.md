- [Overview](#overview)
  - [Unique locations](#unique-locations)
  - [Parameter types](#parameter-types)
    * [stats](#stats)
    * [tileFilter](#tilefilter)
    * [unitFilter](#unitfilter)
    * [cityFilter](#cityfilter)
- [General uniques](#general-uniques)
  * [Stat uniques](#stat-uniques)
  * [One time effect](#one-time-effect)
  * [Other](#other)
- [Buildings-only](#buildings-only)
  * [Stat uniques](#stat-uniques-1)
  * [Construction condition uniques](#construction-condition-uniques)
- [Improvement uniques](#improvement-uniques)


## Overview

Every type of object has some traits that are shared across all, or most, objects of its kind. For example, a building's stat increase, cost and required tech; a unit's type, movement and attack; a resource's type, improvement and bonus stats from improvement. All such traits have their own fields in the said object types.

But there are also other traits, that are only in a small subset of objects will have. Units that can attack submarines, or can move after attacking, or  has a combat bonus against a certain other type of unit. Buildings that give a free great person, or improve stats dependent on the population of a city, or provide extra yield to certain tiles. These traits cannot be given their own fields due to the huge number of them.

Instead, every special trait that an object has is encoded into a single parameter: the list of unique traits, or "uniques".

In the json files, this looks something like `"uniques": ["Requires a [Market] in all cities", "Cost increases by [30] per owned city"]`.

As seen in the above example, in order to provide flexibility and generalization, Uniques have certain *parameters*, marked by the fact that they are inside square braces. These parameters can be changed, and the game will recognize the text inside them and act accordingly.

### Unique locations

Most uniques are "Global uniques" - meaning, they can be put in one of four places:
- Nation uniques - Always active for a specific Nation
- Policy uniques - Active once the policy has been chosen
- Building uniques - Active once the building has been constructed in any city
- Tech uniques - active once the tech has been researched

Most uniques are *ongoing* - they describe something continuous. Some, however, are one-time actions (free technology, free unit, etc) - these cannot be put in a Nation unique, since unlike the other categories, there is no specific time to activate them. Such uniques will be marked in the documentation as "one time effect".

### Parameter types

Parameters come in various types, and will be addressed as such inside the [square brackets].

- amount - This indicates a whole, (usually) positive number, like "2" or "13".
- unitName, buildingName, improvementName etc - Rather self explanatory. Examples: "Warrior", "Library', and "Mine", accordingly.
- stat - This is one of the 6 major stats in the game - "Gold", "Science", "Production", "Food", "Happiness" and "Culture". Note that the stat names need to be capitalized!
- stats, tileFilter, unitFilter, cityFilter - these are more complex and are addressed individually

#### stats

This indicates a text comprised of specific stats and is slightly more complex.

Each stats is comprised of several stat changes, each in the form of "+{amount} {stat}", where 'stat' is one of the size major stats mentioned above.
For example: "+1 Science".

These can be strung together with ", " between them, for example: "+2 Production, +3 Food".

#### tileFilter

tilefilters allow us to specify tiles according to a number of different aspects:

- Base terrain
- Terrain features
- Base terrain uniques
- Terrain feature uniques
- Tile improvements
- Resource
- "Water", "Land"
- "River" (as in all 'river on tile' contexts, it means 'adjacent to a river on at least on side')
- Natural wonder

So for instance, the unique "[stats] from [tileFilter] tiles in this city" can match several cases:
- "[+2 Food] from [Lakes] tiles in this city"
- "[+1 Gold] from [Water] tiles in this city"
- "[+1 Production] from [Forest] tiles in this city"

Please note that using resources is most use cases, but not in combat ones.

This is due to the fact that resources can be visible to some civs while invisible to others - so if you're attacking with a +10% combat bonus from Coal, while the enemy can't see coal, it could get weird.

#### unitFilter

unitFilters allow us to activate uniques for specific units, based on:

- unit name
- unit type
- "Land" for land units
- "Water" for water units
- "Air" for air units
- "non-air" for non-air units
- "Military" for military units
- "All", as a catch all for all units.

#### cityFilter

cityFilters allow us to choose the range of cities affected by this unique:

- "in this city"
- "in all cities"
- "in all coastal cities"
- "in capital"
- "in all cities with a world wonder"
- "in all cities connected to capital"
- "in all cities with a garrison"

## General uniques

### Stat uniques

"+[amount]% growth [cityFilter]" - for example "+[15]% growth [in all cities]". 'Growth' is the amount of food retained by a city after calculating all bonuses and removing food eaten by population - that is, the food that leads to population growth. "+[amount]% growth in all cities" and "+[amount]% growth in capital" are to be deprecated and should not be used.

"+[amount]% [stat] [cityFilter]" - For example, "+[25]% [Culture] [in all cities]"

"[stats] [cityFilter]" - for example "[+3 Culture] [in capital]", "[+2 Food] [in all cities]". "[stats] in capital", "[stats] in all cities" are to be deprecated and should not be used.

"[stats] from every specialist"

"[stats] from every [object]" - where 'object' can be one of:
- Building name
- tileFilter
- Resource name
- "Strategic resource", "Luxury resource", "Bonus resource", "Water resource"

"[stats] per turn from cities before [techName]"

"[stats] from each Trade Route"

"[stats] per [amount] population [cityFilter]"

### One time effect

"[amount] free [unitName] units appear", "Free [unitName] appears" - Self explanatory. If given to a building, the units will appear next to the city the building was constructed in. If the specified unit can construct cities, the unique will not activate for One-City Challenge players.

"Free Great Person" - Same. Great Person DOES NOT count towards your Great Person generation.

"Receive free [unitName] when you discover [techName]" - this is rather special, as it's activated not when you receive the unique, but rather when the specified tech is researched.

"Free Technology"

"Free Social Policy"

"Empire enters golden age" - if already in a golden age, it is extended by the number of turns that a new golden age would have given.

"Reveals the entire map"

"Triggers victory"

### Unit-affecting uniques

"+[amount] Movement for all [unitFilter] units"

"+[amount]% combat bonus for units fighting in [landFilter]" - where landFilter is one of
- Last terrain name (as in: Plains+Forest is considered Forest, not Plains)
- "Friendly Land", for land under your control, or under friendly city-state / major civ with open borders
- "Foreign Land", for all non-friendly land

"+1 Sight for all land military units" - I don't like this either.

"Units fight as though they were at full strength even when damaged"

"+[amount]% Strength if within [amount] tiles of a [tileFilter]" - for example, "+[10]% Strength if within [2] tiles of a [Moai]"

"+1 Movement for all embarked units"

"Units pay only 1 movement point to embark and disembark"

"Melee units pay no movement cost to pillage"

"[unitFilter] units gain [amount]% more Experience from combat"

"[unitFilter] units gain the [promotionName] promotion"

### City-state related uniques

"City-State Influence degrades [amount]% slower"

"Gifts of Gold to City-States generate [amount]% more Influence"

"Resting point for Influence with City-States is increased by [amount]"

"Allied City-States provide Science equal to [amount]% of what they produce for themselves"

"Quantity of Resources gifted by City-States increased by [amount]%"

"Food and Culture from Friendly City-States are increased by 50%"

"City-State Influence recovers at twice the normal rate"

### Other

"Unhappiness from number of Cities doubled"

"-[amount]% [unitFilter] unit maintenance costs"

"Unhappiness from population decreased by [amount]%" - Despite appearances, this is a global unique, affecting all cities.

"+[amount]% Production when constructing [unitFilter] units", "+[amount]% Production when constructing [unitFilter] units [cityFilter]" - The city produces extra Production when a unit fitting the filter in under construction.

"+[amount]% Production when constructing [constructionType]", "+[amount]% Production when constructing [constructionType] [cityfilter]" - where constructionType can be one of the following:
- Construction name (unit or building name)
- "Buildings"
- "Wonders"
- Building unique

"Cost of purchasing [stat] buildings reduced by [amount]%" - Stat-related buildings are defined as one of the following:
- Provides that stat directly (e.g. +1 Culture)
- Provides a percentage bonus for that stat (e.g. +10% Production)
- Provides that stat as a bonus for resources (e.g. +1 Food for Wheat)

"Culture cost of adopting new Policies reduced by [amount]%"

"Each city founded increases culture cost of policies [33]% less than normal"

"Double Happiness from Natural Wonders"

"Tile yields from Natural Wonders doubled"

"Defensive buildings in all cities are 25% more effective"

"Indicates the capital city" - Unciv requires a specific building to indicate the capital city, which is used for many things. In total overhaul mods, you can change the building that indicates this.

"Worker construction increased 25%"

"-[amount]% Culture cost of acquiring tiles [cityFilter]","-[amount]% Gold cost of acquiring tiles [cityFilter]"

"Golden Age length increased by [amount]%"

"Gold from all trade routes +25%"

"Connects trade routes over water"

"[greatPersonName] is earned [amount]% faster"

"Science gained from research agreements +50%"

"Cost of purchasing items in cities reduced by [amount]%" - 'Purchasing' refers to the gold cost of buying buildings or units, not the amount of production needed to construct.

"Maintenance on roads & railroads reduced by [amount]%"

"Gold cost of upgrading military units reduced by 33%"

"Great General provides double combat bonus"

"Double quantity of [resourceName] produced"

"Earn [amount]% of killed [unitFilter] unit's [param1] as [param2]" - param1 accepts "Cost" or "Strength", param2 accepts "Gold" or "Culture". For example, "Earn [100]% of killed [Military] unit's [Strength] as [Culture]", "Earn [10]% of killed [Military] unit's [Cost] as [Gold]"

## Buildings-only

"Doubles Gold given to enemy if city is captured"

"[40]% of food is carried over after population increases"

"All newly-trained [unitFilter] units in this city receive the [promotionName] promotion"

"+[amount]% great person generation in this city"

"Provides 1 extra copy of each improved luxury resource near this City"

"Remove extra unhappiness from annexed cities"

"Provides 1 happiness per 2 additional social policies adopted"

"Cannot be purchased"

"Cost increases by [amount] per owned city"

"Unsellable"

### Stat uniques

"[stats] Per [amount] Population in this city" - provides the given stats for every [amount] of population. For instance, "[+2 Science] Per [2] Population in this city" would provide only 4 Science in a city with 5 population - since there are only 2 'sets' of 2 population in the city, each providing 2 Science.

"[stats] from [tileFilter] tiles in this city" - Adds the given stats to the yield of tiles matching the filter. The yield is still received by the tiles being worked - so even if you have 5 such tiles, but none of them are worked, the city will remain unaffected.

"[stats] once [techName] is discovered"

### Construction condition uniques

"Consumes [amount] [resourceName]" - Acts like the requiredResource field, but can be added multiple times to the same building and with varying amounts. Buildings will require the resources to be constructed, and will continue to consume them as long as the building exists.

"Must be on [tileFilter]", "Must not be on [tileFilter]" - limits the buildings that can be built in a city according the the tile type that the city center is built on.

"Must be next to [tileFilter]", "Must not be next to [tileFilter]" - Same. In addition to the regular tileFilter options, accepts "Fresh water" as an option, which includes river-adjacent tiles on top of lake-adjacent tiles (which is merely a consequence of Lakes having a 'Fresh water' uniques and tileFilter accepting Base Terrain uniques)

"Must have an owned [tileFilter] within [amount] tiles"

"Requires a [buildingName] in all cities"

"Can only be built in annexed cities"

"Obsolete with [techName]" - Building cannot be built once the tech is researched

## Improvement uniques

"Obsolete with [techName]"

"[stats] once [techName] is discovered"

"Gives a defensive bonus of [amount]%"

"Can be built outside your borders"

"Costs [amount] gold per turn when in your territory"

"Great Improvement"

"[stats] for each adjacent [tileFilter]"

"Can only be built on Coastal tiles"

"Cannot be built on bonus resource"

"Tile provides yield without assigned population"