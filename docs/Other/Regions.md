# Regions

## The Concept

During the generation of a random map (only; not pre-made maps) the map is split into a number of regions equal to the number of major civs. Each region gets classified according to its prevalent terrain, or if unable to be classified is called a "hybrid" region.
The region type corresponds to the start bias of the civs as they are distributed.
The region type also determines start placement and what luxuries will appear in the region.

<details>
    <summary>Example</summary>
    <img src="https://user-images.githubusercontent.com/63475501/140308518-ad5a2f50-d5f1-4467-a296-3a67f6d0b007.png" alt="Region Example" />
</details>

## How to define region behavior in your mod

The game will work without any extra json definitions, but if you want the region system to work well when generating maps for your mod, these are the relevant uniques to define.

### Terrains.json

"Always Fertility [amount] for Map Generation", "[amount] to Fertility for Map Generation" - these determine how good a terrain is for purposes of dividing land up fairly. The numbers are arbitrary but should reflect the relative value of the terrains.

"A Region is formed with at least [amount]% [simpleTerrain] tiles, with priority [amount]",
"A Region is formed with at least [amount]% [simpleTerrain] tiles and [simpleTerrain] tiles, with priority [amount]" - these determine the rules for when a region is classified as eg a "desert" region. Terrains are evaluated in ascending priority order, so in the base ruleset tundra regions are checked first.
"A Region can not contain more [simpleTerrain] tiles than [simpleTerrain] tiles" - a useful compliment to the sum-of-two-terrains criterium above, if both terrains are in and of themselves terrain types. So in the base ruleset a large enough sum of jungle and forest allows a region to be classified as jungle, but only if there is more jungle than forest.
"Base Terrain on this tile is not counted for Region determination" - for terrain features that are unremovable or otherwise dominate the tile. Used for Hills in the base ruleset.
A region not fulfilling any criteria is classified as "Hybrid"

"Considered [terrainQuality] when determining start locations" - where "terrainQuality" is one of "Food", "Production", "Desirable", "Undesirable". Usually used together with the "<in [regionType] Regions>" or "<in all except [regionType] Regions>" to determine what terrain is attractive when determining start locations. Note: if there are none of these for a terrain, the game will use the base stats of the terrain to guess a quality, but if there are any, the game will assume that they are complete.
