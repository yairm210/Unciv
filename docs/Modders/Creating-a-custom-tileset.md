# How to make Unciv use your custom tileset

### You should read the [Mods](Mods.md) page first before proceeding

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

To create a config for your tileset you just need to create a new .json file under Jsons/Tilesets/. Just create a .txt file and rename it to MyCoolTilesetExample.json. You only have to add things if you want to change them. Else the default values will be used.

This is an example of such a config file that I will explain below:

```
    "useColorAsBaseTerrain": "false",
    "unexploredTileColor": {"r":1,"g":1,"b":1,"a":1},
    "fogOfWarColor": {"r":1,"g":0,"b":0,"a":1},
    "ruleVariants": {
        "Grassland+Forest": ["Grassland","ForestForGrassland"],
        "Grassland+Jungle+Dyes+Trading post": ["Grassland","JungleForGrasslandBack","Dyes+Trading post","JungleForGrasslandFront"]
    }
```

### useColorAsBaseTerrain

A boolean value ("true" or "false"). Default value: "true"

If true all tiles will be colored in their corresponding base terrain color. This is how the "Default" tileset works.

### unexploredTileColor

A color defined with normalized RGBA values. Default value: "{"r":0.24705882, "g":0.24705882, "b":0.24705882, "a":1}" (DarkGray)

Defines the color of the unexplored tiles.

### fogOfWarColor

A color defined with normalized RGBA values. Default value: "{"r":0, "g":0, "b":0, "a":1}" (Black)

Defines the color of the fog of war. The color gets approximated by 60% to allow the colors of the images below to shine through.

### ruleVariants

A dictionary mapping string to string[]. Default value: empty

The ruleVariants are the most powerful part of the tileset config. With this, you can define, for a specific tile, which images and in which order these images should be used.

An example is given in the code above. For the tile "Grassland+Jungle+Dyes+Trading post" we then use the images "Grassland", "JungleForGrasslandBack", "Dyes+Trading post" and "JungleForGrasslandFront" in that order.

## Nation-coloured units

Unciv can colour units according to the civilization that owns them. [[PR3231]](https://github.com/yairm210/Unciv/pull/3231)

This is used by providing multiple images per unit, each representing a coloured layer. The image suffixed with "-1" will be tinted to the civilization's inner colour, and the image suffixed with "-2" will be tinted to the civilization's outer colour. For example:

| Image | Description | Colour |
| ----- | ----------- | ------ |
| Archer.png | Base image | Untinted |
| Archer-1.png | Colour layer | Nation inner colour |
| Arhcer-2.png | Colour layer | Nation outer colour |

The [Civ Army Color Style Sheet](https://github.com/AdityaMH/Civ-Army-Color-Style-Sheet/tree/main/Images/TileSets/FantasyHex/Units) mod by @AdityaMH and the [5Hex Tileset](https://github.com/ravignir/5Hex-Tileset/tree/master/Images/TileSets/5Hex/Units) by @ravignir are very good practical examples of how this can be used.
