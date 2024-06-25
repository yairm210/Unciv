# Project structure

Since LibGDX, and therefore Unciv, are built for multi-platform support, the project structure is built accordingly.

99% of the code is in the [core](https://github.com/yairm210/Unciv/tree/master/core) project, which contains all the platform-independent code.

The [desktop](https://github.com/yairm210/Unciv/tree/master/desktop) and [android](https://github.com/yairm210/Unciv/tree/master/android) folders contain platform-specific things, and the Android folder also contains the game Images and the all-important Assets, which are required for running from Desktop as well, so we bundle them up into the .jar file when releasing.

The [tests](https://github.com/yairm210/Unciv/tree/master/tests) folder contains tests that can be run manually via gradle with `./gradlew tests:test`, and are run automatically by Travis for every push.

The [server](https://github.com/yairm210/Unciv/tree/master/server) folder contains the sources for the UncivServer (a host enabling communication between multiplayer game instances), which is packaged into its own separate jar.


## Translations

Before we get to the Classes, a word on Languages. Unciv is playable in several handfuls of languages, and there's magic to support that. Whenever you include a new string in code you will need to give it a quick evaluation - will users see it, and if so, what do I need to do to support its translations. Sometimes you may not need to do anything, sometimes you will add a line to the [translation templates](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/translations/template.properties), and sometimes you will adapt the string formatting to support the translations. For details, see [the 'Translation generation - for developers' chapter](../Other/Translating.md#translation-generation---for-developers).

## Major classes

Civ, and therefore Unciv, is a game with endless interconnectivity - everything affects everything else.

In order to have some semblance of order, we'll go over the main classes in the order in which they are serialized.

So yes, you can - for instance - get the center tile of a city, a TileInfo, directly from CityInfo. But delving into all the connections would only harm the point of this overview, that's what the actual code is for ;)

The Game State:

-   GameInfo
    -   CivilizationInfo
        -   CityInfo
    -   TileMap
        -   TileInfo
            -   MapUnit
    -   RuleSet (unique in that it is not part of the game state)

The UI:

-   MainMenuScreen
-   NewGameScreen
-   WorldScreen
-   CityScreen
-   MapEditorScreen
-   Picker Screens - TechPickerScreen, PolicyPickerScreen, ImprovementPickerScreen, PromotionPickerScreen

## Game State

### The Game - `GameInfo`

First off, let's clarify: When we say "The Game", we mean the *state* of the game (what turn it is, who the players are, what each one has etc) and not the *UI* of the game.

That is, The Game is the *currently played* game, not *Unciv*.

The game contains three major parts:

-   The list of the players, or civilizations - `List<CivilizationInfo>`
-   The map upon which the game is played - `TileMap`
-   The ruleset by which the game is played - `RuleSet`. This includes what technologies, buildings, units etc. are available, and IS NOT serialized and deserialized, but comes straight from the game files - more on that later.
-   Parameters unique to this game - difficulty, game speed, victory conditions, etc.

When we save the game, or load the game, we're actually serializing and deserializing this class, which means that the this class is the root of the entire game state.

Most objects in the "state tree" have a transient reference to their parent, meaning the tree can be traversed in-code in all directions, and frequently is.

### A Civilization - `CivilizationInfo`

This represents one of the players of the game, and NOT a specific nation - meaning, not France, but rather "Player X who is France in this game". In another game, there will be another France.

As one of the focal points of the game, it contains a lot of important information, the most important of which are:

-   The list of cities the civilization has - `List<CityInfo>`
-   Which nation this is - references a certain Nation (part of the ruleset)
-   Various Managers for the different aspects of the civilization - `PolicyManager`, `GoldenAgeManager`, `GreatPersonManager`, `TechManager`, `VictoryManager`, `DiplomacyManager`

### A City - `CityInfo`

This contains the information about a specific city.

Beyond basic information like name, location on map etc, the most important classes it contains are:

-   Calculating the yield of the city - `CityStats`
-   Managers for the various aspects - `PopulationManager`, `CityConstructions`, `CityExpansionManager`
-   The tiles controlled and worked by the city - only their locations are permanently saved in the CityInfo, the actual information is in the TileInfo in the TileMap

### The map - `TileMap`

This contains mostly helper functions and acts as a wrapper for the list of tiles it contains

### A tile - `TileInfo`

Each tile is comprised of several layers, and so has information for each.

Tiles have, primarily:

-   A base terrain - Grassland, Hills, Desert etc. References a certain `Terrain` (part of the ruleset)
-   An optional terrain feature - Forest, Jungle, Oasis etc. References a certain `Terrain` (part of the ruleset)
-   An optional resource - Iron, Dye, Wheat etc. References a certain `TileResource` (part of the ruleset)
-   An improvement built on the tile, if any. References a certain `TileImprovement` (part of the ruleset)
-   The units that are currently in the tile - `MapUnit`

### A unit on the map - `MapUnit`

Unlike buildings, Unit in Unciv has two meanings. One is a *Type* of unit (like Spearman), and one is a specific instance of a unit (say, a Babylonian Spearman, at a certain position, with X health).

`MapUnit` is a specific instance of a unit, whereas `BaseUnit` is the type of unit.

Main information:

-   A name - references a specific `BaseUnit`
-   Health and Movement
-   Promotion status - `UnitPromotions`

### Ruleset

So far so good - but what of everything that makes Civ, Civ? The units, the buildings, the nations, the improvements etc?

Since these things remain the same for every game, these are not saved on a per-game basis, but rather are saved in json files in Unciv's asset folder.

Each class in the game state that saves one of these will reference it by name, and when the game is running it will check the Ruleset to find the relevant information for that object.

The various objects are:

-   `Technology` - referenced mainly in `CivilizationInfo.TechManager`
-   `Nations` - referenced mainly in `CivilizationInfo`
-   `Policy` - referenced mainly in `CivilizationInfo.PolicyManager` (seeing a pattern here?)
-   `Building` - referenced mainly in `CityInfo.ConstructionManager`
-   `BaseUnit` - referenced mainly in `MapUnit`
-   `Promotion` - referenced mainly in `MapUnit`
-   `Terrain` - referenced mainly in `TileInfo`
-   `TileResource` - referenced mainly in `TileInfo`
-   `TileImprovement` - referenced mainly in `TileInfo`

There are also Translations in the Ruleset, but they technically have nothing to do with the game state but rather with the UI display.

The information for all of these is in json files in `android\assets\jsons`

## UI

`UncivGame` is the 'base' class for the UI, from which everything starts, but it itself doesn't do much.

When we change a screen, we're changing a value in UncivGame, the interesting stuff happens in the screens themselves.

### The main menu - `MainMenuScreen`

This is what the user sees when first entering the game. It acts as a hub to loading games, adding mods, options etc, without loading an actual game upfront - this allows us to differentiate between "User can't enter game" and "User can't load game" problems

### Starting a new game - `NewGameScreen`

This is basically a giant setting screen for GameOptions and MapOptions classes, divided into:

-   GameOptionsTable - game speed, mods, etc
-   MapOptionsTable - either from preexisting map file or generated, in which case: size, map generation type, etc.
-   PlayerPickerTable - What civs are in the game and who controls them

### The World Screen - `WorldScreen`

90% of the game is spent on this screen, so naturally it's the fullest, with the most things happening.

This is the main hub of the game, with all other screens being opened from it, and closing back to reveal it.

Most notable are:

-   The map itself - a `TileMapHolder` - with each of the rendered tiles being a `TileGroup`
-   The information panels - `WorldScreenTopBar` for stats and resources, `UnitTable` for the currently selected unit, `TileInfoTable` or the currently selected tile, `BattleTable` for battle simulation, and `NotificationsScroll` for the notifications
-   The minimap - `MinimapHolder`
-   Buttons linking to other screens - to the `TechPickerScreen`, `EmpireOverviewScreen`, and `PolicyPickerScreen`
-   The almighty Next Turn button

### The city screen - `CityScreen`

The second-most important screen.

Notable parts:

-   the City Stats table - should definitely be its own class come to think of it
-   The construction list and current construction (bottom left) - `ConstructionsTable`
-   Existing buildings, specialists and stats drilldown - `CityInfoTable`

## Others

A few words need to be said about the NextTurn process, but there isn't really a good place for it so I'll put it here.

We clone the GameInfo and use a "new" GameInfo for each turn because of 2 reasons.

The first is multithreading and thread safety, and the second is multiplayer reproducibility.

The first point is pretty basic. The NextTurn needs to happen in a separate thread so that the user can still have a responsive game when it's off doing stuff. Stuff in the GameInfo changes on NextTurn, so if you're rendering that same GameInfo, this could cause conflicts. Also, after NextTurn we generally autosave, and if stuff changes in the state while we're trying to serialize it to put it in the save file, that's Not Fun. A single clone solves both of these problems at once.

The second point is less obvious. If we use our mutable state, changing stuff in place, then what happens when we're playing in Multiplayer? Multiplayer is based upon the fact that you can receive an entire game state and go from there, and in fact the move to multiplayer was what made the whole "clone" thing necessary (on the way it also solved the aforementioned threading problems)
