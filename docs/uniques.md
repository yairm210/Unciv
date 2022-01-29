## Table of Contents

 - [Global uniques](#global-uniques)
 - [Nation uniques](#nation-uniques)
 - [Tech uniques](#tech-uniques)
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
Example: "[+1 Gold, +2 Production] in cities on [Forest] tiles"

Applicable to: Global, FollowerBelief

#### [stats] from all [buildingFilter] buildings
Example: "[+1 Gold, +2 Production] from all [Culture] buildings"

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

#### [stats] from every [tileFilter/specialist/buildingFilter]
Example: "[+1 Gold, +2 Production] from every [tileFilter/specialist/buildingFilter]"

Applicable to: Global, FollowerBelief

#### [stats] from each Trade Route
Example: "[+1 Gold, +2 Production] from each Trade Route"

Applicable to: Global, FollowerBelief

#### [amount]% [stat]
Example: "[20]% [Culture]"

Applicable to: Global, FollowerBelief

#### [amount]% [stat] [cityFilter]
Example: "[20]% [Culture] [in all cities]"

Applicable to: Global, FollowerBelief

#### [amount]% [stat] from every [tileFilter/specialist/buildingName]
Example: "[20]% [Culture] from every [tileFilter/specialist/buildingName]"

Applicable to: Global, FollowerBelief

#### [amount]% Yield from every [tileFilter]
Example: "[20]% Yield from every [Farm]"

Applicable to: Global, FollowerBelief

#### [amount]% [stat] from City-States
Example: "[20]% [Culture] from City-States"

Applicable to: Global

#### Nullifies [stat] [cityFilter]
Example: "Nullifies [Culture] [in all cities]"

Applicable to: Global

#### Nullifies Growth [cityFilter]
Example: "Nullifies Growth [in all cities]"

Applicable to: Global

#### [amount]% Production when constructing [buildingFilter] wonders [cityFilter]
Example: "[20]% Production when constructing [Culture] wonders [in all cities]"

Applicable to: Global, FollowerBelief, Resource

#### [amount]% Production when constructing [buildingFilter] buildings [cityFilter]
Example: "[20]% Production when constructing [Culture] buildings [in all cities]"

Applicable to: Global, FollowerBelief

#### [amount]% Production when constructing [baseUnitFilter] units [cityFilter]
Example: "[20]% Production when constructing [Melee] units [in all cities]"

Applicable to: Global, FollowerBelief

#### [amount]% Production towards any buildings that already exist in the Capital
Example: "[20]% Production towards any buildings that already exist in the Capital"

Applicable to: Global, FollowerBelief

#### Military Units gifted from City-States start with [amount] XP
Example: "Military Units gifted from City-States start with [20] XP"

Applicable to: Global

#### Militaristic City-States grant units [amount] times as fast when you are at war with a common nation
Example: "Militaristic City-States grant units [20] times as fast when you are at war with a common nation"

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

#### [amount]% City-State Influence degradation
Example: "[20]% City-State Influence degradation"

Applicable to: Global

#### Resting point for Influence with City-States is increased by [amount]
Example: "Resting point for Influence with City-States is increased by [20]"

Applicable to: Global

#### Allied City-States provide [stat] equal to [amount]% of what they produce for themselves
Example: "Allied City-States provide [Culture] equal to [20]% of what they produce for themselves"

Applicable to: Global

#### [amount]% resources gifted by City-States
Example: "[20]% resources gifted by City-States"

Applicable to: Global

#### [amount]% Happiness from luxury resources gifted by City-States
Example: "[20]% Happiness from luxury resources gifted by City-States"

Applicable to: Global

#### [amount] units cost no maintenance
Example: "[20] units cost no maintenance"

Applicable to: Global

#### Cannot build [baseUnitFilter] units
Example: "Cannot build [Melee] units"

Applicable to: Global

#### [amount]% growth [cityFilter]
Example: "[20]% growth [in all cities]"

Applicable to: Global, FollowerBelief

#### [amount]% Food is carried over after population increases [cityFilter]
Example: "[20]% Food is carried over after population increases [in all cities]"

Applicable to: Global, FollowerBelief

#### Gain a free [buildingName] [cityFilter]
Example: "Gain a free [Library] [in all cities]"

Applicable to: Global

#### [amount]% Great Person generation [cityFilter]
Example: "[20]% Great Person generation [in all cities]"

Applicable to: Global, FollowerBelief

#### [amount]% great person generation [cityFilter]
Example: "[20]% great person generation [in all cities]"

Applicable to: Global, FollowerBelief

#### May choose [amount] additional [beliefType] beliefs when [foundingOrEnhancing] a religion
Example: "May choose [20] additional [Follower] beliefs when [founding] a religion"

Applicable to: Global

#### May choose [amount] additional belief(s) of any type when [foundingOrEnhancing] a religion
Example: "May choose [20] additional belief(s) of any type when [founding] a religion"

Applicable to: Global

#### [amount]% unhappiness from population [cityFilter]
Example: "[20]% unhappiness from population [in all cities]"

Applicable to: Global, FollowerBelief

#### [amount]% unhappiness from specialists [cityFilter]
Example: "[20]% unhappiness from specialists [in all cities]"

Applicable to: Global, FollowerBelief

#### [amount]% Food consumption by specialists [cityFilter]
Example: "[20]% Food consumption by specialists [in all cities]"

Applicable to: Global, FollowerBelief

#### [amount]% of excess happiness converted to [stat]
Example: "[20]% of excess happiness converted to [Culture]"

Applicable to: Global

#### [amount]% Culture cost of natural border growth [cityFilter]
Example: "[20]% Culture cost of natural border growth [in all cities]"

Applicable to: Global, FollowerBelief

#### [amount]% Gold cost of acquiring tiles [cityFilter]
Example: "[20]% Gold cost of acquiring tiles [in all cities]"

Applicable to: Global, FollowerBelief

#### May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] at an increasing price ([amount])
Example: "May buy [Melee] units for [20] [Culture] [in all cities] at an increasing price ([20])"

Applicable to: Global, FollowerBelief

#### May buy [buildingFilter] buildings for [amount] [stat] [cityFilter] at an increasing price ([amount])
Example: "May buy [Culture] buildings for [20] [Culture] [in all cities] at an increasing price ([20])"

Applicable to: Global, FollowerBelief

#### May buy [baseUnitFilter] units for [amount] [stat] [cityFilter]
Example: "May buy [Melee] units for [20] [Culture] [in all cities]"

Applicable to: Global, FollowerBelief

#### May buy [buildingFilter] buildings for [amount] [stat] [cityFilter]
Example: "May buy [Culture] buildings for [20] [Culture] [in all cities]"

Applicable to: Global, FollowerBelief

#### May buy [baseUnitFilter] units with [stat] [cityFilter]
Example: "May buy [Melee] units with [Culture] [in all cities]"

Applicable to: Global, FollowerBelief

#### May buy [buildingFilter] buildings with [stat] [cityFilter]
Example: "May buy [Culture] buildings with [Culture] [in all cities]"

Applicable to: Global, FollowerBelief

#### May buy [baseUnitFilter] units with [stat] for [amount] times their normal Production cost
Example: "May buy [Melee] units with [Culture] for [20] times their normal Production cost"

Applicable to: Global, FollowerBelief

#### May buy [buildingFilter] buildings with [stat] for [amount] times their normal Production cost
Example: "May buy [Culture] buildings with [Culture] for [20] times their normal Production cost"

Applicable to: Global, FollowerBelief

#### Enables conversion of city production to gold
Applicable to: Global

#### Enables conversion of city production to science
Applicable to: Global

#### [stat] cost of purchasing items in cities [amount]%
Example: "[Culture] cost of purchasing items in cities [20]%"

Applicable to: Global, FollowerBelief

#### [stat] cost of purchasing [buildingFilter] buildings [amount]%
Example: "[Culture] cost of purchasing [Culture] buildings [20]%"

Applicable to: Global, FollowerBelief

#### [stat] cost of purchasing [baseUnitFilter] units in cities [amount]%
Example: "[Culture] cost of purchasing [Melee] units in cities [20]%"

Applicable to: Global, FollowerBelief

#### Improves movement speed on roads
Applicable to: Global

#### Roads connect tiles across rivers
Applicable to: Global

#### [amount]% maintenance on road & railroads
Example: "[20]% maintenance on road & railroads"

Applicable to: Global

#### [amount]% maintenance cost for buildings [cityFilter]
Example: "[20]% maintenance cost for buildings [in all cities]"

Applicable to: Global, FollowerBelief

#### Receive a free Great Person at the end of every [comment] (every 394 years), after researching [tech]. Each bonus person can only be chosen once.
Example: "Receive a free Great Person at the end of every [comment] (every 394 years), after researching [Agriculture]. Each bonus person can only be chosen once."

Applicable to: Global

#### Once The Long Count activates, the year on the world screen displays as the traditional Mayan Long Count.
Applicable to: Global

#### Retain [amount]% of the happiness from a luxury after the last copy has been traded away
Example: "Retain [20]% of the happiness from a luxury after the last copy has been traded away"

Applicable to: Global

#### [amount] Happiness from each type of luxury resource
Example: "[20] Happiness from each type of luxury resource"

Applicable to: Global

#### Each city founded increases culture cost of policies [amount]% less than normal
Example: "Each city founded increases culture cost of policies [20]% less than normal"

Applicable to: Global

#### [amount]% Culture cost of adopting new Policies
Example: "[20]% Culture cost of adopting new Policies"

Applicable to: Global

#### Enables Open Borders agreements
Applicable to: Global

#### Enables Research agreements
Applicable to: Global

#### Science gained from research agreements [amount]%
Example: "Science gained from research agreements [20]%"

Applicable to: Global

#### Triggers victory
Applicable to: Global

#### Triggers a Cultural Victory upon completion
Applicable to: Global

#### [amount]% City Strength from defensive buildings
Example: "[20]% City Strength from defensive buildings"

Applicable to: Global

#### [amount]% tile improvement construction time
Example: "[20]% tile improvement construction time"

Applicable to: Global

#### [amount]% Gold from Great Merchant trade missions
Example: "[20]% Gold from Great Merchant trade missions"

Applicable to: Global

#### [mapUnitFilter] Units adjacent to this city heal [amount] HP per turn when healing
Example: "[Wounded] Units adjacent to this city heal [20] HP per turn when healing"

Applicable to: Global, FollowerBelief

#### [amount]% Golden Age length
Example: "[20]% Golden Age length"

Applicable to: Global

#### [amount]% Strength for cities
Example: "[20]% Strength for cities"

Applicable to: Global, FollowerBelief

#### New [baseUnitFilter] units start with [amount] Experience [cityFilter]
Example: "New [Melee] units start with [20] Experience [in all cities]"

Applicable to: Global, FollowerBelief

#### All newly-trained [baseUnitFilter] units [cityFilter] receive the [promotion] promotion
Example: "All newly-trained [Melee] units [in all cities] receive the [Shock I] promotion"

Applicable to: Global, FollowerBelief

#### [baseUnitFilter] units built [cityFilter] can [action] [amount] extra times
Example: "[Melee] units built [in all cities] can [action] [20] extra times"

Applicable to: Global, FollowerBelief

#### Enables embarkation for land units
Applicable to: Global

#### Enables embarked units to enter ocean tiles
Applicable to: Global

#### Population loss from nuclear attacks [amount]% [cityFilter]
Example: "Population loss from nuclear attacks [20]% [in all cities]"

Applicable to: Global

#### [amount]% Natural religion spread [cityFilter]
Example: "[20]% Natural religion spread [in all cities]"

Applicable to: Global, FollowerBelief

#### Religion naturally spreads to cities [amount] tiles away
Example: "Religion naturally spreads to cities [20] tiles away"

Applicable to: Global, FollowerBelief

#### Can be continually researched
Applicable to: Global

#### Rebel units may spawn
Applicable to: Global

#### [amount]% Strength
Example: "[20]% Strength"

Applicable to: Global, Unit

#### [amount]% Strength decreasing with distance from the capital
Example: "[20]% Strength decreasing with distance from the capital"

Applicable to: Global, Unit

#### [amount]% to Flank Attack bonuses
Example: "[20]% to Flank Attack bonuses"

Applicable to: Global, Unit

#### [amount] Movement
Example: "[20] Movement"

Applicable to: Global, Unit

#### [amount] Sight
Example: "[20] Sight"

Applicable to: Global, Unit, Terrain

#### [amount] Range
Example: "[20] Range"

Applicable to: Global, Unit

#### [amount]% Spread Religion Strength
Example: "[20]% Spread Religion Strength"

Applicable to: Global, Unit

#### No defensive terrain bonus
Applicable to: Global, Unit

#### No defensive terrain penalty
Applicable to: Global, Unit

#### No movement cost to pillage
Applicable to: Global, Unit

#### May heal outside of friendly territory
Applicable to: Global, Unit

#### All healing effects doubled
Applicable to: Global, Unit

#### Heals [amount] damage if it kills a unit
Example: "Heals [20] damage if it kills a unit"

Applicable to: Global, Unit

#### Normal vision when embarked
Applicable to: Global, Unit

#### Defense bonus when embarked
Applicable to: Global, Unit

#### Embarked units can defend themselves
Applicable to: Global

#### [amount]% maintenance costs
Example: "[20]% maintenance costs"

Applicable to: Global, Unit

#### [amount]% Gold cost of upgrading
Example: "[20]% Gold cost of upgrading"

Applicable to: Global, Unit

#### [greatPerson] is earned [amount]% faster
Example: "[greatPerson] is earned [20]% faster"

Applicable to: Global, Unit

#### Earn [amount]% of the damage done to [mapUnitFilter] units as [plunderableStat]
Example: "Earn [20]% of the damage done to [Wounded] units as [Gold]"

Applicable to: Global, Unit

#### Upon capturing a city, receive [amount] times its [stat] production as [plunderableStat] immediately
Example: "Upon capturing a city, receive [20] times its [Culture] production as [Gold] immediately"

Applicable to: Global, Unit

#### Earn [amount]% of killed [mapUnitFilter] unit's [costOrStrength] as [plunderableStat]
Example: "Earn [20]% of killed [Wounded] unit's [Cost] as [Gold]"

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
Example: "This Unit gains the [Shock I] promotion"

Applicable to: Global

#### [mapUnitFilter] units gain the [promotion] promotion
Example: "[Wounded] units gain the [Shock I] promotion"

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

#### Will not be displayed in Civilopedia
Applicable to: Global, Nation, Era, Tech, Policy, FounderBelief, FollowerBelief, Building, Wonder, Unit, UnitType, Promotion, Terrain, Improvement, Resource, Ruins, CityState, ModOptions, Conditional

## Nation uniques
#### Will not be chosen for new games
Applicable to: Nation

#### Starts with [tech]
Example: "Starts with [Agriculture]"

Applicable to: Nation

## Tech uniques
#### Incompatible with [policy/tech/promotion]
Example: "Incompatible with [policy/tech/promotion]"

Applicable to: Tech, Policy, Promotion

#### Starting tech
Applicable to: Tech

## FollowerBelief uniques
#### [amount]% [stat] from every follower, up to [amount]%
Example: "[20]% [Culture] from every follower, up to [20]%"

Applicable to: FollowerBelief

#### Earn [amount]% of [mapUnitFilter] unit's [costOrStrength] as [plunderableStat] when killed within 4 tiles of a city following this religion
Example: "Earn [20]% of [Wounded] unit's [Cost] as [Gold] when killed within 4 tiles of a city following this religion"

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

#### Not displayed as an available construction without [buildingName/tech/resource/policy]
Example: "Not displayed as an available construction without [buildingName/tech/resource/policy]"

Applicable to: Building, Unit

#### Excess Food converted to Production when under construction
Applicable to: Building, Unit

#### Requires at least [amount] population
Example: "Requires at least [20] population"

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

#### [stats] with [resource]
Example: "[+1 Gold, +2 Production] with [Iron]"

Applicable to: Building

#### Must be on [terrainFilter]
Example: "Must be on [Forest]"

Applicable to: Building

#### Must not be on [terrainFilter]
Example: "Must not be on [Forest]"

Applicable to: Building

#### Must be next to [terrainFilter]
Example: "Must be next to [Forest]"

Applicable to: Building

#### Must not be next to [terrainFilter]
Example: "Must not be next to [Forest]"

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
Example: "Hidden when [Domination] Victory is disabled"

Applicable to: Building, Unit

## Unit uniques
#### Founds a new city
Applicable to: Unit

#### Can construct [improvementName]
Example: "Can construct [Trading Post]"

Applicable to: Unit

#### Can build [improvementFilter/terrainFilter] improvements on tiles
Example: "Can build [improvementFilter/terrainFilter] improvements on tiles"

Applicable to: Unit

#### May create improvements on water resources
Applicable to: Unit

#### May found a religion
Applicable to: Unit

#### May enhance a religion
Applicable to: Unit

#### Can only attack [combatantFilter] units
Example: "Can only attack [City] units"

Applicable to: Unit

#### Can only attack [tileFilter] tiles
Example: "Can only attack [Farm] tiles"

Applicable to: Unit

#### Cannot attack
Applicable to: Unit

#### Must set up to ranged attack
Applicable to: Unit

#### Self-destructs when attacking
Applicable to: Unit

#### Blast radius [amount]
Example: "Blast radius [20]"

Applicable to: Unit

#### Uncapturable
Applicable to: Unit

#### May withdraw before melee ([amount]%)
Example: "May withdraw before melee ([20]%)"

Applicable to: Unit

#### Can move after attacking
Applicable to: Unit

#### Can move immediately once bought
Applicable to: Unit

#### Unit will heal every turn, even if it performs an action
Applicable to: Unit

#### 6 tiles in every direction always visible
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

#### May capture killed [mapUnitFilter] units
Example: "May capture killed [Wounded] units"

Applicable to: Unit

#### Invisible to others
Applicable to: Unit

#### Invisible to non-adjacent units
Applicable to: Unit

#### Can see invisible [mapUnitFilter] units
Example: "Can see invisible [Wounded] units"

Applicable to: Unit

#### May upgrade to [baseUnitFilter] through ruins-like effects
Example: "May upgrade to [Melee] through ruins-like effects"

Applicable to: Unit

#### Double movement in [terrainFilter]
Example: "Double movement in [Forest]"

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

#### May enter foreign tiles without open borders
Applicable to: Unit

#### May enter foreign tiles without open borders, but loses [amount] religious strength each turn it ends there
Example: "May enter foreign tiles without open borders, but loses [20] religious strength each turn it ends there"

Applicable to: Unit

#### Never appears as a Barbarian unit
Applicable to: Unit

#### Religious Unit
Applicable to: Unit

## Promotion uniques
#### Heal this unit by [amount] HP
Example: "Heal this unit by [20] HP"

Applicable to: Promotion

## Terrain uniques
#### Must be adjacent to [amount] [simpleTerrain] tiles
Example: "Must be adjacent to [20] [Elevated] tiles"

Applicable to: Terrain

#### Must be adjacent to [amount] to [amount] [simpleTerrain] tiles
Example: "Must be adjacent to [20] to [20] [Elevated] tiles"

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
Example: "Neighboring tiles will convert to [Grassland]"

Applicable to: Terrain

#### Neighboring tiles except [baseTerrain] will convert to [baseTerrain]
Example: "Neighboring tiles except [Grassland] will convert to [Grassland]"

Applicable to: Terrain

#### Grants 500 Gold to the first civilization to discover it
Applicable to: Terrain

#### Units ending their turn on this terrain take [amount] damage
Example: "Units ending their turn on this terrain take [20] damage"

Applicable to: Terrain

#### Grants [promotion] ([comment]) to adjacent [mapUnitFilter] units for the rest of the game
Example: "Grants [Shock I] ([comment]) to adjacent [Wounded] units for the rest of the game"

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
Example: "Only [All Road] improvements may be built on this tile"

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
Example: "A Region is formed with at least [20]% [Elevated] tiles, with priority [20]"

Applicable to: Terrain

#### A Region is formed with at least [amount]% [simpleTerrain] tiles and [simpleTerrain] tiles, with priority [amount]
Example: "A Region is formed with at least [20]% [Elevated] tiles and [Elevated] tiles, with priority [20]"

Applicable to: Terrain

#### A Region can not contain more [simpleTerrain] tiles than [simpleTerrain] tiles
Example: "A Region can not contain more [Elevated] tiles than [Elevated] tiles"

Applicable to: Terrain

#### Base Terrain on this tile is not counted for Region determination
Applicable to: Terrain

#### Starts in regions of this type receive an extra [resource]
Example: "Starts in regions of this type receive an extra [Iron]"

Applicable to: Terrain

#### Never receives any resources
Applicable to: Terrain

#### Considered [terrainQuality] when determining start locations
Example: "Considered [Undesirable] when determining start locations"

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

#### Adjacent enemy units ending their turn take [amount] damage
Example: "Adjacent enemy units ending their turn take [20] damage"

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
Example: "[20] free random researchable Tech(s) from the [Ancient era]"

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

#### Only available after [amount] turns
Example: "Only available after [20] turns"

Applicable to: Ruins

#### Hidden before founding a Pantheon
Applicable to: Ruins

#### Hidden after founding a Pantheon
Applicable to: Ruins

#### Hidden after generating a Great Prophet
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

#### <during a Golden Age>
Applicable to: Conditional

#### <while the empire is happy>
Applicable to: Conditional

#### <when between [amount] and [amount] Happiness>
Example: "<when between [20] and [20] Happiness>"

Applicable to: Conditional

#### <when below [amount] Happiness>
Example: "<when below [20] Happiness>"

Applicable to: Conditional

#### <during the [era]>
Example: "<during the [Ancient era]>"

Applicable to: Conditional

#### <before the [era]>
Example: "<before the [Ancient era]>"

Applicable to: Conditional

#### <starting from the [era]>
Example: "<starting from the [Ancient era]>"

Applicable to: Conditional

#### <after discovering [tech]>
Example: "<after discovering [Agriculture]>"

Applicable to: Conditional

#### <before discovering [tech]>
Example: "<before discovering [Agriculture]>"

Applicable to: Conditional

#### <after adopting [policy]>
Example: "<after adopting [Oligarchy]>"

Applicable to: Conditional

#### <before adopting [policy]>
Example: "<before adopting [Oligarchy]>"

Applicable to: Conditional

#### <if this city has at least [amount] specialists>
Example: "<if this city has at least [20] specialists>"

Applicable to: Conditional

#### <in cities where this religion has at least [amount] followers>
Example: "<in cities where this religion has at least [20] followers>"

Applicable to: Conditional

#### <with a garrison>
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

#### <when adjacent to a [mapUnitFilter] unit>
Example: "<when adjacent to a [Wounded] unit>"

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
Example: "<in [Hybrid] Regions>"

Applicable to: Conditional

#### <in all except [regionType] Regions>
Example: "<in all except [Hybrid] Regions>"

Applicable to: Conditional

## Deprecated uniques
 - "[stats] per turn from cities before [tech/policy]" - Deprecated As of 3.18.14, replace with "[stats] [in all cities] <before discovering [tech]> OR [stats] [in all cities] <before adopting [policy]>"
 - "[stats] from every Wonder" - Deprecated As of 3.19.1, replace with "[stats] from every [Wonder]"
 - "[stats] from every [buildingFilter] in cities where this religion has at least [amount] followers" - Deprecated As of 3.19.3, replace with "[stats] from every [buildingFilter] <in cities where this religion has at least [amount] followers>"
 - "+[amount]% [stat] from every [tileFilter/specialist/buildingName]" - Deprecated As of 3.18.17, replace with "[amount]% [stat] from every [tileFilter/specialist/buildingName]"
 - "+[amount]% yield from every [tileFilter]" - Deprecated As of 3.18.17, replace with "[+amount]% Yield from every [tileFilter]"
 - "+25% Production towards any buildings that already exist in the Capital" - Deprecated As of 3.19.3, replace with "[amount]% Production towards any buildings that already exist in the Capital"
 - "City-State Influence degrades [amount]% slower" - Deprecated As of 3.18.17, replace with "[-amount]% City-State Influence degradation"
 - "Quantity of Resources gifted by City-States increased by [amount]%" - Deprecated As of 3.18.17, replace with "[+amount]% resources gifted by City-States"
 - "Happiness from Luxury Resources gifted by City-States increased by [amount]%" - Deprecated As of 3.18.17, replace with "[+amount]% Happiness from luxury resources gifted by City-States"
 - "[amount]% of food is carried over after population increases" - Deprecated As of 3.19.2, replace with "[amount]% Food is carried over after population increases [cityFilter]"
 - "[amount]% of food is carried over [cityFilter] after population increases" - Deprecated As of 3.19.2, replace with "[amount]% Food is carried over after population increases [cityFilter]"
 - "-[amount]% food consumption by specialists [cityFilter]" - Deprecated As of 3.18.2, replace with "[-amount]% Food consumption by specialists [cityFilter]"
 - "50% of excess happiness added to culture towards policies" - Deprecated As of 3.18.2, replace with "[50]% of excess happiness converted to [Culture]"
 - "[amount]% Culture cost of natural border growth [cityFilter]" - Deprecated As of 3.19.2, replace with "[amount]% Culture cost of natural border growth [cityFilter]"
 - "-[amount]% Culture cost of acquiring tiles [cityFilter]" - Deprecated As of 3.19.1, replace with "[-amount]% Culture cost of natural border growth [cityFilter]"
 - "[amount]% cost of natural border growth" - Deprecated As of 3.19.1, replace with "[amount]% Culture cost of natural border growth [cityFilter]"
 - "-[amount]% Gold cost of acquiring tiles [cityFilter]" - Deprecated As of 3.19.1, replace with "[-amount]% Gold cost of acquiring tiles [cityFilter]"
 - "Maintenance on roads & railroads reduced by [amount]%" - Deprecated As of 3.18.17, replace with "[-amount]% maintenance on road & railroads"
 - "-[amount]% maintenance cost for buildings [cityFilter]" - Deprecated As of 3.18.17, replace with "[-amount]% maintenace cost for buildings [cityFilter]"
 - "+[amount] happiness from each type of luxury resource" - Deprecated As of 3.18.17, replace with "[+amount] Happiness from each type of luxury resource"
 - "Culture cost of adopting new Policies reduced by [amount]%" - Deprecated As of 3.18.17, replace with "[amount]% Culture cost of adopting new Policies"
 - "[amount]% Culture cost of adopting new policies" - Deprecated As of 3.19.1, replace with "[amount]% Culture cost of adopting new Policies"
 - "Defensive buildings in all cities are 25% more effective" - Deprecated As of 3.18.17, replace with "[+25]% City Strength from defensive buildings"
 - "[amount]% Strength for [mapUnitFilter] units which have another [mapUnitFilter] unit in an adjacent tile" - Deprecated As of 3.18.17, replace with "[amount]% Strength <for [mapUnitFilter] units> <when adjacent to a [mapUnitFilter] unit>"
 - "Gold cost of upgrading [baseUnitFilter] units reduced by [amount]%" - Deprecated As of 3.18.17, replace with "[-amount]% Gold cost of upgrading <for [baseUnitFilter] units>"
 - "Double gold from Great Merchant trade missions" - Deprecated As of 3.18.17, replace with "[+100]% Gold from Great Merchant trade missions"
 - "Golden Age length increased by [amount]%" - Deprecated As of 3.18.17, replace with "[+amount]% Golden Age length"
 - "+[amount]% Defensive Strength for cities" - Deprecated As of 3.18.17, replace with "[+amount]% Strength for cities <when defending>"
 - "[amount]% Attacking Strength for cities" - Deprecated As of 3.18.17, replace with "[amount]% Strength for cities <when attacking>"
 - "+[amount]% attacking strength for cities with garrisoned units" - Deprecated As of 3.19.1, replace with "[amount]% Strength for cities <with a garrison> <when attacking>"
 - "Population loss from nuclear attacks -[amount]%" - Deprecated As of 3.19.2, replace with "Population loss from nuclear attacks [-amount]% [in this city]"
 - "[amount]% Natural religion spread [cityFilter] with [tech/policy]" - Deprecated As of 3.19.3, replace with "[amount]% Natural religion spread [cityFilter] <after discovering [tech]> OR [amount]% natural religion spread [cityFilter] <after adopting [policy]>"
 - "Melee units pay no movement cost to pillage" - Deprecated As of 3.18.17, replace with "No movement cost to pillage <for [Melee] units>"
 - "[mapUnitFilter] units gain [amount]% more Experience from combat" - Deprecated As of 3.18.12, replace with "[amount]% XP gained from combat <for [mapUnitFilter] units>"
 - "[amount]% maintenance costs for [mapUnitFilter] units" - Deprecated As of 3.18.14, replace with "[amount]% maintenance costs <for [mapUnitFilter] units>"
 - "May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] starting from the [era] at an increasing price ([amount])" - Deprecated As of 3.17.9, removed as of 3.19.3, replace with "May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] at an increasing price ([amount]) <starting from the [era]>"
 - "Provides a free [buildingName] [cityFilter]" - Deprecated As of 3.17.7 - removed 3.18.19, replace with "Gain a free [buildingName] [cityFilter]"
 - "+[amount]% [stat] [cityFilter]" - Deprecated As of 3.17.10 - removed 3.18.18, replace with "[+amount]% [stat] [cityFilter]"
 - "+[amount]% [stat] in all cities" - Deprecated As of 3.17.10 - removed 3.18.18, replace with "[+amount]% [stat] [in all cities]"
 - "[amount]% [stat] while the empire is happy" - Deprecated As of 3.17.1 - removed 3.18.18, replace with "[amount]% [stat] [in all cities] <while the empire is happy>"
 - "Immediately creates the cheapest available cultural building in each of your first [amount] cities for free" - Deprecated As of 3.16.15 - removed 3.18.4, replace with "Provides the cheapest [stat] building in your first [amount] cities for free"
 - "Immediately creates a [buildingName] in each of your first [amount] cities for free" - Deprecated As of 3.16.15 - removed 3.18.4, replace with "Provides a [buildingName] in your first [amount] cities for free"
 - "[mapUnitFilter] units deal +[amount]% damage" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <for [mapUnitFilter] units>"
 - "+10% Strength for all units during Golden Age" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[+10]% Strength <for [All] units> <during a Golden Age>"
 - "[amount]% Strength for [mapUnitFilter] units in [tileFilter]" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <for [mapUnitFilter] units> <when fighting in [tileFilter] tiles>"
 - "+15% Combat Strength for all units when attacking Cities" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[+15]% Strength <for [All] units> <vs cities> <when attacking>"
 - "+[amount] Movement for all [mapUnitFilter] units" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount] Movement <for [mapUnitFilter] units>"
 - "+1 Movement for all units during Golden Age" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount] Movement <for [All] units> <during a Golden Age>"
 - "[amount] Sight for all [mapUnitFilter] units" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount] Sight <for [mapUnitFilter] units>"
 - "[amount]% Spread Religion Strength for [mapUnitFilter] units" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Spread Religion Strength <for [mapUnitFilter] units>"
 - "+[amount]% Production when constructing [baseUnitFilter] units [cityFilter]" - Deprecated As of 3.17.10 - removed 3.18.5, replace with "[+amount]% Production when constructing [baseUnitFilter] units [cityFilter]"
 - "+[amount]% Production when constructing [stat] buildings" - Deprecated As of 3.17.10 - removed 3.18.5, replace with "[amount]% Production when constructing [buildingFilter] buildings [cityFilter]"
 - "+[amount]% Production when constructing [constructionFilter]" - Deprecated As of 3.17.10 - removed 3.18.5, replace with "[amount]% Production when constructing [buildingFilter] buildings [cityFilter]"
 - "+[amount]% Production when constructing a [buildingName]" - Deprecated As of 3.17.10 - removed 3.18.5, replace with "[amount]% Production when constructing [buildingFilter] buildings [cityFilter]"
 - "+[amount]% Production when constructing [constructionFilter] [cityFilter]" - Deprecated As of 3.17.10 - removed 3.18.5, replace with "[amount]% Production when constructing [buildingFilter] buildings [cityFilter]"
 - "Not displayed as an available construction unless [buildingName] is built" - Deprecated As of 3.16.11, replace with "Not displayed as an available construction without [buildingName]"
 - "[stats] once [tech] is discovered" - Deprecated As of 3.17.10 - removed 3.18.19, replace with "[stats] <after discovering [tech]>"
 - "Cannot enter ocean tiles until Astronomy" - Deprecated As of 3.18.6, replace with "Cannot enter ocean tiles <before discovering [Astronomy]>"
 - "[amount]% Bonus XP gain" - Deprecated As of 3.18.12, replace with "[amount]% XP gained from combat"
 - "+[amount]% Strength when attacking" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <when attacking>"
 - "+[amount]% Strength when defending" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <shen defending>"
 - "[amount]% Strength when defending vs [mapUnitFilter] units" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <when defending> <vs [mapUnitFilter] units>"
 - "+[amount]% defence in [tileFilter] tiles" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <when fighting in [tileFilter] tiles> <when defending>"
 - "+[amount]% Strength in [tileFilter]" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount]% Strength <when fighting in [tileFilter] tiles>"
 - "[amount] Visibility Range" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[amount] Sight"
 - "Limited Visibility" - Deprecated As of 3.17.5 - removed 3.18.5, replace with "[-1] Sight"
 - "Cannot be built on [tileFilter] tiles until [tech] is discovered" - Deprecated As of 3.18.5, replace with "Cannot be built on [tileFilter] tiles <before discovering [tech]>"
 - "Deal [amount] damage to adjacent enemy units" - Deprecated As of 3.18.17, replace with "Adjacent enemy units ending their turn take [30] damage"
 - "[stats] on [tileFilter] tiles once [tech] is discovered" - Deprecated As of 3.17.10 - removed 3.18.19, replace with "[stats] from [tileFilter] tiles <after discovering [tech]>"
 - "Deal 30 damage to adjacent enemy units" - Deprecated As of 3.17.10 - removed 3.18.19, replace with "Adjacent enemy units ending their turn take [30] damage"