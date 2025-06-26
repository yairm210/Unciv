# Map-related JSON files

## Terrains.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Terrains.json)

This file contains the base terrains, terrain features and natural wonders that can appear on the map.

Each terrain entry has the following structure:

| Attribute                  | Type                                                                | Default  | Notes                                                                                         |
|----------------------------|---------------------------------------------------------------------|----------|-----------------------------------------------------------------------------------------------|
| name                       | String                                                              | Required | [^A]                                                                                          |
| type                       | Enum                                                                | Required | Land, Water, TerrainFeature, NaturalWonder [^B]                                               |
| occursOn                   | List of Strings                                                     | none     | Only for terrain features and Natural Wonders: The baseTerrain it can be placed on            |
| turnsInto                  | String                                                              | none     | Only for NaturalWonder: optional mandatory base terrain [^C]                                  |
| weight                     | Integer                                                             | 10       | Only for NaturalWonder: _relative_ weight of being picked by the map generator                |
| [`<stats>`](#general-stat) | Float                                                               | 0        | Per-turn yield or bonus yield for the tile                                                    |
| overrideStats              | Boolean                                                             | false    | If true, a feature's yields replace any yield from underlying terrain instead of adding to it |
| unbuildable                | Boolean                                                             | false    | If true, nothing can be built here - not even resource improvements                           |
| impassable                 | Boolean                                                             | false    | No unit can enter unless it has a special unique                                              |
| movementCost               | Integer                                                             | 1        | Base movement cost                                                                            |
| defenceBonus               | Float                                                               | 0        | Combat bonus for units being attacked here                                                    |
| RGB                        | [List of 3× Integer](5-Miscellaneous-JSON-files.md#rgb-colors-list) | Gold     | RGB color for 'Default' tileset display                                                       |
| uniques                    | List of Strings                                                     | empty    | List of [unique abilities](../../uniques) this terrain has                                    |
| civilopediaText            | List                                                                | empty    | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text)                 |

[^A]: Some names have special meanings. `Grassland` is used as fallback in some cases - e.g. Civilopedia prefers to displays a TerrainFeature on top of it, unless `occursOn` is not empty and does not contain it.
      `River` is hardcoded to be used to look up a [Stats](../../uniques.md#global-uniques) unique to determine the bonuses an actual River provides (remember, rivers live on the edges not as terrain).
      River should always be a TerrainFeature and have the same uniques the one in the vanilla rulesets has - if you change that, expect surprises.
[^B]: A base ruleset mod is always expected to provide at least one Land and at least one Water terrain. We do not support Land-only or Water-only mods, even if they might be possible to pull off.
[^C]: If set, the base terrain is changed to this after placing the Natural Wonder, and terrain features cleared. Otherwise, terrain features are reduced to only those present in occursOn.

## TileImprovements.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/TileImprovements.json)

This file lists the improvements that can be constructed or created on a map tile by a unit having the appropriate unique.

Note that improvements have two visual representations - icon and pixel graphic in the tileset. Omitting the icon results in a horribly ugly user interface, while omitting tileset graphics will just miss out on an _optional_ visualization. If you provide a pixel graphic for FantasyHex, please be aware of the layering system and the ruleVariants in the tileset json. A single graphic may suffice if it has lots of transparency, as it will be drawn on top of all other terrain elements.

Each improvement has the following structure:

| Attribute            | Type            | Default  | Notes                                                                                                                                                                                  |
|----------------------|-----------------|----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name                 | String          | Required | [^A]                                                                                                                                                                                   |
| terrainsCanBeBuiltOn | List of Strings | empty    | Terrains that this improvement can be built on [^B]. Removable terrain features will need to be removed before building an improvement [^C]. Must be in [Terrains.json](#terrainsjson) |
| techRequired         | String          | none     | The name of the technology required to build this improvement                                                                                                                          |
| replaces             | String          | none     | The name of a improvement that should be replaced by this improvement. Must be in [TileImprovements.json](#TileImprovementsjson)                                                       |
| uniqueTo             | String          | none     | The name of the nation this improvement is unique for                                                                                                                                  |
| [`<stats>`](#stats)  | Integer         | 0        | Per-turn bonus yield for the tile                                                                                                                                                      |
| turnsToBuild         | Integer         | -1       | Number of turns a worker spends building this. If -1, the improvement is unbuildable [^D]. If 0, the improvement is always built in one turn                                           |
| uniques              | List of Strings | empty    | List of [unique abilities](../../uniques) this improvement has                                                                                                                         |
| shortcutKey          | String          | none     | Keyboard binding. Currently, only a single character is allowed (no function keys or Ctrl combinations)                                                                                |
| civilopediaText      | List            | empty    | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text)                                                                                                          |

[^A]: Special improvements: Road, Railroad, Remove \*, Cancel improvement order, City ruins, City center, Barbarian encampment - these have special meanings hardcoded to their names.
[^B]: Improvements with an empty `terrainsCanBeBuiltOn` list and positive `turnsToBuild` value can only be built on [resources](#tileresourcesjson) with `improvedBy` or `improvement` that contains the corresponding improvement.
[^C]: The removal of terrain features is optional if the feature is named in `terrainsCanBeBuiltOn` _or_ the unique `Does not need removal of [tileFilter]` is used (e.g. Camp allowed by resource).
[^D]: They can still be created with the UnitAction unique `Can instantly construct a [improvementFilter] improvement`.

## TileResources.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/TileResources.json)

This file lists the resources that a map tile can have.

Note the predefined `resourceType` enum cannot be altered in a json.

Note also that resources have two visual representations - icon and pixel graphic in the tileset. Omitting the icon results in a horribly ugly user interface, while omitting tileset graphics will miss out on a visualization on the map. If you provide a pixel graphic for FantasyHex, please be aware of the layering system and the ruleVariants in the tileset json. A single graphic may suffice if it has lots of transparency, as it will be drawn on top of terrain and features but below an improvement - if the single improvement graphic exists at all.

Each resource has the following structure:

| Attribute            | Type            | Default  | Notes                                                                                                                                       |
|----------------------|-----------------|----------|---------------------------------------------------------------------------------------------------------------------------------------------|
| name                 | String          | Required |                                                                                                                                             |
| resourceType         | Enum            | Bonus    | Bonus, Luxury or Strategic                                                                                                                  |
| terrainsCanBeFoundOn | List of Strings | empty    | Terrains that this resource can be found on. Must be in [Terrains.json](#terrainsjson)                                                      |
| [`<stats>`](#stats)  | Integer         | 0        | Per-turn bonus yield for the tile                                                                                                           |
| improvementStats     | Object          | none     | The additional yield when improved, see [specialized stats](3-Map-related-JSON-files.md#specialized-stats)                                  |
| revealedBy           | String          | none     | The technology name required to see, work and improve this resource                                                                         |
| improvedBy           | List of strings | empty    | The improvements required for obtaining this resource. Must be in [TileImprovements.json](#tileimprovementsjson)                            |
| improvement          | String          | none     | The improvement required to obtain this resource. Must be in [TileImprovements.json](#tileimprovementsjson) (redundant due to `improvedBy`) |
| unique               | List of Strings | empty    | List of [unique abilities](../../uniques) this resource has                                                                                 |
| civilopediaText      | List            | empty    | See [civilopediaText chapter](5-Miscellaneous-JSON-files.md#civilopedia-text)                                                               |

## Ruins.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Ruins.json)

This optional file contains the possible rewards ancient ruins give.

Base ruleset Mods can omit the file, in which case they inherit the ones from the Vanilla ruleset. They can, however, provide a file with an empty list (`[]`) to avoid that. In this case there should be no improvements with the ["Provides a random bonus when entered" Unique](../uniques.md#improvement-uniques). Conversely, if there are such improvements, the Mod checker will flag an empty Ruins file as error.

Each of the objects in the file represents a single reward you can get from ruins. It has the following structure:

| Attribute            | Type            | Default  | Notes                                                                                                                                                                                             |
|----------------------|-----------------|----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| name                 | String          | Required | Name of the ruins. Never shown to the user, but they have to be distinct                                                                                                                          |
| notification         | String          | Required | Notification added to the user when this reward is chosen. If omitted, an empty notification is shown. Some notifications may have parameters, refer to the table below.                          |
| weight               | Integer (≥0)    | 1        | _Relative_ weight this reward is chosen next [^E]                                                                                                                                                 |
| uniques              | List of Strings | empty    | List of [unique abilities](../../uniques) that will trigger when entering the ruins. If more than 1 unique is added, the notification will be shown multiple times due to a bug (may be outdated) |
| excludedDifficulties | List of Strings | empty    | A list of all difficulties on which this reward may _not_ be awarded                                                                                                                              |

[^E]: The exact algorithm for choosing a reward is the following:

- Create a list of all possible rewards. Each reward's frequency in the list corresponds to its weight, a reward with weight one will appear once, a reward with weight two will appear twice, etc.
- Shuffle this list
- Try give rewards starting from the top of the list. If any of the uniques of the rewards is valid in this context, reward it and stop trying more rewards.

### Notifications

Some of the rewards ruins can give will have results that are not deterministic when writing it in the JSON, so creating a good notification for it would be impossible. An example for this would be the "Gain [50]-[100] [Gold]" unique, which will give a random amount of gold. For this reason, we allow some notifications to have parameters, in which values will be filled, such as "You found [goldAmount] gold in the ruins!". All the uniques which have this property can be found below.
<!-- (need to update) -->

| Unique                                           | Parameters                                                                                                                                                      |
|--------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Free [] found in the ruins                       | The name of the unit will be filled in the notification, including unique units of the nation                                                                   |
| [] population in a random city                   | The name of the city to which the population is added will be filled in the notification                                                                        |
| Gain []-[] []                                    | The exact amount of the stat gained will be filled in the notification                                                                                          |
| [] free random reasearchable Tech(s) from the [] | The notification must have placeholders equal to the number of techs granted this way. Each of the names of these free techs will be filled in the notification |
| Gain enough Faith for a Pantheon                 | The amount of faith gained is filled in the notification                                                                                                        |
| Gain enough Faith for []% of a Great Prophet     | The amount of faith gained is filled in the notification                                                                                                        |

### Specific uniques

A few uniques can be added to ancient ruin effects to modify when they can be earned. These are:

- "Only available after [amount] turns"
- "Only available <when religion is enabled>"
- "Hidden after a great prophet has been earned"

## [Tileset-specific json](../../Creating-a-custom-tileset.md)

[Link to original FantasyHex](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/TileSets/FantasyHex.json)

A mod can define new Tilesets or add to existing ones, namely FantasyHex. There is one json file per Tileset, named same as the Tileset, and placed in a subfolder named "TileSets" relative to the other json files. This is called TileSetConfig and has the following structure:

| Attribute                                                                         | Type    | Default      | Notes                                                                                                                                |
|-----------------------------------------------------------------------------------|---------|--------------|--------------------------------------------------------------------------------------------------------------------------------------|
| [useColorAsBaseTerrain](../../Creating-a-custom-tileset.md#useColorAsBaseTerrain) | Boolean | false        |                                                                                                                                      |
| [useSummaryImages](../../Creating-a-custom-tileset.md#useSummaryImages)           | Boolean | false        |                                                                                                                                      |
| [unexploredTileColor](../../Creating-a-custom-tileset.md#unexploredTileColor)     | Color   | Dark Gray    | `{"r":0.25,"g":0.25,"b":0.25,"a":1}`                                                                                                 |
| [fogOfWarColor](../../Creating-a-custom-tileset.md#fogOfWarColor)                 | Color   | Black        | `{"r":0,"g":0,"b":0,"a":1}`                                                                                                          |
| [fallbackTileSet](../../Creating-a-custom-tileset.md#fallbackTileSet)             | String  | "FantasyHex" | null to disable                                                                                                                      |
| [tileScale](../../Creating-a-custom-tileset.md#tileScale)                         | Float   | 1.0          | The scale of all tiles. Can be used to increase or decrease the size of every tile                                                   |
| [tileScales](../../Creating-a-custom-tileset.md#tileScales)                       | Object  | empty        | Used by the "Minimal" tileset to scale all its tiles except the base terrain down. Overrides `tileScale` value for specified terrain |
| [ruleVariants](../../Creating-a-custom-tileset.md#ruleVariants)                   | Object  | empty        | [See here](#layering-images)                                                                                                         |

### Layering images

ruleVariants control substitutions when layering images for a tile, they are list looking like:

```json
"ruleVariants": {
    "Grassland+Forest": ["Grassland", "GrasslandForest"],
    "Plains+Forest": ["Plains", "PlainsForest"],
    "Plains+Jungle": ["Plains", "PlainsJungle"],
    // . . .
}
```

Each line means "if the tile content is this... then combine the following png images". The key part follows a specific order and must match in its entirety, meaning "Plains+Forest" is not valid for "Plains+Forest+Deer", and when it matches no other image layering is done except roads and units (I think - _WIP_).

When TileSetConfig's for the same Tileset are combined, for the first three properties the last mod wins, while ruleVariants are merged, meaning only an entry with the same key overwrites an earlier entry. (TODO)

## Stats

Terrains, features, resources and improvements may list yield statistics. The statistics can be one of the following:

- production
- food
- gold
- science
- culture
- happiness
- faith

### General stat

If an object carries general stat(s), it contains any combination (or none) of the above stats, each mapping to a corresponding number [^1]. For Example:

```json
"gold": 2,
"improvement": "Quarry",
```

### Specialized stats

For specialized stats, they might come as sub-object in a named field. The sub-object contains any combination (or none) of the above stats, each mapping to a corresponding number [^1]. For Example:

```json
"improvement": "Quarry",
"improvementStats": { "gold": 1, "production": 1 },
```

[^1]: The values are usually integers, though the underlying code supports floating point. The effects are, however, insufficiently tested and therefore -so far- using fractional stats is unsupported. Go ahead and thoroughly test that in a mod and help out with feedback 😁.
