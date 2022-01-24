- [Overview](#overview)
  - [Generated Documentation](#generated-documentation)
  - [Unique locations](#unique-locations)
  - [Parameter types](#parameter-types)
    * [stats](#stats)
    * [tileFilter](#tilefilter)
    * [unitFilter](#unitfilter)
    * [cityFilter](#cityfilter)
  - [Conditionals](#conditionals)
- [General uniques](#general-uniques)
  * [Stat uniques](#stat-uniques)
  * [One time effect](#one-time-effect)
  * [Unit-affecting uniques](#unit-affecting-uniques)
  * [City-state related uniques](#city-state-related-uniques)
  * [Other](#other)
- [Buildings-only](#buildings-only)
  * [Stat uniques](#stat-uniques-1)
  * [Construction condition uniques](#construction-condition-uniques)
- [Religion uniques](#religion-uniques)
- [Improvement uniques](#improvement-uniques)
- [Unit uniques](#unit-uniques)
  * [One time effect units](#one-time-effect-units)
  * [Civilian](#civilian)
  * [Visibility](#visibility)
  * [Movement](#movement)
  * [Healing](#healing)
  * [Combat bonuses](#combat-bonuses)
  * [Other](#other)
- [Terrain uniques](#terrain-uniques)
- [Resource uniques](#resource-uniques)
- [ModOptions uniques](#modoptions-uniques)
- [Deprecated uniques](#deprecated-uniques)


## Overview

Every type of object has some traits that are shared across all, or most, objects of its kind. For example, a building's stat increase, cost and required tech; a unit's type, movement and attack; a resource's type, improvement and bonus stats from improvement. All such traits have their own fields in the said object types.

But there are also other traits, that are only in a small subset of objects will have. Units that can see submarines from more than one tile away, or can move after attacking, or  has a combat bonus against a certain other type of unit. Buildings that give a free great person, or improve stats dependent on the population of a city, or provide extra yield to certain tiles. These traits cannot be given their own fields due to the huge number of them.

Instead, every special trait that an object has is encoded into a single parameter: the list of unique traits, or "uniques".

In the json files, this looks something like `"uniques": ["Requires a [Market] in all cities", "Cost increases by [30] per owned city"]`.

As seen in the above example, in order to provide flexibility and generalization, Uniques have certain *parameters*, marked by the fact that they are inside square braces. These parameters can be changed, and the game will recognize the text inside them and act accordingly.

### Generated Documentation

This part of the wiki is human-edited and partially out of date. However, we now have automatically generated documentation, complete for all Uniques that have been updated to the new UniqueType system. [It is part of the main source tree](/docs/uniques.md).

### Unique locations

Most uniques are "Global uniques" - meaning, they can be put in one of four places:
- Nation uniques - Always active for a specific Nation
- Policy uniques - Active once the policy has been chosen
- Building uniques - Active once the building has been constructed in any city
- Tech uniques - Active once the tech has been researched
- Era uniques - Active once in the specified era
- Religion uniques - Founder & Enhancer beliefs from your religion

Most uniques are *ongoing* - they describe something continuous. Some, however, are one-time actions (free technology, free unit, etc) - these cannot be put in a Nation unique, since unlike the other categories, there is no specific time to activate them. Such uniques will be marked in the documentation as "one time effect".

### Parameter types

Parameters come in various types, and will be addressed as such inside the [square brackets].

- amount - This indicates a whole, (usually) positive number, like "2" or "13".
- unitName, buildingName, improvementName etc - Rather self explanatory. Examples: "Warrior", "Library", and "Mine", accordingly.
- stat - This is one of the 7 major stats in the game - "Gold", "Science", "Production", "Food", "Happiness", "Culture" and "Faith". Note that the stat names need to be capitalized!
- stats, tileFilter, unitFilter, cityFilter, constructionFilter - these are more complex and are addressed individually

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

### ConstructionFilter

ConstructionFilters allow us to activate uniques while constructing certain buildings or units.
For units, the UnitFilter is called. For Buildings, the following options are implemented:

- "All"
- "Buildings", "Building"
- "Wonders", "Wonders"
- building name
- The name of the building it replaces (so for example uniques for libraries will apply to paper makers as well)
- an exact unique the building has (e.g.: "spaceship part")
- if the building is "stat-related" for some stat. Stat-related buildings are defined as one of the following:
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

### Autogenerated unique documentation

Can be found [here!](/docs/uniques.md)

#### Global conditionals

\<when at war\> - Applies when the civilization is at war

\<when not at war\> - Applies when the civilization is not at war

\<while the empire is happy\>

\<during a Golden Age\>

\<before the [eraName]\>

\<starting from the [eraName]\>

\<during the [eraName\]>

\<before discovering [tech]\>

\<after discovering [tech]\>

\<before adopting [policy]\>

\<after adopting [policy]\>

#### City conditionals

\<if this city has at least [amount] specialists\> - Can only be used on things that exist in a city or relate to a specific city

#### Unit conditionals

\<vs [unitFilter] units\>

\<vs cities\>

\<when attacking\>

\<when defending\>

\<in [tileFilter] tiles\>

## General uniques

### Stat uniques

Remember, match is case sensitive, so please capitalize each Stat name.

"[stats]" ©

"[stats] [cityFilter]" © - for example "[+3 Culture] [in capital]", "[+2 Food] [in all cities]". "[stats] in capital", "[stats] in all cities" are to be deprecated and should not be used.

"[amount]% [Stat]" ©

"+[amount]% [Stat] [cityFilter]" - For example, "+[25]% [Culture] [in all cities]"

"[amount]% growth [cityFilter]" © - for example "+[15]% growth [in all cities]". 'Growth' is the amount of food retained by a city after calculating all bonuses and removing food eaten by population - that is, the food that leads to population growth. "+[amount]% growth in all cities" and "+[amount]% growth in capital" are to be deprecated and should not be used.

"[stats] from every specialist [cityFilter]"

"[stats] from every [object]" - where 'object' can be one of:
- Building name
- tileFilter
- Resource name
- "Strategic resource", "Luxury resource", "Bonus resource", "Water resource"
- Specific specialist name

"[stats] per turn from cities before [techName]"

"[stats] from each Trade Route"

"[amount]% [Stat] while the empire is happy"

"[signedAmount]% unhappiness from specialists [cityFilter]"

"[amount]% food consumption by specialists"

"[Stats] when a city adopts this religion for the first time"

"[Stats] when a city adopts this religion for the first time (modified by game speed)" - The difference with the previous is that the stats for these are multiplied by a value dependent on the game speed: 0.67 for quick, 1 for normal, 1.5 for epic and 3 for marathon

"[Stats] for each global city following this religion"

"[amount]% [Stat] from every follower, up to [amount]%"

"[Stats] for every [amount] global followers [cityFilter]"

"[Stats] from every [constructionFilter] in cities where this religion has at least [amount] followers"


### One time effect

"[amount] free [unitName] units appear", "Free [unitName] appears" - Self explanatory. If given to a building, the units will appear next to the city the building was constructed in. If the specified unit can construct cities, the unique will not activate for One-City Challenge players.

"Free Great Person" - Same. Great Person does NOT count towards your Great Person generation.

"Receive free [unitName] when you discover [techName]" - this is rather special, as it's activated not when you receive the unique, but rather when the specified tech is researched.

"Free Technology"

"[amount] Free Technologies"

"Free Social Policy"

"[amount] Free Social Policies"

"Empire enters golden age" - if already in a golden age, it is extended by the number of turns that a new golden age would have given.

"Reveals the entire map"

"Triggers victory"

"Triggers voting for diplomatic victory"

"Allied City-States will occasionally gift Great People" - This will start a timer for the player with this unique, which grants a free great person every 25-60 turns (based on game speed), as long as they are allied to at least one city-state. 

"[amount] population [cityfilter]" - e.g.: [-2] population [in all cities]

"[amount] population in a random city" - Population appears in a single randomly chosen city

"[amount] free random researchable Tech(s) from the [eraName]" - Grants [amount] techs that you can currently research from the [eraName] era. If you have no such techs, this will have no effect

"Gain [amount] [Stat]"

"Gain [amount]-[amount] Stat" - Will make you gain a random amount of [Stat] between the two provided values

"Gain enough Faith for a Pantheon"

"Gain enough Faith for [amount]% of a Great Prophet"

"Reveal up to [amount or "All"] [tileFilter] within a [amount] tile radius" - Used for revealing barbarian encampments in base game ruins

"From a randomly chosen tile [amount] tiles away from the ruins, reveal tiles up to [amount] tiles away with [amount]% chance"

"Triggers a global alert" - Can only be used as a unique for a policy. All players receive the following notification: "[civilizationName] has adopted the [policyName] policy"

"Triggers the following global alert: [param1]" - Can only be used as a unique for a policy. [param1] can be any sentence. All player receive the following notification: 
"[civilizationName] has adopted the [policyName] policy 
[param1]"

"Triggers voting for the Diplomatic Victory"



### Unit-affecting uniques

"+[amount] Movement for all [unitFilter] units"

"+[amount]% Strength for units fighting in [tileFilter]"

"[amount] Sight for all [unitFilter] units"

"Units fight as though they were at full strength even when damaged"

"+[amount]% Strength if within [amount] tiles of a [tileFilter]" - for example, "+[10]% Strength if within [2] tiles of a [Moai]"

"[amount] Movement" - ex.: "[+1] Movement"

"Units pay only 1 movement point to embark and disembark"

"Melee units pay no movement cost to pillage"

"[unitFilter] units gain [amount]% more Experience from combat"

"[unitFilter] units gain the [promotionName] promotion"

"[amount] units cost no maintenance" ©

"[amount]% maintenance costs for [unitFilter] units" ©

"[Stat] cost of purchasing [unitFilter] units [amount]%"

"+[amount]% attack strength to all [unitFilter] units for [amount] turns"

"When spreading religion to a city, gain [amount] times the amount of followers of other religions as [Stat]" - "Stat" may be Science, Culture, Faith or Gold



### City-state related uniques

"City-State Influence degrades [amount]% slower"

"Gifts of Gold to City-States generate [amount]% more Influence"

"Resting point for Influence with City-States is increased by [amount]"

"Resting point for Influence with City-States following this religion [amount]"

"Allied City-States provide [Stat] equal to [amount]% of what they produce for themselves"

"Quantity of Resources gifted by City-States increased by [amount]%"

"Happiness from Luxury Resources gifted by City-States increased by [amount]%"

"Food and Culture from Friendly City-States are increased by 50%"

"City-State Influence recovers at twice the normal rate"

"Militaristic City-States grant units [amount] times as fast when you are at war with a common nation"

"Influence of all other civilizations with all city-states degrades [amount]% faster"

"Gain [amount] Influence with a [unitFilter] gift to a City-State"


### Other

"Unhappiness from number of Cities doubled"

"-[amount]% [unitFilter] unit maintenance costs"

"No Maintenance costs for improvements in [tileFilter] tiles"

"[amount]% unhappiness from population [cityFilter]"

"[amount]% Production when constructing [unitFilter] units [cityFilter]" - The city produces extra Production when a unit fitting the filter in under construction.

"[amount]% Production when constructing [buildingFilter] buildings [cityFilter]"

"[amount]% Production when constructing [buildingFilter] wonders [cityFilter]"

"[Stat] cost of purchasing [buildingFilter] buildings [amount]%"

"Culture cost of adopting new Policies reduced by [amount]%"

"Each city founded increases culture cost of policies [33]% less than normal"

"Double Happiness from Natural Wonders"

"Tile yields from Natural Wonders doubled"

"Defensive buildings in all cities are 25% more effective"

"Indicates the capital city" - Unciv requires a specific building to indicate the capital city, which is used for many things. In total overhaul mods, you can change the building that indicates this.

"[amount]% tile improvement construction time"

"-[amount]% Culture cost of acquiring tiles [cityFilter]","-[amount]% Gold cost of acquiring tiles [cityFilter]"

"Golden Age length increased by [amount]%"

"Gold from all trade routes +25%"

"Connects trade routes over water"

"[greatPersonName] is earned [amount]% faster"

"Science gained from research agreements [amount]%"

"[Stat] cost of purchasing items in cities [amount]%" - 'Purchasing' refers to the gold cost of buying buildings or units, not the amount of production needed to construct.

"Maintenance on roads & railroads reduced by [amount]%"

"Gold cost of upgrading [unitFilter] units reduced by [amount]%"

"Great General provides double combat bonus"

"Double quantity of [resourceName] produced"

"Earn [amount]% of killed [unitFilter] unit's [param1] as [param2]" - param1 accepts "Cost" or "Strength", param2 accepts "Culture", "Science", "Gold" and "Faith". For example, "Earn [100]% of killed [Military] unit's [Strength] as [Culture]", "Earn [10]% of killed [Military] unit's [Cost] as [Gold]". This can also be applied directly to a unit (as a unique or as a promotion effect).

"Upon capturing a city, receive [amount] times its [Stat] production as [param1] immediately" - param1 accepts "Culture", "Science", "Gold" and "Faith".

"-[amount]% maintenance cost for buildings [cityFilter]"

"Provides the cheapest [Stat] building in your first [amount] cities for free" - If more than one unique is found, the [amounts] of all uniques with the same Stat are added together to find the amount of cities that should receive a free building. No city will receive more than 1 building of the same Stat from multiple copies of this unique. These buildings are maintenance-free.

"Provides a [buildingName] in each of your first [amount] cities for free" - If more than one unique is found, the [amounts] off all uniques are added together to find the amound of cities that should receive a free [buildingName]. No city will receive more than 1 [buildingName] from multiple copies of this unique.

These last two uniques may seem like they only have a one-time effect. However, the 'free' also means that you don't pay any maintenance costs for these buildings. 

"Hidden when religion is disabled" - Removes units, buildings or ancient ruin rewards from the game when religion is disabled

"Hidden when [victoryType] victory is disabled"

"Only available after [amount] turns" - To be used for ancient ruin rewards

"Hidden after founding a Pantheon" - same

"Hidden before founding a Pantheon" - same

"Hidden after generating a Great Prophet" - same

"+[amount]% attacking strength for cities with garrisoned units"

"+[amount]% defensive strength for cities"

"+[amount] happiness from each type of luxury resource"

"Quantity of Resources gifted by City-States increased by [amount]%"

"\"Borrows\" city names from other civilizations in the game" - Civs with this unique will start using city names of other civs in the game once they have run out of city names instead of doing the "new [cityName]" thing.

"Cities are razed [] times as fast"

"Retain [amount]% of the happiness from a luxury after the last copy has been traded away"

"When declaring friendship, both parties gain a [amount]% boost to great people generation"

"Hidden after generating a Great Prophet" - Used for ancient ruins to disable a reward after generating a great prophet

"May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] at an increasing price ([amount])" - Price increases quadratically based on the the "amount" parameters

"May buy [constructionFilter] buildings for [amount] [stat] [cityFilter] at an increasing price ([amount])" - same

"May buy [baseUnitFilter] units for [amount] [stat] [cityFilter]"

"May buy [constructionFilter] buildings for [amount] [stat] [cityFilter]"

"May buy [baseUnitFilter] units with [stat] [cityFilter]"

"May buy [constructionFilter] buildings with [stat] [cityFilter]"

"May buy [baseUnitFilter] units with [stat] for [amount] times their normal Production cost"

"May buy [constructionFilter] buildings with [stat] for [amount] times their normal Production cost"


"May not generate great prophet equivalents naturally"

"Starting in this era disables religion" - Can only be used in uniques from eras.json

"Incompatible with [param]" - Can be used for incompatible policies, techs & promotions

"[amount]% of excess happiness converted to [Stat]"

"May choose [amount] additional [beliefType] beliefs when [param] a religion" - param may be "founding" or "enhancing", beleifType may be "Pantheon", "Follower", "Founder", "Enhancer"

May choose [amount] additional belief(s) of any type when [param] a religion


## Buildings-only

"Doubles Gold given to enemy if city is captured"

"[amount]% of food is carried over [cityFilter] after population increases"

"All newly-trained [unitFilter] units [cityFilter] receive the [promotionName] promotion"

"[amount]% great person generation [cityFilter]"

"Provides 1 extra copy of each improved luxury resource near this City"

"Remove extra unhappiness from annexed cities"

"Provides 1 happiness per 2 additional social policies adopted"

"Cannot be purchased"

"Cost increases by [amount] per owned city"

"Unsellable"

"Triggers a global alert upon build start"

"Triggers a global alert upon completion"

"Triggers a Cultural Victory upon completion"

"Population loss from nuclear attacks [amount]% [cityFilter]"

"New [unitFilter] units start with [amount] Experience [cityFilter]"

"Destroyed when the city is captured"

"Never destroyed when the city is captured"

"[unitFilter] units built [cityFilter] can [unitAction] [amount] extra times" - "unitAction" may be one of: "Spread Religion", "Remove Foreign religions from your own cities"

"Provides a free [buildingName] [cityFilter]" © - Provides a maintenance-free copy of the 'building' in all cities of the owner of this building in all cities matching 'cityFilter'. If the city having this building is captured, all free buildings previously provided are removed, and are added to cities of its new owner instead.

"Gain a free [buildingName] [cityFilter]" © - Will be merged with "Provides a free [buildingName] [cityFilter]" sometime in the future

### Stat uniques

"[stats] per [amount] population [cityFilter]" - provides the given stats for every [amount] of population. For instance, "[+2 Science] Per [2] Population in this city" would provide only 4 Science in a city with 5 population - since there are only 2 'sets' of 2 population in the city, each providing 2 Science.

"[stats] from [tileFilter] tiles [cityFilter]" - Adds the given stats to the yield of tiles matching the filter. The yield is still received by the tiles being worked - so even if you have 5 such tiles, but none of them are worked, the city will remain unaffected.

"[stats] once [techName] is discovered"

### Construction condition uniques

"Consumes [amount] [resourceName]" - Acts like the requiredResource field, but can be added multiple times to the same building and with varying amounts. Buildings will require the resources to be constructed, and will continue to consume them as long as the building exists.

"Must be on [terrainFilter]", "Must not be on [terrainFilter]" - limits the buildings that can be built in a city according the the tile type that the city center is built on.

"Must be next to [tileFilter]", "Must not be next to [tileFilter]" - Same. In addition to the regular tileFilter options, accepts "Fresh water" as an option, which includes river-adjacent tiles on top of lake-adjacent tiles (which is merely a consequence of Lakes having a 'Fresh water' uniques and tileFilter accepting Base Terrain uniques)

"Must have an owned [tileFilter] within [amount] tiles"

"Requires a [buildingName] in all cities"

"Can only be built in annexed cities"

"Obsolete with [techName]" - Building cannot be built once the tech is researched

"Hidden until [amount] social policy branches have been completed"

## Religion uniques

### Follower uniques

Follower uniques are uniques applied to each city following a religion which includes this unique. It is also possible for general uniques to be used for beliefs, the ones below are specifically for religions.

"[amount]% attacking Strength for cities"

"[amount]% cost of natural border growth"

"[stats] in cities with [amount] or more population"

"[stats] from cities on [tileFilter] tiles"

"[unitFilter] Units adjacent to this city heal [amount] HP per turn when healing"

"[stats] from [tileFilter] tiles without [tileFilter] [cityFilter]"

"Earn [amount]% of [unitFilter] unit's [param] as [Stat] when killed within 4 tiles of a city following this religion"

## Improvement uniques

"[stats] from [tileFilter] tiles"

"[stats] for each adjacent [tileFilter]"


"Can also be built on tiles adjacent to fresh water"

"Can be built outside your borders"

"Can be built just outside your borders" - one tile outside your borders

"Cannot be built on [tileFilter] tiles"

"Cannot be built on [tileFilter] tiles until [techName] is discovered"

"Cannot be built on bonus resource"

"Does not need removal of [tileFilter]"


"Gives a defensive bonus of [amount]%"

"Costs [amount] gold per turn when in your territory"

"Deal [amount] damage to adjacent enemy units"

"Obsolete with [techName]"

"Great Improvement"

"Tile provides yield without assigned population"

"Unpillageable"

"Indestructable" - Cannot be removed by nukes (might be used more later, for now only this)

"Provides a random bonus when entered" - Effectively makes this improvement ancient ruins, including it being removed when it is entered.

"Consumes [amount] [resource]" - The AI does _not_ consider this unique when deciding which improvement to build



## Unit uniques

### One time effect units

May be added in promotions or ancient ruins equivalents


"Heal this unit by [] HP"

"This Unit gains [] XP"

"This Unit upgrades for free"

"This Unit upgrades for free including special upgrades" - For example scout -> archer in the base game

"This Unit gains the [] promotion"


### Civilian

"Can build [improvementFilter] improvements on tiles"
    - Note this can also take a terrainFilter

"May create improvements on water resources"

"Founds a new city"

"Can undertake a trade mission with City-State, giving a large sum of gold and [amount] Influence"

"Can start an 8-turn golden age"

"Great Person - [Stat]"

"Can hurry technology research"

"Can speed up construction of a wonder"

"Can speed up construction of a building" - Same as above, but capped and can be used for buildings also

"Can construct [improvement]" - destroys the unit upon construction

"Can construct [improvement] if it hasn't used other actions yet" - as above, but only if the unit hasn't used its other actions yet

"Unbuildable"

"May found a religion"

"May enhance a religion"


### Visibility

"[amount] Sight"

"6 tiles in every direction always visible"

"Normal vision when embarked"


### Movement

"Double movement in coast"

"Ignores terrain cost"

"Ignores Zone of Control"

"Cannot enter ocean tiles until Astronomy"

"Can move immediately once bought"

"Can move after attacking"

"Can enter ice tiles"

"No movement cost to pillage"

"May Paradrop up to [amount] tiles from inside friendly territory"

"Transfer Movement to [unitFilter]"

"May enter foreign tiles without open borders, but loses [amount] religious strength each turn it ends there"

"May enter foreign tiles without open borders"


### Healing

"Heal this unit by [amount] HP" - Only for promotions

"[amount] HP when healing"

"All adjacent units heal [amount] extra HP when healing"

"[amount] HP when healing in [tileFilter] tiles"

"Heals [amount] damage if it kills an Unit"

"Heal adjacent units for an additional 15 HP per turn"

"May heal outside of friendly territory" - For water units mostly


### Combat bonuses

"[amount]% Strength" ©

"No defensive terrain bonus"

"+[amount]% Strength for [unitFilter] units which have another [unitFilter] unit in an adjacent tile"

"Eliminates combat penalty for attacking over a river"

"Eliminates combat penalty for attacking from the sea"

"[amount]% Strength for enemy [unitFilter] units in adjacent [tileFilter] tiles"

"[amount]% Strength when stacked with [unitFilter]"

### Other

"[amount] additional attacks per turn"

"[amount] Range" - ex.: "[+1] Range"

"Doing so will consume this opportunity to choose a Promotion" - Used for promotions. Adding this will give immediate effects of the promotion, but not the permanent effects. The promotion can also be chosen again later.

"Self-destructs when attacking" - for single use units, like missiles

"Penalty vs (unitName/unitType) 33%"

"Bonus for units in 2 tile radius 15%"

"Requires Manhattan Project"

"Must set up to ranged attack"

"Ranged attacks may be performed over obstacles"

"May withdraw before melee ([amount]%)"

"Can see invisible [unitFilter] units"

"Can carry [amount] [unitFilter] units" - Currently, only Air & Missile units can be carried

"Can carry [amount] extra [unitFilter] units" - Currently, only Air & Missile units can be carried

"Cannot be carried by [unitFilter] units" - Currently, only Air & Missile units can be carried, and only Carrier units can carry them

"Invisible to others"

"Invisible to non-adjacent units"

"Can only attack [unitFilter] units"

"Can only attack [tileFilter] tiles"

"Not displayed as an available construction without [resourceName/buildingName]"

"[amount]% chance to intercept air attacks"

"[amount] extra interceptions may be made per turn"

"[amount]% Damage when intercepting" - for intercepting units, not for the intercepted unit

"Damage taken from interception reduced by [amount]%"

"Cannot be intercepted"

"Nuclear weapon of Strength [amount]" - Amount should be 1 or 2. 1 is effectively an atomic bomb, 2 is a nuclear missile in the base game

"Blast radius [amount]" - Amount is the radius of the blast of a nuke

"May capture killed [unitFilter] units"

"Earn [amount]% of the damage done to [unitFilter] units as [Stat]" - stat must be Gold, Culture, Science or Faith. If a unit would do more damage than the defender has health, the damage will instead be the health the defender had left before attacking.

"Religious unit" - Will make sure that the unit has a religion upon being built/purchased in a city with a religion

"Takes your religion over the one in their birth city" - Whenever a unit with this unique spawns, is purchased or built, it will take the religion of your civilization instead of the religion of the city in which it appears.

"Removes other religions when spreading religion" - removes all existing pressure from other religions during the 'spread religion' action

"May upgrade to [unitName] through ruins-like effects"

"Can [unitAction] [amount] times" - "unitAction" may be one of: "Spread Religion", "Remove Foreign religions from your own cities"

"Prevents spreading of religion to the city it is next to"

"Can be purchased with [Stat] [cityFilter]"

"Can be purchased for [amount] [Stat] [cityFilter]"

"[amount]% Religious Spread Strength"


# Terrain uniques
Some of the terrain uniques have gameplay effect, others are used only for map generation:

### Terrain uniques - gameplay

"[(+/-)amount] Strength for cities built on this terrain"

"[(+/-)amount] Sight for [unitFilter] units"

"Has an elevation of [amount] for visibility calculations" - For example, in the base setting, mountains are 4, hills are 2, and jungles and forests are 1. Higher tiles hide the tiles behind them, and from higher tiles you can see over lower tiles.

"Blocks line-of-sight from tiles at same elevation" - e.g. Forest and Jungle for Civ V rules

"Resistant to nukes" - Tiles with features with this unique have only a 25% change to be filled with fallout instead of 50%

"Can be destroyed by nukes" - Features with this unique will be removed when fallout is placed on this tile

"Rough terrain"

"Fresh water"

"Grants 500 Gold to the first civilization to discover it" - given to natural wonders

"Grants Rejuvenation (all healing effects doubled) to adjacent military land units for the rest of the game" (Fountain of Youth)

"Nullifies all other stats this tile provides"

"Units ending their turn on this terrain take [amount] damage"

"Provides a one-time Production bonus to the closest city when cut down"

"Only [improvementFilter] improvements may be built on this tile"




### Terrain uniques - map generation

"Occurs at temperature between [0.8] and [1] and humidity between [0] and [0.7]" - This allows modding freedom in map generation. Temperature is between -1 and 1, humidity is between 0 and 1. Since this is a large 2*1 rectangle, and every individual terrain also covers one or more rectangles, you may find it helpful to draw out on paper the temperature and humidity graph, and see where there are missing pieces that aren't covered.

"Occurs in groups around high elevations"

"Occurs in chains at high elevations"

"Must be adjacent to [amount] [terrainFilter] tiles"

"Must be adjacent to [amount] to [amount] [terrainFilter] tiles"

"Must not be on [amount] largest landmasses"

"Occurs on latitudes from [amount] to [amount] percent of distance equator to pole"

"Occurs in groups of [amount] to [amount] tiles"

"Neighboring tiles will convert to [baseTerrain]"

"Neighboring tiles except [terrainFilter] will convert to [baseTerrain]"

"Rare feature"

"Always Fertility [amount] for Map Generation" - see [Regions](./Regions.md)

"[amount] to Fertility for Map Generation" - see [Regions](./Regions.md)

"A Region is formed with at least [amount]% [simpleTerrain] tiles, with priority [amount]" - see [Regions](./Regions.md)

"A Region is formed with at least [amount]% [simpleTerrain] tiles and [simpleTerrain] tiles, with priority [amount]" - see [Regions](./Regions.md)

"A Region can not contain more [simpleTerrain] tiles than [simpleTerrain] tiles" - see [Regions](./Regions.md)

"Base Terrain on this tile is not counted for Region determination" - see [Regions](./Regions.md)

"Considered [terrainQuality] when determining start locations" - see [Regions](./Regions.md)


# Resource Uniques

"+15% production towards Wonder construction"

"Can only be created by Mercantile City-States"

"Deposits in [tileFilter] tiles always provide [amount] resources"


# ModOptions Uniques
These are valid only in a [ModOptions.json](./Miscellaneous-JSON-files.md#modoptionsjson) file.

|unique|comment|
|------|-------|
| Allow City States to spawn with additional units | CS get the same starting units as major civs |
| Can convert gold to science with sliders | Adds sliders to the Overview screen Stats page |
| Diplomatic relationships cannot change | Prevent changes in diplomatic relations |
| Disable religion | disables everything to do with religion |
| Can trade civilization introductions for [] Gold | Allows for trading of civilization introductions |
|  |  |


# Deprecated Uniques
These uniques have been recently deprecated. While they are still supported, they should be phased out of mods, as we will remove support for them in the future. Deprecated uniques are usually replaced with a more generic version that can be used in their place. These replacements are noted here as well.

"Immediately creates a cheapest available cultural building in each of your first 4 cities for free" - Replaced with "Immediately creates the cheapest available cultural building in each of your first [amount] cities for free"

"+50% attacking strength for cities with garrisoned units" - Replaced with "+[amount]% attacking strength for cities with garrisoned units"

"+15% combat strength for melee units which have another military unit in an adjacent tile" - Replaced with "+[amount]% Strength for [unitFilter] units which have another [unitFilter] unit in an adjacent tile"

"Gold cost of upgrading military units reduced by 33%" - Replaced with "Gold cost of upgrading [unitFilter] units reduced by [amount]%"

"+1 happiness from each type of luxury resource" - Replaced with "+[amount] happiness from each type of luxury resource"

"+15% science while the empire is happy" - Replaced with "[amount]% [Stat] while the empire is happy"

"Science gained from research agreements +50%" - Replaced with "Science gained from research agreements [+amount]%"

"Specialists only produce half normal unhappiness" - Replaced with "Specialists only produce [amount]% of normal unhappiness"

"-50% food consumption by specialists" - Replaced with "[amount]% food consumption by specialists"

"-[amount]% food consumption by specialists" - Replaced with "[amount]% food consumption by specialists"

"-33% unit upkeep costs" - Replaced with "[-3]% maintenance costs for [all] units"

"Quantity of Resources gifted by City-States increased by 100%" - Replaced with "Quantity of Resources gifted by City-States increased by [amount]%"

"-[amount]% building maintenance costs []" - Replaced with "[-amount]% maintenance cost for buildings []"

"Allied City-States provide Science equal to [amount]% of what they produce for themselves" - Replaced with "Allied City-States provide [Stat] equal to [amount]% of what they produce for themselves"

"Reduces damage taken from interception by 50%" - Replaced with "Damage taken from interception reduced by [amount]%"

"Can not be intercepted" - Replaced with "Cannot be intercepted"

"Heal this Unit by 50 HP; Doing so will consume this opportunity to choose a Promotion" - Replaced with "Heal this Unit by [amount] HP" and "Doing so will consume this opportunity to choose a Promotion"

"This unit and all others in adjacent tiles heal 5 additional HP. This unit heals 5 additional HP outside of friendly territory." - Replaced with "[amount] HP when healing" and "All adjacent units heal [amount] extra HP when healing" and "[amount] HP when healing in [tileFilter] tiles"

"+1 Visibility Range" - Replaced with "[amount] Visibility Range"

"+2 Visibility Range" - Replaced with "[amount] Visibility Range"

"Can only attack water" - Replaced with "Can only attack [unitFilter] units" or "Can only attack [tileFilter] tiles", depending on use

"[stats] from [tileFilter] tiles in this city" - Replaced with "[stats] from [tileFilter] tiles [cityFilter]"

"+[amount]% great person generation in this city" - Replaced with "[amount]% great person generation [cityFilter]"

"+[amount]% great person generation in all cities" - Replaced with "[amount]% great person generation [cityFilter]"

"New [unitFilter] units start with [amount] Experience in this city" - Replaced with "New [unitFilter] units start with [amount] Experience [cityFilter]"

"[amount]% of food is carried over after population increases" - Replaced with "[amount]% of food is carried over [cityFilter] after population increases"

"Can attack submarines" - Replaced with "Can see invisible [unitFilter] units"

"Unhappiness from population decreased by [amount]%" - Replaced with "[amount]% Unhappiness from population [cityFilter]"

"[stats] from every specialist" - Replaced with "[stats] from every specialist [cityFilter]"

"+2 Culture per turn from cities before discovering Steam Power" - Replaced with "[stats] per turn from cities before [techName]"

"Population loss from nuclear attacks -[amount]%" - Replaced with "Population loss from nuclear attacks [amount]% [cityFilter]"

"Specialists only produce [amount]% of normal unhappiness" - Replaced with "[signedAmount]% unhappiness from specialists [cityFilter]"

"Increases embarked movement +1" - Replaced with "+[1] Movement for all [Embarked] units"

"+1 Movement for all embarked units" - Replaced with "+[1] Movement for all [Embarked] units"

"50% of excess happiness converted to Culture" - Replaced with "[amount]% of excess happiness converted to [Stat]"

"Immediately creates the cheapest available cultural building in each of your first [amount] cities for free" - Replaced with "Provides the cheapest [Culture] building in your first [amount] cities for free"

"Immediately creates a [buildingName] in each of your first [amount] cities for free" - Replaced with "Provides a [buildingName] in each of your first [amount] cities for free"

"+[amount] Sight for all [unitFilter] units" - Replaced with "[amount] Sight for all [unitFilter] units"

"+[amount]% growth [cityFilter]" - Replaced with "[amount]% growth [cityFilter]" ©

"[signedAmount]% growth [cityFilter] when not at war" - Replaced with "[signedAmount]% growth [cityFilter] <when not at war>" ©

"[stats] if this city has at least [amount] specialists" - Repalaced with "[stats] <if this city has at least [amount] specialists>" ©

"-[amount]% unit upkeep costs" - Replaced with "[-amount]% maintenance costs for [all] units"

"+[amount]% [Stat] while the empire is happy" - Replaced with "[amount]% [Stat] <while the empire is happy>"

"+[amount]% Strength vs [unitFilter]" - Replaced with "[amount]% Strength <vs [unitFilter] units>" or "[amount]% Strength <vs cities>"

"+[amount]% Combat Strength" - Replaced with "[amount]% Strength"

"+[amount]% Strength when attacking" - Replaced with "[amount]% Strength \<when attacking>"

"+[amount]% Strength when defending" - Replaced with "[amount]% Strength \<when defending>"

"[amount]% Strength when defending vs [unitFilter]" - Replaced with "[amount]% Strength \<when defending> \<vs [unitFilter] units>"

"+[amount]% Strength in [tileFilter]" - Replaced with "[amount]% Strength \<in [tileFilter] tiles>"

"[stats] once [techName] is discovered" - Replace with "[stats] \<after discovering [techname]>"
