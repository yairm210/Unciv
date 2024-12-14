# Creating a custom tileset

**You should read the [Mods](Mods.md) page first before proceeding**

In order to add a tileset mod (yes, tilesets are just another type of mod), all you need to do is add your images under Images/Tilesets/MyCoolTilesetExample and enable the mod as a permanent visual mod - the game will recognize the tileset, and allow you to pick it in the options menu.

Let's look at the example "Grassland+Jungle+Dyes+Trading post" to learn how the game decides which images it should use for this tile:

1. When there is a rule variant entry in the [tileset config](#tileset-config) for this tile we will use the entry.
2. Else if there is an image called "Grassland+Jungle+Dyes+Trading post" we will use it instead.
3. Otherwise, we will check if there is an image called "Grassland+Jungle" (BaseTerrain+Terrainfeatures) and "Dyes+Trading post" (Resource+Improvement) and use the remainings of it. Let's say you made an image called "Grassland+Jungle" but none called "Dyes+Trading post". In the end, we will then use the images "Grassland+Jungle", "Dyes" and "Trading post".

All these images can also use era-dependant variants if you want to change the appearance of, let's say, "Trading post" throughout the game. Just create images and add the suffix "-[era name]".
E.g. "Trading post-Classical era", "Trading post-Industrial era", etc.

It is advised to use the layered approach (1 and 3) often because it comes with a few advantages. Mainly:

-   Decreased filesize (on disk, for downloads)
-   Easier support for new terrains, improvements, resources, and for changing existing tiles

You should keep in mind that the default rendering order is:
BaseTerrain, TerrainFeatures, Resource, Improvement.

## Tileset config

This is where tileset configs shine. You can use these to alter the way Unicv renders tiles.

To create a config for your tileset you just need to create a new .json file under jsons/Tilesets/. Just create a .txt file and rename it to MyCoolTilesetExample.json. You only have to add things if you want to change them. Else the default values will be used.

This is an example of such a config file that will be explain below:

```json
{
    "useColorAsBaseTerrain": "false",
    "useSummaryImages": "true",
    "unexploredTileColor": {"r":1,"g":1,"b":1,"a":1},
    "fogOfWarColor": {"r":1,"g":0,"b":0,"a":1},
    "fallbackTileSet": null,
    "tileScale":0.9,
    "tileScales": {
        "City center":1.2,
        "Citadel":1.5
    },
    "ruleVariants": {
        "Grassland+Forest": ["Grassland","ForestForGrassland"],
        "Grassland+Jungle+Dyes+Trading post": ["Grassland","JungleForGrasslandBack","Dyes+Trading post","JungleForGrasslandFront"]
    }
}
```

### useColorAsBaseTerrain

A boolean value ("true" or "false"). Default value: "false"

If true, an additional "Hexagon" image is placed below each tile and colored in the corresponding BaseTerrain color. This removes the necessity to add individual BaseTerrain images. This is how the "Minimal" tileset works.

### useSummaryImages

A boolean value ("true" or "false"). Default value: "false"

If true, summary images are used for specific groups of images instead of using individual tile images. The summary images must be placed in the same folder as every other tile image. Summary images used:

| Image group | Summary image |
| ----------- | ------------- |
| Natural wonders | "NaturalWonder" |

### unexploredTileColor

A color defined with normalized RGBA values. Default value: "{"r":0.24705882, "g":0.24705882, "b":0.24705882, "a":1}" (DarkGray)

Defines the color of the unexplored tiles.

### fogOfWarColor

A color defined with normalized RGBA values. Default value: "{"r":0, "g":0, "b":0, "a":1}" (Black)

Defines the color of the fog of war. The color gets approximated by 60% to allow the colors of the images below to shine through.

### fallbackTileSet

A string value. Default value: "FantasyHex"

The name of another tileset whose images should be used if this tileset is missing images. Can be set to null to disable the the fallback tileset

### tileScale

A float value. Default value: 1.0

The scale of all tiles. Can be used to increase or decrease the size of every tile. Is being used by the tileset mod [5Hex (made by ravignir)](https://github.com/ravignir/5Hex-Tileset) to fake shadows.

### tileScales

A dictionary mapping string to a float value. Default value: empty

Used by the "Minimal" tileset to scale all its tiles except the base terrain down. Each entry overrides the tileScale value for the specified tile.

### ruleVariants

A dictionary mapping string to a list of strings. Default value: empty

The ruleVariants are the most powerful part of the tileset config. With this, you can define, for a specific tile, which images and in which order these images should be used.

An example is given in the code above. For the tile "Grassland+Jungle+Dyes+Trading post" we then use the images "Grassland", "JungleForGrasslandBack", "Dyes+Trading post" and "JungleForGrasslandFront" in that order.

## Fog and unexplored tiles

Unciv distinguishes between "unexplored" tiles, which are tiles the Civ has never seen,
and "not visible" tiles, which are those that were seen once but now are not.

Not visible tiles are grayed out by design, and on top of that have the `CrosshatchHexagon.png` image applied to them.

Unexplored tiles display the `UnexploredTile.png` image, on top of which `CrosshatchHexagon.png` is applied.

You can set the CrosshatchHexagon to be functionally invisible by replacing it with a 1px by 1px invisible image.

## Unit images

Unit images can be changed according to civ-specific styles (if a mod specifies a "style" variable for each civilization) and according to the owning civ's current era. Unciv attempts to load the unit images in the following order (where unitName is the unit name given in Units.json, styleName is optionally specified in Nations.json, and eraName is the era name given in Eras.json (including " era")).

1. unitName-styleName-eraName (example: "Archer-customStyle1-Classical era.png")
2. unitName-eraName (example: "Archer-Classical era.png")
3. unitName-styleName (example: "Archer-customStyle1.png")
4. unitName (example: "Archer.png")

Era-specific sprites do not need to be specified for each era, only on eras where the sprites change. If a modder wants a Great General unit to change sprites starting in the Modern era, they only need to create a "Great General-Modern era.png" image. The Great General unit would use the default "Great General.png" sprite for all eras up to the Modern era then the Modern era sprite for the Modern era and all eras after unless there is a later era sprite for this unit.

### Nation-coloured units

Unciv can colour units according to the civilization that owns them. [[PR3231]](https://github.com/yairm210/Unciv/pull/3231)

This is used by providing multiple images per unit, each representing a coloured layer. The image suffixed with "-1" will be tinted to the civilization's inner colour, and the image suffixed with "-2" will be tinted to the civilization's outer colour. For example:

| Image | Description | Colour |
| ----- | ----------- | ------ |
| Archer.png | Base image | Untinted |
| Archer-1.png | Colour layer | Nation inner colour |
| Archer-2.png | Colour layer | Nation outer colour |

The [Civ Army Color Style Sheet](https://github.com/AdityaMH/Civ-Army-Color-Style-Sheet/tree/main/Images/TileSets/FantasyHex/Units) mod by @AdityaMH and the [5Hex Tileset](https://github.com/ravignir/5Hex-Tileset/tree/master/Images/TileSets/5Hex/Units) by @ravignir are very good practical examples of how this can be used.

## Attack animations

These are small animations that play on units when they receive damage.

They can be for unit types (Archery, Seige, Cavalry) or for specific unit names

The files should be in the format of `<unit type/unit name>-attack-<frame number>`.
For example, a 3 frame animation for Sword units would have the files `Sword-attack-1.png`, `Sword-attack-3.png`, `Sword-attack-3.png`

## Edge images

You can add additional images that will be drawn only when a tile is adjacent to another tile in a specific direction.

The images should be placed in the `Images/Tilesets/<tileset name>/Edges` folder, rather than in `/Tiles`.

The name of the tile should be `<tile name>-<origin tile filter>-<destination tile filter>-<neighbor direction>.png`, where direction of one of:

- Bottom
- BottomLeft
- BottomRight
- Top
- TopLeft
- TopRight

And where the tile filter is one of:
- Terrain name
- Feature name
- Terrain type (Land/Water)

For example: `Cliff-Hills-Coast-Top.png`

The initial name has no bearing on the image used, it is just a way to group images together.
