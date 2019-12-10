# Overview

Civ, and therefore Unciv, is a game with endless interconnectivity - everything affects everything else.

In order to have some semblance of order, we'll go over the main classes in the order in which they are serialized.

So yes, you can - for instance - get the center tile of a city, a TileInfo, directly from CityInfo. But delving into all the connections would only harm the point of this overview, that's what the actual code is for ;)

* GameInfo
    * CivilizationInfo
        * CityInfo
    * TileMap
        * TileInfo
            * MapUnit
    * RuleSet (unique in that it is not part of the game state)


# The Game - `GameInfo`

First off, let's clarify: When we say "The Game", we mean the *state* of the game (what turn it is, who the players are, what each one has etc) and not the *UI* of the game.

That is, The Game is the *currently played* game, not *Unciv*.

The game contains three major parts:

- The list of the players, or civilizations - `List<CivilizationInfo>`
- The map upon which the game is played - `TileMap`
- The ruleset by which the game is played - `RuleSet`. This includes what technologies, buildings, units etc. are available, and IS NOT serialized and deserialized, but comes straight from the game files - more on that later.
- Parameters unique to this game - difficulty, game speed, victory conditions, etc.

When we save the game, or load the game, we're actually serializing and deserializing this class, which means that the this class is the root of the entire game state.

Most objects in the "state tree" have a transient reference to their parent, meaning the tree can be traversed in-code in all directions, and frequently is.

# A Civilization - `CivilizationInfo`

This represents one of the players of the game, and NOT a specific nation - meaning, not France, but rather "Player X who is France in this game". In another game, there will be another France.

As one of the focal points of the game, it contains a lot of important information, the most important of which are:

 - The list of cities the civilization has - `List<CityInfo>`
 - Which nation this is - references a certain Nation (part of the ruleset)
 - Various Managers for the different aspects of the civilization - `PolicyManager`, `GoldenAgeManager`, `GreatPersonManager`, `TechManager`, `VictoryManager`, `DiplomacyManager`

# A City - `CityInfo`

This contains the information about a specific city.

Beyond basic information like name, location on map etc, the most important classes it contains are:

- Calculating the yield of the city - `CityStats`
- Managers for the various aspects - `PopulationManager`, `CityConstructions`, `CityExpansionManager`
- The tiles controlled and worked by the city - only their locations are permanently saved in the CityInfo, the actual information is in the TileInfo in the TileMap

# The map - `TileMap`

This contains mostly helper functions and acts as a wrapper for the list of tiles it contains

# A tile - `TileInfo`

Each tile is comprised of several layers, and so has information for each.

Tiles have, primarily:
- A base terrain - Grassland, Hills, Desert etc. References a certain `Terrain` (part of the ruleset)
- An optional terrain feature - Forest, Jungle, Oasis etc.  References a certain `Terrain`  (part of the ruleset)
- An optional resource - Iron, Dye, Wheat etc. References a certain `TileResource` (part of the ruleset)
- An improvement built on the tile, if any.  References a certain `TileImprovement` (part of the ruleset)
- The units that are currently in the tile - `MapUnit`

# A unit on the map - `MapUnit`

Unlike buildings, Unit in Unciv has two meanings. One is a *Type* of unit (like Spearman), and one is a specific instance of a unit (say, a Babylonian Spearman, at a certain position, with X health).

`MapUnit` is a specific instance of a unit, whereas `BaseUnit` is the type of unit.

Main information:
- A name - references a specific `BaseUnit`
- Health and Movement
- Promotion status - `UnitPromotions`

# Ruleset

So far so good - but what of everything that makes Civ, Civ? The units, the buildings, the nations, the improvements etc?

Since these things remain the same for every game, these are not saved on a per-game basis, but rather are saved in json files in Unciv's asset folder.

Each class in the game state that saves one of these will reference it by name, and when the game is running it will check the Ruleset to find the relevant information for that object.

The various objects are:
- `Technology` - referenced mainly in `CivilizationInfo.TechManager`
- `Nations` - referenced mainly in `CivilizationInfo`
- `Policy`  - referenced mainly in `CivilizationInfo.PolicyManager` (seeing a pattern here?)
- `Building` - referenced mainly in `CityInfo.ConstructionManager`
- `BaseUnit` - referenced mainly in `MapUnit`
- `Promotion` - referenced mainly in `MapUnit`
- `Terrain` - referenced mainly in `TileInfo`
- `TileResource` - referenced mainly in `TileInfo`
- `TileImprovement` - referenced mainly in `TileInfo`

There are also Translations in the Ruleset, but they technically have nothing to do with the game state but rather with the UI display.

The information for all of these is in json files in `android\assets\jsons`
