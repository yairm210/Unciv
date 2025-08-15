# Unique parameters

This page contains an overview of all different parameters used in uniques and what values can be filled into them.
Each of the different parameter types is described below, including all possible values for them.
These are split into two categories:

- descriptions: e.g., "the name of any unit"
- `Text that looks like this`. This last one must be used exactly the same

Note that all of these are case-sensitive!

## General Filter Rules

All filters except for `populationFilter` and `resourceFilter` accept multiple values in the format: `{A} {B} {C}` etc, meaning "the object must match ALL of these filters"

> Example: `[{Military} {Water}] units`, `[{Wounded} {Armor}] units`, etc.

No space or other text is allowed between the `[` and the first `{`, nor between the last `}` and the ending `]`. The space in `} {`, however, is mandatory.

All filters accept `non-[filter]` as a possible value

> Example: `[non-[Wounded]] units`

These can be combined by nesting, with the exception that an "ALL" filter cannot contain another "ALL" filter, even with a NON-filter in between.

> Example: `[{non-[Wounded]} {Armor}] units` means unit is type Armor and at full health.
> Example: `[non-[{Wounded} {Armor}]] units` means unit is neither wounded nor an Armor one.

`[{non-[{Wounded} {Armor}]} {Embarked}] units` WILL FAIL because the game will treat both "} {" at the same time and see `non-[{Wounded` and `Armor}]`, both invalid.

Display of complex filters in Civilopedia may become unreadable. If so, consider hiding that unique and provide a better wording using the `Comment []` unique separately.

## civFilter

Allows filtering for specific civs.

Allowed values:

- `Human player`
- `AI player`
- [nationFilter](#nationfilter)

## nationFilter

Allows filtering for specific nations. Used by [ModOptions.nationsToRemove](Mod-file-structure/5-Miscellaneous-JSON-files.md#modoptionsjson).

Allowed values:

- `All`
- `City-States`, `City-State`
- `Major`
- Nation name
- A unique a Nation has (verbatim, no placeholders)

## baseUnitFilter

Unit filters can be divided up into two parts: `baseUnitFilter`s and `mapUnitFilter`s.
The former is tested against the unit as it appears in the json.
This means it doesn't have an owning civ or tile it stands on, just its base properties.
The latter is tested against the unit as it appears on the map, including its nation, tile, health and all other properties.

Allowed values:

- unit name
- unit type - e.g. Melee, Ranged, WaterSubmarine, etc.
- `Land`, `Water`, `Air`
- `land units`, `water units`, `air units`
- `non-air` for non-air non-missile units
- `Military`, `military units`
- `Civilian`, `civilian units`
- `All`
- `Melee`
- `Ranged`
- `Nuclear Weapon`
- `Great Person`, `Great`
- `Embarked`
- Matching [technologyfilter](#technologyfilter) for the tech this unit requires - e.g. `Modern Era`
- Any exact unique the unit has
- Any exact unique the unit type has
- Any combination of the above (will match only if all match). The format is `{filter1} {filter2}` and can match any number of filters. For example: `[{Modern era} {Land}]` units

## mapUnitFilter

This indicates a unit as placed on the map. Compare with `baseUnitFilter`.

Allowed values:

- Any matching [baseUnitFilter](#baseunitfilter)
- Any [civFilter](#civfilter) matching the owner
- Any unique the unit has - also includes uniques not caught by the [baseUnitFilter](#baseunitfilter), for example promotions
- Any promotion name
- `Wounded`
- `Embarked`
- `City-State`
- `Barbarians`, `Barbarian`
- Again, any combination of the above is also allowed, e.g. `[{Wounded} {Water}]` units.

You can check this in-game using the console with the `unit checkfilter <filter>` command

## buildingFilter

Allows to only activate a unique for certain buildings.

Allowed values:

- `All`
- `Buildings`, `Building`
- `Wonder`, `Wonders`
- `National Wonder`, `National`
- `World Wonder`, `World` -- All wonders that are not national wonders
- building name
- The name of the building it replaces (so for example uniques for libraries will apply to paper makers as well)
- Matching [technologyfilter](#technologyfilter) for the tech this building requires - e.g. Modern Era
- An exact unique the building has (e.g.: `spaceship part`)
- `Culture`, `Gold`, etc. if the building is `stat-related` for that stat. Stat-related buildings are defined as one of the following:
    - Provides that stat directly (e.g. +1 Culture)
    - Provides a percentage bonus for that stat (e.g. +10% Production)
    - Provides that stat as a bonus for resources (e.g. +1 Food from every Wheat)
    - Provides that stat per some amount of population (e.g. +1 Science for every 2 population [cityFilter])
- Any combination of the above (will match only if all match). The format is `{filter1} {filter2}` up to any number of filters. For example `[{Ancient era} {Food}]` buildings.

## cityFilter

cityFilters allow us to choose the range of cities affected by this unique:

- `in this city`
- `in all cities`
- `in your cities`, `Your`
- `in all coastal cities`, `Coastal`
- `in capital`, `Capital`
- `in all non-occupied cities`, `Non-occupied` - all cities that are not puppets and don't have extra unhappiness from being recently conquered
- `in all cities with a world wonder`
- `in all cities connected to capital`
- `in all cities with a garrison`, `Garrisoned`
- `in non-enemy foreign cities` - In all cities owned by civs other than you that you are not at war with
- `in enemy cities`, `Enemy`
- `in foreign cities`, `Foreign`
- `in annexed cities`, `Annexed`
- `in puppeted cities`, `Puppeted`
- `in cities being razed`, `Razing`
- `in holy cities`, `Holy`
- `in City-State cities`
- `in cities following this religion` - Should only be used in pantheon/follower uniques for religions
- `in cities following our religion`
- `in all cities in which the majority religion is a major religion`
- `in all cities in which the majority religion is an enhanced religion`
- [civFilter]

You can check this in-game using the console with the `city checkfilter <filter>` command

## improvementFilter

For filtering a specific improvement.

Allowed values:

- improvement name
- `All`
- `Great Improvements`, `Great`
- `All Road` - for Roads & Railroads

## populationFilter

A filter determining a part of the population of a city.

Allowed values:

- `Population`
- `Specialists`
- `Unemployed`
- `Followers of the Majority Religion` or `Followers of this Religion`, both of which only apply when this religion is the majority religion in that city
- Specialist names

## religionFilter

For filtering specific relgions

- `any`
- `major`
- `enhanced`
- `your`
- `foriegn`
- `enemy`
- The name of a relgion symbol
- The name of a belief
- A unique of a belief the religion has

## policyFilter

Allowed values:

- `All` or `all`
- `[policyBranchName] branch`
- The name of the policy
- A unique the Policy has (verbatim, no placeholders)

## combatantFilter

Allowed values:

- [mapUnitFilter](#mapunitfilter), for unit combatants
- `City`, `All`, or [civFilter](#civfilter), for city combatants

Since mapUnitFilter contains civFilter, that means civFilter can be applied to combatantFilter for both units and cities.

## regionType

Used for dividing the world into regions in each of which a single player is placed at the start of the game.
Allowed values are `Hybrid` and the name of any terrain that has one of the following two uniques:

- `A Region is formed with at least [amount]% [simpleTerrain] tiles, with priority [amount]`
- `A Region is formed with at least [amount]% [simpleTerrain] tiles and [simpleTerrain] tiles, with priority [amount]`

## simpleTerrain

Used by NaturalWonderGenerator to place natural wonders

Allowed values:

- `Land`
- `Water`
- `Elevated`
- The name of any terrain

## stats

This indicates a text comprised of specific stats and is slightly more complex.

Each stats is comprised of several stat changes, each in the form of `+{amount} {stat}`,
where 'stat' is one of the seven major stats
(eg `Production`, `Food`, `Gold`, `Science`, `Culture`, `Happiness` and `Faith`).
For example: `+1 Science`.

These can be strung together with ", " between them, for example: `+2 Production, +3 Food`.

## resourceFilter

Allowed values:

- Resource name
- `any`, `all`
- Resource type: `Strategic`, `Luxury`, `Bonus`
- Stat provided by the resource when improved (e.g. `Food`)

## stockpiledResource

This indicates a text that corresponds to a custom Stockpile Resource.

These are global civilization resources that act similar to the main Civ-wide resources like `Gold` and `Faith`.
You can generate them and consume them. And actions that would consume them are blocked if you
don't have enough left in stock.

To use, you need to first define a TileResources with the "Stockpiled" Unique. Then you can reference
them in other Uniques.

## Stockpile

Something that can be added to a civ

Allowed values:

- stockpiled resource (see above)
- Stat name - for global stats, not city stats
- `Stored Food` (for cities)
- `Golden Age points`

## technologyFilter

At the moment only implemented for [ModOptions.techsToRemove](Mod-file-structure/5-Miscellaneous-JSON-files.md#modoptionsjson).

Allowed values:

- `All`
- The name of an Era
- The name of a Technology
- A unique a Technology has (verbatim, no placeholders)

## terrainFilter

This indicates the terrain on a single tile.

Allowed values:

- A filter names a specific json attribute (by name):
    - Base terrain
    - Terrain features
    - Base terrain uniques
    - Terrain feature uniques
    - Resource
    - Natural wonder
    - A [nationFilter](#nationfilter) matching the tile owner
- Or the filter is a constant string choosing a derived test:
    - `All`
    - `Terrain`
    - `Water`, `Land`
    - `Coastal` (at least one direct neighbor is a coast)
    - `River` (as in all 'river on tile' contexts, it means 'adjacent to a river on at least one side')
    - `Open terrain`, `Rough terrain` (note all terrain not having the rough unique is counted as open)
    - `Friendly Land` - land belonging to you, or other civs with open borders to you
    - `Foreign Land` - any land that isn't friendly land
    - `Enemy Land` - any land belonging to a civ you are at war with
    - `your` - land belonging to you
    - `unowned` - land that is not owned by any civ
    - `Water resource`, `Strategic resource`, `Luxury resource`, `Bonus resource`, `resource`
    - `Natural Wonder` (as opposed to above which means testing for a specific Natural Wonder by name, this tests for any of them)

Please note all of these are _case-sensitive_.

Note: Resource filters depend on whether a viewing civ is known in the context where the filter runs. Water and specific tests require a viewing civ, and if the resource needs a tech to be visible, that tech to be researched by the viewing civ. The other resource category tests can succeed without a known viewing civ only for resources not requiring any tech. So - test your mod!

So for instance, the unique "[stats] from [tileFilter] tiles [cityFilter]" can match several cases:

## tileFilter

Allowed values:

- [terrainFilter](#terrainfilter) for this tile
- [improvementFilter](#improvementfilter) for this tile
- `Improvement` or `improved` for tiles with any improvements
- `unimproved` for tiles with no improvement
- `pillaged` for pillaged tiles
- `worked` for tiles worked by a city

You can check this in-game using the console with the `tile checkfilter <filter>` command

## terrainQuality

Used to indicate for what use the terrain should be viewed when dividing the world into regions, in each of which a single player is placed at the start of the game.

Allowed values:

- `Undesirable`
- `Food`
- `Desirable`
- `Production`

## countable

Indicates *something that can be counted*, used both for comparisons and for multiplying uniques

Allowed values:

[//]: # (Countables automatically generated BEGIN)
-   Integer constant - any positive or negative integer number
    - Example: `Only available <when number of [123] is more than [0]>`
-   `turns` - Number of turns played
    - Example: `Only available <when number of [turns] is more than [0]>`
    - Always starts at zero irrespective of game speed or start era
-   `year` - The current year
    - Example: `Only available <when number of [year] is more than [0]>`
    - Depends on game speed or start era, negative for years BC
-   `Cities` - The number of cities the relevant Civilization owns
    - Example: `Only available <when number of [Cities] is more than [0]>`
-   `Units` - The number of units the relevant Civilization owns
    - Example: `Only available <when number of [Units] is more than [0]>`
-   Stat name (`Production`, `Food`, `Gold`, `Science`, `Culture`, `Happiness` or `Faith`)
    - Example: `Only available <when number of [Science] is more than [0]>`
    - Gets the stat *reserve*, not the amount per turn (can be city stats or civilization stats, depending on where the unique is used)
-   Stat name Per Turn (`Production`, `Food`, `Gold`, `Science`, `Culture`, `Happiness` or `Faith`)
    - Example: `Only available <when number of [[Culture] Per Turn] is more than [0]>`
    - Gets the amount of a stat the civilization gains per turn
-   `Completed Policy branches`
    - Example: `Only available <when number of [Completed Policy branches] is more than [0]>`
-   `[cityFilter] Cities`
    - Example: `Only available <when number of [[in all cities] Cities] is more than [0]>`
-   `[mapUnitFilter] Units`
    - Example: `Only available <when number of [[Wounded] Units] is more than [0]>`
-   `[buildingFilter] Buildings`
    - Example: `Only available <when number of [[Culture] Buildings] is more than [0]>`
-   `Adopted [policyFilter] Policies`
    - Example: `Only available <when number of [Adopted [Oligarchy] Policies] is more than [0]>`
-   `Remaining [civFilter] Civilizations`
    - Example: `Only available <when number of [Remaining [City-States] Civilizations] is more than [0]>`
-   `Owned [tileFilter] Tiles`
    - Example: `Only available <when number of [Owned [Farm] Tiles] is more than [0]>`
-   `[tileFilter] Tiles`
    - Example: `Only available <when number of [[Desert] Tiles] is more than [0]>`
-   Resource name - From [TileResources.json](3-Map-related-JSON-files.md#tileresourcesjson)
    - Example: `Only available <when number of [Iron] is more than [0]>`
    - Can be city stats or civilization stats, depending on where the unique is used
    - For example: If a unique is placed on a building, then the retrieved resources will be of the city. If placed on a policy, they will be of the civilization.
    - This can make a difference for e.g. local resources, which are counted per city.
-   `Era number` - Number of the era the current player is in
    - Example: `Only available <when number of [Era number] is more than [0]>`
    - Zero-based index of the Era in Eras.json.
-   `Speed modifier for [stat]` - A game speed modifier for a specific Stat, as percentage
    - Example: `Only available <when number of [Speed modifier for [Culture]] is more than [0]>`
    - Chooses an appropriate field from the Speeds.json entry the player has chosen.
    - It is returned multiplied by 100.
    - Food and Happiness return the generic `modifier` field.
    - Other fields like `goldGiftModifier` or `barbarianModifier` are not accessible with this Countable.
-   Evaluate expressions!
    - Example: `Only available <when number of [[Iron] + 2] is more than [0]>`
    - Expressions support arbitrary math operations, and can include other countables, when surrounded by square brackets.
    - For example, since `Cities` is a countable, and `[Melee] units` is a countable, you can have something like: `([[Melee] units] + 1) / [Cities]` (the whitespace is optional but helps readability)
    - Since on translation, the brackets are removed, the expression will be displayed as `(Melee units + 1) / Cities`
    - Supported operations between 2 values are: +, -, *, /, %, ^
    - Supported operations on 1 value are: - (negation), √ (square root), abs (absolute value - turns negative into positive), sqrt (square root), floor (round down), ceil (round up)

[//]: # (Countables automatically generated END)
