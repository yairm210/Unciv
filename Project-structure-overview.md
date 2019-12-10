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
 - Which nation this is - a String value that points us towards a certain Nation
 - Various Managers for the different aspects of the civilization - `PolicyManager`, `GoldenAgeManager`, `GreatPersonManager`, `TechManager`, `VictoryManager`, `DiplomacyManager`

# A City - `CityInfo`

This contains the information about a specific city.

Beyond basic information like name, location on map etc, the most important classes it contains are:

- Calculating the yield of the city - `CityStats`
- Managers for the various aspects - `PopulationManager`, `CityConstructions`, `CityExpansionManager`
- The tiles controlled and worked by the city
