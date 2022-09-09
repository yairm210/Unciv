# Map-related JSON files

## Terrains.json

This file lists the base terrains, terrain features and natural wonders that can appear on the map.

Each terrain entry can have the following properties:

| Attribute | Type | Optional | Notes |
| --------- | ---- | -------- | ----- |
| name | String | Required |  |
| type | Enum | Required | Land, Water, TerrainFeature, NaturalWonder |
| occursOn | List | Default none | Only for terrain features and Natural Wonders: The baseTerrain it can be placed on |
| turnsInto  | String | Default none | Only for Natural Wonders: After placing the Natural Wonder its base terrain is changed to this |
| weight | Integer | Default 10 | Only for Natural Wonders: _relative_ weight it will be picked by the map generator |
| `<stats>` | Float | Optional | Per-turn yield or bonus yield for the tile, see [Stats](#stats) |
| overrideStats | Boolean | Default false | If on, a feature's yields replace any yield from underlying terrain instead of adding to it |
| unbuildable | Boolean | Default false | If true, nothing can be built here - not even resource improvements |
| impassable | Boolean | Default false | no unit can enter unless it has a special unique |
| movementCost | Integer | Default 1 | base movement cost |
| defenceBonus | Float | Default 0 | combat bonus for units being attacked here |
| RGB | List Integer * 3 | Default 'Gold' | RGB color for 'Default' tileset display |
| uniques | List | Default empty | List of effects, [see here](../Modders/uniques.md#terrain-uniques) |
| civilopediaText | List | Default empty | see [civilopediaText chapter](Miscellaneous-JSON-files.md#civilopedia-text) |


## TileImprovements.json

This file lists the improvements that can be constructed or created on a map tile by a unit (any unit having the appropriate unique).

Note that improvements have two visual representations - icon and pixel graphic in the tileset. Omitting the icon results in a horribly ugly user interface, while omitting tileset graphics will just miss out on an _optional_ visualization. If you provide a pixel graphic for FantasyHex, please be aware of the layering system and the ruleVariants in the tileset json. A single graphic may suffice if it has lots of transparency, as it will be drawn on top of all other terrain elements.

Each improvement can have the following properties:

| Attribute | Type | Optional | Notes |
| --------- | ---- | -------- | ----- |
| name | String | Required |  |
| terrainsCanBeFoundOn | List | Default empty | [Terrains](#terrainsjson) that allow this resource |
| techRequired | String | Default none | The name of the technology required to build this improvement |
| uniqueTo | String | Default none | The name of the nation this improvement is unique for |
| `<stats>` | Float | Optional | Per-turn bonus yield for the tile, see [Stats](#stats) |
| turnsToBuild | Integer |  | Number of turns a worker spends building this (ignored for 'create' actions) |
| uniques | List | Default empty | List of effects, [see here](../Modders/Unique-parameters.md#improvement-uniques) |
| shortcutKey | String | Default none | Keyboard binding. At the moment a single character (no function keys or Ctrl combinations) |
| civilopediaText | List | Default empty | see [civilopediaText chapter](Miscellaneous-JSON-files.md#civilopedia-text) |

-   Tiles with no terrains, but positive turns to build, can be built only when the tile has a resource that names this improvement or special uniques are used. (TODO: missing something?)
-   Tiles with no terrains, and no turns to build, are like great improvements - they're placeable. That means a unit could exist with a 'Can create [this]' unique, and that the improvement will not show in a worker's improvement picker dialog.
-   Removable Terrain features will need to be removed before building an improvement - unless the feature is named in terrainsCanBeFoundOn _or_ the unique "Does not need removal of [terrainFeature]" is used (e.g. Camp allowed by resource).
-   Special improvements: Road, Railroad, Remove \*, Cancel improvement order, City ruins, City center, Barbarian encampment - these have special meanings hardcoded to their names.

## TileResources.json

This file lists the resources that a map tile can have.

Note the predefined resource _types_ cannot be altered in json.

Note also that resources have two visual representations - icon and pixel graphic in the tileset. Omitting the icon results in a horribly ugly user interface, while omitting tileset graphics will miss out on a visualization on the map. If you provide a pixel graphic for FantasyHex, please be aware of the layering system and the ruleVariants in the tileset json. A single graphic may suffice if it has lots of transparency, as it will be drawn on top of terrain and features but below an improvement - if the single improvement graphic exists at all.

Each resource can have the following properties:

| Attribute | Type | Optional | Notes |
| --------- | ---- | -------- | ----- |
| name | String | Required |  |
| resourceType | String | Default Bonus | Bonus, Luxury or Strategic |
| terrainsCanBeFoundOn | List | Default empty | [Terrains](#terrainsjson) that allow this resource |
| `<stats>` | Float | Optional | Per-turn bonus yield for the tile, see [Stats](#stats), can be repeated |
| improvement | String | Default empty | The improvement ([TileImprovements.json](#tileimprovementsjson)) for this resource |
| improvementStats | Object | Default empty | The additional yield when improved as sub-object with one or more [Stats](#stats) |
| revealedBy | String | Default empty | The technology name required to see, work and improve this resource |
| unique | String | Default empty | Effects, [see here](../Modders/Unique-parameters.md#resource-uniques) - at the moment only one unique may be added |
| civilopediaText | List | Default empty | see [civilopediaText chapter](Miscellaneous-JSON-files.md#civilopedia-text) |


## Ruins.json

[Link to original](https://github.com/yairm210/Unciv/blob/master/android/assets/jsons/Civ%20V%20-%20Vanilla/Ruins.json)

This file contains the possible rewards ancient ruins give. It is not required, if omitted, the default file for the game is used, even in baseRuleSet mods.

Each of the objects in the file represents a single reward you can get from ruins. It has the following properties:

| Attribute | Type | Optional | Notes |
| --------- | ---- | -------- | ----- |
| name | String | required | Name of the ruins. Never shown to the user, but they have to be distinct |
| notification | String | required | Notification added to the user when this reward is chosen. If omitted, an empty notification is shown. Some notifications may have parameters, refer to the table below. |
| weight | Integer (≥0) | defaults to 1 | Weight this reward should have. Higher weights result in a higher chance of it being chosen* |
| uniques | List of Strings | defaults to none | [uniques]Uniques#one-time-effect) or [uniques](../Modders/Unique-parameters.md#one-time-effect-units) that will trigger when entering the ruins. If more than 1 unique is added, the notification will be shown multiple times due to a bug. |
| excludedDifficulties | List of Strings | defaults to None | A list of all difficulties on which this reward may _not_ be awarded |

The exact algorithm for choosing a reward is the following:

-   Create a list of all possible rewards, with rewards with a higher weight appearing multiple times. A reward with weight one will appear once, a reward with weight two will appear twice, etc.
-   Shuffle this list
-   Try give rewards starting from the top of the list. If any of the uniques of the rewards is valid in this context, reward it and stop trying more rewards.

### Notifications

Some of the rewards ruins can give will have results that are not deterministic when writing it in the JSON, so creating a good notification for it would be impossible. An example for this would be the "Gain [50]-[100] [Gold]" unique, which will give a random amount of gold. For this reason, we allow some notifications to have parameters, in which values will be filled, such as "You found [goldAmount] gold in the ruins!". All the uniques which have this property can be found below.

| Unique | Parameters |
| ------ | ---------- |
| Free [] found in the ruins | The name of the unit will be filled in the notification, including unique units of the nation |
| [] population in a random city | The name of the city to which the population is added will be filled in the notification |
| Gain []-[] [] | The exact amount of the stat gained will be filled in the notification |
| [] free random reasearchable Tech(s) from the [] | The notification must have placeholders equal to the number of techs granted this way. Each of the names of these free techs will be filled in the notification |
| Gain enough Faith for a Pantheon | The amount of faith gained is filled in the notification |
| Gain enough Faith for []% of a Great Prophet | The amount of faith gained is filled in the notification |

### Specific uniques

A few uniques can be added to ancient ruin effects to modify when they can be earned. These are:

-   "Only available after [amount] turns"
-   "Hidden when religion is disabled"
-   "Hidden after a great prophet has been earned"

## Tileset-specific json

A mod can define new Tilesets or add to existing ones, namely FantasyHex. There is one json file per Tileset, named same as the Tileset, and placed in a subfolder named "TileSets" relative to the other json files. This is called TileSetConfig and has the following structure:

| Attribute | Type | Default value | Notes |
| --------- | ---- | -------- | ----- |
| [useColorAsBaseTerrain](../Modders/Creating-a-custom-tileset.md#useColorAsBaseTerrain) | Boolean | false | |
| [useSummaryImages](../Modders/Creating-a-custom-tileset.md#useSummaryImages) | Boolean | false | |
| [unexploredTileColor](../Modders/Creating-a-custom-tileset.md#unexploredTileColor) | Color | Dark Gray | `{"r":0.25,"g":0.25,"b":0.25,"a":1}` |
| [fogOfWarColor](../Modders/Creating-a-custom-tileset.md#fogOfWarColor) | Color | Black | `{"r":0,"g":0,"b":0,"a":1}` |
| [fallbackTileSet](../Modders/Creating-a-custom-tileset.md#fallbackTileSet) | String | "FantasyHex" | null to disable |
| [tileScale](../Modders/Creating-a-custom-tileset.md#tileScale) | Float | 1.0 |  |
| [tileScales](../Modders/Creating-a-custom-tileset.md#tileScales) | Dictionary | empty |  |
| [ruleVariants](../Modders/Creating-a-custom-tileset.md#ruleVariants) | Dictionary | empty | see below |

ruleVariants control substitutions when layering images for a tile, they are list looking like:

```json
    "ruleVariants": {
        "Grassland+Forest": ["Grassland", "GrasslandForest"],
        "Plains+Forest": ["Plains", "PlainsForest"],
        "Plains+Jungle": ["Plains", "PlainsJungle"],
        // . . .
    }
```

Each line means "if the tile content is this... then combine the following png images". The key part follows a specific order and must match in its entirety, meaning "Plains+Forest" is not valid for "Plains+Forest+Deer", and when it matches no other image layering is done except roads and units (I think - *WIP*).

When TileSetConfig's for the same Tileset are combined, for the first three properties the last mod wins, while ruleVariants are merged, meaning only an entry with the same key overwrites an earlier entry.

## Stats

Terrains, features, resources and improvements may list yield statistics. They can be one of the following:

-   production
-   food
-   gold
-   science
-   culture
-   happiness
-   faith

If an object carries general stats, any combination (or none) of these can be specified. For specialized stats, they might come as sub-object in a named field. Example:

```json
		"gold": 2,
		"improvement": "Quarry",
		"improvementStats": { "gold": 1, "production": 1 },
```

The values are usually integers, though the underlying code supports floating point. The effects are, however, insufficiently tested and therefore -so far- using fractional stats is unsupported. Go ahead and thoroughly test that in a mod and help out with feedback 😁.
