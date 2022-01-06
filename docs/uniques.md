## Table of Contents

 - [Global uniques](#global-uniques)
 - [Nation uniques](#nation-uniques)
 - [FollowerBelief uniques](#followerbelief-uniques)
 - [Building uniques](#building-uniques)
 - [Unit uniques](#unit-uniques)
 - [Promotion uniques](#promotion-uniques)
 - [Terrain uniques](#terrain-uniques)
 - [Improvement uniques](#improvement-uniques)
 - [Resource uniques](#resource-uniques)
 - [Ruins uniques](#ruins-uniques)
 - [CityState uniques](#citystate-uniques)
 - [Conditional uniques](#conditional-uniques)
 - [Deprecated uniques](#deprecated-uniques)

## Global uniques
#### [stats]
Example: "[+1 Gold, +2 Production]"

Applicable to: Global, FollowerBelief, Improvement

#### [stats] [cityFilter]
Example: "[+1 Gold, +2 Production] [in all cities]"

Applicable to: Global, FollowerBelief

#### [stats] from every specialist [cityFilter]
Example: "[+1 Gold, +2 Production] from every specialist [in all cities]"

Applicable to: Global, FollowerBelief

#### [stats] per [amount] population [cityFilter]
Example: "[+1 Gold, +2 Production] per [20] population [in all cities]"

Applicable to: Global, FollowerBelief

#### [stats] in cities with [amount] or more population
Example: "[+1 Gold, +2 Production] in cities with [20] or more population"

Applicable to: Global, FollowerBelief

#### [stats] in cities on [terrainFilter] tiles
Example: "[+1 Gold, +2 Production] in cities on [Grassland] tiles"

Applicable to: Global, FollowerBelief

#### [stats] whenever a Great Person is expended
Example: "[+1 Gold, +2 Production] whenever a Great Person is expended"

Applicable to: Global

#### [stats] from [tileFilter] tiles [cityFilter]
Example: "[+1 Gold, +2 Production] from [Farm] tiles [in all cities]"

Applicable to: Global, FollowerBelief

#### [stats] from [tileFilter] tiles without [tileFilter] [cityFilter]
Example: "[+1 Gold, +2 Production] from [Farm] tiles without [Farm] [in all cities]"

Applicable to: Global, FollowerBelief

#### [stats] from every [tileFilter/specialist/buildingName]
Example: "[+1 Gold, +2 Production] from every [tileFilter/specialist/buildingName]"

Applicable to: Global, FollowerBelief

#### [amount]% [stat]
Example: "[20]% [Culture]"

Applicable to: Global, FollowerBelief

#### [amount]% [stat] from City-States
Example: "[20]% [Culture] from City-States"

Applicable to: Global

#### [amount]% [stat] [cityFilter]
Example: "[20]% [Culture] [in all cities]"

Applicable to: Global, FollowerBelief

#### [amount]% Production when constructing [buildingFilter] wonders [cityFilter]
Example: "[20]% Production when constructing [buildingFilter] wonders [in all cities]"

Applicable to: Global, FollowerBelief, Resource

#### [amount]% Production when constructing [buildingFilter] buildings [cityFilter]
Example: "[20]% Production when constructing [buildingFilter] buildings [in all cities]"

Applicable to: Global, FollowerBelief

#### [amount]% Production when constructing [baseUnitFilter] units [cityFilter]
Example: "[20]% Production when constructing [Melee] units [in all cities]"

Applicable to: Global, FollowerBelief

#### [amount]% unhappiness from population [cityFilter]
Example: "[20]% unhappiness from population [in all cities]"

Applicable to: Global, FollowerBelief

#### Military Units gifted from City-States start with [amount] XP
Example: "Military Units gifted from City-States start with [20] XP"

Applicable to: Global

#### Gifts of Gold to City-States generate [amount]% more Influence
Example: "Gifts of Gold to City-States generate [20]% more Influence"

Applicable to: Global

#### Can spend Gold to annex or puppet a City-State that has been your ally for [amount] turns.
Example: "Can spend Gold to annex or puppet a City-State that has been your ally for [20] turns."

Applicable to: Global

#### City-State territory always counts as friendly territory
Applicable to: Global

#### Allied City-States will occasionally gift Great People
Applicable to: Global

#### [amount] units cost no maintenance
Example: "[20] units cost no maintenance"

Applicable to: Global

#### [amount]% growth [cityFilter]
Example: "[20]% growth [in all cities]"

Applicable to: Global, FollowerBelief

#### Gain a free [buildingName] [cityFilter]
Example: "Gain a free [Library] [in all cities]"

Applicable to: Global

#### May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion
Example: "May choose [20] additional [Follower] beliefs when [foundingOrEnhancing] a religion"

Applicable to: Global

#### May choose [amount] additional belief(s) of any type when [foundingOrEnhancing] a religion
Example: "May choose [20] additional belief(s) of any type when [foundingOrEnhancing] a religion"

Applicable to: Global

#### [amount]% food consumption by specialists [cityFilter]
Example: "[20]% food consumption by specialists [in all cities]"

Applicable to: Global, FollowerBelief

#### [amount]% of excess happiness converted to [stat]
Example: "[20]% of excess happiness converted to [Culture]"

Applicable to: Global

#### May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] at an increasing price ([amount])
Example: "May buy [Melee] units for [20] [Culture] [in all cities] at an increasing price ([20])"

Applicable to: Global, FollowerBelief

#### May buy [buildingFilter] buildings for [amount] [stat] [cityFilter] at an increasing price ([amount])
Example: "May buy [buildingFilter] buildings for [20] [Culture] [in all cities] at an increasing price ([20])"

Applicable to: Global, FollowerBelief

#### May buy [baseUnitFilter] units for [amount] [stat] [cityFilter]
Example: "May buy [Melee] units for [20] [Culture] [in all cities]"

Applicable to: Global, FollowerBelief

#### May buy [buildingFilter] buildings for [amount] [stat] [cityFilter]
Example: "May buy [buildingFilter] buildings for [20] [Culture] [in all cities]"

Applicable to: Global, FollowerBelief

#### May buy [baseUnitFilter] units with [stat] [cityFilter]
Example: "May buy [Melee] units with [Culture] [in all cities]"

Applicable to: Global, FollowerBelief

#### May buy [buildingFilter] buildings with [stat] [cityFilter]
Example: "May buy [buildingFilter] buildings with [Culture] [in all cities]"

Applicable to: Global, FollowerBelief

#### May buy [baseUnitFilter] units with [stat] for [amount] times their normal Production cost
Example: "May buy [Melee] units with [Culture] for [20] times their normal Production cost"

Applicable to: Global, FollowerBelief

#### May buy [buildingFilter] buildings with [stat] for [amount] times their normal Production cost
Example: "May buy [buildingFilter] buildings with [Culture] for [20] times their normal Production cost"

Applicable to: Global, FollowerBelief

#### Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once.
Example: "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once."

Applicable to: Global

#### Once The Long Count activates, the year on the world screen displays as the traditional Mayan Long Count.
Applicable to: Global

#### Retain [amount]% of the happiness from a luxury after the last copy has been traded away
Example: "Retain [20]% of the happiness from a luxury after the last copy has been traded away"

Applicable to: Global

#### Enables Research agreements
Applicable to: Global

#### Triggers victory
Applicable to: Global

#### Triggers a Cultural Victory upon completion
Applicable to: Global

#### Cannot build [baseUnitFilter] units
Example: "Cannot build [Melee] units"

Applicable to: Global

#### [amount]% Strength
Example: "[20]% Strength"

Applicable to: Global, Unit

#### [amount]% Strength decreasing with distance from the capital
Example: "[20]% Strength decreasing with distance from the capital"

Applicable to: Global, Unit

#### [amount] Movement
Example: "[20] Movement"

Applicable to: Global, Unit

#### [amount] Sight
Example: "[20] Sight"

Applicable to: Global, Unit, Terrain

#### [amount]% Spread Religion Strength
Example: "[20]% Spread Religion Strength"

Applicable to: Global, Unit

#### Normal vision when embarked
Applicable to: Global, Unit

#### [amount]% maintenance costs
Example: "[20]% maintenance costs"

Applicable to: Global, Unit

#### [greatPerson] is earned [amount]% faster
Example: "[greatPerson] is earned [20]% faster"

Applicable to: Global, Unit

#### Upon capturing a city, receive [amount] times its [stat] production as [stat] immediately
Example: "Upon capturing a city, receive [20] times its [Culture] production as [Culture] immediately"

Applicable to: Global, Unit

#### Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [stat]
Example: "Earn [20]% of killed [Wounded] unit's [costOrStrength] as [Culture]"

Applicable to: Global, Unit

#### [amount] XP gained from combat
Example: "[20] XP gained from combat"

Applicable to: Global, Unit

#### [amount]% XP gained from combat
Example: "[20]% XP gained from combat"

Applicable to: Global, Unit

#### Free [baseUnitFilter] appears
Example: "Free [Melee] appears"

Applicable to: Global

#### [amount] free [baseUnitFilter] units appear
Example: "[20] free [Melee] units appear"

Applicable to: Global

#### Free Social Policy
Applicable to: Global

#### [amount] Free Social Policies
Example: "[20] Free Social Policies"

Applicable to: Global

#### Empire enters golden age
Applicable to: Global

#### Free Great Person
Applicable to: Global

#### [amount] population [cityFilter]
Example: "[20] population [in all cities]"

Applicable to: Global

#### Free Technology
Applicable to: Global

#### [amount] Free Technologies
Example: "[20] Free Technologies"

Applicable to: Global

#### Reveals the entire map
Applicable to: Global

#### Triggers voting for the Diplomatic Victory
Applicable to: Global

#### This Unit upgrades for free
Applicable to: Global

#### This Unit gains the [promotion] promotion
Example: "This Unit gains the [promotion] promotion"

Applicable to: Global

#### [mapUnitFilter] units gain the [promotion] promotion
Example: "[Wounded] units gain the [promotion] promotion"

Applicable to: Global

#### Quantity of strategic resources produced by the empire +[amount]%
Example: "Quantity of strategic resources produced by the empire +[20]%"

Applicable to: Global

#### +[amount]% attack strength to all [mapUnitFilter] units for [amount] turns
Example: "+[20]% attack strength to all [Wounded] units for [20] turns"

Applicable to: Global

#### Provides the cheapest [stat] building in your first [amount] cities for free
Example: "Provides the cheapest [Culture] building in your first [20] cities for free"

Applicable to: Global

#### Provides a [buildingName] in your first [amount] cities for free
Example: "Provides a [Library] in your first [20] cities for free"

Applicable to: Global

## Nation uniques
#### Will not be chosen for new games
Applicable to: Nation

## FollowerBelief uniques
#### [amount]% [stat] from every follower, up to [amount]%
Example: "[20]% [Culture] from every follower, up to [20]%"

Applicable to: FollowerBelief

#### Earn [amount]% of [mapUnitFilter] unit's [costOrStrength] as [stat] when killed within 4 tiles of a city following this religion
Example: "Earn [20]% of [Wounded] unit's [costOrStrength] as [Culture] when killed within 4 tiles of a city following this religion"

Applicable to: FollowerBelief

## Building uniques
#### Consumes [amount] [resource]
Example: "Consumes [20] [Iron]"

Applicable to: Building, Unit, Improvement

#### Provides [amount] [resource]
Example: "Provides [20] [Iron]"

Applicable to: Building, Improvement

#### Unbuildable
Applicable to: Building, Unit

#### Cannot be purchased
Applicable to: Building, Unit

#### Can be purchased with [stat] [cityFilter]
Example: "Can be purchased with [Culture] [in all cities]"

Applicable to: Building, Unit

#### Can be purchased for [amount] [stat] [cityFilter]
Example: "Can be purchased for [20] [Culture] [in all cities]"

Applicable to: Building, Unit

#### Limited to [amount] per Civilization
Example: "Limited to [20] per Civilization"

Applicable to: Building, Unit

#### Hidden until [amount] social policy branches have been completed
Example: "Hidden until [20] social policy branches have been completed"

Applicable to: Building, Unit

#### Cost increases by [amount] per owned city
Example: "Cost increases by [20] per owned city"

Applicable to: Building

#### Cannot be built with [buildingName]
Example: "Cannot be built with [Library]"

Applicable to: Building

#### Requires a [buildingName] in this city
Example: "Requires a [Library] in this city"

Applicable to: Building

#### Requires a [buildingName] in all cities
Example: "Requires a [Library] in all cities"

Applicable to: Building

#### Not displayed as an available construction without [buildingName/tech/resource/policy]
Example: "Not displayed as an available construction without [buildingName/tech/resource/policy]"

Applicable to: Building, Unit

#### Must be on [terrainFilter]
Example: "Must be on [Grassland]"

Applicable to: Building

#### Must not be on [terrainFilter]
Example: "Must not be on [Grassland]"

Applicable to: Building

#### Must be next to [terrainFilter]
Example: "Must be next to [Grassland]"

Applicable to: Building

#### Must not be next to [terrainFilter]
Example: "Must not be next to [Grassland]"

Applicable to: Building

#### Unsellable
Applicable to: Building

#### Remove extra unhappiness from annexed cities
Applicable to: Building

#### Spaceship part
Applicable to: Building, Unit

#### Hidden when religion is disabled
Applicable to: Building, Unit, Ruins

#### Hidden when [victoryType] Victory is disabled
Example: "Hidden when [victoryType] Victory is disabled"

Applicable to: Building, Unit

## Unit uniques
#### Founds a new city
Applicable to: Unit

#### Can build [improvementFilter/terrainFilter] improvements on tiles
Example: "Can build [improvementFilter/terrainFilter] improvements on tiles"

Applicable to: Unit

#### May create improvements on water resources
Applicable to: Unit

#### Can see invisible [mapUnitFilter] units
Example: "Can see invisible [Wounded] units"

Applicable to: Unit

#### May found a religion
Applicable to: Unit

#### May enhance a religion
Applicable to: Unit

#### Cannot attack
Applicable to: Unit

#### Must set up to ranged attack
Applicable to: Unit

#### 6 tiles in every direction always visible
Applicable to: Unit

#### Blast radius [amount]
Example: "Blast radius [20]"

Applicable to: Unit

#### Can carry [amount] [mapUnitFilter] units
Example: "Can carry [20] [Wounded] units"

Applicable to: Unit

#### Can carry [amount] extra [mapUnitFilter] units
Example: "Can carry [20] extra [Wounded] units"

Applicable to: Unit

#### Cannot be carried by [mapUnitFilter] units
Example: "Cannot be carried by [Wounded] units"

Applicable to: Unit

#### Double movement in [terrainFilter]
Example: "Double movement in [Grassland]"

Applicable to: Unit

#### All tiles cost 1 movement
Applicable to: Unit

#### Can pass through impassable tiles
Applicable to: Unit

#### Ignores terrain cost
Applicable to: Unit

#### Ignores Zone of Control
Applicable to: Unit

#### Rough terrain penalty
Applicable to: Unit

#### Can enter ice tiles
Applicable to: Unit

#### Cannot enter ocean tiles
Applicable to: Unit

#### Never appears as a Barbarian unit
Applicable to: Unit

#### May enter foreign tiles without open borders
Applicable to: Unit

#### May enter foreign tiles without open borders, but loses [amount] religious strength each turn it ends there
Example: "May enter foreign tiles without open borders, but loses [20] religious strength each turn it ends there"

Applicable to: Unit

#### Religious Unit
Applicable to: Unit

## Promotion uniques
#### Heal this unit by [amount] HP
Example: "Heal this unit by [20] HP"

Applicable to: Promotion

## Terrain uniques
#### Must be adjacent to [amount] [simpleTerrain] tiles
Example: "Must be adjacent to [20] [simpleTerrain] tiles"

Applicable to: Terrain

#### Must be adjacent to [amount] to [amount] [simpleTerrain] tiles
Example: "Must be adjacent to [20] to [20] [simpleTerrain] tiles"

Applicable to: Terrain

#### Must not be on [amount] largest landmasses
Example: "Must not be on [20] largest landmasses"

Applicable to: Terrain

#### Must be on [amount] largest landmasses
Example: "Must be on [20] largest landmasses"

Applicable to: Terrain

#### Occurs on latitudes from [amount] to [amount] percent of distance equator to pole
Example: "Occurs on latitudes from [20] to [20] percent of distance equator to pole"

Applicable to: Terrain

#### Occurs in groups of [amount] to [amount] tiles
Example: "Occurs in groups of [20] to [20] tiles"

Applicable to: Terrain

#### Neighboring tiles will convert to [baseTerrain]
Example: "Neighboring tiles will convert to [baseTerrain]"

Applicable to: Terrain

#### Neighboring tiles except [baseTerrain] will convert to [baseTerrain]
Example: "Neighboring tiles except [baseTerrain] will convert to [baseTerrain]"

Applicable to: Terrain

#### Grants 500 Gold to the first civilization to discover it
Applicable to: Terrain

#### Units ending their turn on this terrain take [amount] damage
Example: "Units ending their turn on this terrain take [20] damage"

Applicable to: Terrain

#### Grants [promotion] ([comment]) to adjacent [mapUnitFilter] units for the rest of the game
Example: "Grants [promotion] ([comment]) to adjacent [Wounded] units for the rest of the game"

Applicable to: Terrain

#### [amount] Strength for cities built on this terrain
Example: "[20] Strength for cities built on this terrain"

Applicable to: Terrain

#### Provides a one-time Production bonus to the closest city when cut down
Applicable to: Terrain

#### Tile provides yield without assigned population
Applicable to: Terrain, Improvement

#### Nullifies all other stats this tile provides
Applicable to: Terrain

#### Only [improvementFilter] improvements may be built on this tile
Example: "Only [improvementFilter] improvements may be built on this tile"

Applicable to: Terrain

#### Blocks line-of-sight from tiles at same elevation
Applicable to: Terrain

#### Has an elevation of [amount] for visibility calculations
Example: "Has an elevation of [20] for visibility calculations"

Applicable to: Terrain

#### Always Fertility [amount] for Map Generation
Example: "Always Fertility [20] for Map Generation"

Applicable to: Terrain

#### [amount] to Fertility for Map Generation
Example: "[20] to Fertility for Map Generation"

Applicable to: Terrain

#### A Region is formed with at least [amount]% [simpleTerrain] tiles, with priority [amount]
Example: "A Region is formed with at least [20]% [simpleTerrain] tiles, with priority [20]"

Applicable to: Terrain

#### A Region is formed with at least [amount]% [simpleTerrain] tiles and [simpleTerrain] tiles, with priority [amount]
Example: "A Region is formed with at least [20]% [simpleTerrain] tiles and [simpleTerrain] tiles, with priority [20]"

Applicable to: Terrain

#### A Region can not contain more [simpleTerrain] tiles than [simpleTerrain] tiles
Example: "A Region can not contain more [simpleTerrain] tiles than [simpleTerrain] tiles"

Applicable to: Terrain

#### Base Terrain on this tile is not counted for Region determination
Applicable to: Terrain

#### Starts in regions of this type receive an extra [resource]
Example: "Starts in regions of this type receive an extra [Iron]"

Applicable to: Terrain

#### Never receives any resources
Applicable to: Terrain

#### Considered [terrainQuality] when determining start locations
Example: "Considered [terrainQuality] when determining start locations"

Applicable to: Terrain

#### Doesn't generate naturally
Applicable to: Terrain, Resource

#### Occurs at temperature between [amount] and [amount] and humidity between [amount] and [amount]
Example: "Occurs at temperature between [20] and [20] and humidity between [20] and [20]"

Applicable to: Terrain

#### Occurs in chains at high elevations
Applicable to: Terrain

#### Occurs in groups around high elevations
Applicable to: Terrain

#### Every [amount] tiles with this terrain will receive a major deposit of a strategic resource.
Example: "Every [20] tiles with this terrain will receive a major deposit of a strategic resource."

Applicable to: Terrain

#### Rare feature
Applicable to: Terrain

#### Resistant to nukes
Applicable to: Terrain

#### Can be destroyed by nukes
Applicable to: Terrain

#### Fresh water
Applicable to: Terrain

#### Rough terrain
Applicable to: Terrain

## Improvement uniques
#### Can also be built on tiles adjacent to fresh water
Applicable to: Improvement

#### [stats] from [tileFilter] tiles
Example: "[+1 Gold, +2 Production] from [Farm] tiles"

Applicable to: Improvement

#### [stats] for each adjacent [tileFilter]
Example: "[+1 Gold, +2 Production] for each adjacent [Farm]"

Applicable to: Improvement

#### Can be built outside your borders
Applicable to: Improvement

#### Can be built just outside your borders
Applicable to: Improvement

#### Cannot be built on [tileFilter] tiles
Example: "Cannot be built on [Farm] tiles"

Applicable to: Improvement

#### Does not need removal of [tileFilter]
Example: "Does not need removal of [Farm]"

Applicable to: Improvement

#### Gives a defensive bonus of [amount]%
Example: "Gives a defensive bonus of [20]%"

Applicable to: Improvement

#### Costs [amount] gold per turn when in your territory
Example: "Costs [20] gold per turn when in your territory"

Applicable to: Improvement

#### Deal [amount] damage to adjacent enemy units
Example: "Deal [20] damage to adjacent enemy units"

Applicable to: Improvement

#### Great Improvement
Applicable to: Improvement

#### Provides a random bonus when entered
Applicable to: Improvement

#### Unpillagable
Applicable to: Improvement

#### Indestructible
Applicable to: Improvement

## Resource uniques
#### Generated with weight [amount]
Example: "Generated with weight [20]"

Applicable to: Resource

#### Minor deposits generated with weight [amount]
Example: "Minor deposits generated with weight [20]"

Applicable to: Resource

#### Generated near City States with weight [amount]
Example: "Generated near City States with weight [20]"

Applicable to: Resource

#### Special placement during map generation
Applicable to: Resource

#### Generated on every [amount] tiles
Example: "Generated on every [20] tiles"

Applicable to: Resource

#### Guaranteed with Strategic Balance resource option
Applicable to: Resource

#### Deposits in [tileFilter] tiles always provide [amount] resources
Example: "Deposits in [Farm] tiles always provide [20] resources"

Applicable to: Resource

#### Can only be created by Mercantile City-States
Applicable to: Resource

## Ruins uniques
#### Free [baseUnitFilter] found in the ruins
Example: "Free [Melee] found in the ruins"

Applicable to: Ruins

#### [amount] population in a random city
Example: "[20] population in a random city"

Applicable to: Ruins

#### [amount] free random researchable Tech(s) from the [era]
Example: "[20] free random researchable Tech(s) from the [era]"

Applicable to: Ruins

#### Gain [amount] [stat]
Example: "Gain [20] [Culture]"

Applicable to: Ruins

#### Gain [amount]-[amount] [stat]
Example: "Gain [20]-[20] [Culture]"

Applicable to: Ruins

#### Gain enough Faith for a Pantheon
Applicable to: Ruins

#### Gain enough Faith for [amount]% of a Great Prophet
Example: "Gain enough Faith for [20]% of a Great Prophet"

Applicable to: Ruins

#### Reveal up to [amount/'all'] [tileFilter] within a [amount] tile radius
Example: "Reveal up to [amount/'all'] [Farm] within a [20] tile radius"

Applicable to: Ruins

#### From a randomly chosen tile [amount] tiles away from the ruins, reveal tiles up to [amount] tiles away with [amount]% chance
Example: "From a randomly chosen tile [20] tiles away from the ruins, reveal tiles up to [20] tiles away with [20]% chance"

Applicable to: Ruins

#### This Unit gains [amount] XP
Example: "This Unit gains [20] XP"

Applicable to: Ruins

#### This Unit upgrades for free including special upgrades
Applicable to: Ruins

#### Hidden before founding a Pantheon
Applicable to: Ruins

#### Hidden after founding a Pantheon
Applicable to: Ruins

#### Hidden after generating a Great Prophet
Applicable to: Ruins

#### Only available after [amount] turns
Example: "Only available after [20] turns"

Applicable to: Ruins

## CityState uniques
#### Provides [stats] per turn
Example: "Provides [+1 Gold, +2 Production] per turn"

Applicable to: CityState

#### Provides [stats] [cityFilter] per turn
Example: "Provides [+1 Gold, +2 Production] [in all cities] per turn"

Applicable to: CityState

#### Provides [amount] Happiness
Example: "Provides [20] Happiness"

Applicable to: CityState

#### Provides military units every ≈[amount] turns
Example: "Provides military units every ≈[20] turns"

Applicable to: CityState

#### Provides a unique luxury
Applicable to: CityState

## Conditional uniques
#### <when at war>
Applicable to: Conditional

#### <when not at war>
Applicable to: Conditional

#### <while the empire is happy>
Applicable to: Conditional

#### <during a Golden Age>
Applicable to: Conditional

#### <during the [era]>
Example: "<during the [era]>"

Applicable to: Conditional

#### <before the [era]>
Example: "<before the [era]>"

Applicable to: Conditional

#### <starting from the [era]>
Example: "<starting from the [era]>"

Applicable to: Conditional

#### <after discovering [tech]>
Example: "<after discovering [tech]>"

Applicable to: Conditional

#### <before discovering [tech]>
Example: "<before discovering [tech]>"

Applicable to: Conditional

#### <after adopting [policy]>
Example: "<after adopting [policy]>"

Applicable to: Conditional

#### <before adopting [policy]>
Example: "<before adopting [policy]>"

Applicable to: Conditional

#### <if this city has at least [amount] specialists>
Example: "<if this city has at least [20] specialists>"

Applicable to: Conditional

#### <for [mapUnitFilter] units>
Example: "<for [Wounded] units>"

Applicable to: Conditional

#### <vs cities>
Applicable to: Conditional

#### <vs [mapUnitFilter] units>
Example: "<vs [Wounded] units>"

Applicable to: Conditional

#### <when fighting units from a Civilization with more Cities than you>
Applicable to: Conditional

#### <when attacking>
Applicable to: Conditional

#### <when defending>
Applicable to: Conditional

#### <when fighting in [tileFilter] tiles>
Example: "<when fighting in [Farm] tiles>"

Applicable to: Conditional

#### <on foreign continents>
Applicable to: Conditional

#### <when above [amount] HP>
Example: "<when above [20] HP>"

Applicable to: Conditional

#### <when below [amount] HP>
Example: "<when below [20] HP>"

Applicable to: Conditional

#### <with [amount] to [amount] neighboring [tileFilter] tiles>
Example: "<with [20] to [20] neighboring [Farm] tiles>"

Applicable to: Conditional

#### <with [amount] to [amount] neighboring [tileFilter] [tileFilter] tiles>
Example: "<with [20] to [20] neighboring [Farm] [Farm] tiles>"

Applicable to: Conditional

#### <in [tileFilter] tiles>
Example: "<in [Farm] tiles>"

Applicable to: Conditional

#### <in [tileFilter] [tileFilter] tiles>
Example: "<in [Farm] [Farm] tiles>"

Applicable to: Conditional

#### <in tiles without [tileFilter]>
Example: "<in tiles without [Farm]>"

Applicable to: Conditional

#### <on water maps>
Applicable to: Conditional

#### <in [regionType] Regions>
Example: "<in [regionType] Regions>"

Applicable to: Conditional

#### <in all except [regionType] Regions>
Example: "<in all except [regionType] Regions>"

Applicable to: Conditional

## Deprecated uniques
 - "[stats] per turn from cities before [tech/policy]" - Deprecated As of 3.18.14, replace with "[stats] [in all cities] <before discovering [tech]> OR [stats] [in all cities] <before adopting [policy]>"
 - "+[amount]% [stat] [cityFilter]" - Deprecated As of 3.17.10, replace with "[+amount]% [stat] [cityFilter]"
 - "+[amount]% [stat] in all cities" - Deprecated As of 3.17.10, replace with "[+amount]% [stat] [in all cities]"
 - "[amount]% [stat] while the empire is happy" - Deprecated As of 3.17.1, replace with "[amount]% [stat] [in all cities] <while the empire is happy>"
 - "Provides a free [buildingName] [cityFilter]" - Deprecated As of 3.17.7, replace with "Gain a free [buildingName] [cityFilter]"
 - "-[amount]% food consumption by specialists [cityFilter]" - Deprecated As of 3.18.2, replace with "[-amount]% food consumption by specialists [cityFilter]"
 - "50% of excess happiness added to culture towards policies" - Deprecated As of 3.18.2, replace with "[50]% of excess happiness converted to [Culture]"
 - "May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] starting from the [era] at an increasing price ([amount])" - Deprecated As of 3.17.9, replace with "May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] at an increasing price ([amount]) <starting from the [era]>"
 - "Immediately creates the cheapest available cultural building in each of your first [amount] cities for free" - Deprecated As of 3.16.15 - removed 3.18.4, replace with "Provides the cheapest [stat] building in your first [amount] cities for free"
 - "Immediately creates a [buildingName] in each of your first [amount] cities for free" - Deprecated As of 3.16.15 - removed 3.18.4, replace with "Provides a [buildingName] in your first [amount] cities for free"
 - "[mapUnitFilter] units deal +[amount]% damage" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <for [mapUnitFilter] units>"
 - "+10% Strength for all units during Golden Age" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[+10]% Strength <for [All] units> <during a Golden Age>"
 - "[amount]% Strength for [mapUnitFilter] units in [tileFilter]" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <for [mapUnitFilter] units> <when fighting in [tileFilter] tiles>"
 - "+15% Combat Strength for all units when attacking Cities" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[+15]% Strength <for [All] units> <vs cities> <when attacking>"
 - "Increases embarked movement +1" - Deprecated As of 3.16.11 - removed 3.17.11, replace with "[+1] Movement <for [Embarked] units>"
 - "+1 Movement for all embarked units" - Deprecated As of 3.16.11 - removed 3.17.11, replace with "[+1] Movement <for [Embarked] units>"
 - "+[amount] Movement for all [mapUnitFilter] units" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount] Movement <for [mapUnitFilter] units>"
 - "+1 Movement for all units during Golden Age" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount] Movement <for [All] units> <during a Golden Age>"
 - "[amount] Sight for all [mapUnitFilter] units" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount] Sight <for [mapUnitFilter] units>"
 - "[amount]% Spread Religion Strength for [mapUnitFilter] units" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Spread Religion Strength <for [mapUnitFilter] units>"
 - "Unhappiness from population decreased by [amount]%" - Deprecated As of 3.16.11 - removed 3.17.11, replace with "[amount]% unhappiness from population [cityFilter]"
 - "Unhappiness from population decreased by [amount]% [cityFilter]" - Deprecated As of 3.16.11 - removed 3.17.11, replace with "[amount]% unhappiness from population [cityFilter]"
 - "+[amount]% Production when constructing [baseUnitFilter] units [cityFilter]" - Deprecated As of 3.17.10 - removed 3.18.5, replace with "[+amount]% Production when constructing [baseUnitFilter] units [cityFilter]"
 - "+[amount]% growth [cityFilter]" - Deprecated As of 3.16.14 - removed 3.17.11, replace with "[amount]% growth [cityFilter]"
 - "+[amount]% growth [cityFilter] when not at war" - Deprecated As of 3.16.14 - removed 3.17.11, replace with "[amount]% growth [cityFilter] <when not at war>"
 - "-[amount]% [mapUnitFilter] unit maintenance costs" - Deprecated As of 3.16.16 - removed as of 3.17.11, replace with "[amount]% maintenance costs for [mapUnitFilter] units"
 - "-[amount]% unit upkeep costs" - Deprecated As of 3.16.16 - removed 3.17.11, replace with "[amount]% maintenance costs for [mapUnitFilter] units"
 - "+[amount]% Production when constructing [stat] buildings" - Deprecated As of 3.17.10 - removed 3.18.5, replace with "[amount]% Production when constructing [buildingFilter] buildings [cityFilter]"
 - "+[amount]% Production when constructing [constructionFilter]" - Deprecated As of 3.17.10 - removed 3.18.5, replace with "[amount]% Production when constructing [buildingFilter] buildings [cityFilter]"
 - "+[amount]% Production when constructing a [buildingName]" - Deprecated As of 3.17.10 - removed 3.18.5, replace with "[amount]% Production when constructing [buildingFilter] buildings [cityFilter]"
 - "+[amount]% Production when constructing [constructionFilter] [cityFilter]" - Deprecated As of 3.17.10 - removed 3.18.5, replace with "[amount]% Production when constructing [buildingFilter] buildings [cityFilter]"
 - "[stats] from every specialist" - Deprecated As of 3.16.16 - removed 3.17.11, replace with "[stats] from every specialist [in all cities]"
 - "[stats] if this city has at least [amount] specialists" - Deprecated As of 3.16.16 - removed 3.17.11, replace with "[stats] <if this city has at least [amount] specialists>"
 - "[mapUnitFilter] units gain [amount]% more Experience from combat" - Deprecated As of 3.18.12, replace with "[amount]% XP gained from combat <for [mapUnitFilter] units>"
 - "[amount]% maintenance costs for [mapUnitFilter] units" - Deprecated As of 3.18.14, replace with "[amount]% maintenance costs <for [mapUnitFilter] units>"
 - "Not displayed as an available construction unless [buildingName] is built" - Deprecated As of 3.16.11, replace with "Not displayed as an available construction without [buildingName]"
 - "[stats] once [tech] is discovered" - Deprecated As of 3.17.10, replace with "[stats] <after discovering [tech]>"
 - "Cannot enter ocean tiles until Astronomy" - Deprecated As of 3.18.6, replace with "Cannot enter ocean tiles <before discovering [Astronomy]>"
 - "Double movement in coast" - Deprecated As of 3.17.1 - removed 3.17.13, replace with "Double movement in [terrainFilter]"
 - "Double movement rate through Forest and Jungle" - Deprecated As of 3.17.1 - removed 3.17.13, replace with "Double movement in [terrainFilter]"
 - "Double movement in Snow, Tundra and Hills" - Deprecated As of 3.17.1 - removed 3.17.13, replace with "Double movement in [terrainFilter]"
 - "+[amount]% Strength" - Deprecated As of 3.17.3 - removed 3.17.13, replace with "[amount]% Strength"
 - "-[amount]% Strength" - Deprecated As of 3.17.3 - removed 3.17.13, replace with "[amount]% Strength"
 - "+[amount]% Strength vs [combatantFilter]" - Deprecated As of 3.17.3 - removed 3.17.13, replace with "[amount]% Strength <vs [mapUnitFilter] units>/<vs cities>"
 - "-[amount]% Strength vs [combatantFilter]" - Deprecated As of 3.17.3 - removed 3.17.13, replace with "[amount]% Strength <vs [mapUnitFilter] units>/<vs cities>"
 - "+[amount]% Combat Strength" - Deprecated As of 3.17.3 - removed 3.17.13, replace with "[amount]% Strength"
 - "+[amount]% Strength when attacking" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <when attacking>"
 - "+[amount]% Strength when defending" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <shen defending>"
 - "[amount]% Strength when defending vs [mapUnitFilter] units" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <when defending> <vs [mapUnitFilter] units>"
 - "+[amount]% defence in [tileFilter] tiles" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <when fighting in [tileFilter] tiles> <when defending>"
 - "+[amount]% Strength in [tileFilter]" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <when fighting in [tileFilter] tiles>"
 - "[amount] Visibility Range" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount] Sight"
 - "Limited Visibility" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[-1] Sight"
 - "[amount]% Bonus XP gain" - Deprecated As of 3.18.12, replace with "[amount]% XP gained from combat"
 - "[stats] on [tileFilter] tiles once [tech] is discovered" - Deprecated As of 3.17.10, replace with "[stats] from [tileFilter] tiles <after discovering [tech]>"
 - "Cannot be built on [tileFilter] tiles until [tech] is discovered" - Deprecated As of 3.18.5, replace with "Cannot be built on [tileFilter] tiles <before discovering [tech]>"
 - "Deal 30 damage to adjacent enemy units" - Deprecated As of 3.17.10, replace with "Adjacent enemy units ending their turn take [30] damage"